package com.example.scanpro.feature.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.rememberMotionEnabled
import com.example.scanpro.core.ui.SupportMessage
import com.example.scanpro.core.ui.SupportTopic
import com.example.scanpro.core.ui.contactSupport
import com.example.scanpro.core.ui.openPlayStoreListing
import kotlinx.coroutines.launch

/**
 * "Rate your experience": five faces, a follow-up that depends on the answer, and "Submit feedback", which opens the
 * user's email app with everything filled in (the app sends nothing itself). Afterwards it thanks the user and, for
 * four or five, offers the Google Play listing. Every way out calls [onClose].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateExperienceSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var reasons by rememberSaveable { mutableStateOf(emptyList<FeedbackReason>()) }
    var text by rememberSaveable { mutableStateOf("") }
    var sending by rememberSaveable { mutableStateOf(false) }
    // The delivery confirmation once sent; the sheet then shows its thank-you.
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        val sent = confirmation
        if (sent == null) {
            RateExperienceContent(
                rating = rating,
                reasons = reasons.toSet(),
                text = text,
                sending = sending,
                onRate = { value ->
                    // Low and high ratings ask different things; answers to the other question no longer apply.
                    if (reasonsFor(value) != reasonsFor(rating)) reasons = emptyList()
                    rating = value
                },
                onToggleReason = { reason -> reasons = if (reason in reasons) reasons - reason else reasons + reason },
                onTextChange = { text = it },
                onSubmit = {
                    val mood = Mood.of(rating) ?: return@RateExperienceContent
                    sending = true
                    scope.launch {
                        val moodLabel = context.getString(mood.label)
                        val delivery = contactSupport(
                            context,
                            SupportTopic.FEEDBACK,
                            SupportMessage(
                                subject = context.getString(R.string.feedback_email_subject, mood.rating, moodLabel),
                                text = ratingEmailText(
                                    ratingLine = context.getString(R.string.feedback_email_rating, mood.rating, moodLabel),
                                    question = context.getString(followUpQuestion(mood.rating)),
                                    reasons = reasonsFor(mood.rating).filter { it in reasons }.map { context.getString(it.label) },
                                    commentsLabel = context.getString(R.string.feedback_email_comments),
                                    comments = text
                                )
                            )
                        )
                        sending = false
                        confirmation = delivery.confirmation(context)
                    }
                },
                onNotNow = { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } }
            )
        } else {
            RateThanksContent(
                rating = rating,
                confirmation = sent,
                onRateOnPlay = {
                    openPlayStoreListing(context)
                    onClose()
                },
                onDone = { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } }
            )
        }
    }
}

/** The sheet's question, faces, follow-up and buttons, without the sheet (also used by the screenshot tests). */
@Composable
fun RateExperienceContent(
    rating: Int,
    reasons: Set<FeedbackReason>,
    text: String,
    sending: Boolean = false,
    onRate: (Int) -> Unit,
    onToggleReason: (FeedbackReason) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNotNow: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
    ) {
        Text(
            stringResource(R.string.feedback_rate_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.feedback_rate_question),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            FaceRow(rating = rating, onRate = onRate)
        }

        AnimatedVisibility(
            visible = rating > 0,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(followUpQuestion(rating.coerceAtLeast(1))),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    stringResource(R.string.feedback_choose_any),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reasonsFor(rating).forEach { reason ->
                        val on = reason in reasons
                        FilterChip(
                            selected = on,
                            onClick = { onToggleReason(reason) },
                            label = { Text(stringResource(reason.label)) },
                            leadingIcon = if (on) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val needsDetail = asksForDetail(reasons)
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(if (needsDetail) R.string.feedback_tell_more else R.string.feedback_tell_more_optional)) },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSubmit,
            enabled = !sending && canSubmitRating(rating, reasons, text),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (sending) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.feedback_submit), fontWeight = FontWeight.Bold)
            }
        }
        Text(
            stringResource(R.string.feedback_submit_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
        TextButton(onClick = onNotNow, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.feedback_not_now))
        }
    }
}

/** Five faces; the chosen one fills with colour, grows a little and shows its word. A radio group for TalkBack. */
@Composable
private fun FaceRow(rating: Int, onRate: (Int) -> Unit) {
    val motion = rememberMotionEnabled()
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Mood.entries.forEach { mood ->
            val selected = mood.rating == rating
            val label = stringResource(mood.label)
            val description = stringResource(R.string.feedback_face_description, label, mood.rating)
            val grow by animateFloatAsState(
                if (selected) 1.14f else 1f,
                if (motion) spring(dampingRatio = 0.5f) else tween(0),
                label = "face${mood.rating}"
            )
            Column(
                Modifier
                    .weight(1f)
                    .selectable(selected = selected, onClick = { onRate(mood.rating) }, role = Role.RadioButton)
                    .semantics { contentDescription = description }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    MoodFace(mood, selected, outline, Modifier.size(46.dp).scale(grow))
                }
                Spacer(Modifier.height(4.dp))
                // Only the chosen face says its word; the others keep the space so nothing jumps.
                Text(
                    if (selected) label else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }
        }
    }
}

/** After sending: thanks, what happened to the message, and for a four or five the Google Play listing. */
@Composable
fun RateThanksContent(rating: Int, confirmation: String, onRateOnPlay: () -> Unit, onDone: () -> Unit) {
    val happy = rating >= 4
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (happy) Icons.Rounded.Favorite else Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.feedback_thanks_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (happy) R.string.feedback_play_body else R.string.feedback_thanks_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            confirmation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
        Spacer(Modifier.height(18.dp))
        if (happy) {
            Button(onClick = onRateOnPlay, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.feedback_rate_on_play), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDone) { Text(stringResource(R.string.feedback_done)) }
        } else {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.feedback_done), fontWeight = FontWeight.Bold)
            }
        }
    }
}
