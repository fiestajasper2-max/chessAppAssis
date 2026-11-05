package com.chessmate.ai

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chessmate.ai.data.ChessComApi
import com.chessmate.ai.data.ChessComGame
import com.chessmate.ai.data.PgnTools
import com.chessmate.ai.engine.StockfishEngine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChessMateApp() }
    }
}

@Composable
private fun ChessMateApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ChessComApi() }
    val engine = remember { StockfishEngine(context) }

    var username by remember { mutableStateOf("tenmamatsukaze135") }
    var status by remember { mutableStateOf("Ready to import completed games") }
    var games by remember { mutableStateOf(emptyList<ChessComGame>()) }
    var selected by remember { mutableStateOf<ChessComGame?>(null) }
    var analysis by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var importedPgn by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Could not open the selected PGN file.")
                }.onSuccess {
                    importedPgn = it
                    error = ""
                    status = "PGN file loaded."
                }.onFailure {
                    error = it.message ?: "Could not read PGN file."
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB9F27C),
            background = Color(0xFF101411),
            surface = Color(0xFF1B211D)
        )
    ) {
        Scaffold(containerColor = Color(0xFF101411)) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "CHESSMATE AI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFB9F27C)
                )
                Text(
                    text = "Completed-game analysis · Stockfish",
                    color = Color.LightGray
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Chess.com username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                busy = true
                                error = ""
                                analysis = ""
                                try {
                                    games = api.fetchAllGames(username) { message ->
                                        status = message
                                    }
                                    selected = null
                                    status = "Imported ${games.size} completed games"
                                } catch (throwable: Throwable) {
                                    error = throwable.message ?: "Game import failed."
                                    status = "Import failed."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) {
                        Text(if (busy) "Working…" else "Import games")
                    }

                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            picker.launch(
                                arrayOf(
                                    "application/x-chess-pgn",
                                    "text/plain",
                                    "application/octet-stream"
                                )
                            )
                        }
                    ) {
                        Text("Import PGN")
                    }
                }

                Text(status, color = Color(0xFFB9F27C), style = MaterialTheme.typography.bodySmall)

                if (error.isNotBlank()) {
                    Text(error, color = Color(0xFFFF8A80))
                }

                if (importedPgn.isNotBlank()) {
                    val parsedGames = remember(importedPgn) {
                        PgnTools.parseMany(importedPgn)
                    }
                    Text(
                        text = "Imported PGN: ${parsedGames.size} game(s)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(parsedGames) { parsedGame ->
                            CardRow(
                                title = "${parsedGame.white} vs ${parsedGame.black}",
                                subtitle = "${parsedGame.date} · ${parsedGame.result}"
                            ) {
                                scope.launch {
                                    busy = true
                                    error = ""
                                    try {
                                        val moves = PgnTools.toUciMoves(parsedGame)
                                        val report = analyzeWholeGame(moves, engine) { done, total ->
                                            status = "Analyzing move $done/$total…"
                                        }
                                        analysis = "${parsedGame.white} vs ${parsedGame.black} · ${parsedGame.result}\n\n$report"
                                    } catch (throwable: Throwable) {
                                        error = "PGN analysis failed: ${throwable.message}"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "YOUR GAMES",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(games) { game ->
                        val white = game.white?.username ?: "White"
                        val black = game.black?.username ?: "Black"
                        CardRow(
                            title = "$white vs $black",
                            subtitle = "${game.timeClass} · ${if (game.rated) "Rated" else "Unrated"}"
                        ) {
                            selected = game
                            analysis = ""
                            error = ""
                        }
                    }
                }

                selected?.let { game ->
                    Button(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                busy = true
                                error = ""
                                analysis = ""
                                try {
                                    val parsed = PgnTools.parseOne(
                                        game.pgn ?: throw IllegalStateException("This game has no PGN data.")
                                    ) ?: throw IllegalStateException("No moves were found in this PGN.")
                                    val moves = PgnTools.toUciMoves(parsed)
                                    val report = analyzeWholeGame(moves, engine) { done, total ->
                                        status = "Analyzing move $done/$total…"
                                    }
                                    analysis = buildString {
                                        append("Game: ${parsed.white} vs ${parsed.black}\n")
                                        append("Result: ${parsed.result}\n")
                                        append("Moves analyzed: ${moves.size}\n\n")
                                        append(report)
                                    }
                                    status = "Analysis complete."
                                } catch (throwable: Throwable) {
                                    error = throwable.message ?: "Analysis failed."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) {
                        Text("Analyze selected game")
                    }
                }

                if (analysis.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF202A23)
                        )
                    ) {
                        Text(
                            text = analysis,
                            modifier = Modifier.padding(12.dp),
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = "Independent post-game review tool; not affiliated with Chess.com.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    DisposableEffect(engine) {
        onDispose { engine.close() }
    }
}

private suspend fun analyzeWholeGame(
    moves: List<String>,
    engine: StockfishEngine,
    progress: (Int, Int) -> Unit
): String {
    if (moves.isEmpty()) return "No legal moves found in this PGN."

    val report = StringBuilder()
    val total = moves.size

    for (ply in 1..total) {
        progress(ply, total)
        val result = engine.analyze(moves.take(ply), 10)
        val sideToMove = if (ply % 2 == 1) "Black to move" else "White to move"

        report.append(
            "${(ply + 1) / 2}${if (ply % 2 == 1) "." else "..."} " +
                "After ${moves[ply - 1]} · $sideToMove\n"
        )
        report.append(
            "Evaluation: ${result.score} · Best move: ${result.bestMove} · " +
                "Depth: ${result.depth}\n"
        )
        if (result.principalVariation.isNotBlank()) {
            report.append("Principal variation: ${result.principalVariation}\n")
        }
        report.append("\n")
    }

    return report.toString()
}

@Composable
private fun CardRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF202A23)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = Color.LightGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
