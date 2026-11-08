package cn.family.learning.data

import org.json.JSONObject

interface ApiSession {
    var baseUrl: String
    val credentials: JSONObject?
    val generation: Long
    val epoch: Long
    fun saveIfCurrent(expected: Long,value: JSONObject): Boolean
    fun clearIfCurrent(expected: Long)
}
