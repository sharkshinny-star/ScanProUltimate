package com.example.scanpro.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scanpro.BuildConfig
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.BrandMark
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.ui.openPlayStoreListing
import com.example.scanpro.core.ui.sharePlayStoreListing

/** A library ScanPro ships with and its licence. Names and licence titles are proper names, so they are not translated. */
private class Credit(val name: String, val licence: String)

private const val APACHE_2 = "Apache License 2.0"

/** What the app is built with; keep in step with app/build.gradle.kts. */
private val CREDITS = listOf(
    Credit("OpenCV", APACHE_2),
    Credit("PdfBox-Android", APACHE_2),
    Credit("ZXing", APACHE_2),
    Credit("Android Jetpack (Compose, CameraX, Room, WorkManager)", APACHE_2),
    Credit("Kotlin and kotlinx.coroutines", APACHE_2),
    Credit("Dagger Hilt", APACHE_2),
    Credit("Google ML Kit", "ML Kit Terms of Service"),
    // The fonts Edit PDF carries (F207); their licence texts ship in assets/licences.
    Credit("Roboto font", APACHE_2),
    Credit("Inter, Montserrat and Gelasio fonts", "SIL Open Font License 1.1")
)

/** About ScanPro: who the app is, its version, how it treats your data, sharing, and the software it is built on. */
@Composable
internal fun AboutPage(onOpenPrivacy: () -> Unit) = SettingsPageColumn {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BrandMark(size = 64.dp)
        Text(
            stringResource(R.string.core_recommend_app_subject),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp).semantics { heading() }
        )
        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.settings_about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    SettingsGroup {
        SettingsRow(
            title = stringResource(R.string.settings_privacy_title),
            icon = Icons.Rounded.Lock,
            tone = TileTone.Rose,
            summary = stringResource(R.string.settings_footer_privacy),
            trailing = { Chevron() },
            onClick = onOpenPrivacy
        )
        SettingsRow(
            title = stringResource(R.string.settings_share_with_friends),
            icon = Icons.Rounded.Share,
            tone = TileTone.Coral,
            summary = stringResource(R.string.settings_share_subtitle),
            onClick = { sharePlayStoreListing(context) }
        )
        SettingsRow(
            title = stringResource(R.string.settings_rate_title),
            icon = Icons.Rounded.Star,
            tone = TileTone.Amber,
            summary = stringResource(R.string.settings_rate_subtitle),
            onClick = { openPlayStoreListing(context) }
        )
    }

    SettingsGroup(header = stringResource(R.string.settings_about_open_source)) {
        Text(
            stringResource(R.string.settings_about_open_source_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        CREDITS.forEach { credit ->
            SettingsRow(title = credit.name, icon = Icons.Rounded.Code, summary = credit.licence, summaryMaxLines = 1)
        }
    }
}
