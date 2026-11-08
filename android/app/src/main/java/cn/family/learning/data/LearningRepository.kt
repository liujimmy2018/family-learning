package cn.family.learning.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

private val Context.learningSettings by preferencesDataStore("learning_settings")
class LearningRepository(val context: Context) {
    val session=SessionStore(context)
    val api=ApiClient(session)
    val servers=ServerProfileStore(context,session)
    val cacheOwner: String get()=ServerAddresses.owner(session.baseUrl,session.accountId)
    private val db=Room.databaseBuilder(context,LocalDatabase::class.java,"family-learning.db").build()
    val drafts=db.drafts()
    var usedOfflineCache: Boolean=false
        private set
    suspend fun login(url: String,name: String,password: String,invite: String?) {
        val normalized=ServerAddresses.normalize(url,cn.family.learning.BuildConfig.DEBUG)
        scopeLegacy();session.clear()
        api.setUrl(normalized)
        val body=JSONObject().put("loginName",name).put("password",password)
        if(invite!=null)body.put("inviteCode",invite)
        session.save(api.json(if(invite==null)"/auth/login" else "/auth/register","POST",body,authenticated=false) as JSONObject)
    }
    suspend fun switchServer(url: String){val normalized=ServerAddresses.normalize(url,cn.family.learning.BuildConfig.DEBUG);scopeLegacy();api.setUrl(normalized)}
    suspend fun logout(){val owner=cacheOwner;try{api.json("/auth/logout","POST",JSONObject())}finally{session.clear();drafts.removeAccount(owner);File(context.cacheDir,"audio/$owner").deleteRecursively();context.learningSettings.edit{it.remove(stringPreferencesKey("children_$owner"));it.remove(stringPreferencesKey("child_$owner"))}}}
    private suspend fun scopeLegacy(){
        val account=session.accountId;if(account.isBlank()||ServerAddresses.owner(servers.legacyUrl,account)!=cacheOwner)return
        val migrated=booleanPreferencesKey("legacy_scoped_$account")
        val old=context.learningSettings.data.first();if(old[migrated]==true)return
        val owner=cacheOwner;drafts.scopeLegacy(account,owner)
        context.learningSettings.edit{p->
            old[stringPreferencesKey("children_$account")]?.let{p[stringPreferencesKey("children_$owner")]=it}
            old[stringPreferencesKey("child_$account")]?.let{p[stringPreferencesKey("child_$owner")]=it}
            p[migrated]=true
        }
    }
    suspend fun children(): List<Child> {
        scopeLegacy()
        usedOfflineCache=false
        val key=stringPreferencesKey("children_$cacheOwner")
        val data=try{val result=api.json("/children") as JSONArray;context.learningSettings.edit{it[key]=result.toString()};result}
        catch(e: IOException){if(e is ApiFailure)throw e;val cached=context.learningSettings.data.first()[key]?:throw e;usedOfflineCache=true;JSONArray(cached)}
        return data.objects().map(Child::from)
    }
    suspend fun saveChild(child: Child?,name: String,grade: Int,book: String): Child {
        val body=JSONObject().put("nickname",name).put("grade",grade).put("textbook",book).put("expectedVersion",child?.version?:0)
        return Child.from(api.json("/children"+(child?.let {"/${it.id}"}?:""),if(child==null)"POST" else "PATCH",body) as JSONObject)
    }
    suspend fun selectChild(id: String){context.learningSettings.edit{it[stringPreferencesKey("child_$cacheOwner")]=id}}
    suspend fun selectedChild()=context.learningSettings.data.first()[stringPreferencesKey("child_$cacheOwner")]
    suspend fun settings()=(context.learningSettings.data.first()[stringPreferencesKey("dictation")]?.let {DictationSettings.from(JSONObject(it))}?:DictationSettings()).let{it.copy(intervalSeconds=it.intervalSeconds.coerceIn(1,10))}
    suspend fun settings(value: DictationSettings){context.learningSettings.edit{it[stringPreferencesKey("dictation")]=value.json(true).toString()}}
    private fun key(child: String,kind: String,id: String)= "$cacheOwner:$child:$kind:$id"
    suspend fun putDraft(child: String,kind: String,id: String,payload: JSONObject){drafts.put(Draft(key(child,kind,id),cacheOwner,child,kind,payload.toString()))}
    suspend fun getDraft(child: String,kind: String,id: String)=drafts.get(key(child,kind,id))?.let {JSONObject(it.payload)}
    suspend fun removeDraft(child: String,kind: String,id: String)=drafts.remove(key(child,kind,id))
    suspend fun lists(child: String): List<WordList> {
        val data=try{val r=api.json("/children/$child/lists") as JSONArray;putDraft(child,"cache","lists",JSONObject().put("items",r));r}
        catch(e: IOException){if(e is ApiFailure)throw e;val cached=getDraft(child,"cache","lists")?.getJSONArray("items")?:throw e;usedOfflineCache=true;cached}
        return data.objects().map(WordList::from)
    }
    suspend fun saveList(child: String,editor: EditorDraft,key: String): WordList {
        val words=editor.words.filter {it.selected}.map{JSONObject().put("text",it.text.trim()).put("meaning",it.meaning.trim())}
        val body=JSONObject().put("title",editor.title.trim()).put("words",JSONArray(words)).put("expectedVersion",editor.version)
        editor.recognitionJobId?.let{body.put("recognitionJobId",it)}
        val path="/children/$child/lists"+(editor.listId?.let{"/$it/versions"}?:"")
        return WordList.from(api.json(path,"POST",body,key) as JSONObject)
    }
    suspend fun recognition(child: String,title: String,type: String,files: List<File>,key: String): JSONObject {
        val old=getDraft(child,"import","current")
        val savedAssets=old?.optJSONArray("assetIds")
        val ids=if(savedAssets!=null&&(old.optString("requestKey")==key))savedAssets else JSONArray(files.map{api.upload(child,it).getString("id")})
        putDraft(child,"import","current",JSONObject().put("source","CLOUD").put("assetIds",ids).put("requestKey",key).put("title",title).put("type",type).put("files",JSONArray(files.map{it.path})))
        val body=JSONObject().put("title",title).put("type",type).put("assetIds",ids)
        val job=api.json("/children/$child/recognitions","POST",body,key) as JSONObject
        putDraft(child,"import","current",JSONObject().put("source","CLOUD").put("jobId",job.getString("id")).put("assetIds",ids).put("requestKey",key).put("title",title).put("type",type).put("files",JSONArray(files.map{it.path})))
        return awaitJob(child,job.getString("id"))
    }
    suspend fun awaitJob(child: String,id: String): JSONObject {
        repeat(150){attempt->
            val job=api.json("/children/$child/jobs/$id") as JSONObject
            when(job.getString("status")){"SUCCEEDED"->return job;"FAILED","CANCELLED"->throw ApiFailure(422,job.optString("errorCode"),job.optString("errorMessage","任务失败，请重试"))}
            delay(minOf(2000L+attempt*200L,10000L))
        }
        throw IOException("处理仍在进行，可稍后继续查看")
    }
    suspend fun createPractice(child: String,list: WordList,selected: Set<String>,settings: DictationSettings,key: String): Practice {
        val body=JSONObject().put("listId",list.id).put("expectedListVersion",list.version).put("itemIds",JSONArray(list.words.filter{it.id in selected}.map{it.id})).put("settings",settings.json())
        val data=api.json("/children/$child/practices","POST",body,key) as JSONObject
        putDraft(child,"practice",data.getString("id"),data);return Practice.from(data).copy(settings=settings)
    }
    suspend fun practices(child: String): List<Practice> {
        val result=api.json("/children/$child/practices") as JSONArray
        result.objects().forEach{putDraft(child,"practice",it.getString("id"),it)}
        return result.objects().map(Practice::from)
    }
    suspend fun cachedPractices(child: String)=drafts.list(cacheOwner,child,"practice").map{Practice.from(JSONObject(it.payload))}
    suspend fun saveProgress(child: String,practice: Practice,status: String,round: Int,index: Int,events: JSONArray): Practice {
        val old=getDraft(child,"outbox",practice.id)?.optJSONArray("events")?.objects()?:emptyList()
        val merged=(old+events.objects()).distinctBy{it.getString("eventId")}
        val body=JSONObject().put("expectedVersion",practice.version).put("status",status).put("round",round).put("itemIndex",index).put("events",JSONArray(merged))
        putDraft(child,"outbox",practice.id,body)
        return flushProgress(child,practice.id)?:practice
    }
    suspend fun flushProgress(child: String,id: String): Practice? {
        val body=getDraft(child,"outbox",id)?:return null
        val result=try{api.json("/children/$child/practices/$id/events","POST",body) as JSONObject}
        catch(e: ApiFailure){
            if(e.status!=409)throw e
            val remote=api.json("/children/$child/practices/$id") as JSONObject
            val progress=remote.optJSONObject("progress")?:JSONObject()
            if(remote.optString("status")==body.optString("status")&&progress.optInt("round")==body.optInt("round")&&progress.optInt("itemIndex")==body.optInt("itemIndex")){
                removeDraft(child,"outbox",id);putDraft(child,"practice",id,remote);return Practice.from(remote)
            }
            throw ApiFailure(409,"VERSION_CONFLICT","听写进度已更新，请刷新后重新打开任务")
        }
        putDraft(child,"practice",id,result);removeDraft(child,"outbox",id);return Practice.from(result)
    }
    suspend fun cloudAudio(child: String,text: String,language: String,file: File){
        val j=api.json("/children/$child/audio-preparations","POST",JSONObject().put("text",text).put("language",language)) as JSONObject
        val ready=awaitJob(child,j.getString("id"));api.download(child,ready.getJSONObject("result").getString("assetId"),file)
    }
    fun close(){db.close()}
}
