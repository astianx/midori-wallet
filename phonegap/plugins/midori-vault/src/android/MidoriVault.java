package org.astian.midori.vault;
import android.app.Activity;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;
public final class MidoriVault extends CordovaPlugin {
  private CallbackContext receiptCallback;
  private byte[] receiptBytes;
  private static final int SAVE_RECEIPT = 7122, SHARE_RECEIPT = 7123;
  private CallbackContext fileCallback;
  private String fileContents;
  private static final int SAVE_FILE = 7120, READ_FILE = 7121;
  private static final String ALIAS = "midori.vault.v1";
  private synchronized SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
    if (store.containsAlias(ALIAS)) return (SecretKey)store.getKey(ALIAS, null);
    KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).setRandomizedEncryptionRequired(true).build());
    return generator.generateKey();
  }
  @Override public boolean execute(String action, JSONArray args, CallbackContext callback) {
    if (action.equals("shareReceipt") || action.equals("saveReceipt")) {
      if (receiptCallback != null) { callback.error("Receipt action already open"); return true; }
      try {
        String name = args.getString(0), mime = args.getString(1), encoded = args.getString(2);
        boolean pdf = mime.equals("application/pdf");
        if (!name.matches("midori-receipt-[0-9a-f-]{36}\\.(png|pdf)") || (!pdf && !mime.equals("image/png")) || encoded.length() > 12 * 1024 * 1024) throw new Exception();
        byte[] data = Base64.decode(encoded, Base64.DEFAULT);
        if (data.length < 8 || data.length > 8 * 1024 * 1024 || pdf && (!name.endsWith(".pdf") || !new String(data, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) || !pdf && (!name.endsWith(".png") || data[0] != (byte)137 || data[1] != 80 || data[2] != 78 || data[3] != 71 || data[4] != 13 || data[5] != 10 || data[6] != 26 || data[7] != 10)) throw new Exception();
        receiptCallback = callback;
        if (action.equals("saveReceipt")) {
          receiptBytes = data;
          Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT); picker.addCategory(Intent.CATEGORY_OPENABLE); picker.setType(mime); picker.putExtra(Intent.EXTRA_TITLE, name); cordova.startActivityForResult(this, picker, SAVE_RECEIPT);
        } else {
          File folder = new File(cordova.getActivity().getCacheDir(), "midori-receipts");
          if (!folder.exists() && !folder.mkdirs()) throw new Exception();
          File[] previous = folder.listFiles(); if (previous != null) for (File file : previous) if (file.lastModified() < System.currentTimeMillis() - 86400000L) file.delete();
          File output = new File(folder, name); try (FileOutputStream stream = new FileOutputStream(output)) { stream.write(data); }
          Uri uri = FileProvider.getUriForFile(cordova.getActivity(), cordova.getActivity().getPackageName() + ".midori.receipts", output);
          Intent send = new Intent(Intent.ACTION_SEND); send.setType(mime); send.putExtra(Intent.EXTRA_STREAM, uri); send.setClipData(ClipData.newRawUri("Midori Wallet", uri)); send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          cordova.startActivityForResult(this, Intent.createChooser(send, null), SHARE_RECEIPT);
        }
      } catch (Exception error) { receiptCallback = null; receiptBytes = null; callback.error("Receipt could not be saved or shared"); }
      return true;
    }
    if (action.equals("saveFile") || action.equals("readFile")) {
      if (fileCallback != null) { callback.error("File picker is already open"); return true; }
      try {
        fileContents = action.equals("saveFile") ? args.getString(0) : null;
        if (fileContents != null && fileContents.length() > 32768) throw new Exception();
        fileCallback = callback;
        Intent intent = new Intent(action.equals("saveFile") ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json");
        if (fileContents != null) intent.putExtra(Intent.EXTRA_TITLE, "midori-recovery.json");
        cordova.startActivityForResult(this, intent, fileContents != null ? SAVE_FILE : READ_FILE);
      } catch (Exception error) { fileCallback = null; fileContents = null; callback.error("Recovery file could not be opened or saved"); }
      return true;
    }
    if (!action.equals("seal") && !action.equals("open") && !action.equals("remove")) return false;
    cordova.getThreadPool().execute(() -> {
      try {
        String value = args.getString(0);
        if (value.length() > 100000) throw new Exception();
        if (action.equals("remove")) { callback.success(); return; }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        if (action.equals("seal")) {
          cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
          JSONObject record = new JSONObject(); record.put("version", 1); record.put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
          record.put("ciphertext", Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP)); callback.success(record.toString());
        } else {
          JSONObject record = new JSONObject(value); if (record.getInt("version") != 1) throw new Exception();
          cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(record.getString("iv"), Base64.NO_WRAP)));
          cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
          byte[] plain = cipher.doFinal(Base64.decode(record.getString("ciphertext"), Base64.NO_WRAP));
          try { callback.success(new String(plain, StandardCharsets.UTF_8)); } finally { java.util.Arrays.fill(plain, (byte)0); }
        }
      } catch (Exception error) { callback.error("Protected mobile storage is unavailable or invalid"); }
    }); return true;
  }
  @Override public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    if (requestCode == SAVE_RECEIPT || requestCode == SHARE_RECEIPT) {
      CallbackContext callback = receiptCallback; byte[] data = receiptBytes; receiptCallback = null; receiptBytes = null;
      if (callback == null) return;
      try {
        if (requestCode == SAVE_RECEIPT && resultCode == Activity.RESULT_OK && intent != null && intent.getData() != null) {
          try (OutputStream stream = cordova.getActivity().getContentResolver().openOutputStream(intent.getData(), "wt")) { stream.write(data); }
        }
        JSONObject result = new JSONObject(); result.put("cancelled", resultCode != Activity.RESULT_OK); callback.success(result);
      } catch (Exception error) { callback.error("Receipt could not be saved"); }
      finally { if (data != null) java.util.Arrays.fill(data, (byte)0); }
      return;
    }
    if (requestCode != SAVE_FILE && requestCode != READ_FILE) return;
    CallbackContext callback = fileCallback; String contents = fileContents; fileCallback = null; fileContents = null;
    if (callback == null) return;
    if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null) { callback.error("File picker cancelled"); return; }
    try {
      if (requestCode == SAVE_FILE) {
        try (OutputStream output = cordova.getActivity().getContentResolver().openOutputStream(intent.getData(), "wt")) { output.write(contents.getBytes(StandardCharsets.UTF_8)); }
        callback.success();
      } else {
        try (InputStream input = cordova.getActivity().getContentResolver().openInputStream(intent.getData()); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
          byte[] buffer = new byte[4096]; int count;
          while ((count = input.read(buffer)) != -1) { if (output.size() + count > 32768) throw new Exception(); output.write(buffer, 0, count); }
          callback.success(output.toString("UTF-8"));
        }
      }
    } catch (Exception error) { callback.error("Recovery file could not be opened or saved"); }
  }
}
