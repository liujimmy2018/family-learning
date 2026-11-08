package cn.family.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.family.learning.data.*
import java.util.Locale

fun durationText(seconds: Int): String=String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60)

@Composable fun ReviewPage(s: AppState,vm: AppViewModel){
    val review=s.review?:return
    val chinese=s.studySubject=="CHINESE"
    var confirming by remember{mutableStateOf(false)}
    val listState=rememberLazyListState()
    LaunchedEffect(review.graded){if(review.graded)listState.animateScrollToItem(0)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),state=listState,verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)){
        item{Text(review.title,style=MaterialTheme.typography.titleLarge);Text("有效练习用时 ${durationText(review.elapsedSeconds)} · 共${review.rounds}轮 / ${review.total}题")}
        if(review.graded){item{Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){Text("${String.format(Locale.ROOT,"%.1f",review.score?:0.0)} 分",style=MaterialTheme.typography.headlineLarge);Text("正确 ${review.correct} / ${review.total}题，错词已自动入库。")}}}}
        else item{Text("请按纸面答案的轮次和题号，对照标准答案逐题标记。全部批改后自动计算成绩，孩子写的内容可选填。",style=MaterialTheme.typography.bodySmall)}
        items(review.total,key={it}){position->
            val round=position/review.words.size;val index=position%review.words.size;val word=review.words[index];val key="$round:$index"
            OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("第${round+1}轮 · 第${index+1}题",style=MaterialTheme.typography.labelLarge)
                Text(word.english,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                if(word.phonetic.isNotBlank())Text(word.phonetic)
                if(!chinese)Text(word.chinese)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    FilterChip(s.reviewMarks[key]==true,{vm.reviewMark(key,true)},enabled=!review.graded&&!s.busy,label={Text("正确")})
                    FilterChip(s.reviewMarks[key]==false,{vm.reviewMark(key,false)},enabled=!review.graded&&!s.busy,label={Text("错误")})
                }
                if(s.reviewMarks[key]==false)OutlinedTextField(s.reviewWritten[key]?:"",{vm.reviewWritten(key,it)},label={Text("孩子写的内容（可选）")},enabled=!review.graded&&!s.busy,singleLine=true,modifier=Modifier.fillMaxWidth())
            }}
        }
        if(!review.graded)item{Text("已批改 ${s.reviewMarks.size} / ${review.total}题");Button(onClick={confirming=true},enabled=!s.busy&&s.reviewMarks.size==review.total,modifier=Modifier.fillMaxWidth()){Text("提交批改并保存错词")}}
        item{OutlinedButton(onClick=vm::openWrongWords,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("查看孩子的错词本")}}
        item{Button(onClick={vm.openHistory()},enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("返回听写记录")}}
        item{OutlinedButton(onClick=vm::home,modifier=Modifier.fillMaxWidth()){Text("返回学习首页")}}
    }
    if(confirming)AlertDialog(onDismissRequest={confirming=false},title={Text("确认提交本次批改？")},text={Text("成绩按正确题数占比计算，保留一位小数。提交后固定保存，错误单词自动记入当前孩子的错词本。")},confirmButton={TextButton(onClick={confirming=false;vm.submitReview()}){Text("确认提交")}},dismissButton={TextButton(onClick={confirming=false}){Text("返回核对")}})
}

@Composable fun WrongWordsPage(s: AppState,vm: AppViewModel){
    val chinese=s.studySubject=="CHINESE"
    var onlyPending by remember{mutableStateOf(true)}
    val shown=s.wrongWords.filter{!onlyPending||!it.mastered}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)){
        item{Text("${s.child?.nickname}的错词本",style=MaterialTheme.typography.titleLarge);Text("累计 ${s.wrongWords.size} 个错词，待复习 ${s.wrongWords.count{!it.mastered}} 个")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){FilterChip(onlyPending,{onlyPending=true},label={Text("待复习")});FilterChip(!onlyPending,{onlyPending=false},label={Text("全部")});TextButton(onClick=vm::openWrongWords,enabled=!s.busy){Text("刷新")}}}
        if(shown.isEmpty())item{Text("暂无错词。完成听写并提交家长批改后，错词会自动汇总。")}
        items(shown,key={it.id}){word->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(word.english,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            if(word.phonetic.isNotBlank())Text(word.phonetic)
            if(!chinese)Text(word.chinese)
            Text(listOf(if(word.grade>0)"${word.grade}年级" else "",word.semester,word.unit).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
            Text("累计错误 ${word.mistakeCount} 次 · ${if(word.mastered)"已掌握" else "待复习"}")
            TextButton(onClick={vm.wrongMastered(word)},enabled=!s.busy){Text(if(word.mastered)"恢复待复习" else "标记已掌握")}
        }}}
    }
}
