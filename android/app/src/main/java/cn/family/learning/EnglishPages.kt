package cn.family.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.family.learning.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable private fun SectionLabel(text: String){Text(text,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
@Composable internal fun ActionRow(title: String,subtitle: String,enabled: Boolean=true,onClick: ()->Unit){
    OutlinedCard(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.ChevronRight,null)}
    }
}

@Composable fun EnglishHomePage(s: AppState,vm: AppViewModel){
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(18.dp),contentPadding=PaddingValues(top=12.dp,bottom=24.dp)){
        item{Surface(color=Color.White,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text("当前学习孩子",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.child?.let{"${it.nickname} · ${it.grade}年级"}?:"还未添加孩子",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                if(s.child==null)TextButton(onClick=vm::profile){Text("前往我的，添加孩子")}
            }
        }}
        item{Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFE9EFFD)),shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                Text("英语听写",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text("选好词库和范围，按顺序听写。\n完成后由家长批改，成绩自动保存。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){Text("选择范围",style=MaterialTheme.typography.labelMedium);Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(14.dp));Text("逐词听写",style=MaterialTheme.typography.labelMedium);Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(14.dp));Text("家长批改",style=MaterialTheme.typography.labelMedium)}
                Button(onClick=vm::openCatalog,enabled=!s.busy&&s.child!=null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("开始听写")}
            }
        }}
        item{SectionLabel("练习记录")}
        item{ActionRow("听写记录","查看完成情况、用时与成绩",!s.busy&&s.child!=null){vm.openHistory()}}
        item{ActionRow("错词本","查看家长批改后收集的错词",!s.busy&&s.child!=null,vm::openWrongWords)}
    }
}

