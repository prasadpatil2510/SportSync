package club.nmtcc.cricket

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NmtccApp() }
    }
}

private enum class AppTheme { CLASSIC_RED, DARK_GOLD }

private data class BrandPalette(
    val primary: Color,
    val action: Color,
    val page: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val isDark: Boolean
)

private val ClassicPalette = BrandPalette(Color(0xFFEC1C2A), Color(0xFF159A96), Color(0xFFF7F7F7), Color.White, Color(0xFF292929), Color(0xFF929292), false)
private val GoldPalette = BrandPalette(Color(0xFFB88A2A), Color(0xFFD4AF37), Color(0xFF0D0D0D), Color(0xFF191919), Color(0xFFF5ECD8), Color(0xFFB7A98D), true)
private val LocalBrandPalette = staticCompositionLocalOf { ClassicPalette }
private val LocalThemeChoice = staticCompositionLocalOf { AppTheme.CLASSIC_RED to { _: AppTheme -> } }

private val AppRed: Color @Composable get() = LocalBrandPalette.current.primary
private val ActionTeal: Color @Composable get() = LocalBrandPalette.current.action
private val Page: Color @Composable get() = LocalBrandPalette.current.page
private val Ink: Color @Composable get() = LocalBrandPalette.current.ink
private val Muted: Color @Composable get() = LocalBrandPalette.current.muted

private enum class Screen { HOME, CREATE_TOURNAMENT, TOURNAMENT, ADD_TEAM, TEAM, EDIT_TEAM, ADD_PLAYER, ADD_EXISTING_PLAYER, CREATE_MATCH, LINEUP, LIVE_LINEUP, TOSS, SCORE }

@Composable
fun NmtccApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("sportsync-ui", Context.MODE_PRIVATE) }
    var selectedTheme by remember { mutableStateOf(runCatching { AppTheme.valueOf(preferences.getString("theme", AppTheme.CLASSIC_RED.name)!!) }.getOrDefault(AppTheme.CLASSIC_RED)) }
    val palette = if (selectedTheme == AppTheme.DARK_GOLD) GoldPalette else ClassicPalette
    val selectTheme: (AppTheme) -> Unit = { choice -> selectedTheme = choice; preferences.edit().putString("theme", choice.name).apply() }
    val scheme = if (palette.isDark) darkColorScheme(primary = palette.primary, secondary = palette.action, background = palette.page, surface = palette.surface, onPrimary = Color.Black, onSecondary = Color.Black, onBackground = palette.ink, onSurface = palette.ink)
    else lightColorScheme(primary = palette.primary, secondary = palette.action, background = palette.page, surface = palette.surface, onPrimary = Color.White, onSecondary = Color.White, onBackground = palette.ink, onSurface = palette.ink)
    CompositionLocalProvider(LocalBrandPalette provides palette, LocalThemeChoice provides (selectedTheme to selectTheme)) {
    MaterialTheme(colorScheme = scheme) {
        var organiserPin by remember { mutableStateOf<String?>(null) }
        if (organiserPin == null) {
            OrganiserLoginScreen { organiserPin = it }
            return@MaterialTheme
        }
        val api = remember(organiserPin) { CloudApi(writeToken = organiserPin!!) }
        var screen by remember { mutableStateOf(Screen.HOME) }
        var tournament by remember { mutableStateOf<Tournament?>(null) }
        var team by remember { mutableStateOf<Team?>(null) }
        var cricketMatch by remember { mutableStateOf<CricketMatch?>(null) }
        var refresh by remember { mutableIntStateOf(0) }
        Surface(Modifier.fillMaxSize(), color = Page) {
            when (screen) {
                Screen.HOME -> TournamentListScreen(api, refresh, onCreate = { screen = Screen.CREATE_TOURNAMENT }, onOpen = { tournament = it; screen = Screen.TOURNAMENT })
                Screen.CREATE_TOURNAMENT -> CreateTournamentScreen(api, onBack = { screen = Screen.HOME }, onCreated = { tournament = it; refresh++; screen = Screen.TOURNAMENT })
                Screen.TOURNAMENT -> TournamentScreen(api, tournament!!, refresh, onBack = { refresh++; screen = Screen.HOME }, onAddTeam = { screen = Screen.ADD_TEAM }, onTeam = { team = it; screen = Screen.TEAM }, onNewMatch = { screen = Screen.CREATE_MATCH }, onMatch = { cricketMatch = it; screen = if (it.status == "SCHEDULED") Screen.LINEUP else Screen.SCORE })
                Screen.ADD_TEAM -> AddTeamScreen(api, tournament!!, onBack = { screen = Screen.TOURNAMENT }, onCreated = { refresh++; screen = Screen.TOURNAMENT })
                Screen.TEAM -> TeamScreen(api, team!!, refresh, onBack = { refresh++; screen = Screen.TOURNAMENT }, onEditTeam = { screen = Screen.EDIT_TEAM }, onAddPlayer = { screen = Screen.ADD_PLAYER }, onAddExisting = { screen = Screen.ADD_EXISTING_PLAYER }, onChanged = { refresh++ })
                Screen.EDIT_TEAM -> EditTeamScreen(api, team!!, onBack = { screen = Screen.TEAM }, onSaved = { team = it; refresh++; screen = Screen.TEAM })
                Screen.ADD_PLAYER -> AddPlayerScreen(api, team!!, onBack = { screen = Screen.TEAM }, onCreated = { refresh++; screen = Screen.TEAM })
                Screen.ADD_EXISTING_PLAYER -> AddExistingPlayerScreen(api, team!!, onBack = { screen = Screen.TEAM }, onAdded = { refresh++; screen = Screen.TEAM })
                Screen.CREATE_MATCH -> CreateMatchScreen(api, tournament!!, onBack = { screen = Screen.TOURNAMENT }, onCreated = { cricketMatch = it; screen = Screen.LINEUP })
                Screen.LINEUP -> LineupScreen(api, cricketMatch!!, onBack = { screen = Screen.TOURNAMENT }, onSaved = { screen = Screen.TOSS })
                Screen.LIVE_LINEUP -> LineupScreen(api, cricketMatch!!, onBack = { screen = Screen.SCORE }, onSaved = { screen = Screen.SCORE }, liveEdit = true)
                Screen.TOSS -> TossScreen(api, cricketMatch!!, onBack = { screen = Screen.LINEUP }, onStarted = { cricketMatch = it; screen = Screen.SCORE })
                Screen.SCORE -> ScoringScreen(api, cricketMatch!!, onBack = { refresh++; screen = Screen.TOURNAMENT }, onUpdated = { cricketMatch = it })
            }
        }
    }
    }
}

@Composable
private fun OrganiserLoginScreen(onAuthenticated: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Page)) {
        AppHeader("SportSync Cricket", back = false)
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val theme = LocalThemeChoice.current.first
            Image(painter = painterResource(if (theme == AppTheme.DARK_GOLD) R.drawable.sportsync_logo_dark_gold else R.drawable.sportsync_logo_classic), contentDescription = "SportSync", modifier = Modifier.fillMaxWidth().height(105.dp).padding(horizontal = 8.dp))
            Spacer(Modifier.height(22.dp))
            Text("Organiser access", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Enter your testing PIN to manage tournaments, teams and players.", color = Muted, modifier = Modifier.padding(vertical = 12.dp))
            OutlinedTextField(value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it }, label = { Text("4–8 digit PIN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
            Button(onClick = { checking = true; error = null; scope.launch { val accepted = withContext(Dispatchers.IO) { CloudApi(writeToken = pin).adminAccess() }; if (accepted) onAuthenticated(pin) else error = "Incorrect organiser PIN"; checking = false } }, enabled = pin.length >= 4 && !checking, colors = ButtonDefaults.buttonColors(containerColor = ActionTeal), modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(54.dp)) { Text(if (checking) "CHECKING…" else "CONTINUE") }
        }
    }
}

@Composable
private fun AppHeader(title: String, back: Boolean = true, onBack: () -> Unit = {}) {
    var themeMenu by remember { mutableStateOf(false) }
    val (theme, selectTheme) = LocalThemeChoice.current
    Surface(color = AppRed, shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(72.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back) Text("‹", color = Color.White, fontSize = 48.sp, modifier = Modifier.width(48.dp).clickable { onBack() })
            else Text("☰", color = Color.White, fontSize = 28.sp, modifier = Modifier.width(48.dp))
            Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box {
                Text(if (theme == AppTheme.DARK_GOLD) "◆" else "●", color = if (theme == AppTheme.DARK_GOLD) Color(0xFFFFE28A) else Color.White, fontSize = 24.sp, modifier = Modifier.padding(10.dp).clickable { themeMenu = true })
                DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                    DropdownMenuItem(text = { Text("Classic Red") }, leadingIcon = { Text("●", color = Color(0xFFEC1C2A)) }, onClick = { selectTheme(AppTheme.CLASSIC_RED); themeMenu = false })
                    DropdownMenuItem(text = { Text("Dark Gold") }, leadingIcon = { Text("◆", color = Color(0xFFD4AF37)) }, onClick = { selectTheme(AppTheme.DARK_GOLD); themeMenu = false })
                }
            }
        }
    }
}

