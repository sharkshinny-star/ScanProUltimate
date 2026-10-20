package com.example.scanpro.feature.rating

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val StarGold = Color(0xFFFFB800)

private data class RatingCopy(val emoji: String, val title: String, val message: String, val label: String)

private val Prompt = RatingCopy(
    emoji = "😊",
    title = "Enjoying ScanPro?",
    message = "Tap a star to tell us how it's going. It only takes a moment.",
    label = "Tap to rate"
)

private val ByRating = listOf(
    RatingCopy("😞", "We're sorry to hear that", "Tell us on Google Play what went wrong. We read every review and use it to improve.", "Not good"),
    RatingCopy("😕", "Thanks for being honest", "Let us know on Google Play what we should fix. Your feedback shapes the next update.", "Could be better"),
    RatingCopy("🙂", "Thanks for the feedback!", "A quick review on Google Play tells us what to improve next.", "It's okay"),
    RatingCopy("😄", "Glad you like it!", "A quick review on Google Play helps other people find ScanPro.", "Really good"),
    RatingCopy("🥰", "Much appreciated!", "Your support is our biggest motivation. A review on Google Play helps others find ScanPro.", "Love it!")
)

/**
 * Asks for a Google Play review. Every rating leads to the same Play button: Play policy forbids only
 * sending happy users to the store ("review gating"), so the stars set the tone and nothing more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatePromptSheet(
    onRate: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var rating by rememberSaveable { mutableIntStateOf(0) }

    // Close with the slide-down animation before telling the caller, so the sheet doesn't just vanish.
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        RatePromptContent(
            rating = rating,
            onRatingChange = { rating = it },
            onRate = { close(onRate) },
            onLater = { close(onDismiss) }
        )
    }
}

@Composable
internal fun RatePromptContent(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    onRate: () -> Unit,
    onLater: () -> Unit
) {
    val copy = if (rating == 0) Prompt else ByRating[rating - 1]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmojiHalo(emoji = copy.emoji, celebrating = rating >= 4)
        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = copy,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "ratingCopy"
        ) { shown ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    shown.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    shown.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        StarRow(rating = rating, onRate = onRatingChange)
        Spacer(Modifier.height(8.dp))
        Text(
            copy.label,
            modifier = Modifier.height(20.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (rating == 0) MaterialTheme.colorScheme.onSurfaceVariant else StarGold,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRate,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Rate on Google Play", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        TextButton(onClick = onLater, modifier = Modifier.padding(top = 4.dp)) {
            Text("Maybe later", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The emoji on a soft glow, gently bobbing; hearts drift up around it for 4 and 5 stars. */
@Composable
private fun EmojiHalo(emoji: String, celebrating: Boolean) {
    val float = rememberInfiniteTransition(label = "emojiFloat")
    val bob by float.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val heartRise by float.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "hearts"
    )
    val glow by animateColorAsState(
        if (celebrating) StarGold.copy(alpha = 0.30f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        tween(400),
        label = "glow"
    )

    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(120.dp)
                .background(Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f))), CircleShape)
        )
        AnimatedContent(
            targetState = emoji,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.6f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.6f) + fadeOut())
            },
            label = "emoji"
        ) { shown ->
            Text(shown, fontSize = 64.sp, modifier = Modifier.offset(y = bob.dp))
        }
        if (celebrating) {
            // Three hearts on staggered phases of one loop, each rising and fading out.
            listOf(Triple(-44, 0f, 15), Triple(42, 0.33f, 13), Triple(-6, 0.66f, 11)).forEach { (x, phase, size) ->
                val t = (heartRise + phase) % 1f
                Text(
                    "❤️",
                    fontSize = size.sp,
                    modifier = Modifier
                        .offset(x = x.dp, y = (20 - 70 * t).dp)
                        .alpha(if (t < 0.15f) t / 0.15f else 1f - (t - 0.15f) / 0.85f)
                )
            }
        }
    }
}

@Composable
private fun StarRow(rating: Int, onRate: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (value in 1..5) {
            AnimatedStar(
                value = value,
                filled = value <= rating,
                rating = rating,
                onClick = { onRate(value) }
            )
        }
    }
}

@Composable
private fun AnimatedStar(value: Int, filled: Boolean, rating: Int, onClick: () -> Unit) {
    // Pops in one after another when the sheet opens, then bounces in a wave up to the chosen star.
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80L * value)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(rating) {
        if (rating == 0 || !filled) return@LaunchedEffect
        delay(45L * (value - 1))
        scale.animateTo(1.28f, tween(110))
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    val tint by animateColorAsState(
        if (filled) StarGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        label = "starTint"
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            .scale(scale.value)
            .selectable(
                selected = filled,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 26.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = "$value star${if (value == 1) "" else "s"}",
            tint = tint,
            modifier = Modifier.size(44.dp)
        )
    }
}
