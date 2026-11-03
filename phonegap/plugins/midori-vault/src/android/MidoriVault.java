package org.astian.midori.vault;
import android.app.Activity;
import android.content.Intent;
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
