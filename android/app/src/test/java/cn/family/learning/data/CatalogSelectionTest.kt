package cn.family.learning.data
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class CatalogSelectionTest {
    @Test fun cachedWordsPreservePhoneticsAndAcceptOlderSnapshots(){
        val legacy=JSONObject("""{"id":"w","text":"apple","meaning":"苹果","selected":false}""")
        assertEquals("",Word.from(legacy).phonetic)
        val word=Word.from(legacy.put("phonetic","/ˈæpəl/"))
        val restored=Word.from(JSONObject(word.json().toString()))
        assertEquals(word,restored);assertEquals("/ˈæpəl/",restored.phonetic);assertFalse(restored.selected)
    }
    @Test fun historyDistinguishesUnreviewedFromActualZeroScore(){
        val p="""{"id":"p","title":"听写","words":[{"id":"w","text":"apple","meaning":"苹果"}],"settings":{},"status":"COMPLETED","version":1}"""
        val source=JSONObject().put("practice",JSONObject(p)).put("elapsedSeconds",23).put("graded",false).put("score",JSONObject.NULL).put("total",1).put("createdAt","2026-11-05T10:00:00Z")
        val pending=HistoryItem.from(source);assertFalse(pending.graded);assertNull(pending.score);assertEquals(23,pending.elapsedSeconds)
        source.put("graded",true).put("score",0.0);val graded=HistoryItem.from(source);assertTrue(graded.graded);assertEquals(0.0,graded.score!!,0.0)
    }
    private fun entry(id: String,grade: Int,semester: String,unit: String)=CatalogEntry(id,grade,semester,unit,"apple","苹果","/ˈæpəl/")
    @Test fun filtersGradeSemesterAndSelectedUnitsTogether(){val all=listOf(entry("a",3,"上册","Unit 1"),entry("b",3,"下册","Unit 1"),entry("c",4,"上册","Unit 1"),entry("d",3,"上册","Unit 2"));assertEquals(listOf("a"),CatalogSelection.eligible(all,3,"上册",setOf("Unit 1")).map{it.id});assertEquals(listOf("Unit 1","Unit 2"),CatalogSelection.units(all,3,"上册"));assertTrue(CatalogSelection.eligible(all,3,"上册",emptySet()).isEmpty())}
    @Test fun parsesGradedResultsAndExplicitFalseMarks(){val review=ManualReview.from(JSONObject("""{"practiceId":"p","title":"听写","words":[{"id":"w","english":"apple","chinese":"苹果","phonetic":"/x/"}],"rounds":2,"elapsedSeconds":37,"graded":true,"score":50.0,"correct":1,"marks":[{"round":0,"index":0,"correct":false,"written":"appl"},{"round":1,"index":0,"correct":true}]}"""));assertEquals(2,review.total);assertEquals(false,review.marks["0:0"]);assertEquals("appl",review.written["0:0"]);assertEquals(50.0,review.score!!,0.0)}
}
