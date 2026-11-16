package com.example.scanpro.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.data.ScanHistoryCsv

/** A short scanner beep through the notification stream (no permission); silent when the phone is on silent. */
internal class CodeBeeper {
    private var tone: ToneGenerator? = null

    fun beep() {
        val generator = tone ?: runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()?.also { tone = it } ?: return
        runCatching { generator.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
    }

    fun release() {
        tone?.release()
        tone = null
    }
}

/** The text of collected codes for Copy all and Share all: one per line, Wi-Fi passwords left out. */
internal fun batchText(codes: List<ScannedCode>): String =
    codes.joinToString("\n") { ScanHistoryCsv.withoutWifiPassword(it.raw).replace("\r\n", " ").replace('\n', ' ') }

/** The codes collected by batch scan: tap one to open it, or copy, share or clear them all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrBatchSheet(codes: List<ScannedCode>, onPick: (ScannedCode) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.qrscan_batch_title)
    val copied = stringResource(R.string.scan_code_copied)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear, enabled = codes.isNotEmpty()) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text(stringResource(R.string.qrscan_batch_clear))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText(title, batchText(codes)))
                    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }, enabled = codes.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text(stringResource(R.string.qrscan_batch_copy_all), maxLines = 1)
                }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, batchText(codes))
                    runCatching { context.startActivity(Intent.createChooser(send, title)) }
                }, enabled = codes.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 3.dp))
                    Text(stringResource(R.string.qrscan_batch_share_all), maxLines = 1)
                }
            }
            LazyColumn(Modifier.fillMaxWidth().height((codes.size.coerceIn(1, 6) * 72).dp)) {
                itemsIndexed(codes, key = { _, code -> code.raw }) { index, code ->
                    ListItem(
                        modifier = Modifier.clickable(role = Role.Button) { onPick(code) },
                        overlineContent = { Text("${index + 1} · ${titleFor(code)}") },
                        headlineContent = { Text(codeSummary(code, 120), maxLines = 2) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
