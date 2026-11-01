package com.pro.videodownloader.player

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.provider.MediaStore
import android.text.InputType
import android.util.Rational
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

data class TranscriptLine(
    val timestampFormatted: String,
    val text: String
)

data class DownloadTaskItem(
    val id: String,
    val title: String,
    val streamUrl: String,
    val pageUrl: String,
    val cookies: String,
    val userAgent: String,
    val targetFile: File,
    var downloadedBytes: Long = 0L,
    var totalBytes: Long = 0L,
    var speedText: String = "0 KB/s",
    var status: String = "Connecting...",
    var errorMessage: String = "",
    val isPaused: AtomicBoolean = AtomicBoolean(false),
    val isCancelled: AtomicBoolean = AtomicBoolean(false),
    val isAudioOnly: Boolean = false
)

class MainActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val downloadExecutor = Executors.newFixedThreadPool(6)

    private lateinit var rootLayout: LinearLayout
    private lateinit var topHeaderLayout: LinearLayout
    private lateinit var topHeaderTitleTv: TextView
    private lateinit var topHeaderSubtitleTv: TextView
    private lateinit var bottomNavLayout: LinearLayout
    private lateinit var contentFrame: FrameLayout

    // 5 DEDICATED TABS: Home, Script Studio, Saved, 4K Player, Vault
    private lateinit var tabBrowserView: LinearLayout
    private lateinit var tabScriptView: LinearLayout
    private lateinit var tabDownloadsView: LinearLayout
    private lateinit var tabPlayerView: LinearLayout
    private lateinit var tabVaultView: LinearLayout

    private val navTabContainers = mutableListOf<LinearLayout>()
    private val navTabIcons = mutableListOf<ImageView>()
    private val navTabLabels = mutableListOf<TextView>()

    // Tab 0: Clean Downloader Home
    private lateinit var urlInput: EditText
    private lateinit var closeBrowserIconBtn: ImageView
    private lateinit var streamBannerBtn: LinearLayout
    private lateinit var streamBannerText: TextView
    private lateinit var shortcutsScroll: ScrollView
    private lateinit var clipboardRadarCard: LinearLayout
    private lateinit var clipboardRadarUrlTv: TextView
    private lateinit var webViewContainer: FrameLayout
    private lateinit var browserWebView: WebView
    private lateinit var webProgressBar: ProgressBar
    private lateinit var desktopModeIconBtn: ImageView
    private lateinit var adBlockIconBtn: ImageView

    // Tab 1: Full-Screen YouTube-to-Transcript Studio (9:16 Layout)
    private lateinit var scriptUrlInput: EditText
    private lateinit var scriptFetchBtn: Button
    private lateinit var scriptStatusBar: ProgressBar
    private lateinit var scriptVideoTitleTv: TextView
    private lateinit var scriptTabTranscriptBtn: TextView
    private lateinit var scriptTabInfoBtn: TextView
    private lateinit var scriptCopyMainBtn: Button
    private lateinit var scriptTimestampToggleBtn: Button
    private lateinit var scriptSaveTxtBtn: Button
    private lateinit var scriptOutputTextTv: TextView
    private lateinit var hiddenTranscriptWebView: WebView

    private val currentTranscriptLines = mutableListOf<TranscriptLine>()
    private var currentScriptVideoTitle = "Paste any YouTube / Video link above"
    private var currentScriptDescription = ""
    private var showTimestampsInScript = false
    private var showingTranscriptTab = true
    private var transcriptJobFinished = AtomicBoolean(false)

    private var adBlockEnabled = true
    private var desktopModeEnabled = false
    private var backgroundPlayEnabled = false
    private var isPlayerFullscreen = false
    private var currentPlaybackSpeed = 1.0f
    private var smoothVolumeTracker = -1f

    private var latestDetectedVideoUrl: String = ""
    private var latestDetectedAudioUrl: String = ""
    private var latestDetectedPageUrl: String = ""
    private var latestDetectedTitle: String = "Video"
    private var isLockedByActiveDomVideo = false
    private var lastDetectedClipboardLink = ""

    private val activeTasks = ConcurrentHashMap<String, DownloadTaskItem>()
    private lateinit var downloadsListContainer: LinearLayout

    private lateinit var videoPlayerBox: FrameLayout
    private lateinit var customVideoView: VideoView
    private lateinit var videoTouchOverlay: FrameLayout
    private lateinit var centerPlayPauseIcon: ImageView
    private lateinit var playerSeekBar: SeekBar
    private lateinit var playerTimeLabel: TextView
    private lateinit var overlayTitleTv: TextView
    private lateinit var speedChipTv: TextView
    private lateinit var bgPlayOverlayIcon: ImageView
    private lateinit var gestureFeedbackBox: LinearLayout
    private lateinit var gestureFeedbackText: TextView
    private lateinit var gestureFeedbackBar: ProgressBar
    private lateinit var playerFileListScroll: ScrollView
    private lateinit var playerFileListContainer: LinearLayout
    private var activeMediaPlayer: MediaPlayer? = null
    private var currentlyPlayingFile: File? = null
    private var isOverlayVisible = true

    private val hideOverlayRunnable = Runnable {
        if (::customVideoView.isInitialized && customVideoView.isPlaying) {
            videoTouchOverlay.visibility = View.GONE
            isOverlayVisible = false
        }
    }

    private lateinit var vaultListContainer: LinearLayout
    private var isVaultUnlocked = false
    private val prefs by lazy { getSharedPreferences("pro_saver_phase10_prefs", Context.MODE_PRIVATE) }

    private val blockedAdDomains = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "taboola.com", "outbrain.com", "popads.net", "propellerads.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.parseColor("#0B0F19")
        window.navigationBarColor = Color.parseColor("#111827")
        requestStoragePermissionsIfNeeded()
        restoreIncompleteDownloads()
        buildCompletePhase10UI()
        setupWebViewEngine()
        setupHiddenTranscriptEngine()
        startPlayerProgressUpdater()
        switchTab(0)
        handleIncomingShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        mainHandler.postDelayed({ checkClipboardForVideoLink() }, 350)
    }

    override fun onPause() {
        super.onPause()
        if (!backgroundPlayEnabled && ::customVideoView.isInitialized && customVideoView.isPlaying) {
            customVideoView.pause()
            centerPlayPauseIcon.setImageResource(R.drawable.ic_p5_play)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank()) {
                switchTab(0)
                urlInput.setText(shared.trim())
                smartGrabOrNavigate(shared.trim())
            }
        }
    }

    private fun checkClipboardForVideoLink() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() ?: return
            if (clip.startsWith("http://") || clip.startsWith("https://")) {
                lastDetectedClipboardLink = clip
                clipboardRadarUrlTv.text = clip
                clipboardRadarCard.visibility = View.VISIBLE
                if (scriptUrlInput.text.isNullOrBlank()) {
                    scriptUrlInput.setText(clip)
                }
            }
        } catch (_: Exception) {}
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun makeRoundedBg(colorHex: String, cornerDp: Int = 12, strokeColorHex: String? = null, strokeDp: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            cornerRadius = dp(cornerDp).toFloat()
            if (strokeColorHex != null) {
                setStroke(dp(strokeDp), Color.parseColor(strokeColorHex))
            }
        }
    }

    private fun makeGradientBg(startHex: String, endHex: String, cornerDp: Int = 14): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(startHex), Color.parseColor(endHex))
        ).apply {
            cornerRadius = dp(cornerDp).toFloat()
        }
    }

    private fun requestStoragePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 101)
            }
        }
    }

    private fun restoreIncompleteDownloads() {
        try {
            val files = getAppMediaDir().listFiles() ?: return
            files.forEach { file ->
                if (file.name.endsWith(".part")) {
                    val cleanName = file.name.removeSuffix(".part")
                    val savedUrl = prefs.getString("url_$cleanName", null)
                    val savedPage = prefs.getString("page_$cleanName", "") ?: ""
                    val savedTotal = prefs.getLong("tot_$cleanName", -1L)
                    if (!savedUrl.isNullOrEmpty()) {
                        val id = cleanName.substringAfterLast("_").substringBefore(".")
                        val task = DownloadTaskItem(
                            id = id,
                            title = cleanName,
                            streamUrl = savedUrl,
                            pageUrl = savedPage,
                            cookies = "",
                            userAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36",
                            targetFile = file,
                            downloadedBytes = file.length(),
                            totalBytes = savedTotal,
                            speedText = "Paused",
                            status = "Paused"
                        )
                        task.isPaused.set(true)
                        activeTasks[id] = task
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun buildCompletePhase10UI() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F19"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        topHeaderLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#111827"))
        }

        val appBadge = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_download)
            setColorFilter(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = makeGradientBg("#EC4899", "#8B5CF6", 12)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(12) }
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topHeaderTitleTv = TextView(this).apply {
            text = "Pro Video Saver"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F9FAFB"))
        }
        topHeaderSubtitleTv = TextView(this).apply {
            text = "No-Login 4K Downloader"
            textSize = 11f
            setTextColor(Color.parseColor("#9CA3AF"))
        }
        titleCol.addView(topHeaderTitleTv)
        titleCol.addView(topHeaderSubtitleTv)

        adBlockIconBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_shield)
            setColorFilter(Color.parseColor("#10B981"))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = makeRoundedBg("#064E3B", 20, "#10B981", 1)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setOnClickListener {
                adBlockEnabled = !adBlockEnabled
                if (adBlockEnabled) {
                    setColorFilter(Color.parseColor("#10B981"))
                    background = makeRoundedBg("#064E3B", 20, "#10B981", 1)
                    Toast.makeText(this@MainActivity, "AdBlock Shield ON", Toast.LENGTH_SHORT).show()
                } else {
                    setColorFilter(Color.parseColor("#9CA3AF"))
                    background = makeRoundedBg("#1F2937", 20, "#374151", 1)
                    Toast.makeText(this@MainActivity, "AdBlock Shield OFF", Toast.LENGTH_SHORT).show()
                }
            }
        }

        topHeaderLayout.addView(appBadge)
        topHeaderLayout.addView(titleCol)
        topHeaderLayout.addView(adBlockIconBtn)

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        buildBrowserTab()
        buildScriptStudioTab()
        buildDownloadsTab()
        buildPlayerTab()
        buildVaultTab()

        contentFrame.addView(tabBrowserView)
        contentFrame.addView(tabScriptView)
        contentFrame.addView(tabDownloadsView)
        contentFrame.addView(tabPlayerView)
        contentFrame.addView(tabVaultView)

        bottomNavLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
        }

        val tabs = listOf(
            Pair(R.drawable.ic_p5_browser, "Home"),
            Pair(R.drawable.ic_p5_script, "Script"),
            Pair(R.drawable.ic_p5_download, "Saved"),
            Pair(R.drawable.ic_p5_player, "Player"),
            Pair(R.drawable.ic_p5_lock, "Vault")
        )

        tabs.forEachIndexed { index, (iconRes, label) ->
            val tabBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(6), dp(2), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                }
                setOnClickListener { switchTab(index) }
            }
            val icon = ImageView(this).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }
            val lbl = TextView(this).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, dp(4), 0, 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }
            navTabContainers.add(tabBox)
            navTabIcons.add(icon)
            navTabLabels.add(lbl)
            tabBox.addView(icon)
            tabBox.addView(lbl)
            bottomNavLayout.addView(tabBox)
        }

        rootLayout.addView(topHeaderLayout)
        rootLayout.addView(contentFrame)
        rootLayout.addView(bottomNavLayout)
        setContentView(rootLayout)
    }

    private fun buildBrowserTab() {
        tabBrowserView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val omniboxCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = makeRoundedBg("#161F30", 16, "#26334D", 1)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(12), dp(10), dp(12), dp(6))
            }
        }

        val omniRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeRoundedBg("#090D16", 14, "#1F2937", 1)
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }

        val homeIconBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_home)
            setColorFilter(Color.parseColor("#38BDF8"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener { resetToBrowserHomeScreen() }
        }

        closeBrowserIconBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_close)
            setColorFilter(Color.parseColor("#F87171"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener { resetToBrowserHomeScreen() }
        }

        urlInput = EditText(this).apply {
            hint = "Paste video link or search..."
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            textSize = 13f
            setSingleLine(true)
            background = null
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val pasteIconBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_paste)
            setColorFilter(Color.parseColor("#38BDF8"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(4) }
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()?.trim()
                if (!clip.isNullOrEmpty()) {
                    urlInput.setText(clip)
                    smartGrabOrNavigate(clip)
                } else {
                    Toast.makeText(this@MainActivity, "Clipboard empty", Toast.LENGTH_SHORT).show()
                }
            }
        }

        desktopModeIconBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_device)
            setColorFilter(Color.parseColor("#9CA3AF"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(4) }
            setOnClickListener {
                desktopModeEnabled = !desktopModeEnabled
                applyWebViewUserAgent()
                setColorFilter(if (desktopModeEnabled) Color.parseColor("#EC4899") else Color.parseColor("#9CA3AF"))
                if (webViewContainer.visibility == View.VISIBLE) browserWebView.reload()
            }
        }

        val goGrabIconBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_download)
            setColorFilter(Color.WHITE)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = makeGradientBg("#EC4899", "#8B5CF6", 12)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setOnClickListener {
                val raw = urlInput.text.toString().trim()
                if (raw.isNotEmpty()) smartGrabOrNavigate(raw)
                else Toast.makeText(this@MainActivity, "Paste video link first", Toast.LENGTH_SHORT).show()
            }
        }

        omniRow.addView(homeIconBtn)
        omniRow.addView(closeBrowserIconBtn)
        omniRow.addView(urlInput)
        omniRow.addView(pasteIconBtn)
        omniRow.addView(desktopModeIconBtn)
        omniRow.addView(goGrabIconBtn)

        streamBannerBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = makeGradientBg("#10B981", "#059669", 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
            val ic = ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_p5_download)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(8) }
            }
            streamBannerText = TextView(this@MainActivity).apply {
                text = "⚡ Active Video Locked • Tap to Download"
                setTextColor(Color.WHITE)
                textSize = 12f
                maxLines = 1
                setTypeface(null, Typeface.BOLD)
            }
            addView(ic)
            addView(streamBannerText)
            setOnClickListener { lockCurrentVisibleVideoAndShowPicker() }
        }

        webProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).apply { topMargin = dp(6) }
        }

        omniboxCard.addView(omniRow)
        omniboxCard.addView(streamBannerBtn)
        omniboxCard.addView(webProgressBar)

        shortcutsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val dashboardCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(24))
        }

        clipboardRadarCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = makeRoundedBg("#1E1B4B", 16, "#8B5CF6", 1)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }
        val radarTitle = TextView(this).apply {
            text = "✨ Copied Link Ready"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#C4B5FD"))
        }
        clipboardRadarUrlTv = TextView(this).apply {
            text = ""
            textSize = 11f
            maxLines = 1
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(10))
        }
        val radarBtnsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val radarDlBtn = Button(this).apply {
            text = "⚡ Download Video"
            textSize = 11f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeGradientBg("#EC4899", "#8B5CF6", 10)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(6) }
            setOnClickListener {
                urlInput.setText(lastDetectedClipboardLink)
                smartGrabOrNavigate(lastDetectedClipboardLink)
            }
        }
        val radarScriptBtn = Button(this).apply {
            text = "📝 Open in Script Studio"
            textSize = 11f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            background = makeRoundedBg("#FACC15", 10)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(6) }
            setOnClickListener {
                scriptUrlInput.setText(lastDetectedClipboardLink)
                switchTab(1)
                startFullTranscriptExtraction(lastDetectedClipboardLink)
            }
        }
        radarBtnsRow.addView(radarDlBtn)
        radarBtnsRow.addView(radarScriptBtn)
        clipboardRadarCard.addView(radarTitle)
        clipboardRadarCard.addView(clipboardRadarUrlTv)
        clipboardRadarCard.addView(radarBtnsRow)
        dashboardCol.addView(clipboardRadarCard)

        val sectionPlatformsTv = TextView(this).apply {
            text = "🌐 SUPPORTED PLATFORMS"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(dp(4), dp(2), dp(4), dp(10))
        }
  dashboardCol.addView(sectionPlatformsTv)

        val gridCard = GridLayout(this).apply {
            columnCount = 4
            setPadding(dp(10), dp(16), dp(10), dp(16))
            background = makeRoundedBg("#161F30", 20, "#26334D", 1)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        data class BrandSite(val name: String, val iconRes: Int, val colorStart: String, val colorEnd: String, val url: String)
        val sites = listOf(
            BrandSite("Instagram", R.drawable.ic_brand_instagram, "#F58529", "#DD2A7B", "https://www.instagram.com/reels/"),
            BrandSite("TikTok", R.drawable.ic_brand_tiktok, "#00F2FE", "#FE2C55", "https://www.tiktok.com/"),
            BrandSite("Facebook", R.drawable.ic_brand_facebook, "#1877F2", "#0D5BD9", "https://m.facebook.com/watch/"),
            BrandSite("Kwai", R.drawable.ic_brand_kwai, "#FF7700", "#FF3D00", "https://www.kwai.com/"),
            BrandSite("Bilibili", R.drawable.ic_brand_bilibili, "#00A1D6", "#FB7299", "https://m.bilibili.com/"),
            BrandSite("Pinterest", R.drawable.ic_brand_pinterest, "#E60023", "#AD081B", "https://www.pinterest.com/videos/"),
            BrandSite("Dailymotion", R.drawable.ic_brand_dailymotion, "#0066DC", "#004799", "https://www.dailymotion.com/"),
            BrandSite("Vimeo", R.drawable.ic_brand_vimeo, "#1AB7EA", "#0D8ECF", "https://vimeo.com/watch"),
            BrandSite("X / Twitter", R.drawable.ic_brand_x, "#374151", "#111827", "https://x.com/explore"),
            BrandSite("Threads", R.drawable.ic_brand_instagram, "#4B5563", "#1F2937", "https://www.threads.net/"),
            BrandSite("Likee", R.drawable.ic_p5_play, "#EC4899", "#F43F5E", "https://likee.video/"),
            BrandSite("IMDB Video", R.drawable.ic_p5_player, "#F59E0B", "#B45309", "https://m.imdb.com/trailers/")
        )

        sites.forEach { site ->
            val itemBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(12), dp(4), dp(12))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                setOnClickListener {
                    urlInput.setText(site.url)
                    navigateToInputUrl(site.url)
                }
            }
            val iconCircle = ImageView(this).apply {
                setImageResource(site.iconRes)
                setColorFilter(Color.WHITE)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = makeGradientBg(site.colorStart, site.colorEnd, 18)
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
            }
            val label = TextView(this).apply {
                text = site.name
                textSize = 11f
                setTextColor(Color.parseColor("#E5E7EB"))
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            }
            itemBox.addView(iconCircle)
            itemBox.addView(label)
            gridCard.addView(itemBox)
        }
        dashboardCol.addView(gridCard)
        shortcutsScroll.addView(dashboardCol)

        webViewContainer = FrameLayout(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        browserWebView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        webViewContainer.addView(browserWebView)

        tabBrowserView.addView(omniboxCard)
        tabBrowserView.addView(shortcutsScroll)
        tabBrowserView.addView(webViewContainer)
    }

    private fun buildScriptStudioTab() {
        tabScriptView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Top Yellow/Dark Hero Card inspired by youtubetotranscript.com
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = makeRoundedBg("#161F30", 18, "#FACC15", 1)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        }

        val urlBoxRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeRoundedBg("#090D16", 12, "#374151", 1)
            setPadding(dp(10), dp(4), dp(6), dp(4))
        }

        scriptUrlInput = EditText(this).apply {
            hint = "Paste YouTube URL here..."
            setHintTextColor(Color.parseColor("#9CA3AF"))
            setTextColor(Color.WHITE)
            textSize = 13f
            setSingleLine(true)
            background = null
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val quickPasteBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_paste)
            setColorFilter(Color.parseColor("#FACC15"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()?.trim()
                if (!clip.isNullOrEmpty()) {
                    scriptUrlInput.setText(clip)
                    startFullTranscriptExtraction(clip)
                }
            }
        }

        urlBoxRow.addView(scriptUrlInput)
        urlBoxRow.addView(quickPasteBtn)

        scriptFetchBtn = Button(this).apply {
            text = "⚡ Get Free Transcript"
            textSize = 13f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            background = makeRoundedBg("#FACC15", 12)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                topMargin = dp(10)
            }
            setOnClickListener {
                val link = scriptUrlInput.text.toString().trim()
                if (link.isNotEmpty()) {
                    startFullTranscriptExtraction(link)
                } else {
                    Toast.makeText(this@MainActivity, "Paste YouTube URL first!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        scriptStatusBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply { topMargin = dp(8) }
        }

        heroCard.addView(urlBoxRow)
        heroCard.addView(scriptFetchBtn)
        heroCard.addView(scriptStatusBar)

        // Video Title Display
        scriptVideoTitleTv = TextView(this).apply {
            text = "🎬 Paste a YouTube link above & tap Get Free Transcript"
            textSize = 14f
            maxLines = 2
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }

        // Sub-Tabs: [ 📄 Transcript ] vs [ 📌 Video Info & Chapters ]
        val subTabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = makeRoundedBg("#111827", 12, "#1F2937", 1)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }

        scriptTabTranscriptBtn = TextView(this).apply {
            text = "📄 Word-by-Word Transcript"
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = makeRoundedBg("#1E293B", 10, "#FACC15", 1)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                showingTranscriptTab = true
                refreshScriptStudioDisplay()
            }
        }

        scriptTabInfoBtn = TextView(this).apply {
            text = "📌 Description & Chapters"
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = null
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                showingTranscriptTab = false
                refreshScriptStudioDisplay()
            }
        }
        subTabsRow.addView(scriptTabTranscriptBtn)
        subTabsRow.addView(scriptTabInfoBtn)

        // Action Bar: [ 📋 1-Click Copy ] [ ⏱️ Timestamp: OFF ] [ 💾 Save .TXT ]
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }

        scriptCopyMainBtn = Button(this).apply {
            text = "📋 Copy Script"
            textSize = 11f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeGradientBg("#F59E0B", "#D97706", 10)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1.2f).apply { rightMargin = dp(6) }
            setOnClickListener {
                val txt = scriptOutputTextTv.text.toString()
                if (txt.isNotBlank()) {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("YouTube Transcript", txt))
                    Toast.makeText(this@MainActivity, "✅ Copied (${txt.length} chars) to Clipboard!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        scriptTimestampToggleBtn = Button(this).apply {
            text = "⏱️ Time: OFF"
            textSize = 11f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeRoundedBg("#1E293B", 10, "#4B5563", 1)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(6) }
            setOnClickListener {
                showTimestampsInScript = !showTimestampsInScript
                text = if (showTimestampsInScript) "⏱️ Time: ON" else "⏱️ Time: OFF"
                refreshScriptStudioDisplay()
            }
        }

        scriptSaveTxtBtn = Button(this).apply {
            text = "💾 Save .TXT"
            textSize = 11f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeRoundedBg("#065F46", 10, "#10B981", 1)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 0.9f)
            setOnClickListener {
                val txt = scriptOutputTextTv.text.toString()
                if (txt.isNotBlank()) {
                    val cleanTitle = currentScriptVideoTitle.replace(Regex("[^a-zA-Z0-9]"), "_").take(20).ifBlank { "Transcript" }
                    val f = File(getAppMediaDir(), "Script_${cleanTitle}.txt")
                    f.writeText(txt)
                    Toast.makeText(this@MainActivity, "Saved: ${f.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        actionsRow.addView(scriptCopyMainBtn)
        actionsRow.addView(scriptTimestampToggleBtn)
        actionsRow.addView(scriptSaveTxtBtn)

        // Full-Screen Scrollable Transcript Reader Box
        val readerScroll = ScrollView(this).apply {
            background = makeRoundedBg("#090D16", 14, "#1F2937", 1)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        scriptOutputTextTv = TextView(this).apply {
            text = "Your full word-by-word video transcript will appear here.\n\n• Supports 5-minute to 60+ minute YouTube videos & Shorts\n• 1-Click Copy to paste directly into Notes or ChatGPT\n• Toggle Timestamps ON or OFF anytime"
            textSize = 13f
            setLineSpacing(dp(3).toFloat(), 1.1f)
            setTextColor(Color.parseColor("#E5E7EB"))
            setTextIsSelectable(true)
        }
        readerScroll.addView(scriptOutputTextTv)

        hiddenTranscriptWebView = WebView(this).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(1, 1)
        }

        tabScriptView.addView(heroCard)
        tabScriptView.addView(scriptVideoTitleTv)
        tabScriptView.addView(subTabsRow)
        tabScriptView.addView(actionsRow)
        tabScriptView.addView(readerScroll)
        tabScriptView.addView(hiddenTranscriptWebView)
    }

    private fun refreshScriptStudioDisplay() {
        if (showingTranscriptTab) {
            scriptTabTranscriptBtn.background = makeRoundedBg("#1E293B", 10, "#FACC15", 1)
            scriptTabTranscriptBtn.setTextColor(Color.WHITE)
            scriptTabInfoBtn.background = null
            scriptTabInfoBtn.setTextColor(Color.parseColor("#9CA3AF"))

            if (currentTranscriptLines.isEmpty()) {
                scriptOutputTextTv.text = if (currentScriptDescription.isNotEmpty()) {
                    "⚠️ Spoken subtitle tracks were not found on this video, or are still loading. Tap '📌 Description & Chapters' tab to view creator notes."
                } else {
                    "Paste a YouTube link above and tap '⚡ Get Free Transcript'."
                }
            } else {
                val sb = StringBuilder()
                currentTranscriptLines.forEach { line ->
                    if (showTimestampsInScript) {
                        sb.append("[${line.timestampFormatted}] ").append(line.text).append("\n\n")
                    } else {
                        sb.append(line.text).append(" ")
                    }
                }
                val finalStr = sb.toString().trim()
                scriptOutputTextTv.text = finalStr
                scriptCopyMainBtn.text = "📋 Copy (${finalStr.length})"
            }
        } else {
            scriptTabInfoBtn.background = makeRoundedBg("#1E293B", 10, "#FACC15", 1)
            scriptTabInfoBtn.setTextColor(Color.WHITE)
            scriptTabTranscriptBtn.background = null
            scriptTabTranscriptBtn.setTextColor(Color.parseColor("#9CA3AF"))
            scriptOutputTextTv.text = currentScriptDescription.ifBlank { "No description available." }
        }
    }

    private fun applyWebViewUserAgent() {
        browserWebView.settings.userAgentString = if (desktopModeEnabled) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        } else {
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewEngine() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(browserWebView, true)
        }

        browserWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        applyWebViewUserAgent()

        browserWebView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onActiveViewportVideoLocked(videoUrl: String, pageUrl: String, pageTitle: String) {
                if (isValidMediaStreamUrl(videoUrl)) {
                    mainHandler.post {
                        isLockedByActiveDomVideo = true
                        registerDetectedStream(videoUrl, pageUrl, pageTitle)
                    }
                }
            }

            @JavascriptInterface
            fun onDeepStreamFound(videoUrl: String, pageUrl: String, pageTitle: String) {
                if (isValidMediaStreamUrl(videoUrl)) {
                    mainHandler.post {
                        if (!isLockedByActiveDomVideo || latestDetectedVideoUrl.isEmpty()) {
                            registerDetectedStream(videoUrl, pageUrl, pageTitle)
                        }
                    }
                }
            }
        }, "ProVideoBridge")

        browserWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress in 1..99) {
                    webProgressBar.visibility = View.VISIBLE
                    webProgressBar.progress = newProgress
                } else {
                    webProgressBar.visibility = View.GONE
                }
            }
        }

        browserWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUrl = request?.url?.toString() ?: return false
                if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                    if (targetUrl.contains("play.google.com/store") || targetUrl.contains("apps.apple.com")) return true
                    return false
                }
                return true
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                val lower = reqUrl.lowercase(Locale.ROOT)
                if (adBlockEnabled && blockedAdDomains.any { lower.contains(it) }) {
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
                if (isValidMediaStreamUrl(reqUrl)) {
                    mainHandler.post {
                        if (!isLockedByActiveDomVideo && latestDetectedVideoUrl.isEmpty()) {
                            val currentPage = browserWebView.url ?: latestDetectedPageUrl
                            val currentTitle = browserWebView.title ?: "Social_Video"
                            registerDetectedStream(reqUrl, currentPage, currentTitle)
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrEmpty() && url != "about:blank") {
                    urlInput.setText(url)
                    latestDetectedPageUrl = url
                    CookieManager.getInstance().flush()
                    injectViewportCenterVideoScannerJS()
                }
            }
        }
    }

    private fun injectViewportCenterVideoScannerJS() {
        val jsCode = """
            (function() {
                function findActiveCenterVideo() {
                    try {
                        var vids = document.querySelectorAll('video');
                        var bestUrl = '';
                        var bestScore = -999999;
                        var viewCenterY = window.innerHeight / 2;

                        for (var i = 0; i < vids.length; i++) {
                            var v = vids[i];
                            var rect = v.getBoundingClientRect();
                            if (rect.height < 40 || rect.bottom < 0 || rect.top > window.innerHeight) continue;

                            var src = v.currentSrc || v.src || '';
                            if (!src || src.indexOf('blob:') === 0) {
                                var sTags = v.querySelectorAll('source');
                                for (var j = 0; j < sTags.length; j++) {
                                    if (sTags[j].src && sTags[j].src.indexOf('http') === 0) {
                                        src = sTags[j].src;
                                        break;
                                    }
                                }
                            }
                            if (!src || src.indexOf('http') !== 0) continue;

                            var vidCenterY = rect.top + (rect.height / 2);
                            var distFromCenter = Math.abs(viewCenterY - vidCenterY);
                            var score = 10000 - distFromCenter;
                            if (!v.paused && v.currentTime > 0) score += 50000;
                            if (v.readyState >= 2) score += 5000;

                            if (score > bestScore) {
                                bestScore = score;
                                bestUrl = src;
                            }
                        }

                        if (bestUrl) {
                            var cleanTitle = document.title || 'Active_Video';
                            ProVideoBridge.onActiveViewportVideoLocked(bestUrl, window.location.href, cleanTitle);
                        }
                    } catch(e) {}
                }
                findActiveCenterVideo();
                if (!window.__proViewportTracker) {
                    window.__proViewportTracker = setInterval(findActiveCenterVideo, 650);
                    document.addEventListener('play', findActiveCenterVideo, true);
                    window.addEventListener('scroll', findActiveCenterVideo, { passive: true });
                    document.addEventListener('touchend', function() { setTimeout(findActiveCenterVideo, 250); }, { passive: true });
                }
            })();
        """.trimIndent()
        browserWebView.evaluateJavascript(jsCode, null)
    }

    private fun lockCurrentVisibleVideoAndShowPicker() {
        if (webViewContainer.visibility == View.VISIBLE) {
            val instantLockJs = """
                (function() {
                    try {
                        var vids = document.querySelectorAll('video');
                        var bestUrl = '';
                        var bestScore = -999999;
                        var viewCenterY = window.innerHeight / 2;
                        for (var i = 0; i < vids.length; i++) {
                            var v = vids[i];
                            var rect = v.getBoundingClientRect();
                            if (rect.height < 40 || rect.bottom < 0 || rect.top > window.innerHeight) continue;
                            var src = v.currentSrc || v.src || '';
                            if (!src || src.indexOf('http') !== 0) continue;
                            var dist = Math.abs(viewCenterY - (rect.top + rect.height / 2));
                            var score = 10000 - dist + (!v.paused ? 50000 : 0);
                            if (score > bestScore) { bestScore = score; bestUrl = src; }
                        }
                        return bestUrl;
                    } catch(e) { return ''; }
                })();
            """.trimIndent()

            browserWebView.evaluateJavascript(instantLockJs) { result ->
                val cleaned = result?.trim()?.removeSurrounding("\"")?.replace("\\/", "/") ?: ""
                if (cleaned.startsWith("http") && isValidMediaStreamUrl(cleaned)) {
                    isLockedByActiveDomVideo = true
                    latestDetectedVideoUrl = cleaned
                }
                showQualityPickerDialog()
            }
        } else {
            showQualityPickerDialog()
        }
    }

    private fun isValidMediaStreamUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (lower.startsWith("blob:") || lower.startsWith("data:")) return false
        if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".js") || lower.endsWith(".css")) return false
        return lower.contains(".mp4") ||
                lower.contains("video_mp4") ||
                lower.contains("v16-webapp") ||
                lower.contains("v19-webapp") ||
                (lower.contains("tiktokcdn.com") && (lower.contains("video") || lower.contains("mime_type=video"))) ||
                (lower.contains("cdninstagram.com") && lower.contains(".mp4")) ||
                (lower.contains("fbcdn.net") && lower.contains(".mp4")) ||
                (lower.contains("bilivideo.com") && lower.contains(".mp4")) ||
                (lower.contains("kwai") && lower.contains(".mp4")) ||
                (lower.contains("kspkg.com") && lower.contains(".mp4")) ||
                lower.contains("googlevideo.com/videoplayback")
    }

    private fun registerDetectedStream(streamUrl: String, pageUrl: String, title: String, audioUrl: String = "") {
        latestDetectedVideoUrl = streamUrl
        if (audioUrl.isNotEmpty()) latestDetectedAudioUrl = audioUrl
        if (pageUrl.isNotEmpty()) latestDetectedPageUrl = pageUrl
        if (title.isNotEmpty()) latestDetectedTitle = title
        streamBannerBtn.visibility = View.VISIBLE
        val shortName = title.take(26).ifBlank { "Active Video" }
        streamBannerText.text = "⚡ Locked: $shortName • Tap to Download"
    }

    private fun smartGrabOrNavigate(rawInput: String) {
        val cleaned = rawInput.trim()
        if (cleaned.isEmpty()) return
        val lower = cleaned.lowercase(Locale.ROOT)

        if (lower.endsWith(".mp4") || lower.contains(".mp4?")) {
            isLockedByActiveDomVideo = true
            registerDetectedStream(cleaned, cleaned, "Direct_4K_Video")
            showQualityPickerDialog()
            return
        }

        val isDirectPost = (lower.contains("instagram.com/reel/") ||
                lower.contains("instagram.com/reels/") ||
                lower.contains("instagram.com/p/") ||
                lower.contains("instagram.com/tv/") ||
                (lower.contains("tiktok.com/") && (lower.contains("/video/") || lower.contains("vm.tiktok") || lower.contains("vt.tiktok") || lower.contains("/t/"))) ||
                lower.contains("fb.watch") ||
                lower.contains("facebook.com/reel/") ||
                lower.contains("facebook.com/share/") ||
                lower.contains("facebook.com/watch") ||
                (lower.contains("x.com/") && lower.contains("/status/")) ||
                lower.contains("pin.it/") ||
                lower.contains("pinterest.com/pin/") ||
                lower.contains("kwai.com/") ||
                lower.contains("kck.ai/") ||
                lower.contains("bilibili.com/video/"))

        if (isDirectPost) {
            Toast.makeText(this, "⚡ Bypassing Login & Extracting Direct HD Stream...", Toast.LENGTH_SHORT).show()
            resolveSocialVideoMetadata(cleaned)
            return
        }

        navigateToInputUrl(cleaned)
    }

    private fun resolveSocialVideoMetadata(socialUrl: String) {
        downloadExecutor.submit {
            var resolvedVideo: String? = null
            var resolvedAudio: String? = null
            var resolvedTitle = "Social_HD_Video"
            val lower = socialUrl.lowercase(Locale.ROOT)

            try {
                if (lower.contains("tiktok.com")) {
                    val api = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(socialUrl, "UTF-8")
                    val jsonStr = httpGetText(api, "Mozilla/5.0", 8000)
                    val root = JSONObject(jsonStr)
                    if (root.optInt("code") == 0) {
                        val data = root.optJSONObject("data")
                        resolvedVideo = data?.optString("hdplay")?.ifEmpty { data.optString("play") }
                        resolvedAudio = data?.optString("music")
                        resolvedTitle = data?.optString("title", "TikTok_Video")?.take(30) ?: "TikTok_Video"
                    }
                }

                if (resolvedVideo.isNullOrEmpty() && lower.contains("instagram.com")) {
                    val shortcodeMatch = Regex("""/(?:reel|reels|p|tv)/([A-Za-z0-9_-]+)""").find(socialUrl)
                    val shortcode = shortcodeMatch?.groupValues?.get(1) ?: ""
                    val candidates = mutableListOf<String>()
                    if (shortcode.isNotEmpty()) {
                        candidates.add("https://www.ddinstagram.com/p/$shortcode/")
                        candidates.add("https://www.vxinstagram.com/reel/$shortcode/")
                        candidates.add("https://www.instagram.com/p/$shortcode/embed/captioned/")
                        candidates.add("https://www.instagram.com/reel/$shortcode/embed/")
                    }
                    candidates.add(socialUrl)

                    for (u in candidates) {
                        try {
                            val ua = if (u.contains("/embed")) {
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            } else {
                                "TelegramBot (like TwitterBot)"
                            }
                            val html = httpGetText(u, ua, 7000)
                            val m = Regex("""property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
                                ?: Regex("""content=["']([^"']+)["']\s+property=["']og:video""", RegexOption.IGNORE_CASE).find(html)
                                ?: Regex(""""video_url"\s*:\s*"([^"]+)"""").find(html)
                                ?: Regex("""video_url\\?":\\?"([^"\\]+)""").find(html)
                            if (m != null) {
                                val candidateUrl = m.groupValues[1]
                                    .replace("\\\\u0026", "&")
                                    .replace("\\u0026", "&")
                                    .replace("\\\\/", "/")
                                    .replace("\\/", "/")
                                    .replace("&amp;", "&")
                                if (candidateUrl.startsWith("http")) {
                                    resolvedVideo = candidateUrl
                                    resolvedTitle = "IG_Reel_$shortcode"
                                    break
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (resolvedVideo.isNullOrEmpty()) {
                    val userAgents = listOf(
                        "TelegramBot (like TwitterBot)",
                        "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
                    )
                    for (ua in userAgents) {
                        try {
                            val html = httpGetText(socialUrl, ua, 7000)
                            val m = Regex("""property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
                                ?: Regex(""""playable_url_quality_hd"\s*:\s*"([^"]+)"""").find(html)
                                ?: Regex(""""playable_url"\s*:\s*"([^"]+)"""").find(html)
                                ?: Regex(""""browser_native_hd_url"\s*:\s*"([^"]+)"""").find(html)
                                ?: Regex(""""browser_native_sd_url"\s*:\s*"([^"]+)"""").find(html)
                                ?: Regex(""""video_url"\s*:\s*"([^"]+)"""").find(html)
                            if (m != null) {
                                val candidate = m.groupValues[1]
                                    .replace("\\u0026", "&")
                                    .replace("\\/", "/")
                                    .replace("&amp;", "&")
                                if (candidate.startsWith("http")) {
                                    resolvedVideo = candidate
                                    break
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}

            mainHandler.post {
                if (!resolvedVideo.isNullOrEmpty() && resolvedVideo!!.startsWith("http")) {
                    isLockedByActiveDomVideo = true
                    registerDetectedStream(resolvedVideo!!, socialUrl, resolvedTitle, resolvedAudio ?: "")
                    showQualityPickerDialog()
                } else {
                    navigateToInputUrl(socialUrl)
                }
            }
        }
    }

    private fun httpGetText(urlStr: String, ua: String, timeoutMs: Int = 8000, cookieHeader: String = ""): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            if (cookieHeader.isNotEmpty()) {
                setRequestProperty("Cookie", cookieHeader)
            }
        }
        return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
    }

    private fun navigateToInputUrl(rawInput: String) {
        val cleaned = rawInput.trim()
        if (cleaned.isEmpty()) return
        latestDetectedVideoUrl = ""
        isLockedByActiveDomVideo = false
        streamBannerBtn.visibility = View.GONE
        shortcutsScroll.visibility = View.GONE
        webViewContainer.visibility = View.VISIBLE
        closeBrowserIconBtn.visibility = View.VISIBLE
        desktopModeIconBtn.visibility = View.VISIBLE

        val finalUrl = when {
            cleaned.startsWith("http://") || cleaned.startsWith("https://") -> cleaned
            cleaned.contains(".") && !cleaned.contains(" ") -> "https://$cleaned"
            else -> "https://www.google.com/search?q=${Uri.encode(cleaned)}"
        }
    latestDetectedPageUrl = finalUrl
        urlInput.setText(finalUrl)
        browserWebView.stopLoading()
        browserWebView.loadUrl(finalUrl)
    }

    private fun resetToBrowserHomeScreen() {
        browserWebView.stopLoading()
        browserWebView.loadUrl("about:blank")
        latestDetectedVideoUrl = ""
        isLockedByActiveDomVideo = false
        streamBannerBtn.visibility = View.GONE
        webProgressBar.visibility = View.GONE
        webViewContainer.visibility = View.GONE
        closeBrowserIconBtn.visibility = View.GONE
        desktopModeIconBtn.visibility = View.GONE
        shortcutsScroll.visibility = View.VISIBLE
        urlInput.setText("")
        checkClipboardForVideoLink()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupHiddenTranscriptEngine() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(hiddenTranscriptWebView, true)
        }
        hiddenTranscriptWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }
        hiddenTranscriptWebView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onBrowserTranscriptFetched(rawPayload: String, pageTitle: String) {
                if (transcriptJobFinished.get()) return
                val parsed = parseTranscriptLinesFromRaw(rawPayload)
                if (parsed.isNotEmpty()) {
                    transcriptJobFinished.set(true)
                    mainHandler.post {
                        hiddenTranscriptWebView.loadUrl("about:blank")
                        currentTranscriptLines.clear()
                        currentTranscriptLines.addAll(parsed)
                        if (pageTitle.isNotBlank()) currentScriptVideoTitle = pageTitle.replace("- YouTube", "").trim()
                        scriptStatusBar.visibility = View.GONE
                        scriptFetchBtn.text = "⚡ Get Free Transcript"
                        scriptVideoTitleTv.text = "🎬 $currentScriptVideoTitle"
                        showingTranscriptTab = true
                        refreshScriptStudioDisplay()
                        Toast.makeText(this@MainActivity, "✅ Full Transcript Extracted (${parsed.size} lines)!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }, "TranscriptBridge")

        hiddenTranscriptWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url.isNullOrEmpty() || url == "about:blank" || transcriptJobFinished.get()) return
                val jsHarvester = """
                    (function() {
                        setTimeout(function() {
                            try {
                                var pr = window.ytInitialPlayerResponse;
                                var tracks = pr && pr.captions && pr.captions.playerCaptionsTracklistRenderer && pr.captions.playerCaptionsTracklistRenderer.captionTracks;
                                if (tracks && tracks.length > 0) {
                                    var tUrl = tracks[0].baseUrl;
                                    fetch(tUrl).then(function(r) { return r.text(); }).then(function(txt) {
                                        if (txt && txt.length > 20) {
                                            TranscriptBridge.onBrowserTranscriptFetched(txt, document.title || '');
                                        }
                                    });
                                }
                            } catch(e) {}
                        }, 900);
                    })();
                """.trimIndent()
                hiddenTranscriptWebView.evaluateJavascript(jsHarvester, null)
            }
        }
    }

    private fun extractYoutubeVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""(?:v=|/v/|youtu\.be/|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{10,12})"""),
            Regex("""^([A-Za-z0-9_-]{11})$""")
        )
        for (p in patterns) {
            val m = p.find(url.trim())
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private fun startFullTranscriptExtraction(videoUrl: String) {
        val cleanedUrl = videoUrl.trim()
        if (cleanedUrl.isEmpty()) return

        transcriptJobFinished.set(false)
        currentTranscriptLines.clear()
        currentScriptDescription = ""
        scriptStatusBar.visibility = View.VISIBLE
        scriptFetchBtn.text = "⏳ Extracting Full Transcript..."
        scriptVideoTitleTv.text = "🔍 Connecting to YouTube Transcript Engine..."
        scriptOutputTextTv.text = "Fetching word-by-word spoken transcript... Please wait 2-4 seconds."

        val ytId = extractYoutubeVideoId(cleanedUrl)
        if (!ytId.isNullOrEmpty()) {
            hiddenTranscriptWebView.loadUrl("https://www.youtube.com/watch?v=$ytId&hl=en")
        }

        downloadExecutor.submit {
            val extractedLines = mutableListOf<TranscriptLine>()
            var fetchedTitle = ""
            var fetchedDesc = ""

            try {
                val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                val consentCookie = "CONSENT=YES+cb.20210328-17-p0.en+FX+410; PREF=hl=en"

                if (!ytId.isNullOrEmpty()) {
                    // STEP 1: Fetch YouTube Watch Page once to get Cookies, VisitorData, getTranscriptEndpoint.params & captionTracks
                    val watchUrl = "https://www.youtube.com/watch?v=$ytId&hl=en"
                    val watchConn = (URL(watchUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 7000
                        readTimeout = 7000
                        setRequestProperty("User-Agent", desktopUA)
                        setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                        setRequestProperty("Cookie", consentCookie)
                    }
                    val setCookies = watchConn.headerFields["Set-Cookie"]?.joinToString("; ") { it.substringBefore(";") } ?: ""
                    val combinedCookie = if (setCookies.isNotEmpty()) "$consentCookie; $setCookies" else consentCookie
                    val html = BufferedReader(InputStreamReader(watchConn.inputStream)).use { it.readText() }

                    val tMatch = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)
                    if (tMatch != null) {
                        fetchedTitle = decodeHtmlEntities(tMatch.groupValues[1].replace("- YouTube", "").trim())
                    }
                    val dMatch = Regex(""""shortDescription"\s*:\s*"((?:\\.|[^"\\])*)"""").find(html)
                    if (dMatch != null) {
                        fetchedDesc = dMatch.groupValues[1]
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                    }

                    val visitorMatch = Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""").find(html)
                    val visitorData = visitorMatch?.groupValues?.get(1) ?: ""

                    // ENGINE 1: YouTube's Official Internal "/youtubei/v1/get_transcript" Endpoint (Used by "Show transcript" button!)
                    val paramsMatch = Regex(""""getTranscriptEndpoint"\s*:\s*\{\s*"params"\s*:\s*"([^"]+)"""").find(html)
                    if (paramsMatch != null && extractedLines.isEmpty()) {
                        try {
                            val transcriptParams = paramsMatch.groupValues[1]
                            val apiConn = (URL("https://www.youtube.com/youtubei/v1/get_transcript?prettyPrint=false").openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                doOutput = true
                                connectTimeout = 7000
                                readTimeout = 7000
                                setRequestProperty("Content-Type", "application/json")
                                setRequestProperty("User-Agent", desktopUA)
                                setRequestProperty("Cookie", combinedCookie)
                                setRequestProperty("Origin", "https://www.youtube.com")
                                setRequestProperty("Referer", watchUrl)
                                if (visitorData.isNotEmpty()) setRequestProperty("X-Goog-Visitor-Id", visitorData)
                            }
                            val payload = """
                                {
                                  "context": {
                                    "client": {
                                      "clientName": "WEB",
                                      "clientVersion": "2.20250101.00.00",
                                      "hl": "en",
                                      "gl": "US"
                                    }
                                  },
                                  "params": "$transcriptParams"
                                }
                            """.trimIndent()
                            OutputStreamWriter(apiConn.outputStream, "UTF-8").use { it.write(payload) }
                            val respStr = BufferedReader(InputStreamReader(apiConn.inputStream)).use { it.readText() }
                            extractedLines.addAll(parseGetTranscriptApiJson(respStr))
                        } catch (_: Exception) {}
                    }

                    // ENGINE 2: Direct Unmodified captionTracks URL with Session Cookie + Innertube ANDROID / IOS Fallback
                    if (extractedLines.isEmpty()) {
                        val captionRegex = Regex(""""captionTracks"\s*:\s*\[\s*\{[^}]*"baseUrl"\s*:\s*"([^"]+)"""")
                        val cMatch = captionRegex.find(html)
                        if (cMatch != null) {
                            val rawBaseUrl = cMatch.groupValues[1].replace("\\u0026", "&").replace("\\/", "/")
                            for (u in listOf(rawBaseUrl, "$rawBaseUrl&fmt=srv1", "$rawBaseUrl&fmt=json3")) {
                                if (extractedLines.isNotEmpty()) break
                                try {
                                    val subBody = httpGetText(u, desktopUA, 6000, combinedCookie)
                                    extractedLines.addAll(parseTranscriptLinesFromRaw(subBody))
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    // ENGINE 3: Innertube ANDROID / IOS / WEB_EMBEDDED Player Clients
                    if (extractedLines.isEmpty()) {
                        val clients = listOf(
                            Triple("IOS", "19.45.4", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1 like Mac OS X;)"),
                            Triple("ANDROID", "19.44.38", "com.google.android.youtube/19.44.38 (Linux; U; Android 14) gzip"),
                            Triple("ANDROID_VR", "1.56.21", "com.google.android.apps.youtube.vr.oculus/1.56.21 (Linux; U; Android 12; Quest 2) gzip")
                        )
                        for ((cName, cVer, cUa) in clients) {
                            if (extractedLines.isNotEmpty()) break
                            try {
                                val pConn = (URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection).apply {
                                    requestMethod = "POST"
                                    doOutput = true
                                    connectTimeout = 6000
                                    readTimeout = 6000
                                    setRequestProperty("Content-Type", "application/json")
                                    setRequestProperty("User-Agent", cUa)
                                    if (visitorData.isNotEmpty()) setRequestProperty("X-Goog-Visitor-Id", visitorData)
                                }
                                val body = """
                                    {
                                      "videoId": "$ytId",
                                      "context": {
                                        "client": {
                                          "clientName": "$cName",
                                          "clientVersion": "$cVer",
                                          "hl": "en",
                                          "gl": "US"
                                        }
                                      }
                                    }
                                """.trimIndent()
                                OutputStreamWriter(pConn.outputStream, "UTF-8").use { it.write(body) }
                                val pJson = JSONObject(BufferedReader(InputStreamReader(pConn.inputStream)).use { it.readText() })
                                if (fetchedTitle.isEmpty()) fetchedTitle = pJson.optJSONObject("videoDetails")?.optString("title", "") ?: ""
                                if (fetchedDesc.isEmpty()) fetchedDesc = pJson.optJSONObject("videoDetails")?.optString("shortDescription", "") ?: ""

                                val tracks = pJson.optJSONObject("captions")
                                    ?.optJSONObject("playerCaptionsTracklistRenderer")
                                    ?.optJSONArray("captionTracks")
                                if (tracks != null && tracks.length() > 0) {
                                    val bUrl = tracks.getJSONObject(0).optString("baseUrl", "")
                                    if (bUrl.isNotEmpty()) {
                                        val rawXml = httpGetText(bUrl, cUa, 6000, combinedCookie)
                                        extractedLines.addAll(parseTranscriptLinesFromRaw(rawXml))
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    val html = httpGetText(cleanedUrl, desktopUA, 7000)
                    val tMatch = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)
                    if (tMatch != null) fetchedTitle = decodeHtmlEntities(tMatch.groupValues[1].trim())
                    val dMatch = Regex("""property=["']og:description["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
                    if (dMatch != null) fetchedDesc = decodeHtmlEntities(dMatch.groupValues[1].trim())
                }
            } catch (_: Exception) {}

            mainHandler.post {
                if (transcriptJobFinished.get()) return@post
                if (fetchedTitle.isNotEmpty()) currentScriptVideoTitle = fetchedTitle
                if (fetchedDesc.isNotEmpty()) currentScriptDescription = fetchedDesc

                if (extractedLines.isNotEmpty()) {
                    transcriptJobFinished.set(true)
                    hiddenTranscriptWebView.loadUrl("about:blank")
                    currentTranscriptLines.clear()
                    currentTranscriptLines.addAll(extractedLines)
                    scriptStatusBar.visibility = View.GONE
                    scriptFetchBtn.text = "⚡ Get Free Transcript"
                    scriptVideoTitleTv.text = "🎬 $currentScriptVideoTitle"
                    showingTranscriptTab = true
                    refreshScriptStudioDisplay()
                    Toast.makeText(this@MainActivity, "✅ Full Transcript Extracted!", Toast.LENGTH_SHORT).show()
                } else {
                    mainHandler.postDelayed({
                        if (!transcriptJobFinished.get()) {
                            scriptStatusBar.visibility = View.GONE
                            scriptFetchBtn.text = "⚡ Get Free Transcript"
                            scriptVideoTitleTv.text = "🎬 ${currentScriptVideoTitle.ifBlank { "Video Info Loaded" }}"
                            showingTranscriptTab = currentTranscriptLines.isNotEmpty()
                            refreshScriptStudioDisplay()
                        }
                    }, 2200)
                }
            }
        }
    }

    private fun parseGetTranscriptApiJson(jsonStr: String): List<TranscriptLine> {
        val list = mutableListOf<TranscriptLine>()
        try {
            val segRegex = Regex(""""transcriptSegmentRenderer"\s*:\s*\{([\s\S]*?)\}\s*\}""")
            for (m in segRegex.findAll(jsonStr)) {
                val block = m.groupValues[1]
                val timeMatch = Regex(""""startTimeText"\s*:\s*\{\s*"simpleText"\s*:\s*"([^"]+)"""").find(block)
                val startMsMatch = Regex(""""startMs"\s*:\s*"(\d+)"""").find(block)
                val ts = timeMatch?.groupValues?.get(1)
                    ?: startMsMatch?.groupValues?.get(1)?.toLongOrNull()?.let { formatMs((it).toInt()) }
                    ?: "00:00"

                val textRuns = Regex(""""text"\s*:\s*"((?:\\.|[^"\\])*)"""").findAll(block)
                val sb = StringBuilder()
                for (tm in textRuns) {
                    val piece = decodeHtmlEntities(tm.groupValues[1].replace("\\n", " ").replace("\\\"", "\"")).trim()
                    if (piece.isNotEmpty() && piece != ts) {
                        sb.append(piece).append(" ")
                    }
                }
                val finalLine = sb.toString().trim()
                if (finalLine.isNotEmpty()) {
                    list.add(TranscriptLine(ts, finalLine))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseTranscriptLinesFromRaw(raw: String): List<TranscriptLine> {
        val result = mutableListOf<TranscriptLine>()
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return result

        if (trimmed.startsWith("{")) {
            try {
                val root = JSONObject(trimmed)
                val events = root.optJSONArray("events")
                if (events != null) {
                    for (i in 0 until events.length()) {
                        val ev = events.optJSONObject(i) ?: continue
                        val startMs = ev.optInt("tStartMs", 0)
                        val segs = ev.optJSONArray("segs") ?: continue
                        val sb = StringBuilder()
                        for (j in 0 until segs.length()) {
                            val utf8 = segs.optJSONObject(j)?.optString("utf8", "")?.replace("\n", " ")?.trim() ?: ""
                            if (utf8.isNotEmpty()) sb.append(utf8).append(" ")
                        }
                        val lineText = decodeHtmlEntities(sb.toString().trim())
                        if (lineText.isNotEmpty()) {
                            result.add(TranscriptLine(formatMs(startMs), lineText))
                        }
                    }
                }
                if (result.isNotEmpty()) return result
            } catch (_: Exception) {}
        }

        val textTagRegex = Regex("""<(?:text|p)\b([^>]*)>([\s\S]*?)</(?:text|p)>""")
        for (m in textTagRegex.findAll(trimmed)) {
            val attrs = m.groupValues[1]
            val inner = m.groupValues[2]
            val startSec = Regex("""start=["']([0-9.]+)["']""").find(attrs)?.groupValues?.get(1)?.toFloatOrNull()
            val startMsAttr = Regex("""\bt=["'](\d+)["']""").find(attrs)?.groupValues?.get(1)?.toIntOrNull()
            val timeMs = when {
                startSec != null -> (startSec * 1000f).toInt()
                startMsAttr != null -> startMsAttr
                else -> 0
            }
            val cleanText = decodeHtmlEntities(
                inner.replace(Regex("<[^>]+>"), " ")
                    .replace("\n", " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            )
            if (cleanText.isNotEmpty()) {
                result.add(TranscriptLine(formatMs(timeMs), cleanText))
            }
        }
        return result
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("\\u0026", "&")
    }

    private fun showQualityPickerDialog() {
        if (latestDetectedVideoUrl.isEmpty()) {
            Toast.makeText(this, "Play the video for 1 second so stream can lock!", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = makeRoundedBg("#161F30", 18, "#374151", 1)
        }

        val header = TextView(this).apply {
            text = "Select Quality / Format"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(4))
        }
        val subHeader = TextView(this).apply {
            text = "Locked: ${latestDetectedTitle.take(35)}"
            textSize = 11f
            setTextColor(Color.parseColor("#10B981"))
            setPadding(0, 0, 0, dp(12))
        }
        dialogLayout.addView(header)
        dialogLayout.addView(subHeader)

        val dialog = AlertDialog.Builder(this).setView(dialogLayout).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val options = mutableListOf(
            Triple("2K / 1080p Ultra HD (MP4)", latestDetectedVideoUrl, false),
            Triple("720p Fast Stream (MP4)", latestDetectedVideoUrl, false),
            Triple("480p / 360p Data Saver (MP4)", latestDetectedVideoUrl, false)
        )
        if (latestDetectedAudioUrl.isNotEmpty()) {
            options.add(Triple("Direct MP3 Voiceover / Audio", latestDetectedAudioUrl, true))
        } else {
            options.add(Triple("Direct MP3 Voiceover / Audio", latestDetectedVideoUrl, true))
        }

        options.forEach { (label, targetUrl, isAudio) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = makeRoundedBg("#1E293B", 12, "#374151", 1)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
                setOnClickListener {
                    dialog.dismiss()
                    enqueueAuthenticatedDownload(targetUrl, latestDetectedPageUrl, isAudio)
                }
            }
            val ic = ImageView(this).apply {
                setImageResource(if (isAudio) R.drawable.ic_p5_music else R.drawable.ic_p5_player)
                setColorFilter(if (isAudio) Color.parseColor("#A855F7") else Color.parseColor("#EC4899"))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(12) }
            }
            val titleTv = TextView(this).apply {
                text = label
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val dlIcon = ImageView(this).apply {
                setImageResource(R.drawable.ic_p5_download)
                setColorFilter(Color.WHITE)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                background = makeGradientBg("#EC4899", "#8B5CF6", 10)
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            }
            row.addView(ic)
            row.addView(titleTv)
            row.addView(dlIcon)
            dialogLayout.addView(row)
        }
        dialog.show()
    }

    private fun getAppMediaDir(): File {
        val base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        val folder = File(base, "ProVideoSaver")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    private fun getVaultDir(): File {
        val folder = File(filesDir, "PrivateVault")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    private fun collectCombinedCookies(streamUrl: String, pageUrl: String): String {
        val cm = CookieManager.getInstance()
        val cookieSet = LinkedHashSet<String>()
        listOf(streamUrl, pageUrl, "https://www.tiktok.com/", "https://www.instagram.com/", "https://m.facebook.com/", "https://m.bilibili.com/", "https://www.kwai.com/").forEach { u ->
            if (u.isNotEmpty()) {
                val c = cm.getCookie(u)
                if (!c.isNullOrBlank()) {
                    c.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { cookieSet.add(it) }
                }
            }
        }
        return cookieSet.joinToString("; ")
    }

    private fun enqueueAuthenticatedDownload(streamUrl: String, pageUrl: String, extractAudioAfter: Boolean) {
        CookieManager.getInstance().flush()
        val combinedCookies = collectCombinedCookies(streamUrl, pageUrl)
        val currentUa = browserWebView.settings.userAgentString
        val effectivePageUrl = if (pageUrl.isNotBlank()) pageUrl else streamUrl
        val cleanName = latestDetectedTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(22).ifBlank { "Video" }
        val stamp = (System.currentTimeMillis() % 100000).toString()
        val ext = if (extractAudioAfter) "mp3" else "mp4"
        val finalFileName = "HD_${cleanName}_$stamp.$ext"
        val partFile = File(getAppMediaDir(), "$finalFileName.part")

        prefs.edit()
            .putString("url_$finalFileName", streamUrl)
            .putString("page_$finalFileName", effectivePageUrl)
            .apply()

        val task = DownloadTaskItem(
            id = stamp,
            title = finalFileName,
            streamUrl = streamUrl,
            pageUrl = effectivePageUrl,
            cookies = combinedCookies,
            userAgent = currentUa,
            targetFile = partFile,
            isAudioOnly = extractAudioAfter
        )
        activeTasks[task.id] = task
        switchTab(2)
        refreshDownloadsUI()
        startOrResumeDownloadTask(task)
    }

    private fun startOrResumeDownloadTask(task: DownloadTaskItem) {
        task.isPaused.set(false)
        task.status = "Connecting..."
        task.errorMessage = ""
        refreshDownloadsUI()

        downloadExecutor.submit {
            var connection: HttpURLConnection? = null
            try {
                var currentTargetUrl = task.streamUrl
                var redirectsLeft = 8
                val existingBytes = if (task.targetFile.exists()) task.targetFile.length() else 0L
                task.downloadedBytes = existingBytes

                val refererUrl = when {
                    currentTargetUrl.contains("tiktok") || task.pageUrl.contains("tiktok") -> "https://www.tiktok.com/"
                    currentTargetUrl.contains("instagram") || task.pageUrl.contains("instagram") -> "https://www.instagram.com/"
                    currentTargetUrl.contains("fbcdn") || task.pageUrl.contains("facebook") -> "https://m.facebook.com/"
                    currentTargetUrl.contains("bilivideo") || task.pageUrl.contains("bilibili") -> "https://m.bilibili.com/"
                    currentTargetUrl.contains("kwai") || currentTargetUrl.contains("kspkg") -> "https://www.kwai.com/"
                    task.pageUrl.isNotBlank() -> task.pageUrl
                    else -> currentTargetUrl
                }

                while (redirectsLeft > 0) {
                    val urlObj = URL(currentTargetUrl)
                    connection = (urlObj.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 20000
                        readTimeout = 25000
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", task.userAgent)
                        setRequestProperty("Accept", "*/*")
                        setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                        setRequestProperty("Accept-Encoding", "identity")
                        setRequestProperty("Connection", "keep-alive")
                        setRequestProperty("Referer", refererUrl)
                        if (task.cookies.isNotBlank()) setRequestProperty("Cookie", task.cookies)
                        if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
                    }

                    val code = connection.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        val location = connection.getHeaderField("Location")
                        connection.disconnect()
                        if (location.isNullOrBlank()) break
                        currentTargetUrl = URL(URL(currentTargetUrl), location).toString()
                        redirectsLeft--
                    } else break
                }

                val finalConn = connection ?: throw Exception("Connection failed")
                val responseCode = finalConn.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw Exception("HTTP $responseCode")
                }

                val cType = finalConn.contentType?.lowercase(Locale.ROOT) ?: ""
                if (cType.contains("text/html")) {
                    throw Exception("Play video once in browser to unlock stream")
                }

                val isResuming = (responseCode == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0L)
                val contentLength = finalConn.contentLengthLong
                task.totalBytes = if (contentLength > 0) {
                    if (isResuming) existingBytes + contentLength else contentLength
                } else -1L

                if (task.totalBytes > 0) {
                    prefs.edit().putLong("tot_${task.title}", task.totalBytes).apply()
                }

                if (!isResuming) task.downloadedBytes = 0L
                task.status = "Downloading"
                mainHandler.post { refreshDownloadsUI() }

                val raf = RandomAccessFile(task.targetFile, "rw")
                if (isResuming) raf.seek(existingBytes) else raf.setLength(0)

                val input = BufferedInputStream(finalConn.inputStream, 131072)
                val buffer = ByteArray(131072)
                var bytesRead: Int
                var lastUiUpdate = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (task.isCancelled.get()) {
                        raf.close()
                        input.close()
                        task.targetFile.delete()
                        return@submit
                    }
                    if (task.isPaused.get()) {
                        raf.close()
                        input.close()
                        task.status = "Paused"
                        task.speedText = "Paused"
                        mainHandler.post { refreshDownloadsUI() }
                        return@submit
                    }

                    raf.write(buffer, 0, bytesRead)
                    task.downloadedBytes += bytesRead
                    bytesSinceLastUpdate += bytesRead

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastUiUpdate
                    if (elapsed >= 400) {
                        val bytesPerSec = (bytesSinceLastUpdate * 1000L) / elapsed.coerceAtLeast(1L)
                        task.speedText = formatSpeed(bytesPerSec)
                        lastUiUpdate = now
                        bytesSinceLastUpdate = 0L
                        mainHandler.post { refreshDownloadsUI() }
                    }
                }

                raf.close()
                input.close()
                finalConn.disconnect()

                if (task.targetFile.length() < 4096L) {
                    task.targetFile.delete()
                    throw Exception("Protected stream - Play video once in browser")
                }

                val completedFile = File(getAppMediaDir(), task.title)
                if (task.targetFile.name.endsWith(".part")) {
                    task.targetFile.copyTo(completedFile, overwrite = true)
                    task.targetFile.delete()
                }
                prefs.edit().remove("url_${task.title}").remove("page_${task.title}").remove("tot_${task.title}").apply()

                task.status = "Completed"
                activeTasks.remove(task.id)
                exportFileToPublicDownloads(completedFile)

                mainHandler.post {
                    refreshDownloadsUI()
                    refreshPlayerFileList()
                    Toast.makeText(this, "Download Complete: ${completedFile.name}", Toast.LENGTH_SHORT).show()
                    if (task.isAudioOnly && !completedFile.name.endsWith(".mp3")) {
                        extractHardwareAudioTrack(completedFile)
                    }
                }
            } catch (e: Exception) {
                task.status = "Failed"
                task.errorMessage = e.message ?: "Interrupted - Tap Play to Resume"
                mainHandler.post { refreshDownloadsUI() }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return if (bytesPerSec >= 1024 * 1024) {
            String.format(Locale.US, "%.2f MB/s", bytesPerSec / (1024f * 1024f))
        } else {
            String.format(Locale.US, "%d KB/s", (bytesPerSec / 1024L).coerceAtLeast(1L))
        }
    }

    private fun formatSizeMB(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))

    private fun buildDownloadsTab() {
        tabDownloadsView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        downloadsListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(downloadsListContainer)
        tabDownloadsView.addView(scroll)
    }

    private fun makeIconActionCircle(iconRes: Int, bgHex: String, onClick: () -> Unit): ImageView {
        return ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = makeRoundedBg(bgHex, 18, "#374151", 1)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { leftMargin = dp(6) }
            setOnClickListener { onClick() }
        }
    }

    private fun moveFileToVaultSafely(file: File) {
        try {
            val dest = File(getVaultDir(), file.name)
            file.copyTo(dest, overwrite = true)
            file.delete()
            Toast.makeText(this, "🔒 Moved to Private Vault!", Toast.LENGTH_SHORT).show()
            refreshDownloadsUI()
            refreshPlayerFileList()
            refreshVaultUI()
        } catch (e: Exception) {
            Toast.makeText(this, "Vault move error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshDownloadsUI() {
        downloadsListContainer.removeAllViews()

        activeTasks.values.filter { it.status != "Completed" }.forEach { task ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = makeRoundedBg("#161F30", 14, "#26334D", 1)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val nameTv = TextView(this).apply {
                text = task.title
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val isRunning = task.status == "Downloading" || task.status == "Connecting..."
            val pauseResumeIcon = makeIconActionCircle(
                if (isRunning) R.drawable.ic_p5_pause else R.drawable.ic_p5_play,
                if (isRunning) "#D97706" else "#10B981"
            ) {
                if (isRunning) task.isPaused.set(true) else startOrResumeDownloadTask(task)
            }
            val cancelIcon = makeIconActionCircle(R.drawable.ic_p5_delete, "#1F2937") {
                task.isCancelled.set(true)
                task.targetFile.delete()
                prefs.edit().remove("url_${task.title}").remove("page_${task.title}").remove("tot_${task.title}").apply()
                activeTasks.remove(task.id)
                refreshDownloadsUI()
            }
            topRow.addView(nameTv)
            topRow.addView(pauseResumeIcon)
            topRow.addView(cancelIcon)

            val percent = if (task.totalBytes > 0) ((task.downloadedBytes * 100L) / task.totalBytes).toInt() else 0
            val pBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = percent
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(4)
                }
            }
            val statusLine = when (task.status) {
                "Paused" -> "⏸ Paused at ${formatSizeMB(task.downloadedBytes)} • Tap ▶ to Resume"
                "Failed" -> "⚠️ ${task.errorMessage} • Tap ▶ to Resume"
                else -> "${formatSizeMB(task.downloadedBytes)} • $percent% • ${task.speedText}"
            }
            val statusTv = TextView(this).apply {
                text = statusLine
                textSize = 11f
                setTextColor(if (task.status == "Failed") Color.parseColor("#F87171") else if (task.status == "Paused") Color.parseColor("#FBBF24") else Color.parseColor("#38BDF8"))
            }
            card.addView(topRow)
            card.addView(pBar)
            card.addView(statusTv)
            downloadsListContainer.addView(card)
        }

        val savedFiles = getAppMediaDir().listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".txt") && it.length() > 4096L }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        savedFiles.forEach { file ->
            val isAudio = file.name.endsWith(".m4a", true) || file.name.endsWith(".mp3", true)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = makeRoundedBg("#161F30", 14, "#1F2937", 1)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            }
            val typeIcon = ImageView(this).apply {
                setImageResource(if (isAudio) R.drawable.ic_p5_music else R.drawable.ic_p5_player)
                setColorFilter(Color.WHITE)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = makeGradientBg(if (isAudio) "#8B5CF6" else "#EC4899", if (isAudio) "#6D28D9" else "#8B5CF6", 12)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(10) }
            }
            val metaCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val titleTv = TextView(this).apply {
                text = file.name
                textSize = 12f
                maxLines = 1
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            val subTv = TextView(this).apply {
                text = formatSizeMB(file.length())
                textSize = 11f
                setTextColor(Color.parseColor("#10B981"))
            }
            metaCol.addView(titleTv)
            metaCol.addView(subTv)

            val actionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (!isAudio) {
                actionsRow.addView(makeIconActionCircle(R.drawable.ic_p5_music, "#4C1D95") { extractHardwareAudioTrack(file) })
            }
            actionsRow.addView(makeIconActionCircle(R.drawable.ic_p5_lock, "#1E293B") { moveFileToVaultSafely(file) })
            actionsRow.addView(makeIconActionCircle(R.drawable.ic_p5_share, "#1E293B") { shareMediaFile(file) })
            actionsRow.addView(makeIconActionCircle(R.drawable.ic_p5_delete, "#1E2937") {
                file.delete()
                refreshDownloadsUI()
                refreshPlayerFileList()
            })
            actionsRow.addView(makeIconActionCircle(R.drawable.ic_p5_play, "#059669") {
                switchTab(3)
                playFileIn4KPlayer(file)
            })

            card.addView(typeIcon)
            card.addView(metaCol)
            card.addView(actionsRow)
            downloadsListContainer.addView(card)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPlayerTab() {
        tabPlayerView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        videoPlayerBox = FrameLayout(this).apply {
            background = makeRoundedBg("#000000", 16, "#1F2937", 1)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260))
        }

        customVideoView = VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            setOnPreparedListener { mp ->
                activeMediaPlayer = mp
                playerSeekBar.max = mp.duration.coerceAtLeast(100)
                applyPlaybackSpeed(currentPlaybackSpeed)
                mp.start()
                centerPlayPauseIcon.setImageResource(R.drawable.ic_p5_pause)
                scheduleOverlayAutoHide()
            }
            setOnCompletionListener {
                centerPlayPauseIcon.setImageResource(R.drawable.ic_p5_play)
                showVideoOverlay()
            }
        }

        gestureFeedbackBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = makeRoundedBg("#CC0B0F19", 16, "#374151", 1)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        gestureFeedbackText = TextView(this).apply {
            text = "100%"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        gestureFeedbackBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 50
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply { topMargin = dp(8) }
        }
        gestureFeedbackBox.addView(gestureFeedbackText)
        gestureFeedbackBox.addView(gestureFeedbackBar)

        videoTouchOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val topOverlayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        }
        overlayTitleTv = TextView(this).apply {
            text = "4K Hardware Player"
            textSize = 13f
            maxLines = 1
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        speedChipTv = TextView(this).apply {
            text = "1.0x"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = makeRoundedBg("#801F2937", 14, "#4B5563", 1)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8)
            }
            setOnClickListener {
                currentPlaybackSpeed = when (currentPlaybackSpeed) {
                    1.0f -> 1.25f
                    1.25f -> 1.5f
                    1.5f -> 2.0f
                    else -> 1.0f
                }
                text = "${currentPlaybackSpeed}x"
                applyPlaybackSpeed(currentPlaybackSpeed)
                scheduleOverlayAutoHide()
            }
        }
        bgPlayOverlayIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_headphones)
            setColorFilter(Color.parseColor("#9CA3AF"))
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = makeRoundedBg("#801F2937", 16, "#4B5563", 1)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { rightMargin = dp(8) }
            setOnClickListener {
                backgroundPlayEnabled = !backgroundPlayEnabled
                setColorFilter(if (backgroundPlayEnabled) Color.parseColor("#10B981") else Color.parseColor("#9CA3AF"))
                background = makeRoundedBg(if (backgroundPlayEnabled) "#80064E3B" else "#801F2937", 16, if (backgroundPlayEnabled) "#10B981" else "#4B5563", 1)
                Toast.makeText(this@MainActivity, if (backgroundPlayEnabled) "Background Audio ON" else "Background Audio OFF", Toast.LENGTH_SHORT).show()
                scheduleOverlayAutoHide()
            }
        }
        val pipOverlayIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_pip)
            setColorFilter(Color.WHITE)
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = makeRoundedBg("#801F2937", 16, "#4B5563", 1)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                }
            }
        }
        topOverlayRow.addView(overlayTitleTv)
        topOverlayRow.addView(speedChipTv)
        topOverlayRow.addView(bgPlayOverlayIcon)
        topOverlayRow.addView(pipOverlayIcon)

        val centerControlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        val rewOverlayIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_rew10)
            setColorFilter(Color.WHITE)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = makeRoundedBg("#80111827", 24, "#4B5563", 1)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(24) }
            setOnClickListener {
                seekByDelta(-10000)
                scheduleOverlayAutoHide()
            }
        }
        centerPlayPauseIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_play)
            setColorFilter(Color.WHITE)
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = makeGradientBg("#EC4899", "#8B5CF6", 30)
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
            setOnClickListener {
                if (customVideoView.isPlaying) {
                    customVideoView.pause()
                    setImageResource(R.drawable.ic_p5_play)
                } else {
                    customVideoView.start()
                    setImageResource(R.drawable.ic_p5_pause)
                    scheduleOverlayAutoHide()
                }
            }
        }
        val ffOverlayIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_ff10)
            setColorFilter(Color.WHITE)
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = makeRoundedBg("#80111827", 24, "#4B5563", 1)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { leftMargin = dp(24) }
            setOnClickListener {
                seekByDelta(10000)
                scheduleOverlayAutoHide()
            }
        }
        centerControlsRow.addView(rewOverlayIcon)
        centerControlsRow.addView(centerPlayPauseIcon)
        centerControlsRow.addView(ffOverlayIcon)

        val bottomOverlayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        }
        playerSeekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        customVideoView.seekTo(progress)
                        scheduleOverlayAutoHide()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        playerTimeLabel = TextView(this).apply {
            text = "00:00 / 00:00"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, dp(10), 0)
        }
        val fullscreenOverlayIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_p5_fullscreen)
            setColorFilter(Color.WHITE)
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = makeRoundedBg("#801F2937", 16, "#4B5563", 1)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            setOnClickListener {
                toggleHorizontalFullscreen(!isPlayerFullscreen)
                scheduleOverlayAutoHide()
            }
        }
        bottomOverlayRow.addView(playerSeekBar)
        bottomOverlayRow.addView(playerTimeLabel)
        bottomOverlayRow.addView(fullscreenOverlayIcon)

        videoTouchOverlay.addView(topOverlayRow)
        videoTouchOverlay.addView(centerControlsRow)
        videoTouchOverlay.addView(bottomOverlayRow)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isOverlayVisible) {
                    videoTouchOverlay.visibility = View.GONE
                    isOverlayVisible = false
                } else {
                    showVideoOverlay()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val width = videoPlayerBox.width.coerceAtLeast(1)
                if (e.x < width / 2f) {
                    seekByDelta(-10000)
                    showGestureFeedback("⏪ -10s", 0, false)
                } else {
                    seekByDelta(10000)
                    showGestureFeedback("+10s ⏩", 100, false)
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null || abs(distanceY) < abs(distanceX)) return false
                val boxWidth = videoPlayerBox.width.coerceAtLeast(1)
                val boxHeight = videoPlayerBox.height.coerceAtLeast(1)
                val deltaRatio = distanceY / boxHeight.toFloat()

                if (e1.x < boxWidth / 2f) {
                    val lp = window.attributes
                    val currentBright = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                    val newBright = (currentBright + deltaRatio).coerceIn(0.05f, 1.0f)
                    lp.screenBrightness = newBright
                    window.attributes = lp
                    val pct = (newBright * 100).toInt()
                    showGestureFeedback("☀️ $pct%", pct, true)
                } else {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (smoothVolumeTracker < 0f) smoothVolumeTracker = curVol.toFloat()
                    smoothVolumeTracker = (smoothVolumeTracker + (deltaRatio * maxVol * 1.5f)).coerceIn(0f, maxVol.toFloat())
                    val newVolInt = smoothVolumeTracker.roundToInt()
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolInt, 0)
                    val pct = ((newVolInt * 100f) / maxVol).toInt()
                    showGestureFeedback("🔊 $pct%", pct, true)
                }
                return true
            }
        })

        videoPlayerBox.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                smoothVolumeTracker = -1f
            }
            gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                mainHandler.postDelayed({ gestureFeedbackBox.visibility = View.GONE }, 600)
            }
            true
        }

        videoPlayerBox.addView(customVideoView)
        videoPlayerBox.addView(videoTouchOverlay)
        videoPlayerBox.addView(gestureFeedbackBox)

        playerFileListScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(10)
            }
        }
        playerFileListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        playerFileListScroll.addView(playerFileListContainer)

        tabPlayerView.addView(videoPlayerBox)
        tabPlayerView.addView(playerFileListScroll)
    }

    private fun seekByDelta(deltaMs: Int) {
        try {
            val duration = customVideoView.duration.coerceAtLeast(1)
            val target = (customVideoView.currentPosition + deltaMs).coerceIn(0, duration)
            customVideoView.seekTo(target)
            playerSeekBar.progress = target
        } catch (_: Exception) {}
    }

    private fun showGestureFeedback(label: String, progressVal: Int, showBar: Boolean) {
        gestureFeedbackBox.visibility = View.VISIBLE
        gestureFeedbackText.text = label
        gestureFeedbackBar.visibility = if (showBar) View.VISIBLE else View.GONE
        gestureFeedbackBar.progress = progressVal
    }

    private fun showVideoOverlay() {
        videoTouchOverlay.visibility = View.VISIBLE
        isOverlayVisible = true
        scheduleOverlayAutoHide()
    }

    private fun scheduleOverlayAutoHide() {
        mainHandler.removeCallbacks(hideOverlayRunnable)
        mainHandler.postDelayed(hideOverlayRunnable, 3200)
    }

    private fun toggleHorizontalFullscreen(enableLandscape: Boolean) {
        isPlayerFullscreen = enableLandscape
        if (enableLandscape) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            topHeaderLayout.visibility = View.GONE
            bottomNavLayout.visibility = View.GONE
            playerFileListScroll.visibility = View.GONE
            tabPlayerView.setPadding(0, 0, 0, 0)
            videoPlayerBox.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            hideSystemBars()
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            topHeaderLayout.visibility = View.VISIBLE
            bottomNavLayout.visibility = View.VISIBLE
            playerFileListScroll.visibility = View.VISIBLE
            tabPlayerView.setPadding(dp(10), dp(10), dp(10), dp(10))
            videoPlayerBox.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(260)
            )
            showSystemBars()
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun applyPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                activeMediaPlayer?.let { mp ->
                    mp.playbackParams = mp.playbackParams.setSpeed(speed)
                }
            } catch (_: Exception) {}
        }
    }

    private fun playFileIn4KPlayer(file: File) {
        if (!file.exists() || file.length() == 0L) return
        currentlyPlayingFile = file
        overlayTitleTv.text = file.name
        customVideoView.setVideoURI(Uri.fromFile(file))
        customVideoView.requestFocus()
        customVideoView.start()
        centerPlayPauseIcon.setImageResource(R.drawable.ic_p5_pause)
        showVideoOverlay()
    }

    private fun refreshPlayerFileList() {
        playerFileListContainer.removeAllViews()
        val files = getAppMediaDir().listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".txt") && it.length() > 4096L }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        files.forEach { file ->
            val isAudio = file.name.endsWith(".m4a", true) || file.name.endsWith(".mp3", true)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = makeRoundedBg("#161F30", 12, "#1F2937", 1)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
                setOnClickListener { playFileIn4KPlayer(file) }
            }
            val ic = ImageView(this).apply {
                setImageResource(if (isAudio) R.drawable.ic_p5_music else R.drawable.ic_p5_play)
                setColorFilter(Color.WHITE)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = makeGradientBg("#EC4899", "#8B5CF6", 10)
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(12) }
            }
            val tv = TextView(this).apply {
                text = file.name
                textSize = 13f
                maxLines = 1
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val sizeTv = TextView(this).apply {
                text = formatSizeMB(file.length())
                textSize = 11f
                setTextColor(Color.parseColor("#9CA3AF"))
            }
            row.addView(ic)
            row.addView(tv)
            row.addView(sizeTv)
            playerFileListContainer.addView(row)
        }
    }

    private fun startPlayerProgressUpdater() {
        mainHandler.post(object : Runnable {
            override fun run() {
                try {
                    if (customVideoView.isPlaying) {
                        val pos = customVideoView.currentPosition
                        val dur = customVideoView.duration.coerceAtLeast(1)
                        playerSeekBar.max = dur
                        playerSeekBar.progress = pos
                        playerTimeLabel.text = "${formatMs(pos)} /${formatMs(dur)}"
                    }
                } catch (_: Exception) {}
                mainHandler.postDelayed(this, 500)
            }
        })
    }

    private fun formatMs(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun extractHardwareAudioTrack(sourceVideo: File) {
        Toast.makeText(this, "Extracting MP3...", Toast.LENGTH_SHORT).show()
        downloadExecutor.submit {
            try {
                val outName = sourceVideo.nameWithoutExtension + "_Audio.mp3"
                val outFile = File(getAppMediaDir(), outName)
                if (outFile.exists()) outFile.delete()

                val extractor = MediaExtractor()
                extractor.setDataSource(sourceVideo.absolutePath)
                var audioTrackIndex = -1
                var audioFormat: MediaFormat? = null

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        audioFormat = format
                        break
                    }
                }
                if (audioTrackIndex < 0 || audioFormat == null) {
                    extractor.release()
                    throw Exception("No audio track")
                }

                extractor.selectTrack(audioTrackIndex)
                val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val dstTrack = muxer.addTrack(audioFormat)
                muxer.start()

                val buffer = ByteBuffer.allocate(256 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    info.offset = 0
                    info.size = extractor.readSampleData(buffer, 0)
                    if (info.size < 0) break
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, info)
                    extractor.advance()
                }
                muxer.stop()
                muxer.release()
                extractor.release()
                exportFileToPublicDownloads(outFile)

                mainHandler.post {
                    refreshDownloadsUI()
                    refreshPlayerFileList()
                    Toast.makeText(this, "MP3 Saved: ${outFile.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                mainHandler.post { Toast.makeText(this, "MP3 Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun shareMediaFile(file: File) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, "File not ready", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val isAudio = file.name.endsWith(".mp3", true) || file.name.endsWith(".m4a", true)
            val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
            val directFileUri = Uri.fromFile(file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, directFileUri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                clipData = ClipData.newRawUri(file.name, directFileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share ${file.name}"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportFileToPublicDownloads(file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val isAudio = file.name.endsWith(".mp3", true) || file.name.endsWith(".m4a", true)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, if (isAudio) "audio/mpeg" else "video/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ProVideoSaver")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { input -> input.copyTo(out) }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun buildVaultTab() {
        tabVaultView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        vaultListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(vaultListContainer)
        }
        tabVaultView.addView(scroll)
    }

    private fun refreshVaultUI() {
        vaultListContainer.removeAllViews()
        val savedPin = prefs.getString("vault_pin", null)

        if (!isVaultUnlocked) {
            val pinCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(24), dp(28), dp(24), dp(28))
                background = makeRoundedBg("#161F30", 20, "#26334D", 1)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(24)
                }
            }
            val heroIcon = ImageView(this).apply {
                setImageResource(R.drawable.ic_p5_lock)
                setColorFilter(Color.WHITE)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = makeGradientBg("#EC4899", "#8B5CF6", 28)
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { bottomMargin = dp(14) }
            }
            val title = TextView(this).apply {
                text = if (savedPin == null) "Create 4-Digit Vault PIN" else "Unlock Private Vault"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }
            val pinInput = EditText(this).apply {
                hint = "• • • •"
                gravity = Gravity.CENTER
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#6B7280"))
                textSize = 20f
                background = makeRoundedBg("#090D16", 14, "#374151", 1)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(16)
                    bottomMargin = dp(16)
                }
            }
            val unlockBtn = Button(this).apply {
                text = if (savedPin == null) "Save & Unlock" else "Unlock"
                isAllCaps = false
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = makeGradientBg("#EC4899", "#8B5CF6", 14)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
                setOnClickListener {
                    val entered = pinInput.text.toString().trim()
                    if (entered.length < 4) {
                        Toast.makeText(this@MainActivity, "4 digits required", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (savedPin == null || entered == savedPin) {
                        if (savedPin == null) prefs.edit().putString("vault_pin", entered).apply()
                        isVaultUnlocked = true
                        refreshVaultUI()
                    } else {
                        Toast.makeText(this@MainActivity, "Wrong PIN", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            pinCard.addView(heroIcon)
            pinCard.addView(title)
            pinCard.addView(pinInput)
            pinCard.addView(unlockBtn)
            vaultListContainer.addView(pinCard)
            return
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val headerTitle = TextView(this).apply {
            text = "🔒 Private Vault Unlocked"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val lockVaultBtn = Button(this).apply {
            text = "Lock Vault"
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = makeRoundedBg("#374151", 8)
            setOnClickListener {
                isVaultUnlocked = false
                refreshVaultUI()
            }
        }
        headerRow.addView(headerTitle)
        headerRow.addView(lockVaultBtn)
        vaultListContainer.addView(headerRow)

        val vaultFiles = getVaultDir().listFiles()?.filter { it.isFile && it.length() > 0 }?.toList() ?: emptyList()
        if (vaultFiles.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Vault is empty. Tap '🔒' on any downloaded file to hide it here."
                setTextColor(Color.parseColor("#9CA3AF"))
                setPadding(0, dp(20), 0, 0)
                gravity = Gravity.CENTER
            }
            vaultListContainer.addView(emptyTv)
        } else {
            vaultFiles.forEach { file ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = makeRoundedBg("#161F30", 12, "#1F2937", 1)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8)
                    }
                }
                val nameTv = TextView(this).apply {
                    text = file.name
                    textSize = 13f
                    maxLines = 1
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(nameTv)
                row.addView(makeIconActionCircle(R.drawable.ic_p5_play, "#059669") {
                    switchTab(3)
                    playFileIn4KPlayer(file)
                })
                row.addView(makeIconActionCircle(R.drawable.ic_p5_unlock, "#2563EB") {
                    try {
                        val restored = File(getAppMediaDir(), file.name)
                        file.copyTo(restored, overwrite = true)
                        file.delete()
                        refreshVaultUI()
                        refreshDownloadsUI()
                        refreshPlayerFileList()
                        Toast.makeText(this@MainActivity, "Restored to Downloads", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Restore error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                })
                vaultListContainer.addView(row)
            }
        }
    }

    private fun switchTab(index: Int) {
        if (isPlayerFullscreen && index != 3) toggleHorizontalFullscreen(false)

        tabBrowserView.visibility = if (index == 0) View.VISIBLE else View.GONE
        tabScriptView.visibility = if (index == 1) View.VISIBLE else View.GONE
        tabDownloadsView.visibility = if (index == 2) View.VISIBLE else View.GONE
        tabPlayerView.visibility = if (index == 3) View.VISIBLE else View.GONE
        tabVaultView.visibility = if (index == 4) View.VISIBLE else View.GONE

        when (index) {
            0 -> {
                topHeaderTitleTv.text = "Pro Video Saver"
                topHeaderSubtitleTv.text = "No-Login 4K Downloader"
            }
            1 -> {
                topHeaderTitleTv.text = "AI Transcript Studio"
                topHeaderSubtitleTv.text = "YouTube to Word-by-Word Script"
            }
            2 -> {
                topHeaderTitleTv.text = "Saved Media"
                topHeaderSubtitleTv.text = "Turbo Downloads & MP3 Audio"
            }
            3 -> {
                topHeaderTitleTv.text = "4K Hardware Player"
                topHeaderSubtitleTv.text = "Gesture Volume & Brightness"
            }
            4 -> {
                topHeaderTitleTv.text = "Private Vault"
                topHeaderSubtitleTv.text = "PIN-Protected Media"
            }
        }

        navTabContainers.forEachIndexed { i, box ->
            if (i == index) {
                box.background = if (i == 1) makeGradientBg("#F59E0B", "#D97706", 14) else makeGradientBg("#EC4899", "#8B5CF6", 14)
                navTabIcons[i].setColorFilter(Color.WHITE)
                navTabLabels[i].setTextColor(Color.WHITE)
                navTabLabels[i].setTypeface(null, Typeface.BOLD)
            } else {
                box.background = null
                navTabIcons[i].setColorFilter(Color.parseColor("#9CA3AF"))
                navTabLabels[i].setTextColor(Color.parseColor("#9CA3AF"))
                navTabLabels[i].setTypeface(null, Typeface.NORMAL)
            }
        }

        if (index != 3 && !backgroundPlayEnabled && ::customVideoView.isInitialized && customVideoView.isPlaying) {
            customVideoView.pause()
            centerPlayPauseIcon.setImageResource(R.drawable.ic_p5_play)
        }
        if (index == 0 || index == 1) checkClipboardForVideoLink()
        if (index == 2) refreshDownloadsUI()
        if (index == 3) refreshPlayerFileList()
        if (index == 4) refreshVaultUI()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isPlayerFullscreen) {
            toggleHorizontalFullscreen(false)
            return
        }
        if (tabBrowserView.visibility == View.VISIBLE && webViewContainer.visibility == View.VISIBLE) {
            if (browserWebView.canGoBack()) browserWebView.goBack() else resetToBrowserHomeScreen()
            return
        }
        if (tabBrowserView.visibility != View.VISIBLE) {
            switchTab(0)
            return
        }
        super.onBackPressed()
    }
}
