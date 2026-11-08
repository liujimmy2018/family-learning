package cn.family.learning.recognition

import cn.family.learning.data.EditorDraft
import org.junit.Assert.*
import org.junit.Test

class LocalWordParserTest {
    private fun line(text: String, y: Int = 0, x: Int = 0, width: Int = 100) = OcrLine(text, x, y, x + width, y + 20)
    private fun parse(vararg lines: OcrLine) = LocalWordParser.parse(listOf(lines.toList()), false).words

    @Test fun extractsBilingualRowsAndRemovesPronunciationAndPartOfSpeech() {
        val words = parse(line("1. apple [ˈæpəl] n. 苹果"), line("2. banana /bəˈnɑːnə/ n. 香蕉", 30))
        assertEquals(listOf("apple", "banana"), words.map { it.text })
        assertEquals(listOf("苹果", "香蕉"), words.map { it.meaning })
    }

    @Test fun pairsSeparateOcrColumnsByGeometryRegardlessOfBlockOrder() {
        val words = parse(line("香蕉", 30, 180), line("apple"), line("苹果", 1, 180), line("banana", 30))
        assertEquals(listOf("apple", "banana"), words.map { it.text })
        assertEquals(listOf("苹果", "香蕉"), words.map { it.meaning })
    }

    @Test fun neverAssignsTranslationFromAnotherRowOrLeftColumn() {
        val words = parse(line("apple", 30, 180), line("苹果", 30), line("香蕉", 70, 300))
        assertEquals("", words.single().meaning)
    }

    @Test fun missingTranslationStaysEmptyRatherThanInvented() {
        assertEquals("", parse(line("apple")).single().meaning)
        assertTrue(parse(line("图片模糊"), line("12345")).isEmpty())
    }

    @Test fun extractsMultipleBilingualEntriesOnOneLine() {
        val words = parse(line("apple 苹果 banana 香蕉"))
        assertEquals(listOf("apple", "banana"), words.map { it.text })
        assertEquals(listOf("苹果", "香蕉"), words.map { it.meaning })
    }

    @Test fun preservesPhrasesHyphensApostrophesAndCapitalization() {
        val words = parse(line("ice cream 冰淇淋"), line("mother-in-law 岳母", 30), line("don't 不要", 60))
        assertEquals(listOf("ice cream", "mother-in-law", "don't"), words.map { it.text })
    }

    @Test fun deduplicatesAcrossPagesAndPrefersAvailableTranslation() {
        val result = LocalWordParser.parse(listOf(listOf(line("Apple")), listOf(line("apple 苹果"))), false)
        assertEquals(1, result.words.size)
        assertEquals("苹果", result.words.single().meaning)
    }

    @Test fun neverPairsTranslationsAcrossImages() {
        val result = LocalWordParser.parse(listOf(listOf(line("apple")), listOf(line("苹果", 0, 180))), false)
        assertEquals("", result.words.single().meaning)
    }

    @Test fun textbookModeListsIndividualWordsForReviewAndSkipsHeadings() {
        val result = LocalWordParser.parse(listOf(listOf(line("Unit 1"), line("I like apples. I don't like bananas.", 30))), true)
        assertEquals(listOf("I", "like", "apples", "don't", "bananas"), result.words.map { it.text })
        assertTrue(result.words.all { it.meaning.isEmpty() })
    }

    @Test fun refusesOversizedTermsAndDropsUnsupportedSurrogates() {
        assertTrue(parse(line("a".repeat(101))).isEmpty())
        assertEquals("苹果", parse(line("apple 苹果😀")).single().meaning)
    }

    @Test fun boundsCandidatesAndReportsTruncation() {
        fun name(n: Int): String = "word" + ('a' + n / 26) + ('a' + n % 26)
        val result = LocalWordParser.parse(listOf((0..500).map { line(name(it), it * 30) }), false)
        assertEquals(500, result.words.size)
        assertTrue(result.truncated)
    }

    @Test fun restoresLocalEditorDraftAndReadsOlderDrafts() {
        val local = EditorDraft("本地词表", parse(line("apple 苹果")), localRecognition = true)
        val restored = EditorDraft.from(local.json())
        assertTrue(restored.localRecognition)
        assertNull(restored.recognitionJobId)
        assertEquals(local.words, restored.words)
        val old = local.json().apply { remove("localRecognition") }
        assertFalse(EditorDraft.from(old).localRecognition)
    }
}
