package com.gothwad.grixchat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gothwad.grixchat.data.GrixDatabase
import com.gothwad.grixchat.data.GrixRepository
import com.gothwad.grixchat.ui.GrixViewModel
import com.gothwad.grixchat.ui.GrixViewModelFactory
import com.gothwad.grixchat.ui.GrixJavascriptInterface
import com.gothwad.grixchat.ui.theme.MyApplicationTheme
import com.gothwad.grixchat.utils.GrixNotificationHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MyApplication)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup global WebView ServiceWorker preferences for robust offline background caching
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val swController = ServiceWorkerController.getInstance()
                val swSettings = swController.serviceWorkerWebSettings
                
                val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                val actuallyOnline = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                swSettings.cacheMode = if (actuallyOnline) {
                    WebSettings.LOAD_DEFAULT
                } else {
                    WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
                swSettings.allowContentAccess = true
                swSettings.allowFileAccess = true
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error configuring ServiceWorkerController on launch", e)
            }
        }

        // Create the notification channels on launch
        GrixNotificationHelper.createNotificationChannel(applicationContext)

        // Setup repository
        val database = GrixDatabase.getDatabase(applicationContext)
        val repository = GrixRepository(database.grixDao())

        // Fetch Real Firebase token asynchronously on launch
        try {
            val hasFirebase = try {
                if (com.google.firebase.FirebaseApp.getApps(applicationContext).isEmpty()) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:1234567890:android:e1234567890abcdef") // Fallback placeholder
                        .setApiKey("placeholder-api-key-to-allow-init")
                        .setProjectId("grixchatlite-placeholder")
                        .build()
                    com.google.firebase.FirebaseApp.initializeApp(applicationContext, options)
                }
                true
            } catch (initEx: Exception) {
                android.util.Log.w("MainActivity", "Could not initialize Firebase dynamically: ${initEx.message}")
                false
            }

            if (hasFirebase) {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        android.util.Log.d("MainActivity", "Successfully retrieved initial FCM token: $token")
                        val sharedPrefs = getSharedPreferences("grix_prefs", android.content.Context.MODE_PRIVATE)
                        sharedPrefs.edit().putString("fcm_token", token).apply()
                    } else {
                        android.util.Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Firebase initialization or token fetch error", e)
        }

        setContent {
            val grixViewModel: GrixViewModel = viewModel(
                factory = GrixViewModelFactory(application, repository)
            )

            val isDarkThemeOverride by grixViewModel.isDarkThemeOverride.collectAsStateWithLifecycle()
            val systemIsDark = isSystemInDarkTheme()
            val useDarkTheme = isDarkThemeOverride ?: systemIsDark

            MyApplicationTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GrixChatScreen(viewModel = grixViewModel, isDarkTheme = useDarkTheme)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GrixChatScreen(viewModel: GrixViewModel, isDarkTheme: Boolean) {
    val context = LocalContext.current
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isError by viewModel.isWebViewError.collectAsStateWithLifecycle()
    val progress by viewModel.loadProgress.collectAsStateWithLifecycle()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    var customFilePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            var results: Array<Uri>? = null
            if (data != null) {
                val dataString = data.dataString
                val clipData = data.clipData
                if (clipData != null) {
                    results = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } else if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                }
            }
            customFilePathCallback?.onReceiveValue(results)
        } else {
            customFilePathCallback?.onReceiveValue(null)
        }
        customFilePathCallback = null
    }

    // Intercept back actions so page history goes back rather than exiting app
    BackHandler(enabled = webViewInstance != null) {
        val webView = webViewInstance
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            (context as? Activity)?.finish()
        }
    }

    // Request setup for all essential device permissions on startup (Notifications, Camera, Microphone, Location, Contacts) except storage/photos to prevent annoying popup
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val deniedPermissions = results.filter { !it.value }.keys
        if (deniedPermissions.isNotEmpty()) {
            android.util.Log.d("MainActivity", "User denied some permissions: $deniedPermissions")
        }
    }

    LaunchedEffect(Unit) {
        val list = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Filter out already granted permissions to avoid redundant prompts
        val ungranted = list.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionsLauncher.launch(ungranted.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Solid Status Bar with theme matching background so the web content does not overlap
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background)
            )

            // Web view / offline core screen area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (!isError) {
                    // Full-screen WebView sitting precisely in the safe frame
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                
                                // Enable hardware acceleration for high-end rendering (e.g. CSS glassmorphism, backdrop filters)
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                                // Synchronously compute current connectivity to bypass race conditions on first frame
                                val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                                val activeNetwork = connectivityManager.activeNetwork
                                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                                val actuallyOnline = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                                // Performance and database caching parameters
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    setGeolocationEnabled(true) // Enable Web Geolocation support
                                    loadsImagesAutomatically = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = false // Prevent forced zoom-out overview scaling which breaks mobile responsive styling
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    textZoom = 100 // Enforce default font scale; prevents system accessibility settings from scrambling column layout
                                    mediaPlaybackRequiresUserGesture = false // Crucial: allows WebRTC / camera video stream to play automatically without showing a play button
                                    
                                    // Handle dynamic dark mode / light mode selection based on system theme
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        try {
                                            isAlgorithmicDarkeningAllowed = isDarkTheme
                                        } catch (e: Exception) {
                                            android.util.Log.e("MainActivity", "Failed to set algorithmic darkening", e)
                                        }
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        try {
                                            @Suppress("DEPRECATION")
                                            forceDark = if (isDarkTheme) {
                                                WebSettings.FORCE_DARK_ON
                                            } else {
                                                WebSettings.FORCE_DARK_OFF
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("MainActivity", "Failed to set force dark", e)
                                        }
                                    }

                                    cacheMode = if (actuallyOnline) {
                                        WebSettings.LOAD_DEFAULT
                                    } else {
                                        WebSettings.LOAD_CACHE_ELSE_NETWORK
                                    }

                                    // Bypass Google OAuth "disallowed_useragent" block
                                    // By removing "Version/X.X" and "; wv", we simulate a clean, standard Chrome mobile browser.
                                    val defaultUA = userAgentString
                                    val customizedUA = defaultUA
                                        .replace("; wv", "")
                                        .replace("Version/\\d+\\.\\d+\\s".toRegex(), "")
                                        .replace("Version/\\d+\\.\\d+".toRegex(), "")
                                    userAgentString = if (customizedUA.isNotEmpty() && customizedUA != defaultUA) {
                                        customizedUA
                                    } else {
                                        "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                    }
                                }

                                // Enable Cookies including third-party cookies (essential for Google Auth / iframe identity tools)
                                val webViewCurrent = this
                                try {
                                    CookieManager.getInstance().apply {
                                        setAcceptCookie(true)
                                        setAcceptThirdPartyCookies(webViewCurrent, true)
                                    }
                                } catch (e: Exception) {
                                    // Guard against rare system webview cookie manager failures
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        viewModel.setLoadProgress(15)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        viewModel.setLoadProgress(100)
                                        
                                        // Inject an observer/evaluator script to capture and mirror website light/dark theme toggle triggers
                                        view?.evaluateJavascript(
                                            """
                                            (function() {
                                                function checkAndUpdateTheme() {
                                                    var isDark = false;
                                                    
                                                    // 1. Check for standard Tailwind/Next/Bootstrap dark mode class lists or data attributes
                                                    if (document.documentElement.classList.contains('dark') || 
                                                        document.body.classList.contains('dark') ||
                                                        document.documentElement.getAttribute('data-theme') === 'dark' ||
                                                        document.body.getAttribute('data-theme') === 'dark' ||
                                                        document.documentElement.classList.contains('theme-dark') ||
                                                        document.body.classList.contains('theme-dark')) {
                                                        isDark = true;
                                                    } else {
                                                        // 2. Fallback: Luma-based analysis of body background color for un-annotated dark themes
                                                        try {
                                                            var bg = window.getComputedStyle(document.body).backgroundColor;
                                                            if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
                                                                var rgb = bg.match(/\d+/g);
                                                                if (rgb && rgb.length >= 3) {
                                                                    var r = parseInt(rgb[0]);
                                                                    var g = parseInt(rgb[1]);
                                                                    var b = parseInt(rgb[2]);
                                                                    var luma = (r * 299 + g * 587 + b * 114) / 1000;
                                                                    if (luma < 120) {
                                                                        isDark = true;
                                                                    }
                                                                }
                                                            }
                                                        } catch(e) {}
                                                    }
                                                    
                                                    if (window.GrixApp && window.GrixApp.setTheme) {
                                                        window.GrixApp.setTheme(isDark);
                                                    }
                                                }
                                                
                                                // Execute immediately on finish
                                                checkAndUpdateTheme();
                                                
                                                // Create a DOM MutationObserver to dynamically react to client-side switch toggles
                                                try {
                                                    var themeObserver = new MutationObserver(function() {
                                                        checkAndUpdateTheme();
                                                    });
                                                    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'data-theme'] });
                                                    if (document.body) {
                                                        themeObserver.observe(document.body, { attributes: true, attributeFilter: ['class', 'data-theme'] });
                                                    }
                                                } catch(err) {}
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            viewModel.setWebViewError(true)
                                        }
                                    }

                                    @Suppress("OVERRIDE_DEPRECATION")
                                    override fun onReceivedError(
                                        view: WebView?,
                                        errorCode: Int,
                                        description: String?,
                                        failingUrl: String?
                                    ) {
                                        if (failingUrl != null && failingUrl.trimEnd('/').equals(viewModel.targetUrl.trimEnd('/'), ignoreCase = true)) {
                                            viewModel.setWebViewError(true)
                                        }
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        super.onProgressChanged(view, newProgress)
                                        viewModel.setLoadProgress(newProgress)
                                    }

                                    override fun onPermissionRequest(request: PermissionRequest?) {
                                        // Grant requested camera/audio/etc permission requests inside the WebView
                                        try {
                                            request?.grant(request?.resources ?: emptyArray())
                                        } catch (e: Exception) {
                                            android.util.Log.e("MainActivity", "WebRTC grant permission error", e)
                                        }
                                    }

                                    override fun onGeolocationPermissionsShowPrompt(
                                        origin: String?,
                                        callback: GeolocationPermissions.Callback?
                                    ) {
                                        // Auto-approve the WebView-level request since OS-level permissions protect the user
                                        callback?.invoke(origin, true, false)
                                    }

                                     override fun onShowFileChooser(
                                        webView: WebView?,
                                        filePathCallback: ValueCallback<Array<Uri>>?,
                                        fileChooserParams: FileChooserParams?
                                    ): Boolean {
                                        customFilePathCallback?.onReceiveValue(null)
                                        customFilePathCallback = filePathCallback
                                        
                                        try {
                                            val intent = fileChooserParams?.createIntent()
                                            if (intent != null) {
                                                fileChooserLauncher.launch(intent)
                                            } else {
                                                filePathCallback?.onReceiveValue(null)
                                                customFilePathCallback = null
                                                return false
                                            }
                                        } catch (e: Exception) {
                                            filePathCallback?.onReceiveValue(null)
                                            customFilePathCallback = null
                                            return false
                                        }
                                        return true
                                    }
                                }

                                // Inject JS push notification / token channel to match website capabilities
                                addJavascriptInterface(
                                    GrixJavascriptInterface(ctx, viewModel),
                                    "GrixApp"
                                )

                                loadUrl(viewModel.targetUrl)
                                webViewInstance = this
                            }
                        },
                        update = { webView ->
                            webView.settings.cacheMode = if (isOnline) {
                                WebSettings.LOAD_DEFAULT
                            } else {
                                WebSettings.LOAD_CACHE_ELSE_NETWORK
                            }
                            
                             // Dynamic theme update for WebView
                             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                 try {
                                     webView.settings.isAlgorithmicDarkeningAllowed = isDarkTheme
                                 } catch (e: Exception) {
                                     // ignore 
                                 }
                             }
                             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                 try {
                                     @Suppress("DEPRECATION")
                                     webView.settings.forceDark = if (isDarkTheme) {
                                         WebSettings.FORCE_DARK_ON
                                     } else {
                                         WebSettings.FORCE_DARK_OFF
                                     }
                                 } catch (e: Exception) {
                                     // ignore
                                 }
                             }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                try {
                                    val swController = ServiceWorkerController.getInstance()
                                    swController.serviceWorkerWebSettings.cacheMode = if (isOnline) {
                                        WebSettings.LOAD_DEFAULT
                                    } else {
                                        WebSettings.LOAD_CACHE_ELSE_NETWORK
                                    }
                                } catch (e: Exception) {
                                    // Ignore failures in updating process-global SW settings
                                }
                            }
                            if (isOnline && isError) {
                                viewModel.setWebViewError(false)
                                webView.loadUrl(viewModel.targetUrl)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("grix_webview_panel")
                    )

                    // Elegantly fade-out loading spinner overlay on page transitions
                    androidx.compose.animation.AnimatedVisibility(
                        visible = progress < 100,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(400))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("page_loader_spinner")
                            )
                        }
                    }
                } else {
                    // Elegant offline recovery screen - minimalist theme matching
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Offline Mode",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Text(
                                text = "Connection Offline",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Make sure your Wi-Fi or cellular network is active and try reloading.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    viewModel.setWebViewError(false)
                                    webViewInstance?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("offline_retry_button")
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry Connection")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Solid Navigation Bar spacer so the bottom bar isn't transparently cutting off WebView
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.safeDrawing)
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
