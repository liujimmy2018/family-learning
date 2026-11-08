package cn.family.learning.playback

import org.junit.Assert.*
import org.junit.Test

class DictationCursorTest {
    @Test fun multipleRoundsPreserveItemOrder(){
        val c=DictationCursor(3,2);val visited=mutableListOf<Pair<Int,Int>>()
        do{visited.add(c.round to c.index)}while(c.next())
        assertEquals(listOf(0 to 0,0 to 1,0 to 2,1 to 0,1 to 1,1 to 2),visited)
        assertTrue(c.completed);assertEquals(1,c.round);assertEquals(2,c.index)
    }
    @Test fun previousCrossesRoundBoundary(){val c=DictationCursor(4,3,1,0);c.previous();assertEquals(0,c.round);assertEquals(3,c.index)}
    @Test fun resumeDoesNotAdvanceBecauseTimePassed(){val c=DictationCursor(5,2,1,3);assertEquals(1,c.round);assertEquals(3,c.index);c.previous();assertEquals(2,c.index)}
    @Test fun firstItemPreviousStaysInBounds(){val c=DictationCursor(1,1);c.previous();assertEquals(0,c.index);assertFalse(c.next())}
    @Test(expected=IllegalArgumentException::class) fun rejectsProgressOutsideSnapshot(){DictationCursor(2,1,0,2)}
}
