package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AiProviderType
import com.example.data.model.BookmarkDockSide
import com.example.data.model.DEFAULT_ISLAND_ALLOWED_PACKAGES
import com.example.data.model.IslandNotificationFilterMode
import com.example.data.model.PageAnimationMode
import com.example.data.model.ReaderFont
import com.example.data.model.ReaderMenuStyle
import com.example.data.model.ReaderPreferences
import com.example.data.model.ReadingTheme
import com.example.data.model.StandardPageFormat
import com.example.data.model.TargetFpsOption
import com.example.data.model.TextAlignOption
import com.example.data.model.TranslationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(name = "luma_preferences")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val USE_SYSTEM_FONT = booleanPreferencesKey("use_system_font")
        val FONT = stringPreferencesKey("font")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val MARGIN_DP = floatPreferencesKey("margin_dp")
        val TEXT_ALIGN = stringPreferencesKey("text_align")
        val PAGE_ANIMATION = stringPreferencesKey("page_animation")
        val MENU_STYLE = stringPreferencesKey("menu_style")
        val DOCK_SIDE = stringPreferencesKey("dock_side")
        val DOCK_Y_FRACTION = floatPreferencesKey("dock_y_fraction")
        val DOCK_SCALE = floatPreferencesKey("dock_scale")
        val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val TARGET_FPS = stringPreferencesKey("target_fps")
        val SHOW_FPS_COUNTER = booleanPreferencesKey("show_fps_counter")
        val SHOW_FOOTER = booleanPreferencesKey("show_footer")
        val TAP_ZONES_ENABLED = booleanPreferencesKey("tap_zones_enabled")
        val PDF_NIGHT_FILTER = booleanPreferencesKey("pdf_night_filter")
        val AUTO_OCR_PDF = booleanPreferencesKey("auto_ocr_pdf")
        val TRANSLATION_ENGINE = stringPreferencesKey("translation_engine")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val ISLAND_FILTER_MODE = stringPreferencesKey("island_filter_mode")
        val ISLAND_ALLOWED_PACKAGES = stringSetPreferencesKey("island_allowed_packages")
        val ISLAND_CRITICAL_BATTERY = booleanPreferencesKey("island_critical_battery")
        val ISLAND_CRITICAL_CONNECTIVITY = booleanPreferencesKey("island_critical_connectivity")
        val ISLAND_ALLOW_CALLS = booleanPreferencesKey("island_allow_calls")
        val USE_STANDARD_PAGE_VIEW = booleanPreferencesKey("use_standard_page_view")
        val STANDARD_PAGE_FORMAT = stringPreferencesKey("standard_page_format")
        val WARM_FILTER_STRENGTH = floatPreferencesKey("warm_filter_strength")
        val NIGHT_MODE_ENABLED = booleanPreferencesKey("night_mode_enabled")
        val NIGHT_AUTO_SCHEDULE = booleanPreferencesKey("night_auto_schedule")
        val NIGHT_START_HOUR = intPreferencesKey("night_start_hour")
        val NIGHT_END_HOUR = intPreferencesKey("night_end_hour")
        val PREFERRED_DAY_THEME = stringPreferencesKey("preferred_day_theme")
        val PREFERRED_NIGHT_THEME = stringPreferencesKey("preferred_night_theme")
        val NIGHT_TEXT_DIMMING = floatPreferencesKey("night_text_dimming")
        val HAS_SEEN_GUIDE = booleanPreferencesKey("has_seen_guide")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val SEEDED_SAMPLE_VERSION = intPreferencesKey("seeded_sample_version")
    }

    val preferencesFlow: Flow<ReaderPreferences> = context.readerDataStore.data.map { prefs ->
        val provider = enumOrDefault(prefs[Keys.AI_PROVIDER], AiProviderType.GEMINI)
        ReaderPreferences(
            theme = enumOrDefault(prefs[Keys.THEME], ReadingTheme.LIGHT),
            useSystemFont = prefs[Keys.USE_SYSTEM_FONT] ?: false,
            font = enumOrDefault(prefs[Keys.FONT], ReaderFont.LITERATA),
            fontSizeSp = (prefs[Keys.FONT_SIZE] ?: 18f).coerceIn(14f, 28f),
            lineHeightMultiplier = (prefs[Keys.LINE_HEIGHT] ?: 1.6f).coerceIn(1.2f, 2.2f),
            horizontalMarginDp = (prefs[Keys.MARGIN_DP] ?: 22f).coerceIn(12f, 44f),
            textAlign = enumOrDefault(prefs[Keys.TEXT_ALIGN], TextAlignOption.START),
            pageAnimation = enumOrDefault(prefs[Keys.PAGE_ANIMATION], PageAnimationMode.BOOK_CURL),
            menuStyle = enumOrDefault(prefs[Keys.MENU_STYLE], ReaderMenuStyle.BOOKMARK_DOCK),
            dockSide = enumOrDefault(prefs[Keys.DOCK_SIDE], BookmarkDockSide.RIGHT),
            dockYFraction = (prefs[Keys.DOCK_Y_FRACTION] ?: 0.48f).coerceIn(0.14f, 0.86f),
            dockScale = (prefs[Keys.DOCK_SCALE] ?: 1.0f).coerceIn(0.85f, 1.35f),
            readerBrightness = prefs[Keys.READER_BRIGHTNESS] ?: -1f,
            targetFps = enumOrDefault(prefs[Keys.TARGET_FPS], TargetFpsOption.AUTO),
            showFpsCounter = prefs[Keys.SHOW_FPS_COUNTER] ?: false,
            showMinimalFooter = prefs[Keys.SHOW_FOOTER] ?: true,
            tapZonesEnabled = prefs[Keys.TAP_ZONES_ENABLED] ?: true,
            pdfNightFilter = prefs[Keys.PDF_NIGHT_FILTER] ?: false,
            autoOcrPdf = prefs[Keys.AUTO_OCR_PDF] ?: true,
            translationEngine = enumOrDefault(prefs[Keys.TRANSLATION_ENGINE], TranslationEngine.GOOGLE_TRANSLATE),
            aiProvider = provider,
            aiApiKey = prefs[Keys.AI_API_KEY].orEmpty(),
            aiModel = prefs[Keys.AI_MODEL]?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
            aiBaseUrl = prefs[Keys.AI_BASE_URL]?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl,
            islandFilterMode = enumOrDefault(prefs[Keys.ISLAND_FILTER_MODE], IslandNotificationFilterMode.ALLOWED_ONLY),
            islandAllowedPackages = prefs[Keys.ISLAND_ALLOWED_PACKAGES] ?: DEFAULT_ISLAND_ALLOWED_PACKAGES,
            islandShowCriticalBattery = prefs[Keys.ISLAND_CRITICAL_BATTERY] ?: true,
            islandShowCriticalConnectivity = prefs[Keys.ISLAND_CRITICAL_CONNECTIVITY] ?: true,
            islandAllowIncomingCalls = prefs[Keys.ISLAND_ALLOW_CALLS] ?: true,
            useStandardPageView = prefs[Keys.USE_STANDARD_PAGE_VIEW] ?: false,
            standardPageFormat = enumOrDefault(prefs[Keys.STANDARD_PAGE_FORMAT], StandardPageFormat.AUTO),
            warmFilterStrength = (prefs[Keys.WARM_FILTER_STRENGTH] ?: 0f).coerceIn(0f, 0.45f),
            nightModeEnabled = prefs[Keys.NIGHT_MODE_ENABLED] ?: false,
            nightAutoSchedule = prefs[Keys.NIGHT_AUTO_SCHEDULE] ?: false,
            nightStartHour = (prefs[Keys.NIGHT_START_HOUR] ?: 20).coerceIn(0, 23),
            nightEndHour = (prefs[Keys.NIGHT_END_HOUR] ?: 7).coerceIn(0, 23),
            preferredDayTheme = enumOrDefault(prefs[Keys.PREFERRED_DAY_THEME], ReadingTheme.WARM_CREAM),
            preferredNightTheme = enumOrDefault(prefs[Keys.PREFERRED_NIGHT_THEME], ReadingTheme.WARM_NIGHT),
            nightTextDimming = (prefs[Keys.NIGHT_TEXT_DIMMING] ?: 0f).coerceIn(0f, 0.38f),
            hasSeenGuide = prefs[Keys.HAS_SEEN_GUIDE] ?: false,
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            seededSampleVersion = prefs[Keys.SEEDED_SAMPLE_VERSION] ?: 0
        )
    }

    suspend fun updatePreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
        context.readerDataStore.edit { prefs ->
            val provider = enumOrDefault(prefs[Keys.AI_PROVIDER], AiProviderType.GEMINI)
            val current = ReaderPreferences(
                theme = enumOrDefault(prefs[Keys.THEME], ReadingTheme.LIGHT),
                useSystemFont = prefs[Keys.USE_SYSTEM_FONT] ?: false,
                font = enumOrDefault(prefs[Keys.FONT], ReaderFont.LITERATA),
                fontSizeSp = prefs[Keys.FONT_SIZE] ?: 18f,
                lineHeightMultiplier = prefs[Keys.LINE_HEIGHT] ?: 1.6f,
                horizontalMarginDp = prefs[Keys.MARGIN_DP] ?: 22f,
                textAlign = enumOrDefault(prefs[Keys.TEXT_ALIGN], TextAlignOption.START),
                pageAnimation = enumOrDefault(prefs[Keys.PAGE_ANIMATION], PageAnimationMode.BOOK_CURL),
                menuStyle = enumOrDefault(prefs[Keys.MENU_STYLE], ReaderMenuStyle.BOOKMARK_DOCK),
                dockSide = enumOrDefault(prefs[Keys.DOCK_SIDE], BookmarkDockSide.RIGHT),
                dockYFraction = prefs[Keys.DOCK_Y_FRACTION] ?: 0.48f,
                dockScale = prefs[Keys.DOCK_SCALE] ?: 1.0f,
                readerBrightness = prefs[Keys.READER_BRIGHTNESS] ?: -1f,
                targetFps = enumOrDefault(prefs[Keys.TARGET_FPS], TargetFpsOption.AUTO),
                showFpsCounter = prefs[Keys.SHOW_FPS_COUNTER] ?: false,
                showMinimalFooter = prefs[Keys.SHOW_FOOTER] ?: true,
                tapZonesEnabled = prefs[Keys.TAP_ZONES_ENABLED] ?: true,
                pdfNightFilter = prefs[Keys.PDF_NIGHT_FILTER] ?: false,
                autoOcrPdf = prefs[Keys.AUTO_OCR_PDF] ?: true,
                translationEngine = enumOrDefault(prefs[Keys.TRANSLATION_ENGINE], TranslationEngine.GOOGLE_TRANSLATE),
                aiProvider = provider,
                aiApiKey = prefs[Keys.AI_API_KEY].orEmpty(),
                aiModel = prefs[Keys.AI_MODEL] ?: provider.defaultModel,
                aiBaseUrl = prefs[Keys.AI_BASE_URL] ?: provider.defaultBaseUrl,
                islandFilterMode = enumOrDefault(prefs[Keys.ISLAND_FILTER_MODE], IslandNotificationFilterMode.ALLOWED_ONLY),
                islandAllowedPackages = prefs[Keys.ISLAND_ALLOWED_PACKAGES] ?: DEFAULT_ISLAND_ALLOWED_PACKAGES,
                islandShowCriticalBattery = prefs[Keys.ISLAND_CRITICAL_BATTERY] ?: true,
                islandShowCriticalConnectivity = prefs[Keys.ISLAND_CRITICAL_CONNECTIVITY] ?: true,
                islandAllowIncomingCalls = prefs[Keys.ISLAND_ALLOW_CALLS] ?: true,
                useStandardPageView = prefs[Keys.USE_STANDARD_PAGE_VIEW] ?: false,
                standardPageFormat = enumOrDefault(prefs[Keys.STANDARD_PAGE_FORMAT], StandardPageFormat.AUTO),
                warmFilterStrength = (prefs[Keys.WARM_FILTER_STRENGTH] ?: 0f).coerceIn(0f, 0.45f),
                nightModeEnabled = prefs[Keys.NIGHT_MODE_ENABLED] ?: false,
                nightAutoSchedule = prefs[Keys.NIGHT_AUTO_SCHEDULE] ?: false,
                nightStartHour = (prefs[Keys.NIGHT_START_HOUR] ?: 20).coerceIn(0, 23),
                nightEndHour = (prefs[Keys.NIGHT_END_HOUR] ?: 7).coerceIn(0, 23),
                preferredDayTheme = enumOrDefault(prefs[Keys.PREFERRED_DAY_THEME], ReadingTheme.WARM_CREAM),
                preferredNightTheme = enumOrDefault(prefs[Keys.PREFERRED_NIGHT_THEME], ReadingTheme.WARM_NIGHT),
                nightTextDimming = (prefs[Keys.NIGHT_TEXT_DIMMING] ?: 0f).coerceIn(0f, 0.38f),
                hasSeenGuide = prefs[Keys.HAS_SEEN_GUIDE] ?: false,
                hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
                seededSampleVersion = prefs[Keys.SEEDED_SAMPLE_VERSION] ?: 0
            )
            val updated = transform(current)
            prefs[Keys.THEME] = updated.theme.name
            prefs[Keys.USE_SYSTEM_FONT] = updated.useSystemFont
            prefs[Keys.FONT] = updated.font.name
            prefs[Keys.FONT_SIZE] = updated.fontSizeSp
            prefs[Keys.LINE_HEIGHT] = updated.lineHeightMultiplier
            prefs[Keys.MARGIN_DP] = updated.horizontalMarginDp
            prefs[Keys.TEXT_ALIGN] = updated.textAlign.name
            prefs[Keys.PAGE_ANIMATION] = updated.pageAnimation.name
            prefs[Keys.MENU_STYLE] = updated.menuStyle.name
            prefs[Keys.DOCK_SIDE] = updated.dockSide.name
            prefs[Keys.DOCK_Y_FRACTION] = updated.dockYFraction
            prefs[Keys.DOCK_SCALE] = updated.dockScale
            prefs[Keys.READER_BRIGHTNESS] = updated.readerBrightness
            prefs[Keys.TARGET_FPS] = updated.targetFps.name
            prefs[Keys.SHOW_FPS_COUNTER] = updated.showFpsCounter
            prefs[Keys.SHOW_FOOTER] = updated.showMinimalFooter
            prefs[Keys.TAP_ZONES_ENABLED] = updated.tapZonesEnabled
            prefs[Keys.PDF_NIGHT_FILTER] = updated.pdfNightFilter
            prefs[Keys.AUTO_OCR_PDF] = updated.autoOcrPdf
            prefs[Keys.TRANSLATION_ENGINE] = updated.translationEngine.name
            prefs[Keys.AI_PROVIDER] = updated.aiProvider.name
            prefs[Keys.AI_API_KEY] = updated.aiApiKey
            prefs[Keys.AI_MODEL] = updated.aiModel
            prefs[Keys.AI_BASE_URL] = updated.aiBaseUrl
            prefs[Keys.ISLAND_FILTER_MODE] = updated.islandFilterMode.name
            prefs[Keys.ISLAND_ALLOWED_PACKAGES] = updated.islandAllowedPackages
            prefs[Keys.ISLAND_CRITICAL_BATTERY] = updated.islandShowCriticalBattery
            prefs[Keys.ISLAND_CRITICAL_CONNECTIVITY] = updated.islandShowCriticalConnectivity
            prefs[Keys.ISLAND_ALLOW_CALLS] = updated.islandAllowIncomingCalls
            prefs[Keys.USE_STANDARD_PAGE_VIEW] = updated.useStandardPageView
            prefs[Keys.STANDARD_PAGE_FORMAT] = updated.standardPageFormat.name
            prefs[Keys.WARM_FILTER_STRENGTH] = updated.warmFilterStrength.coerceIn(0f, 0.45f)
            prefs[Keys.NIGHT_MODE_ENABLED] = updated.nightModeEnabled
            prefs[Keys.NIGHT_AUTO_SCHEDULE] = updated.nightAutoSchedule
            prefs[Keys.NIGHT_START_HOUR] = updated.nightStartHour.coerceIn(0, 23)
            prefs[Keys.NIGHT_END_HOUR] = updated.nightEndHour.coerceIn(0, 23)
            prefs[Keys.PREFERRED_DAY_THEME] = updated.preferredDayTheme.name
            prefs[Keys.PREFERRED_NIGHT_THEME] = updated.preferredNightTheme.name
            prefs[Keys.NIGHT_TEXT_DIMMING] = updated.nightTextDimming.coerceIn(0f, 0.38f)
            prefs[Keys.HAS_SEEN_GUIDE] = updated.hasSeenGuide
            prefs[Keys.HAS_COMPLETED_ONBOARDING] = updated.hasCompletedOnboarding
            prefs[Keys.SEEDED_SAMPLE_VERSION] = updated.seededSampleVersion
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T {
        if (name.isNullOrBlank()) return default
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
