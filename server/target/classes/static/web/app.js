'use strict';
const $=s=>document.querySelector(s);
const state={session:null,tab:'catalog',collections:[],detail:null,children:[],wrong:[],child:'',collectionId:'',preview:null,importKey:null,lessons:[],wrongSubject:'ENGLISH',busy:false};
function node(tag,attrs={},...children){const n=document.createElement(tag);for(const [k,v] of Object.entries(attrs)){if(k.startsWith('on'))n.addEventListener(k.slice(2).toLowerCase(),v);else if(k==='class')n.className=v;else if(k==='value')n.value=v;else if(k==='checked'||k==='disabled')n[k]=v;else n.setAttribute(k,v);}for(const c of children.flat())if(c!=null)n.append(c instanceof Node?c:document.createTextNode(String(c)));return n;}
const button=(text,fn,kind='')=>node('button',{type:'button',class:kind,onClick:()=>run(fn)},text);
const field=(text,input)=>node('label',{},text,input);
const isChineseCatalog=()=>state.tab==='chinese';
const catalogQuery=(tab=state.tab)=>tab==='chinese'?'?subject=CHINESE':'';
const requestKey=()=>crypto.randomUUID?.()||'import-'+Date.now()+'-'+Math.random().toString(36).slice(2);
function select(items,value,change){const s=node('select',{onChange:e=>change(e.target.value)});for(const [v,label] of items)s.append(node('option',{value:v},label));s.value=value;return s;}
function notify(text,error=false){const n=$('#notice');n.textContent=text;n.className=error?'error':'';n.hidden=!text;$('#modal-error')?.remove();if(error&&$('#modal').open){const body=$('#modal-content .dialog-body');if(body){const message=node('div',{id:'modal-error',class:'errors',role:'alert'},text);body.prepend(message);body.scrollTop=0;}}}
async function run(fn){if(state.busy)return;state.busy=true;document.body.setAttribute('aria-busy','true');try{await fn();}catch(e){notify(e.message||'操作失败，请重试',true);}finally{state.busy=false;document.body.removeAttribute('aria-busy');}}
let refreshPromise=null;
async function request(path,options={},retry=true){
 const headers=new Headers(options.headers||{});if(state.session)headers.set('Authorization','Bearer '+state.session.accessToken);
 let body=options.body;if(body&&!(body instanceof FormData)){headers.set('Content-Type','application/json');body=JSON.stringify(body);}
 const response=await fetch('/api/v1'+path,{...options,headers,body});
 if(response.status===401&&state.session&&retry&&path!=='/auth/login'&&path!=='/auth/refresh'){
  if(!refreshPromise)refreshPromise=request('/auth/refresh',{method:'POST',body:{refreshToken:state.session.refreshToken}},false).then(s=>{state.session=s;}).finally(()=>{refreshPromise=null;});
  try{await refreshPromise;return request(path,options,false);}catch(e){signOutLocal();throw new Error('登录已过期，请重新登录');}
 }
 if(options.binary){if(!response.ok)throw new Error('模板下载失败，请重新登录后重试');return response.blob();}
 const result=await response.json().catch(()=>({message:'服务返回异常，请确认已升级 Java 服务'}));if(!response.ok)throw new Error(result.message||'请求失败');return result.data;
}
function signOutLocal(){state.session=null;state.tab='catalog';state.collections=[];state.collectionId='';state.detail=null;state.children=[];state.child='';state.wrong=[];state.lessons=[];state.wrongSubject='ENGLISH';closeDialog();$('#workspace').hidden=true;$('#login').hidden=false;$('#account').replaceChildren();$('#main').replaceChildren();}
$('#login-form').addEventListener('submit',e=>{e.preventDefault();run(async()=>{const f=new FormData(e.target);state.session=await request('/auth/login',{method:'POST',body:{loginName:String(f.get('name')).trim(),password:f.get('password')}},false);e.target.elements.password.value='';$('#login').hidden=true;$('#workspace').hidden=false;$('#account').replaceChildren(button('退出登录',async()=>{try{await request('/auth/logout',{method:'POST',body:{}});}finally{signOutLocal();}}));notify('');await loadCollections();render();});});
document.querySelectorAll('[data-tab]').forEach(b=>b.addEventListener('click',()=>run(async()=>{
 const tab=b.dataset.tab;
 notify('');
 if(tab==='catalog'||tab==='chinese')await loadCollections(tab);
 if(tab==='recitation')await loadLessons();
 if(tab==='wrong'){
  state.children=await request('/children');
  if(!state.children.some(c=>c.id===state.child))state.child=state.children[0]?.id||'';
  await loadWrong();
 }
 state.tab=tab;
 render();
})));
async function loadCollections(tab=state.tab){
 const collections=await request('/catalog/collections'+catalogQuery(tab));
 const id=collections.some(c=>c.id===state.collectionId)?state.collectionId:collections[0]?.id||'';
 const detail=id?await request('/catalog/collections/'+id):null;
 Object.assign(state,{collections,collectionId:id,detail});
}
async function loadWrong(){
 state.wrong=state.child?await request('/children/'+state.child+'/wrong-words?subject='+state.wrongSubject):[];
}
async function loadLessons(){state.lessons=await request('/recitation/lessons');}
function heading(title,description,actions=[]){return node('div',{class:'page-heading'},node('div',{},node('h1',{},title),node('p',{},description)),node('div',{class:'actions'},actions));}
function empty(title,description){return node('div',{class:'empty'},node('strong',{},title),description);}
function table(headers,rows){return node('div',{class:'table-wrap'},node('table',{},node('thead',{},node('tr',{},headers.map(h=>node('th',{},h)))),node('tbody',{},rows)));}
function render(){document.querySelectorAll('[data-tab]').forEach(b=>b.classList.toggle('selected',b.dataset.tab===state.tab));const main=$('#main');main.replaceChildren();if(state.tab==='catalog'||state.tab==='chinese')renderCatalog(main);else if(state.tab==='recitation')renderRecitation(main);else if(state.tab==='wrong')renderWrong(main);else renderGuide(main);}
function renderCatalog(main){
 const chinese=isChineseCatalog();
 main.append(heading(chinese?'我的语文生字词':'我的英语词表','在电脑整理，在手机听写。词表由同一个家长账号共享给孩子。',[button('下载 Excel 模板',downloadTemplate),button('导入词表',openImport,'primary')]));
 const sidebar=node('aside',{class:'panel sidebar'},node('h3',{},'已导入词表 · '+state.collections.length));
 for(const c of state.collections)sidebar.append(button('',async()=>{state.collectionId=c.id;state.detail=await request('/catalog/collections/'+c.id);render();},'collection'+(c.id===state.collectionId?' active':'')).appendChild(node('span',{},c.title,node('small',{},c.count+' 个词 · 第 '+c.revision+' 版'))).parentNode);
 if(!state.collections.length)sidebar.append(node('small',{},'导入后，词表会显示在这里。'));
 const content=node('section',{class:'panel'});main.append(node('div',{class:'catalog-layout'},sidebar,content));
 if(!state.detail){content.append(empty('先导入一份整理好的词表',chinese?'下载模板，填写年级、上下册、单元、生字词和拼音，再导入预览。':'下载模板，填写年级、上下册、单元、中英文和音标，再导入预览。'));return;}
 const {collection:c,entries}=state.detail;
 content.append(node('div',{class:'panel-body page-heading'},node('div',{},node('h2',{},c.title),node('small',{},entries.length+' 个词 · 保存后 App 刷新即可读取')),node('div',{class:'actions'},button('重命名',()=>openRename()),button(chinese?'新增生字词':'新增单词',()=>openEntry()),button('删除词表',()=>confirmDialog('删除词表？','词表将从可选列表移除，已保存的听写答案和批改记录仍保留。',async()=>{await request('/catalog/collections/'+c.id+'?expectedRevision='+c.revision,{method:'DELETE'});state.collectionId='';await loadCollections();render();notify('词表已移除');}),'danger'))));
 let grade='',semester='',unit='',query='';const body=node('div');
 const unitSelect=select([['','全部单元'],...Array.from(new Set(entries.map(e=>e.unit))).map(u=>[u,u])],'',v=>{unit=v;draw();});
 const filters=node('div',{class:'filters'},field('年级',select([['','全部年级'],...[1,2,3,4,5,6].map(g=>[String(g),g+'年级'])],'',v=>{grade=v;draw();})),field('上下册',select([['','全部'],['上册','上册'],['下册','下册']],'',v=>{semester=v;draw();})),field('所属单元',unitSelect),field('查找词条',node('input',{placeholder:chinese?'汉字或拼音':'英文或中文',onInput:e=>{query=e.target.value.trim().toLowerCase();draw();}})));
 content.append(filters,body);draw();
 function draw(){const shown=entries.filter(e=>(!grade||e.grade===Number(grade))&&(!semester||e.semester===semester)&&(!unit||e.unit===unit)&&(!query||(e.english+' '+e.chinese+' '+e.phonetic).toLowerCase().includes(query)));body.replaceChildren();if(!shown.length){body.append(empty('没有匹配的单词','试试其他年级、上下册、单元或关键词。'));return;}
 body.append(table(chinese?['年级 / 册','单元','生字词','拼音','操作']:['年级 / 册','单元','中文','英文','音标','操作'],shown.map(e=>node('tr',{},node('td',{},e.grade+'年级 · '+e.semester),node('td',{},node('span',{class:'badge'},e.unit)),chinese?null:node('td',{class:'wrap'},e.chinese),node('td',{class:'english wrap'},e.english),node('td',{class:'phonetic'},e.phonetic||'—'),node('td',{},button('编辑',()=>openEntry(e),'quiet'),button('删除',()=>confirmDialog('删除这条词条？',e.english+' · '+(chinese?e.phonetic:e.chinese),async()=>{state.detail=await request('/catalog/collections/'+c.id+'/entries/'+e.id+'?expectedRevision='+c.revision,{method:'DELETE'});await loadCollections();render();notify('单词已删除');}),'quiet danger'))))));}
}
async function downloadTemplate(){const blob=await request('/catalog/template.xlsx'+catalogQuery(),{binary:true});const url=URL.createObjectURL(blob);const a=node('a',{href:url,download:isChineseCatalog()?'语文生字词导入模板.xlsx':'英语词表导入模板.xlsx'});a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function closeDialog(){$('#modal').close();$('#modal-content').replaceChildren();state.preview=null;}
function dialog(title,body,actions){$('#modal-content').replaceChildren(node('div',{class:'dialog-head'},node('h2',{},title),button('关闭',closeDialog,'quiet')),node('div',{class:'dialog-body'},body),node('div',{class:'dialog-footer'},actions));if(!$('#modal').open)$('#modal').showModal();}
function confirmDialog(title,text,fn){dialog(title,node('p',{},text),[button('取消',closeDialog),button('确认',async()=>{await fn();closeDialog();},'danger')]);}
function openImport(){
 const chinese=isChineseCatalog();
 state.preview=null;state.importKey=requestKey();
 const title=node('input',{placeholder:'例如：人教版三年级上册',maxlength:'100'}),file=node('input',{type:'file',accept:'.xlsx'}),preview=node('div');
 const save=button('确认导入',async()=>{if(!state.preview||!state.preview.valid)throw new Error('请先选择并校验 Excel 文件');if(!title.value.trim())throw new Error('请输入词表名称');await request('/catalog/collections'+catalogQuery(),{method:'POST',headers:{'Idempotency-Key':state.importKey},body:{title:title.value.trim(),entries:state.preview.entries}});closeDialog();state.collectionId='';await loadCollections();render();notify(chinese?'词表已导入，请在 App 中打开“语文 → 生字听写”':'词表已导入，请在 App 中打开“英语 → 开始听写”并刷新');},'primary');save.disabled=true;
 file.addEventListener('change',()=>run(async()=>{save.disabled=true;state.preview=null;preview.replaceChildren();if(!file.files[0])return;const data=new FormData();data.append('file',file.files[0]);const p=await request('/catalog/preview'+catalogQuery(),{method:'POST',body:data});state.preview=p;save.disabled=!p.valid;preview.append(node('h3',{},p.valid?'校验通过 · '+p.count+' 个单词':'请修正 Excel 后重新选择文件'));
 if(p.errors.length)preview.append(node('div',{class:'errors'},p.errors.slice(0,30).map(e=>node('div',{},'第 '+e.row+' 行：'+e.message)),p.errors.length>30?'其余 '+(p.errors.length-30)+' 个错误请修正后再次校验。':''));
 if(p.entries.length)preview.append(table(chinese?['年级','册','单元','生字词','拼音']:['年级','册','单元','中文','英文','音标'],p.entries.slice(0,100).map(e=>node('tr',{},(chinese?[e.grade,e.semester,e.unit,e.english,e.phonetic]:[e.grade,e.semester,e.unit,e.chinese,e.english,e.phonetic]).map(v=>node('td',{class:'wrap'},v))))));
 if(p.entries.length>100)preview.append(node('small',{},'预览前100行，确认导入会保存全部 '+p.entries.length+' 行。'));
 }));
 dialog('导入 Excel 词表',[node('p',{},chinese?'按模板填写年级、上下册、所属单元、生字词、拼音。拼音可使用声调符号或数字，单字和词语均可。每份文件最多5000行、8MB。':'使用模板首行，不合并单元格。年级填写1—6，上下册填写“上册”或“下册”，音标可留空。每份文件最多5000行、8MB。'),field('词表名称',title),field('选择 .xlsx 文件',file),preview],[button('取消',closeDialog),save]);
}
function openRename(){const c=state.detail.collection;const input=node('input',{value:c.title,maxlength:'100'});dialog('修改词表名称',field('名称',input),[button('取消',closeDialog),button('保存',async()=>{await request('/catalog/collections/'+c.id,{method:'PATCH',body:{title:input.value.trim(),expectedRevision:c.revision}});closeDialog();await loadCollections();render();},'primary')]);}
function openEntry(entry){
 const chinese=isChineseCatalog();
 const collection=state.detail.collection;
 const e=entry||{grade:3,semester:'上册',unit:chinese?'第一单元':'Unit 1'};
 const grade=select([1,2,3,4,5,6].map(g=>[String(g),g+'年级']),String(e.grade),()=>{});
 const semester=select([['上册','上册'],['下册','下册']],e.semester,()=>{});
 const fields=chinese
  ?[['unit','所属单元',40],['english','生字词',100],['phonetic','拼音',200]]
  :[['unit','所属单元',40],['chinese','中文',300],['english','英文',100],['phonetic','音标（可选）',200]];
 const inputs=fields.map(([key,label,max])=>[key,label,node('input',{value:e[key]||'',maxlength:String(max)})]);
 const wordLabel=chinese?'生字词':'单词';
 dialog((entry?'编辑':'新增')+wordLabel,node('div',{class:'form-grid'},field('年级',grade),field('上下册',semester),
  inputs.map(([,label,input])=>field(label,input))),[button('取消',closeDialog),button('保存',async()=>{
   const word={grade:Number(grade.value),semester:semester.value};
   for(const [key,,input] of inputs)word[key]=input.value.trim();
   await request('/catalog/collections/'+collection.id+'/entries'+(entry?'/'+entry.id:''),{
    method:entry?'PATCH':'POST',body:{expectedRevision:collection.revision,entry:word}
   });
   closeDialog();
   await loadCollections();
   render();
   notify(wordLabel+'已保存');
  },'primary')]);
}
function renderRecitation(main){
 main.append(heading('背诵课文','按年级、册别和单元整理正文。孩子可以在 App 遮住原文练习。',[
  button('导入课文',()=>openLesson(),'primary')
 ]));
 const panel=node('section',{class:'panel'});
 if(!state.lessons.length)panel.append(empty('还没有背诵课文','可以粘贴正文或选择 UTF-8 TXT 文件。'));
 else panel.append(table(['课文','年级 / 册','单元','操作'],state.lessons.map(lesson=>node('tr',{},
  node('td',{class:'wrap'},lesson.title),node('td',{},lesson.grade+'年级 · '+lesson.semester),node('td',{class:'wrap'},lesson.unit),
  node('td',{},button('编辑',async()=>openLesson(await request('/recitation/lessons/'+lesson.id)),'quiet'),
   button('删除',()=>confirmDialog('删除课文？',lesson.title+'。已保存的背诵记录会保留。',async()=>{
    await request('/recitation/lessons/'+lesson.id+'?expectedRevision='+lesson.revision,{method:'DELETE'});
    await loadLessons();
    render();
    notify('课文已移除');
   }),'quiet danger'))
 ))));
 main.append(panel);
}
function openLesson(lesson){
 const key=requestKey();
 const grade=select([1,2,3,4,5,6].map(g=>[String(g),g+'年级']),String(lesson?.grade||1),()=>{});
 const semester=select([['上册','上册'],['下册','下册']],lesson?.semester||'上册',()=>{});
 const unit=node('input',{maxlength:'40',value:lesson?.unit||'',placeholder:'例如 第一单元'});
 const title=node('input',{maxlength:'100',value:lesson?.title||''});
 const content=node('textarea',{rows:'10',maxlength:'12000',value:lesson?.content||'',placeholder:'在这里粘贴课文正文，保留分行'});
 const file=node('input',{type:'file',accept:'.txt,text/plain'});
 file.addEventListener('change',()=>run(async()=>{
  const selected=file.files[0];
  if(!selected)return;
  try{
   if(!/\.txt$/i.test(selected.name)||selected.size>48000)throw new Error('请选择不超过48KB的 TXT 文件');
   let body;
   try{body=new TextDecoder('utf-8',{fatal:true}).decode(await selected.arrayBuffer());}
   catch{throw new Error('请将 TXT 文件另存为 UTF-8 编码');}
   if(body.length>12000)throw new Error('每篇课文最多12000字，请分段导入');
   content.value=body;
   if(!title.value.trim())title.value=selected.name.replace(/\.txt$/i,'').slice(0,100);
  }finally{file.value='';}
 }));
 dialog(lesson?'编辑课文':'导入背诵课文',[
  node('div',{class:'form-grid'},field('年级',grade),field('上下册',semester),field('所属单元',unit),field('课文标题',title)),
  field('从 UTF-8 TXT 读取正文（可选）',file),field('课文正文',content)
 ],[button('取消',closeDialog),button('保存课文',async()=>{
  const body={grade:Number(grade.value),semester:semester.value,unit:unit.value.trim(),title:title.value.trim(),
   content:content.value.trim(),expectedRevision:lesson?.revision||0};
  if(!body.unit||!body.title||!body.content)throw new Error('请填写所属单元、标题和正文');
  await request('/recitation/lessons'+(lesson?'/'+lesson.id:''),{method:lesson?'PATCH':'POST',headers:{'Idempotency-Key':key},body});
  closeDialog();
  await loadLessons();
  render();
  notify('课文已保存，请在 App 的“语文 → 背诵课文”查看');
 },'primary')]);
}
function renderWrong(main){
 const chinese=state.wrongSubject==='CHINESE';
 main.append(heading('孩子的错词本','家长在 App 批改后按科目汇总，保留累计错误次数。'));
 if(!state.children.length){main.append(node('section',{class:'panel'},empty('还没有孩子档案','请先在 App 添加孩子，再完成听写和家长批改。')));return;}
 const unresolved=state.wrong.filter(w=>!w.mastered);main.append(node('div',{class:'stats'},node('div',{class:'stat'},'累计错词',node('strong',{},state.wrong.length)),node('div',{class:'stat'},'待复习',node('strong',{},unresolved.length)),node('div',{class:'stat'},'已掌握',node('strong',{},state.wrong.length-unresolved.length))));
 let only=true;const check=node('input',{type:'checkbox',checked:true,onChange:e=>{only=e.target.checked;draw();}});const body=node('div');
 main.append(node('section',{class:'panel'},node('div',{class:'panel-body actions'},field('科目',select([['ENGLISH','英语'],['CHINESE','语文']],state.wrongSubject,subject=>run(async()=>{state.wrongSubject=subject;await loadWrong();render();}))),field('选择孩子',select(state.children.map(c=>[c.id,c.nickname+' · '+c.grade+'年级']),state.child,id=>run(async()=>{state.child=id;await loadWrong();render();}))),node('label',{class:'check'},check,'仅看未掌握'),button('刷新',async()=>{await loadWrong();render();})),body));draw();
 function draw(){const shown=state.wrong.filter(w=>!only||!w.mastered);body.replaceChildren();if(!shown.length){body.append(empty('暂时没有待复习错词','完成听写并提交家长批改后，错误单词会自动记录在这里。'));return;}
 body.append(table(chinese?['生字词','拼音','所属课程','错误次数','状态','操作']:['英文','中文','音标','所属课程','错误次数','状态','操作'],shown.map(w=>node('tr',{},node('td',{class:'english wrap'},w.english),chinese?null:node('td',{class:'wrap'},w.chinese),node('td',{class:'phonetic'},w.phonetic||'—'),node('td',{},[w.grade?w.grade+'年级':'',w.semester,w.unit].filter(Boolean).join(' · ')),node('td',{},w.mistakeCount),node('td',{},node('span',{class:'badge'+(w.mastered?' green':'')},w.mastered?'已掌握':'待复习')),node('td',{},button(w.mastered?'恢复待复习':'标为已掌握',async()=>{await request('/children/'+state.child+'/wrong-words/'+w.id,{method:'PATCH',body:{mastered:!w.mastered}});await loadWrong();render();},'quiet'))))));}
}
function renderGuide(main){main.append(heading('从词表到错词复习','家长整理一次，孩子可以反复练习。'),node('section',{class:'panel help'},node('h3',{},'1. 按模板整理 Excel'),node('p',{},'第一行依次为：年级、上下册、所属单元、中文、英文、音标。年级填写1—6，册别使用“上册”或“下册”，单元名称保持一致。同一年级、册别和单元内不要重复填写同一个英文。音标可留空，其他列必填。'),node('h3',{},'2. 预览、确认导入，再维护'),node('p',{},'工作台会检查错误行，校验通过后点击确认导入。导入为当前账号的独立词表，可筛选、编辑、新增和删除单词。导入其他版本教材时，建议使用不同词表名称。'),node('h3',{},'3. 在 App 选择听写范围'),node('p',{},'使用与工作台相同的账号和服务器，在“我的”选择孩子 → 英语 → 开始听写 → 选择词库、年级、上下册 → 单个单元或整册。更多设置可随机抽取指定数量。播报可选择读中文、读英文；设备语音需要对应离线语音包。每词等待可设置1—10秒，每次最多500词。完成情况与成绩可在“英语 → 听写记录”查询。'),node('h3',{},'4. 对照答案逐题批改'),node('p',{},'听写时记录有效练习用时，暂停和切后台不计时。完成后进入家长批改，按轮次、题号对照标准答案逐题标记正确或错误。全部标记后提交，按正确题数 / 总题数计算百分制成绩，保留一位小数。'),node('h3',{},'5. 错词入库和复习'),node('p',{},'提交批改后自动累计错词，多轮中每次写错都记一次，重复提交同一份批改不会重复计数。错词本可按孩子查看并标记掌握；再次写错会恢复为待复习。已提交的批改固定保存，词表修改不会改变历史答案。')));}
