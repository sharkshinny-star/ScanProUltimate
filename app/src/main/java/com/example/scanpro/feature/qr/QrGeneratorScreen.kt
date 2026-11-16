package com.example.scanpro.feature.qr

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.scanpro.R
import com.example.scanpro.core.ui.shareFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@get:StringRes
private val QrKind.label: Int
    get() = when (this) {
        QrKind.TEXT -> R.string.qr_type_text
        QrKind.LINK -> R.string.qr_type_link
        QrKind.WIFI -> R.string.qr_type_wifi
        QrKind.PHONE -> R.string.qr_type_phone
        QrKind.SMS -> R.string.qrgen_type_sms
        QrKind.EMAIL -> R.string.qr_type_email
        QrKind.CONTACT -> R.string.qr_type_contact
        QrKind.LOCATION -> R.string.qrgen_type_location
        QrKind.EVENT -> R.string.qrgen_type_event
        QrKind.UPI -> R.string.qr_type_upi
        QrKind.EAN_13 -> R.string.qrgen_type_ean13
        QrKind.CODE_128 -> R.string.qrgen_type_code128
    }

/** The preview is drawn smaller than the saved picture: fast to draw, and the screen shows it at most 320 dp wide. */
private const val PREVIEW_SIZE = 512
private const val PREVIEW_DELAY_MS = 200L
private val OUTPUT_SIZES = listOf(512, 1024, 2048)

@Composable
fun QrGeneratorRoute(onBack: () -> Unit, viewModel: QrGeneratorViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.qr_save_failed)
    val shareTitle = stringResource(R.string.qr_share_title)
    val savedToGallery = stringResource(R.string.qrgen_saved_gallery)
    val clipLabel = stringResource(R.string.qrgen_clip_label)
    val copied = stringResource(R.string.qrgen_copied)
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    QrGeneratorScreen(
        onBack = onBack,
        render = viewModel::render,
        canSaveToGallery = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        onOutput = { output, code ->
            scope.launch {
                if (output == QrOutput.COPY) {
                    copyText(context, clipLabel, code.content, sensitive = code.kind == QrKind.WIFI)
                    if (Build.VERSION.SDK_INT < 33) toast(copied)
                    viewModel.recordCreated(code.content, code.kind.format)
                    return@launch
                }
                // The saved picture is drawn again at the chosen size, off the main thread.
                val bitmap = viewModel.render(code.content, code.kind.format, code.style)
                val ok = when (output) {
                    QrOutput.SHARE -> viewModel.writePng(bitmap ?: return@launch toast(saveFailed))?.let { shareFile(context, it.path, "image/png", shareTitle) } != null
                    QrOutput.GALLERY -> bitmap != null && viewModel.saveToGallery(bitmap).also { if (it) toast(savedToGallery) }
                    QrOutput.DOCUMENT -> viewModel.writePng(bitmap ?: return@launch toast(saveFailed))?.let { viewModel.saveAsDocument(it) } != null
                    QrOutput.COPY -> true
                }
                if (ok) viewModel.recordCreated(code.content, code.kind.format) else toast(saveFailed)
            }
        }
    )
}

/** What the person asked to do with the finished code. */
internal enum class QrOutput { SHARE, GALLERY, COPY, DOCUMENT }

/** A finished code: its text, kind and look. */
internal data class GeneratedCode(val content: String, val kind: QrKind, val style: CodeStyle)

