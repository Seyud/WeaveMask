package io.github.seyud.weave.core

import android.os.Bundle
import androidx.core.content.edit
import io.github.seyud.weave.core.di.ServiceLocator
import io.github.seyud.weave.core.repository.DBConfig
import io.github.seyud.weave.core.repository.PreferenceConfig
import io.github.seyud.weave.core.utils.LocaleSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object Config : PreferenceConfig, DBConfig {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val stringDB get() = ServiceLocator.stringDB
    override val settingsDB get() = ServiceLocator.settingsDB
    override val context get() = ServiceLocator.deContext
    override val coroutineScope get() = appScope

    object Key {
        // db configs
        const val ROOT_ACCESS = "root_access"
        const val SU_MULTIUSER_MODE = "multiuser_mode"
        const val SU_MNT_NS = "mnt_ns"
        const val SU_BIOMETRIC = "su_biometric"
        const val ZYGISK = "zygisk"
        const val BOOTLOOP = "bootloop"
        const val SU_MANAGER = "requester"
        const val KEYSTORE = "keystore"

        // prefs
        const val SU_REQUEST_TIMEOUT = "su_request_timeout"
        const val SU_AUTO_RESPONSE = "su_auto_response"
        const val SU_NOTIFICATION = "su_notification"
        const val SU_REAUTH = "su_reauth"
        const val SU_TAPJACK = "su_tapjack"
        const val SU_LIST_MODE = "su_list_mode"
        const val SU_RESTRICT = "su_restrict"
        const val CHECK_UPDATES = "check_update"
        const val RELEASE_CHANNEL = "release_channel"
        const val CUSTOM_CHANNEL = "custom_channel"
        const val MODULE_REPO_BASE_URL = "module_repo_base_url"
        const val MODULE_REPO_SORT_BY_NAME = "module_repo_sort_by_name"
        const val LOCALE = "locale"
        const val DARK_THEME = "dark_theme_extended"
        const val DOWNLOAD_DIR = "download_dir"
        const val SAFETY = "safety_notice"
        const val THEME_ORDINAL = "theme_ordinal"
        const val COLOR_MODE = "color_mode"
        const val KEY_COLOR = "key_color"
        const val ASKED_HOME = "asked_home"
        const val DOH = "doh"
        const val RAND_NAME = "rand_name"
        const val ENABLE_BLUR = "enable_blur"
        const val ENABLE_FLOATING_BOTTOM_BAR = "enable_floating_bottom_bar"
        const val ENABLE_FLOATING_BOTTOM_BAR_BLUR = "enable_floating_bottom_bar_blur"
        const val ENABLE_SMOOTH_CORNER = "enable_smooth_corner"
        const val ENABLE_PREDICTIVE_BACK = "enable_predictive_back"
        const val PAGE_SCALE = "page_scale"
        const val HOME_LAYOUT_MODE = "home_layout_mode"
        const val APP_ICON_VARIANT = "app_icon_variant"

        val NO_MIGRATION = setOf(ASKED_HOME, SU_REQUEST_TIMEOUT,
            SU_AUTO_RESPONSE, SU_REAUTH, SU_TAPJACK)
    }

    object OldValue {
        // Update channels
        const val DEFAULT_CHANNEL = -1
        const val STABLE_CHANNEL = 0
        const val BETA_CHANNEL = 1
        const val CUSTOM_CHANNEL = 2
        const val CANARY_CHANNEL = 3
        const val DEBUG_CHANNEL = 4
    }

    object Value {
        // Update channels
        const val DEFAULT_CHANNEL = -1
        const val STABLE_CHANNEL = 0
        const val BETA_CHANNEL = 1
        const val DEBUG_CHANNEL = 2
        const val CUSTOM_CHANNEL = 3

        // root access mode
        const val ROOT_ACCESS_DISABLED = 0
        const val ROOT_ACCESS_APPS_ONLY = 1
        const val ROOT_ACCESS_ADB_ONLY = 2
        const val ROOT_ACCESS_APPS_AND_ADB = 3

        // su multiuser
        const val MULTIUSER_MODE_OWNER_ONLY = 0
        const val MULTIUSER_MODE_OWNER_MANAGED = 1
        const val MULTIUSER_MODE_USER = 2

        // su mnt ns
        const val NAMESPACE_MODE_GLOBAL = 0
        const val NAMESPACE_MODE_REQUESTER = 1
        const val NAMESPACE_MODE_ISOLATE = 2

        // su notification
        const val NO_NOTIFICATION = 0
        const val NOTIFICATION_TOAST = 1

        // superuser list mode
        const val SU_MODE_BLACKLIST = 0
        const val SU_MODE_WHITELIST = 1

        // su auto response
        const val SU_PROMPT = 0
        const val SU_AUTO_DENY = 1
        const val SU_AUTO_ALLOW = 2

        // su timeout
        val TIMEOUT_LIST = longArrayOf(0, -1, 10, 20, 30, 60)

        // home layout
        const val HOME_LAYOUT_CLASSIC = 0
        const val HOME_LAYOUT_WEAVSK = 1
    }

    @JvmField var keepVerity = false
    @JvmField var keepEnc = false
    @JvmField var recovery = false
    var denyList = false

    var askedHome by preference(Key.ASKED_HOME, false)
    var bootloop by dbSettings(Key.BOOTLOOP, 0)

    var safetyNotice by preference(Key.SAFETY, true)
    var darkTheme by preference(Key.DARK_THEME, -1)
    var themeOrdinal by preference(Key.THEME_ORDINAL, 0)
    var colorMode by preference(Key.COLOR_MODE, 0)
    var keyColor by preference(Key.KEY_COLOR, 0)
    private var enableBlurPrefs by preference(Key.ENABLE_BLUR, true)
    var enableFloatingBottomBar by preference(Key.ENABLE_FLOATING_BOTTOM_BAR, false)
    private var enableFloatingBottomBarBlurPrefs by preference(Key.ENABLE_FLOATING_BOTTOM_BAR_BLUR, false)
    var enableSmoothCorner by preference(Key.ENABLE_SMOOTH_CORNER, true)
    var enablePredictiveBack by preference(Key.ENABLE_PREDICTIVE_BACK, false)
    var pageScale by preference(Key.PAGE_SCALE, 1.0f)
    var homeLayoutMode by preference(Key.HOME_LAYOUT_MODE, Value.HOME_LAYOUT_CLASSIC)
    var appIconVariant by preference(Key.APP_ICON_VARIANT, "current")

    var enableBlur
        get() = enableBlurPrefs
        set(value) {
            enableBlurPrefs = value
            if (!value && enableFloatingBottomBarBlurPrefs) {
                enableFloatingBottomBarBlurPrefs = false
            }
        }

    var enableFloatingBottomBarBlur
        get() = enableFloatingBottomBarBlurPrefs
        set(value) {
            if (value && !enableBlurPrefs) {
                enableBlurPrefs = true
            }
            enableFloatingBottomBarBlurPrefs = value
        }

    private var checkUpdatePrefs by preference(Key.CHECK_UPDATES, true)
    private var localePrefs by preference(Key.LOCALE, "")
    var doh by preference(Key.DOH, false)
    var updateChannel by preference(Key.RELEASE_CHANNEL, Value.DEFAULT_CHANNEL)
    var customChannelUrl by preference(Key.CUSTOM_CHANNEL, "")
    private var moduleRepoBaseUrlPrefs by preference(Key.MODULE_REPO_BASE_URL, Const.Url.KERNELSU_MODULE_REPO_URL)
    var moduleRepoSortByName by preference(Key.MODULE_REPO_SORT_BY_NAME, false)
    var downloadDir by preference(Key.DOWNLOAD_DIR, "")
    var randName by preference(Key.RAND_NAME, true)
    var checkUpdate
        get() = checkUpdatePrefs
        set(value) {
            if (checkUpdatePrefs != value) {
                checkUpdatePrefs = value
                JobService.schedule(AppContext)
            }
        }
    var locale
        get() = localePrefs
        set(value) {
            localePrefs = value
            LocaleSetting.instance.setLocale(value)
        }
    var moduleRepoBaseUrl
        get() = normalizeModuleRepoBaseUrl(moduleRepoBaseUrlPrefs) ?: Const.Url.KERNELSU_MODULE_REPO_URL
        set(value) {
            moduleRepoBaseUrlPrefs = normalizeModuleRepoBaseUrl(value) ?: Const.Url.KERNELSU_MODULE_REPO_URL
        }

    var zygisk by dbSettings(Key.ZYGISK, Info.isEmulator)
    var suManager by dbStrings(Key.SU_MANAGER, "", true)
    var keyStoreRaw by dbStrings(Key.KEYSTORE, "", true)

    var suDefaultTimeout by preferenceStrInt(Key.SU_REQUEST_TIMEOUT, 10)
    var suAutoResponse by preferenceStrInt(Key.SU_AUTO_RESPONSE, Value.SU_PROMPT)
    var suNotification by preferenceStrInt(Key.SU_NOTIFICATION, Value.NOTIFICATION_TOAST)
    var rootMode by dbSettings(Key.ROOT_ACCESS, Value.ROOT_ACCESS_APPS_AND_ADB)
    var suMntNamespaceMode by dbSettings(Key.SU_MNT_NS, Value.NAMESPACE_MODE_REQUESTER)
    var suMultiuserMode by dbSettings(Key.SU_MULTIUSER_MODE, Value.MULTIUSER_MODE_OWNER_ONLY)
    private var suBiometric by dbSettings(Key.SU_BIOMETRIC, false)
    var suAuth
        get() = Info.isDeviceSecure && suBiometric
        set(value) {
            suBiometric = value
        }
    var suReAuth by preference(Key.SU_REAUTH, false)
    var suTapjack by preference(Key.SU_TAPJACK, true)
    var suListMode by preference(Key.SU_LIST_MODE, Value.SU_MODE_WHITELIST)
    var suRestrict by preference(Key.SU_RESTRICT, false)

    private const val SU_FINGERPRINT = "su_fingerprint"
    private const val UPDATE_CHANNEL = "update_channel"

    fun toBundle(): Bundle {
        val map = prefs.all - Key.NO_MIGRATION
        return Bundle().apply {
            for ((key, value) in map) {
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun fromBundle(bundle: Bundle) {
        val keys = bundle.keySet().apply { removeAll(Key.NO_MIGRATION) }
        prefs.edit {
            for (key in keys) {
                when (val value = bundle.get(key)) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
    }

    fun init(bundle: Bundle?) {
        // Only try to load prefs when fresh install
        if (bundle != null && prefs.all.isEmpty()) {
            fromBundle(bundle)
        }

        // 首次安装时随机选择默认主题
        if (!prefs.contains(Key.COLOR_MODE)) {
            colorMode = if (kotlin.random.Random.nextBoolean()) 3 else 0  // 50% Monet跟随系统, 50% 跟随系统
        }

        prefs.edit {
            // Migrate su_fingerprint
            if (prefs.getBoolean(SU_FINGERPRINT, false))
                suBiometric = true
            remove(SU_FINGERPRINT)

            // Migrate update_channel
            prefs.getString(UPDATE_CHANNEL, null)?.let {
                val channel = when (it.toInt()) {
                    OldValue.STABLE_CHANNEL -> Value.STABLE_CHANNEL
                    OldValue.CANARY_CHANNEL, OldValue.BETA_CHANNEL -> Value.BETA_CHANNEL
                    OldValue.DEBUG_CHANNEL -> Value.DEBUG_CHANNEL
                    OldValue.CUSTOM_CHANNEL -> Value.CUSTOM_CHANNEL
                    else -> Value.DEFAULT_CHANNEL
                }
                putInt(Key.RELEASE_CHANNEL, channel)
            }
            remove(UPDATE_CHANNEL)
        }
    }

    fun normalizeModuleRepoBaseUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        val normalized = trimmed.toHttpUrlOrNull() ?: return null
        if (normalized.scheme != "http" && normalized.scheme != "https") {
            return null
        }
        return normalized.newBuilder().apply {
            encodedPath("/")
            query(null)
            fragment(null)
        }.build().toString().removeSuffix("/")
    }
}
