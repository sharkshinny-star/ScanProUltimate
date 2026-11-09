package com.example.scanpro.core.ui

import androidx.compose.ui.graphics.luminance
import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.rememberMotionEnabled

/** One line of the sheet for a given star count (0 = nothing chosen yet). */
private class RatingCopy(val emoji: String, @StringRes val title: Int, @StringRes val body: Int, @StringRes val label: Int)

private val COPY = listOf(
    RatingCopy("😊", R.string.rate_title, R.string.rate_body, R.string.rate_tap_a_star),
    RatingCopy("😢", R.string.rate_title_1, R.string.rate_body_1, R.string.rate_label_1),
    RatingCopy("😕", R.string.rate_title_2, R.string.rate_body_2, R.string.rate_label_2),
    RatingCopy("🙂", R.string.rate_title_3, R.string.rate_body_3, R.string.rate_label_3),
    RatingCopy("😍", R.string.rate_title_4, R.string.rate_body_4, R.string.rate_label_4),
    RatingCopy("🤩", R.string.rate_title_5, R.string.rate_body_5, R.string.rate_label_5)
)

/**
 * Star gold with enough contrast for an icon (WCAG 3:1) in both themes: #FFB800 is 10.9:1 on the dark window but
 * only 1.7:1 on white, so the light theme uses the deeper gold the favourites mark already uses (3.4:1).
 */
private val Gold: Color
    @Composable get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFFFB800) else Color(0xFFC47A00)

/**
 * The one-time rating sheet, shown after the first saved document (the owner's "ScanPro New Look" design): an
 * emoji that reacts to the stars, five stars with a word under them, and two buttons. Four or five stars go to the
 * Play Store listing; fewer open the feedback email, where a complaint can actually be answered. Either way the
 * sheet is never shown again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingSheet(onNotNow: () -> Unit, onRateOnPlay: () -> Unit, onSendFeedback: (stars: Int) -> Unit) {
    var stars by rememberSaveable { mutableIntStateOf(0) }
    val copy = COPY[stars]
    val motion = rememberMotionEnabled()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val starDescription = stringResource(R.string.rate_stars_content_description)

    ModalBottomSheet(onDismissRequest = onNotNow, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmojiDisc(copy.emoji, gold = stars == 5, motion = motion)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(copy.title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(copy.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp))
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = starDescription },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (value in 1..5) {
                        val on = value <= stars
                        val starLabel = pluralStringResource(R.plurals.rate_star, value, value)
                        val tint by androidx.compose.animation.animateColorAsState(
                            if (on) Gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            tween(if (motion) 180 else 0),
                            label = "star$value"
                        )
                        val bump by animateFloatAsState(
                            if (on && value == stars && motion) 1.18f else 1f,
                            spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
                            label = "bump$value"
                        )
                        IconButton(
                            onClick = { stars = value },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = starLabel; selected = on }
                        ) {
                            Icon(Icons.Rounded.Star, contentDescription = null, tint = tint, modifier = Modifier.size(38.dp).scale(bump))
                        }
                    }
                }
                Text(
                    stringResource(copy.label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (stars > 0) Gold else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onNotNow, modifier = Modifier.weight(1f).height(50.dp)) {
                    Text(stringResource(R.string.rate_not_now))
                }
                Button(
                    onClick = { if (stars >= 4) onRateOnPlay() else onSendFeedback(stars) },
                    enabled = stars > 0,
                    modifier = Modifier.weight(1.8f).height(50.dp)
                ) {
                    Text(
                        stringResource(if (stars in 1..3) R.string.rate_send_feedback else R.string.rate_on_google_play),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** The emoji in its tinted disc: pops when it changes, bobs gently, and turns gold with sparkles on five stars. */
@Composable
private fun EmojiDisc(emoji: String, gold: Boolean, motion: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val tint by androidx.compose.animation.animateColorAsState(if (gold) Gold else primary, tween(if (motion) 400 else 0), label = "disc")
    val pop by animateFloatAsState(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow), label = "pop")
    val bob = if (motion) {
        val transition = rememberInfiniteTransition(label = "bob")
        transition.animateFloat(-2f, 2f, infiniteRepeatable(tween(2800), androidx.compose.animation.core.RepeatMode.Reverse), label = "bobY").value
    } else 0f
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(72.dp)
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.05f))), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Keyed on the emoji so each change starts the pop from small.
            androidx.compose.runtime.key(emoji) {
                val enter by animateFloatAsState(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow), label = "enter")
                Text(
                    emoji,
                    fontSize = 40.sp,
                    modifier = Modifier.graphicsLayer {
                        translationY = bob.dp.toPx()
                        val s = if (motion) enter * pop else 1f
                        scaleX = s
                        scaleY = s
                    }
                )
            }
        }
        if (gold) {
            Sparkle(Modifier.offset(x = (-30).dp, y = (-26).dp), delay = 0, motion = motion)
            Sparkle(Modifier.offset(x = 32.dp, y = (-30).dp), delay = 600, motion = motion)
            Sparkle(Modifier.offset(x = 30.dp, y = 28.dp), delay = 1200, motion = motion)
        }
    }
}

@Composable
private fun Sparkle(modifier: Modifier, delay: Int, motion: Boolean) {
    val animatedAlpha = if (motion) {
        val transition = rememberInfiniteTransition(label = "sparkle")
        transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(900, delayMillis = delay), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "sparkleAlpha"
        )
    } else null
    // Decoration only: without this TalkBack reads "sparkles" out loud.
    Text(
        "✨",
        fontSize = 13.sp,
        modifier = modifier
            .graphicsLayer { alpha = animatedAlpha?.value ?: 1f }
            .clearAndSetSemantics { }
    )
}
