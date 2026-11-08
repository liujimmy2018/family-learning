package cn.family.learning.playback
import org.junit.Assert.*
import org.junit.Test
class PracticeClockTest {
    @Test fun countsActiveTimeAndExcludesPauses(){var now=0L;val clock=PracticeClock{now};clock.start();now=4000;assertEquals(4000,clock.elapsed());clock.pause();now=64000;assertEquals(4000,clock.elapsed());clock.start();now=68000;clock.pause();assertEquals(8000,clock.elapsed())}
    @Test fun restoreContinuesDurationWithoutCountingPreparation(){var now=500L;val clock=PracticeClock{now};clock.restore(15000);now=10000;assertEquals(15000,clock.elapsed());clock.start();now=12000;assertEquals(17000,clock.elapsed());clock.restore(0);assertEquals(0,clock.elapsed())}
    @Test fun repeatedStartDoesNotResetTheStartTime(){var now=0L;val clock=PracticeClock{now};clock.start();now=2000;clock.start();now=3000;assertEquals(3000,clock.elapsed())}
}
