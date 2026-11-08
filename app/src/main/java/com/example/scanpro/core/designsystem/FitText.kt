package com.example.scanpro.core.designsystem

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * One line of text that shrinks, in steps, until it fits the width it is given, instead of being cut with an
 * ellipsis or broken in the middle of a word (R46: long Tamil labels in the bottom bar, the side rail and the
 * document tabs). It stops at [minScale] of the given size and only then ends in an ellipsis, so a label too long
 * even then still shows its start. The size is worked out again when the text changes, for example after a
 * language change.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    minScale: Float = 0.6f
) {
    var scale by remember(text, style.fontSize) { mutableFloatStateOf(1f) }
    val fitted = if (style.fontSize.isSpecified) style.copy(fontSize = style.fontSize * scale) else style
    Text(
        text = text,
        modifier = modifier,
        style = fitted,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            // With an ellipsis, Compose does not report an overflowing width: the line is cut to fit instead, so
            // "ellipsized" is what says the text was too long.
            val tooLong = result.didOverflowWidth || (result.lineCount > 0 && result.isLineEllipsized(0))
            if (tooLong && scale > minScale) scale = (scale * 0.92f).coerceAtLeast(minScale)
        }
    )
}

private val androidx.compose.ui.unit.TextUnit.isSpecified get() = this != androidx.compose.ui.unit.TextUnit.Unspecified
