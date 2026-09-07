package club.nmtcc.cricket

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Tournament(val id: String, val name: String, val city: String = "", val ground: String = "", val startDate: String = "", val endDate: String = "", val status: String = "ONGOING", val category: String = "OPEN", val ballType: String = "TENNIS", val pitchType: String = "", val matchType: String = "LIMITED_OVERS")
data class Team(val id: String, val name: String, val shortName: String = "", val city: String = "", val captainName: String = "", val captainPhone: String = "", val logoUrl: String? = null)
data class Player(val id: String, val name: String, val role: String = "PLAYER", val teamId: String = "", val isAdmin: Boolean = false, val isCaptain: Boolean = false, val isWicketKeeper: Boolean = false, val photoUrl: String? = null)
data class TournamentDraft(val name: String, val city: String, val ground: String, val organiserName: String, val organiserPhone: String, val organiserEmail: String, val startDate: String, val endDate: String, val category: String, val ballType: String, val pitchType: String, val matchType: String)
data class TeamDraft(val name: String, val city: String, val captainName: String, val captainPhone: String)
data class PlayerDraft(val name: String, val role: String, val isAdmin: Boolean, val isCaptain: Boolean, val isWicketKeeper: Boolean)
data class CricketMatch(val id: String, val tournamentId: String, val roundName: String, val teamAId: String, val teamAName: String, val teamBId: String, val teamBName: String, val ground: String = "", val overs: Int = 20, val status: String = "SCHEDULED", val currentInnings: Int = 0, val result: String = "", val innings: List<Innings> = emptyList())
data class Innings(val id: String, val number: Int, val battingTeamId: String, val bowlingTeamId: String, val runs: Int, val wickets: Int, val legalBalls: Int, val status: String, val strikerId: String = "", val nonStrikerId: String = "", val bowlerId: String = "", val strikerName: String = "", val nonStrikerName: String = "", val bowlerName: String = "")
data class MatchDraft(val tournamentId: String, val roundName: String, val teamAId: String, val teamBId: String, val scheduledAt: String, val ground: String, val overs: Int)
data class DeliveryDraft(val batterRuns: Int = 0, val extraRuns: Int = 0, val extraType: String = "NONE", val isWicket: Boolean = false, val dismissalType: String? = null, val dismissedPlayerId: String? = null, val nextBatterId: String? = null)
data class Delivery(val id:String,val sequence:Int,val batterRuns:Int,val extraRuns:Int,val extraType:String,val wicket:Boolean,val dismissalType:String,val dismissedPlayerId:String,val strikerName:String,val bowlerId:String,val bowlerName:String)
data class BatterStat(val id:String,val name:String,val runs:Int,val balls:Int,val fours:Int,val sixes:Int,val dismissal:String)
data class BowlerStat(val id:String,val name:String,val legalBalls:Int,val runs:Int,val wickets:Int)
data class Scorecard(val batters:List<BatterStat>,val bowlers:List<BowlerStat>)

