package cn.family.learning.playback

/** Only navigation belongs here; audio duration never consumes the writing interval. */
class DictationCursor(val wordCount: Int,val roundCount: Int,round: Int=0,index: Int=0) {
    var round: Int=round; private set
    var index: Int=index; private set
    var completed: Boolean=false; private set
    init { require(wordCount>0&&roundCount in 1..3&&round in 0 until roundCount&&index in 0 until wordCount) }
    fun next(): Boolean {
        if(index+1<wordCount){index++;return true}
        if(round+1<roundCount){round++;index=0;return true}
        completed=true;return false
    }
    fun previous() {
        completed=false
        if(index>0)index-- else if(round>0){round--;index=wordCount-1}
    }
}
