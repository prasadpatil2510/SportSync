package club.nmtcc.cricket

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.SurfaceHolder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.common.AudioCodec
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.gl.render.filters.ViewFilterRender
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.encoder.input.sources.OrientationForced
import com.pedro.encoder.input.sources.audio.SilenceAudioSource
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

private enum class BroadcastMode { YOUTUBE, REMOTE_OBS }
private const val PERMANENT_TEST_BROADCAST_PIN = "301022"
private const val COMPOSITION_WIDTH = 1920
private const val COMPOSITION_HEIGHT = 1080
private const val BROADCAST_FPS = 30
private const val BROADCAST_VIDEO_BITRATE = 9_000_000
private const val BROADCAST_KEYFRAME_INTERVAL_SECONDS = 2
private const val BROADCAST_AUDIO_BITRATE = 128_000
private const val BROADCAST_AUDIO_SAMPLE_RATE = 48_000
internal const val BROADCAST_PREFERENCES = "sports-sync-broadcast"
internal const val LAST_BROADCAST_PIN = "last-generated-pin"

@Composable
fun BroadcastScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences(BROADCAST_PREFERENCES, Context.MODE_PRIVATE) }
    var pin by remember { mutableStateOf(preferences.getString(LAST_BROADCAST_PIN, "").orEmpty()) }
    var session by remember { mutableStateOf<BroadcastSession?>(null) }
    var snapshot by remember { mutableStateOf<BroadcastSnapshot?>(null) }
    var mode by remember { mutableStateOf(BroadcastMode.YOUTUBE) }
    var serverUrl by remember { mutableStateOf(BuildConfig.TEST_BROADCAST_SERVER.takeIf { it.startsWith("rtmps://") } ?: "rtmps://a.rtmps.youtube.com/live2") }
    var streamKey by remember { mutableStateOf(BuildConfig.TEST_BROADCAST_KEY) }
    var blankTest by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var surfaceReady by remember { mutableStateOf(false) }
    var openGlView by remember { mutableStateOf<OpenGlView?>(null) }
    var showOverCard by remember { mutableStateOf(false) }
    var showChaseInfo by remember { mutableStateOf(false) }
    val logoCache = remember { mutableMapOf<String, Bitmap?>() }
    val checker = remember { object : ConnectChecker {
        override fun onConnectionStarted(url: String) { message = "Connecting…" }
        override fun onConnectionSuccess() { message = "Broadcast connected"; streaming = true }
        override fun onConnectionFailed(reason: String) { message = "Connection failed: $reason"; streaming = false }
        override fun onNewBitrate(bitrate: Long) {}
        override fun onDisconnect() { message = "Broadcast disconnected"; streaming = false }
        override fun onAuthError() { message = "Stream authentication failed"; streaming = false }
        override fun onAuthSuccess() {}
    } }
    val stream = remember(blankTest) {
        if (blankTest) RtmpStream(context, checker, NoVideoSource(), SilenceAudioSource())
        else RtmpStream(context, checker)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        message = if (result.values.all { it }) "Camera and microphone ready" else "Camera and microphone permissions are required"
    }
    val havePermissions = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    DisposableEffect(stream) {
        val activity = context as? android.app.Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (stream.isStreaming) stream.stopStream()
            if (preview) stream.stopPreview()
            stream.release()
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }
    LaunchedEffect(session) {
        val active = session ?: return@LaunchedEffect
        val readApi = CloudApi(authToken = active.phoneToken, legacyAdminToken = "")
        while (isActive) {
            runCatching { withContext(Dispatchers.IO) { readApi.broadcastSnapshot() } }
                .onSuccess { snapshot = it }
                .onFailure { message = "Score refresh failed: ${it.message}" }
            delay(2_000)
        }
    }
    LaunchedEffect(snapshot?.overBreak, snapshot?.matchId, snapshot?.bowler) {
        if (snapshot?.overBreak == true) {
            delay(2_000)
            showOverCard = true
        } else {
            showOverCard = false
        }
    }
    LaunchedEffect(snapshot?.matchId, snapshot?.deliverySequence) {
        if (snapshot?.inningsNumber == 2 && (snapshot?.deliverySequence ?: 0) > 0) {
            showChaseInfo = true
            delay(4_100)
            showChaseInfo = false
        } else {
            showChaseInfo = false
        }
    }
    LaunchedEffect(snapshot, showOverCard, showChaseInfo, mode, prepared, stream) {
        val score = snapshot ?: return@LaunchedEffect
        if (mode == BroadcastMode.YOUTUBE && prepared && !blankTest) {
            val battingLogo = withContext(Dispatchers.IO) { loadBroadcastLogo(score.battingTeamLogo, logoCache) }
            val bowlingLogo = withContext(Dispatchers.IO) { loadBroadcastLogo(score.bowlingTeamLogo, logoCache) }
            val filter = ViewFilterRender()
            stream.getGlInterface().setFilter(filter)
            filter.view = if (showOverCard) createBetweenOversCanvas(context, score, battingLogo) else createBroadcastOverlayCanvas(context, score, battingLogo, bowlingLogo, showChaseInfo)
            filter.setScale(100f, 100f)
            filter.setPosition(0f, 0f)
        }
    }
    LaunchedEffect(message) {
        if ((message.startsWith("Connection failed") || message == "Stream authentication failed") && stream.isStreaming) {
            stream.stopStream()
            streaming = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("Live broadcast", style = MaterialTheme.typography.headlineSmall)
        if (session == null) {
            Text("Enter the tournament broadcast code shown on the scoring phone. It follows whichever match is currently live in that tournament.")
            OutlinedTextField(pin, { value -> if (value.length <= 6 && value.all(Char::isDigit)) pin = value }, label = { Text("Tournament broadcast code") }, modifier = Modifier.fillMaxWidth())
            if (pin.length == 6 && pin != PERMANENT_TEST_BROADCAST_PIN) Text("Latest generated PIN is ready to connect.", color = MaterialTheme.colorScheme.primary)
            if (BuildConfig.TEST_AUTH_BYPASS) {
                AssistChip(
                    onClick = { pin = PERMANENT_TEST_BROADCAST_PIN; message = "Permanent testing PIN selected" },
                    label = { Text("USE TEST PIN 301022") }
                )
                Text("The test PIN remains valid. Generating a new match PIN replaces only the previous generated PIN.", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { scope.launch {
                runCatching { withContext(Dispatchers.IO) { CloudApi(legacyAdminToken = "").redeemBroadcastPin(pin) } }
                    .onSuccess { session = it; message = "Match connected" }
                    .onFailure { message = it.message ?: "PIN invalid, expired, or replaced by a newer PIN" }
            } }, enabled = pin.length == 6) { Text("CONNECT TO MATCH") }
        } else {
            val score = snapshot
            Text(if (score == null) "Loading score…" else "${score.teamA} vs ${score.teamB} • ${score.runs}/${score.wickets} (${score.legalBalls / 6}.${score.legalBalls % 6} ov)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == BroadcastMode.YOUTUBE, onClick = { if (!streaming) { mode = BroadcastMode.YOUTUBE; serverUrl = "rtmps://a.rtmps.youtube.com/live2" } }, label = { Text("Direct YouTube") })
                FilterChip(selected = mode == BroadcastMode.REMOTE_OBS, onClick = { if (!streaming) { mode = BroadcastMode.REMOTE_OBS; serverUrl = "" } }, label = { Text("Remote OBS") })
            }
            if (BuildConfig.TEST_AUTH_BYPASS) {
                FilterChip(selected = blankTest, onClick = { if (!streaming) { blankTest = !blankTest; preview = false; prepared = false; openGlView = null } }, label = { Text("Blank-screen test") })
                if (BuildConfig.TEST_BROADCAST_KEY.isNotBlank()) Text("Testing endpoint is preloaded in this beta APK. Do not share this build outside the test group.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (mode == BroadcastMode.YOUTUBE) "Enter the RTMPS server and stream key from YouTube Live Control Room. The score is added to the outgoing video." else "Enter your public RTMP/RTMPS relay address and stream key. In OBS, open the relay stream and add the score overlay URL as a Browser Source. Internet access is required on both ends.")
            if (mode == BroadcastMode.YOUTUBE) Text("LANDSCAPE • 1920×1080 • 30 FPS • H.264 9 Mbps • AAC 128 kbps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(serverUrl, { serverUrl = it.trim() }, label = { Text(if (mode == BroadcastMode.YOUTUBE) "YouTube RTMPS server" else "Public relay RTMP/RTMPS server") }, modifier = Modifier.fillMaxWidth(), enabled = !streaming)
            OutlinedTextField(streamKey, { streamKey = it.trim() }, label = { Text("Stream key (testing only; never saved)") }, modifier = Modifier.fillMaxWidth(), enabled = !streaming)
            if (mode == BroadcastMode.REMOTE_OBS) {
                OutlinedTextField(session!!.overlayUrl, {}, label = { Text("OBS Browser Source URL — read-only") }, modifier = Modifier.fillMaxWidth(), readOnly = true)
                OutlinedButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("OBS score overlay", session!!.overlayUrl)); message = "OBS URL copied" }) { Text("COPY OBS URL") }
                Text("Treat the overlay URL as private. The scorer can revoke it by ending the PIN.")
            }
            if (!blankTest && !havePermissions) Button(onClick = { permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) { Text("ALLOW CAMERA + MICROPHONE") }
            if (!blankTest && havePermissions) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                    AndroidView(factory = { viewContext ->
                        OpenGlView(viewContext).also { view ->
                            openGlView = view
                            view.holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) = Unit
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                    surfaceReady = true
                                    runCatching {
                                        if (!prepared) prepared = prepareBroadcastPipeline(stream)
                                        stream.getGlInterface().setPreviewResolution(width, height)
                                        if (!stream.isOnPreview) stream.startPreview(view)
                                        preview = true
                                        message = "Live camera preview ready"
                                    }.onFailure { message = it.message ?: "Camera preview failed" }
                                }
                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    surfaceReady = false
                                    if (stream.isOnPreview && !stream.isStreaming) stream.stopPreview()
                                    preview = false
                                }
                            })
                        }
                    }, modifier = Modifier.fillMaxSize())
                }
            }
            if (blankTest || havePermissions) {
                val canStart = !streaming && serverUrl.isNotBlank() && streamKey.isNotBlank() && snapshot != null
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!blankTest) {
                    Button(onClick = {
                        val view = openGlView
                        if (view == null) { message = "Camera preview is not ready"; return@Button }
                        runCatching {
                            if (stream.isOnPreview) stream.stopPreview()
                            if (!prepared) prepared = prepareBroadcastPipeline(stream)
                            stream.startPreview(view)
                            preview = true
                            message = "Camera preview refreshed"
                        }
                            .onFailure { message = it.message ?: "Camera preview failed" }
                    }, enabled = surfaceReady && !streaming) { Text(if (!surfaceReady) "CAMERA LOADING…" else if (preview) "REFRESH PREVIEW" else "PREVIEW") }
                    }
                    Button(onClick = {
                        val url = buildStreamUrl(serverUrl, streamKey)
                        if (url == null) { message = "Enter an RTMP/RTMPS server and stream key"; return@Button }
                        if (mode == BroadcastMode.YOUTUBE && !url.startsWith("rtmps://")) { message = "YouTube broadcasting requires an RTMPS server"; return@Button }
                        runCatching {
                            if (!prepared) prepared = prepareBroadcastPipeline(stream)
                            if (!blankTest && !preview) { val view = openGlView ?: error("Camera preview not ready"); stream.startPreview(view); preview = true }
                            stream.startStream(url)
                            message = "Connecting…"
                        }.onFailure { message = it.message ?: "Broadcast could not start" }
                    }, enabled = canStart) { Text(if (blankTest) "START BLANK TEST" else "START") }
                    OutlinedButton(onClick = { if (stream.isStreaming) stream.stopStream(); streaming = false }, enabled = streaming) { Text("STOP") }
                }
                if (!streaming && !canStart) {
                    Text(
                        when {
                            snapshot == null -> "START is waiting for the live score."
                            serverUrl.isBlank() -> "Enter the RTMPS server to enable START."
                            streamKey.isBlank() -> "Enter the YouTube stream key to enable START."
                            else -> "START will be available when setup is complete."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (!streaming) {
                    Text("STOP becomes available after the broadcast connects.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (message.isNotBlank()) Text(message)
    }
}

internal fun buildStreamUrl(server:String, key:String):String? {
    val trimmed = server.trim().trimEnd('/')
    val secret = key.trim().trimStart('/')
    if (!trimmed.matches(Regex("^rtmps?://[^\\s/?#]+(?:/[^\\s?#]*)?")) || secret.isEmpty() || secret.any(Char::isWhitespace)) return null
    return "$trimmed/$secret"
}

private fun String.firstName(): String = trim().substringBefore(' ').ifBlank { "—" }

private fun prepareBroadcastPipeline(stream: RtmpStream): Boolean {
    stream.setVideoCodec(VideoCodec.H264)
    stream.setAudioCodec(AudioCodec.AAC)
    stream.forceFpsLimit(true)
    stream.forceBt709Color(true)
    stream.getGlInterface().apply {
        forceOrientation(OrientationForced.LANDSCAPE)
        setAspectRatioMode(AspectRatioMode.Fill)
        setEncoderSize(COMPOSITION_WIDTH, COMPOSITION_HEIGHT)
        setStreamIsPortrait(false)
        setPreviewIsPortrait(false)
        forceFpsLimit(BROADCAST_FPS)
    }
    if (!stream.prepareVideo(COMPOSITION_WIDTH, COMPOSITION_HEIGHT, BROADCAST_VIDEO_BITRATE, BROADCAST_FPS, BROADCAST_KEYFRAME_INTERVAL_SECONDS, 0)) {
        error("1080p H.264 video encoder unavailable on this device")
    }
    if (!stream.prepareAudio(BROADCAST_AUDIO_SAMPLE_RATE, true, BROADCAST_AUDIO_BITRATE)) {
        error("AAC microphone encoder unavailable")
    }
    return true
}

private fun loadBroadcastLogo(url: String, cache: MutableMap<String, Bitmap?>): Bitmap? {
    if (url.isBlank()) return null
    if (cache.containsKey(url)) return cache[url]
    val bitmap = runCatching {
        URL(url).openConnection().apply { connectTimeout = 5_000; readTimeout = 5_000 }
            .getInputStream().use(BitmapFactory::decodeStream)
    }.getOrNull()
    cache[url] = bitmap
    return bitmap
}

private fun createBroadcastOverlayCanvas(context: Context, score: BroadcastSnapshot, battingLogo: Bitmap?, bowlingLogo: Bitmap?, showChaseInfo: Boolean): View {
    fun rounded(color: Int, radius: Float = 56f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }
    fun label(text: String, sizePx: Float, color: Int, bold: Boolean = false, horizontalGravity: Int = Gravity.START) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL or horizontalGravity
        maxLines = 1
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    fun column(primary: String, secondary: String, width: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(18, 0, 18, 0)
        addView(label(primary, 27f, AndroidColor.rgb(10, 23, 58), true), LinearLayout.LayoutParams(width, 52))
        addView(label(secondary, 21f, AndroidColor.rgb(69, 79, 99)), LinearLayout.LayoutParams(width, 44))
    }
    fun batterColumn(striker: String, strikerRuns: Int, strikerBalls: Int, nonStriker: String, nonStrikerRuns: Int, nonStrikerBalls: Int, width: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(18, 0, 18, 0)
        addView(label("🏏 $striker   $strikerRuns ($strikerBalls)", 27f, AndroidColor.rgb(10, 23, 58), true), LinearLayout.LayoutParams(width, 52))
        addView(label("$nonStriker   $nonStrikerRuns ($nonStrikerBalls)", 27f, AndroidColor.rgb(10, 23, 58), true), LinearLayout.LayoutParams(width, 52))
    }
    fun teamLogo(bitmap: Bitmap?) = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(8, 8, 8, 8)
        if (bitmap != null) setImageBitmap(bitmap)
    }
    val scorebar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(AndroidColor.rgb(242, 242, 242))
    }
    scorebar.addView(teamLogo(battingLogo), LinearLayout.LayoutParams(96, 112))
    scorebar.addView(
        batterColumn(score.striker.firstName(), score.strikerRuns, score.strikerBalls, score.nonStriker.firstName(), score.nonStrikerRuns, score.nonStrikerBalls, 484),
        LinearLayout.LayoutParams(484, 112)
    )
    scorebar.addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = rounded(AndroidColor.rgb(9, 31, 98))
        if (score.inningsNumber == 2 && score.target > 0) {
            addView(label("${score.battingTeam.teamInitials()}   ${score.runs}/${score.wickets}   ${score.legalBalls / 6}.${score.legalBalls % 6} OV", 32f, AndroidColor.WHITE, true, Gravity.CENTER), LinearLayout.LayoutParams(620, 46))
            addView(label("TARGET  ${score.target}", 22f, AndroidColor.rgb(248, 213, 119), true, Gravity.CENTER), LinearLayout.LayoutParams(620, 30))
            addView(label("${score.teamA}  vs  ${score.teamB}", 17f, AndroidColor.rgb(183, 192, 218), horizontalGravity = Gravity.CENTER), LinearLayout.LayoutParams(620, 28))
        } else {
            addView(label("${score.battingTeam.teamInitials()}   ${score.runs}/${score.wickets}   ${score.legalBalls / 6}.${score.legalBalls % 6} OV", 34f, AndroidColor.WHITE, true, Gravity.CENTER), LinearLayout.LayoutParams(620, 62))
            addView(label("${score.teamA}  vs  ${score.teamB}", 18f, AndroidColor.rgb(183, 192, 218), horizontalGravity = Gravity.CENTER), LinearLayout.LayoutParams(620, 38))
        }
    }, LinearLayout.LayoutParams(620, 112))
    val bowlerOvers = "${score.bowlerLegalBalls / 6}.${score.bowlerLegalBalls % 6}"
    scorebar.addView(
        column("${score.bowler.firstName()}   $bowlerOvers-${score.bowlerRuns}-${score.bowlerWickets}", score.currentOver.joinToString("  ").ifBlank { "—" }, 484),
        LinearLayout.LayoutParams(484, 112)
    )
    scorebar.addView(teamLogo(bowlingLogo), LinearLayout.LayoutParams(96, 112))

    val canvas = FrameLayout(context).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        layoutParams = FrameLayout.LayoutParams(COMPOSITION_WIDTH, COMPOSITION_HEIGHT)
        addView(scorebar, FrameLayout.LayoutParams(1780, 112).apply {
            leftMargin = 70
            topMargin = 926
        })
        if (showChaseInfo && score.inningsNumber == 2) {
            val chaseText = buildString {
                append("${score.battingTeam.teamInitials()} need ${score.runsNeeded} runs in ${score.ballsRemaining} balls with ${score.wicketsInHand} wickets in hand")
                append("   •   ${score.bowlingTeam.teamInitials()} need ${score.wicketsInHand} wickets to win")
            }
            addView(TextView(context).apply {
                text = chaseText
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f)
                setTextColor(AndroidColor.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 0, 24, 0)
                background = rounded(AndroidColor.rgb(9, 31, 98), 28f)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSelected = true
                alpha = 0f
                post {
                    animate().alpha(1f).setDuration(300).withEndAction {
                        animate().alpha(0f).setStartDelay(3_000).setDuration(700).start()
                    }.start()
                }
            }, FrameLayout.LayoutParams(820, 48).apply { leftMargin = 70; topMargin = 866 })
        }
        addView(ImageView(context).apply {
            setImageResource(R.drawable.nmtcc_logo_transparent)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, FrameLayout.LayoutParams(198, 198, Gravity.TOP or Gravity.END).apply {
            topMargin = 42
            rightMargin = 54
        })
    }
    canvas.measure(
        View.MeasureSpec.makeMeasureSpec(COMPOSITION_WIDTH, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(COMPOSITION_HEIGHT, View.MeasureSpec.EXACTLY)
    )
    canvas.layout(0, 0, COMPOSITION_WIDTH, COMPOSITION_HEIGHT)
    return canvas
}

