package com.deutschdreamers.mensis.ui.screens.about

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.deutschdreamers.mensis.R
import com.deutschdreamers.mensis.ui.components.MensisTopBar
import com.deutschdreamers.mensis.ui.navigation.NoticeDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * About → Licence (the MIT licence) and Open-source libraries (THIRD_PARTY_NOTICES.md, followed by
 * the full licence texts it refers to). The texts are bundled as raw resources at build time.
 */
@Composable
fun NoticesScreen(
    document: NoticeDocument,
    onBack: () -> Unit,
) {
    val resources = LocalResources.current
    val headings =
        listOf(
            stringResource(R.string.notices_apache),
            stringResource(R.string.notices_ofl_roboto),
            stringResource(R.string.notices_ofl_serif),
        )
    val blocks by produceState<List<NoticeBlock>?>(null, document) {
        value = withContext(Dispatchers.IO) { load(resources, document, headings) }
    }
    NoticesContent(
        title =
            stringResource(
                if (document == NoticeDocument.LICENCE) R.string.about_licence else R.string.about_libraries,
            ),
        blocks = blocks,
        onBack = onBack,
    )
}

private fun load(
    resources: Resources,
    document: NoticeDocument,
    headings: List<String>,
): List<NoticeBlock> {
    fun read(id: Int) = resources.openRawResource(id).bufferedReader().use { it.readText() }
    return when (document) {
        NoticeDocument.LICENCE -> NoticeText.parsePlain(read(R.raw.licence_mit))
        NoticeDocument.LIBRARIES ->
            NoticeText.parseMarkdown(read(R.raw.third_party_notices)) +
                listOf(R.raw.apache_2_0, R.raw.ofl_roboto_flex, R.raw.ofl_source_serif_4)
                    .zip(headings)
                    .flatMap { (id, heading) ->
                        listOf(NoticeBlock.Heading(heading, level = 2)) + NoticeText.parsePlain(read(id))
                    }
    }
}

@Composable
fun NoticesContent(
    title: String,
    blocks: List<NoticeBlock>?,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { MensisTopBar(title = title, onBack = onBack) }) { padding ->
        if (blocks == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(blocks) { block -> NoticeBlockView(block) }
        }
    }
}

@Composable
private fun NoticeBlockView(block: NoticeBlock) {
    when (block) {
        is NoticeBlock.Heading ->
            Text(
                text = block.text,
                style =
                    if (block.level ==
                        1
                    ) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                modifier = Modifier.padding(top = 12.dp),
            )
        is NoticeBlock.Paragraph -> Text(text = styled(block.text), style = MaterialTheme.typography.bodyMedium)
        is NoticeBlock.Bullet ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = BULLET, style = MaterialTheme.typography.bodyMedium)
                Text(text = styled(block.text), style = MaterialTheme.typography.bodyMedium)
            }
        is NoticeBlock.TableRow ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                block.cells.forEachIndexed { index, cell ->
                    Text(
                        text = styled(cell),
                        style =
                            if (block.header) {
                                MaterialTheme.typography.labelLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                        color =
                            if (block.header) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        modifier = Modifier.weight(if (index == 0) 2f else 1f),
                    )
                }
            }
    }
}

private fun styled(text: String): AnnotatedString =
    buildAnnotatedString {
        for (span in NoticeText.spans(text)) {
            when {
                span.bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) }
                span.code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(span.text) }
                else -> append(span.text)
            }
        }
    }

private const val BULLET = "•"