@Composable
private fun TournamentListScreen(api: CloudApi, refresh: Int, onCreate: () -> Unit, onOpen: (Tournament) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tournaments by remember { mutableStateOf(emptyList<Tournament>()) }
    LaunchedEffect(refresh) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { api.tournaments() } }.onSuccess { tournaments = it }.onFailure { error = it.message }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        AppHeader("SportSync Cricket", back = false)
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(14.dp), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("Matches", "Tournaments", "Teams", "Stats").forEach { Text(it, color = if (it == "Tournaments") AppRed else Ink, fontWeight = if (it == "Tournaments") FontWeight.Bold else FontWeight.Normal) }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Want to host a tournament?", fontSize = 18.sp, modifier = Modifier.weight(1f))
            Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = ActionTeal)) { Text("Register") }
        }
        ChoiceRow(listOf("Your", "Participate", "Network", "All"), "Your", {})
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppRed) }
        else if (error != null) ErrorCard(error!!) { scope.launch { loading = true; runCatching { withContext(Dispatchers.IO) { api.tournaments() } }.onSuccess { tournaments = it; error = null }; loading = false } }
        else if (tournaments.isEmpty()) EmptyState("No tournaments yet", "Create your first tournament to add teams and begin scheduling.", "CREATE TOURNAMENT", onCreate)
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(tournaments, key = { it.id }) { item -> TournamentCard(item) { onOpen(item) } }
        }
    }
}

@Composable
private fun TournamentCard(item: Tournament, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(3.dp), elevation = CardDefaults.cardElevation(3.dp)) {
        Box(Modifier.fillMaxWidth().height(155.dp).background(Color(0xFF183D5B))) {
            Text("NMTCC", color = Color(0x33FFFFFF), fontSize = 52.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
            Text(item.status, color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(AppRed, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 6.dp))
            Text(item.name, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
        }
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("${item.startDate} to ${item.endDate}", color = Muted); Text(item.city, color = Muted) }
            Text("Open", color = ActionTeal, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CreateTournamentScreen(api: CloudApi, onBack: () -> Unit, onCreated: (Tournament) -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }; var ground by remember { mutableStateOf("") }
    var organiser by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }; var end by remember { mutableStateOf("") }; var category by remember { mutableStateOf("OPEN") }
    var ball by remember { mutableStateOf("TENNIS") }; var pitch by remember { mutableStateOf("TURF") }; var matchType by remember { mutableStateOf("LIMITED_OVERS") }
    Column(Modifier.fillMaxSize()) {
        AppHeader("Add a tournament / series", onBack = onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { UploadPlaceholders() }
            item { FormField("Tournament / series name*", name) { name = it } }
            item { FormField("City / town*", city) { city = it } }
            item { FormField("Ground*", ground) { ground = it } }
            item { FormField("Organiser name*", organiser) { organiser = it } }
            item { FormField("Organiser number", phone) { phone = it } }
            item { FormField("Organiser email", email) { email = it } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.weight(1f)) { FormField("Start date*", start) { start = it } }; Box(Modifier.weight(1f)) { FormField("End date*", end) { end = it } } } }
            item { SectionChoice("Tournament category*", listOf("OPEN", "CORPORATE", "COMMUNITY", "SCHOOL", "SERIES", "COLLEGE"), category) { category = it } }
            item { SectionChoice("Select ball type*", listOf("TENNIS", "LEATHER", "OTHER"), ball) { ball = it } }
            item { SectionChoice("Pitch type", listOf("ROUGH", "CEMENT", "TURF", "ASTROTURF", "MATTING"), pitch) { pitch = it } }
            item { SectionChoice("Match type*", listOf("LIMITED_OVERS", "BOX_TURF", "PAIR_CRICKET", "TEST_MATCH", "THE_HUNDRED"), matchType) { matchType = it } }
            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        }
        Button(onClick = {
            if (name.isBlank() || city.isBlank() || ground.isBlank() || organiser.isBlank() || start.isBlank() || end.isBlank()) { error = "Complete all required fields"; return@Button }
            saving = true; scope.launch { runCatching { withContext(Dispatchers.IO) { api.createTournament(TournamentDraft(name,city,ground,organiser,phone,email,start,end,category,ball,pitch,matchType)) } }.onSuccess(onCreated).onFailure { error = it.message }; saving = false }
        }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(0.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionTeal)) { Text(if (saving) "SAVING…" else "CREATE TOURNAMENT", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun TournamentScreen(api: CloudApi, tournament: Tournament, refresh: Int, onBack: () -> Unit, onAddTeam: () -> Unit, onTeam: (Team) -> Unit, onNewMatch: () -> Unit, onMatch: (CricketMatch) -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Matches") }; var teams by remember { mutableStateOf(emptyList<Team>()) }; var matches by remember { mutableStateOf(emptyList<CricketMatch>()) }; var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }; var importMessage by remember { mutableStateOf<String?>(null) }; var localRefresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(tournament.id, refresh, localRefresh) { loading = true; val result=runCatching { withContext(Dispatchers.IO) { api.tournamentTeams(tournament.id) to api.tournamentMatches(tournament.id) } }; result.onSuccess { teams=it.first; matches=it.second }; loading = false }
    Column(Modifier.fillMaxSize()) {
        AppHeader(tournament.name, onBack = onBack)
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("Matches", "Teams", "Points Table", "Leaderboard").forEach { item -> Column(Modifier.clickable { tab = item }.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(item, fontWeight = if (tab == item) FontWeight.Bold else FontWeight.Normal); if (tab == item) Box(Modifier.padding(top = 10.dp).height(3.dp).width(70.dp).background(AppRed)) } }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = {
                    importing = true; importMessage = null
                    scope.launch { runCatching { withContext(Dispatchers.IO) { api.refreshAuctionData(tournament.id) } }
                        .onSuccess { result -> importMessage = "Auction refreshed: ${result.teamsCreated} new teams, ${result.playersCreated} new players, ${result.membershipsAdded} squad links"; localRefresh++ }
                        .onFailure { importMessage = it.message ?: "Auction refresh failed" }; importing = false }
                }, enabled = !importing, modifier = Modifier.fillMaxWidth()) { Text(if (importing) "REFRESHING AUCTION DATA…" else "↻  REFRESH AUCTION DATA") }
                if (importMessage != null) Text(importMessage!!, color = if (importMessage!!.startsWith("Auction refreshed")) ActionTeal else MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (tab == "Matches" && loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (tab == "Matches" && matches.isEmpty()) EmptyState("No matches yet", "Set up teams, playing XI and toss to begin scoring.", "START A MATCH", onNewMatch)
        else if (tab == "Matches") Box(Modifier.fillMaxSize()) { LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(matches,key={it.id}){item->MatchCard(item){onMatch(item)}}}; Button(onClick=onNewMatch,modifier=Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),colors=ButtonDefaults.buttonColors(containerColor=ActionTeal)){Text("＋  Start a match")} }
        else if (tab != "Teams") EmptyState(tab, "Results from completed matches will appear here in the statistics phase.", "VIEW MATCHES") { tab = "Matches" }
        else if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (teams.isEmpty()) EmptyState("Invite Captains to Add Teams", "Share an invitation later, or add teams manually now.", "ADD MANUALLY", onAddTeam)
        else Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(teams, key = { it.id }) { TeamCard(it) { onTeam(it) } } }
            Button(onClick = onAddTeam, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionTeal)) { Text("＋  Add teams", fontSize = 18.sp) }
        }
    }
}

