package cn.family.learning.data

import org.json.JSONArray
import org.json.JSONObject

data class Child(val id: String, val nickname: String, val grade: Int, val textbook: String, val version: Long) {
    companion object { fun from(j: JSONObject) = Child(j.getString("id"),j.getString("nickname"),j.getInt("grade"),j.getString("textbook"),j.getLong("version")) }
}
data class Word(val id: String, val text: String, val meaning: String, val selected: Boolean = true, val phonetic: String = "") {
    fun json() = JSONObject().put("id",id).put("text",text).put("meaning",meaning).put("selected",selected).put("phonetic",phonetic)
    companion object { fun from(j: JSONObject) = Word(j.optString("id"),j.getString("text"),j.getString("meaning"),j.optBoolean("selected",true),j.optString("phonetic","")) }
}
data class WordList(val id: String,val title: String,val version: Int,val words: List<Word>) {
    companion object { fun from(j: JSONObject) = WordList(j.getString("id"),j.getString("title"),j.getInt("version"),j.getJSONArray("words").objects().map(Word::from)) }
}
data class DictationSettings(
    val mode: String = "ENGLISH", val countdownSeconds: Int = 5, val intervalSeconds: Int = 10,
    val repeats: Int = 2, val repeatGapSeconds: Double = 1.0, val rounds: Int = 1, val randomOrder: Boolean = false,
    val voiceSource: String = "CLOUD"
) {
    fun json(includeSource: Boolean = false): JSONObject = JSONObject().put("mode",mode).put("countdownSeconds",countdownSeconds)
        .put("intervalSeconds",intervalSeconds).put("repeats",repeats).put("repeatGapSeconds",repeatGapSeconds)
        .put("rounds",rounds).put("randomOrder",randomOrder).apply { if(includeSource) put("voiceSource",voiceSource) }
    companion object { fun from(j: JSONObject) = DictationSettings(j.optString("mode","ENGLISH"),j.optInt("countdownSeconds",5),j.optInt("intervalSeconds",10),j.optInt("repeats",2),j.optDouble("repeatGapSeconds",1.0),j.optInt("rounds",1),j.optBoolean("randomOrder",false),j.optString("voiceSource","CLOUD")) }
}
data class Practice(val id: String,val title: String,val words: List<Word>,val settings: DictationSettings,val status: String,val version: Long,val round: Int,val itemIndex: Int,val subject: String="ENGLISH") {
    companion object { fun from(j: JSONObject): Practice { val p=j.optJSONObject("progress")?:JSONObject();return Practice(j.getString("id"),j.getString("title"),j.getJSONArray("words").objects().map(Word::from),DictationSettings.from(j.getJSONObject("settings")),j.getString("status"),j.getLong("version"),p.optInt("round"),p.optInt("itemIndex"),j.optString("subject","ENGLISH")) } }
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

data class EditorDraft(val title: String="",val words: List<Word> = listOf(Word("","","")),val listId: String?=null,val version: Int=0,val recognitionJobId: String?=null,val localRecognition: Boolean=false) {
    fun json()=JSONObject().put("title",title).put("words",JSONArray(words.map {it.json()})).put("listId",listId).put("version",version).put("recognitionJobId",recognitionJobId).put("localRecognition",localRecognition)
    companion object { fun from(j: JSONObject)=EditorDraft(j.optString("title"),j.getJSONArray("words").objects().map(Word::from),j.optString("listId").takeIf {it.isNotBlank()&&it!="null"},j.optInt("version"),j.optString("recognitionJobId").takeIf {it.isNotBlank()&&it!="null"},j.optBoolean("localRecognition",false)) }
}
