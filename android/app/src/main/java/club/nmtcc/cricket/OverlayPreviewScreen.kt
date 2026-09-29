package club.nmtcc.cricket

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun OverlayPreviewScreen(api: CloudApi, onBack: () -> Unit) {
    var matches by remember { mutableStateOf(emptyList<CricketMatch>()) }
    var selectedId by remember { mutableStateOf("") }
    var match by remember { mutableStateOf<CricketMatch?>(null) }
    var scorecard by remember { mutableStateOf(Scorecard(emptyList(), emptyList())) }
    var deliveries by remember { mutableStateOf(emptyList<Delivery>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { api.matches() } }
            .onSuccess { list ->
                matches = list
                selectedId = list.firstOrNull { it.status == "LIVE" }?.id ?: list.firstOrNull()?.id.orEmpty()
            }
            .onFailure { error = it.message }
        loading = false
    }
    LaunchedEffect(selectedId) {
        if (selectedId.isBlank()) return@LaunchedEffect
        while (isActive) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val fresh = api.match(selectedId)
                    val innings = fresh.innings.lastOrNull()
                    Triple(fresh, innings?.let { api.scorecard(it.id) } ?: Scorecard(emptyList(), emptyList()), innings?.let { api.deliveries(it.id) } ?: emptyList())
                }
            }.onSuccess {
                match = it.first
                scorecard = it.second
                deliveries = it.third
                error = null
            }.onFailure { error = it.message }
            delay(2_000)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.primary) {
            Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = MaterialTheme.colorScheme.onPrimary) }
                Text("Overlay preview", color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Preview without broadcasting", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("A local five-second HD motion scene loops behind the real scoring overlay. Camera, microphone and YouTube are not started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (matches.isNotEmpty()) {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(matches.find { it.id == selectedId }?.let { "${it.teamAName} vs ${it.teamBName}" } ?: "Choose match", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        matches.forEach { item -> DropdownMenuItem(text = { Text("${item.teamAName} vs ${item.teamBName} • ${item.status}") }, onClick = { selectedId = item.id; expanded = false }) }
                    }
                }
            }
            when {
                loading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                matches.isEmpty() -> Card { Column(Modifier.padding(20.dp)) { Text("No matches available", fontWeight = FontWeight.Bold); Text("Create a match first, then return here to preview its overlay.") } }
                else -> BroadcastPreview(match, scorecard, deliveries)
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            OverlayControls()
        }
    }
}

@Composable
private fun BroadcastPreview(match: CricketMatch?, scorecard: Scorecard, deliveries: List<Delivery>) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).border(1.dp, Color(0xFFCF9F2E), RoundedCornerShape(12.dp))) {
        LocalMotionScene()
        if (match == null || match.innings.isEmpty()) {
            Surface(color = Color(0xDD08131F), shape = RoundedCornerShape(10.dp), modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(match?.let { "${it.teamAName} vs ${it.teamBName} • Waiting for play" } ?: "Loading match…", color = Color.White, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        } else {
            val innings = match.innings.last()
            val battingTeam = if (innings.battingTeamId == match.teamAId) match.teamAName else match.teamBName
            val activeBatters = scorecard.batters.filter { it.dismissal.isBlank() }.take(2)
            val bowler = scorecard.bowlers.find { it.id == innings.bowlerId } ?: scorecard.bowlers.lastOrNull()
            val currentOver = currentOver(deliveries)
            Surface(color = Color(0xEB07131F), shape = RoundedCornerShape(10.dp), modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).fillMaxWidth(.78f)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) { Text(battingTeam.uppercase(), color = Color(0xFFFFCA43), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("${innings.runs}/${innings.wickets}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black) }
                        Text("${innings.legalBalls / 6}.${innings.legalBalls % 6} OV", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = Color(0x44FFFFFF))
                    activeBatters.forEachIndexed { index, batter -> Row { Text((if (index == 0) "🏏 " else "   ") + batter.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Text("${batter.runs} (${batter.balls})", color = Color.White, fontWeight = FontWeight.Bold) } }
                    if (bowler != null) Row { Text("● ${bowler.name}", color = Color(0xFF8DE5E1), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Text("${bowler.legalBalls / 6}.${bowler.legalBalls % 6}  ${bowler.runs}/${bowler.wickets}", color = Color(0xFF8DE5E1), fontWeight = FontWeight.Bold) }
                    if (currentOver.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { currentOver.forEach { ball -> Surface(color = if (ball.wicket) Color(0xFFE73945) else Color(0xFFE9EEF3), shape = RoundedCornerShape(50)) { Text(ballLabel(ball), color = if (ball.wicket) Color.White else Color(0xFF111820), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) } } }
                }
            }
        }
        Text("LOCAL PREVIEW • 5 SEC LOOP", color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).background(Color(0x99000000), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun LocalMotionScene() {
    val transition = rememberInfiniteTransition(label = "preview-loop")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5_000), RepeatMode.Restart), label = "phase")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(listOf(Color(0xFF071A2D), Color(0xFF175B3C), Color(0xFF06101C))))
        val stripe = size.width / 9f
        repeat(9) { index -> if (index % 2 == 0) drawRect(Color(0x1218FF80), Offset(index * stripe, 0f), androidx.compose.ui.geometry.Size(stripe, size.height)) }
        drawCircle(Color(0x33FFFFFF), size.height * .32f, Offset(size.width * .5f, size.height * .55f))
        drawLine(Color(0x55FFFFFF), Offset(0f, size.height * .84f), Offset(size.width, size.height * .84f), 2f)
        val x = -40f + (size.width + 80f) * phase
        val y = size.height * (.2f + .18f * kotlin.math.sin(phase * Math.PI * 2).toFloat())
        drawCircle(Color(0xFFD83B2F), 13f, Offset(x, y))
        drawCircle(Color(0x44FFCC66), 28f, Offset(x, y))
    }
}

@Composable
private fun OverlayControls() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Overlay controls", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Preview controls are ready for the options you will provide next.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("LAYOUT") }
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("POSITION") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("COLORS") }
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("GRAPHICS") }
            }
        }
    }
}

private fun currentOver(deliveries: List<Delivery>): List<Delivery> {
    var legal = 0
    val result = mutableListOf<Delivery>()
    deliveries.sortedBy { it.sequence }.forEach { ball ->
        result += ball
        if (ball.legal) legal++
        if (legal == 6) { result.clear(); legal = 0 }
    }
    return result
}

private fun ballLabel(ball: Delivery): String = when {
    ball.wicket -> "W"
    ball.extraType == "WIDE" -> "WD${if (ball.extraRuns > 1) ball.extraRuns else ""}"
    ball.extraType == "NO_BALL" -> "NB${if (ball.batterRuns > 0) "+${ball.batterRuns}" else ""}"
    ball.batterRuns + ball.extraRuns == 0 -> "0"
    else -> (ball.batterRuns + ball.extraRuns).toString()
}