@Composable
private fun TeamCard(team: Team, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), elevation = CardDefaults.cardElevation(2.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { RemoteAvatar(team.name, team.logoUrl); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(team.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text(team.city, color = Muted); if (team.captainName.isNotBlank()) Text("ⓒ ${team.captainName}", color = Muted) }; Text("Members", color = ActionTeal) } }
}

@Composable
private fun AddTeamScreen(api: CloudApi, tournament: Tournament, onBack: () -> Unit, onCreated: (Team) -> Unit) {
    val scope = rememberCoroutineScope(); var name by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }; var captain by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) { AppHeader("Add teams to ${tournament.name}", onBack = onBack); LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar("Logo", 110) } }; item { FormField("Team name*", name) { name = it } }; item { FormField("City / town*", city) { city = it } }; item { FormField("Team captain name", captain) { captain = it } }; item { FormField("Captain phone number", phone) { phone = it } }; if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) } }
        Button(onClick = { if (name.isBlank() || city.isBlank()) { error = "Team name and city are required"; return@Button }; saving = true; scope.launch { runCatching { withContext(Dispatchers.IO) { api.createTeam(tournament.id, TeamDraft(name,city,captain,phone)) } }.onSuccess(onCreated).onFailure { error = it.message }; saving = false } }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(0.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionTeal)) { Text(if (saving) "SAVING…" else "DONE") }
    }
}

@Composable
private fun TeamScreen(api: CloudApi, team: Team, refresh: Int, onBack: () -> Unit, onEditTeam: () -> Unit, onAddPlayer: () -> Unit, onAddExisting: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var players by remember { mutableStateOf(emptyList<Player>()) }; var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(team.id, refresh) { loading = true; players = runCatching { withContext(Dispatchers.IO) { api.teamPlayers(team.id) } }.getOrDefault(emptyList()); loading = false }
    Column(Modifier.fillMaxSize()) { AppHeader(team.name, onBack = onBack); Surface(color = ActionTeal) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Text("Build your complete squad", color = Color.White, modifier = Modifier.weight(1f)); TextButton(onClick = onEditTeam) { Text("EDIT TEAM", color = Color.White, fontWeight = FontWeight.Bold) } } }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(players, key = { it.id }) { player -> PlayerCard(player, onRemove = { scope.launch { runCatching { withContext(Dispatchers.IO) { api.removePlayerFromTeam(team.id, player.id) } }.onSuccess { onChanged() }.onFailure { error = it.message } } }) } }
        Row(Modifier.fillMaxWidth()) { OutlinedButton(onClick = onAddExisting, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(0.dp)) { Text("ADD EXISTING") }; Button(onClick = onAddPlayer, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(0.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionTeal)) { Text("NEW PLAYER", fontSize = 16.sp) } }
    }
}

@Composable
private fun PlayerCard(player: Player, onRemove: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { RemoteAvatar(player.name, player.photoUrl); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(player.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (player.isAdmin) RoleBadge("Admin"); if (player.isCaptain) RoleBadge("Captain"); if (player.isWicketKeeper) RoleBadge("WK"); RoleBadge(player.role.replace('_',' ')) } }; if (onRemove != null) TextButton(onClick = onRemove) { Text("REMOVE", color = AppRed, fontSize = 12.sp) } else Text("0\nMat", color = Muted) } }
}

@Composable
private fun RemoteAvatar(label: String, url: String?, size: Int = 64) {
    if (url.isNullOrBlank()) Avatar(label, size) else AsyncImage(model = url, contentDescription = "$label photo", contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
private fun EditTeamScreen(api: CloudApi, team: Team, onBack: () -> Unit, onSaved: (Team) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(team.name) }; var city by remember { mutableStateOf(team.city) }; var captain by remember { mutableStateOf(team.captainName) }; var phone by remember { mutableStateOf(team.captainPhone) }
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        AppHeader("Modify team", onBack = onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar(name.ifBlank { "Team" }, 110) } }
            item { FormField("Team name*", name) { name = it } }
            item { FormField("City / town*", city) { city = it } }
            item { FormField("Team captain name", captain) { captain = it } }
            item { FormField("Captain phone number", phone) { phone = it } }
            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
        }
        Button(onClick = { if (name.isBlank() || city.isBlank()) { error = "Team name and city are required"; return@Button }; saving = true; scope.launch { runCatching { withContext(Dispatchers.IO) { api.updateTeam(team.id, TeamDraft(name, city, captain, phone)) } }.onSuccess(onSaved).onFailure { error = it.message }; saving = false } }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(0.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionTeal)) { Text(if (saving) "SAVING…" else "SAVE TEAM") }
    }
}

@Composable
private fun AddExistingPlayerScreen(api: CloudApi, team: Team, onBack: () -> Unit, onAdded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }; var allPlayers by remember { mutableStateOf(emptyList<Player>()) }; var teamPlayerIds by remember { mutableStateOf(emptySet<String>()) }
    var addingId by remember { mutableStateOf<String?>(null) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(team.id) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { api.players() to api.teamPlayers(team.id).map { it.id }.toSet() } }.onSuccess { allPlayers = it.first; teamPlayerIds = it.second }.onFailure { error = it.message }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        AppHeader("Add existing player", onBack = onBack)
        Text("Choose a saved player to add to ${team.name}.", color = Muted, modifier = Modifier.padding(16.dp))
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(allPlayers.filterNot { teamPlayerIds.contains(it.id) }, key = { it.id }) { player ->
                Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(player.name, 56); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(player.name, fontWeight = FontWeight.SemiBold); Text(player.role.replace('_', ' '), color = Muted) }; Button(onClick = { addingId = player.id; scope.launch { runCatching { withContext(Dispatchers.IO) { api.addPlayerToTeam(team.id, player.id) } }.onSuccess { onAdded() }.onFailure { error = it.message }; addingId = null } }, enabled = addingId == null, colors = ButtonDefaults.buttonColors(containerColor = ActionTeal)) { Text(if (addingId == player.id) "…" else "ADD") } } }
            }
        }
    }
}

@Composable
private fun AddPlayerScreen(api: CloudApi, team: Team, onBack: () -> Unit, onCreated: (Player) -> Unit) {
    val scope = rememberCoroutineScope(); var name by remember { mutableStateOf("") }; var role by remember { mutableStateOf("BATTER") }; var admin by remember { mutableStateOf(false) }; var captain by remember { mutableStateOf(false) }; var keeper by remember { mutableStateOf(false) }; var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) { AppHeader("Add player to ${team.name}", onBack = onBack); LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { item { FormField("Player name*", name) { name = it } }; item { SectionChoice("Playing role", listOf("BATTER", "BOWLER", "ALL_ROUNDER", "PLAYER"), role) { role = it } }; item { Text("Assign roles", fontSize = 20.sp, fontWeight = FontWeight.Bold) }; item { ToggleRow("Team admin", admin) { admin = it } }; item { ToggleRow("Captain", captain) { captain = it } }; item { ToggleRow("Wicket keeper", keeper) { keeper = it } }; if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) } }
        Button(onClick = { if (name.isBlank()) { error = "Player name is required"; return@Button }; saving = true; scope.launch { runCatching { withContext(Dispatchers.IO) { api.createPlayer(team.id, PlayerDraft(name,role,admin,captain,keeper)) } }.onSuccess(onCreated).onFailure { error = it.message }; saving = false } }, enabled = !saving, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(0.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionTeal)) { Text(if (saving) "SAVING…" else "ADD PLAYER") }
    }
}

