package com.epiclibrary.mobile.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import coil.compose.AsyncImage
import com.epiclibrary.mobile.BuildConfig
import com.epiclibrary.mobile.data.AppUpdateInfo
import com.epiclibrary.mobile.data.AppUpdateManager
import com.epiclibrary.mobile.data.AppUpdateStatus
import com.epiclibrary.mobile.data.EpicApi
import com.epiclibrary.mobile.data.EpicAuth
import com.epiclibrary.mobile.data.EpicAuthException
import com.epiclibrary.mobile.data.EpicToken
import com.epiclibrary.mobile.data.Game
import com.epiclibrary.mobile.data.GameCollection
import com.epiclibrary.mobile.data.LibraryItemKind
import com.epiclibrary.mobile.data.RealEpicApi
import com.epiclibrary.mobile.data.SecureTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.text.Collator

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EpicLibraryApp() }
    }
}

private sealed class AppState {
    data object Login : AppState()
    data class Loading(val message: String) : AppState()
    data class Ready(
        val games: List<Game>,
        val collections: List<GameCollection>,
        val displayName: String,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null
    ) : AppState()
    data class Error(val message: String, val retrySession: Boolean = false) : AppState()
}

private enum class AppPage { STORE, LIBRARY, SETTINGS }

private const val STORE_HOME_URL = "https://store.epicgames.com/"

private fun isStoreHomeUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    if (!uri.host.equals("store.epicgames.com", ignoreCase = true)) return false
    val path = uri.path.orEmpty().trimEnd('/')
    return path.isEmpty() || path.matches(Regex("^/[a-z]{2}-[a-z]{2}$", RegexOption.IGNORE_CASE))
}

