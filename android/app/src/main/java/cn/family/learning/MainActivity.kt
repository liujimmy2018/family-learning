package cn.family.learning

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.family.learning.data.*
import cn.family.learning.media.CameraPage
import cn.family.learning.media.CropPage
import cn.family.learning.playback.PlaybackUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity: ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?){super.onCreate(savedInstanceState);setContent{
        MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF315BCF),secondary=Color(0xFF387D67),background=Color(0xFFF7F9FC),surface=Color.White)){
            val ui by vm.state.collectAsStateWithLifecycle();val playback by vm.player.state.collectAsStateWithLifecycle()
            DisposableEffect(playback.phase){
                if(playback.phase in listOf("COUNTDOWN","PLAYING","WAITING"))window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose{}
            }
            LearningApp(ui,playback,vm)
        }
    }}
    override fun onStop(){vm.player.background();super.onStop()}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LearningApp(s: AppState,p: PlaybackUi,vm: AppViewModel){
    val root=s.route in listOf("HOME","CHINESE","MATH","PROFILE")
    BackHandler(enabled=!root&&s.route!="LOGIN"){vm.back()}
    Scaffold(
        topBar={TopAppBar(title={Text(when(s.route){"HOME"->"英语";"CHINESE"->"语文";"MATH"->"数学";"HISTORY"->"听写记录";"TOOLS"->"词表工具";"CATALOG"->"准备听写";"REVIEW"->"家长批改与评分";"WRONG_WORDS"->"错词本";"SERVERS"->"选择服务器";"SERVER_EDIT"->if(s.editingServer==null)"新增服务器" else "编辑服务器";"LOGIN"->"家庭学习助手";"CHILD"->"孩子档案";"LIST"->s.currentList?.title?:"英语词表";"EDITOR"->"确认英语词表";"IMPORT"->"拍照建立词表";"CROP"->"裁剪学习区域";"CAMERA"->"拍摄学习内容";"SETTINGS"->"听写设置";"PLAYER"->if(s.practice?.subject=="CHINESE")"生字听写" else "单词听写";"RECITATION"->"背诵课文";"RECITATION_LESSON"->"背诵练习";"RECITATION_HISTORY"->"背诵记录";"STROKES"->"汉字笔顺";"LOOKUP"->"查单词";"PROFILE"->"我的";else->"英语"})},navigationIcon={if(!root&&s.route!="LOGIN")IconButton(onClick=vm::back){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}})},
        bottomBar={if(root)NavigationBar{
            NavigationBarItem(selected=s.route=="HOME",onClick={vm.subject("HOME")},enabled=!s.busy,icon={Icon(Icons.Default.Translate,null)},label={Text("英语")})
            NavigationBarItem(selected=s.route=="CHINESE",onClick={vm.subject("CHINESE")},enabled=!s.busy,icon={Icon(Icons.Default.AutoStories,null)},label={Text("语文")})
            NavigationBarItem(selected=s.route=="MATH",onClick={vm.subject("MATH")},enabled=!s.busy,icon={Icon(Icons.Default.Calculate,null)},label={Text("数学")})
            NavigationBarItem(selected=s.route=="PROFILE",onClick={vm.subject("PROFILE")},enabled=!s.busy,icon={Icon(Icons.Default.PersonOutline,null)},label={Text("我的")})
        }}
    ){padding->Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()){
        if(s.busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text(s.busyText,Modifier.padding(horizontal=20.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall)}
        s.message?.let{message->Surface(color=MaterialTheme.colorScheme.errorContainer){Row(Modifier.fillMaxWidth().padding(start=16.dp),verticalAlignment=Alignment.CenterVertically){Text(message,Modifier.weight(1f).padding(vertical=8.dp),style=MaterialTheme.typography.bodyMedium);IconButton(onClick={vm.message(null)}){Icon(Icons.Default.Close,"关闭提示")}}}}
        Box(Modifier.weight(1f)){
            when(s.route){
                "LOGIN"->LoginPage(s,vm)
                "CATALOG"->CleanCatalogPage(s,vm)
                "HISTORY"->HistoryPage(s,vm)
                "TOOLS"->ToolsPage(s,vm)
                "CHINESE"->ChineseHomePage(s,vm)
                "RECITATION"->RecitationListPage(s,vm)
                "RECITATION_LESSON"->RecitationLessonPage(s,vm)
                "RECITATION_HISTORY"->RecitationHistoryPage(s,vm)
                "STROKES"->StrokeOrderPage()
                "MATH"->SubjectPlaceholderPage("数学")
                "REVIEW"->ReviewPage(s,vm)
                "WRONG_WORDS"->WrongWordsPage(s,vm)
                "SERVERS"->ServerListPage(s,vm)
                "SERVER_EDIT"->ServerEditPage(s,vm)
                "CHILD"->ChildPage(s,vm)
                "HOME"->EnglishHomePage(s,vm)
                "PROFILE"->MyPage(s,vm)
                "LIST"->WordListPage(s,vm)
                "EDITOR"->EditorPage(s,vm)
                "IMPORT"->ImportPage(s,vm)
                "CAMERA"->CameraPage(onPhoto=vm::importUri,onError=vm::message)
                "CROP"->s.cropPath?.let{CropPage(it,vm::cropped,vm::message)}
                "SETTINGS"->SettingsPage(s,vm)
                "PLAYER"->PlayerPage(s,p,vm)
                "LOOKUP"->LookupPage(s,vm)
            }
        }
    }}
    if(s.conflictPractice!=null)AlertDialog(onDismissRequest=vm::dismissConflict,title={Text("听写进度已更新")},text={Text("服务器上的进度与本机待同步内容不同。使用服务器进度将放弃本机待同步的播放记录，不会修改词表或已保存的答案。")},confirmButton={TextButton(onClick=vm::useServerProgress){Text("使用服务器进度")}},dismissButton={TextButton(onClick=vm::dismissConflict){Text("返回")}})
}

