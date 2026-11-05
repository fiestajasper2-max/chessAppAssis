package com.chessmate.ai.engine
import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
data class EngineLine(val depth: Int, val score: String, val principalVariation: String, val bestMove: String)
class StockfishEngine(private val context: Context) {
    private var process: Process? = null
    private var writer: java.io.BufferedWriter? = null
    private var reader: java.io.BufferedReader? = null
    private fun executable(): File {
        val target = File(context.filesDir, "stockfish")
        if (!target.exists()) { context.assets.open("stockfish").use { input -> target.outputStream().use(input::copyTo) }; target.setExecutable(true, true) }
        return target
    }
    suspend fun start() = withContext(Dispatchers.IO) {
        if (process?.isAlive == true) return@withContext
        val binary = executable()
        process = ProcessBuilder(binary.absolutePath).directory(context.filesDir).redirectErrorStream(true).start()
        writer = process!!.outputStream.bufferedWriter(); reader = process!!.inputStream.bufferedReader()
        send("uci"); waitFor("uciok"); send("isready"); waitFor("readyok")
        send("setoption name Threads value 2"); send("setoption name Hash value 128")
    }
    private fun send(command: String) { writer?.apply { write(command); newLine(); flush() } }
    private fun waitFor(marker: String) { while (true) { val line = reader?.readLine() ?: error("Stockfish stopped unexpectedly"); if (line.contains(marker)) return } }
    suspend fun analyze(uciMoves: List<String>, depth: Int = 14): EngineLine = withContext(Dispatchers.IO) {
        start(); send("ucinewgame"); send("position startpos moves ${uciMoves.joinToString(" ")}"); send("go depth $depth")
        var result = EngineLine(0, "—", "", "")
        while (true) {
            val line = reader?.readLine() ?: error("Stockfish stopped unexpectedly")
            if (line.startsWith("info ") && line.contains(" pv ")) {
                val d = Regex("\\bdepth (\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val score = Regex("\\bscore (cp|mate) (-?\\d+)").find(line)?.let { if (it.groupValues[1] == "mate") "Mate in ${it.groupValues[2]}" else "${it.groupValues[2].toInt() / 100.0} pawns" } ?: "—"
                result = result.copy(depth = d, score = score, principalVariation = line.substringAfter(" pv ", ""))
            }
            if (line.startsWith("bestmove ")) return@withContext result.copy(bestMove = line.split(" ").getOrNull(1) ?: "")
        }
        @Suppress("UNREACHABLE_CODE") result
    }
    fun close() { runCatching { send("quit") }; runCatching { process?.destroy() }; process = null }
}