@Composable
private fun MatchCard(item: CricketMatch, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick=onClick), elevation=CardDefaults.cardElevation(2.dp)) { Column(Modifier.fillMaxWidth().padding(16.dp)) { Row(verticalAlignment=Alignment.CenterVertically) { Text(item.roundName, color=Muted, modifier=Modifier.weight(1f)); RoleBadge(item.status) }; Spacer(Modifier.height(8.dp)); Text("${item.teamAName}  vs  ${item.teamBName}",fontSize=20.sp,fontWeight=FontWeight.Bold); Text("${item.overs} overs • ${item.ground}",color=Muted); if(item.result.isNotBlank()) Text(item.result,color=ActionTeal,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=8.dp)) } }
}

@Composable
private fun CreateMatchScreen(api: CloudApi, tournament: Tournament, onBack: () -> Unit, onCreated: (CricketMatch) -> Unit) {
    val scope=rememberCoroutineScope(); var teams by remember{mutableStateOf(emptyList<Team>())}; var teamA by remember{mutableStateOf("")}; var teamB by remember{mutableStateOf("")}; var round by remember{mutableStateOf("League Match")}; var whenText by remember{mutableStateOf("")}; var ground by remember{mutableStateOf(tournament.ground)}; var overs by remember{mutableStateOf("20")}; var error by remember{mutableStateOf<String?>(null)}; var saving by remember{mutableStateOf(false)}
    LaunchedEffect(tournament.id){ teams=runCatching{withContext(Dispatchers.IO){api.tournamentTeams(tournament.id)}}.getOrDefault(emptyList()) }
    Column(Modifier.fillMaxSize()){AppHeader("Start a match",onBack=onBack); LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{FormField("Round",round){round=it}};item{Text("Select first team",fontWeight=FontWeight.Bold);TeamSelector(teams,teamA){teamA=it}};item{Text("Select second team",fontWeight=FontWeight.Bold);TeamSelector(teams.filterNot{it.id==teamA},teamB){teamB=it}};item{FormField("Date and time",whenText){whenText=it}};item{FormField("Ground",ground){ground=it}};item{FormField("Overs per innings",overs){if(it.all(Char::isDigit))overs=it}};if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}}; Button(onClick={if(teamA.isBlank()||teamB.isBlank()){error="Select two teams";return@Button};saving=true;scope.launch{runCatching{withContext(Dispatchers.IO){api.createMatch(MatchDraft(tournament.id,round,teamA,teamB,whenText,ground,overs.toIntOrNull()?:20))}}.onSuccess(onCreated).onFailure{error=it.message};saving=false}},enabled=!saving,modifier=Modifier.fillMaxWidth().height(64.dp),shape=RoundedCornerShape(0.dp),colors=ButtonDefaults.buttonColors(containerColor=ActionTeal)){Text(if(saving)"CREATING…" else "SELECT PLAYING XI")}}
}

@Composable
private fun TeamSelector(teams: List<Team>, selected: String, onSelect:(String)->Unit){ Column(verticalArrangement=Arrangement.spacedBy(7.dp)){teams.forEach{t->Surface(color=if(t.id==selected)Color(0xFFD9F2F0) else Color.White,shape=RoundedCornerShape(5.dp),modifier=Modifier.fillMaxWidth().border(1.dp,if(t.id==selected)ActionTeal else Color.LightGray,RoundedCornerShape(5.dp)).clickable{onSelect(t.id)}){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Avatar(t.name,44);Spacer(Modifier.width(10.dp));Text(t.name,modifier=Modifier.weight(1f));if(t.id==selected)Text("✓",color=ActionTeal,fontSize=22.sp)}}}} }

@Composable
private fun LineupScreen(api: CloudApi, match: CricketMatch, onBack:()->Unit, onSaved:()->Unit, liveEdit:Boolean=false){
    val scope=rememberCoroutineScope();var a by remember{mutableStateOf(emptyList<Player>())};var b by remember{mutableStateOf(emptyList<Player>())};var selectedA by remember{mutableStateOf(emptySet<String>())};var selectedB by remember{mutableStateOf(emptySet<String>())};var loading by remember{mutableStateOf(true)};var saving by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(match.id){runCatching{withContext(Dispatchers.IO){Triple(api.teamPlayers(match.teamAId),api.teamPlayers(match.teamBId),api.lineup(match.id))}}.onSuccess{a=it.first;b=it.second;val existingA=it.third.filter{p->p.teamId==match.teamAId}.map{p->p.id}.toSet();val existingB=it.third.filter{p->p.teamId==match.teamBId}.map{p->p.id}.toSet();selectedA=existingA.ifEmpty{a.map{p->p.id}.toSet()};selectedB=existingB.ifEmpty{b.map{p->p.id}.toSet()}}.onFailure{error=it.message};loading=false}
    Column(Modifier.fillMaxSize()){AppHeader(if(liveEdit)"Replace playing players" else "Select playing XI",onBack=onBack);if(liveEdit)Text("Changes apply immediately. Keep current batters and bowler selected unless they are being replaced.",color=Muted,modifier=Modifier.padding(16.dp));if(loading)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp)){item{Text(match.teamAName,fontSize=21.sp,fontWeight=FontWeight.Bold)};items(a,key={"a${it.id}"}){p->PlayerCheck(p,selectedA.contains(p.id)){selectedA=selectedA.toggle(p.id)}};item{Spacer(Modifier.height(18.dp));Text(match.teamBName,fontSize=21.sp,fontWeight=FontWeight.Bold)};items(b,key={"b${it.id}"}){p->PlayerCheck(p,selectedB.contains(p.id)){selectedB=selectedB.toggle(p.id)}};if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}};Button(onClick={if(selectedA.size<2||selectedB.size<2){error="Select at least two players from each team";return@Button};saving=true;scope.launch{runCatching{withContext(Dispatchers.IO){api.saveLineup(match.id,match.teamAId,selectedA);api.saveLineup(match.id,match.teamBId,selectedB)}}.onSuccess{onSaved()}.onFailure{error=it.message};saving=false}},enabled=!saving,modifier=Modifier.fillMaxWidth().height(64.dp),shape=RoundedCornerShape(0.dp),colors=ButtonDefaults.buttonColors(containerColor=ActionTeal)){Text(if(saving)"SAVING…" else if(liveEdit)"SAVE CHANGES" else "CONTINUE TO TOSS")}}
}
private fun Set<String>.toggle(value:String)=if(contains(value))minus(value) else plus(value)
@Composable private fun PlayerCheck(player:Player,checked:Boolean,onCheck:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=onCheck).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(checked,onCheckedChange={onCheck()},colors=CheckboxDefaults.colors(checkedColor=ActionTeal));Text(player.name,modifier=Modifier.weight(1f));Text(player.role.replace('_',' '),color=Muted,fontSize=12.sp)}}

