package com.chessmate.ai
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chessmate.ai.data.*
import com.chessmate.ai.engine.StockfishEngine
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { ChessMateApp() } }
}
@Composable private fun ChessMateApp() {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val api = remember { ChessComApi() }; val engine = remember { StockfishEngine(context) }
    var username by remember { mutableStateOf("tenmamatsukaze135") }
    var status by remember { mutableStateOf("Ready to import completed games") }
    var games by remember { mutableStateOf(emptyList<ChessComGame>()) }
    var selected by remember { mutableStateOf<ChessComGame?>(null) }
    var analysis by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }; var importedPgn by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }.onSuccess { importedPgn = it; status = "PGN file loaded." }.onFailure { error = it.message ?: "Could not read PGN file" } }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB9F27C), background = Color(0xFF101411), surface = Color(0xFF1B211D))) {
        Scaffold(containerColor = Color(0xFF101411)) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CHESSMATE AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color(0xFFB9F27C))
                Text("Completed-game analysis · Stockfish", color = Color.LightGray)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Chess.com username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { scope.launch { busy = true; error = ""; analysis = ""; try { games = api.fetchAllGames(username) { status = it }; status = "Imported ${games.size} completed games" } catch (t: Throwable) { error = t.message ?: "Import failed"; status = "Import failed" } finally { busy = false } }, modifier = Modifier.weight(1f)) { Text(if (busy) "Working…" else "Import games") }
                    OutlinedButton(onClick = { picker.launch(arrayOf("application/x-chess-pgn", "text/plain", "application/octet-stream")) }) { Text("Import PGN") }
                }
                Text(status, color = Color(0xFFB9F27C), style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80))
                if (importedPgn.isNotBlank()) {
                    val imported = remember(importedPgn) { PgnTools.parseMany(importedPgn) }
                    Text("Imported PGN: ${imported.size} game(s)", fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.heightIn(max = 130.dp)) { items(imported) { g -> CardRow("${g.white} vs ${g.black}", "${g.date} · ${g.result}") { scope.launch { busy = true; runCatching { val uci = PgnTools.toUciMoves(g); val report = analyzeWholeGame(uci, engine) { done, total -> status = "Analyzing position $done/$total…" }; analysis = "${g.white} vs ${g.black} · ${g.result}\n\n$report" }.onFailure { error = "PGN analysis error: ${it.message}" }; busy = false } } } }
                }
                Text("YOUR GAMES", fontWeight = FontWeight.Bold, color = Color.White)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(games) { game ->
                        val w = game.white?.username ?: "White"; val b = game.black?.username ?: "Black"
                        CardRow("$w vs $b", "${game.timeClass} · ${if (game.rated) "Rated" else "Unrated"}") { selected = game; analysis = "" }
                    }
                }
                selected?.let { game ->
                    Button(enabled = !busy, onClick = { scope.launch { busy = true; error = ""; analysis = "Analyzing completed game…"; try { val parsed = PgnTools.parseOne(game.pgn.orEmpty()) ?: throw IllegalStateException("No PGN moves found"); val moves = PgnTools.toUciMoves(parsed); val report = analyzeWholeGame(moves, engine) { done, total -> status = "Analyzing position $done/$total…" }; analysis = "Game: ${parsed.white} vs ${parsed.black}\nResult: ${parsed.result}\nPly count: ${moves.size}\n\n$report" } catch (t: Throwable) { error = t.message ?: "Analysis failed"; analysis = "" } finally { busy = false } } }, modifier = Modifier.fillMaxWidth()) { Text("Analyze selected game") }
                }
                if (analysis.isNotBlank()) Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202A23))) { Text(analysis, Modifier.padding(12.dp), color = Color.White) }
                Text("Independent post-game review tool; not affiliated with Chess.com.", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    DisposableEffect(Unit) { onDispose { engine.close() } }
}
private suspend fun analyzeWholeGame(moves: List<String>, engine: StockfishEngine, progress: (Int, Int) -> Unit): String {
    if (moves.isEmpty()) return "No legal moves found in this PGN."
    val report = StringBuilder(); val total = moves.size
    for (ply in 1..total) {
        progress(ply, total); val result = engine.analyze(moves.take(ply), 10)
        val side = if (ply % 2 == 1) "Black to move" else "White to move"
        report.append("${(ply + 1) / 2}${if (ply % 2 == 1) "." else "..."} After ${moves[ply - 1]} · $side\n")
        report.append("Eval: ${result.score} · Engine best: ${result.bestMove} · depth ${result.depth}\n")
        if (result.principalVariation.isNotBlank()) report.append("Line: ${result.principalVariation}\n")
        report.append("\n")
    }
    return report.toString()
}
@Composable private fun CardRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color(0xFF202A23)), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
    }
}