@Composable private fun Page(content: @Composable ColumnScope.()->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)}
@Composable private fun Primary(text: String,enabled: Boolean=true,onClick: ()->Unit){Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Text(text)}}
@Composable private fun SectionTitle(text: String){Text(text,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}

@Composable private fun LoginPage(s: AppState,vm: AppViewModel){
    var registering by rememberSaveable{mutableStateOf(false)}
    var name by rememberSaveable{mutableStateOf("")}
    // Passwords deliberately do not enter saved-instance state.
    var password by remember{mutableStateOf("")};var invite by remember{mutableStateOf("")}
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    Page{
        OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("当前服务器",style=MaterialTheme.typography.labelLarge)
            Text(s.servers.firstOrNull{it.url==s.serverUrl}?.name?:"未选择服务器")
            Text(s.serverUrl.ifBlank{"请先添加并选择服务地址"},style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={keyboard?.hide();focus.clearFocus();vm.openServers()},enabled=!s.busy){Text("选择 / 管理服务器")}
        }}
        Text("把每天的听写准备，变得简单一点。",style=MaterialTheme.typography.headlineSmall)
        Text("登录家长账号，分别管理每个孩子的学习内容。",color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name,{name=it},label={Text("账号")},supportingText={Text("4—40位字母、数字或下划线")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("密码")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        if(registering)OutlinedTextField(invite,{invite=it},label={Text("邀请码")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Primary(if(registering)"注册并登录" else "登录",!s.busy&&s.serverUrl.isNotBlank()){keyboard?.hide();focus.clearFocus();vm.login(s.serverUrl,name,password,if(registering)invite else null)}
        TextButton(onClick={registering=!registering}){Text(if(registering)"已有账号，去登录" else "使用邀请码注册")}
        Text("密码遗忘时请联系试用管理员重置。",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun ServerListPage(s: AppState,vm: AppViewModel){
    var removing by remember{mutableStateOf<ServerProfile?>(null)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=16.dp)){
        item{Text("保存家里、办公室等常用地址，点击选择切换。切换到其他服务器后需重新登录，本机草稿与缓存按服务器分别保留。")}
        item{Primary("新增服务器",!s.busy){vm.editServer(null)}}
        items(s.servers,key={it.id}){server->OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                RadioButton(selected=server.url==s.serverUrl,onClick={vm.selectServer(server)},enabled=!s.busy)
                TextButton(onClick={vm.selectServer(server)},enabled=!s.busy,modifier=Modifier.weight(1f)){
                    Column(Modifier.fillMaxWidth()){Text(server.name);Text(server.url,style=MaterialTheme.typography.bodySmall);if(server.url==s.serverUrl)Text("当前使用",style=MaterialTheme.typography.labelSmall)}
                }
            }
            Row{TextButton(onClick={vm.editServer(server)},enabled=!s.busy){Text("编辑")};TextButton(onClick={removing=server},enabled=!s.busy&&server.url!=s.serverUrl){Text("删除地址")}}
        }}}
    }
    removing?.let{server->AlertDialog(onDismissRequest={removing=null},title={Text("删除已保存的地址？")},text={Text("${server.name}\n${server.url}\n只删除本机地址记录，服务器上的账号和学习资料不受影响。")},confirmButton={TextButton(onClick={removing=null;vm.removeServer(server)}){Text("删除地址")}},dismissButton={TextButton(onClick={removing=null}){Text("取消")}})}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ServerInput(value: String,onChange: (String)->Unit,label: String,enabled: Boolean,url: Boolean=false){
    val requester=remember{BringIntoViewRequester()}
    var focused by remember{mutableStateOf(false)}
    val bottom=WindowInsets.ime.getBottom(LocalDensity.current)
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    LaunchedEffect(focused,bottom){if(focused){withFrameNanos{};requester.bringIntoView()}}
    OutlinedTextField(value,onChange,label={Text(label)},singleLine=true,enabled=enabled,
        keyboardOptions=KeyboardOptions(keyboardType=if(url)KeyboardType.Uri else KeyboardType.Text,imeAction=ImeAction.Done,autoCorrectEnabled=false),
        keyboardActions=KeyboardActions(onDone={keyboard?.hide();focus.clearFocus()}),
        modifier=Modifier.fillMaxWidth().bringIntoViewRequester(requester).onFocusChanged{focused=it.isFocused})
}

@Composable private fun ServerEditPage(s: AppState,vm: AppViewModel){
    var name by rememberSaveable(s.editingServer?.id){mutableStateOf(s.editingServer?.name?:"")}
    var url by rememberSaveable(s.editingServer?.id){mutableStateOf(s.editingServer?.url?:"")}
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    Page{
        ServerInput(name,{name=it.take(40)},"服务器名称，如家里电脑",!s.busy)
        ServerInput(url,{url=it},"服务地址",!s.busy,url=true)
        Text(if(BuildConfig.DEBUG)"例如 http://192.168.1.10:8080，手机与电脑需在同一网络。地址不要加 /api/v1。" else "填写家庭服务的HTTPS根地址，不要加 /api/v1。",style=MaterialTheme.typography.bodySmall)
        Primary("保存服务器",!s.busy){keyboard?.hide();focus.clearFocus();vm.saveServer(name,url)}
        if(s.editingServer?.url==s.serverUrl)Text("修改当前服务器地址后，需要重新登录。",style=MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ChildPage(s: AppState,vm: AppViewModel){
    val editing=s.editingChild
    var name by rememberSaveable(editing?.id){mutableStateOf(editing?.nickname?:"")}
    var grade by rememberSaveable(editing?.id){mutableIntStateOf(editing?.grade?:1)}
    var book by rememberSaveable(editing?.id){mutableStateOf(editing?.textbook?:"")}
    Page{
        SectionTitle(if(editing==null)"添加孩子" else "编辑孩子档案")
        Text("使用昵称即可，年级用于匹配学习内容。")
        OutlinedTextField(name,{name=it.take(30)},label={Text("孩子昵称")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("年级")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..6).forEach{n->FilterChip(selected=grade==n,onClick={grade=n},label={Text("${n}年级")})}}
        OutlinedTextField(book,{book=it.take(80)},label={Text("教材版本（可稍后填写）")},modifier=Modifier.fillMaxWidth())
        Primary("保存档案",!s.busy){vm.saveChild(name,grade,book)}
    }
}

@Composable private fun WordListPage(s: AppState,vm: AppViewModel){val list=s.currentList?:return
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=20.dp)){
        item{Text("${s.child?.nickname} · 共${list.words.size}个词，已选${s.selectedWords.size}个",Modifier.padding(vertical=12.dp))}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=vm::editList,enabled=!s.busy){Text("编辑词表")};OutlinedButton(onClick=vm::openSettings){Text("听写设置")}}}
        item{Row{TextButton(onClick={vm.selectWords(true)}){Text(if(list.words.size>100)"选前100项" else "全选")};TextButton(onClick={vm.selectWords(false)}){Text("清空选择")}}}
        item{Primary("开始所选单词听写",!s.busy&&s.selectedWords.isNotEmpty(),vm::startPractice)}
        items(list.words,key={it.id}){word->OutlinedCard(Modifier.fillMaxWidth()){Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(word.id in s.selectedWords,{vm.toggleWord(word.id)});Column(Modifier.weight(1f)){Text(word.text,style=MaterialTheme.typography.titleMedium);Text(word.meaning,style=MaterialTheme.typography.bodyMedium)};IconButton(onClick={vm.pronounce(word)},enabled=!s.busy){Icon(Icons.Default.VolumeUp,"播放英文读音")}}}}
    }
}

