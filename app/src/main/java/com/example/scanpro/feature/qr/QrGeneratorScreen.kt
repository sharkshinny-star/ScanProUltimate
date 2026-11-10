package com.example.scanpro.feature.qr

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.scanpro.R
import com.example.scanpro.core.ui.shareFile
import kotlinx.coroutines.launch

private enum class QrKind(@StringRes val label: Int) {
    TEXT(R.string.qr_type_text),
    LINK(R.string.qr_type_link),
    WIFI(R.string.qr_type_wifi),
    PHONE(R.string.qr_type_phone),
    EMAIL(R.string.qr_type_email),
    CONTACT(R.string.qr_type_contact),
    UPI(R.string.qr_type_upi)
}

@Composable
fun QrGeneratorRoute(onBack: () -> Unit, viewModel: QrGeneratorViewModel = hiltViewModel()) {
    QrGeneratorScreen(viewModel = viewModel, onBack = onBack)
}

/** Makes a QR code from typed details, shows it live, and shares it as a PNG or saves it as a document. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrGeneratorScreen(viewModel: QrGeneratorViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(QrKind.TEXT) }
    var text by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    var ssid by rememberSaveable { mutableStateOf("") }
    var wifiPassword by rememberSaveable { mutableStateOf("") }
    var security by rememberSaveable { mutableStateOf(WifiSecurity.WPA) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var phone by rememberSaveable { mutableStateOf("") }
    var emailAddress by rememberSaveable { mutableStateOf("") }
    var emailSubject by rememberSaveable { mutableStateOf("") }
    var emailBody by rememberSaveable { mutableStateOf("") }
    var contactName by rememberSaveable { mutableStateOf("") }
    var contactPhone by rememberSaveable { mutableStateOf("") }
    var contactEmail by rememberSaveable { mutableStateOf("") }
    var upiAddress by rememberSaveable { mutableStateOf("") }
    var upiName by rememberSaveable { mutableStateOf("") }
    var upiAmount by rememberSaveable { mutableStateOf("") }
    var upiNote by rememberSaveable { mutableStateOf("") }

    val payload: String? = when (kind) {
        QrKind.TEXT -> QrPayloads.text(text)
        QrKind.LINK -> QrPayloads.link(link)
        QrKind.WIFI -> QrPayloads.wifi(ssid, wifiPassword, security, hidden)
        QrKind.PHONE -> QrPayloads.phone(phone)
        QrKind.EMAIL -> QrPayloads.email(emailAddress, emailSubject, emailBody)
        QrKind.CONTACT -> QrPayloads.contact(contactName, contactPhone, contactEmail)
        QrKind.UPI -> QrPayloads.upi(upiAddress, upiName, upiAmount, upiNote)
    }
    val bitmap = remember(payload) { payload?.let { QrEncoder.encode(it)?.toBitmap() } }
    val saveFailed = stringResource(R.string.qr_save_failed)
    val shareTitle = stringResource(R.string.qr_share_title)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(stringResource(R.string.qr_generator_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QrKind.entries.forEach { option ->
                    FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(stringResource(option.label)) })
                }
            }

            when (kind) {
                QrKind.TEXT -> Field(text, { text = it }, R.string.qr_field_text, singleLine = false)
                QrKind.LINK -> Field(link, { link = it }, R.string.qr_field_link, KeyboardType.Uri)
                QrKind.WIFI -> {
                    Field(ssid, { ssid = it }, R.string.qr_field_network)
                    Text(stringResource(R.string.qr_field_security), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            WifiSecurity.WPA to R.string.qr_security_wpa,
                            WifiSecurity.WEP to R.string.qr_security_wep,
                            WifiSecurity.NONE to R.string.qr_security_none
                        ).forEach { (option, label) ->
                            FilterChip(selected = security == option, onClick = { security = option }, label = { Text(stringResource(label)) })
                        }
                    }
                    if (security != WifiSecurity.NONE) Field(wifiPassword, { wifiPassword = it }, R.string.qr_field_password)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = hidden, role = Role.Switch, onValueChange = { hidden = it })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.qr_field_hidden), modifier = Modifier.weight(1f))
                        Switch(checked = hidden, onCheckedChange = null)
                    }
                    Text(
                        stringResource(R.string.qr_wifi_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                QrKind.PHONE -> Field(phone, { phone = it }, R.string.qr_field_phone, KeyboardType.Phone)
                QrKind.EMAIL -> {
                    Field(emailAddress, { emailAddress = it }, R.string.qr_field_email, KeyboardType.Email)
                    Field(emailSubject, { emailSubject = it }, R.string.qr_field_subject)
                    Field(emailBody, { emailBody = it }, R.string.qr_field_body, singleLine = false)
                }
                QrKind.CONTACT -> {
                    Field(contactName, { contactName = it }, R.string.qr_field_name)
                    Field(contactPhone, { contactPhone = it }, R.string.qr_field_phone, KeyboardType.Phone)
                    Field(contactEmail, { contactEmail = it }, R.string.qr_field_email, KeyboardType.Email)
                }
                QrKind.UPI -> {
                    Field(upiAddress, { upiAddress = it }, R.string.qr_field_upi_id, KeyboardType.Email)
                    Field(upiName, { upiName = it }, R.string.qr_field_payee_name)
                    Field(upiAmount, { upiAmount = it }, R.string.qr_field_amount, KeyboardType.Decimal, isError = !QrPayloads.isValidUpiAmount(upiAmount))
                    Field(upiNote, { upiNote = it }, R.string.qr_field_note)
                }
            }

            // Always black on white with its own quiet zone, so it scans in dark mode too.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.White, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.qr_preview_description),
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        stringResource(if (payload == null) R.string.qr_preview_empty else R.string.qr_too_long),
                        color = Color(0xFF444444),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            Button(
                enabled = bitmap != null,
                onClick = {
                    if (bitmap != null) scope.launch {
                        val file = viewModel.writePng(bitmap)
                        if (file != null) shareFile(context, file.path, "image/png", shareTitle)
                        else Toast.makeText(context, saveFailed, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.qr_share_png))
            }
            OutlinedButton(
                enabled = bitmap != null,
                onClick = {
                    if (bitmap != null) scope.launch {
                        val file = viewModel.writePng(bitmap)
                        if (file != null) viewModel.saveAsDocument(file)
                        else Toast.makeText(context, saveFailed, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.qr_save_document))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    @StringRes label: Int,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}
