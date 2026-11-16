package com.example.scanpro.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The card every first-time introduction is drawn on (F146): rounded, over the dimmed screen, and rising in with a
 * soft spring as it opens. [content] is told whether the card has entered, so its parts can step in after it. With
 * animations turned off in the system settings everything is simply there. Scrolls when the window is short.
 */
@Composable
fun IllustratedDialogFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.(entered: Boolean) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val motion = rememberMotionEnabled()
        var entered by remember { mutableStateOf(!motion) }
        LaunchedEffect(Unit) { entered = true }
        val appear by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = if (motion) spring(dampingRatio = 0.82f, stiffness = 420f) else snap(),
            label = "introAppear"
        )
        // The height the dialog may take decides how tall the picture is (a phone on its side gets a shorter one).
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalShortDialog provides (maxHeight < 560.dp)) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = appear.coerceIn(0f, 1f)
                            val scale = 0.9f + 0.1f * appear
                            scaleX = scale
                            scaleY = scale
                            translationY = (1f - appear) * 28.dp.toPx()
                        }
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { content(entered) }
                }
            }
        }
    }
}

/**
 * Gives a flat illustration depth (owner request, 2026-11-16): it is tilted back in perspective like an object lying on
 * a table seen from above, floats and turns slowly, and casts a soft shadow on the panel that tightens as it rises.
 * All of it is one graphics layer and one oval, read while drawing, so nothing recomposes; still when animations are off.
 */
@Composable
private fun BoxScope.Stage3D(content: @Composable BoxScope.() -> Unit) {
    val motion = rememberMotionEnabled()
    val drift = if (motion) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "stage3d").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                tween(3_600, easing = FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "drift"
        )
    } else null
    val shadowColour = if (isDarkTheme()) Color.Black.copy(alpha = 0.55f) else Color(0xFF1B1F3A).copy(alpha = 0.22f)
    Box(
        Modifier
            .matchParentSize()
            .drawBehind {
                val t = drift?.value ?: 0f
                // The shadow sits on the "table" under the object: smaller and darker when it is low, wider when it lifts.
                val lift = (t + 1f) / 2f
                val w = size.width * (0.46f + 0.08f * lift)
                val h = size.height * 0.10f
                val centre = Offset(size.width / 2f + size.width * 0.03f * t, size.height * 0.90f)
                drawOval(
                    Brush.radialGradient(
                        listOf(shadowColour.copy(alpha = shadowColour.alpha * (1f - 0.35f * lift)), Color.Transparent),
                        center = centre,
                        radius = w / 2f
                    ),
                    topLeft = Offset(centre.x - w / 2f, centre.y - h / 2f),
                    size = androidx.compose.ui.geometry.Size(w, h)
                )
            }
            .graphicsLayer {
                val t = drift?.value ?: 0f
                cameraDistance = 7f * density
                rotationX = 20f + 3f * t
                rotationY = -12f * t
                rotationZ = -2f
                translationY = -(6.dp.toPx()) * ((t + 1f) / 2f) - 4.dp.toPx()
                scaleX = 0.92f
                scaleY = 0.92f
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * The first sentence of [text], for popups that say one thing (owner request: shorter text). Works in every app
 * language: Latin full stops and the Devanagari danda both end a sentence; a text with neither is returned whole.
 */
fun firstSentence(text: String): String {
    val end = Regex("""[.!?।](\s|$)""").find(text) ?: return text
    return text.substring(0, end.range.first + 1)
}

/** True inside an [IllustratedDialogFrame] that has less than 560 dp of height to work with. */
private val LocalShortDialog = staticCompositionLocalOf { false }

/**
 * The picture at the top of an introduction: a panel washed with the brand colours that fades into the card, holding
 * an animated illustration and, optionally, an icon badge in the corner like the welcome's. Shorter in a short window
 * (a phone on its side), so the text and buttons still fit.
 */
@Composable
fun IllustrationPanel(
    modifier: Modifier = Modifier,
    badgeIcon: ImageVector? = null,
    badgeTone: TileTone = TileTone.Indigo,
    content: @Composable BoxScope.() -> Unit
) {
    val brand = ScanProTheme.colors
    val card = MaterialTheme.colorScheme.surfaceContainerHigh
    val strength = if (isDarkTheme()) 0.30f else 0.18f
    Box(
        modifier
            .fillMaxWidth()
            .height(illustrationHeight())
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(brand.gradientStart.copy(alpha = strength), brand.gradientEnd.copy(alpha = strength * 0.6f), card)
                    )
                )
                val radius = size.width * 0.42f
                val glow = Offset(size.width * 0.84f, size.height * 0.16f)
                drawCircle(
                    Brush.radialGradient(listOf(brand.scanLine.copy(alpha = strength), Color.Transparent), center = glow, radius = radius),
                    radius = radius,
                    center = glow
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Stage3D(content)
        if (badgeIcon != null) {
            IconTile(
                badgeIcon,
                badgeTone,
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 10.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp)),
                size = 48.dp,
                corner = 16.dp,
                iconSize = 24.dp
            )
        }
    }
}

@Composable
private fun illustrationHeight(): Dp = if (LocalShortDialog.current) 112.dp else 176.dp

/**
 * A first-time introduction: [illustration] on its panel, an optional eyebrow, the title and a line or two of text,
 * optional numbered [steps] that step in one after another, any [extra] content (a "Don't remind me again" box), the
 * main button and, if [dismissText] is given, a quieter one that closes it. Back and a tap outside call [onDismiss].
 */
@Composable
fun IllustratedDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    illustration: @Composable BoxScope.() -> Unit,
    eyebrow: String? = null,
    steps: List<String> = emptyList(),
    badgeIcon: ImageVector? = null,
    badgeTone: TileTone = TileTone.Indigo,
    dismissText: String? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null
) {
    IllustratedDialogFrame(onDismiss = onDismiss) { entered ->
        IllustrationPanel(badgeIcon = badgeIcon, badgeTone = badgeTone, content = illustration)
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 16.dp)) {
            if (eyebrow != null) {
                Eyebrow(eyebrow)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(8.dp))
            Text(firstSentence(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (steps.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HintSteps(steps, entered)
            }
            extra?.invoke(this)
            Spacer(Modifier.height(20.dp))
            GradientButton(text = confirmText, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            if (dismissText != null) {
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(dismissText) }
            }
        }
    }
}

/** Numbered steps, each read out as one item ("1, Choose a PDF") and stepping in just after the card. */
@Composable
fun HintSteps(steps: List<String>, entered: Boolean) {
    val motion = rememberMotionEnabled()
    Column {
        steps.forEachIndexed { index, step ->
            val reveal by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = if (motion) tween(320, delayMillis = 180 + index * 90, easing = FastOutSlowInEasing) else snap(),
                label = "hintStep$index"
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = reveal
                        translationX = (1f - reveal) * 16.dp.toPx()
                    }
                    .semantics(mergeDescendants = true) { }
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}
