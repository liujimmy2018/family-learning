package cn.family.learning

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.family.learning.data.*
import cn.family.learning.media.PhotoFiles
import cn.family.learning.playback.*
import cn.family.learning.recognition.LocalTextRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AppState(
    val studySubject: String="ENGLISH",val settingsForChinese: Boolean=false,
    val recitationLessons: List<RecitationLesson> = emptyList(),
    val recitationLesson: RecitationLesson?=null,
    val recitationAttempts: List<RecitationAttempt> = emptyList(),
    val recitationSaved: RecitationAttempt?=null,
    val catalogBooks: List<CatalogBook> = emptyList(),val catalogBook: CatalogBook?=null,val catalogEntries: List<CatalogEntry> = emptyList(),
    val catalogGrade: Int=3,val catalogSemester: String="上册",val catalogUnits: Set<String> = emptySet(),val catalogSelection: String="ALL",val catalogCount: Int=20,val catalogScope: String="UNIT",
    val historyItems: List<HistoryItem> = emptyList(),val historyFilter: String="ALL",val historyPage: Int=0,val historyHasMore: Boolean=false,
    val review: ManualReview?=null,val reviewMarks: Map<String,Boolean> = emptyMap(),val reviewWritten: Map<String,String> = emptyMap(),val wrongWords: List<WrongWord> = emptyList(),
    val servers: List<ServerProfile> = emptyList(),val serverUrl: String="",val editingServer: ServerProfile?=null,
    val route: String="LOGIN",val busy: Boolean=false,val busyText: String="",val message: String?=null,
    val children: List<Child> = emptyList(),val child: Child?=null,val editingChild: Child?=null,
    val lists: List<WordList> = emptyList(),val currentList: WordList?=null,val selectedWords: Set<String> = emptySet(),
    val editor: EditorDraft=EditorDraft(),val importFiles: List<String> = emptyList(),val importType: String="WORD_TABLE",
    val recognitionSource: String="LOCAL",val importTitle: String="英语词表",
    val cropPath: String?=null,val cropIndex: Int?=null,val settings: DictationSettings=DictationSettings(),
    val practice: Practice?=null,val history: List<Practice> = emptyList(),val hasEditorDraft: Boolean=false,val hasImportDraft: Boolean=false,val lookupWord: Word?=null,val conflictPractice: Practice?=null
)

