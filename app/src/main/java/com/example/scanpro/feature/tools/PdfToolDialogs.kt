package com.example.scanpro.feature.tools

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.imaging.PdfSecurity

// PDF to images and Images to PDF moved to ConvertToolPages.kt (F211).

@Composable
internal fun PasswordDialog(
    isProtect: Boolean,
    initialError: String? = null,
    /** Set when Merge asks for the password of this file: the dialog names it and the button carries on merging. */
    mergeFileName: String? = null,
    /** The file being protected or unlocked, shown above the question; not set when Merge asks. */
    fileUri: Uri? = null,
    /** Protect only: the picked file already needs a password, and the person chose to remove it first. */
    onUnlockInstead: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    // Protect: find out before any typing whether the file already needs a password to open. Null while checking.
    // A file that cannot be read at all counts as not protected here; the tool itself then explains the problem.
    val alreadyProtected by androidx.compose.runtime.produceState<Boolean?>(
        initialValue = if (isProtect && fileUri != null) null else false,
        key1 = fileUri,
        key2 = isProtect
    ) {
        if (isProtect && fileUri != null) {
            value = runCatching { com.example.scanpro.imaging.PdfToolsEngine.needsOpenPassword(context, fileUri) }.getOrDefault(false)
        }
    }
    val blocked = alreadyProtected == true
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorText by remember(initialError) { mutableStateOf(initialError) }
    // Unlocking must accept whatever password the file already has, however short.
    val minimum = if (isProtect) PdfSecurity.MIN_PASSWORD_LENGTH else 1
    // Read here: the confirm button's onClick is not a composable scope.
    val tooShortError = pluralStringResource(R.plurals.tools_password_too_short, minimum, minimum)
    val enterPasswordError = stringResource(R.string.tools_password_enter)
    val mismatchError = stringResource(R.string.tools_password_mismatch)

    // A full tool page (F146) instead of a pop-up: options on cards, the button always in reach at the bottom.
    ToolPage(
        title = stringResource(
                    when {
                        mergeFileName != null -> R.string.ui_pdf_password_title
                        isProtect -> R.string.tools_password_protect_title
                        else -> R.string.tools_unlock_pdf
                    }
                ),
        onClose = onDismiss,
        actionLabel = stringResource(
            when {
                mergeFileName != null -> R.string.common_continue
                blocked && onUnlockInstead != null -> R.string.tools_protect_remove_password_first
                blocked -> R.string.common_close
                isProtect -> R.string.tools_protect_pdf
                else -> R.string.tools_unlock_pdf
            }
        ),
        onAction = {
            if (blocked) {
                onUnlockInstead?.invoke() ?: onDismiss()
            } else if (password.length < minimum) {
                errorText = if (isProtect) tooShortError else enterPasswordError
            } else if (isProtect && confirm != password) {
                errorText = mismatchError
            } else {
                onConfirm(password)
            }
        },
        // Waits the moment the check takes, so a protected file is never sent to be protected again.
        actionEnabled = alreadyProtected != null,
        actionSupport = null,
        // Merge asks for a password on its own behalf: no demonstration of Protect or Unlock there.
        demoToolId = if (mergeFileName != null) null else if (isProtect) "protect" else "unlock"
    ) {
            Column {
                if (fileUri != null) {
                    PickedFileRow(fileUri)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (alreadyProtected == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        com.example.scanpro.core.designsystem.ProcessingDots(MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.msg_tools_checking_protection),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    return@Column
                }
                if (blocked) {
                    AlreadyProtectedNotice()
                    return@Column
                }
                Text(
                    when {
                        mergeFileName != null -> stringResource(R.string.tools_merge_password_prompt, mergeFileName)
                        isProtect -> pluralStringResource(R.plurals.tools_password_protect_warning, PdfSecurity.MIN_PASSWORD_LENGTH, PdfSecurity.MIN_PASSWORD_LENGTH)
                        else -> stringResource(R.string.tools_password_unlock_prompt)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (mergeFileName != null) {
                    // The pages leave the protected file behind: say so before the user carries on.
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.tools_merge_password_unprotected_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorText = null },
                    label = { Text(stringResource(R.string.tools_password_label)) },
                    singleLine = true,
                    isError = errorText != null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    // Password type even while shown, so the keyboard never learns or suggests it.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.tools_password_toggle_visibility)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isProtect) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it; errorText = null },
                        label = { Text(stringResource(R.string.tools_password_confirm_label)) },
                        singleLine = true,
                        isError = errorText != null,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // The error sits right under the fields it is about.
                if (errorText != null) {
                    Text(
                        errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (isProtect && password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PasswordStrength(password)
                }
                // Protect: the file as it is now and as it will be (same pages, a little larger for the encryption),
                // after the choice it depends on.
                if (isProtect && fileUri != null) {
                    val facts by rememberPdfFacts(fileUri)
                    facts?.let { known ->
                        Spacer(modifier = Modifier.height(16.dp))
                        PdfOutcomeRow(known, known.pageCount?.let { PdfOutcome(it, known.sizeBytes + PROTECT_OVERHEAD_BYTES, approximate = true) })
                    }
                }
                if (isProtect) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ToolHint(stringResource(R.string.edit_protect_what_happens))
                }
            }
        }
}

/** About what encryption adds to a file: the encryption dictionary and a little padding per stream. */
private const val PROTECT_OVERHEAD_BYTES = 1_500L

/**
 * How hard [password] is to guess, as bits: its length times log2 of the characters it draws from (lower case, upper
 * case, digits, symbols, anything else). A rough upper bound (a dictionary word scores more than it deserves), so the
 * labels are cautious: under 45 bits weak, under 60 fair, under 80 good, then strong.
 */
internal fun passwordBits(password: String): Int {
    if (password.isEmpty()) return 0
    var pool = 0
    if (password.any { it in 'a'..'z' }) pool += 26
    if (password.any { it in 'A'..'Z' }) pool += 26
    if (password.any { it in '0'..'9' }) pool += 10
    if (password.any { it.code in 33..126 && !it.isLetterOrDigit() }) pool += 33
    if (password.any { it.code > 126 || it == ' ' }) pool += 64
    // The same character repeated ("aaaaaaaa") is one guess per length, whatever the pool.
    val distinct = password.toSet().size
    val length = if (distinct == 1) 1 else password.length
    return (length * kotlin.math.ln(pool.coerceAtLeast(2).toDouble()) / kotlin.math.ln(2.0)).toInt()
}

/** A bar and a word for [password]'s strength, from [passwordBits]. */
@Composable
private fun PasswordStrength(password: String) {
    val bits = passwordBits(password)
    val (label, colour) = when {
        bits < 45 -> R.string.edit_protect_strength_weak to MaterialTheme.colorScheme.error
        bits < 60 -> R.string.edit_protect_strength_fair to androidx.compose.ui.graphics.Color(0xFFB26A00)
        bits < 80 -> R.string.edit_protect_strength_good to androidx.compose.ui.graphics.Color(0xFF1B7F4B)
        else -> R.string.edit_protect_strength_strong to androidx.compose.ui.graphics.Color(0xFF1B7F4B)
    }
    val share = (bits / 100f).coerceIn(0.05f, 1f)
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(Modifier.fillMaxWidth(share).height(6.dp).background(colour))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.edit_protect_strength, stringResource(label), bits),
            style = MaterialTheme.typography.bodySmall,
            color = colour
        )
    }
}

/** Protect PDF on a file that already needs a password to open: says so instead of asking for a new one. */
@Composable
private fun AlreadyProtectedNotice() {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(top = 2.dp).size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(
                    stringResource(R.string.tools_protect_already_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.tools_protect_already_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}
