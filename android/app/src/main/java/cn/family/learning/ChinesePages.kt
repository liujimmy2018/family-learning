package cn.family.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ChineseHomePage(s: AppState, vm: AppViewModel) {
    val canPractice = !s.busy && s.child != null
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 18.dp)
    ) {
        item {
            Text("语文练习", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(s.child?.let { "${it.nickname} · ${it.grade}年级" } ?: "请在“我的”添加并选择孩子")
        }
        item { ActionRow("生字听写", "选年级、册别和单元，听普通话写生字词", canPractice, vm::openCatalog) }
        item { ActionRow("听写记录", "查看语文听写成绩或继续练习", canPractice) { vm.openHistory() } }
        item { ActionRow("错词本", "复习语文听写中写错的生字词", canPractice, vm::openWrongWords) }
        item { ActionRow("背诵课文", "查看课文、遮住正文背诵并记录结果", !s.busy, vm::openRecitation) }
        item { ActionRow("汉字笔顺", "输入汉字，逐笔查看写法", !s.busy, vm::openStrokeOrder) }
    }
}

@Composable
internal fun RecitationListPage(s: AppState, vm: AppViewModel) {
    var grade by rememberSaveable(s.child?.id) { mutableIntStateOf(s.child?.grade ?: 0) }
    var semester by rememberSaveable { mutableStateOf("") }
    val lessons = s.recitationLessons.filter {
        (grade == 0 || it.grade == grade) && (semester.isEmpty() || it.semester == semester)
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(vm::openRecitation, enabled = !s.busy) { Text("刷新") }
                OutlinedButton(vm::openRecitationHistory, enabled = !s.busy && s.child != null) { Text("背诵记录") }
            }
        }
        if (s.recitationLessons.isEmpty()) {
            item { Text("请在电脑工作台的“背诵课文”导入正文，再回来刷新。") }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChoiceField(
                        "年级", if (grade == 0) "全部年级" else "${grade}年级",
                        listOf("0" to "全部年级") + (1..6).map { it.toString() to "${it}年级" },
                        !s.busy, Modifier.weight(1f)
                    ) { grade = it.toInt() }
                    ChoiceField(
                        "册别", semester.ifBlank { "全部" },
                        listOf("" to "全部", "上册" to "上册", "下册" to "下册"),
                        !s.busy, Modifier.weight(1f)
                    ) { semester = it }
                }
            }
            if (lessons.isEmpty()) item { Text("当前年级和册别没有课文，可以切换筛选条件。") }
            items(lessons, key = { it.id }) { lesson ->
                ActionRow(lesson.title, lesson.scope, !s.busy) { vm.openRecitationLesson(lesson) }
            }
        }
    }
}

@Composable
internal fun RecitationLessonPage(s: AppState, vm: AppViewModel) {
    val lesson = s.recitationLesson ?: return
    var hidden by rememberSaveable(lesson.id) { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(lesson.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(lesson.scope, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedButton(onClick = { hidden = !hidden }) {
                Text(if (hidden) "显示原文" else "遮住原文开始背诵")
            }
        }
        if (hidden) {
            item { Text("原文已遮住。背完后可显示原文核对，由家长记录结果。") }
        }
        item {
            val saved = s.recitationSaved
            if (saved != null) {
                Text("已保存背诵记录 · ${saved.resultLabel}", color = MaterialTheme.colorScheme.primary)
                TextButton(vm::openRecitationHistory, enabled = !s.busy) { Text("查看背诵记录") }
            } else if (s.child != null) {
                Text("由家长记录 ${s.child.nickname} 的背诵结果", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.recordRecitation("COMPLETED") }, enabled = !s.busy) { Text("已完成") }
                    OutlinedButton(onClick = { vm.recordRecitation("REVIEW") }, enabled = !s.busy) { Text("需再练习") }
                }
            }
        }
        if (!hidden) {
            items(lesson.content.split('\n')) { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
internal fun RecitationHistoryPage(s: AppState, vm: AppViewModel) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("${s.child?.nickname.orEmpty()}的背诵记录", style = MaterialTheme.typography.headlineSmall)
            Text("最近100条家长记录", style = MaterialTheme.typography.bodySmall)
            TextButton(vm::openRecitationHistory, enabled = !s.busy) { Text("刷新") }
        }
        if (s.recitationAttempts.isEmpty()) item { Text("还没有背诵记录。") }
        items(s.recitationAttempts, key = { it.id }) { attempt ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(attempt.title, style = MaterialTheme.typography.titleMedium)
                    Text("${attempt.resultLabel} · ${historyDate(attempt.createdAt)}")
                }
            }
        }
    }
}