@Composable fun MyPage(s: AppState,vm: AppViewModel){
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(vertical=18.dp)){
        item{Text("孩子与学习设置",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("学习资料和听写记录按孩子分别保存。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{SectionLabel("当前学习孩子")}
        if(s.children.isEmpty())item{Text("添加孩子后，就可以开始听写。")}
        items(s.children,key={it.id}){child->OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
            Row(Modifier.padding(horizontal=8.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                RadioButton(selected=child.id==s.child?.id,onClick={vm.switchChild(child)},enabled=!s.busy)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                    Text(child.nickname,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${child.grade}年级 · ${child.textbook.ifBlank{"教材未填写"}}",style=MaterialTheme.typography.bodySmall)
                    if(child.id==s.child?.id)Text("正在学习",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick={vm.openChild(child)},enabled=!s.busy){Text("编辑")}
            }
        }}
        item{OutlinedButton(onClick={vm.openChild(null)},enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Text("添加孩子")}}
        item{SectionLabel("学习设置")}
        item{ActionRow("听写默认设置","播报方式、等待时间和次数",!s.busy,vm::openSettings)}
        item{ActionRow("词表工具","拍照识词、手动词表与查词",!s.busy,vm::openTools)}
        item{ActionRow("服务器管理",s.servers.firstOrNull{it.url==s.serverUrl}?.name?:s.serverUrl,!s.busy,vm::openServers)}
        item{Text("版本 ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{OutlinedButton(onClick=vm::logout,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("退出登录")}}
    }
}

@Composable fun ToolsPage(s: AppState,vm: AppViewModel){
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(vertical=16.dp)){
        item{Text("词库可在电脑维护",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("${s.serverUrl}/web/",style=MaterialTheme.typography.bodySmall)}
        if(s.child==null)item{Text("请先在“我的”中添加并选择孩子。")}
        item{ActionRow("拍照或选图识词","本地识别后，由家长核对释义",!s.busy&&s.child!=null,vm::openImport)}
        item{ActionRow("手动建立词表","逐行填写英文与中文释义",!s.busy&&s.child!=null,vm::newList)}
        item{ActionRow("查单词","云端查询释义，需要配置 AI",!s.busy&&s.child!=null,vm::openLookup)}
        if(s.hasEditorDraft)item{OutlinedButton(onClick=vm::resumeEditor,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("继续编辑词表草稿")}}
        if(s.hasImportDraft)item{OutlinedButton(onClick=vm::openImport,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("继续图片导入")}}
        item{SectionLabel("孩子的其他词表")}
        items(s.lists,key={it.id}){list->ActionRow(list.title,"${list.words.size}个词",!s.busy){vm.openList(list)}}
    }
}

@Composable fun SubjectPlaceholderPage(subject: String){
    Box(Modifier.fillMaxSize().padding(32.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
        Icon(if(subject=="语文")Icons.Default.AutoStories else Icons.Default.Calculate,null,Modifier.size(58.dp),tint=MaterialTheme.colorScheme.primary.copy(alpha=.55f))
        Text("$subject",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text("功能将在后续版本补充",color=MaterialTheme.colorScheme.onSurfaceVariant)
    }}
}

@Composable internal fun ChoiceField(label: String,text: String,options: List<Pair<String,String>>,enabled: Boolean,modifier: Modifier=Modifier,onSelect: (String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth()){
            OutlinedButton(onClick={expanded=true},enabled=enabled,shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text(text,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis);Icon(Icons.Default.ExpandMore,null)
            }
            DropdownMenu(expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=350.dp)){options.forEach{(id,title)->DropdownMenuItem(text={Text(title)},onClick={expanded=false;onSelect(id)})}}
        }
    }
}

@Composable fun CleanCatalogPage(s: AppState,vm: AppViewModel){
    val chinese=s.studySubject=="CHINESE"
    var advanced by remember{mutableStateOf(false)}
    val units=CatalogSelection.units(s.catalogEntries,s.catalogGrade,s.catalogSemester)
    val available=CatalogSelection.eligible(s.catalogEntries,s.catalogGrade,s.catalogSemester,s.catalogUnits)
    val count=if(s.catalogSelection=="RANDOM")s.catalogCount else available.size
    Column(Modifier.fillMaxSize()){
        LazyColumn(Modifier.weight(1f).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(20.dp),contentPadding=PaddingValues(vertical=16.dp)){
            item{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(chinese)"生字听写" else "准备听写",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(s.child?.let{"${it.nickname} · ${it.grade}年级"}?:"",style=MaterialTheme.typography.bodySmall)};TextButton(onClick=vm::openCatalog,enabled=!s.busy){Icon(Icons.Default.Refresh,null,Modifier.size(18.dp));Text("刷新")}}}
            if(s.catalogBooks.isEmpty()){
                item{Text("还没有词库",style=MaterialTheme.typography.titleMedium);Text("请在电脑工作台的${if(chinese)"语文生字词" else "英语词表"}导入词表，再返回刷新。\n${s.serverUrl}/web/",style=MaterialTheme.typography.bodyMedium)}
            }else{
                item{SectionLabel("1 选择词库");Spacer(Modifier.height(8.dp));ChoiceField("词库",s.catalogBook?.title?:"请选择",s.catalogBooks.map{it.id to "${it.title} · ${it.count}词"},!s.busy){id->s.catalogBooks.firstOrNull{it.id==id}?.let(vm::selectCatalog)}}
                item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    SectionLabel("2 选择听写范围")
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        ChoiceField("年级","${s.catalogGrade}年级",(1..6).map{it.toString() to "${it}年级"},!s.busy,Modifier.weight(1f)){vm.catalogFilter(grade=it.toInt())}
                        ChoiceField("上下册",s.catalogSemester,listOf("上册" to "上册","下册" to "下册"),!s.busy,Modifier.weight(1f)){vm.catalogFilter(semester=it)}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){FilterChip(s.catalogScope=="UNIT",{vm.catalogScope("UNIT")},enabled=!s.busy,label={Text("按单元")});FilterChip(s.catalogScope=="BOOK",{vm.catalogScope("BOOK")},enabled=!s.busy,label={Text("整册")})}
                    if(s.catalogScope=="UNIT")ChoiceField("所属单元",s.catalogUnits.firstOrNull()?:"暂无单元",units.map{it to it},!s.busy&&units.isNotEmpty()){vm.catalogSingleUnit(it)}
                    else Text("根据本册词库顺序逐个听写，共${units.size}个单元。",style=MaterialTheme.typography.bodySmall)
                    if(available.isEmpty())Text("当前范围没有单词，请切换年级、册别或单元。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                }}
                item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    SectionLabel("3 设置听写方式")
                    if(chinese)Text("使用设备普通话语音播报生字词",style=MaterialTheme.typography.bodyMedium)
                    else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){FilterChip(s.settings.mode=="CHINESE",{vm.settings(s.settings.copy(mode="CHINESE"))},enabled=!s.busy,label={Text("读中文")});FilterChip(s.settings.mode=="ENGLISH",{vm.settings(s.settings.copy(mode="ENGLISH"))},enabled=!s.busy,label={Text("读英文")})}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("每词等待时间");Text("${s.settings.intervalSeconds} 秒",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)}
                    Slider(value=s.settings.intervalSeconds.coerceIn(1,10).toFloat(),onValueChange={value->val seconds=value.roundToInt();if(seconds!=s.settings.intervalSeconds)vm.settings(s.settings.copy(intervalSeconds=seconds))},valueRange=1f..10f,steps=8,enabled=!s.busy)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("1秒",style=MaterialTheme.typography.labelSmall);Text("10秒",style=MaterialTheme.typography.labelSmall)}
                    Text("当前词全部播报结束后，再留出书写时间。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }}
                item{TextButton(onClick={advanced=!advanced},enabled=!s.busy){Icon(Icons.Default.Tune,null,Modifier.size(18.dp));Text(if(advanced)"收起更多设置" else "更多设置")}}
                if(advanced)item{OutlinedCard(shape=RoundedCornerShape(14.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Text("随机抽取词条",Modifier.weight(1f));Switch(s.catalogSelection=="RANDOM",{vm.catalogSelection(if(it)"RANDOM" else "ALL")},enabled=!s.busy)}
                    if(s.catalogSelection=="RANDOM")Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(onClick={vm.catalogCount(s.catalogCount-1)},enabled=!s.busy&&s.catalogCount>1){Text("－")};Text("${s.catalogCount}词");OutlinedButton(onClick={vm.catalogCount(s.catalogCount+1)},enabled=!s.busy&&s.catalogCount<minOf(500,available.size)){Text("＋")}}
                    Text("每词读${s.settings.repeats}次 · 共${s.settings.rounds}轮 · ${if(s.settings.randomOrder)"随机顺序" else "词库顺序"}",style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick=vm::openSettings,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("调整次数、轮次与倒计时")}
                }}}
            }
        }
        Surface(shadowElevation=5.dp){Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(if(s.catalogSelection=="RANDOM")"所选范围${available.size}词，本次随机抽取$count 词" else "本次听写 $count 词 · ${if(chinese)"普通话" else if(s.settings.mode=="CHINESE")"读中文" else "读英文"} · 等待${s.settings.intervalSeconds}秒",style=MaterialTheme.typography.bodySmall)
            Button(onClick=vm::startCatalogPractice,enabled=!s.busy&&available.isNotEmpty()&&count in 1..500&&count<=available.size,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Text("开始听写")}
            if(count>500)Text("整册超过500词，请按单元或随机抽取分批练习。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        }}
    }
}

