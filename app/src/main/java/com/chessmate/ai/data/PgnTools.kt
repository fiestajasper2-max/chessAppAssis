package com.chessmate.ai.data
import com.github.bhlangonijr.chesslib.move.MoveList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
data class ParsedGame(val white: String, val black: String, val result: String, val date: String, val pgn: String, val sanMoves: List<String>)
object PgnTools {
    private val tag = Regex("^\\[(\\w+) \"(.*)\"\\]$")
    fun parseMany(text: String): List<ParsedGame> = text.trim().split(Regex("(?m)(?=^\\[Event\\s+)")).filter { it.isNotBlank() }.mapNotNull { parseOne(it) }
    fun parseOne(text: String): ParsedGame? {
        val headers = mutableMapOf<String, String>()
        val body = text.lineSequence().filter { line -> val m = tag.matchEntire(line.trim()); if (m != null) headers[m.groupValues[1]] = m.groupValues[2]; m == null }.joinToString(" ")
        val cleaned = body.replace(Regex("\\{[^}]*}"), " ").replace(Regex("\\([^)]*\\)"), " ").replace(Regex("\\$\\d+"), " ").replace(Regex("\\d+\\.(\\.\\.)?"), " ").replace(Regex("1-0|0-1|1/2-1/2|\\*"), " ")
        val san = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() && !it.startsWith(";") }.map { it.trim().replace(Regex("[!?]+$"), "") }.filter { it.isNotBlank() }
        if (san.isEmpty()) return null
        return ParsedGame(headers["White"] ?: "White", headers["Black"] ?: "Black", headers["Result"] ?: "*", headers["Date"] ?: "Unknown date", text.trim(), san)
    }
    fun toUciMoves(game: ParsedGame): List<String> { val list = MoveList(); list.loadFromSan(game.sanMoves.joinToString(" ")); return list.map { it.toString() } }
    fun safeDate(epoch: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epoch * 1000))
}
