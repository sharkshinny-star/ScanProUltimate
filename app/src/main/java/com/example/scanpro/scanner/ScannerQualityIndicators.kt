package com.example.scanpro.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R

/** Small, non-interactive diagnostics below the live camera hint, only after a page is found. */
@Composable
internal fun ScannerQualityIndicators(detection: DocumentDetection, modifier: Modifier = Modifier) {
    if (detection.quad == null) return
    val sharp = !detection.isBlurry
    val glareFree = !PageQuality.isGlare(detection.glareShare)
    val level = !detection.isSkewed
    val sharpLabel = stringResource(if (sharp) R.string.scan_check_sharp else R.string.scan_check_blur)
    val glareLabel = stringResource(if (glareFree) R.string.scan_check_clear else R.string.scan_check_glare)
    val levelLabel = stringResource(if (level) R.string.scan_check_level else R.string.scan_check_tilt)
    // One summary for TalkBack; the live guidance above remains the only announced changing element.
    val description = stringResource(R.string.scan_check_summary, sharpLabel, glareLabel, levelLabel)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QualityIndicator(sharpLabel, sharp)
        QualityIndicator(glareLabel, glareFree)
        QualityIndicator(levelLabel, level)
    }
}

@Composable
private fun QualityIndicator(label: String, passed: Boolean) {
    val tint = if (passed) Color(0xFF9BF2CE) else Color(0xFFFFCF80)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
