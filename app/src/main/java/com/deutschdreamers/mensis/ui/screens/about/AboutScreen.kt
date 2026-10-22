package com.deutschdreamers.mensis.ui.screens.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.deutschdreamers.mensis.BuildConfig
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.ui.components.ContentCard
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.components.MsIcon
import com.deutschdreamers.mensis.ui.components.SettingsRow
import com.deutschdreamers.mensis.ui.navigation.NoticeDocument
import com.deutschdreamers.mensis.ui.theme.MensisTheme
import com.deutschdreamers.mensis.ui.theme.SerifStyles

/** Design §17 "About and privacy". */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNotices: (NoticeDocument) -> Unit,
) {
    val context = LocalContext.current
    AboutContent(
        version = BuildConfig.VERSION_NAME,
        onOpenLink = { url -> openLink(context, url) },
        onNotices = onNotices,
        onBack = onBack,
    )
}

private fun openLink(
    context: Context,
    url: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (expected: ActivityNotFoundException) {
        Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AboutContent(
    version: String,
    onOpenLink: (String) -> Unit,
    onNotices: (NoticeDocument) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { MensisTopBar(title = stringResource(R.string.settings_about), onBack = onBack) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero(version)
            CreatedBy(onSource = { onOpenLink(AboutLinks.SOURCE) })
            WhatLeaves()
            Column {
                SettingsRow(
                    icon = R.drawable.ms_gavel,
                    title = stringResource(R.string.about_licence),
                    supporting = stringResource(R.string.about_licence_desc),
                    horizontalPadding = 0.dp,
                    onClick = { onNotices(NoticeDocument.LICENCE) },
                )
                SettingsRow(
                    icon = R.drawable.ms_library_books,
                    title = stringResource(R.string.about_libraries),
                    supporting = stringResource(R.string.about_libraries_desc),
                    horizontalPadding = 0.dp,
                    onClick = { onNotices(NoticeDocument.LIBRARIES) },
                )
                SettingsRow(
                    icon = R.drawable.ms_policy,
                    title = stringResource(R.string.about_privacy_policy),
                    supporting = stringResource(R.string.about_privacy_policy_desc),
                    horizontalPadding = 0.dp,
                    trailingIcon = R.drawable.ms_open_in_new,
                    onClick = { onOpenLink(AboutLinks.PRIVACY_POLICY) },
                )
                SettingsRow(
                    icon = R.drawable.ms_bug_report,
                    title = stringResource(R.string.about_report),
                    supporting = stringResource(R.string.about_report_desc),
                    horizontalPadding = 0.dp,
                    trailingIcon = R.drawable.ms_open_in_new,
                    onClick = { onOpenLink(AboutLinks.ISSUES) },
                )
            }
            Text(
                text = stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.about_copyright),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Hero(version: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MsIcon(
                resId = R.drawable.ms_timeline_fill,
                contentDescription = null,
                size = 48.dp,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = SerifStyles.wordmark,
            modifier = Modifier.padding(top = 8.dp),
        )
        Box(
            modifier =
                Modifier
                    .height(32.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.about_version, version),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }
}

@Composable
private fun CreatedBy(onSource: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.about_created_by),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.about_author),
            style = SerifStyles.statSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = madeWith(stringResource(R.string.about_made_with)), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onSource, modifier = Modifier.padding(top = 8.dp)) {
            MsIcon(resId = R.drawable.ms_code, contentDescription = null, size = 20.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.about_source))
            Spacer(modifier = Modifier.width(8.dp))
            MsIcon(resId = R.drawable.ms_open_in_new, contentDescription = null, size = 18.dp)
        }
    }
}

/** The heart in "Made with ♥ in …" takes the period colour, as in the design. */
@Composable
private fun madeWith(text: String) =
    buildAnnotatedString {
        val heart = text.indexOf(HEART)
        if (heart < 0) {
            append(text)
        } else {
            append(text.substring(0, heart))
            withStyle(SpanStyle(color = MensisTheme.colors.period)) { append(HEART) }
            append(text.substring(heart + HEART.length))
        }
    }

@Composable
private fun WhatLeaves() {
    ContentCard {
        Text(
            text = stringResource(R.string.about_leaves_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
        )
        LeavesRow(R.drawable.ms_check_circle, MensisTheme.colors.fertile, stringResource(R.string.about_leaves_nothing))
        LeavesRow(
            R.drawable.ms_cloud_upload,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.about_leaves_backup),
        )
        LeavesRow(
            R.drawable.ms_share,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.about_leaves_export),
        )
    }
}

@Composable
private fun LeavesRow(
    icon: Int,
    tint: Color,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MsIcon(resId = icon, contentDescription = null, size = 20.dp, tint = tint)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private const val HEART = "♥"
