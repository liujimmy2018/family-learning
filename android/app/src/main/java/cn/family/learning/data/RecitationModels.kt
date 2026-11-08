package cn.family.learning.data

import org.json.JSONObject

data class RecitationLesson(
    val id: String,
    val title: String,
    val grade: Int,
    val semester: String,
    val unit: String,
    val revision: Int,
    val content: String
) {
    val scope get() = "${grade}年级${semester} · $unit"

    companion object {
        fun from(json: JSONObject) = RecitationLesson(
            json.getString("id"), json.getString("title"), json.getInt("grade"),
            json.getString("semester"), json.getString("unit"), json.getInt("revision"),
            json.optString("content")
        )
    }
}

data class RecitationAttempt(
    val id: String,
    val title: String,
    val result: String,
    val createdAt: String
) {
    val resultLabel get() = if (result == "COMPLETED") "已完成" else "需再练习"

    companion object {
        fun from(json: JSONObject) = RecitationAttempt(
            json.getString("id"), json.getString("title"), json.getString("result"), json.getString("createdAt")
        )
    }
}