@Composable private fun EditorPage(s: AppState,vm: AppViewModel){LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(vertical=12.dp)){
    item{Text("请对照照片确认单词和释义；取消勾选可排除不需要学习的词。")}
    if(s.editor.localRecognition)item{Text("本地识别仅提取照片文字，不会自动翻译。请补齐空白中文释义、删除无关词后保存。听写已选择设备语音，需要手机安装对应离线语音包。",style=MaterialTheme.typography.bodySmall)}
    item{OutlinedTextField(s.editor.title,vm::editorTitle,label={Text("词表名称")},modifier=Modifier.fillMaxWidth())}
    itemsIndexed(s.editor.words){index,word->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(word.selected,{vm.editWord(index,word.copy(selected=it))});Text("第${index+1}项",Modifier.weight(1f));IconButton(onClick={vm.removeWord(index)}){Icon(Icons.Default.DeleteOutline,"删除此词")}}
        OutlinedTextField(word.text,{vm.editWord(index,word.copy(text=it.take(100)))},label={Text("英文单词或短语")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(word.meaning,{vm.editWord(index,word.copy(meaning=it.take(300)))},label={Text("中文释义 / 中文听写提示")},modifier=Modifier.fillMaxWidth())
    }}}
    item{OutlinedButton(onClick=vm::addWord,modifier=Modifier.fillMaxWidth()){Text("添加一行")}}
    item{Primary("确认并保存词表",!s.busy,vm::saveEditor)}
}}

