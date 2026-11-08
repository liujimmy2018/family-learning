package cn.family.learning.data

import cn.family.learning.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiFailure(val status: Int,val code: String,override val message: String): IOException(message)
class ApiClient(val session: ApiSession) {
    private val http=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(100,TimeUnit.SECONDS).build()
    private val refreshLock=Any()
    fun setUrl(raw: String) {
        session.baseUrl=ServerAddresses.normalize(raw,BuildConfig.DEBUG)
    }
    suspend fun json(path: String,method: String="GET",body: JSONObject?=null,key: String?=null,authenticated: Boolean=true): Any {
        val epoch=session.epoch
        return withContext(Dispatchers.IO){
        requireEpoch(epoch)
        val request=build(path,method,body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType()),key,authenticated,epoch)
        response(request,authenticated).use {r->parse(r)}
    }}
    private fun requireEpoch(expected: Long){if(expected!=session.epoch)throw ApiFailure(409,"SERVER_CHANGED","服务器或登录状态已切换，请重新操作")}
    suspend fun upload(child: String,file: File): JSONObject {
        val epoch=session.epoch
        return withContext(Dispatchers.IO){
        val multipart=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file","learning.jpg",file.readBytes().toRequestBody("image/jpeg".toMediaType())).build()
        response(build("/children/$child/assets","POST",multipart,null,true,epoch),true).use {parse(it) as JSONObject}
    }}
    suspend fun download(child: String,asset: String,target: File){
        val epoch=session.epoch
        withContext(Dispatchers.IO){
        response(build("/children/$child/assets/$asset/content","GET",null,null,true,epoch),true).use {r->
            if(!r.isSuccessful)parse(r)
            val bytes=r.body?.bytes()?:throw IOException("语音下载为空")
            if(bytes.isEmpty()||bytes.size>4*1024*1024)throw IOException("语音文件无效")
            target.parentFile?.mkdirs();val temp=File(target.path+".partial");temp.writeBytes(bytes);if(!temp.renameTo(target)){temp.delete();throw IOException("无法保存音频")}
        }
    }}
    private data class RequestSession(val url: String,val epoch: Long)
    private fun build(path: String,method: String,body: RequestBody?,key: String?,auth: Boolean,expectedEpoch: Long=session.epoch): Request = synchronized(session) {
        requireEpoch(expectedEpoch)
        val b=Request.Builder().url(session.baseUrl+"/api/v1"+path).method(method,body)
        b.tag(RequestSession::class.java,RequestSession(session.baseUrl,session.epoch))
        if(auth)session.credentials?.optString("accessToken")?.let {b.header("Authorization","Bearer $it")}
        if(key!=null)b.header("Idempotency-Key",key)
        b.build()
    }
    private fun ensureCurrent(request: Request,response: Response?=null){
        val expected=request.tag(RequestSession::class.java)!!
        if(expected.url!=session.baseUrl||expected.epoch!=session.epoch){response?.close();throw ApiFailure(409,"SERVER_CHANGED","服务器或登录状态已切换，请重新操作")}
    }
    private fun response(request: Request,auth: Boolean): Response {
        ensureCurrent(request)
        val r=http.newCall(request).execute();ensureCurrent(request,r);if(r.code!=401||!auth)return r
        r.close()
        synchronized(refreshLock){
            ensureCurrent(request)
            val oldHeader=request.header("Authorization")
            if(oldHeader=="Bearer "+session.credentials?.optString("accessToken")){
                val refresh=session.credentials?.optString("refreshToken")?:throw ApiFailure(401,"UNAUTHORIZED","请重新登录")
                val generation=session.generation
                val refreshRequest=synchronized(session){ensureCurrent(request);build("/auth/refresh","POST",JSONObject().put("refreshToken",refresh).toString().toRequestBody("application/json".toMediaType()),null,false)}
                http.newCall(refreshRequest).execute().use {rr->
                    ensureCurrent(request)
                    if(!rr.isSuccessful){session.clearIfCurrent(generation);throw ApiFailure(401,"SESSION_EXPIRED","登录已失效，请重新登录")}
                    if(!session.saveIfCurrent(generation,parse(rr) as JSONObject))throw ApiFailure(401,"SESSION_CLOSED","登录状态已变更，请重新登录")
                }
            }
        }
        val retry=synchronized(session){ensureCurrent(request);val access=session.credentials?.optString("accessToken")?:throw ApiFailure(401,"SESSION_EXPIRED","请重新登录");request.newBuilder().header("Authorization","Bearer $access").build()}
        val retried=http.newCall(retry).execute();ensureCurrent(request,retried);return retried
    }
    private fun parse(response: Response): Any {
        val j=try{JSONObject(response.body?.string()?:"{}")}catch(_:Exception){throw ApiFailure(response.code,"SERVER_UNAVAILABLE","服务返回异常，请检查服务地址")}
        if(!response.isSuccessful)throw ApiFailure(response.code,j.optString("code","ERROR"),j.optString("message","请求失败，请稍后重试"))
        return j.opt("data")?:JSONObject()
    }
}
