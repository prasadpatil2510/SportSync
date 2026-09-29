package club.nmtcc.cricket

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.SurfaceHolder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
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
    LaunchedEffect(snapshot, mode, prepared, stream) {
        val score = snapshot ?: return@LaunchedEffect
        if (mode == BroadcastMode.YOUTUBE && prepared && !blankTest) {
            val filter = ViewFilterRender()
            stream.getGlInterface().setFilter(filter)
            filter.view = createEncodedScorebar(context, score)
            filter.setScale(94f, 15f)
            filter.setPosition(3f, 82f)
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
            Text("Enter the latest six-digit PIN shown on the scoring phone.")
            OutlinedTextField(pin, { value -> if (value.length <= 6 && value.all(Char::isDigit)) pin = value }, label = { Text("Match broadcast PIN") }, modifier = Modifier.fillMaxWidth())
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!blankTest) {
                    Button(onClick = {
                        val view = openGlView
                        if (view == null) { message = "Camera preview is not ready"; return@Button }
                        runCatching {
                            if (!prepared) prepared = prepareBroadcastPipeline(stream)
                            stream.startPreview(view)
                            preview = true
                            message = "Camera preview ready"
                        }
                            .onFailure { message = it.message ?: "Camera preview failed" }
                    }, enabled = surfaceReady && !preview && !streaming) { Text(if(surfaceReady) "PREVIEW" else "CAMERA LOADING…") }
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
                    }, enabled = !streaming && serverUrl.isNotBlank() && streamKey.isNotBlank() && snapshot != null) { Text(if (blankTest) "START BLANK TEST" else "START") }
                    OutlinedButton(onClick = { if (stream.isStreaming) stream.stopStream(); streaming = false }, enabled = streaming) { Text("STOP") }
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

private fun createEncodedScorebar(context: Context, score: BroadcastSnapshot): View {
    fun rounded(color: Int, radius: Float = 52f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }
    fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    fun column(primary: String, secondary: String, width: Int, horizontalGravity: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(24, 0, 24, 0)
        addView(label(primary, 24f, AndroidColor.rgb(10, 23, 58), true), LinearLayout.LayoutParams(width, 52).apply { gravity = horizontalGravity })
        addView(label(secondary, 15f, AndroidColor.rgb(102, 112, 133)), LinearLayout.LayoutParams(width, 34).apply { gravity = horizontalGravity })
    }

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(AndroidColor.rgb(242, 242, 242))
        layoutParams = LinearLayout.LayoutParams(1800, 162)
    }
    root.addView(
        column("🏏 ${score.striker.firstName()}  •  ${score.nonStriker.firstName()}", "STRIKER  •  NON-STRIKER", 620, Gravity.START),
        LinearLayout.LayoutParams(620, 162)
    )
    root.addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = rounded(AndroidColor.rgb(9, 31, 98))
        addView(label("${score.battingTeam.take(3).uppercase()}   ${score.runs}/${score.wickets}   ${score.legalBalls / 6}.${score.legalBalls % 6} OV", 32f, AndroidColor.WHITE, true), LinearLayout.LayoutParams(560, 88).apply { gravity = Gravity.CENTER })
        addView(label("${score.teamA}  vs  ${score.teamB}", 17f, AndroidColor.rgb(183, 192, 218)), LinearLayout.LayoutParams(560, 44).apply { gravity = Gravity.CENTER })
    }, LinearLayout.LayoutParams(560, 162))
    root.addView(
        column(score.bowler.firstName(), "THIS OVER  —", 620, Gravity.START),
        LinearLayout.LayoutParams(620, 162)
    )
    root.measure(
        View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(162, View.MeasureSpec.EXACTLY)
    )
    root.layout(0, 0, 1800, 162)
    return root
}