@Composable
private fun TossScreen(api: CloudApi, match: CricketMatch, onBack:()->Unit, onStarted:(CricketMatch)->Unit){
    val scope=rememberCoroutineScope();var lineup by remember{mutableStateOf(emptyList<Player>())};var winner by remember{mutableStateOf(match.teamAId)};var decision by remember{mutableStateOf("BAT")};var striker by remember{mutableStateOf("")};var nonStriker by remember{mutableStateOf("")};var bowler by remember{mutableStateOf("")};var saving by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(match.id){runCatching{withContext(Dispatchers.IO){api.lineup(match.id)}}.onSuccess{lineup=it}.onFailure{error=it.message}}
    val other=if(winner==match.teamAId)match.teamBId else match.teamAId;val batting=if(decision=="BAT")winner else other;val bowling=if(batting==match.teamAId)match.teamBId else match.teamAId;val batters=lineup.filter{it.teamId==batting};val bowlers=lineup.filter{it.teamId==bowling}
    LaunchedEffect(batting,bowling,lineup){striker=batters.getOrNull(0)?.id?:"";nonStriker=batters.getOrNull(1)?.id?:"";bowler=bowlers.getOrNull(0)?.id?:""}
    Column(Modifier.fillMaxSize()){AppHeader("Toss & opening players",onBack=onBack);LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){item{Text("Who won the toss?",fontSize=20.sp,fontWeight=FontWeight.Bold);TeamSelector(listOf(Team(match.teamAId,match.teamAName),Team(match.teamBId,match.teamBName)),winner){winner=it}};item{SectionChoice("Winner elected to",listOf("BAT","BOWL"),decision){decision=it}};item{PlayerSelector("Striker",batters,striker){striker=it}};item{PlayerSelector("Non-striker",batters.filterNot{it.id==striker},nonStriker){nonStriker=it}};item{PlayerSelector("Opening bowler",bowlers,bowler){bowler=it}};if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}};Button(onClick={if(striker.isBlank()||nonStriker.isBlank()||bowler.isBlank()){error="Select both opening batters and the bowler";return@Button};saving=true;scope.launch{runCatching{withContext(Dispatchers.IO){api.recordToss(match.id,winner,decision,striker,nonStriker,bowler)}}.onSuccess(onStarted).onFailure{error=it.message};saving=false}},enabled=!saving,modifier=Modifier.fillMaxWidth().height(64.dp),shape=RoundedCornerShape(0.dp),colors=ButtonDefaults.buttonColors(containerColor=ActionTeal)){Text(if(saving)"STARTING…" else "START SCORING")}}
}

@Composable private fun PlayerSelector(title:String,players:List<Player>,selected:String,onSelect:(String)->Unit){Column(verticalArrangement=Arrangement.spacedBy(7.dp)){Text(title,fontWeight=FontWeight.Bold);players.forEach{p->Surface(color=if(p.id==selected)Color(0xFFD9F2F0) else Color.White,shape=RoundedCornerShape(5.dp),modifier=Modifier.fillMaxWidth().border(1.dp,if(p.id==selected)ActionTeal else Color.LightGray,RoundedCornerShape(5.dp)).clickable{onSelect(p.id)}){Row(Modifier.padding(11.dp)){Text(p.name,modifier=Modifier.weight(1f));if(p.id==selected)Text("✓",color=ActionTeal)}}}}}

@Composable private fun DropdownChoice(title:String,options:List<String>,selected:String,onSelect:(String)->Unit){var open by remember{mutableStateOf(false)};Column{Text(title,fontWeight=FontWeight.Bold);Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){Text(selected.replace('_',' '),modifier=Modifier.weight(1f));Text("▾")};DropdownMenu(expanded=open,onDismissRequest={open=false}){options.forEach{option->DropdownMenuItem(text={Text(option.replace('_',' '))},onClick={open=false;onSelect(option)})}}}}}
@Composable private fun DropdownPlayerSelector(title:String,players:List<Player>,selected:String,onSelect:(String)->Unit){var open by remember{mutableStateOf(false)};val label=players.find{it.id==selected}?.name?:"Select player";Column{Text(title,fontWeight=FontWeight.Bold);Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth(),enabled=players.isNotEmpty()){Text(label,modifier=Modifier.weight(1f));Text("▾")};DropdownMenu(expanded=open,onDismissRequest={open=false}){players.forEach{player->DropdownMenuItem(text={Text(player.name)},onClick={open=false;onSelect(player.id)})}}}}}

