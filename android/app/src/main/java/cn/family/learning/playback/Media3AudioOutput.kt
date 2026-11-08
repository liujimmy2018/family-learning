package cn.family.learning.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class Media3AudioOutput(context: Context): AudioOutput {
    override var onFocusLost: (() -> Unit)?=null
    private val player=ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),true)
        addListener(object: Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean,reason: Int){
                if(!playWhenReady&&reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)onFocusLost?.invoke()
            }
        })
    }
    override suspend fun play(file: File)=suspendCancellableCoroutine<Unit>{continuation->
        val listener=object: Player.Listener{
            override fun onPlaybackStateChanged(state: Int){if(state==Player.STATE_ENDED&&continuation.isActive){player.removeListener(this);continuation.resume(Unit)}}
            override fun onPlayerError(error: PlaybackException){player.removeListener(this);if(continuation.isActive)continuation.resumeWithException(IOException("音频播放失败，请重播当前词"))}
        }
        player.addListener(listener);preview(file)
        continuation.invokeOnCancellation{player.removeListener(listener)}
    }
    override fun preview(file: File){player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));player.prepare();player.play()}
    override fun stop(){player.stop()}
    override fun pause(){player.pause()}
    override fun close(){player.release()}
}