class CloudApi(private val baseUrl: String = BuildConfig.API_BASE_URL, private val writeToken: String = BuildConfig.API_WRITE_TOKEN) {
    private fun request(path: String, method: String = "GET", payload: JSONObject? = null): String {
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            if (writeToken.isNotBlank()) connection.setRequestProperty("x-admin-token", writeToken)
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("error") }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "Request failed ($status)")
            }
            response
        } finally { connection.disconnect() }
    }

    fun health(): Boolean = JSONObject(request("/api/health")).optString("status") == "UP"
    fun adminAccess(): Boolean = runCatching { JSONObject(request("/api/admin/status")).has("counts") }.getOrDefault(false)
    fun tournaments(): List<Tournament> = JSONArray(request("/api/tournaments")).objects().map { it.toTournament() }
    fun createTournament(draft: TournamentDraft): Tournament {
        val json = JSONObject().put("name", draft.name).put("city", draft.city).put("ground", draft.ground)
            .put("organiserName", draft.organiserName).put("organiserPhone", draft.organiserPhone).put("organiserEmail", draft.organiserEmail)
            .put("startDate", draft.startDate).put("endDate", draft.endDate).put("category", draft.category)
            .put("ballType", draft.ballType).put("pitchType", draft.pitchType).put("matchType", draft.matchType)
            .put("format", "ROUND_ROBIN").put("oversPerInnings", 20)
        return JSONObject(request("/api/tournaments", "POST", json)).toTournament()
    }
    fun tournamentTeams(tournamentId: String): List<Team> = JSONArray(request("/api/tournaments/$tournamentId/teams")).objects().map { it.toTeam() }
    fun createTeam(tournamentId: String, draft: TeamDraft): Team {
        val json = JSONObject().put("tournamentId", tournamentId).put("name", draft.name).put("city", draft.city).put("captainName", draft.captainName).put("captainPhone", draft.captainPhone)
        return JSONObject(request("/api/teams", "POST", json)).toTeam()
    }
    fun updateTeam(teamId: String, draft: TeamDraft): Team {
        val json = JSONObject().put("name", draft.name).put("city", draft.city).put("captainName", draft.captainName).put("captainPhone", draft.captainPhone)
        return JSONObject(request("/api/teams/$teamId", "PUT", json)).toTeam()
    }
    fun players(): List<Player> = JSONArray(request("/api/players")).objects().map { it.toPlayer() }
    fun teamPlayers(teamId: String): List<Player> = JSONArray(request("/api/teams/$teamId/players")).objects().map { it.toPlayer() }
    fun createPlayer(teamId: String, draft: PlayerDraft): Player {
        val json = JSONObject().put("teamId", teamId).put("name", draft.name).put("role", draft.role).put("isAdmin", draft.isAdmin).put("isCaptain", draft.isCaptain).put("isWicketKeeper", draft.isWicketKeeper)
        return JSONObject(request("/api/players", "POST", json)).toPlayer()
    }
    fun addPlayerToTeam(teamId: String, playerId: String) { request("/api/teams/$teamId/players/$playerId", "PUT", JSONObject()) }
    fun removePlayerFromTeam(teamId: String, playerId: String) { request("/api/teams/$teamId/players/$playerId", "DELETE", JSONObject()) }
    fun tournamentMatches(tournamentId: String): List<CricketMatch> = JSONArray(request("/api/tournaments/$tournamentId/matches")).objects().map { it.toMatch() }
    fun match(matchId: String): CricketMatch = JSONObject(request("/api/matches/$matchId")).toMatch()
    fun createMatch(draft: MatchDraft): CricketMatch = JSONObject(request("/api/matches", "POST", JSONObject().put("tournamentId",draft.tournamentId).put("roundName",draft.roundName).put("teamAId",draft.teamAId).put("teamBId",draft.teamBId).put("scheduledAt",draft.scheduledAt).put("ground",draft.ground).put("oversPerInnings",draft.overs))).toMatch()
    fun saveLineup(matchId: String, teamId: String, playerIds: Set<String>) { request("/api/matches/$matchId/lineup/$teamId", "PUT", JSONObject().put("playerIds", JSONArray(playerIds.toList()))) }
    fun lineup(matchId: String): List<Player> = JSONArray(request("/api/matches/$matchId/lineup")).objects().map { it.toPlayer() }
    fun recordToss(matchId: String, winnerId: String, decision: String, strikerId: String, nonStrikerId: String, bowlerId: String): CricketMatch = JSONObject(request("/api/matches/$matchId/toss", "PUT", JSONObject().put("tossWinnerId",winnerId).put("decision",decision).put("strikerId",strikerId).put("nonStrikerId",nonStrikerId).put("bowlerId",bowlerId))).toMatch()
    fun updateParticipants(inningsId: String, strikerId: String? = null, nonStrikerId: String? = null, bowlerId: String? = null) { request("/api/innings/$inningsId/participants", "PUT", JSONObject().putOpt("strikerId",strikerId).putOpt("nonStrikerId",nonStrikerId).putOpt("bowlerId",bowlerId)) }
    fun addDelivery(inningsId: String, draft: DeliveryDraft) { request("/api/innings/$inningsId/deliveries", "POST", JSONObject().put("batterRuns",draft.batterRuns).put("extraRuns",draft.extraRuns).put("extraType",draft.extraType).put("isWicket",draft.isWicket).putOpt("dismissalType",draft.dismissalType).putOpt("dismissedPlayerId",draft.dismissedPlayerId).putOpt("nextBatterId",draft.nextBatterId)) }
    fun undoDelivery(inningsId: String) { request("/api/innings/$inningsId/undo", "POST", JSONObject()) }
    fun deliveries(inningsId:String):List<Delivery> = JSONArray(request("/api/innings/$inningsId/deliveries")).objects().map{it.toDelivery()}
    fun scorecard(inningsId:String):Scorecard { val json=JSONObject(request("/api/innings/$inningsId/scorecard"));return Scorecard(json.optJSONArray("batters")?.objects()?.map{it.toBatterStat()}?:emptyList(),json.optJSONArray("bowlers")?.objects()?.map{it.toBowlerStat()}?:emptyList()) }
    fun endInnings(matchId: String, strikerId: String? = null, nonStrikerId: String? = null, bowlerId: String? = null): CricketMatch = JSONObject(request("/api/matches/$matchId/end-innings", "POST", JSONObject().putOpt("strikerId",strikerId).putOpt("nonStrikerId",nonStrikerId).putOpt("bowlerId",bowlerId))).toMatch()
    fun setMatchStatus(matchId: String, status: String): CricketMatch = JSONObject(request("/api/matches/$matchId/status", "PUT", JSONObject().put("status",status))).toMatch()
}

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.string(name: String) = optString(name, "")
private fun JSONObject.toTournament() = Tournament(id = string("id"), name = string("name"), city = string("city"), ground = string("ground"), startDate = string("start_date").ifBlank { string("startDate") }, endDate = string("end_date").ifBlank { string("endDate") }, status = string("status").ifBlank { "ONGOING" }, category = string("category").ifBlank { "OPEN" }, ballType = string("ball_type").ifBlank { string("ballType") }.ifBlank { "TENNIS" }, pitchType = string("pitch_type").ifBlank { string("pitchType") }, matchType = string("match_type").ifBlank { string("matchType") })
private fun JSONObject.toTeam() = Team(id = string("id"), name = string("name"), shortName = string("short_name").ifBlank { string("shortName") }, city = string("city"), captainName = string("captain_name").ifBlank { string("captainName") }, captainPhone = string("captain_phone").ifBlank { string("captainPhone") }, logoUrl = optString("logo_url").takeIf { it.isNotBlank() })
private fun JSONObject.toPlayer() = Player(id = string("id"), name = string("name"), role = string("member_role").ifBlank { string("role") }.ifBlank { "PLAYER" }, teamId = string("team_id"), isAdmin = optInt("is_admin") == 1, isCaptain = optInt("is_captain") == 1, isWicketKeeper = optInt("is_wicket_keeper") == 1, photoUrl = optString("photo_url").takeIf { it.isNotBlank() })
private fun JSONObject.toInnings() = Innings(id=string("id"),number=optInt("innings_number"),battingTeamId=string("batting_team_id"),bowlingTeamId=string("bowling_team_id"),runs=optInt("runs"),wickets=optInt("wickets"),legalBalls=optInt("legal_balls"),status=string("status"),strikerId=string("striker_id"),nonStrikerId=string("non_striker_id"),bowlerId=string("bowler_id"),strikerName=string("striker_name"),nonStrikerName=string("non_striker_name"),bowlerName=string("bowler_name"))
private fun JSONObject.toMatch(): CricketMatch { val list=optJSONArray("innings")?.objects()?.map { it.toInnings() } ?: emptyList(); return CricketMatch(id=string("id"),tournamentId=string("tournament_id").ifBlank{string("tournamentId")},roundName=string("round_name").ifBlank{string("roundName")},teamAId=string("team_a_id").ifBlank{string("teamAId")},teamAName=string("team_a_name").ifBlank{string("teamAName")},teamBId=string("team_b_id").ifBlank{string("teamBId")},teamBName=string("team_b_name").ifBlank{string("teamBName")},ground=string("ground"),overs=optInt("overs_per_innings",20),status=string("status").ifBlank{"SCHEDULED"},currentInnings=optInt("current_innings"),result=string("result_text"),innings=list) }
private fun JSONObject.toDelivery()=Delivery(id=string("id"),sequence=optInt("sequence_number"),batterRuns=optInt("batter_runs"),extraRuns=optInt("extra_runs"),extraType=string("extra_type"),wicket=optInt("is_wicket")==1,dismissalType=string("dismissal_type"),dismissedPlayerId=string("dismissed_player_id"),strikerName=string("striker_name"),bowlerId=string("bowler_id"),bowlerName=string("bowler_name"))
private fun JSONObject.toBatterStat()=BatterStat(id=string("id"),name=string("name"),runs=optInt("runs"),balls=optInt("balls"),fours=optInt("fours"),sixes=optInt("sixes"),dismissal=string("dismissal"))
private fun JSONObject.toBowlerStat()=BowlerStat(id=string("id"),name=string("name"),legalBalls=optInt("legal_balls"),runs=optInt("runs"),wickets=optInt("wickets"))