/**
 * Makes a QR code or barcode from typed details. The preview follows the typing (debounced, drawn off the main thread);
 * the result can be shared, saved to the gallery or as a document, or its text copied.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun QrGeneratorScreen(
    onBack: () -> Unit,
    render: suspend (String, CodeFormat, CodeStyle) -> Bitmap?,
    canSaveToGallery: Boolean,
    onOutput: (QrOutput, GeneratedCode) -> Unit,
    initialKind: QrKind = QrKind.TEXT,
    initialValues: Map<String, String> = emptyMap(),
    initialStyleOpen: Boolean = false,
    initialStyle: CodeStyle = CodeStyle()
) {
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var values by rememberSaveable { mutableStateOf<Map<String, String>>(HashMap(initialValues)) }
    var foreground by rememberSaveable { mutableIntStateOf(initialStyle.foreground) }
    var background by rememberSaveable { mutableIntStateOf(initialStyle.background) }
    var correction by rememberSaveable { mutableStateOf(initialStyle.correction) }
    var outputSize by rememberSaveable { mutableIntStateOf(initialStyle.size) }
    var styleOpen by rememberSaveable { mutableStateOf(initialStyleOpen) }

    fun value(key: String) = values[key].orEmpty()
    fun set(key: String, text: String) {
        values = HashMap(values).apply { put(key, text) }
    }

    val payload = remember(kind, values) { QrForm.payload(kind, values) }
    val style = CodeStyle(foreground, background, correction, outputSize)
    val format = kind.format
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var encodeFailed by remember { mutableStateOf(false) }
    LaunchedEffect(payload, format, foreground, background, correction) {
        if (payload == null) {
            preview = null
            encodeFailed = false
            return@LaunchedEffect
        }
        delay(PREVIEW_DELAY_MS) // a new keystroke cancels this and starts again
        val drawn = render(payload, format, style.copy(size = PREVIEW_SIZE))
        preview = drawn
        encodeFailed = drawn == null
    }
    val anyProblem = values.keys.any { QrForm.problem(it, values) != null && fieldsOf(kind).contains(it) }
    val ready = payload != null && preview != null && !encodeFailed

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Twelve kinds don't fit across a phone: the chosen one is always scrolled into view.
            val chips = rememberLazyListState()
            LaunchedEffect(kind) { chips.animateScrollToItem((kind.ordinal - 1).coerceAtLeast(0)) }
            LazyRow(state = chips, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(QrKind.entries, key = { it.name }) { option ->
                    FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(stringResource(option.label)) })
                }
            }

            KindFields(kind, values, ::value, ::set)

            // The preview sits on the chosen background, with its own quiet zone, so it scans in dark mode too.
            val previewShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .aspectRatio(if (format.isLinear) 2f else 1f)
                    .background(Color(background), previewShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, previewShape),
                contentAlignment = Alignment.Center
            ) {
                val shown = preview
                if (shown != null && payload != null) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = stringResource(if (format.isLinear) R.string.qrgen_barcode_preview else R.string.qr_preview_description),
                        filterQuality = FilterQuality.None,
                        // A barcode picture has no margin above and below its bars; the frame adds one.
                        modifier = Modifier.fillMaxSize().padding(if (format.isLinear) 14.dp else 0.dp)
                    )
                } else {
                    Text(
                        stringResource(
                            when {
                                encodeFailed -> R.string.qr_too_long
                                anyProblem -> R.string.qrgen_fix_fields
                                else -> R.string.qr_preview_empty
                            }
                        ),
                        color = if (Color(background).luminance() > 0.5f) Color(0xFF444444) else Color(0xFFDDDDDD),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
            if (format.isLinear && payload != null) {
                // The digits printed under a product barcode, so a wrong number is easy to spot.
                Text(
                    payload,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            ContrastWarning(CodeColours.verdict(foreground, background))

            StyleSection(
                open = styleOpen,
                onOpenChange = { styleOpen = it },
                isQr = format == CodeFormat.QR,
                foreground = foreground,
                background = background,
                correction = correction,
                size = outputSize,
                onForeground = { foreground = it },
                onBackground = { background = it },
                onCorrection = { correction = it },
                onSize = { outputSize = it }
            )

            val code = payload?.let { GeneratedCode(it, kind, style) }
            Button(
                enabled = ready,
                onClick = { code?.let { onOutput(QrOutput.SHARE, it) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.qr_share_png))
            }
            // One per row: the labels are long in Tamil and Malayalam, and a cut-off label hides what the button does.
            if (canSaveToGallery) {
                OutputButton(Icons.Default.Image, R.string.qrgen_save_gallery, ready) { code?.let { onOutput(QrOutput.GALLERY, it) } }
            }
            OutputButton(Icons.Default.PictureAsPdf, R.string.qr_save_document, ready) { code?.let { onOutput(QrOutput.DOCUMENT, it) } }
            OutputButton(Icons.Default.ContentCopy, R.string.qrgen_copy_text, payload != null) { code?.let { onOutput(QrOutput.COPY, it) } }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The keys of the fields shown for [kind], so a problem in a hidden kind's field doesn't change this kind's hint. */
