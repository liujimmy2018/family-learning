package cn.family.learning.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class UuidContractTest {
    @Test fun childAndEditorKeepUuidStringsThroughJson() {
        val childId = UUID.randomUUID().toString()
        val child = Child.from(JSONObject().put("id", childId).put("nickname", "孩子")
            .put("grade", 3).put("textbook", "教材").put("version", 2))
        assertEquals(childId, child.id)
        val listId = UUID.randomUUID().toString()
        val wordId = UUID.randomUUID().toString()
        val jobId = UUID.randomUUID().toString()
        val original = EditorDraft("单词", listOf(Word(wordId, "apple", "苹果")), listId, 1, jobId)
        assertEquals(original, EditorDraft.from(JSONObject(original.json().toString())))
    }

    @Test fun practiceSnapshotKeepsUuidWordAssociations() {
        val practiceId = UUID.randomUUID().toString()
        val wordId = UUID.randomUUID().toString()
        val words = JSONArray().put(JSONObject().put("id", wordId).put("ordinal", 0)
            .put("text", "apple").put("meaning", "苹果"))
        val practice = Practice.from(JSONObject().put("id", practiceId).put("title", "听写")
            .put("words", words).put("settings", DictationSettings().json())
            .put("status", "PAUSED").put("version", 3)
            .put("progress", JSONObject().put("round", 0).put("itemIndex", 0)))
        assertEquals(practiceId, practice.id)
        assertEquals(wordId, practice.words.single().id)
        assertEquals("PAUSED", practice.status)
        assertEquals(3L, practice.version)
    }
}
