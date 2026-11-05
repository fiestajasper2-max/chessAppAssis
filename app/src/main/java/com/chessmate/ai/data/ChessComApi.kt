package com.chessmate.ai.data
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
private data class ArchiveResponse(val archives: List<String> = emptyList())
private data class GamesResponse(val games: List<ChessComGame> = emptyList())
data class ChessComGame(
    val url: String = "", val pgn: String? = null,
    @SerializedName("end_time") val endTime: Long = 0,
    @SerializedName("time_class") val timeClass: String = "unknown",
    val rated: Boolean = false, val white: PlayerSide? = null, val black: PlayerSide? = null
)
data class PlayerSide(val username: String = "", val rating: Int = 0, val result: String = "")
class ChessComApi {
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val gson = Gson()
    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "ChessMateAI/1.0 (Android personal game-review app)").header("Accept", "application/json, application/x-chess-pgn, text/plain").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Chess.com returned HTTP ${response.code}. Try again later.")
            return response.body?.string() ?: ""
        }
    }
    suspend fun fetchArchives(username: String): List<String> = withContext(Dispatchers.IO) {
        val clean = username.trim().lowercase()
        require(clean.matches(Regex("[a-z0-9_-]{3,25}"))) { "Enter a valid Chess.com username." }
        gson.fromJson(get("https://api.chess.com/pub/player/$clean/games/archives"), ArchiveResponse::class.java)?.archives ?: emptyList()
    }
    suspend fun fetchGames(archiveUrl: String): List<ChessComGame> = withContext(Dispatchers.IO) {
        gson.fromJson(get(archiveUrl), GamesResponse::class.java)?.games ?: emptyList()
    }
    suspend fun fetchAllGames(username: String, onProgress: (String) -> Unit = {}): List<ChessComGame> {
        val archives = fetchArchives(username); val result = mutableListOf<ChessComGame>()
        archives.forEachIndexed { index, url -> onProgress("Downloading archive ${index + 1}/${archives.size}…"); result += fetchGames(url).filter { !it.pgn.isNullOrBlank() } }
        return result.distinctBy { it.url }.sortedByDescending { it.endTime }
    }
}