@Composable
fun EpicLibraryApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { EpicAuth() }
    val api: EpicApi = remember { RealEpicApi() }
    val sessionStore = remember { SecureTokenStore(context) }
    val preferences = remember { AppPreferences(context) }
    val updateManager = remember { AppUpdateManager(context) }
    val refreshMutex = remember { Mutex() }

    var language by remember { mutableStateOf(preferences.language()) }
    var themeMode by remember { mutableStateOf(preferences.theme()) }
    var state by remember { mutableStateOf<AppState>(AppState.Loading(AppStrings.text(language, "loading_start"))) }
    var code by remember { mutableStateOf("") }
    var updateStatus by remember { mutableStateOf<AppUpdateStatus>(AppUpdateStatus.NotChecked) }
    var showUpdatePrompt by remember { mutableStateOf(false) }
    fun text(key: String, vararg args: Any): String = AppStrings.text(language, key, *args)

    suspend fun checkForUpdates() {
        updateStatus = AppUpdateStatus.Checking
        val result = updateManager.checkForUpdate()
        updateStatus = result
        showUpdatePrompt = result is AppUpdateStatus.Available
    }

    fun installUpdate(update: AppUpdateInfo) {
        scope.launch {
            showUpdatePrompt = false
            if (!updateManager.canRequestInstallPackages()) {
                updateStatus = AppUpdateStatus.PermissionRequired(update)
                runCatching { context.startActivity(updateManager.installPermissionSettingsIntent()) }
                    .onFailure { updateStatus = AppUpdateStatus.Failed("install permission") }
                return@launch
            }

            updateStatus = AppUpdateStatus.Downloading(update)
            val apk = runCatching { updateManager.downloadUpdate(update) }.getOrElse {
                updateStatus = AppUpdateStatus.Failed("download")
                return@launch
            }
            runCatching { context.startActivity(updateManager.installerIntent(apk)) }
                .onSuccess { updateStatus = AppUpdateStatus.InstallerOpened }
                .onFailure { updateStatus = AppUpdateStatus.Failed("installer") }
        }
    }

    suspend fun loadWithToken(token: EpicToken, showLoading: Boolean = true) {
        if (showLoading) state = AppState.Loading(text("loading_library"))
        // Store the rotated refresh token before any follow-up request can fail.
        sessionStore.saveSession(token)
        val (account, games, collections) = withContext(Dispatchers.IO) {
            val account = auth.verify(token.accessToken)
            val games = api.loadLibrary(token.accessToken, language.localeTag)
            val collections = api.loadCollections(token.accessToken, account.accountId)
            Triple(account, games, collections)
        }
        sessionStore.saveDisplayName(account.displayName)
        state = AppState.Ready(games, collections, account.displayName)
        code = ""
    }

    suspend fun refreshAndLoadSavedSession(showLoading: Boolean = true) = refreshMutex.withLock {
        val refreshToken = sessionStore.loadRefreshToken()
        if (refreshToken.isNullOrBlank()) {
            val accessToken = sessionStore.loadAccessToken()
            val expiresAt = sessionStore.loadAccessExpiresAt()
            if (!accessToken.isNullOrBlank() && expiresAt > System.currentTimeMillis() + 60_000L) {
                loadWithToken(
                    EpicToken(
                        accessToken = accessToken,
                        refreshToken = null,
                        expiresInSeconds = ((expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(60L)
                    ),
                    showLoading
                )
                return@withLock
            }
            throw IllegalStateException("No saved Epic session")
        }

        // Epic rotates refresh tokens. Read and refresh under one lock so two
        // tab taps cannot reuse the same one-time refresh token concurrently.
        val refreshed = withContext(Dispatchers.IO) { auth.refresh(refreshToken) }
        sessionStore.saveSession(refreshed)
        loadWithToken(refreshed, showLoading)
    }

    suspend fun restoreSession() {
        val savedAccessToken = sessionStore.loadAccessToken()
        val accessExpiresAt = sessionStore.loadAccessExpiresAt()
        val refreshToken = sessionStore.loadRefreshToken()
        val hasValidAccessToken = !savedAccessToken.isNullOrBlank() &&
            accessExpiresAt > System.currentTimeMillis() + 60_000L

        if (hasValidAccessToken) {
            state = AppState.Loading(text("loading_session"))
            val savedAttempt = runCatching {
                loadWithToken(
                    EpicToken(
                        accessToken = savedAccessToken!!,
                        refreshToken = refreshToken,
                        expiresInSeconds = ((accessExpiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(60L)
                    ),
                    showLoading = false
                )
            }
            if (savedAttempt.isSuccess) return
        }

        if (refreshToken.isNullOrBlank()) {
            state = if (hasValidAccessToken) {
                AppState.Error(text("error_session"), retrySession = true)
            } else {
                AppState.Login
            }
            return
        }

        state = AppState.Loading(text("loading_refresh"))
        val refreshAttempt = runCatching { refreshAndLoadSavedSession(showLoading = false) }
        if (refreshAttempt.isFailure) {
            val failure = refreshAttempt.exceptionOrNull()
            if (failure is EpicAuthException && failure.refreshRejected) {
                sessionStore.clear()
                state = AppState.Login
            } else {
                state = AppState.Error(text("error_refresh"), retrySession = true)
            }
        }
    }

    fun refreshLibrary() {
        val current = state as? AppState.Ready ?: return
        if (current.isRefreshing) return
        state = current.copy(isRefreshing = true, refreshError = null)
        scope.launch {
            runCatching { refreshAndLoadSavedSession(showLoading = false) }
                .onFailure { failure ->
                    if (failure is EpicAuthException && failure.refreshRejected) {
                        sessionStore.clear()
                        state = AppState.Login
                    } else {
                        val latest = state as? AppState.Ready
                        if (latest != null) {
                            state = latest.copy(isRefreshing = false, refreshError = text("error_library_refresh"))
                        }
                    }
                }
        }
    }

    LaunchedEffect(Unit) {
        restoreSession()
        checkForUpdates()
    }

    fun openEpicLogin() {
        context.startActivity(Intent(Intent.ACTION_VIEW, auth.buildLoginUrl()))
    }

    fun pasteClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        code = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    }

    fun submitCode() {
        val authCode = extractAuthorizationCode(code)
        if (authCode.isBlank()) {
            state = AppState.Error(text("error_missing_code"))
            return
        }
        scope.launch {
            state = AppState.Loading(text("loading_verify"))
            runCatching {
                val token = withContext(Dispatchers.IO) { auth.exchangeAuthorizationCode(authCode) }
                loadWithToken(token)
            }.onFailure {
                state = AppState.Error(text("error_login"))
            }
        }
    }

    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colors = if (useDarkTheme) {
        darkColorScheme(
            primary = Color(0xFFB6A6FF),
            secondary = Color(0xFF80D8CF),
            background = Color(0xFF0E1016),
            surface = Color(0xFF171A22),
            surfaceVariant = Color(0xFF232733),
            onBackground = Color(0xFFF3F1F8),
            onSurface = Color(0xFFF3F1F8),
            onSurfaceVariant = Color(0xFFB8BBC7)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF5746A8),
            secondary = Color(0xFF176B66),
            background = Color(0xFFF4F5FA),
            surface = Color.White,
            surfaceVariant = Color(0xFFE9EAF2),
            onBackground = Color(0xFF171821),
            onSurface = Color(0xFF171821),
            onSurfaceVariant = Color(0xFF5F6270)
        )
    }

    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            when (val current = state) {
                AppState.Login -> LoginScreen(
                    language = language,
                    code = code,
                    onCodeChange = { code = it },
                    onOpenEpic = ::openEpicLogin,
                    onPaste = ::pasteClipboard,
                    onSubmit = ::submitCode
                )
                is AppState.Loading -> LoadingScreen(current.message)
                is AppState.Error -> ErrorScreen(
                    language = language,
                    message = current.message,
                    code = code,
                    retrySession = current.retrySession,
                    onCodeChange = { code = it },
                    onRetrySession = { scope.launch { restoreSession() } },
                    onLogin = { state = AppState.Login }
                )
                is AppState.Ready -> HomeScreen(
                    language = language,
                    themeMode = themeMode,
                    games = current.games,
                    collections = current.collections,
                    displayName = current.displayName,
                    isRefreshing = current.isRefreshing,
                    refreshError = current.refreshError,
                    updateStatus = updateStatus,
                    onSelectLanguage = { selected ->
                        preferences.setLanguage(selected)
                        language = selected
                        refreshLibrary()
                    },
                    onSelectTheme = { selected ->
                        preferences.setTheme(selected)
                        themeMode = selected
                    },
                    onCheckUpdates = { scope.launch { checkForUpdates() } },
                    onInstallUpdate = ::installUpdate,
                    onRefreshLibrary = ::refreshLibrary,
                    onLogout = {
                        sessionStore.clear()
                        state = AppState.Login
                        code = ""
                    }
                )
            }

            val availableUpdate = (updateStatus as? AppUpdateStatus.Available)?.update
            if (state is AppState.Ready && showUpdatePrompt && availableUpdate != null) {
                AlertDialog(
                    onDismissRequest = { showUpdatePrompt = false },
                    title = { Text(text("update_prompt_title")) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text("update_prompt_body", availableUpdate.versionName))
                            if (availableUpdate.releaseNotes.isNotBlank()) Text(availableUpdate.releaseNotes)
                        }
                    },
                    confirmButton = {
                        Button(onClick = { installUpdate(availableUpdate) }) { Text(text("update_action")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdatePrompt = false }) { Text(text("later")) }
                    }
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    language: AppLanguage,
    code: String,
    onCodeChange: (String) -> Unit,
    onOpenEpic: () -> Unit,
    onPaste: () -> Unit,
    onSubmit: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("E", color = colors.onPrimaryContainer, fontSize = 36.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(22.dp))
        Text(AppStrings.text(language, "app_title"), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            AppStrings.text(language, "login_headline"),
            color = colors.onSurfaceVariant,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(28.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(AppStrings.text(language, "login_privacy"), color = colors.onSurfaceVariant)
                Button(onClick = onOpenEpic, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(AppStrings.text(language, "login_open"), fontWeight = FontWeight.Bold)
                }
                Text(
                    AppStrings.text(language, "login_instructions"),
                    color = colors.onSurfaceVariant,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text(AppStrings.text(language, "auth_code")) },
                    shape = RoundedCornerShape(16.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f)) {
                        Text(AppStrings.text(language, "paste"), maxLines = 1)
                    }
                    Button(onClick = onSubmit, modifier = Modifier.weight(1f)) {
                        Text(AppStrings.text(language, "connect"), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(18.dp))
            Text(message, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorScreen(
    language: AppLanguage,
    message: String,
    code: String,
    retrySession: Boolean,
    onCodeChange: (String) -> Unit,
    onRetrySession: () -> Unit,
    onLogin: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(AppStrings.text(language, "error_title"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(message, color = colors.onSurfaceVariant)
                if (retrySession) {
                    Button(onClick = onRetrySession, modifier = Modifier.fillMaxWidth()) {
                        Text(AppStrings.text(language, "retry_session"))
                    }
                    TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                        Text(AppStrings.text(language, "login_again"))
                    }
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = onCodeChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text(AppStrings.text(language, "auth_code")) }
                    )
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                        Text(AppStrings.text(language, "back_login"))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    language: AppLanguage,
    themeMode: AppThemeMode,
    games: List<Game>,
    collections: List<GameCollection>,
    displayName: String,
    isRefreshing: Boolean,
    refreshError: String?,
    updateStatus: AppUpdateStatus,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (AppThemeMode) -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: (AppUpdateInfo) -> Unit,
    onRefreshLibrary: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val storeLoading = remember { mutableStateOf(true) }
    val storeError = remember { mutableStateOf(false) }
    val storeCanGoBack = remember { mutableStateOf(false) }
    val storeCurrentUrl = remember { mutableStateOf(STORE_HOME_URL) }
    var storePopupWebView by remember { mutableStateOf<WebView?>(null) }

    fun closeStorePopup(webView: WebView) {
        if (storePopupWebView !== webView) return
        storePopupWebView = null
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    val storeWebView = remember(context) {
        createStoreWebView(
            context = context,
            onLoading = { storeLoading.value = it },
            onError = { storeError.value = it },
            onNavigationChanged = { url, canGoBack ->
                if (!url.isNullOrBlank()) storeCurrentUrl.value = url
                storeCanGoBack.value = canGoBack
            },
            onPopupRequested = { popup ->
                if (storePopupWebView != null) false
                else {
                    storePopupWebView = popup
                    true
                }
            },
            onClosePopup = ::closeStorePopup
        )
    }
    var selectedPage by remember { mutableStateOf(AppPage.STORE) }

    DisposableEffect(storeWebView) {
        onDispose {
            CookieManager.getInstance().flush()
            storePopupWebView?.let(::closeStorePopup)
            storeWebView.stopLoading()
            (storeWebView.parent as? ViewGroup)?.removeView(storeWebView)
            storeWebView.destroy()
        }
    }

    val storeHasBackDestination = storeCanGoBack.value || !isStoreHomeUrl(storeCurrentUrl.value)
    BackHandler(enabled = selectedPage != AppPage.STORE || storeHasBackDestination) {
        if (selectedPage != AppPage.STORE) {
            selectedPage = AppPage.STORE
        } else {
            val currentUrl = storeWebView.url ?: storeCurrentUrl.value
            when {
                storeWebView.canGoBack() -> storeWebView.goBack()
                !isStoreHomeUrl(currentUrl) -> storeWebView.loadUrl(STORE_HOME_URL)
            }
        }
    }

    fun selectPage(page: AppPage) {
        if (page != selectedPage) {
            selectedPage = page
            return
        }

        when (page) {
            AppPage.STORE -> {
                // Re-selecting Store is the explicit refresh gesture: return to
                // the store home page at its top, including when a product page is open.
                storeWebView.loadUrl(STORE_HOME_URL)
            }
            AppPage.LIBRARY -> onRefreshLibrary()
            AppPage.SETTINGS -> Unit
        }
    }

    val title = when (selectedPage) {
        AppPage.STORE -> AppStrings.text(language, "nav_store")
        AppPage.LIBRARY -> AppStrings.text(language, "nav_library")
        AppPage.SETTINGS -> AppStrings.text(language, "nav_settings")
    }
    val subtitle = when (selectedPage) {
        AppPage.STORE -> AppStrings.text(language, "store_subtitle")
        AppPage.LIBRARY -> AppStrings.librarySubtitle(language, games.size, displayName)
        AppPage.SETTINGS -> AppStrings.text(language, "account_connected", displayName)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val compactLibraryHeader = selectedPage == AppPage.LIBRARY
            Surface(
                modifier = Modifier.statusBarsPadding(),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 1.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (compactLibraryHeader) 7.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = if (compactLibraryHeader) 20.sp else 22.sp, fontWeight = FontWeight.Bold)
                        Text(
                            subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (compactLibraryHeader) 11.sp else 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isRefreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                listOf(AppPage.STORE, AppPage.LIBRARY, AppPage.SETTINGS).forEach { page ->
                    val selected = selectedPage == page
                    val label = when (page) {
                        AppPage.STORE -> AppStrings.text(language, "nav_store")
                        AppPage.LIBRARY -> AppStrings.text(language, "nav_library")
                        AppPage.SETTINGS -> AppStrings.text(language, "nav_settings")
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectPage(page) },
                        icon = { NavigationGlyph(page, selected) },
                        label = { Text(label) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { contentPadding ->
        AnimatedContent(
            targetState = selectedPage,
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            transitionSpec = {
                (fadeIn(tween(210)) + slideInHorizontally(tween(230)) { it / 18 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 22 })
            },
            label = "main-tabs"
        ) { page ->
            when (page) {
                AppPage.STORE -> StorePage(
                    webView = storeWebView,
                    loading = storeLoading.value,
                    failed = storeError.value,
                    language = language,
                    onRetry = {
                        if (storeWebView.url.isNullOrBlank()) storeWebView.loadUrl(STORE_HOME_URL)
                        else storeWebView.reload()
                    }
                )
                AppPage.LIBRARY -> LibraryPage(
                    language = language,
                    games = games,
                    collections = collections,
                    isRefreshing = isRefreshing,
                    refreshError = refreshError
                )
                AppPage.SETTINGS -> SettingsPage(
                    language = language,
                    themeMode = themeMode,
                    displayName = displayName,
                    updateStatus = updateStatus,
                    onLanguage = onSelectLanguage,
                    onTheme = onSelectTheme,
                    onCheckUpdates = onCheckUpdates,
                    onInstallUpdate = onInstallUpdate,
                    onLogout = onLogout
                )
            }
        }
    }

    StorePopupDialog(
        webView = storePopupWebView,
        language = language,
        onClose = { storePopupWebView?.let(::closeStorePopup) }
    )
}

@Composable
private fun NavigationGlyph(page: AppPage, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        when (page) {
            AppPage.STORE -> {
                val path = Path().apply {
                    moveTo(size.width * .12f, size.height * .38f)
                    lineTo(size.width * .22f, size.height * .12f)
                    lineTo(size.width * .78f, size.height * .12f)
                    lineTo(size.width * .88f, size.height * .38f)
                    lineTo(size.width * .88f, size.height * .86f)
                    lineTo(size.width * .12f, size.height * .86f)
                    close()
                }
                drawPath(path, color, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .12f, size.height * .4f), androidx.compose.ui.geometry.Offset(size.width * .88f, size.height * .4f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .39f, size.height * .4f), androidx.compose.ui.geometry.Offset(size.width * .39f, size.height * .86f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .64f, size.height * .4f), androidx.compose.ui.geometry.Offset(size.width * .64f, size.height * .86f), stroke.width, cap = StrokeCap.Round)
            }
            AppPage.LIBRARY -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * .12f, size.height * .12f), size = androidx.compose.ui.geometry.Size(size.width * .76f, size.height * .76f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .38f, size.height * .18f), androidx.compose.ui.geometry.Offset(size.width * .38f, size.height * .82f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .59f, size.height * .18f), androidx.compose.ui.geometry.Offset(size.width * .59f, size.height * .82f), stroke.width, cap = StrokeCap.Round)
            }
            AppPage.SETTINGS -> {
                drawCircle(color, radius = size.minDimension * .28f, style = stroke)
                drawCircle(color, radius = size.minDimension * .075f)
                listOf(0f, 90f, 180f, 270f).forEach { degrees ->
                    val radians = Math.toRadians(degrees.toDouble())
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val inner = size.minDimension * .34f
                    val outer = size.minDimension * .46f
                    drawLine(
                        color,
                        androidx.compose.ui.geometry.Offset(cx + kotlin.math.cos(radians).toFloat() * inner, cy + kotlin.math.sin(radians).toFloat() * inner),
                        androidx.compose.ui.geometry.Offset(cx + kotlin.math.cos(radians).toFloat() * outer, cy + kotlin.math.sin(radians).toFloat() * outer),
                        stroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

private enum class LibraryCategory { ALL, GAMES, TOOLS }
private enum class LibrarySort { AZ, ZA, MOST_PLAYED, LEAST_PLAYED }

@Composable
private fun LibraryPage(
    language: AppLanguage,
    games: List<Game>,
    collections: List<GameCollection>,
    isRefreshing: Boolean,
    refreshError: String?
) {
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(LibraryCategory.ALL) }
    var selectedPlayerMode by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(LibrarySort.AZ) }
    var sortExpanded by remember { mutableStateOf(false) }
    var selectedCollectionId by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val scrollScope = rememberCoroutineScope()
    val selectedCollection = collections.firstOrNull { it.id == selectedCollectionId }
    val uniqueGames = remember(games) {
        games.distinctBy { game ->
            game.id.ifBlank { "${game.namespace}:${game.catalogItemId}:${game.appName}:${game.title}" }
        }
    }
    val uniqueCollections = remember(collections) { collections.distinctBy { it.id } }
    val hasPlaytimeData = games.any { it.kind == LibraryItemKind.GAME && it.playtimeMinutes != null }
    val hasPlayerModeData = games.any { it.kind == LibraryItemKind.GAME && it.playerModes.isNotEmpty() }
    val collator = remember(language) { Collator.getInstance(java.util.Locale.forLanguageTag(language.localeTag)) }
    val filtered = uniqueGames.filter { game ->
        val categoryMatches = when (selectedCategory) {
            LibraryCategory.ALL -> true
            LibraryCategory.GAMES -> game.kind == LibraryItemKind.GAME
            LibraryCategory.TOOLS -> game.kind == LibraryItemKind.TOOL
        }
        val playerModeMatches = selectedPlayerMode == null || game.playerModes.contains(selectedPlayerMode)
        val collectionMatches = selectedCollection == null ||
            selectedCollection.gameIds.contains(game.id) ||
            selectedCollection.gameIds.contains(game.appName) ||
            selectedCollection.gameIds.contains(game.catalogItemId) ||
            selectedCollection.gameIds.contains("${game.namespace}:${game.catalogItemId}")
        categoryMatches && playerModeMatches && collectionMatches && game.title.contains(search, ignoreCase = true)
    }
    val sorted = when (sortMode) {
        LibrarySort.AZ -> filtered.sortedWith { left, right -> collator.compare(left.title, right.title) }
        LibrarySort.ZA -> filtered.sortedWith { left, right -> collator.compare(right.title, left.title) }
        LibrarySort.MOST_PLAYED -> filtered.sortedWith { left, right ->
            val playtimeOrder = (right.playtimeMinutes ?: -1L).compareTo(left.playtimeMinutes ?: -1L)
            if (playtimeOrder != 0) playtimeOrder else collator.compare(left.title, right.title)
        }
        LibrarySort.LEAST_PLAYED -> filtered.sortedWith { left, right ->
            val leftTime = left.playtimeMinutes
            val rightTime = right.playtimeMinutes
            when {
                leftTime == null && rightTime == null -> collator.compare(left.title, right.title)
                leftTime == null -> 1
                rightTime == null -> -1
                leftTime != rightTime -> leftTime.compareTo(rightTime)
                else -> collator.compare(left.title, right.title)
            }
        }
    }

    fun filterByPlayerMode(mode: String) {
        selectedCategory = LibraryCategory.GAMES
        selectedPlayerMode = if (selectedPlayerMode == mode) null else mode
        scrollScope.launch { if (sorted.isNotEmpty()) gridState.animateScrollToItem(0) }
    }

    fun selectSort(sort: LibrarySort) {
        sortMode = sort
        sortExpanded = false
        scrollScope.launch { if (sorted.isNotEmpty()) gridState.animateScrollToItem(0) }
    }

    fun selectCategory(category: LibraryCategory) {
        selectedCategory = category
        selectedPlayerMode = null
        if (category == LibraryCategory.TOOLS &&
            (sortMode == LibrarySort.MOST_PLAYED || sortMode == LibrarySort.LEAST_PLAYED)
        ) sortMode = LibrarySort.AZ
        scrollScope.launch { if (sorted.isNotEmpty()) gridState.animateScrollToItem(0) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(AppStrings.text(language, "search")) },
            shape = RoundedCornerShape(18.dp)
        )

        if (isRefreshing) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
        }
        if (refreshError != null) {
            Spacer(Modifier.height(6.dp))
            Text(refreshError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        if (collections.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionHeading(AppStrings.text(language, "collections"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCollectionId == null,
                        onClick = { selectedCollectionId = null },
                        label = { Text(AppStrings.text(language, "filter_all")) }
                    )
                }
                items(uniqueCollections, key = { it.id }) { collection ->
                    FilterChip(
                        selected = selectedCollectionId == collection.id,
                        onClick = { selectedCollectionId = collection.id },
                        label = { Text(collection.name, maxLines = 1) }
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        SectionHeading(AppStrings.text(language, "library_categories"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == LibraryCategory.ALL,
                        onClick = { selectCategory(LibraryCategory.ALL) },
                        label = { Text(AppStrings.text(language, "filter_all")) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == LibraryCategory.GAMES,
                        onClick = { selectCategory(LibraryCategory.GAMES) },
                        label = { Text(AppStrings.text(language, "category_games")) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCategory == LibraryCategory.TOOLS,
                        onClick = { selectCategory(LibraryCategory.TOOLS) },
                        label = { Text(AppStrings.text(language, "category_tools")) }
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box {
                OutlinedButton(
                    onClick = { sortExpanded = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(AppStrings.text(language, "sort"), fontSize = 12.sp) }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(AppStrings.text(language, "sort_order_heading"), fontWeight = FontWeight.Bold) },
                        onClick = {},
                        enabled = false
                    )
                    DropdownMenuItem(
                        text = { Text(AppStrings.text(language, "sort_az")) },
                        onClick = { selectSort(LibrarySort.AZ) }
                    )
                    DropdownMenuItem(
                        text = { Text(AppStrings.text(language, "sort_za")) },
                        onClick = { selectSort(LibrarySort.ZA) }
                    )
                    if (selectedCategory != LibraryCategory.TOOLS) {
                        DropdownMenuItem(
                            text = { Text(AppStrings.text(language, "sort_most_played")) },
                            onClick = { selectSort(LibrarySort.MOST_PLAYED) },
                            enabled = hasPlaytimeData
                        )
                        DropdownMenuItem(
                            text = { Text(AppStrings.text(language, "sort_least_played")) },
                            onClick = { selectSort(LibrarySort.LEAST_PLAYED) },
                            enabled = hasPlaytimeData
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(AppStrings.text(language, "sort_player_heading"), fontWeight = FontWeight.Bold) },
                            onClick = {},
                            enabled = false
                        )
                        DropdownMenuItem(
                            text = { Text(AppStrings.text(language, "sort_single_player")) },
                            onClick = { filterByPlayerMode("single"); sortExpanded = false },
                            enabled = hasPlayerModeData
                        )
                        DropdownMenuItem(
                            text = { Text(AppStrings.text(language, "sort_multiplayer")) },
                            onClick = { filterByPlayerMode("multi"); sortExpanded = false },
                            enabled = hasPlayerModeData
                        )
                        if (!hasPlaytimeData || !hasPlayerModeData) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(AppStrings.text(language, "sort_metadata_unavailable")) },
                                onClick = {},
                                enabled = false
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (selectedCollection != null) AppStrings.text(language, "empty_collection")
                    else AppStrings.text(language, "empty_library"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(sorted, key = { game ->
                    game.id.ifBlank { "${game.namespace}:${game.catalogItemId}:${game.appName}:${game.title}" }
                }) { game -> GameCard(game, language) }
            }
        }
    }
}

@Composable
private fun SectionHeading(label: String) {
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StorePage(
    webView: WebView,
    loading: Boolean,
    failed: Boolean,
    language: AppLanguage,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F5FA))) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
        AndroidView(
            modifier = Modifier.fillMaxSize().weight(1f),
            factory = { webView },
            update = { }
        )
        if (failed) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        AppStrings.text(language, "store_load_error"),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp
                    )
                    TextButton(onClick = onRetry) {
                        Text(AppStrings.text(language, "store_retry"))
                    }
                }
            }
        }
    }
}

@Composable
private fun StorePopupDialog(webView: WebView?, language: AppLanguage, onClose: () -> Unit) {
    if (webView == null) return
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(AppStrings.text(language, "store_checkout"), fontWeight = FontWeight.Bold)
                    TextButton(onClick = onClose) { Text("✕") }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    factory = { webView },
                    update = { }
                )
            }
        }
    }
}

private fun createStoreWebView(
    context: Context,
    onLoading: (Boolean) -> Unit,
    onError: (Boolean) -> Unit,
    onNavigationChanged: (String?, Boolean) -> Unit,
    onPopupRequested: (WebView) -> Boolean,
    onClosePopup: (WebView) -> Unit,
    isPopup: Boolean = false
): WebView {
    CookieManager.getInstance().setAcceptCookie(true)
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.textZoom = 100
        settings.setSupportZoom(false)
        settings.setSupportMultipleWindows(!isPopup)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
                request.url.scheme != "https"

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                onLoading(true)
                onError(false)
                onNavigationChanged(url ?: view?.url, view?.canGoBack() == true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                onLoading(false)
                onNavigationChanged(url ?: view?.url, view?.canGoBack() == true)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                onNavigationChanged(url ?: view?.url, view?.canGoBack() == true)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    onLoading(false)
                    onError(true)
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                    onLoading(false)
                    onError(true)
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onLoading(newProgress < 100)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (isPopup || !isUserGesture || resultMsg == null) return false
                val popup = createStoreWebView(
                    context = context,
                    onLoading = onLoading,
                    onError = onError,
                    onNavigationChanged = { _, _ -> },
                    onPopupRequested = onPopupRequested,
                    onClosePopup = onClosePopup,
                    isPopup = true
                )
                if (!onPopupRequested(popup)) {
                    popup.destroy()
                    return false
                }
                val transport = resultMsg.obj as? WebView.WebViewTransport
                if (transport == null) {
                    onClosePopup(popup)
                    return false
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                if (window != null) onClosePopup(window)
            }
        }
        setBackgroundColor(android.graphics.Color.rgb(244, 245, 250))
        if (!isPopup) loadUrl(STORE_HOME_URL)
    }
}

@Composable
private fun GameCard(game: Game, language: AppLanguage) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            if (game.imageUrl.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { Text("EPIC", color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold) }
            } else {
                AsyncImage(
                    model = game.imageUrl,
                    contentDescription = game.title.ifBlank { AppStrings.text(language, "unnamed_game") },
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
                Text(
                    game.title.ifBlank { AppStrings.text(language, "unnamed_game") },
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (game.kind == LibraryItemKind.TOOL) AppStrings.text(language, "category_tools")
                    else AppStrings.genre(language, game.genre),
                    color = colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (game.kind == LibraryItemKind.GAME && game.playtimeMinutes != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        AppStrings.playtime(language, game.playtimeMinutes),
                        color = colors.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    language: AppLanguage,
    themeMode: AppThemeMode,
    displayName: String,
    updateStatus: AppUpdateStatus,
    onLanguage: (AppLanguage) -> Unit,
    onTheme: (AppThemeMode) -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: (AppUpdateInfo) -> Unit,
    onLogout: () -> Unit
) {
    var languageExpanded by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsCard(title = AppStrings.text(language, "settings_profile")) {
                Text(AppStrings.text(language, "account_connected", displayName), color = colors.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(AppStrings.text(language, "version", BuildConfig.VERSION_NAME), fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
        item {
            SettingsCard(title = AppStrings.text(language, "language")) {
                Text(AppStrings.text(language, "language_help"), color = colors.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedButton(onClick = { languageExpanded = true }, shape = RoundedCornerShape(14.dp)) {
                        Text(language.nativeName)
                    }
                    DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                        AppLanguage.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.nativeName) },
                                onClick = {
                                    languageExpanded = false
                                    onLanguage(option)
                                }
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsCard(title = AppStrings.text(language, "theme")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AppThemeMode.SYSTEM, AppThemeMode.LIGHT, AppThemeMode.DARK).forEach { option ->
                        val label = when (option) {
                            AppThemeMode.SYSTEM -> AppStrings.text(language, "theme_system")
                            AppThemeMode.LIGHT -> AppStrings.text(language, "theme_light")
                            AppThemeMode.DARK -> AppStrings.text(language, "theme_dark")
                        }
                        FilterChip(
                            selected = themeMode == option,
                            onClick = { onTheme(option) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        item {
            SettingsCard(title = AppStrings.text(language, "updates")) {
                Text(AppStrings.text(language, "updates_help"), color = colors.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                UpdateStatusContent(
                    language = language,
                    status = updateStatus,
                    onCheck = onCheckUpdates,
                    onInstall = onInstallUpdate
                )
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error)
            ) {
                Text(AppStrings.text(language, "logout"), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(AppStrings.text(language, "logout_title")) },
            text = { Text(AppStrings.text(language, "logout_message")) },
            confirmButton = {
                Button(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text(AppStrings.text(language, "logout_confirm")) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(AppStrings.text(language, "cancel")) }
            },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
private fun UpdateStatusContent(
    language: AppLanguage,
    status: AppUpdateStatus,
    onCheck: () -> Unit,
    onInstall: (AppUpdateInfo) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    when (status) {
        AppUpdateStatus.NotChecked -> Text(AppStrings.text(language, "update_not_checked"), color = colors.onSurfaceVariant)
        AppUpdateStatus.NotConfigured -> Text(AppStrings.text(language, "update_not_configured"), color = colors.onSurfaceVariant)
        AppUpdateStatus.Checking -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(AppStrings.text(language, "update_checking"), color = colors.onSurfaceVariant)
        }
        AppUpdateStatus.UpToDate -> Text(AppStrings.text(language, "update_latest"), color = colors.secondary)
        is AppUpdateStatus.Available -> {
            Text(AppStrings.text(language, "update_available", status.update.versionName), fontWeight = FontWeight.SemiBold)
            if (status.update.releaseNotes.isNotBlank()) Text(status.update.releaseNotes, color = colors.onSurfaceVariant)
            Button(onClick = { onInstall(status.update) }, modifier = Modifier.fillMaxWidth()) {
                Text(AppStrings.text(language, "update_install"))
            }
        }
        is AppUpdateStatus.PermissionRequired -> {
            Text(AppStrings.text(language, "update_permission"), color = colors.onSurfaceVariant)
            Button(onClick = { onInstall(status.update) }, modifier = Modifier.fillMaxWidth()) {
                Text(AppStrings.text(language, "continue"))
            }
        }
        is AppUpdateStatus.Downloading -> {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(AppStrings.text(language, "update_downloading", status.update.versionName), color = colors.onSurfaceVariant)
        }
        AppUpdateStatus.InstallerOpened -> Text(AppStrings.text(language, "update_installer"), color = colors.onSurfaceVariant)
        is AppUpdateStatus.Failed -> Text(AppStrings.text(language, "update_failed"), color = colors.error)
    }
    if (status !is AppUpdateStatus.Checking && status !is AppUpdateStatus.Downloading) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
            Text(AppStrings.text(language, "check_updates"))
        }
    }
}

private fun extractAuthorizationCode(input: String): String {
    val trimmed = input.trim().removeSurrounding("\"", "\"")
    if (trimmed.startsWith("{")) {
        return runCatching { org.json.JSONObject(trimmed).optString("authorizationCode") }.getOrDefault("")
    }
    runCatching {
        val uri = Uri.parse(trimmed)
        uri.getQueryParameter("code") ?: uri.getQueryParameter("authorizationCode")
    }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return URLDecoder.decode(it, "UTF-8") }
    return Regex("authorizationCode\\s*[=:]\\s*[\\\"]([^\\\"]+)")
        .find(trimmed)?.groupValues?.get(1) ?: trimmed
}

