package cn.family.learning.data

import org.json.JSONObject

data class CatalogBook(val id: String,val title: String,val revision: Int,val count: Int,val subject: String="ENGLISH"){
    companion object{fun from(j: JSONObject)=CatalogBook(j.getString("id"),j.getString("title"),j.getInt("revision"),j.optInt("count"),j.optString("subject","ENGLISH"))}
}
data class CatalogEntry(val id: String,val grade: Int,val semester: String,val unit: String,val english: String,val chinese: String,val phonetic: String){
    companion object{fun from(j: JSONObject)=CatalogEntry(j.optString("id"),j.optInt("grade"),j.optString("semester"),j.optString("unit"),j.getString("english"),j.getString("chinese"),j.optString("phonetic"))}
}
data class ManualReview(val practiceId: String,val title: String,val words: List<CatalogEntry>,val rounds: Int,val elapsedSeconds: Int,val graded: Boolean,val score: Double?,val correct: Int,val marks: Map<String,Boolean>,val written: Map<String,String>){
    val total get()=words.size*rounds
    companion object{fun from(j: JSONObject): ManualReview{
        val marks=j.optJSONArray("marks")?.objects()?:emptyList()
        return ManualReview(j.getString("practiceId"),j.getString("title"),j.getJSONArray("words").objects().map(CatalogEntry::from),j.getInt("rounds"),j.optInt("elapsedSeconds"),j.getBoolean("graded"),if(j.isNull("score"))null else j.getDouble("score"),j.optInt("correct"),marks.associate{ "${it.getInt("round")}:${it.getInt("index")}" to it.getBoolean("correct") },marks.associate{"${it.getInt("round")}:${it.getInt("index")}" to it.optString("written")})
    }}
}
data class WrongWord(val id: String,val english: String,val chinese: String,val phonetic: String,val grade: Int,val semester: String,val unit: String,val mistakeCount: Int,val mastered: Boolean){
    companion object{fun from(j: JSONObject)=WrongWord(j.getString("id"),j.getString("english"),j.getString("chinese"),j.optString("phonetic"),j.optInt("grade"),j.optString("semester"),j.optString("unit"),j.getInt("mistakeCount"),j.getBoolean("mastered"))}
}
object CatalogSelection {
    fun eligible(entries: List<CatalogEntry>,grade: Int,semester: String,units: Set<String>)=entries.filter{it.grade==grade&&it.semester==semester&&it.unit in units}
    fun units(entries: List<CatalogEntry>,grade: Int,semester: String)=entries.filter{it.grade==grade&&it.semester==semester}.map{it.unit}.distinct()
}
data class HistoryItem(val practice: Practice,val elapsedSeconds: Int,val graded: Boolean,val score: Double?,val correct: Int,val total: Int,val createdAt: String){
    companion object{fun from(j: JSONObject)=HistoryItem(Practice.from(j.getJSONObject("practice")),j.optInt("elapsedSeconds"),j.optBoolean("graded"),if(j.isNull("score"))null else j.getDouble("score"),j.optInt("correct"),j.optInt("total"),j.optString("createdAt"))}
}
