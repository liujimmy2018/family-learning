package cn.family.learning.playback

import cn.family.learning.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DictationPlayerTest {
    private class FakeSpeech: SpeechSource {
        val prompts=mutableListOf<Pair<String,String>>()
        override suspend fun audio(child: String,text: String,language: String,source: String): File {
            prompts.add(text to language)
            return File(text)
        }
        override fun stop(){}
    }
    private class FakeOutput(val duration: Long): AudioOutput {
        override var onFocusLost: (() -> Unit)?=null
        val played=mutableListOf<String>()
        override suspend fun play(file: File){played.add(file.name);delay(duration)}
        override fun preview(file: File){}
        override fun stop(){}
        override fun pause(){}
        override fun close(){}
    }
    private fun practice(settings: DictationSettings=DictationSettings(countdownSeconds=0,intervalSeconds=3,repeats=2,repeatGapSeconds=1.0))=Practice("1","test",listOf(Word("a","apple","苹果"),Word("b","book","书")),settings,"READY",1,0,0)
    private fun TestScope.engine(output: FakeOutput)=DictationPlayer(this,FakeSpeech(),output,{testScheduler.currentTime})

    @Test fun writingIntervalStartsAfterAllRepeatedAudioFinishes()=runTest {
        val output=FakeOutput(2000);val player=engine(output)
        player.prepare("1",practice(),"DEVICE");runCurrent();player.start();runCurrent()
        advanceTimeBy(4999);runCurrent();assertEquals("PLAYING",player.state.value.phase)
        advanceTimeBy(1);runCurrent();assertEquals("WAITING",player.state.value.phase);assertEquals(3,player.state.value.remaining)
        advanceTimeBy(2999);runCurrent();assertEquals(0,player.state.value.itemIndex)
        advanceTimeBy(1);runCurrent();assertEquals(1,player.state.value.itemIndex);assertEquals("PLAYING",player.state.value.phase)
        player.close()
    }
    @Test fun pausingNeverAdvancesWithWallTimeAndResumeReplaysCurrentWord()=runTest {
        val output=FakeOutput(2000);val player=engine(output)
        player.prepare("1",practice(),"DEVICE");runCurrent();player.start();runCurrent();advanceTimeBy(6000);runCurrent();player.pause()
        advanceTimeBy(60000);runCurrent();assertEquals("PAUSED",player.state.value.phase);assertEquals(0,player.state.value.itemIndex)
        val before=output.played.size;player.resume();runCurrent();assertEquals(before+1,output.played.size);assertEquals("apple",output.played.last())
        player.close()
    }
    @Test fun replayWhilePausedPlaysAudioWithoutAutomaticallyMovingToNextWord()=runTest {
        val output=FakeOutput(100);val player=engine(output)
        player.prepare("1",practice(),"DEVICE");runCurrent();player.replay();advanceUntilIdle()
        assertEquals(listOf("apple","apple"),output.played);assertEquals("PAUSED",player.state.value.phase);assertEquals(0,player.state.value.itemIndex)
        player.close()
    }
    @Test fun allRoundsReuseTheFixedWordOrderAndStopAtFinalItem()=runTest {
        val output=FakeOutput(100);val player=engine(output)
        player.prepare("1",practice(DictationSettings(countdownSeconds=0,intervalSeconds=3,repeats=1,rounds=2)),"DEVICE");runCurrent();player.start();advanceUntilIdle()
        assertEquals(listOf("apple","book","apple","book"),output.played);assertEquals("COMPLETED",player.state.value.phase);assertEquals(1,player.state.value.round);assertEquals(1,player.state.value.itemIndex)
        player.close()
    }
    @Test fun losingAudioFocusPausesInsteadOfSkippingWords()=runTest {
        val output=FakeOutput(2000);val player=engine(output)
        player.prepare("1",practice(),"DEVICE");runCurrent();player.start();runCurrent();output.onFocusLost?.invoke();advanceTimeBy(30000);runCurrent()
        assertEquals("PAUSED",player.state.value.phase);assertEquals(0,player.state.value.itemIndex);player.close()
    }
    @Test fun chineseModePreparesMeaningsRatherThanEnglishWords()=runTest {
        val output=FakeOutput(100);val player=engine(output)
        player.prepare("1",practice(DictationSettings(mode="CHINESE",countdownSeconds=0,intervalSeconds=3,repeats=1)),"DEVICE");runCurrent();player.start();advanceUntilIdle()
        assertEquals(listOf("苹果","书"),output.played);player.close()
    }
    @Test fun chineseSubjectSpeaksHanziInsteadOfPinyinOrTheEnglishPromptMode()=runTest {
        val speech=FakeSpeech()
        val player=DictationPlayer(this,speech,FakeOutput(100),{testScheduler.currentTime})
        val practice=practice().copy(subject="CHINESE",words=listOf(Word("a","春天","chūn tiān",phonetic="chūn tiān")))
        player.prepare("1",practice,"DEVICE")
        runCurrent()
        assertEquals(listOf("春天" to "zh-CN"),speech.prompts)
        assertEquals("READY",player.state.value.phase)
        player.close()
    }
}
