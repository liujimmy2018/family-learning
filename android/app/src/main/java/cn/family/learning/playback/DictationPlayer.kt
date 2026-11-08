package cn.family.learning.playback

import cn.family.learning.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.ceil

data class PlaybackUi(val phase: String="IDLE",val round: Int=0,val itemIndex: Int=0,val remaining: Int=0,val prepared: Int=0,val total: Int=0,val error: String?=null,val practiceId: String="",val elapsedMillis: Long=0)
class DictationPlayer(private val scope: CoroutineScope,private val speech: SpeechSource,private val output: AudioOutput,private val monotonicMillis: ()->Long) {
    private val mutable=MutableStateFlow(PlaybackUi());val state=mutable.asStateFlow()
    private var running: Job?=null
    private var preparation: Job?=null
    private var cursor: DictationCursor?=null
    private var practice: Practice?=null
    private var files: List<File> = emptyList()
    private var previewing=false
    private val events=mutableListOf<JSONObject>()
    private val clock=PracticeClock(monotonicMillis)
    private var ticker: Job?=null
    private fun startClock(){clock.start();ticker?.cancel();ticker=scope.launch{while(isActive){mutable.value=mutable.value.copy(elapsedMillis=clock.elapsed());delay(1000)}}}
    private fun stopClock(){clock.pause();ticker?.cancel();mutable.value=mutable.value.copy(elapsedMillis=clock.elapsed())}
    init {output.onFocusLost={pause()}}
    fun prepare(child: String,p: Practice,source: String,resumeRound: Int=p.round,resumeIndex: Int=p.itemIndex,elapsedMillis: Long=0) {
        ticker?.cancel();clock.restore(elapsedMillis)
        running?.cancel();preparation?.cancel();output.stop();previewing=false;files=emptyList();practice=p;events.clear()
        cursor=DictationCursor(p.words.size,p.settings.rounds,resumeRound,resumeIndex)
        mutable.value=PlaybackUi("PREPARING",resumeRound,resumeIndex,total=p.words.size,practiceId=p.id,elapsedMillis=elapsedMillis)
        preparation=scope.launch {
            try {
                files=p.words.mapIndexed{index,w->
                    val englishPrompt=p.subject!="CHINESE"&&p.settings.mode=="ENGLISH"
                    val language=if(englishPrompt)"en-US" else "zh-CN"
                    val prompt=if(p.subject=="CHINESE"||englishPrompt)w.text else w.meaning
                    val file=speech.audio(child,prompt,language,source)
                    mutable.value=mutable.value.copy(prepared=index+1);file
                }
                mutable.value=mutable.value.copy(phase="READY")
            }catch(e: CancellationException){throw e}catch(e: Exception){mutable.value=mutable.value.copy(phase="ERROR",error=e.message?:"音频准备失败")}
        }
    }
    fun start(){if(mutable.value.phase=="READY")launchLoop(true)}
    fun resume(){if(mutable.value.phase=="PAUSED"){event("RESUME");launchLoop(false)}}
    fun pause(){
        if(mutable.value.phase in listOf("COUNTDOWN","PLAYING","WAITING")){
            stopClock()
            running?.cancel();output.stop();event("PAUSE");mutable.value=mutable.value.copy(phase="PAUSED",remaining=0)
        }
    }
    fun background(){
        if(previewing){output.pause();previewing=false}
        if(mutable.value.phase=="PREPARING"){preparation?.cancel();speech.stop();mutable.value=mutable.value.copy(phase="ERROR",error="音频准备已暂停，请重新准备")}
        pause()
    }
    fun next(){navigate("NEXT"){it.next()}}
    fun previous(){navigate("PREVIOUS"){it.previous();true}}
    fun replay(){
        if(mutable.value.phase in listOf("READY","PAUSED")&&files.isNotEmpty()){
            val p=practice?:return;val c=cursor?:return
            running?.cancel();output.stop();event("REPLAY");event("PLAY")
            startClock()
            running=scope.launch{
                try{
                    mutable.value=mutable.value.copy(phase="PLAYING")
                    repeat(p.settings.repeats){i->play(files[c.index]);if(i+1<p.settings.repeats)waitFor(p.settings.repeatGapSeconds,"PLAYING")}
                    stopClock();mutable.value=mutable.value.copy(phase="PAUSED",remaining=0)
                }catch(e: CancellationException){throw e}catch(e:Exception){stopClock();mutable.value=mutable.value.copy(phase="PAUSED",error="重播失败，请重试")}
            }
        }else navigate("REPLAY"){true}
    }
    fun preview(file: File){pause();output.stop();previewing=true;output.preview(file)}
    private fun navigate(action: String,move: (DictationCursor)->Boolean){
        if(files.isEmpty()||mutable.value.phase in listOf("COMPLETED","STOPPED","ERROR","PREPARING"))return
        val c=cursor?:return;val wasPaused=mutable.value.phase in listOf("PAUSED","READY")
        running?.cancel();output.stop();event(action)
        if(!move(c)){finish();return}
        mutable.value=mutable.value.copy(round=c.round,itemIndex=c.index,remaining=0)
        if(wasPaused)mutable.value=mutable.value.copy(phase="PAUSED") else launchLoop(false)
    }
    fun stop(){stopClock();running?.cancel();preparation?.cancel();output.stop();previewing=false;speech.stop();event("STOP");mutable.value=mutable.value.copy(phase="STOPPED",remaining=0)}
    fun reset(){ticker?.cancel();clock.restore(0);running?.cancel();preparation?.cancel();output.stop();speech.stop();previewing=false;files=emptyList();events.clear();practice=null;cursor=null;mutable.value=PlaybackUi()}
    private fun launchLoop(countdown: Boolean){
        running?.cancel();val p=practice?:return;val c=cursor?:return
        startClock()
        running=scope.launch {
            try {
                if(countdown)waitFor(p.settings.countdownSeconds.toDouble(),"COUNTDOWN")
                while(isActive){
                    mutable.value=mutable.value.copy(phase="PLAYING",round=c.round,itemIndex=c.index,remaining=0);event("PLAY")
                    repeat(p.settings.repeats){i->play(files[c.index]);if(i+1<p.settings.repeats)waitFor(p.settings.repeatGapSeconds,"PLAYING")}
                    waitFor(p.settings.intervalSeconds.toDouble(),"WAITING")
                    if(!c.next()){finish();break}
                }
            }catch(e: CancellationException){throw e}catch(e: Exception){stopClock();mutable.value=mutable.value.copy(phase="PAUSED",error=e.message?:"播放失败，请重试")}
        }
    }
    private suspend fun waitFor(seconds: Double,phase: String){
        val deadline=monotonicMillis()+(seconds*1000).toLong()
        while(true){val remaining=deadline-monotonicMillis();if(remaining<=0)break
            mutable.value=mutable.value.copy(phase=phase,remaining=ceil(remaining/1000.0).toInt());delay(minOf(remaining,100))
        }
    }
    private suspend fun play(file: File)=output.play(file)
    private fun event(action: String){val c=cursor?:return;events.add(JSONObject().put("eventId",UUID.randomUUID().toString()).put("action",action).put("round",c.round).put("itemIndex",c.index).put("occurredAt",System.currentTimeMillis()))}
    private fun finish(){stopClock();event("COMPLETE");mutable.value=mutable.value.copy(phase="COMPLETED",remaining=0)}
    fun pendingEvents()=JSONArray(events.map{JSONObject(it.toString())})
    fun acknowledgeEvents(count: Int){repeat(minOf(count,events.size)){events.removeAt(0)}}
    fun close(){ticker?.cancel();running?.cancel();preparation?.cancel();output.close()}
}
