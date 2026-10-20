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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
    title = "How's ScanPro working for you?",
    message = "Your rating helps us make every scan better.",
    label = "Tap a star"
)

private val ByRating = listOf(
    RatingCopy("😢", "Sorry we let you down", "Tell us what went wrong on Google Play.", "Poor"),
    RatingCopy("😕", "We can do better", "Tell us what to fix on Google Play.", "Fair"),
    RatingCopy("🙂", "Thanks for the feedback", "A quick review shows us what to improve.", "Good"),
    RatingCopy("😍", "Glad you like it!", "A quick review helps others find ScanPro.", "Great"),
    RatingCopy("🤩", "You made our day!", "Share the love with a quick review.", "Amazing!")
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
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmojiBadge(emoji = copy.emoji, celebrating = rating == 5)
        Spacer(Modifier.height(12.dp))

        AnimatedContent(
            targetState = copy,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "ratingCopy"
        ) { shown ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    shown.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    shown.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        // Stars sit on a tinted tray, with the rating's word tucked underneath.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StarRow(rating = rating, onRate = onRatingChange)
            Text(
                copy.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (rating == 0) MaterialTheme.colorScheme.onSurfaceVariant else StarGold,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = onLater,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f).height(50.dp),
                shape = CircleShape
            ) {
                Text("Not now", maxLines = 1)
            }
            Button(
                onClick = onRate,
                modifier = Modifier.weight(1.8f).height(50.dp),
                shape = CircleShape
            ) {
                Text("Rate on Google Play", maxLines = 1, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The emoji on a soft gradient disc, gently bobbing; sparkles twinkle around it at five stars. */
@Composable
private fun EmojiBadge(emoji: String, celebrating: Boolean) {
    val loop = rememberInfiniteTransition(label = "emojiLoop")
    val bob by loop.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val twinkle by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "twinkle"
    )
    val discTop by animateColorAsState(
        if (celebrating) StarGold.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        tween(400),
        label = "discTop"
    )
    val discBottom = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)

    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(72.dp)
                .background(Brush.linearGradient(listOf(discTop, discBottom)), CircleShape)
        )
        AnimatedContent(
            targetState = emoji,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.5f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.5f) + fadeOut())
            },
            label = "emoji"
        ) { shown ->
            Text(shown, fontSize = 42.sp, modifier = Modifier.offset(y = bob.dp))
        }
        if (celebrating) {
            // Three sparkles on staggered phases of one loop, each fading in and out.
            listOf(Triple(-34, -26, 0f), Triple(34, -20, 0.33f), Triple(30, 28, 0.66f)).forEach { (x, y, phase) ->
                val t = (twinkle + phase) % 1f
                val pulse = if (t < 0.5f) t * 2 else (1f - t) * 2
                Text(
                    "✨",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .offset(x = x.dp, y = y.dp)
                        .scale(0.6f + 0.4f * pulse)
                        .alpha(pulse)
                )
            }
        }
    }
}

@Composable
private fun StarRow(rating: Int, onRate: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
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
            .size(48.dp)
            .scale(scale.value)
            .selectable(
                selected = filled,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = "$value star${if (value == 1) "" else "s"}",
            tint = tint,
            modifier = Modifier.size(38.dp)
        )
    }
}