class AppViewModel(application: Application): AndroidViewModel(application) {
    val repository=LearningRepository(application)
    private val speech=SpeechCache(application,repository)
    val player=DictationPlayer(viewModelScope,speech,Media3AudioOutput(application),android.os.SystemClock::elapsedRealtime)
    private fun initialState(route: String)=AppState(route=route,servers=repository.servers.list(),serverUrl=repository.session.baseUrl)
    private val mutable=MutableStateFlow(initialState(if(repository.session.credentials==null)"LOGIN" else "HOME"))
    val state=mutable.asStateFlow()
    private val syncMutex=Mutex()
    private var saveKey=UUID.randomUUID().toString()
    private var practiceKey=UUID.randomUUID().toString()
    private var recitationKey=UUID.randomUUID().toString()
    private var importKey=UUID.randomUUID().toString()
    private val pendingPhotos=java.util.ArrayDeque<Uri>()
    private var activeJob: Job?=null
    private val progressJobs=mutableSetOf<Job>()
    private var serverReturnRoute="LOGIN"
    private var settingsReturnRoute="PROFILE"
    private var reviewSave: Job?=null
    private var reviewReturnRoute="HOME"
    init {
        viewModelScope.launch(Dispatchers.IO){
            val expiry=System.currentTimeMillis()-30L*24*60*60*1000
            File(application.filesDir,"imports").listFiles()?.filter{it.isFile&&it.lastModified()<expiry}?.forEach{it.delete()}
        }
        if(repository.session.credentials!=null)perform("正在加载学习内容"){reload()}
        viewModelScope.launch {
            player.state.map{it.copy(remaining=0,prepared=0,error=null,elapsedMillis=0)}.distinctUntilChanged().collect{playback->
                val child=mutable.value.child?:return@collect;val p=mutable.value.practice?:return@collect
                if(p.id!=playback.practiceId)return@collect
                val phase=playback.phase;val round=playback.round;val index=playback.itemIndex
                if(phase=="IDLE")return@collect
                repository.putDraft(child.id,"playback",p.id,JSONObject().put("phase",phase).put("round",round).put("itemIndex",index).put("source",mutable.value.settings.voiceSource).put("elapsedMillis",player.state.value.elapsedMillis))
                if(phase in listOf("PAUSED","COMPLETED","STOPPED"))syncProgress(p.id,phase,round,index)
            }
        }
    }
    private fun update(block: (AppState)->AppState){mutable.update(block)}
    fun message(text: String?){update{it.copy(message=text)}}
    private fun perform(label: String,block: suspend ()->Unit){
        if(mutable.value.busy)return
        activeJob=viewModelScope.launch {
            update{it.copy(busy=true,busyText=label,message=null)}
            try{block()}catch(e: CancellationException){throw e}catch(e: Exception){
                if(e is ApiFailure&&e.status==401)update{it.copy(route="LOGIN",message=e.message)}
                else message(e.message?:"操作失败，请稍后重试")
            }finally{update{it.copy(busy=false,busyText="")}}
        }
    }
    fun login(url: String,name: String,password: String,invite: String?)=perform("正在验证身份"){
        repository.login(url,name.trim(),password,invite?.trim());reload();update{it.copy(route=if(it.children.isEmpty())"CHILD" else "HOME")}
    }
    private suspend fun reload(){
        val children=repository.children();val selected=repository.selectedChild();val child=children.firstOrNull{it.id==selected}?:children.firstOrNull()
        val settings=repository.settings()
        update{it.copy(children=children,child=child,settings=settings)}
        if(child!=null)loadChild(child)
    }
    private suspend fun loadChild(child: Child){
        repository.selectChild(child.id)
        val pending=repository.drafts.list(repository.cacheOwner,child.id,"outbox")
        for(item in pending)try{repository.flushProgress(child.id,item.key.substringAfterLast(':'))}catch(_: Exception){}
        val lists=repository.lists(child.id)
        val serverHistory=try{repository.practices(child.id)}catch(_: Exception){repository.cachedPractices(child.id)}
        val history=serverHistory.map{p->
            val local=repository.getDraft(child.id,"playback",p.id)
            val phase=local?.optString("phase")
            if(phase in listOf("COMPLETED","STOPPED","PAUSED")&&p.status !in listOf("COMPLETED","STOPPED"))p.copy(status=phase!!,round=local!!.optInt("round"),itemIndex=local.optInt("itemIndex")) else p
        }
        val editor=repository.getDraft(child.id,"editor","current")
        val importing=repository.getDraft(child.id,"import","current")
        update{it.copy(child=child,lists=lists,history=history,hasEditorDraft=editor!=null,hasImportDraft=importing!=null,message=if(repository.usedOfflineCache)"当前显示本机缓存，联网后可同步和导入新内容" else it.message)}
    }
    fun switchChild(child: Child)=perform("正在切换孩子"){
        player.pause();progressJobs.toList().forEach{it.join()}
        update{it.copy(practice=null)};player.reset();reviewSave?.join()
        loadChild(child);update{it.copy(route="PROFILE",currentList=null,review=null,reviewMarks=emptyMap(),reviewWritten=emptyMap(),catalogBook=null,catalogBooks=emptyList(),catalogEntries=emptyList(),catalogUnits=emptySet(),historyItems=emptyList(),historyFilter="ALL",historyPage=0,historyHasMore=false,wrongWords=emptyList(),recitationLesson=null,recitationAttempts=emptyList(),recitationSaved=null,message="已切换为${child.nickname}")}
    }
    fun openChild(child: Child?){player.pause();update{it.copy(route="CHILD",editingChild=child)}}
    fun saveChild(name: String,grade: Int,book: String)=perform("正在保存档案"){
        require(name.isNotBlank()){ "请输入孩子昵称" }
        val child=repository.saveChild(mutable.value.editingChild,name.trim(),grade,book.trim());reload();loadChild(child);update{it.copy(route="PROFILE",editingChild=null)}
    }
    fun home(){
        player.pause()
        update{it.copy(route=if(it.studySubject=="CHINESE")"CHINESE" else "HOME")}
    }
    fun profile(){player.pause();update{it.copy(route="PROFILE")}}
    fun subject(route: String){
        if(mutable.value.busy)return
        require(route in listOf("HOME","CHINESE","MATH","PROFILE"))
        player.pause()
        update{ current ->
            val subject=when(route) {
                "CHINESE" -> "CHINESE"
                "HOME" -> "ENGLISH"
                else -> current.studySubject
            }
            current.copy(route=route,studySubject=subject,message=null,
                historyFilter=if(subject==current.studySubject)current.historyFilter else "ALL")
        }
    }
    fun openStrokeOrder(){update{it.copy(route="STROKES",message=null)}}
    fun openRecitation()=perform("正在读取背诵课文"){
        val lessons=(repository.api.json("/recitation/lessons") as JSONArray).objects().map(RecitationLesson::from)
        update{it.copy(route="RECITATION",recitationLessons=lessons,recitationLesson=null,recitationSaved=null)}
    }
    fun openRecitationLesson(lesson: RecitationLesson)=perform("正在读取课文正文"){
        val detail=repository.api.json("/recitation/lessons/${lesson.id}") as JSONObject
        recitationKey=UUID.randomUUID().toString()
        update{it.copy(route="RECITATION_LESSON",recitationLesson=RecitationLesson.from(detail),recitationSaved=null)}
    }
    fun recordRecitation(result: String)=perform("正在保存背诵记录"){
        val s=mutable.value
        val child=s.child?:return@perform
        val lesson=s.recitationLesson?:return@perform
        if(s.recitationSaved!=null)return@perform
        val body=JSONObject().put("lessonId",lesson.id).put("lessonRevision",lesson.revision).put("result",result)
        val saved=repository.api.json("/children/${child.id}/recitation-attempts","POST",body,recitationKey) as JSONObject
        update{it.copy(recitationSaved=RecitationAttempt.from(saved))}
    }
    fun openRecitationHistory()=perform("正在读取背诵记录"){
        val child=mutable.value.child?:return@perform
        val attempts=(repository.api.json("/children/${child.id}/recitation-attempts") as JSONArray).objects().map(RecitationAttempt::from)
        update{it.copy(route="RECITATION_HISTORY",recitationAttempts=attempts)}
    }
    fun openTools(){player.pause();update{it.copy(route="TOOLS")}}
    fun openHistory(filter: String=mutable.value.historyFilter)=perform("正在查询听写记录"){
        val child=mutable.value.child?:return@perform
        syncMutex.withLock{
            val pending=repository.drafts.list(repository.cacheOwner,child.id,"outbox")
            for(item in pending)try{repository.flushProgress(child.id,item.key.substringAfterLast(':'))}catch(e:CancellationException){throw e}catch(_:java.io.IOException){message("部分进度尚未同步，联网后请刷新")}
        }
        update{it.copy(route="HISTORY",historyFilter=filter,historyItems=emptyList(),historyPage=0,historyHasMore=false)}
        loadHistory(0,false)
    }
    fun moreHistory()=perform("正在读取更多记录"){if(mutable.value.historyHasMore)loadHistory(mutable.value.historyPage+1,true)}
    private suspend fun loadHistory(page: Int,append: Boolean){
        val s=mutable.value;val child=s.child?:return
        val key=(if(s.studySubject=="CHINESE")"chinese_" else "")+"${s.historyFilter}_$page"
        val json=try{
            val result=repository.api.json("/children/${child.id}/dictation-history?filter=${s.historyFilter}&page=$page&size=20&subject=${s.studySubject}") as JSONObject
            repository.putDraft(child.id,"history",key,result);result
        }catch(e:java.io.IOException){if(e is ApiFailure)throw e;repository.getDraft(child.id,"history",key)?.also{message("当前显示本机历史缓存，联网后可刷新") }?:throw e}
        val items=json.getJSONArray("items").objects().map(HistoryItem::from).filter{it.practice.subject==s.studySubject}.map{item->
            val local=repository.getDraft(child.id,"playback",item.practice.id)
            val elapsed=maxOf(item.elapsedSeconds,((local?.optLong("elapsedMillis")?:0)/1000).toInt())
            item.copy(elapsedSeconds=elapsed)
        }
        update{it.copy(historyItems=(if(append)it.historyItems+items else items).distinctBy{r->r.practice.id},historyPage=page,historyHasMore=json.optBoolean("hasMore"))}
    }
    fun openServers(){if(mutable.value.busy)return;serverReturnRoute=if(repository.session.credentials==null)"LOGIN" else "PROFILE";player.pause();update{it.copy(route="SERVERS",servers=repository.servers.list(),serverUrl=repository.session.baseUrl)}}
    fun editServer(profile: ServerProfile?){if(mutable.value.busy)return;update{it.copy(route="SERVER_EDIT",editingServer=profile,message=null)}}
    fun saveServer(name: String,url: String)=perform("正在保存服务器"){
        val previous=mutable.value.editingServer
        // Validate both fields before persisting or touching the active session.
        val normalized=ServerAddresses.normalize(url,BuildConfig.DEBUG)
        ServerAddresses.save(repository.servers.list(),previous?.id,name,normalized)
        if(previous?.url==repository.session.baseUrl&&normalized!=repository.session.baseUrl){
            changeServer(normalized)
            repository.servers.save(previous.id,name,normalized)
            update{it.copy(servers=repository.servers.list(),message="服务器已修改，请重新登录")}
        }else{
            repository.servers.save(previous?.id,name,normalized)
            update{it.copy(route="SERVERS",editingServer=null,servers=repository.servers.list(),message="服务器已保存，请从列表选择")}
        }
    }
    fun removeServer(profile: ServerProfile)=perform("正在移除服务器地址"){
        repository.servers.remove(profile.id);update{it.copy(servers=repository.servers.list(),message="地址已移除，服务器上的数据不受影响")}
    }
    fun selectServer(profile: ServerProfile)=perform("正在切换服务器"){
        if(profile.url==repository.session.baseUrl){update{it.copy(route=serverReturnRoute)};return@perform}
        changeServer(profile.url)
    }
    private suspend fun changeServer(url: String){
        // Stop deferred writes before changing the endpoint; existing per-server drafts remain on disk.
        editorSave?.join();importSave?.join();reviewSave?.join()
        progressJobs.toList().forEach{it.cancel()};progressJobs.clear()
        update{it.copy(practice=null)};player.reset();pendingPhotos.clear()
        repository.switchServer(url)
        saveKey=UUID.randomUUID().toString();practiceKey=UUID.randomUUID().toString();importKey=UUID.randomUUID().toString()
        serverReturnRoute="LOGIN"
        update{initialState("LOGIN").copy(message="已切换服务器，请登录目标服务器的账号")}
    }
    fun refresh()=perform("正在刷新"){reload()}
    fun openList(list: WordList){update{it.copy(route="LIST",currentList=list,selectedWords=list.words.take(100).map{w->w.id}.toSet())};practiceKey=UUID.randomUUID().toString()}
    fun selectWords(all: Boolean){update{it.copy(selectedWords=if(all)it.currentList?.words?.take(100)?.map{w->w.id}?.toSet()?:emptySet() else emptySet())};practiceKey=UUID.randomUUID().toString()}
    fun toggleWord(id: String){update{it.copy(selectedWords=if(id in it.selectedWords)it.selectedWords-id else it.selectedWords+id)};practiceKey=UUID.randomUUID().toString()}
    fun newList() {saveKey=UUID.randomUUID().toString();update{it.copy(route="EDITOR",editor=EditorDraft())};persistEditor()}
    fun editList(){val l=mutable.value.currentList?:return;saveKey=UUID.randomUUID().toString();update{it.copy(route="EDITOR",editor=EditorDraft(l.title,l.words,l.id,l.version))};persistEditor()}
    fun resumeEditor()=perform("正在恢复草稿"){
        val child=mutable.value.child?:return@perform;val j=repository.getDraft(child.id,"editor","current")?:return@perform
        saveKey=j.optString("saveKey",UUID.randomUUID().toString());update{it.copy(route="EDITOR",editor=EditorDraft.from(j))}
    }
    fun editorTitle(value: String){saveKey=UUID.randomUUID().toString();update{it.copy(editor=it.editor.copy(title=value))};persistEditor()}
    fun editWord(index: Int,word: Word){saveKey=UUID.randomUUID().toString();update{it.copy(editor=it.editor.copy(words=it.editor.words.toMutableList().apply{this[index]=word}))};persistEditor()}
    fun addWord(){update{it.copy(editor=it.editor.copy(words=it.editor.words+Word("","","")))};persistEditor()}
    fun removeWord(index: Int){update{it.copy(editor=it.editor.copy(words=it.editor.words.filterIndexed{i,_->i!=index}))};persistEditor()}
    private var editorSave: Job?=null
    private fun persistEditor(){
        editorSave?.cancel();val s=mutable.value;val c=s.child?:return;val json=s.editor.json().put("saveKey",saveKey)
        editorSave=viewModelScope.launch{delay(200);repository.putDraft(c.id,"editor","current",json);update{it.copy(hasEditorDraft=true)}}
    }
    fun saveEditor()=perform("正在保存词表"){
        val s=mutable.value;val child=s.child?:return@perform
        val chosen=s.editor.words.filter{it.selected&&!(it.text.isBlank()&&it.meaning.isBlank())}
        require(s.editor.title.isNotBlank()){ "请输入词表名称" };require(chosen.isNotEmpty()){ "至少保留一个单词" }
        require(chosen.all{it.text.isNotBlank()&&it.meaning.isNotBlank()}){"请补齐每个单词和中文释义"}
        val list=repository.saveList(child.id,s.editor.copy(words=chosen),saveKey)
        editorSave?.cancel();repository.removeDraft(child.id,"editor","current");loadChild(child);openList(list);message("词表已保存")
    }
    fun openImport()=perform("正在打开学习图片"){
        val c=mutable.value.child?:return@perform;val j=repository.getDraft(c.id,"import","current")
        importKey=j?.optString("requestKey")?.takeIf{it.isNotBlank()}?:UUID.randomUUID().toString()
        update{it.copy(route="IMPORT",importFiles=j?.optJSONArray("files")?.let{arr->(0 until arr.length()).map{n->arr.getString(n)}.filter{path->File(path).exists()}}?:emptyList(),importType=j?.optString("type","WORD_TABLE")?:"WORD_TABLE",recognitionSource=j?.optString("source","LOCAL")?:"LOCAL",importTitle=j?.optString("title","英语词表")?:"英语词表")}
    }
    fun importType(type: String){if(mutable.value.busy)return;importKey=UUID.randomUUID().toString();update{it.copy(importType=type)};persistImport()}
    fun recognitionSource(source: String){if(mutable.value.busy)return;require(source in listOf("LOCAL","CLOUD"));importKey=UUID.randomUUID().toString();update{it.copy(recognitionSource=source)};persistImport()}
    fun importTitle(title: String){if(mutable.value.busy)return;importKey=UUID.randomUUID().toString();update{it.copy(importTitle=title.take(100))};persistImport()}
    fun camera(){update{it.copy(route="CAMERA")}}
    fun importUri(uri: Uri)=perform("正在准备图片"){
        require(mutable.value.importFiles.size<5){"单次最多导入5张图片"}
        val file=PhotoFiles.import(getApplication(),uri);update{it.copy(route="CROP",cropPath=file.path,cropIndex=null)}
    }
    fun importUris(uris: List<Uri>){
        val available=5-mutable.value.importFiles.size
        pendingPhotos.clear();pendingPhotos.addAll(uris.take(available))
        if(pendingPhotos.isNotEmpty())importUri(pendingPhotos.removeFirst()) else message("单次最多导入5张图片")
    }
    fun recrop(index: Int){update{it.copy(route="CROP",cropPath=it.importFiles[index],cropIndex=index)}}
    fun cropped(path: String){
        importKey=UUID.randomUUID().toString();update{it.copy(route="IMPORT",importFiles=it.importFiles.toMutableList().apply{if(it.cropIndex==null)add(path) else this[it.cropIndex]=path},cropPath=null,cropIndex=null)};persistImport()
        if(pendingPhotos.isNotEmpty())importUri(pendingPhotos.removeFirst())
    }
    fun removeImage(index: Int){importKey=UUID.randomUUID().toString();update{it.copy(importFiles=it.importFiles.filterIndexed{i,_->i!=index})};persistImport()}
    fun moveImage(index: Int){if(index==0)return;importKey=UUID.randomUUID().toString();update{it.copy(importFiles=it.importFiles.toMutableList().apply{val a=this[index];this[index]=this[index-1];this[index-1]=a})};persistImport()}
    private var importSave: Job?=null
    private fun persistImport(){
        importSave?.cancel();val s=mutable.value;val c=s.child?:return
        val json=JSONObject().put("requestKey",importKey).put("type",s.importType).put("source",s.recognitionSource).put("title",s.importTitle).put("files",JSONArray(s.importFiles))
        importSave=viewModelScope.launch{repository.putDraft(c.id,"import","current",json);update{it.copy(hasImportDraft=true)}}
    }
    fun recognizeLocal()=perform("正在本地识别，请稍候"){
        val s=mutable.value;val c=s.child?:return@perform
        require(s.recognitionSource=="LOCAL"){ "请先选择本地识别" }
        require(s.importTitle.isNotBlank()){ "请输入词表名称" }
        val result=LocalTextRecognizer().recognize(s.importFiles.map{File(it)},s.importType=="TEXTBOOK"){current,total->
            update{it.copy(busyText="正在本地识别第${current}/${total}张图片")}
        }
        val editor=EditorDraft(s.importTitle.trim(),result.words,localRecognition=true)
        saveKey=UUID.randomUUID().toString()
        editorSave?.cancelAndJoin();importSave?.cancelAndJoin()
        repository.putDraft(c.id,"editor","current",editor.json().put("saveKey",saveKey))
        repository.removeDraft(c.id,"import","current")
        val deviceSettings=s.settings.copy(voiceSource="DEVICE")
        repository.settings(deviceSettings);practiceKey=UUID.randomUUID().toString()
        update{it.copy(route=if(it.route=="IMPORT")"EDITOR" else it.route,editor=editor,settings=deviceSettings,hasEditorDraft=true,hasImportDraft=false,
            message=(if(result.truncated)"已保留前500个候选词，请分批识别。" else "本地识别完成。")+"请核对单词并补齐中文释义；已选择设备语音。")}
    }
    fun recognize(title: String)=perform("正在上传并识别，请稍候"){
        val s=mutable.value;val c=s.child?:return@perform;require(s.importFiles.isNotEmpty()){ "请先添加图片" };require(title.isNotBlank()){ "请输入词表名称" }
        require(s.recognitionSource=="CLOUD"){ "请先选择云端AI识别" };importSave?.cancelAndJoin()
        val draft=repository.getDraft(c.id,"import","current")
        val job=if(draft?.optString("requestKey")==importKey&&!draft.optString("jobId").isNullOrBlank())repository.awaitJob(c.id,draft!!.getString("jobId"))
            else repository.recognition(c.id,title.trim(),s.importType,s.importFiles.map{File(it)},importKey)
        val words=job.getJSONObject("result").getJSONArray("words").objects().map(Word::from)
        saveKey=UUID.randomUUID().toString();update{it.copy(route=if(it.route=="IMPORT")"EDITOR" else it.route,editor=EditorDraft(title.trim(),words,recognitionJobId=job.getString("id")),message=if(it.route=="IMPORT")null else "识别已完成，可从首页继续确认词表")}
        repository.putDraft(c.id,"editor","current",mutable.value.editor.json().put("saveKey",saveKey));repository.removeDraft(c.id,"import","current");update{it.copy(hasEditorDraft=true,hasImportDraft=false)}
    }
    fun settings(value: DictationSettings){val valid=value.copy(intervalSeconds=value.intervalSeconds.coerceIn(1,10));update{it.copy(settings=valid)};practiceKey=UUID.randomUUID().toString();viewModelScope.launch{repository.settings(valid)}}
    fun openSettings(){
        settingsReturnRoute=mutable.value.route
        update{it.copy(route="SETTINGS",settingsForChinese=settingsReturnRoute=="CATALOG"&&it.studySubject=="CHINESE",
            settings=it.settings.copy(intervalSeconds=it.settings.intervalSeconds.coerceIn(1,10)))}
    }
    fun openCatalog()=perform("正在读取导入词库"){
        val subject=mutable.value.studySubject
        val books=(repository.api.json("/catalog/collections?subject=$subject") as JSONArray).objects()
            .map(CatalogBook::from).filter{it.subject==subject}
        update{it.copy(catalogBooks=books,route="CATALOG",settings=it.settings.copy(intervalSeconds=it.settings.intervalSeconds.coerceIn(1,10),randomOrder=false))}
        val selected=books.firstOrNull{it.id==mutable.value.catalogBook?.id}?:books.firstOrNull()
        if(selected!=null)loadCatalog(selected) else update{it.copy(catalogBook=null,catalogEntries=emptyList(),catalogUnits=emptySet())}
    }
    private suspend fun loadCatalog(book: CatalogBook){
        val detail=repository.api.json("/catalog/collections/${book.id}") as JSONObject
        val entries=detail.getJSONArray("entries").objects().map(CatalogEntry::from)
        val grade=mutable.value.child?.grade?.takeIf{g->entries.any{it.grade==g}}?:entries.firstOrNull()?.grade?:3
        val semester=entries.firstOrNull{it.grade==grade}?.semester?:"上册"
        val units=CatalogSelection.units(entries,grade,semester)
        update{boundedCatalog(it.copy(catalogBook=book.copy(revision=detail.getJSONObject("collection").getInt("revision")),catalogEntries=entries,catalogGrade=grade,catalogSemester=semester,catalogUnits=if(it.catalogScope=="BOOK")units.toSet() else units.take(1).toSet()))}
        practiceKey=UUID.randomUUID().toString()
    }
    fun selectCatalog(book: CatalogBook)=perform("正在读取词表"){loadCatalog(book)}
    fun catalogFilter(grade: Int=mutable.value.catalogGrade,semester: String=mutable.value.catalogSemester){
        if(mutable.value.busy)return
        update{val units=CatalogSelection.units(it.catalogEntries,grade,semester);boundedCatalog(it.copy(catalogGrade=grade,catalogSemester=semester,catalogUnits=if(it.catalogScope=="BOOK")units.toSet() else units.take(1).toSet()))};practiceKey=UUID.randomUUID().toString()
    }
    private fun boundedCatalog(s: AppState): AppState {val count=CatalogSelection.eligible(s.catalogEntries,s.catalogGrade,s.catalogSemester,s.catalogUnits).size;return s.copy(catalogCount=s.catalogCount.coerceIn(1,count.coerceIn(1,500)))}
    fun catalogScope(scope: String){if(mutable.value.busy)return;require(scope in listOf("UNIT","BOOK"));update{val units=CatalogSelection.units(it.catalogEntries,it.catalogGrade,it.catalogSemester);boundedCatalog(it.copy(catalogScope=scope,catalogUnits=if(scope=="BOOK")units.toSet() else units.take(1).toSet()))};practiceKey=UUID.randomUUID().toString()}
    fun catalogSingleUnit(unit: String){if(mutable.value.busy)return;update{boundedCatalog(it.copy(catalogUnits=setOf(unit)))};practiceKey=UUID.randomUUID().toString()}
    fun catalogUnit(unit: String,all: Boolean?=null){if(mutable.value.busy)return;update{it.copy(catalogUnits=if(all!=null){if(all)CatalogSelection.units(it.catalogEntries,it.catalogGrade,it.catalogSemester).toSet() else emptySet()} else if(unit in it.catalogUnits)it.catalogUnits-unit else it.catalogUnits+unit)};practiceKey=UUID.randomUUID().toString()}
    fun catalogSelection(value: String){update{val size=CatalogSelection.eligible(it.catalogEntries,it.catalogGrade,it.catalogSemester,it.catalogUnits).size;it.copy(catalogSelection=value,catalogCount=it.catalogCount.coerceAtMost(size.coerceAtLeast(1)))};practiceKey=UUID.randomUUID().toString()}
    fun catalogCount(value: Int){update{it.copy(catalogCount=value.coerceIn(1,500))};practiceKey=UUID.randomUUID().toString()}
    fun startCatalogPractice()=perform("正在建立词库听写"){
        val s=mutable.value;val child=s.child?:return@perform;val book=s.catalogBook?:return@perform
        val available=CatalogSelection.eligible(s.catalogEntries,s.catalogGrade,s.catalogSemester,s.catalogUnits)
        require(available.isNotEmpty()){ "请至少选择一个有单词的单元" }
        require(if(s.catalogSelection=="RANDOM")s.catalogCount<=available.size else available.size<=500){"每次词库听写最多500词，抽取数量不能超过可用词数，请调整范围"}
        val chinese=s.studySubject=="CHINESE"
        val settings=s.settings.copy(mode=if(chinese)"CHINESE" else s.settings.mode,
            voiceSource="DEVICE",intervalSeconds=s.settings.intervalSeconds.coerceIn(1,10))
        val body=JSONObject().put("collectionId",book.id).put("expectedRevision",book.revision).put("grade",s.catalogGrade).put("semester",s.catalogSemester).put("units",JSONArray(if(s.catalogScope=="BOOK")emptyList<String>() else s.catalogUnits.sorted())).put("selection",s.catalogSelection).put("count",s.catalogCount).put("settings",settings.json())
        val json=repository.api.json("/children/${child.id}/catalog-practices","POST",body,practiceKey) as JSONObject
        repository.putDraft(child.id,"practice",json.getString("id"),json)
        val practice=Practice.from(json).copy(settings=settings)
        if(!chinese)repository.settings(settings)
        update{it.copy(practice=practice,settings=if(chinese)it.settings else settings,route="PLAYER")}
        player.prepare(child.id,practice,"DEVICE")
    }
    fun openReview(practice: Practice)=perform("正在读取听写答案与用时"){
        reviewReturnRoute=if(mutable.value.route=="HISTORY")"HISTORY" else "HOME"
        val child=mutable.value.child?:return@perform
        syncMutex.withLock{repository.flushProgress(child.id,practice.id)}
        val local=repository.getDraft(child.id,"playback",practice.id)
        val seconds=((local?.optLong("elapsedMillis")?:0)/1000).toInt().coerceIn(0,604800)
        val json=repository.api.json("/children/${child.id}/practices/${practice.id}/timing","POST",JSONObject().put("elapsedSeconds",seconds)) as JSONObject
        val review=ManualReview.from(json)
        val draft=repository.getDraft(child.id,"grading",practice.id)
        val marks=if(review.graded)review.marks else draft?.optJSONObject("marks")?.let{obj->obj.keys().asSequence().associateWith{obj.getBoolean(it)}}?:emptyMap()
        val written=if(review.graded)review.written else draft?.optJSONObject("written")?.let{obj->obj.keys().asSequence().associateWith{obj.getString(it)}}?:emptyMap()
        update{it.copy(route="REVIEW",studySubject=practice.subject,review=review,reviewMarks=marks,reviewWritten=written)}
    }
    fun reviewMark(key: String,correct: Boolean){if(mutable.value.busy||mutable.value.review?.graded==true)return;update{it.copy(reviewMarks=it.reviewMarks+(key to correct))};persistReview()}
    fun reviewWritten(key: String,value: String){if(mutable.value.busy||mutable.value.review?.graded==true)return;update{it.copy(reviewWritten=it.reviewWritten+(key to value.take(100)))};persistReview()}
    private fun persistReview(){val s=mutable.value;val child=s.child?:return;val review=s.review?:return
        reviewSave?.cancel();reviewSave=viewModelScope.launch{repository.putDraft(child.id,"grading",review.practiceId,JSONObject().put("marks",JSONObject(s.reviewMarks)).put("written",JSONObject(s.reviewWritten)))}
    }
    fun submitReview()=perform("正在保存成绩与错词"){
        val s=mutable.value;val child=s.child?:return@perform;val review=s.review?:return@perform
        require(s.reviewMarks.size==review.total){"请逐题标记每一轮的对错后再提交"}
        val marks=JSONArray();repeat(review.rounds){round->review.words.indices.forEach{index->val key="$round:$index";require(s.reviewMarks.containsKey(key)){"仍有题目未批改"};marks.put(JSONObject().put("round",round).put("index",index).put("correct",s.reviewMarks.getValue(key)).put("written",s.reviewWritten[key]?:""))}}
        val json=repository.api.json("/children/${child.id}/practices/${review.practiceId}/review","POST",JSONObject().put("elapsedSeconds",review.elapsedSeconds).put("marks",marks)) as JSONObject
        reviewSave?.cancelAndJoin();repository.removeDraft(child.id,"grading",review.practiceId)
        val result=ManualReview.from(json);update{it.copy(review=result,reviewMarks=result.marks,reviewWritten=result.written,message="批改已保存，错词已自动整理入库")}
    }
    fun openWrongWords()=perform("正在读取错词本"){
        val s=mutable.value
        val child=s.child?:return@perform
        val words=(repository.api.json("/children/${child.id}/wrong-words?subject=${s.studySubject}") as JSONArray)
            .objects().filter{it.optString("subject","ENGLISH")==s.studySubject}.map(WrongWord::from)
        update{it.copy(route="WRONG_WORDS",wrongWords=words)}
    }
    fun wrongMastered(word: WrongWord)=perform("正在更新掌握状态"){val child=mutable.value.child?:return@perform;repository.api.json("/children/${child.id}/wrong-words/${word.id}","PATCH",JSONObject().put("mastered",!word.mastered));update{it.copy(wrongWords=it.wrongWords.map{w->if(w.id==word.id)w.copy(mastered=!word.mastered) else w})}}
    fun openLookup(){update{it.copy(route="LOOKUP",lookupWord=null)}}
    fun lookup(text: String)=perform("正在查询单词释义"){
        val c=mutable.value.child?:return@perform;require(text.isNotBlank()){ "请输入英文单词或短语" }
        val job=repository.api.json("/children/${c.id}/word-lookups","POST",JSONObject().put("text",text.trim())) as JSONObject
        val ready=repository.awaitJob(c.id,job.getString("id"));val word=Word.from(ready.getJSONObject("result").getJSONObject("word"));update{it.copy(lookupWord=word)}
    }
    fun saveLookup(){val word=mutable.value.lookupWord?:return;saveKey=UUID.randomUUID().toString();update{it.copy(route="EDITOR",editor=EditorDraft("新词学习",listOf(word)))};persistEditor()}
    fun startPractice()=perform("正在建立听写任务"){
        val s=mutable.value;val c=s.child?:return@perform;val l=s.currentList?:return@perform
        require(s.selectedWords.isNotEmpty()){ "请至少选择一个单词" }
        require(s.selectedWords.size<=100){ "每次听写最多100个词，请分批练习" }
        val settings=s.settings.copy(intervalSeconds=s.settings.intervalSeconds.coerceIn(1,10))
        val p=repository.createPractice(c.id,l,s.selectedWords,settings,practiceKey);update{it.copy(practice=p,studySubject=p.subject,settings=settings,route="PLAYER")};player.prepare(c.id,p,settings.voiceSource)
    }
    fun resumePractice(practice: Practice)=perform("正在恢复听写"){
        val c=mutable.value.child?:return@perform
        try{repository.flushProgress(c.id,practice.id)}catch(e: ApiFailure){if(e.status==409){update{it.copy(conflictPractice=practice)};return@perform}else throw e}catch(_:java.io.IOException){}
        val local=repository.getDraft(c.id,"playback",practice.id)
        val chinese=practice.subject=="CHINESE"
        val source=if(chinese)"DEVICE" else local?.optString("source")?:mutable.value.settings.voiceSource
        update{it.copy(practice=practice,studySubject=practice.subject,route="PLAYER",
            settings=if(chinese)it.settings else practice.settings.copy(voiceSource=source))}
        player.prepare(c.id,practice,source,local?.optInt("round")?:practice.round,local?.optInt("itemIndex")?:practice.itemIndex,local?.optLong("elapsedMillis")?:0)
    }
    fun dismissConflict(){update{it.copy(conflictPractice=null)}}
    fun useServerProgress()=perform("正在读取服务器进度"){
        val c=mutable.value.child?:return@perform;val old=mutable.value.conflictPractice?:return@perform
        val latest=Practice.from(repository.api.json("/children/${c.id}/practices/${old.id}") as JSONObject)
        repository.removeDraft(c.id,"outbox",old.id);repository.removeDraft(c.id,"playback",old.id)
        update{it.copy(conflictPractice=null,practice=latest,studySubject=latest.subject,route="PLAYER")}
        if(latest.status in listOf("COMPLETED","STOPPED")){home();loadChild(c)}
        else player.prepare(c.id,latest,if(latest.subject=="CHINESE")"DEVICE" else mutable.value.settings.voiceSource)
    }
    fun prepareAgain(source: String?=null){val s=mutable.value;val c=s.child?:return;val p=s.practice?:return
        val voice=if(p.subject=="CHINESE")"DEVICE" else source?:s.settings.voiceSource
        val current=player.state.value
        if(p.subject!="CHINESE")update{it.copy(settings=it.settings.copy(voiceSource=voice))}
        player.prepare(c.id,p,voice,current.round,current.itemIndex,current.elapsedMillis)
    }
    fun pronounce(word: Word,meaning: Boolean=false)=perform("正在准备读音"){
        val c=mutable.value.child?:return@perform;val file=speech.audio(c.id,if(meaning)word.meaning else word.text,if(meaning)"zh-CN" else "en-US",mutable.value.settings.voiceSource)
        player.preview(file)
    }
    private fun syncProgress(practiceId: String,phase: String,round: Int,index: Int){val epoch=repository.session.epoch;val task=viewModelScope.launch {
        syncMutex.withLock {
            val s=mutable.value;val p=s.practice?:return@withLock;val c=s.child?:return@withLock
            if(p.id!=practiceId)return@withLock
            val events=player.pendingEvents();val status=when(phase){"COMPLETED"->"COMPLETED";"STOPPED"->"STOPPED";else->"PAUSED"}
            try{val updated=repository.saveProgress(c.id,p,status,round,index,events);player.acknowledgeEvents(events.length());if(mutable.value.practice?.id==p.id)update{it.copy(practice=updated)};val local=repository.getDraft(c.id,"playback",p.id);repository.api.json("/children/${c.id}/practices/${p.id}/timing","POST",JSONObject().put("elapsedSeconds",((local?.optLong("elapsedMillis")?:0)/1000).toInt().coerceIn(0,604800)))}
            catch(e: CancellationException){throw e}
            catch(e: Exception){if(repository.session.epoch==epoch)message(if(e is ApiFailure&&e.status==409)e.message else "听写进度已保存在本机，联网后会继续同步")}
        }
    };progressJobs.add(task);task.invokeOnCompletion{viewModelScope.launch{progressJobs.remove(task)}}}
    fun back(){when(mutable.value.route){"REVIEW"->if(reviewReturnRoute=="HISTORY")openHistory() else home();"SERVERS"->update{it.copy(route=serverReturnRoute)};"SERVER_EDIT"->update{it.copy(route="SERVERS",editingServer=null,message=null)};"CAMERA","CROP"->update{it.copy(route="IMPORT")};"PLAYER"->{player.pause();home()};"SETTINGS"->update{it.copy(route=settingsReturnRoute)};"RECITATION_LESSON","RECITATION_HISTORY"->update{it.copy(route="RECITATION",message=null)};"EDITOR"->{persistEditor();home()};else->home()}}
    fun logout()=perform("正在退出登录"){player.stop();try{repository.logout()}catch(_:Exception){};update{initialState("LOGIN")}}
    override fun onCleared(){player.close();speech.close();repository.close();super.onCleared()}
}
