package com.example.data.model

import androidx.compose.ui.text.font.FontFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LexendFamily
import com.example.ui.theme.LiterataFamily
import com.example.ui.theme.PlusJakartaSansFamily

enum class DocumentFormat(val label: String, val extension: String, val mimeType: String) {
    EPUB("EPUB", "epub", "application/epub+zip"),
    PDF("PDF", "pdf", "application/pdf"),
    DOCX("DOCX", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    TXT("TXT", "txt", "text/plain"),
    FB2("FB2", "fb2", "application/x-fictionbook+xml");

    companion object {
        fun fromExtension(ext: String): DocumentFormat? =
            entries.firstOrNull { it.extension.equals(ext.trim().lowercase(), ignoreCase = true) }

        fun fromFileName(name: String): DocumentFormat? {
            val ext = name.substringAfterLast('.', "")
            return fromExtension(ext)
        }
    }
}

enum class ReadingTheme(val displayName: String) {
    LIGHT("Papel"),
    SEPIA("Sepia"),
    SAGE("Salvia"),
    LAVENDER("Lavanda"),
    PEACH("Durazno"),
    SKY("Cielo"),
    ROSE("Rosa"),
    LEMON("Limón"),
    NAVY_MID("Azul marino"),
    MIDNIGHT("Medianoche"),
    DARK("Grafito"),
    AMOLED("AMOLED")
}

enum class ReaderFont(val displayName: String, val subtitle: String) {
    LITERATA("Literata", "Serif editorial"),
    PLUS_JAKARTA("Plus Jakarta", "Sans suave"),
    LEXEND("Lexend", "Dislexia / Fluidez"),
    JETBRAINS_MONO("JetBrains Mono", "Monoespaciada"),
    SYSTEM("Dispositivo", "Fuente del sistema");