private fun fieldsOf(kind: QrKind): Set<String> = when (kind) {
    QrKind.TEXT -> setOf(QrForm.TEXT)
    QrKind.LINK -> setOf(QrForm.LINK)
    QrKind.WIFI -> setOf(QrForm.SSID, QrForm.WIFI_PASSWORD)
    QrKind.PHONE -> setOf(QrForm.PHONE)
    QrKind.SMS -> setOf(QrForm.SMS_NUMBER, QrForm.SMS_MESSAGE)
    QrKind.EMAIL -> setOf(QrForm.EMAIL, QrForm.SUBJECT, QrForm.BODY)
    QrKind.CONTACT -> setOf(QrForm.NAME, QrForm.CONTACT_PHONE, QrForm.CONTACT_EMAIL)
    QrKind.LOCATION -> setOf(QrForm.LATITUDE, QrForm.LONGITUDE, QrForm.PLACE)
    QrKind.EVENT -> setOf(QrForm.EVENT_TITLE, QrForm.START_DATE, QrForm.START_TIME, QrForm.END_DATE, QrForm.END_TIME, QrForm.EVENT_LOCATION, QrForm.EVENT_DESCRIPTION)
    QrKind.UPI -> setOf(QrForm.UPI_ADDRESS, QrForm.UPI_NAME, QrForm.UPI_AMOUNT, QrForm.UPI_NOTE)
    QrKind.EAN_13 -> setOf(QrForm.EAN)
    QrKind.CODE_128 -> setOf(QrForm.CODE_128)
}

/** Examples shown inside empty fields; numbers and addresses that read the same in every language. */
private object Examples {
    const val LINK = "example.com"
    const val PHONE = "+91 98450 12345"
    const val EMAIL = "name@example.com"
    const val UPI = "name@bank"
    const val LATITUDE = "12.9716"
    const val LONGITUDE = "77.5946"
    const val DATE = "2026-11-20"
    const val TIME = "14:30"
    const val EAN = "400638133393"
    const val CODE_128 = "INV-2026-0042"
}