@Composable
private fun ScoringScreen(api:CloudApi,initial:CricketMatch,onBack:()->Unit,onUpdated:(CricketMatch)->Unit){
    val scope=rememberCoroutineScope()
    var match by remember{mutableStateOf(initial)};var lineup by remember{mutableStateOf(emptyList<Player>())};var deliveries by remember{mutableStateOf(emptyList<Delivery>())};var scorecard by remember{mutableStateOf(Scorecard(emptyList(),emptyList()))}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var generation by remember{mutableIntStateOf(0)};var showBowler by remember{mutableStateOf(false)};var showWicket by remember{mutableStateOf(false)};var extraTypeDialog by remember{mutableStateOf<String?>(null)};var showReplaceStriker by remember{mutableStateOf(false)}
    var mustChangeBowler by remember{mutableStateOf(false)};var syncedTick by remember{mutableIntStateOf(0)};var showSynced by remember{mutableStateOf(false)}
    var dismissal by remember{mutableStateOf("BOWLED")};var dismissedId by remember{mutableStateOf("")};var nextBatterId by remember{mutableStateOf("")};var fielderId by remember{mutableStateOf("")};var assistantFielderId by remember{mutableStateOf("")}
    suspend fun snapshot(expected:Int):CricketMatch{val bundle=withContext(Dispatchers.IO){val fresh=api.match(match.id);val current=fresh.innings.lastOrNull();Triple(fresh,if(current!=null)api.deliveries(current.id)else emptyList(),if(current!=null)api.scorecard(current.id)else Scorecard(emptyList(),emptyList()))};if(generation==expected){match=bundle.first;deliveries=bundle.second;scorecard=bundle.third;onUpdated(bundle.first);val current=bundle.first.innings.lastOrNull();val waiting=bundle.first.status=="LIVE"&&current!=null&&current.legalBalls>0&&current.legalBalls%6==0&&bundle.second.firstOrNull()?.bowlerId==current.bowlerId;if(waiting){mustChangeBowler=true;showBowler=true}};return bundle.first}
    fun perform(onSuccess:(CricketMatch)->Unit={},action:()->Unit){generation++;val expected=generation;busy=true;error=null;scope.launch{runCatching{withContext(Dispatchers.IO){action()};snapshot(expected)}.onSuccess{fresh->syncedTick++;onSuccess(fresh)}.onFailure{error=it.message};busy=false}}
    LaunchedEffect(match.id){lineup=runCatching{withContext(Dispatchers.IO){api.lineup(match.id)}}.getOrDefault(emptyList());while(true){val expected=generation;runCatching{snapshot(expected)}.onFailure{error=it.message};delay(2000)}}
    LaunchedEffect(syncedTick){if(syncedTick>0){showSynced=true;delay(1800);showSynced=false}}
    val inn=match.innings.lastOrNull();val battingName=if(inn?.battingTeamId==match.teamAId)match.teamAName else match.teamBName;val oversText=inn?.let{"${it.legalBalls/6}.${it.legalBalls%6}"}?:"0.0";val target=if(match.currentInnings==2)(match.innings.firstOrNull()?.runs?:0)+1 else 0;val locked=busy||match.status=="PAUSED"||mustChangeBowler
    val battingPlayers=if(inn==null)emptyList()else lineup.filter{it.teamId==inn.battingTeamId};val bowlingPlayers=if(inn==null)emptyList()else lineup.filter{it.teamId==inn.bowlingTeamId};val dismissed=deliveries.filter{it.wicket}.map{it.dismissedPlayerId}.toSet()
    fun recordDelivery(draft:DeliveryDraft){val before=inn?.legalBalls?:0;if(inn!=null)perform(onSuccess={fresh->val current=fresh.innings.lastOrNull();if(fresh.status=="LIVE"&&current!=null&&current.legalBalls>before&&current.legalBalls%6==0){mustChangeBowler=true;showBowler=true}}){api.addDelivery(inn.id,draft)}}
    val currentOverBalls=remember(deliveries){val chronological=deliveries.sortedBy{it.sequence};val afterLastBoundary=chronological.fold(emptyList<Delivery>() to 0){acc,ball->val next=acc.first+ball;val legal=acc.second+(if(ball.legal)1 else 0);if(legal==6)emptyList<Delivery>() to 0 else next to legal};afterLastBoundary.first}
    val canReplaceStriker=inn!=null&&inn.legalBalls%6==0&&currentOverBalls.isEmpty()&&!mustChangeBowler
    if(showBowler&&inn!=null)AlertDialog(onDismissRequest={if(!mustChangeBowler)showBowler=false},title={Text(if(mustChangeBowler)"Over complete — select next bowler" else "Change bowler")},text={Column{if(mustChangeBowler)Text("A new bowler is required before scoring can continue.",color=Muted,modifier=Modifier.padding(bottom=8.dp));bowlingPlayers.filter{it.id!=inn.bowlerId}.forEach{p->TextButton(onClick={perform(onSuccess={mustChangeBowler=false;showBowler=false}){api.updateParticipants(inn.id,bowlerId=p.id)}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(p.name)}}}},confirmButton={if(!mustChangeBowler)TextButton(onClick={showBowler=false}){Text("CLOSE")}})
    if(showReplaceStriker&&inn!=null)AlertDialog(onDismissRequest={showReplaceStriker=false},title={Text("Replace striker")},text={Column{Text("Replacement is allowed only before the first ball of the over.",color=Muted,modifier=Modifier.padding(bottom=8.dp));battingPlayers.filter{p->p.id!=inn.strikerId&&p.id!=inn.nonStrikerId&&!dismissed.contains(p.id)&&scorecard.batters.none{it.id==p.id}}.forEach{p->TextButton(onClick={perform(onSuccess={showReplaceStriker=false}){api.updateParticipants(inn.id,strikerId=p.id)}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(p.name)}}}},confirmButton={},dismissButton={TextButton(onClick={showReplaceStriker=false}){Text("CANCEL")}})
    if(showWicket&&inn!=null)AlertDialog(onDismissRequest={showWicket=false},title={Text("Record wicket")},text={LazyColumn(Modifier.heightIn(max=560.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{DropdownChoice("Dismissal type",listOf("BOWLED","CAUGHT","LBW","RUN_OUT","STUMPED","HIT_WICKET","RETIRED_OUT"),dismissal){dismissal=it;fielderId="";assistantFielderId=""}};item{DropdownPlayerSelector("Dismissed player",listOfNotNull(battingPlayers.find{it.id==inn.strikerId},battingPlayers.find{it.id==inn.nonStrikerId}),dismissedId){dismissedId=it}};if(dismissal=="CAUGHT")item{DropdownPlayerSelector("Caught by *",bowlingPlayers,fielderId){fielderId=it}};if(dismissal=="RUN_OUT"){item{DropdownPlayerSelector("Primary fielder *",bowlingPlayers,fielderId){fielderId=it;if(assistantFielderId==it)assistantFielderId=""}};item{DropdownPlayerSelector("Assisting fielder (optional)",bowlingPlayers.filterNot{it.id==fielderId},assistantFielderId){assistantFielderId=it}}};item{DropdownPlayerSelector("Next batter",battingPlayers.filter{it.id!=inn.strikerId&&it.id!=inn.nonStrikerId&&!dismissed.contains(it.id)},nextBatterId){nextBatterId=it}}}},confirmButton={Button(onClick={showWicket=false;recordDelivery(DeliveryDraft(isWicket=true,dismissalType=dismissal,dismissedPlayerId=dismissedId.ifBlank{inn.strikerId},nextBatterId=nextBatterId.ifBlank{null},fielderId=fielderId.ifBlank{null},assistantFielderId=assistantFielderId.ifBlank{null}))},enabled=dismissedId.isNotBlank()&&((dismissal!="CAUGHT"&&dismissal!="RUN_OUT")||fielderId.isNotBlank())){Text("CONFIRM")}},dismissButton={TextButton(onClick={showWicket=false}){Text("CANCEL")}})
    if(extraTypeDialog!=null)AlertDialog(onDismissRequest={extraTypeDialog=null},title={Text(if(extraTypeDialog=="WIDE")"Wide + runs" else "No-ball + runs")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Select runs in addition to the free run");(0..6).chunked(4).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{runs->OutlinedButton(onClick={val type=extraTypeDialog!!;extraTypeDialog=null;if(type=="NO_BALL")recordDelivery(DeliveryDraft(batterRuns=runs,extraRuns=1,extraType=type))else recordDelivery(DeliveryDraft(extraRuns=runs+1,extraType=type))},modifier=Modifier.weight(1f)){Text(runs.toString())}}}}}},confirmButton={},dismissButton={TextButton(onClick={extraTypeDialog=null}){Text("CANCEL")}})
    Box(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize()){
        AppHeader("Live scoring",onBack=onBack)
        when{
            inn==null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            match.status=="COMPLETE"->EmptyState("Match complete",match.result,"BACK TO TOURNAMENT",onBack)
            match.status=="INNINGS_BREAK"->InningsBreakPanel(match,lineup,busy,error){s,n,b->perform{api.endInnings(match.id,s,n,b)}}
            else->LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(0.dp)){
                item{ScoringHero(battingName,inn,oversText,match.overs,target,battingPlayers.size,scorecard)}
                item{BowlerStrip(inn,scorecard,currentOverBalls){showBowler=true}}
                item{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
                    if(canReplaceStriker)OutlinedButton(onClick={showReplaceStriker=true},modifier=Modifier.align(Alignment.End)){Text("REPLACE STRIKER",fontSize=11.sp)}
                    Text("Runs",fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){(0..6).forEach{r->ScoreButton(r.toString(),locked){recordDelivery(DeliveryDraft(batterRuns=r))}}}
                    Text("Extras & wicket",fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){ScoreButton("WD+",locked){extraTypeDialog="WIDE"};ScoreButton("NB+",locked){extraTypeDialog="NO_BALL"};ScoreButton("B",locked){recordDelivery(DeliveryDraft(extraRuns=1,extraType="BYE"))};ScoreButton("LB",locked){recordDelivery(DeliveryDraft(extraRuns=1,extraType="LEG_BYE"))};ScoreButton("W",locked){dismissedId=inn.strikerId;nextBatterId=battingPlayers.firstOrNull{it.id!=inn.strikerId&&it.id!=inn.nonStrikerId&&!dismissed.contains(it.id)}?.id?:"";showWicket=true}}
                    ScorecardPanel(scorecard)
                    if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton(onClick={perform{api.undoDelivery(inn.id)}},enabled=!locked,modifier=Modifier.weight(1f)){Text("UNDO BALL")};Button(onClick={perform{api.endInnings(match.id)}},enabled=!locked,colors=ButtonDefaults.buttonColors(containerColor=AppRed),modifier=Modifier.weight(1f)){Text(if(match.currentInnings==1)"END INNINGS" else "END MATCH")}}
                }}
            }
        }
    };if(showSynced)Surface(color=Color(0xFF454545),shape=RoundedCornerShape(5.dp),shadowElevation=8.dp,modifier=Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=22.dp)){Row(Modifier.padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Text("✓",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.width(10.dp));Text("Synced",color=Color.White,fontSize=17.sp)}} }
}

@Composable private fun ScoringHero(teamName:String,inn:Innings,oversText:String,maxOvers:Int,target:Int,battingPlayerCount:Int,scorecard:Scorecard){
    val striker=scorecard.batters.find{it.id==inn.strikerId};val nonStriker=scorecard.batters.find{it.id==inn.nonStrikerId}
    Column(Modifier.fillMaxWidth().background(Color(0xFF15191C))){
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(teamName,color=Color(0xFFCED4D8),fontSize=18.sp);Row(verticalAlignment=Alignment.Bottom){Text("${inn.runs}/${inn.wickets}",color=Color.White,fontSize=52.sp,fontWeight=FontWeight.Light);Text("  ($oversText/$maxOvers)",color=Color.White,fontSize=22.sp,modifier=Modifier.padding(bottom=8.dp))};if(target>0){val needed=(target-inn.runs).coerceAtLeast(0);val balls=(maxOvers*6-inn.legalBalls).coerceAtLeast(0);val wickets=(battingPlayerCount-1-inn.wickets).coerceAtLeast(0);Text("$needed runs needed in $balls balls, wickets in hand $wickets",color=Color(0xFF4ED5CF),fontSize=14.sp,fontWeight=FontWeight.SemiBold)}}
        HorizontalDivider(color=Color(0xFF41464A));Row(Modifier.fillMaxWidth()){
            BatterHighlight(inn.strikerName,striker,true,Modifier.weight(1f));Box(Modifier.width(1.dp).height(78.dp).background(Color(0xFF41464A)));BatterHighlight(inn.nonStrikerName,nonStriker,false,Modifier.weight(1f))
        }
    }
}