@Composable private fun LookupPage(s: AppState,vm: AppViewModel){
    var text by rememberSaveable{mutableStateOf("")}
    Page{
        Text("输入英文单词或短语，查看常见中文释义。多义词请结合课文确认。")
        OutlinedTextField(text,{text=it.take(100)},label={Text("英文单词或短语")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Primary("查询释义",!s.busy&&text.isNotBlank()){vm.lookup(text)}
        s.lookupWord?.let{w->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(w.text,style=MaterialTheme.typography.headlineSmall);Text(w.meaning)
            OutlinedButton(onClick={vm.pronounce(w)},enabled=!s.busy){Icon(Icons.Default.VolumeUp,null);Text("播放英文读音")}
            TextButton(onClick=vm::saveLookup){Text("编辑并保存到新词表")}
        }}}
    }
}

@Composable private fun Thumbnail(path: String){
    val bitmap by produceState<android.graphics.Bitmap?>(null,path){value=withContext(Dispatchers.IO){android.graphics.BitmapFactory.decodeFile(path,android.graphics.BitmapFactory.Options().apply{inSampleSize=8})}}
    bitmap?.let{Image(it.asImageBitmap(),"学习照片",Modifier.size(76.dp))}
}

@Composable private fun ImportPage(s: AppState,vm: AppViewModel){
    var consent by remember{mutableStateOf(false)}
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)){uris->if(uris.isNotEmpty())vm.importUris(uris)}
    val context=LocalContext.current
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)vm.camera() else vm.message("未允许相机，可从相册选择照片")}
    Page{
        Text("裁剪到需要学习的区域，尽量去掉姓名和学校等无关内容。")
        OutlinedTextField(s.importTitle,vm::importTitle,label={Text("词表名称")},enabled=!s.busy,modifier=Modifier.fillMaxWidth())
        SectionTitle("识别方式")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=s.recognitionSource=="LOCAL",onClick={vm.recognitionSource("LOCAL")},enabled=!s.busy,label={Text("本地识别 · 免费")})
            FilterChip(selected=s.recognitionSource=="CLOUD",onClick={vm.recognitionSource("CLOUD")},enabled=!s.busy,label={Text("云端AI识别")})
        }
        Text(if(s.recognitionSource=="LOCAL")"照片在手机本地识别，无需AI密钥。可提取清晰的英文和照片中的中文释义，缺少的释义需手动补充。确认保存词表仍需连接家庭服务。" else "云端AI可识别单词并生成中文释义，需要服务端配置AI，可能产生调用费用。",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(selected=s.importType=="WORD_TABLE",onClick={vm.importType("WORD_TABLE")},enabled=!s.busy,label={Text("单词表")})
            FilterChip(selected=s.importType=="TEXTBOOK",onClick={vm.importType("TEXTBOOK")},enabled=!s.busy,label={Text("课文选词")})
        }
        if(s.recognitionSource=="LOCAL"&&s.importType=="TEXTBOOK")Text("本地课文识别会列出英文候选词，需手动筛选学习词并补充释义。",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)vm.camera() else cameraPermission.launch(Manifest.permission.CAMERA)},enabled=!s.busy&&s.importFiles.size<5,modifier=Modifier.weight(1f)){Text("拍照")}
            OutlinedButton(onClick={pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!s.busy&&s.importFiles.size<5,modifier=Modifier.weight(1f)){Text("选图")}
        }
        s.importFiles.forEachIndexed{index,path->
            OutlinedCard(Modifier.fillMaxWidth()){
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                    Thumbnail(path)
                    Column(Modifier.weight(1f).padding(start=12.dp)){
                        Text("第${index+1}张")
                        Row{
                            TextButton(onClick={vm.recrop(index)},enabled=!s.busy){Text("裁剪")}
                            TextButton(onClick={vm.removeImage(index)},enabled=!s.busy){Text("移除")}
                        }
                    }
                    if(index>0)IconButton(onClick={vm.moveImage(index)},enabled=!s.busy){Icon(Icons.Default.KeyboardArrowUp,"向前移动")}
                }
            }
        }
        Text("最多5张；识别结果会先交给你确认。",style=MaterialTheme.typography.bodySmall)
        Primary(if(s.recognitionSource=="LOCAL")"本地识别单词（免费）" else "上传并识别单词",!s.busy&&s.importFiles.isNotEmpty()){
            if(s.recognitionSource=="LOCAL")vm.recognizeLocal() else consent=true
        }
    }
    if(consent)AlertDialog(onDismissRequest={consent=false},title={Text("使用云端识别")},text={Text("本次裁剪后的学习照片将上传到家庭学习服务和云端AI用于识别。确认照片中不包含不需要上传的个人信息后继续。")},confirmButton={TextButton(onClick={consent=false;vm.recognize(s.importTitle)}){Text("确认上传")}},dismissButton={TextButton(onClick={consent=false}){Text("返回检查")}})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun SettingsPage(s: AppState,vm: AppViewModel){
    val d=s.settings
    Page{
        SectionTitle("播报方式")
        if(s.settingsForChinese) {
            Text("使用设备普通话语音播报生字词")
        } else {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(d.mode=="ENGLISH",{vm.settings(d.copy(mode="ENGLISH"))},label={Text("读英文，写英文")})
                FilterChip(d.mode=="CHINESE",{vm.settings(d.copy(mode="CHINESE"))},label={Text("读中文，写英文")})
            }
        }
        SectionTitle("时间与次数")
        Stepper("开始倒计时",d.countdownSeconds,"秒",0..30){vm.settings(d.copy(countdownSeconds=it))}
        Stepper("每词等待时间",d.intervalSeconds,"秒",1..10){vm.settings(d.copy(intervalSeconds=it))}
        Stepper("每词播报次数",d.repeats,"次",1..5){vm.settings(d.copy(repeats=it))}
        Stepper("整轮听写次数",d.rounds,"轮",1..3){vm.settings(d.copy(rounds=it))}
        Text("重复播报间停顿：${d.repeatGapSeconds}秒")
        Slider(value=d.repeatGapSeconds.toFloat(),onValueChange={vm.settings(d.copy(repeatGapSeconds=(it*2).toInt()/2.0))},valueRange=0.5f..3f,steps=4)
        Row(verticalAlignment=Alignment.CenterVertically){Text("随机顺序",Modifier.weight(1f));Switch(d.randomOrder,{vm.settings(d.copy(randomOrder=it))})}
        Text("书写间隔从当前词全部播报结束后开始。多轮沿用同一顺序，每轮请单独编号。",style=MaterialTheme.typography.bodySmall)
        if(!s.settingsForChinese) {
            SectionTitle("语音来源")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(d.voiceSource=="CLOUD",{vm.settings(d.copy(voiceSource="CLOUD"))},label={Text("云端语音")})
                FilterChip(d.voiceSource=="DEVICE",{vm.settings(d.copy(voiceSource="DEVICE"))},label={Text("设备语音")})
            }
            Text("云端语音需要服务端已配置AI；设备语音需要手机安装对应语言的语音包。正式开始前会缓存本次音频。",style=MaterialTheme.typography.bodySmall)
        }
        Primary("保存并返回",onClick=vm::back)
    }
}
@Composable private fun Stepper(label: String,value: Int,unit: String,range: IntRange,onChange: (Int)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));IconButton(onClick={onChange(value-1)},enabled=value>range.first){Icon(Icons.Default.Remove,"减少$label")};Text("$value $unit");IconButton(onClick={onChange(value+1)},enabled=value<range.last){Icon(Icons.Default.Add,"增加$label")}}
}