private fun createBetweenOversCanvas(context: Context, score: BroadcastSnapshot, battingLogo: Bitmap?): View {
    fun rounded(color: Int, radius: Float = 32f) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    fun text(value: String, size: Float, color: Int, bold: Boolean = false, gravityValue: Int = Gravity.CENTER_VERTICAL) = TextView(context).apply {
        this.text = value
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        setTextColor(color)
        gravity = gravityValue
        setPadding(14, 0, 14, 0)
        maxLines = 1
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    fun row(left: String, middle: String, runs: String, balls: String, header: Boolean = false) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (header) setBackgroundColor(AndroidColor.rgb(9, 31, 98))
        val color = if (header) AndroidColor.WHITE else AndroidColor.rgb(10, 23, 58)
        addView(text(left, if (header) 22f else 24f, color, header), LinearLayout.LayoutParams(480, 50))
        addView(text(middle, if (header) 20f else 21f, color), LinearLayout.LayoutParams(430, 50))
        addView(text(runs, 23f, color, true, Gravity.CENTER), LinearLayout.LayoutParams(120, 50))
        addView(text(balls, 23f, color, true, Gravity.CENTER), LinearLayout.LayoutParams(120, 50))
    }
    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(AndroidColor.rgb(242, 242, 242))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(AndroidColor.rgb(9, 31, 98))
            addView(ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE; if (battingLogo != null) setImageBitmap(battingLogo) }, LinearLayout.LayoutParams(90, 90))
            addView(text(score.battingTeam.uppercase(), 34f, AndroidColor.WHITE, true), LinearLayout.LayoutParams(1060, 90))
        }, LinearLayout.LayoutParams(1150, 90))
        addView(text(score.tournamentName.uppercase(), 19f, AndroidColor.rgb(69, 79, 99), true), LinearLayout.LayoutParams(1150, 44))
        addView(row("BATTER", "STATUS", "RUNS", "BALLS", true), LinearLayout.LayoutParams(1150, 50))
        score.battingCard.take(11).forEach { batter ->
            addView(row(batter.name, batter.dismissal, batter.runs.toString(), batter.balls.toString()), LinearLayout.LayoutParams(1150, 50))
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(AndroidColor.rgb(9, 31, 98))
            addView(text("OVERS  ${score.legalBalls / 6}.${score.legalBalls % 6}", 25f, AndroidColor.WHITE, true), LinearLayout.LayoutParams(575, 64))
            addView(text("TOTAL  ${score.runs}/${score.wickets}", 29f, AndroidColor.WHITE, true, Gravity.CENTER), LinearLayout.LayoutParams(575, 64))
        }, LinearLayout.LayoutParams(1150, 64))
    }
    val panelHeight = 90 + 44 + 50 + score.battingCard.take(11).size * 50 + 64
    return FrameLayout(context).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        layoutParams = FrameLayout.LayoutParams(COMPOSITION_WIDTH, COMPOSITION_HEIGHT)
        addView(panel, FrameLayout.LayoutParams(1150, panelHeight).apply { leftMargin = 385; topMargin = (COMPOSITION_HEIGHT - panelHeight) / 2 })
        addView(ImageView(context).apply { setImageResource(R.drawable.nmtcc_logo_transparent); scaleType = ImageView.ScaleType.CENTER_INSIDE }, FrameLayout.LayoutParams(198, 198, Gravity.TOP or Gravity.END).apply { topMargin = 42; rightMargin = 54 })
        measure(View.MeasureSpec.makeMeasureSpec(COMPOSITION_WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(COMPOSITION_HEIGHT, View.MeasureSpec.EXACTLY))
        layout(0, 0, COMPOSITION_WIDTH, COMPOSITION_HEIGHT)
    }
}

private fun String.teamInitials(): String {
    val words = trim().split(Regex("\\s+")).filter { it.isNotBlank() && !it.equals("the", true) }
    return words.take(3).joinToString("") { it.first().uppercase() }.ifBlank { take(2).uppercase() }
}
