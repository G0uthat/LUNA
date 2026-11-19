package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.DictionarySourceEntity
import com.example.data.model.SelectedWordContext
import com.example.data.model.WikipediaSummary
import com.example.engine.TranslationService
import com.example.ui.theme.HighlightAmber
import com.example.ui.theme.HighlightCobalt
import com.example.ui.theme.HighlightCoral
import com.example.ui.theme.HighlightMint
import com.example.ui.theme.LocalFolioPalette
import kotlin.math.roundToInt

val HighlightSwatches = listOf(
    HighlightAmber to 0xFFF6C445L,
    HighlightCobalt to 0xFF5A9BFFL,
    HighlightMint to 0xFF28C17CL,
    HighlightCoral to 0xFFFF6B78L
)

/**
 * Dynamic floating contextual popover anchored near the selected word without obscuring it,
 * clamping safely to screen edges. Includes:
 * - Instant EN/auto -> ES translation banner
 * - Quick Highlight (4 colors), Add Note, and Copy controls
 * - Wikipedia ES native API tab + customizable {palabra} WebView tabs (RAE, WordReference, DeepL, etc.)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ContextualDictionaryPopover(
    selection: SelectedWordContext,
    sources: List<DictionarySourceEntity>,
    instantTranslation: String?,
    isTranslatingWord: Boolean,
    translationStatusText: String,
    wikiSummary: WikipediaSummary?,
    wikiError: String?,
    isWikiLoading: Boolean,
    onHighlight: (Long) -> Unit,
    onAddNote: () -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val enabledSources = remember(sources) { sources.filter { it.isEnabled } }
    var selectedTabIndex by remember(selection.word) { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val density = androidx.compose.ui.platform.LocalDensity.current

        val maxPopWidthDp = if (maxWidth > 620.dp) 440.dp else 356.dp
        val popW = with(density) { minOf(maxPopWidthDp.toPx(), maxW - 24.dp.toPx()).roundToInt() }
        val popH = with(density) { minOf(368.dp.toPx(), maxH * 0.56f).roundToInt() }
        val marginPx = with(density) { 12.dp.toPx().roundToInt() }
        val clearancePx = with(density) { 36.dp.toPx().roundToInt() }

        val anchorX = (selection.anchorXFraction * maxW).roundToInt()
        val anchorY = (selection.anchorYFraction * maxH).roundToInt()

        val targetX = (anchorX - popW / 2).coerceIn(marginPx, (maxW - popW - marginPx).coerceAtLeast(marginPx))

        val placeBelow = selection.anchorYFraction < 0.46f
        val rawY = if (placeBelow) {
            anchorY + clearancePx
        } else {
            anchorY - popH - clearancePx
        }
        val targetY = rawY.coerceIn(marginPx + 40, (maxH - popH - marginPx - 24).coerceAtLeast(marginPx))

        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(
                initialScale = 0.94f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
            ),
            exit = fadeOut(),
            modifier = Modifier
                .offset { IntOffset(targetX, targetY) }
                .size(
                    width = with(density) { popW.toDp() },
                    height = with(density) { popH.toDp() }
                )
        ) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.28f))
                    .clip(shape)
                    .background(palette.surface)
                    .border(1.5.dp, palette.border, shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(12.dp)
                    .testTag("contextual_dictionary_popover")
            ) {
                // Header: Selected word + Highlight Swatches + Note + Copy + Discreet Settings + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = TranslationService.cleanWord(selection.word).ifEmpty { selection.word },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for ((color, argb) in HighlightSwatches) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.5.dp, palette.surface, CircleShape)
                                    .clickable { onHighlight(argb) }
                                    .testTag("popover_highlight_$argb")
                            )
                        }
                    }

                    Spacer(Modifier.width(5.dp))

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceAlt)
                            .clickable(onClick = onAddNote)
                            .testTag("popover_add_note"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = "Añadir nota",
                            tint = palette.ink,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceAlt)
                            .clickable { clipboard.setText(AnnotatedString(selection.word)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copiar palabra",
                            tint = palette.ink,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    // Small discreet recuadro for Dictionary / API config right inside the popover!
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceAlt.copy(alpha = 0.7f))
                            .clickable {
                                onDismiss()
                                onOpenDictionarySettings()
                            }
                            .testTag("popover_dict_config_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Ajustar diccionario o API",
                            tint = palette.inkMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(Modifier.width(2.dp))

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar popover",
                            tint = palette.inkMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Instant EN / Auto -> ES Translation Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accentSoft)
                        .border(1.dp, palette.accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("popover_translation_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isTranslatingWord) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = palette.accent
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = translationStatusText.ifBlank { "Traduciendo al español…" },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = instantTranslation ?: "Sin traducción directa disponible",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Source Tabs Row (Wikipedia + RAE + WordReference + Custom {palabra})
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    enabledSources.forEachIndexed { idx, source ->
                        val active = idx == selectedTabIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) palette.accent else palette.surfaceAlt)
                                .clickable { selectedTabIndex = idx }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("dict_tab_${source.name}")
                        ) {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (active) palette.onAccent else palette.ink
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Tab Content Body
                val activeSource = enabledSources.getOrNull(selectedTabIndex)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.background)
                        .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                ) {
                    if (activeSource == null) {
                        Text(
                            text = "Activa una fuente en Diccionario e IA.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted,
                            modifier = Modifier.align(Alignment.Center).padding(12.dp)
                        )
                    } else if (activeSource.isWikipediaApi) {
                        WikipediaNativeSummaryBody(
                            summary = wikiSummary,
                            error = wikiError,
                            isLoading = isWikiLoading,
                            onOpenExternal = { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        )
                    } else {
                        val url = remember(activeSource.urlTemplate, selection.word) {
                            TranslationService.buildTemplatedUrl(activeSource.urlTemplate, selection.word)
                        }
                        EmbeddedDictionaryWebView(
                            url = url,
                            onOpenExternal = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WikipediaNativeSummaryBody(
    summary: WikipediaSummary?,
    error: String?,
    isLoading: Boolean,
    onOpenExternal: (String) -> Unit
) {
    val palette = LocalFolioPalette.current
    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.5.dp,
                    color = palette.accent
                )
            }
        }
        summary != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        if (summary.description.isNotBlank()) {
                            Text(
                                text = summary.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceAlt)
                            .clickable { onOpenExternal(summary.pageUrl) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Abrir en Wikipedia",
                            tint = palette.ink,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = summary.extract,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.ink
                )
            }
        }
        else -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error ?: "No se encontró resumen en Wikipedia ES. Prueba las pestañas RAE o WordReference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedDictionaryWebView(
    url: String,
    onOpenExternal: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var progress by remember(url) { mutableIntStateOf(15) }
    var failed by remember(url) { mutableStateOf(false) }
    var retryNonce by remember(url) { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (failed) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No se pudo cargar la vista embebida en este momento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TactileChip(
                        label = "Reintentar",
                        selected = false,
                        onClick = {
                            failed = false
                            progress = 15
                            retryNonce++
                        }
                    )
                    TactileChip(
                        label = "Abrir en navegador",
                        selected = true,
                        icon = Icons.Rounded.OpenInNew,
                        onClick = onOpenExternal
                    )
                }
            }
        } else {
            androidx.compose.runtime.key(retryNonce) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean = false

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        failed = true
                                    }
                                }

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: RenderProcessGoneDetail?
                                ): Boolean {
                                    // Cleanly detach and destroy the crashed renderer without killing the app
                                    runCatching {
                                        (view?.parent as? ViewGroup)?.removeView(view)
                                        view?.destroy()
                                    }
                                    failed = true
                                    return true
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            tag = url
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        // Compare against the requested URL stored in tag (not webView.url, which
                        // changes on redirects and would otherwise trigger an infinite reload loop)
                        if (webView.tag != url) {
                            failed = false
                            progress = 15
                            webView.tag = url
                            webView.loadUrl(url)
                        }
                    },
                    onRelease = { webView ->
                        runCatching {
                            webView.stopLoading()
                            webView.webChromeClient = null
                            webView.webViewClient = WebViewClient()
                            webView.loadUrl("about:blank")
                            webView.clearHistory()
                            webView.removeAllViews()
                            webView.destroy()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (progress < 95) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = palette.accent
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(palette.surface.copy(alpha = 0.92f))
                    .border(1.dp, palette.border, CircleShape)
                    .clickable(onClick = onOpenExternal),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = "Abrir enlace externo",
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
