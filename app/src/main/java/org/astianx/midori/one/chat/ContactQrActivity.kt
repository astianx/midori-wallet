/* Copyright 2026 Astian. Licensed under the Apache License, Version 2.0. */
package org.astianx.midori.one.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.astianx.midori.one.R

/** Portrait-only QR reader. Contents are passed to contact review, never executed as an action. */
class ContactQrActivity : CaptureActivity()

@Composable
internal fun rememberContactScanner(onResult: (String) -> Unit): () -> Unit {
    val prompt = stringResource(R.string.chat_scan_contact_prompt)
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(onResult)
    }
    return {
        launcher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setCaptureActivity(ContactQrActivity::class.java).setOrientationLocked(true)
            .setBeepEnabled(false).setBarcodeImageEnabled(false).setPrompt(prompt))
    }
}
