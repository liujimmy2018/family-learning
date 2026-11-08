package cn.family.learning.playback

/** Monotonic active duration; pause/background/voice preparation do not advance the clock. */
class PracticeClock(private val now: () -> Long) {
    private var accumulated=0L
    private var started: Long?=null
    fun restore(milliseconds: Long){accumulated=milliseconds.coerceIn(0,604800000L);started=null}
    fun start(){if(started==null)started=now()}
    fun pause(){accumulated=elapsed();started=null}
    fun elapsed(): Long=(accumulated+(started?.let{(now()-it).coerceAtLeast(0)}?:0)).coerceAtMost(604800000L)
}