@Composable
private fun KindFields(kind: QrKind, values: Map<String, String>, value: (String) -> String, set: (String, String) -> Unit) {
    @Composable
    fun F(
        key: String,
        @StringRes label: Int,
        keyboard: KeyboardType = KeyboardType.Text,
        placeholder: String? = null,
        singleLine: Boolean = true,
        capitalise: Boolean = false,
        secret: Boolean = false,
        helper: String? = null,
        trailing: (@Composable () -> Unit)? = null
    ) = Field(
        value = value(key),
        onChange = { set(key, it) },
        label = label,
        keyboard = keyboard,
        placeholder = placeholder,
        singleLine = singleLine,
        capitalise = capitalise,
        secret = secret,
        problem = QrForm.problem(key, values),
        values = values,
        helper = helper,
        trailing = trailing
    )

    when (kind) {
        QrKind.TEXT -> F(QrForm.TEXT, R.string.qr_field_text, placeholder = stringResource(R.string.qrgen_hint_text), singleLine = false, capitalise = true)
        QrKind.LINK -> F(QrForm.LINK, R.string.qr_field_link, KeyboardType.Uri, Examples.LINK)
        QrKind.WIFI -> {
            F(QrForm.SSID, R.string.qr_field_network)
            val security = QrForm.security(values)
            Text(stringResource(R.string.qr_field_security), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    WifiSecurity.WPA to R.string.qr_security_wpa,
                    WifiSecurity.WEP to R.string.qr_security_wep,
                    WifiSecurity.NONE to R.string.qr_security_none
                ).forEach { (option, label) ->
                    FilterChip(selected = security == option, onClick = { set(QrForm.SECURITY, option.name) }, label = { Text(stringResource(label)) })
                }
            }
            if (security != WifiSecurity.NONE) F(QrForm.WIFI_PASSWORD, R.string.qr_field_password, KeyboardType.Password, secret = true)
            val hidden = value(QrForm.HIDDEN) == "true"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = hidden, role = Role.Switch, onValueChange = { set(QrForm.HIDDEN, it.toString()) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.qr_field_hidden), modifier = Modifier.weight(1f))
                Switch(checked = hidden, onCheckedChange = null)
            }
            Note(stringResource(R.string.qr_wifi_warning))
        }
        QrKind.PHONE -> F(QrForm.PHONE, R.string.qr_field_phone, KeyboardType.Phone, Examples.PHONE)
        QrKind.SMS -> {
            F(QrForm.SMS_NUMBER, R.string.qr_field_phone, KeyboardType.Phone, Examples.PHONE)
            F(QrForm.SMS_MESSAGE, R.string.qrgen_field_message, singleLine = false, capitalise = true)
        }
        QrKind.EMAIL -> {
            F(QrForm.EMAIL, R.string.qr_field_email, KeyboardType.Email, Examples.EMAIL)
            F(QrForm.SUBJECT, R.string.qr_field_subject, capitalise = true)
            F(QrForm.BODY, R.string.qr_field_body, singleLine = false, capitalise = true)
        }
        QrKind.CONTACT -> {
            F(QrForm.NAME, R.string.qr_field_name, capitalise = true)
            F(QrForm.CONTACT_PHONE, R.string.qr_field_phone, KeyboardType.Phone, Examples.PHONE)
            F(QrForm.CONTACT_EMAIL, R.string.qr_field_email, KeyboardType.Email, Examples.EMAIL)
        }
        QrKind.LOCATION -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { F(QrForm.LATITUDE, R.string.qrgen_field_latitude, KeyboardType.Decimal, Examples.LATITUDE) }
                Box(Modifier.weight(1f)) { F(QrForm.LONGITUDE, R.string.qrgen_field_longitude, KeyboardType.Decimal, Examples.LONGITUDE) }
            }
            F(QrForm.PLACE, R.string.qrgen_field_place, capitalise = true)
            Note(stringResource(R.string.qrgen_location_help))
        }
        QrKind.EVENT -> {
            F(QrForm.EVENT_TITLE, R.string.qrgen_field_event_title, capitalise = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1.2f)) { DateField(QrForm.START_DATE, R.string.qrgen_field_start_date, value, set) { F(QrForm.START_DATE, R.string.qrgen_field_start_date, KeyboardType.Number, Examples.DATE, trailing = it) } }
                Box(Modifier.weight(1f)) { F(QrForm.START_TIME, R.string.qrgen_field_start_time, KeyboardType.Number, Examples.TIME) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1.2f)) { DateField(QrForm.END_DATE, R.string.qrgen_field_end_date, value, set) { F(QrForm.END_DATE, R.string.qrgen_field_end_date, KeyboardType.Number, Examples.DATE, trailing = it) } }
                Box(Modifier.weight(1f)) { F(QrForm.END_TIME, R.string.qrgen_field_end_time, KeyboardType.Number, Examples.TIME) }
            }
            Note(stringResource(R.string.qrgen_event_all_day))
            F(QrForm.EVENT_LOCATION, R.string.qrgen_field_location, capitalise = true)
            F(QrForm.EVENT_DESCRIPTION, R.string.qrgen_field_description, singleLine = false, capitalise = true)
        }
        QrKind.UPI -> {
            F(QrForm.UPI_ADDRESS, R.string.qr_field_upi_id, KeyboardType.Email, Examples.UPI)
            F(QrForm.UPI_NAME, R.string.qr_field_payee_name, capitalise = true)
            F(QrForm.UPI_AMOUNT, R.string.qr_field_amount, KeyboardType.Decimal, "250")
            F(QrForm.UPI_NOTE, R.string.qr_field_note, capitalise = true)
        }
        QrKind.EAN_13 -> {
            val added = QrForm.eanCheckDigitToAdd(values)
            F(QrForm.EAN, R.string.qrgen_field_ean13, KeyboardType.Number, Examples.EAN, helper = added?.let { stringResource(R.string.qrgen_ean_check_added, it) })
        }
        QrKind.CODE_128 -> F(QrForm.CODE_128, R.string.qrgen_field_code128, KeyboardType.Ascii, Examples.CODE_128)
    }
}