internal fun historyDate(value: String): String=runCatching{DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.parse(value).atZone(ZoneId.systemDefault()))}.getOrDefault(value.replace('T',' ').removeSuffix("Z"))

@Composable fun HistoryPage(s: AppState,vm: AppViewModel){
    val filters=listOf("ALL" to "全部","PENDING" to "进行中","UNREVIEWED" to "待批改","GRADED" to "已批改")
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(vertical=16.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("听写记录",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(s.child?.nickname?:"",style=MaterialTheme.typography.bodySmall)};TextButton(onClick={vm.openHistory()},enabled=!s.busy){Text("刷新")}}}
        item{LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(filters,key={it.first}){(key,label)->FilterChip(s.historyFilter==key,{vm.openHistory(key)},enabled=!s.busy,label={Text(label)})}}}
        if(s.historyItems.isEmpty()&&!s.busy)item{Text("暂无符合条件的听写记录。")}
        items(s.historyItems,key={it.practice.id}){entry->val p=entry.practice
            OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(historyDate(entry.createdAt),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(if(entry.graded)"已批改" else when(p.status){"COMPLETED"->"待批改";"STOPPED"->"提前结束";"PAUSED"->"进行中";else->"待开始"},style=MaterialTheme.typography.labelMedium,color=if(entry.graded)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)}
                Text(p.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("${p.words.size}词 · ${p.settings.rounds}轮 · 用时 ${durationText(entry.elapsedSeconds)}",style=MaterialTheme.typography.bodySmall)
                if(entry.graded)Text("${String.format(Locale.ROOT,"%.1f",entry.score?:0.0)} 分  ·  正确 ${entry.correct}/${entry.total}题",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.secondary)
                if(p.status=="COMPLETED")TextButton(onClick={vm.openReview(p)},enabled=!s.busy){Text(if(entry.graded)"查看批改详情" else "去批改")}
                else if(p.status!="STOPPED")TextButton(onClick={vm.resumePractice(p)},enabled=!s.busy){Text("继续听写")}
            }}
        }
        if(s.historyHasMore)item{OutlinedButton(onClick=vm::moreHistory,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("加载更多")}}
    }
}