@Composable private fun BatterHighlight(name:String,stat:BatterStat?,striker:Boolean,modifier:Modifier=Modifier){val shownName=if(name.length>15)name.trim().substringBefore(" ") else name;Row(modifier.background(if(striker)Color(0xFF123A3A) else Color.Transparent).padding(horizontal=12.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(34.dp).clip(CircleShape).background(if(striker)Color(0xFF35C9C2) else Color(0xFF535B60)),contentAlignment=Alignment.Center){Text("🏏",fontSize=19.sp)};Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(shownName+(if(striker)" *" else ""),color=if(striker)Color(0xFF4ED5CF) else Color.White,fontSize=14.sp,fontWeight=if(striker)FontWeight.Bold else FontWeight.Normal,maxLines=1);Text("${stat?.runs?:0}(${stat?.balls?:0})",color=Color.White,fontSize=14.sp)}}}

@Composable private fun BowlerStrip(inn:Innings,scorecard:Scorecard,deliveries:List<Delivery>,onChange:()->Unit){val bowler=scorecard.bowlers.find{it.id==inn.bowlerId};Surface(color=Color(0xFF303234)){Column(Modifier.fillMaxWidth().padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("◉",color=Color.White,fontSize=25.sp);Spacer(Modifier.width(10.dp));Text(inn.bowlerName,color=Color.White,fontSize=18.sp,modifier=Modifier.weight(1f));Text("${bowler?.legalBalls?.div(6)?:0}.${bowler?.legalBalls?.rem(6)?:0}-${bowler?.runs?:0}-${bowler?.wickets?:0}",color=Color.White);TextButton(onClick=onChange){Text("CHANGE",color=Color(0xFF4ED5CF))}};if(deliveries.isNotEmpty())LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){items(deliveries.size){index->BallBadge(deliveries[index])}}}}}

@Composable private fun InningsBreakPanel(match:CricketMatch,lineup:List<Player>,busy:Boolean,error:String?,onStart:(String,String,String)->Unit){val first=match.innings.first();val bats=lineup.filter{it.teamId==first.bowlingTeamId};val bowls=lineup.filter{it.teamId==first.battingTeamId};var s by remember{mutableStateOf(bats.getOrNull(0)?.id?:"")};var n by remember{mutableStateOf(bats.getOrNull(1)?.id?:"")};var b by remember{mutableStateOf(bowls.getOrNull(0)?.id?:"")};LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{Text("First innings complete",fontSize=25.sp,fontWeight=FontWeight.Bold);Text("Target: ${first.runs+1}",color=ActionTeal,fontSize=20.sp)};item{PlayerSelector("Striker",bats,s){s=it}};item{PlayerSelector("Non-striker",bats.filterNot{it.id==s},n){n=it}};item{PlayerSelector("Opening bowler",bowls,b){b=it}};if(error!=null)item{Text(error,color=MaterialTheme.colorScheme.error)};item{Button(onClick={onStart(s,n,b)},enabled=!busy&&s.isNotBlank()&&n.isNotBlank()&&b.isNotBlank(),modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=ActionTeal)){Text("START CHASE")}}}}
@Composable private fun BallBadge(ball:Delivery){val label=when{ball.wicket->"W";ball.extraType=="WIDE"->"Wd${ball.extraRuns}";ball.extraType=="NO_BALL"->"Nb${ball.extraRuns}";ball.extraType=="BYE"->"B${ball.extraRuns}";ball.extraType=="LEG_BYE"->"Lb${ball.extraRuns}";else->ball.batterRuns.toString()};Box(Modifier.size(42.dp).clip(CircleShape).background(if(ball.wicket)AppRed else Color(0xFFE8ECEF)),contentAlignment=Alignment.Center){Text(label,color=if(ball.wicket)Color.White else Ink,fontSize=11.sp,fontWeight=FontWeight.Bold,maxLines=1)}}
@Composable private fun ScorecardPanel(card:Scorecard){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Live scorecard",fontWeight=FontWeight.Bold);card.batters.forEach{s->Row(Modifier.fillMaxWidth()){Text(s.name+(if(s.dismissal.isBlank())" *" else ""),modifier=Modifier.weight(1f));Text("${s.runs} (${s.balls})  4s ${s.fours}  6s ${s.sixes}")}};if(card.bowlers.isNotEmpty())HorizontalDivider();card.bowlers.forEach{s->Row(Modifier.fillMaxWidth()){Text(s.name,modifier=Modifier.weight(1f));Text("${s.legalBalls/6}.${s.legalBalls%6}  ${s.runs}/${s.wickets}")}}}}

private fun dismissalText(stat:BatterStat):String=when(stat.dismissal){"CAUGHT"->"c ${stat.fielderName} b ${stat.dismissalBowlerName}";"RUN_OUT"->"run out (${listOf(stat.fielderName,stat.assistantFielderName).filter{it.isNotBlank()}.joinToString(" / ")})";"BOWLED"->"b ${stat.dismissalBowlerName}";"LBW"->"lbw b ${stat.dismissalBowlerName}";"STUMPED"->"st ${stat.fielderName} b ${stat.dismissalBowlerName}";""->"not out";else->stat.dismissal.lowercase().replace('_',' ')}

@Composable
private fun LiveScoreScreen(api:CloudApi,initial:CricketMatch,onBack:()->Unit){
    var match by remember{mutableStateOf(initial)}
    var lineup by remember{mutableStateOf(emptyList<Player>())}
    var cards by remember{mutableStateOf<Map<String,Scorecard>>(emptyMap())}
    var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(initial.id){
        lineup=runCatching{withContext(Dispatchers.IO){api.lineup(initial.id)}}.getOrDefault(emptyList())
        while(true){
            runCatching{withContext(Dispatchers.IO){val fresh=api.match(initial.id);fresh to fresh.innings.associate{it.id to api.scorecard(it.id)}}}
                .onSuccess{match=it.first;cards=it.second;error=null}.onFailure{error=it.message}
            delay(2000)
        }
    }
    Column(Modifier.fillMaxSize()){
        AppHeader("Live scorecard",onBack=onBack)
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}
            items(match.innings,key={it.id}){inn->
                val card=cards[inn.id]?:Scorecard(emptyList(),emptyList())
                val teamName=if(inn.battingTeamId==match.teamAId)match.teamAName else match.teamBName
                val teamPlayers=lineup.filter{it.teamId==inn.battingTeamId}
                Card{Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("$teamName — Innings ${inn.number}",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Text("${inn.runs}/${inn.wickets}  (${inn.legalBalls/6}.${inn.legalBalls%6} overs)",color=ActionTeal,fontSize=18.sp)
                    HorizontalDivider();Text("BATTING",fontWeight=FontWeight.Bold)
                    Row{Text("Batter",Modifier.weight(1f));Text("R   B   4s   6s")}
                    teamPlayers.forEach{player->
                        val stat=card.batters.find{it.id==player.id}
                        if(stat!=null)Column{Row{Column(Modifier.weight(1f)){Text(player.name,fontWeight=if(player.id==inn.strikerId)FontWeight.Bold else FontWeight.Normal);Text(dismissalText(stat),fontSize=11.sp,color=Muted)};Text("${stat.runs}   ${stat.balls}   ${stat.fours}   ${stat.sixes}")};HorizontalDivider()}
                        else Row{Text(player.name,Modifier.weight(1f),color=Muted);Text("DNB",color=Muted)}
                    }
                    Spacer(Modifier.height(6.dp));Text("BOWLING",fontWeight=FontWeight.Bold)
                    Row{Text("Bowler",Modifier.weight(1f));Text("O    M    R    W    Econ")}
                    card.bowlers.forEach{bowler->val economy=if(bowler.legalBalls==0)"0.00" else String.format("%.2f",bowler.runs*6.0/bowler.legalBalls);Row{Text(bowler.name,Modifier.weight(1f));Text("${bowler.legalBalls/6}.${bowler.legalBalls%6}    ${bowler.maidens}    ${bowler.runs}    ${bowler.wickets}    $economy")}}
                }}
            }
        }
    }
}