/** A date field with a calendar button that opens a date picker; typing YYYY-MM-DD also works. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    key: String,
    @StringRes label: Int,
    value: (String) -> String,
    set: (String, String) -> Unit,
    field: @Composable ((@Composable () -> Unit)) -> Unit
) {
    var picking by rememberSaveable(key) { mutableStateOf(false) }
    val pickLabel = stringResource(R.string.qrgen_pick_date)
    field {
        IconButton(onClick = { picking = true }, modifier = Modifier.semantics { contentDescription = pickLabel }) {
            Icon(Icons.Default.DateRange, contentDescription = null)
        }
    }
    if (picking) {
        val iso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") } }
        val initial = QrPayloads.icalDate(value(key))?.let { runCatching { iso.parse(value(key).trim())?.time }.getOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { set(key, iso.format(Date(it))) }
                    picking = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.common_cancel)) } }
        ) { DatePicker(state = state, title = { Text(stringResource(label), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }) }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun problemText(problem: FieldProblem, values: Map<String, String>): String = when (problem) {
    FieldProblem.LINK -> stringResource(R.string.qrgen_error_link)
    FieldProblem.EMAIL -> stringResource(R.string.qrgen_error_email)
    FieldProblem.PHONE -> stringResource(R.string.qrgen_error_phone)
    FieldProblem.LATITUDE -> stringResource(R.string.qrgen_error_latitude)
    FieldProblem.LONGITUDE -> stringResource(R.string.qrgen_error_longitude)
    FieldProblem.DATE -> stringResource(R.string.qrgen_error_date, Examples.DATE)
    FieldProblem.TIME -> stringResource(R.string.qrgen_error_time, Examples.TIME)
    FieldProblem.END_BEFORE_START -> stringResource(R.string.qrgen_error_end_before_start)
    FieldProblem.AMOUNT -> stringResource(R.string.qrgen_error_amount)
    FieldProblem.EAN_DIGITS -> stringResource(R.string.qrgen_error_ean_digits)
    FieldProblem.EAN_LENGTH -> stringResource(R.string.qrgen_error_ean_length)
    FieldProblem.EAN_CHECK -> stringResource(R.string.qrgen_error_ean_check, QrForm.eanExpectedCheckDigit(values) ?: 0)
    FieldProblem.CODE_128_CHARACTERS -> stringResource(R.string.qrgen_error_code128_characters)
    FieldProblem.CODE_128_TOO_LONG -> stringResource(R.string.qrgen_error_code128_long, BarcodeChecks.CODE_128_MAX)
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    @StringRes label: Int,
    keyboard: KeyboardType,
    placeholder: String?,
    singleLine: Boolean,
    capitalise: Boolean,
    secret: Boolean,
    problem: FieldProblem?,
    values: Map<String, String>,
    helper: String?,
    trailing: (@Composable () -> Unit)?
) {
    val supporting = problem?.let { problemText(it, values) } ?: helper
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = problem != null,
        supportingText = supporting?.let { { Text(it) } },
        trailingIcon = trailing,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (capitalise) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            autoCorrectEnabled = capitalise,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ContrastWarning(verdict: ContrastVerdict) {
    val message = when (verdict) {
        ContrastVerdict.GOOD -> return
        ContrastVerdict.LOW -> R.string.qrgen_contrast_low
        ContrastVerdict.TOO_LOW -> R.string.qrgen_contrast_too_low
        ContrastVerdict.INVERTED -> R.string.qrgen_contrast_inverted
    }
    val severe = verdict != ContrastVerdict.LOW
    Surface(
        color = if (severe) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = if (severe) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = if (severe) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleSection(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    isQr: Boolean,
    foreground: Int,
    background: Int,
    correction: QrCorrection,
    size: Int,
    onForeground: (Int) -> Unit,
    onBackground: (Int) -> Unit,
    onCorrection: (QrCorrection) -> Unit,
    onSize: (Int) -> Unit
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = open, role = Role.Button, onValueChange = onOpenChange)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.qrgen_style), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.qrgen_colour_code), style = MaterialTheme.typography.labelLarge)
                    Swatches(CodeColours.FOREGROUNDS, foreground, onForeground)
                    Text(stringResource(R.string.qrgen_colour_background), style = MaterialTheme.typography.labelLarge)
                    Swatches(CodeColours.BACKGROUNDS, background, onBackground)
                    if (isQr) {
                        Text(stringResource(R.string.qrgen_correction), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QrCorrection.entries.forEach { level ->
                                FilterChip(
                                    selected = correction == level,
                                    onClick = { onCorrection(level) },
                                    label = { Text(stringResource(R.string.qrgen_correction_option, level.name, level.percent)) }
                                )
                            }
                        }
                        Note(stringResource(R.string.qrgen_correction_help))
                    }
                    Text(stringResource(R.string.qrgen_size), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OUTPUT_SIZES.forEach { option ->
                            FilterChip(selected = size == option, onClick = { onSize(option) }, label = { Text(stringResource(R.string.qrgen_size_option, option)) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(colours: IntArray, selected: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colours.forEachIndexed { index, argb ->
            val colour = Color(argb)
            val isSelected = argb == selected
            val description = stringResource(R.string.qrgen_colour_option, index + 1, colours.size)
            Box(
                Modifier
                    .size(40.dp)
                    .background(colour, CircleShape)
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onPick(argb) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = if (colour.luminance() > 0.5f) Color.Black else Color.White)
            }
        }
    }
}

@Composable
private fun OutputButton(icon: androidx.compose.ui.graphics.vector.ImageVector, @StringRes label: Int, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(enabled = enabled, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label))
    }
}

/** Copies [text]; a Wi-Fi code (it holds the password) is marked sensitive so the keyboard and clipboard preview hide it. */
private fun copyText(context: Context, label: String, text: String, sensitive: Boolean) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
        }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}
