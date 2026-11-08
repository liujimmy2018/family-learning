package cn.family.learning.recognition

import cn.family.learning.data.Word
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Coordinates belong to one image only. Never pair translations across photos. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)
data class LocalWordResult(val words: List<Word>, val truncated: Boolean)

object LocalWordParser {
    private val han = Regex("[\\u3400-\\u9fff]")
    private val english = Regex("[A-Za-z]+(?:['’-][A-Za-z]+)*(?:[ \\t]+[A-Za-z]+(?:['’-][A-Za-z]+)*)*")
    private val annotations = Regex("\\[[^]\\r\\n]*]|/[^/\\r\\n]+/|【[^】]*】")
    private val partOfSpeech = Regex("(?i)\\b(?:n|v|adj|adv|pron|prep|conj|interj|aux|vt|vi)\\.")
    private val heading = Regex("(?i)^(?:unit|lesson|page|word list|word bank|vocabulary|words|english|chinese)(?:\\s*\\d+)?[\\s:：.]*$")

    fun parse(pages: List<List<OcrLine>>, textbook: Boolean): LocalWordResult {
        val candidates = pages.flatMap { page -> if (textbook) prose(page) else table(page) }
        val words = linkedMapOf<String, Word>()
        for (word in candidates) {
            val key = word.text.lowercase(Locale.ROOT)
            val previous = words[key]
            if (previous == null || (previous.meaning.isBlank() && word.meaning.isNotBlank())) words[key] = word
        }
        return LocalWordResult(words.values.take(500), words.size > 500)
    }

    private fun clean(text: String): String = partOfSpeech.replace(annotations.replace(text, " "), " ")
        .replace('’', '\'').replace(Regex("[ \\t]+"), " ").trim()

    private fun table(page: List<OcrLine>): List<Word> {
        val ordered = page.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })
        val usedMeanings = mutableSetOf<Int>()
        return ordered.flatMap { line ->
            val text = clean(line.text)
            if (heading.matches(text)) return@flatMap emptyList()
            val matches = english.findAll(text).toList()
            matches.mapIndexedNotNull { index, match ->
                val term = match.value.trim()
                if (term.length > 100 || heading.matches(term)) return@mapIndexedNotNull null
                val suffix = text.substring(match.range.last + 1, matches.getOrNull(index + 1)?.range?.first ?: text.length)
                var meaning = chineseMeaning(suffix)
                // OCR often returns English and Chinese columns in separate blocks.
                // Pair only a single candidate with the nearest Chinese line to its right on the same row.
                if (meaning.isBlank() && matches.size == 1 && !han.containsMatchIn(text)) {
                    val partner = ordered.withIndex().filter { (i, other) ->
                        i !in usedMeanings && other.left >= line.right && han.containsMatchIn(other.text) &&
                            !english.containsMatchIn(clean(other.text)) && aligned(line, other)
                    }.minByOrNull { (_, other) -> other.left - line.right }
                    if (partner != null) {
                        meaning = chineseMeaning(partner.value.text)
                        usedMeanings += partner.index
                    }
                }
                Word("", term, meaning)
            }
        }
    }

    private fun aligned(a: OcrLine, b: OcrLine): Boolean {
        val height = min(a.bottom - a.top, b.bottom - b.top).coerceAtLeast(1)
        val overlap = min(a.bottom, b.bottom) - max(a.top, b.top)
        return overlap >= height * 0.5 && abs((a.top + a.bottom) - (b.top + b.bottom)) <= height
    }

    private fun chineseMeaning(text: String): String {
        val first = han.find(text)?.range?.first ?: return ""
        // Exclude non-BMP characters unsupported by the existing MySQL 5.1 utf8 schema.
        return text.substring(first).filterNot { Character.isSurrogate(it) }
            .trim().trimEnd(';', '；', ',', '，', ':', '：', '|', ' ', '.', '。').take(300)
    }

    private fun prose(page: List<OcrLine>): List<Word> = page
        .sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })
        .filterNot { heading.matches(clean(it.text)) }
        .flatMap { line -> english.findAll(clean(line.text)).flatMap { match ->
            // Local OCR cannot select vocabulary by context. Return individual candidates for parent review.
            match.value.split(' ').asSequence().filter { it.isNotBlank() && it.length <= 100 }
                .map { Word("", it, "") }
        }.toList() }
}