    fun toFontFamily(): FontFamily = when (this) {
        LITERATA -> LiterataFamily
        PLUS_JAKARTA -> PlusJakartaSansFamily
        LEXEND -> LexendFamily
        JETBRAINS_MONO -> JetBrainsMonoFamily
        SYSTEM -> FontFamily.Default
    }
}

enum class PageAnimationMode(val displayName: String, val subtitle: String) {
    BOOK_CURL("Libro", "Hoja 3D"),
    E_INK("Tinta electrónica", "Destello Kindle"),
    SLIDE_DECK("Diapositiva", "Deslizamiento"),
    DEPTH_CARD("Tarjeta", "Superposición"),
    FADE("Desvanecer", "Fundido suave"),
    VERTICAL_SCROLL("Scroll continuo", "Webtoon / vertical")
}

enum class ReaderMenuStyle(val displayName: String, val description: String) {
    BOOKMARK_DOCK("Marcapáginas", "Pestaña arrastrable en cualquier lateral"),
    DYNAMIC_ISLAND("Isla dinámica", "Cápsula flotante superior desplegable"),
    CLASSIC_BAR("Clásico", "Barra superior e inferior al tocar el centro")
}

enum class BookmarkDockSide(val displayName: String) {
    RIGHT("Derecha"),
    LEFT("Izquierda")
}

enum class TextAlignOption(val displayName: String) {
    START("Izquierda"),
    JUSTIFY("Justificado")
}

enum class TranslationEngine(val displayName: String) {
    GOOGLE_TRANSLATE("Google Traductor"),
    AI_CUSTOM_API("IA con tu API key")
}

enum class AiProviderType(val displayName: String, val defaultModel: String, val defaultBaseUrl: String) {
    GEMINI("Google Gemini API", "gemini-2.5-flash", "https://generativelanguage.googleapis.com"),
    OPENAI_COMPATIBLE("OpenAI / Compatible", "gpt-4o-mini", "https://api.openai.com")
}

enum class TargetFpsOption(val label: String, val fps: Float) {
    AUTO("Máx", 0f),
    FPS_60("60", 60f),
    FPS_90("90", 90f),
    FPS_120("120", 120f),
    FPS_144("144", 144f),
    FPS_240("240", 240f)
}

enum class IslandNotificationFilterMode(val displayName: String, val subtitle: String) {
    ALLOWED_ONLY("Solo permitidas", "Estados críticos y únicamente apps que autorices"),
    CRITICAL_ONLY("Solo críticos", "Silencia apps externas; solo batería, red y llamadas"),
    ALL_APPS("Todas las apps", "Muestra avisos de cualquier app externa activa")
}

val DEFAULT_ISLAND_ALLOWED_PACKAGES: Set<String> = setOf(
    "com.whatsapp",
    "com.whatsapp.w4b",
    "org.telegram.messenger",
    "org.thoughtcrime.securesms",
    "com.google.android.apps.messaging",
    "com.samsung.android.messaging",
    "com.google.android.dialer",
    "com.android.dialer",
    "com.samsung.android.dialer",
    "com.google.android.gm"
)

data class ReaderPreferences(
    val theme: ReadingTheme = ReadingTheme.LIGHT,
    val useSystemFont: Boolean = false,
    val font: ReaderFont = ReaderFont.LITERATA,
    val fontSizeSp: Float = 18f,
    val lineHeightMultiplier: Float = 1.6f,
    val horizontalMarginDp: Float = 22f,
    val textAlign: TextAlignOption = TextAlignOption.START,
    val pageAnimation: PageAnimationMode = PageAnimationMode.BOOK_CURL,
    val menuStyle: ReaderMenuStyle = ReaderMenuStyle.BOOKMARK_DOCK,
    val dockSide: BookmarkDockSide = BookmarkDockSide.RIGHT,
    val dockYFraction: Float = 0.48f,
    val dockScale: Float = 1.0f, // 0.85f compact .. 1.35f large tablet
    val readerBrightness: Float = -1f, // -1f = system default, 0.05f..1.0f = manual
    val targetFps: TargetFpsOption = TargetFpsOption.AUTO,
    val showFpsCounter: Boolean = false,
    val showMinimalFooter: Boolean = true,
    val tapZonesEnabled: Boolean = true,
    val pdfNightFilter: Boolean = false,
    val autoOcrPdf: Boolean = true,
    val translationEngine: TranslationEngine = TranslationEngine.GOOGLE_TRANSLATE,
    val aiProvider: AiProviderType = AiProviderType.GEMINI,
    val aiApiKey: String = "",
    val aiModel: String = AiProviderType.GEMINI.defaultModel,
    val aiBaseUrl: String = AiProviderType.GEMINI.defaultBaseUrl,
    val islandFilterMode: IslandNotificationFilterMode = IslandNotificationFilterMode.ALLOWED_ONLY,
    val islandAllowedPackages: Set<String> = DEFAULT_ISLAND_ALLOWED_PACKAGES,
    val islandShowCriticalBattery: Boolean = true,
    val islandShowCriticalConnectivity: Boolean = true,
    val islandAllowIncomingCalls: Boolean = true
)

enum class BlockType {
    HEADING_1,
    HEADING_2,
    PARAGRAPH,
    QUOTE,
    LIST_ITEM
}

data class ParsedBlock(
    val index: Int,
    val type: BlockType,
    val text: String
)

data class ParsedChapter(
    val index: Int,
    val title: String,
    val cfiPrefix: String,
    val href: String,
    val blocks: List<ParsedBlock>,
    val wordCount: Int
)

data class TocEntry(
    val title: String,
    val targetIndex: Int,
    val level: Int = 0
)

data class ParsedDocument(
    val title: String,
    val author: String,
    val format: DocumentFormat,
    val chapters: List<ParsedChapter>,
    val toc: List<TocEntry>,
    val totalWords: Int,
    val pdfPageCount: Int = 0,
    val pdfPageTexts: List<String> = emptyList()
)

enum class DrawingTool(val displayName: String) {
    PEN("Lápiz"),
    HIGHLIGHTER("Resaltador"),
    ERASER("Borrador")
}

data class StrokePoint(
    val x: Float,
    val y: Float
)

data class DrawingStroke(
    val id: String,
    val tool: DrawingTool,
    val colorArgb: Long,
    val widthNorm: Float,
    val alpha: Float,
    val points: List<StrokePoint>
)

data class OcrWordBox(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class OcrBlockBox(
    val id: Int,
    val originalText: String,
    val translatedText: String? = null,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val words: List<OcrWordBox>,
    val bgColorArgb: Long = 0xFFFFFFFFL,
    val inkColorArgb: Long = 0xFF101828L
)

data class OcrPageResult(
    val pageIndex: Int,
    val blocks: List<OcrBlockBox>,
    val isTranslated: Boolean = false
)

data class SearchMatch(
    val targetIndex: Int,
    val chapterTitle: String,
    val snippet: String,
    val blockIndex: Int = 0
)

data class SelectedWordContext(
    val word: String,
    val snippet: String,
    val locationKey: String,
    val blockIndex: Int,
    val startChar: Int,
    val endChar: Int,
    val anchorXFraction: Float,
    val anchorYFraction: Float,
    val fullBlockText: String = "",
    val blockCharOffset: Int = 0,
    val wordStartChar: Int = startChar,
    val wordEndChar: Int = endChar
)

data class WikipediaSummary(
    val title: String,
    val description: String,
    val extract: String,
    val pageUrl: String
)

enum class ExportFormatOption(val label: String, val extension: String, val mimeType: String) {
    NATIVE_MERGED("Formato original fusionado", "", "*/*"),
    UNIVERSAL_PDF("PDF con texto seleccionable + dibujos", "pdf", "application/pdf")
}
