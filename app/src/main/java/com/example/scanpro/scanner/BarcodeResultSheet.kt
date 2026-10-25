package com.example.scanpro.scanner

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.scanpro.core.ui.ContactDraft
import com.example.scanpro.core.ui.openNewContactForm
import com.google.mlkit.vision.barcode.common.Barcode

/** What a scanned code holds, with the actions that make sense for it. Nothing is opened without a tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeResultSheet(code: Barcode, onScanAgain: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val raw = code.rawValue.orEmpty()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(titleFor(code), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                SelectionContainer {
                    Text(
                        displayText(code),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            when (code.valueType) {
                Barcode.TYPE_URL -> {
                    val url = code.url?.url ?: raw
                    val uri = url.toUri()
                    // Only web links are opened; other schemes in a code could launch unexpected apps.
                    if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
                        Action(Icons.AutoMirrored.Filled.OpenInNew, "Open link", uri.host) { view(context, uri) }
                    }
                }
                Barcode.TYPE_WIFI -> code.wifi?.let { wifi ->
                    wifi.password?.takeIf { it.isNotEmpty() }?.let { password ->
                        Action(Icons.Default.ContentCopy, "Copy Wi-Fi password", wifi.ssid) { copy(context, "Wi-Fi password", password, sensitive = true) }
                    }
                    Action(Icons.Default.Wifi, "Open Wi-Fi settings", null) { start(context, Intent(Settings.ACTION_WIFI_SETTINGS)) }
                }
                Barcode.TYPE_CONTACT_INFO -> code.contactInfo?.let { info ->
                    Action(Icons.Default.PersonAdd, "Add to contacts", info.name?.formattedName) {
                        openNewContactForm(
                            context,
                            ContactDraft(
                                name = info.name?.formattedName,
                                company = info.organization,
                                jobTitle = info.title,
                                phones = info.phones.mapNotNull { it.number },
                                emails = info.emails.mapNotNull { it.address },
                                website = info.urls.firstOrNull(),
                                address = info.addresses.firstOrNull()?.addressLines?.joinToString(", ")
                            )
                        )
                    }
                }
                Barcode.TYPE_PHONE -> code.phone?.number?.let { number ->
                    Action(Icons.Default.Call, "Call", number) { start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }
                }
                Barcode.TYPE_SMS -> code.sms?.let { sms ->
                    Action(Icons.Default.Sms, "Send message", sms.phoneNumber) {
                        start(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", sms.phoneNumber.orEmpty(), null)).putExtra("sms_body", sms.message))
                    }
                }
                Barcode.TYPE_EMAIL -> code.email?.let { email ->
                    Action(Icons.Default.Email, "Write email", email.address) {
                        start(
                            context,
                            Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
                                putExtra(Intent.EXTRA_EMAIL, arrayOf(email.address.orEmpty()))
                                putExtra(Intent.EXTRA_SUBJECT, email.subject)
                                putExtra(Intent.EXTRA_TEXT, email.body)
                            }
                        )
                    }
                }
                Barcode.TYPE_GEO -> code.geoPoint?.let { geo ->
                    Action(Icons.Default.Map, "Show on map", "${geo.lat}, ${geo.lng}") { view(context, "geo:${geo.lat},${geo.lng}?q=${geo.lat},${geo.lng}".toUri()) }
                }
                Barcode.TYPE_CALENDAR_EVENT -> code.calendarEvent?.let { event ->
                    Action(Icons.Default.Event, "Add to calendar", event.summary) {
                        start(
                            context,
                            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                                putExtra(CalendarContract.Events.TITLE, event.summary)
                                putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
                                putExtra(CalendarContract.Events.DESCRIPTION, event.description)
                            }
                        )
                    }
                }
            }
            Action(Icons.Default.ContentCopy, "Copy", null) { copy(context, "Scanned code", raw, sensitive = code.valueType == Barcode.TYPE_WIFI) }
            Action(Icons.Default.Share, "Share", null) {
                start(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, raw), "Share"))
            }
            Action(Icons.Default.QrCodeScanner, "Scan another", null, onScanAgain)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Action(icon: ImageVector, title: String, detail: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = detail?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1) } },
        leadingContent = { Icon(icon, contentDescription = null) }
    )
}

private fun titleFor(code: Barcode) = when (code.valueType) {
    Barcode.TYPE_URL -> "Link"
    Barcode.TYPE_WIFI -> "Wi-Fi network"
    Barcode.TYPE_CONTACT_INFO -> "Contact"
    Barcode.TYPE_PHONE -> "Phone number"
    Barcode.TYPE_SMS -> "Text message"
    Barcode.TYPE_EMAIL -> "Email"
    Barcode.TYPE_GEO -> "Location"
    Barcode.TYPE_CALENDAR_EVENT -> "Event"
    Barcode.TYPE_PRODUCT, Barcode.TYPE_ISBN -> "Product code"
    else -> if (code.format == Barcode.FORMAT_QR_CODE) "QR code" else "Barcode"
}

private fun displayText(code: Barcode): String = when (code.valueType) {
    // Never show the Wi-Fi password on screen; it can be copied instead.
    Barcode.TYPE_WIFI -> code.wifi?.let { wifi ->
        val security = when (wifi.encryptionType) {
            Barcode.WiFi.TYPE_WPA -> "WPA/WPA2"
            Barcode.WiFi.TYPE_WEP -> "WEP"
            else -> "Open"
        }
        "Network: ${wifi.ssid.orEmpty()}\nSecurity: $security"
    } ?: code.rawValue.orEmpty()
    Barcode.TYPE_URL -> code.url?.url ?: code.rawValue.orEmpty()
    else -> code.displayValue ?: code.rawValue.orEmpty()
}

private fun view(context: Context, uri: Uri) = start(context, Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))

private fun start(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this", Toast.LENGTH_SHORT).show()
    }
}

private fun copy(context: Context, label: String, text: String, sensitive: Boolean) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= 33) android.content.ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
