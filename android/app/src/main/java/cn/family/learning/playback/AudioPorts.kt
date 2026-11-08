package cn.family.learning.playback

import java.io.File

interface SpeechSource {
    suspend fun audio(child: String,text: String,language: String,source: String): File
    fun stop()
}

interface AudioOutput {
    var onFocusLost: (() -> Unit)?
    suspend fun play(file: File)
    fun preview(file: File)
    fun stop()
    fun pause()
    fun close()
}