@Composable
private fun ScoringScreenLegacy(api:CloudApi, initial:CricketMatch,onBack:()->Unit,onUpdated:(CricketMatch)->Unit){
    val scope=rememberCoroutineScope();var match by remember{mutableStateOf(initial)};var lineup by remember{mutableStateOf(emptyList<Player>())};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var showBowler by remember{mutableStateOf(false)}
    fun reload(){scope.launch{runCatching{withContext(Dispatchers.IO){api.match(match.id)}}.onSuccess{match=it;onUpdated(it)}.onFailure{error=it.message};busy=false}}
    LaunchedEffect(match.id){lineup=runCatching{withContext(Dispatchers.IO){api.lineup(match.id)}}.getOrDefault(emptyList());reload()}
    val inn=match.innings.lastOrNull();val battingName=if(inn?.battingTeamId==match.teamAId)match.teamAName else match.teamBName;val oversText=inn?.let{"${it.legalBalls/6}.${it.legalBalls%6}"}?:"0.0";val target=if(match.currentInnings==2)(match.innings.firstOrNull()?.runs?:0)+1 else 0;val controlsLocked=busy||match.status=="PAUSED"
    fun ball(d:DeliveryDraft){if(inn==null)return;busy=true;scope.launch{runCatching{withContext(Dispatchers.IO){api.addDelivery(inn.id,d)}}.onSuccess{reload()}.onFailure{error=it.message;busy=false}}}
    if(showBowler&&inn!=null)AlertDialog(onDismissRequest={showBowler=false},title={Text("Change bowler")},text={Column{lineup.filter{it.teamId==inn.bowlingTeamId}.forEach{p->TextButton(onClick={showBowler=false;busy=true;scope.launch{runCatching{withContext(Dispatchers.IO){api.updateParticipants(inn.id,bowlerId=p.id)}}.onSuccess{reload()}.onFailure{error=it.message;busy=false}}},modifier=Modifier.fillMaxWidth()){Text(p.name)}}}},confirmButton={TextButton(onClick={showBowler=false}){Text("CLOSE")}})
    Column(Modifier.fillMaxSize()){AppHeader("Live scoring",onBack=onBack);if(inn==null)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}else if(match.status=="COMPLETE")EmptyState("Match complete",match.result,"BACK TO TOURNAMENT",onBack)else{Column(Modifier.weight(1f).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF173E5B)),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){Text(battingName,color=Color.White,fontSize=20.sp);Text("${inn.runs}/${inn.wickets}",color=Color.White,fontSize=48.sp,fontWeight=FontWeight.Black);Text("Overs $oversText / ${match.overs}"+(if(target>0)"  •  Target $target" else ""),color=Color(0xFFCEE7F5))}};Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${inn.strikerName} *",modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold);Text(inn.nonStrikerName,modifier=Modifier.weight(1f));TextButton(onClick={showBowler=true}){Text("${inn.bowlerName} ▾")}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={busy=true;scope.launch{runCatching{withContext(Dispatchers.IO){api.setMatchStatus(match.id,if(match.status=="PAUSED")"LIVE" else "PAUSED")}}.onSuccess{match=it;onUpdated(it);busy=false}.onFailure{error=it.message;busy=false}}}){Text(if(match.status=="PAUSED")"RESUME" else "PAUSE")};if(match.status=="PAUSED")Text("Scoring paused",color=AppRed,modifier=Modifier.align(Alignment.CenterVertically))};Text("Runs",fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){(0..6).forEach{r->ScoreButton(r.toString(),controlsLocked){ball(DeliveryDraft(batterRuns=r))}}};Text("Extras & wicket",fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){ScoreButton("WD",controlsLocked){ball(DeliveryDraft(extraRuns=1,extraType="WIDE"))};ScoreButton("NB",controlsLocked){ball(DeliveryDraft(extraRuns=1,extraType="NO_BALL"))};ScoreButton("B",controlsLocked){ball(DeliveryDraft(extraRuns=1,extraType="BYE"))};ScoreButton("LB",controlsLocked){ball(DeliveryDraft(extraRuns=1,extraType="LEG_BYE"))};ScoreButton("W",controlsLocked){val next=lineup.firstOrNull{it.teamId==inn.battingTeamId&&it.id!=inn.strikerId&&it.id!=inn.nonStrikerId};ball(DeliveryDraft(isWicket=true,dismissalType="OUT",dismissedPlayerId=inn.strikerId,nextBatterId=next?.id))}};if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error);Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton(onClick={busy=true;scope.launch{runCatching{withContext(Dispatchers.IO){api.undoDelivery(inn.id)}}.onSuccess{reload()}.onFailure{error=it.message;busy=false}}},enabled=!controlsLocked,modifier=Modifier.weight(1f)){Text("UNDO BALL")};Button(onClick={val nextBatTeam=inn.bowlingTeamId;val bats=lineup.filter{it.teamId==nextBatTeam};val bowls=lineup.filter{it.teamId==inn.battingTeamId};busy=true;scope.launch{runCatching{withContext(Dispatchers.IO){api.endInnings(match.id,bats.getOrNull(0)?.id,bats.getOrNull(1)?.id,bowls.getOrNull(0)?.id)}}.onSuccess{match=it;onUpdated(it);busy=false}.onFailure{error=it.message;busy=false}}},enabled=!controlsLocked,colors=ButtonDefaults.buttonColors(containerColor=AppRed),modifier=Modifier.weight(1f)){Text(if(match.currentInnings==1)"END INNINGS" else "END MATCH")}}}}}
}
@Composable private fun ScoreButton(text:String,busy:Boolean,onClick:()->Unit){OutlinedButton(onClick=onClick,enabled=!busy,contentPadding=PaddingValues(0.dp),modifier=Modifier.sizeIn(minWidth=42.dp,minHeight=44.dp)){Text(text,fontWeight=FontWeight.Bold)}}

@Composable private fun FormField(label: String, value: String, onValue: (String) -> Unit) { OutlinedTextField(value, onValue, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ActionTeal, focusedLabelColor = ActionTeal)) }
@Composable private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().border(1.dp, Color(0xFFE3E3E3), RoundedCornerShape(5.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, modifier = Modifier.weight(1f)); Switch(checked, onChecked, colors = SwitchDefaults.colors(checkedTrackColor = ActionTeal)) } }
@Composable private fun Avatar(name: String, size: Int = 72) { Box(Modifier.size(size.dp).clip(CircleShape).background(ActionTeal), contentAlignment = Alignment.Center) { Text(name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString(""), color = Color.White, fontSize = (size / 3).sp, fontWeight = FontWeight.Bold) } }
@Composable private fun RoleBadge(text: String) { Text(text.lowercase().replaceFirstChar { it.uppercase() }, color = ActionTeal, fontSize = 11.sp, modifier = Modifier.border(1.dp, ActionTeal, RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) }
@Composable private fun UploadPlaceholders() { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Avatar("Banner", 90); Text("Add banner") }; Column(horizontalAlignment = Alignment.CenterHorizontally) { Avatar("Logo", 90); Text("Add logo") } } }
@Composable private fun SectionChoice(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); ChoiceRow(options, selected, onSelect) } }
@Composable private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) { Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { options.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { option -> Surface(color = if (option == selected) ActionTeal else Color(0xFFEDEDF0), shape = RoundedCornerShape(24.dp), modifier = Modifier.clickable { onSelect(option) }) { Text(option.replace('_',' '), color = if (option == selected) Color.White else Ink, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) } } } } } }
@Composable private fun EmptyState(title: String, message: String, action: String, onAction: () -> Unit) { Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(10.dp)); Text(message, color = Muted); Spacer(Modifier.height(28.dp)); Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = ActionTeal), modifier = Modifier.fillMaxWidth()) { Text(action) } } }
@Composable private fun ErrorCard(message: String, retry: () -> Unit) { EmptyState("Could not load", message, "RETRY", retry) }