@Composable private fun PlayerPage(s: AppState,p: PlaybackUi,vm: AppViewModel){val practice=s.practice?:return
    val chinese=practice.subject=="CHINESE"
    var stopConfirm by remember{mutableStateOf(false)}
    var showWord by remember(practice.id,p.round,p.itemIndex){mutableStateOf(false)}
    LaunchedEffect(p.phase){if(p.phase in listOf("PREPARING","ERROR","COMPLETED","STOPPED"))showWord=false}
    Page{
        Text("${s.child?.nickname} · ${practice.title}",style=MaterialTheme.typography.titleMedium)
        Text("有效练习用时 ${durationText((p.elapsedMillis/1000).toInt())}（暂停不计时）",style=MaterialTheme.typography.bodySmall)
        if(p.phase=="PREPARING"){
            Text("准备本次听写音频",style=MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(progress={if(p.total==0)0f else p.prepared.toFloat()/p.total},modifier=Modifier.fillMaxWidth())
            Text("${p.prepared} / ${p.total}，准备完毕后再开始")
        }else{
            Text("第${p.round+1}轮 / ${practice.settings.rounds}轮",style=MaterialTheme.typography.titleMedium)
            Text("第${p.itemIndex+1}项 / ${practice.words.size}项",style=MaterialTheme.typography.headlineLarge)
            Text(when(p.phase){"READY"->"音频已就绪，请准备纸笔";"COUNTDOWN"->"即将开始：${p.remaining}秒";"PLAYING"->"正在播报，请仔细听";"WAITING"->"书写时间：${p.remaining}秒";"PAUSED"->"已暂停，继续时会重播当前词";"COMPLETED"->"本次听写已完成";"STOPPED"->"本次听写已提前结束";"ERROR"->"音频准备未完成";else->"准备听写"},Modifier.padding(vertical=16.dp),style=MaterialTheme.typography.titleLarge)
        }
        p.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(p.phase in listOf("READY","COUNTDOWN","PLAYING","WAITING","PAUSED")){
            practice.words.getOrNull(p.itemIndex)?.let{word->
                OutlinedCard(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Text("单词卡片",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                            TextButton(onClick={if(!showWord)vm.player.pause();showWord=!showWord}){
                                Text(if(showWord)"收起" else if(p.phase in listOf("COUNTDOWN","PLAYING","WAITING"))"暂停并查看" else "查看")
                            }
                        }
                        if(showWord){
                            Text(word.text,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                            val label=if(chinese)"拼音" else "音标"
                            Text("$label：${word.phonetic.ifBlank{"未提供"}}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(!chinese)Text("中文：${word.meaning}",style=MaterialTheme.typography.titleMedium)
                        }else Text("听写时隐藏答案，查看可用于核对与跟读。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        when(p.phase){
            "READY"->Primary("开始听写"){showWord=false;vm.player.start()}
            "COUNTDOWN","PLAYING","WAITING"->Primary("暂停",onClick=vm.player::pause)
            "PAUSED"->Primary("继续并重播当前词"){showWord=false;vm.player.resume()}
            "ERROR"->{Primary("重新准备音频"){vm.prepareAgain()};if(!chinese)OutlinedButton(onClick={vm.prepareAgain("DEVICE")},modifier=Modifier.fillMaxWidth()){Text("改用设备语音")}}
            "COMPLETED"->{Primary("家长批改与评分",!s.busy){vm.openReview(practice)};OutlinedButton(onClick={vm.home();vm.refresh()},modifier=Modifier.fillMaxWidth()){Text("返回学习首页")}}
            "STOPPED"->Primary("返回学习首页"){vm.home();vm.refresh()}
        }
        if(p.phase in listOf("READY","COUNTDOWN","PLAYING","WAITING","PAUSED")){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick=vm.player::previous,modifier=Modifier.weight(1f)){Text("上一词")}
                OutlinedButton(onClick=vm.player::replay,modifier=Modifier.weight(1f)){Text("重播")}
                OutlinedButton(onClick=vm.player::next,modifier=Modifier.weight(1f)){Text("下一词")}
            }
            TextButton(onClick={stopConfirm=true}){Text("提前结束")}
        }
        Text("按轮次和题号书写。切后台、锁屏或来电时会暂停，回来后手动继续。",style=MaterialTheme.typography.bodySmall)
    }
    if(stopConfirm)AlertDialog(onDismissRequest={stopConfirm=false},title={Text("结束本次听写？")},text={Text("保留已播放的记录，尚未练习的词不会算作漏写。")},confirmButton={TextButton(onClick={stopConfirm=false;vm.player.stop()}){Text("结束并保存")}},dismissButton={TextButton(onClick={stopConfirm=false}){Text("继续听写")}})
}
