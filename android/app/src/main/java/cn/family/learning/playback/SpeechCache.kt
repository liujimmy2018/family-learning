package cn.family.learning.playback

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import cn.family.learning.data.*
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpeechCache(context: Context,private val repository: LearningRepository): SpeechSource {
    private val ready=CompletableDeferred<Unit>()
    private val pending=ConcurrentHashMap<String,CompletableDeferred<Unit>>()
    private val tts=TextToSpeech(context){status->if(status==TextToSpeech.SUCCESS)ready.complete(Unit) else ready.completeExceptionally(IllegalStateException("设备语音不可用，请使用云端语音"))}
    init {
        tts.setOnUtteranceProgressListener(object: UtteranceProgressListener(){
            override fun onStart(id: String?){}
            override fun onDone(id: String?){pending.remove(id)?.complete(Unit)}
            @Deprecated("Platform callback") override fun onError(id: String?){pending.remove(id)?.completeExceptionally(IllegalStateException("设备语音生成失败，请检查语音包"))}
            override fun onError(id: String?,errorCode: Int){onError(id)}
        })
    }
    override suspend fun audio(child: String,text: String,language: String,source: String): File {
        val digest=MessageDigest.getInstance("SHA-256").digest("v1|$source|$language|$text".toByteArray()).joinToString(""){"%02x".format(it)}
        val file=File(repository.context.cacheDir,"audio/${repository.cacheOwner}/$child/$digest.audio")
        if(file.exists()&&file.length()>44)return file
        file.parentFile?.mkdirs()
        if(source=="CLOUD")repository.cloudAudio(child,text,language,file) else synthesize(text,language,file)
        return file
    }
    private suspend fun synthesize(text: String,language: String,file: File) {
        withTimeout(15000){ready.await()}
        val locale=if(language=="en-US")Locale.US else Locale.SIMPLIFIED_CHINESE
        if(tts.isLanguageAvailable(locale)<TextToSpeech.LANG_AVAILABLE)throw IllegalStateException("设备未安装${if(language=="en-US")"英语" else "中文"}语音，请安装语音包后重试")
        tts.language=locale
        val voice=tts.voices?.firstOrNull{it.locale.language==locale.language&&!it.isNetworkConnectionRequired}
            ?:throw IllegalStateException("设备未安装可离线使用的${if(language=="en-US")"英语" else "中文"}语音包，请安装后重试")
        tts.voice=voice
        val id=UUID.randomUUID().toString();val done=CompletableDeferred<Unit>();val temp=File(file.path+".partial");pending[id]=done
        try {
            if(tts.synthesizeToFile(text,Bundle(),temp,id)!=TextToSpeech.SUCCESS)throw IllegalStateException("设备语音生成失败")
            withTimeout(45000){done.await()}
            withContext(Dispatchers.IO){if(temp.length()<44||!temp.renameTo(file))throw IllegalStateException("音频保存失败")}
        }finally{pending.remove(id);if(temp.exists())temp.delete()}
    }
    override fun stop(){tts.stop();pending.values.forEach{it.cancel()};pending.clear()}
    fun close(){stop();tts.shutdown()}
}
