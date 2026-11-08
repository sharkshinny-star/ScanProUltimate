package com.example.scanpro.feature.library

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.Eyebrow
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.data.ScanMode
import com.example.scanpro.navigation.ScanProDestinations
import java.util.Calendar

/** Where a tip's button leads. */
internal sealed interface TipAction {
    /** Opens the camera in a mode: a [ScanMode] name or [ScanProDestinations.CAMERA_QR]. */
    data class Camera(val mode: String) : TipAction
    data object Tools : TipAction
    data object Search : TipAction
    data object Settings : TipAction
}

internal class HomeTip(
    val icon: ImageVector,
    val tone: TileTone,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val action: Int,
    val target: TipAction
)

/** Features people tend not to find on their own. Every tip leads straight to the feature it describes. */
internal val homeTips = listOf(
    HomeTip(Icons.Rounded.QrCodeScanner, TileTone.Violet, R.string.tip_qr_title, R.string.tip_qr_body, R.string.tip_qr_action, TipAction.Camera(ScanProDestinations.CAMERA_QR)),
    HomeTip(Icons.Rounded.TextFields, TileTone.Teal, R.string.tip_text_title, R.string.tip_text_body, R.string.tip_text_action, TipAction.Camera(ScanMode.TEXT.name)),
    HomeTip(Icons.Rounded.Badge, TileTone.Indigo, R.string.tip_id_title, R.string.tip_id_body, R.string.tip_id_action, TipAction.Camera(ScanMode.ID_CARD.name)),
    HomeTip(Icons.AutoMirrored.Rounded.MenuBook, TileTone.Amber, R.string.tip_book_title, R.string.tip_book_body, R.string.tip_book_action, TipAction.Camera(ScanMode.BOOK.name)),
    HomeTip(Icons.Rounded.Handyman, TileTone.Coral, R.string.tip_tools_title, R.string.tip_tools_body, R.string.tip_tools_action, TipAction.Tools),
    HomeTip(Icons.Rounded.Search, TileTone.Sky, R.string.tip_search_title, R.string.tip_search_body, R.string.tip_search_action, TipAction.Search),
    HomeTip(Icons.Rounded.Backup, TileTone.Slate, R.string.tip_backup_title, R.string.tip_backup_body, R.string.tip_backup_action, TipAction.Settings),
    HomeTip(Icons.Rounded.ColorLens, TileTone.Rose, R.string.tip_theme_title, R.string.tip_theme_body, R.string.tip_theme_action, TipAction.Settings)
)

/** Today's tip: a different one each day, the same one all day. */
internal fun tipOfTheDay(dayOfYear: Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)): Int = dayOfYear.mod(homeTips.size)

/**
 * The "Did you know?" card on Home: one tip with a button that goes to the feature, "Next" to see
 * another, and a close button that hides the card for good (Settings > Appearance brings it back).
 */
@Composable
internal fun HomeTipCard(onAction: (TipAction) -> Unit, onHide: () -> Unit, modifier: Modifier = Modifier) {
    var index by rememberSaveable { mutableIntStateOf(tipOfTheDay()) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TipsAndUpdates, contentDescription = null, tint = TileTone.Amber.content(), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Eyebrow(stringResource(R.string.tip_eyebrow), color = TileTone.Amber.content(), modifier = Modifier.weight(1f))
                IconButton(onClick = onHide) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.tip_cd_hide), modifier = Modifier.size(20.dp))
                }
            }
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(160)))
                },
                label = "tip"
            ) { shown ->
                val tip = homeTips[shown]
                Column(Modifier.padding(end = 12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        IconTile(tip.icon, tip.tone, size = 44.dp, corner = 14.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(tip.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(tip.body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 58.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onAction(tip.target) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                        ) {
                            Text(
                                stringResource(tip.action),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { index = (index + 1) % homeTips.size },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                        ) {
                            Text(
                                stringResource(R.string.tip_next),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
