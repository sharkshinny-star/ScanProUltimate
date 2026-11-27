package com.example.scanpro.feature.feedback

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.BrandMark
import com.example.scanpro.core.designsystem.rememberMotionEnabled
import com.example.scanpro.core.ui.openPlayStoreListing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STAGE_ASK = 0
private const val STAGE_PROBLEM = 1
private const val STAGE_THANKS = 2

/**
 * The rating sheet, shown after a scan was saved (see [com.example.scanpro.data.FeedbackSchedule]) and from Settings.
 * It asks one thing first: five stars. Four or five are thanked at once, with the Google Play listing on offer. One to
 * three open "Tell us the problem you encountered" (the issue form: topics, a description, screenshots), whose Submit
 * opens the user's email app with everything filled in; the app sends nothing itself. Every way out calls [onClose].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateExperienceSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val motion = rememberMotionEnabled()
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var stage by rememberSaveable { mutableIntStateOf(STAGE_ASK) }
    // What happened to the problem report once it was handed to the email app; null after a four or five.
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    val hide: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } }

    // The stars fill and are seen for a moment before the sheet moves on; another tap in that moment starts it again.
    LaunchedEffect(rating) {
        val next = followUpFor(rating) ?: return@LaunchedEffect
        if (stage != STAGE_ASK) return@LaunchedEffect
        delay(if (motion) STARS_SHOWN_MS else 120L)
        stage = if (next == RatingFollowUp.THANKS) STAGE_THANKS else STAGE_PROBLEM
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                if (motion) fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90))
                else EnterTransition.None togetherWith ExitTransition.None
            },
            label = "rateStage"
        ) { shown ->
            when (shown) {
                STAGE_ASK -> RatePromptContent(rating = rating, onRate = { rating = it }, onNotNow = hide)
                STAGE_PROBLEM -> {
                    val mood = Mood.of(rating)
                    FeedbackFormHost(
                        kind = FeedbackFormKind.ISSUE,
                        title = stringResource(R.string.feedback_form_issue_heading),
                        // The report says which rating it came with.
                        preface = mood?.let { resources.getString(R.string.feedback_email_rating, it.rating, resources.getString(it.label)) },
                        onSent = { delivery ->
                            confirmation = delivery.confirmation(context)
                            stage = STAGE_THANKS
                        },
                        onClose = hide
                    )
                }
                else -> RateThanksContent(
                    rating = rating,
                    confirmation = confirmation,
                    onRateOnPlay = {
                        openPlayStoreListing(context)
                        onClose()
                    },
                    onDone = hide
                )
            }
        }
    }
}

/** How long the filled stars stay before the next step, long enough for the last star to finish filling. */
private const val STARS_SHOWN_MS = 650L

/** The first step, without the sheet (also drawn by the screenshot tests): the app's mark, the question, five stars. */
@Composable
fun RatePromptContent(rating: Int, onRate: (Int) -> Unit, onNotNow: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandMark(size = 64.dp, modifier = Modifier.clearAndSetSemantics { })
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.feedback_enjoying_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.feedback_enjoying_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        StarRow(rating = rating, onRate = onRate)
        // The word for the chosen rating. The line keeps its height while empty, so nothing below it jumps.
        Text(
            Mood.of(rating)?.let { stringResource(it.label) }.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics { }
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onNotNow) { Text(stringResource(R.string.feedback_not_now)) }
    }
}

/** Five stars; tapping one fills it and those before it, one after another. A radio group for TalkBack. */
@Composable
private fun StarRow(rating: Int, onRate: (Int) -> Unit) {
    val motion = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current
    // A true gold on a light sheet (the theme's own star colour is a brown there, chosen for small stars beside text);
    // on a dark sheet the theme's is already a warm yellow. A filled star also differs from an empty one in shape.
    val scheme = MaterialTheme.colorScheme
    val gold = if (scheme.surface.luminance() > 0.5f) androidx.compose.ui.graphics.Color(0xFFF0A202) else scheme.tertiary
    val empty = MaterialTheme.colorScheme.outline
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Mood.entries.forEach { mood ->
            val filled = mood.rating <= rating
            val description = stringResource(R.string.feedback_face_description, stringResource(mood.label), mood.rating)
            val fill by animateFloatAsState(
                targetValue = if (filled) 1f else 0f,
                animationSpec = when {
                    !motion -> snap()
                    // Left to right, each star a beat after the one before it.
                    filled -> tween(220, delayMillis = (mood.rating - 1) * 55)
                    else -> tween(120)
                },
                label = "star${mood.rating}"
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(
                        selected = mood.rating == rating,
                        role = Role.RadioButton,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRate(mood.rating)
                        }
                    )
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (fill > 0f) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = lerp(empty, gold, fill),
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            // A small swell while it fills, back to its own size once full.
                            val swell = 1f + 0.2f * kotlin.math.sin(fill * Math.PI).toFloat()
                            scaleX = swell
                            scaleY = swell
                        }
                )
            }
        }
    }
}

/**
 * The last step. After four or five stars: "Thank you for your rating!" and the Google Play listing. After a problem
 * report: thanks, and [confirmation] saying what happened to the message.
 */
@Composable
fun RateThanksContent(rating: Int, confirmation: String?, onRateOnPlay: () -> Unit, onDone: () -> Unit) {
    val happy = followUpFor(rating) == RatingFollowUp.THANKS
    Column(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (happy) {
            // Folded hands, as a thank-you is drawn in most messaging apps; decoration only.
            Text("🙏", fontSize = 52.sp, modifier = Modifier.clearAndSetSemantics { })
        } else {
            Box(
                Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(if (happy) R.string.feedback_thanks_rating_title else R.string.feedback_thanks_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(if (happy) R.string.feedback_play_body else R.string.feedback_thanks_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (confirmation != null) {
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
        }
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
