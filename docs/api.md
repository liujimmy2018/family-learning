# 家庭学习助手 API

## 0.5.0 语文

- 词库列表、确认导入、Excel 预览和模板下载支持 `?subject=CHINESE`，省略时为 `ENGLISH`。语文模板为年级、上下册、所属单元、生字词、拼音五列。复用词条结构，语文条目的 `english` 为生字词，`phonetic` 为拼音，`chinese` 由服务端保存为同一生字词以兼容原快照。
- 创建词库听写沿用 `POST /children/{child}/catalog-practices`，科目由词库确定。语文设置使用 `mode=CHINESE`。返回的听写包含 `subject`，语文词条的 `text` 为生字词、`phonetic` 为拼音。听写历史和错词查询支持 `?subject=CHINESE`。
- `GET /recitation/lessons` 返回家长账号下的课文列表，`GET /recitation/lessons/{id}` 返回含正文的详情。新建 `POST /recitation/lessons` 接收 `grade,semester,unit,title,content` 和幂等键。编辑 `PATCH /recitation/lessons/{id}` 另传 `expectedRevision`，删除时通过查询参数传版本。
- `POST /children/{child}/recitation-attempts` 传 `lessonId,lessonRevision,result` 和幂等键。结果为 `COMPLETED` 或 `REVIEW`。`GET` 同一路径返回该孩子最近100条手动记录。记录保留当时的标题、课程范围及课文版本。

## 0.4.1：词库、听写历史与家长批改

以下接口继续使用家长 Bearer 凭证；网页静态入口为 `/web/`，无需额外 Web 服务或独立账号。服务端兼容 JDK8 / MySQL5.1，V3 新增4张 UUID 表。历史接口继续兼容。

| 方法 | 路径（均带 `/api/v1` 前缀） | 功能 |
| --- | --- | --- |
| GET | `/catalog/template.xlsx` | 下载六列表头和3行示例的xlsx模板（二进制响应） |
| POST | `/catalog/preview` | multipart字段`file`，返回`entries/errors/valid/count`；仅预览，不落库 |
| GET / POST | `/catalog/collections` | 列出当前账号词表 / 确认导入；POST需`Idempotency-Key` |
| GET / PATCH / DELETE | `/catalog/collections/{id}` | 读取、重命名或移除词表 |
| POST | `/catalog/collections/{id}/entries` | 新增词条 |
| PATCH / DELETE | `/catalog/collections/{id}/entries/{entry}` | 编辑或删除词条 |
| POST | `/children/{child}/catalog-practices` | 按年级、册别、单元选词或抽样，生成不可变答案；需`Idempotency-Key` |
| POST | `/children/{child}/practices/{id}/timing` | 保存有效练习秒数；重复同步取较大值，批改完成后不再修改 |
| GET / POST | `/children/{child}/practices/{id}/review` | 查看标准答案、来源、计时和成绩 / 提交家长批改 |
| GET | `/children/{child}/dictation-history` | 分页查询完成情况、用时、批改状态与成绩 |
| GET | `/children/{child}/wrong-words` | 当前孩子错词，最多5000条，含累计错误次数和掌握状态 |
| PATCH | `/children/{child}/wrong-words/{id}` | 设置`{mastered:true/false}` |

确认导入请求：`{title,entries:[{grade,semester,unit,chinese,english,phonetic}]}`，最多5000行。`semester`仅为`上册/下册`。音标可空，其他字段必填；同一词表内按年级、册别、单元及规范化英文判重。预览有错误时，前端不允许确认；服务端对确认请求再次独立校验。

编辑词条请求：`{expectedRevision,entry:{grade,semester,unit,chinese,english,phonetic}}`；重命名为`{expectedRevision,title}`。删除通过查询参数传`expectedRevision`。不匹配返回409，整次修改回滚。

词库听写请求：`{collectionId,expectedRevision,grade,semester,units:["Unit 1"],selection:"ALL"或"RANDOM",count:20,settings:{...原Settings字段...}}`。App按单元时提交选中的单元，整册时提交空`units`，表示该年级册别所有单元。随机抽取不放回，单次最多500词；同一请求标识重试返回原听写，不重新抽样。返回原有`PracticeDto`，音标和课程来源在`review.words`中提供。0.4.1开始，听写标题包含单元或“整册”；返回的`PracticeDto.words`及固定答案快照增加可选`phonetic`字段，旧快照缺少该字段时按空串读取。新版App的新听写等待时间为1—10秒，API保留1—120秒以兼容旧客户端和历史快照。

听写历史查询参数：`filter=ALL|PENDING|UNREVIEWED|GRADED`（默认ALL）、`page`（从0开始，最大10000）、`size`（默认20，1—100）。返回`{items:[{practice:PracticeDto,elapsedSeconds,graded,score,correct,total,createdAt}],page,size,hasMore}`，按创建时间、ID倒序。`createdAt`为UTC ISO时间；未批改时`score/correct`为null，不能当作0分。PENDING排除已完成和提前结束，UNREVIEWED仅已完成未批改，GRADED为已提交成绩。查询只返回当前家长所选孩子的数据。0.4.0沿用V3，无新增数据库迁移。

计时请求：`{elapsedSeconds:83}`，范围0—604800秒。批改请求：`{elapsedSeconds:83,marks:[{round:0,index:0,correct:true,written:""},...]}`，最多1500项。轮次及题号从0开始，必须覆盖全部轮次和词条，禁止重复、越界或缺少`correct`。仅COMPLETED听写可批改；每题等权，百分制保留1位小数。`written`可选，不参与自动判分。服务端依标准答案快照入库错词，不信任客户端提供的答案文字。

批改返回：`{practiceId,title,status,words:[{id,ordinal,english,chinese,phonetic,grade,semester,unit}],rounds,elapsedSeconds,graded,marks,score,correct,total}`；未批改时`score/correct`为null。成绩首次提交后固定保存；相同内容重试返回原结果，其他内容返回409，错词累计与成绩提交处于同一数据库事务。

## 原有第一阶段接口

前缀 `/api/v1`。成功返回 `{code:"OK",data:...,requestId:...}`；失败包含稳定的 `code` 与可读 `message`。所有家庭资源接口需 `Authorization: Bearer <accessToken>`。所有业务 ID 使用标准小写 36 位 UUID 字符串，服务端生成 UUID v4；数字 ID、缩写及非规范格式返回 400。版本号、词条序号与 UTC 时间戳仍是数值。

例如 `child`、`id`、`listId`、`itemIds`、`assetIds`、`recognitionJobId` 和登录返回的 `accountId` 均使用 `550e8400-e29b-41d4-a716-446655440000` 这样的格式。旧数据升级步骤见 [UUID 迁移说明](uuid-migration.md)。

| 方法与路径 | 内容 |
|---|---|
| POST /auth/register | loginName、password、inviteCode |
| POST /auth/login | loginName、password |
| POST /auth/refresh | refreshToken，成功后旧访问和刷新凭证均失效 |
| POST /auth/logout | 撤销当前会话 |
| GET /children | 当前账号孩子列表 |
| POST /children | nickname、grade、textbook、expectedVersion=0 |
| PATCH /children/{child} | 同上，expectedVersion为当前版本 |
| DELETE /children/{child} | 停用档案并取消任务，非物理删除 |
| POST /children/{child}/assets | multipart字段file，仅JPEG/PNG |
| GET /children/{child}/assets/{id}/content | 鉴权文件内容，过期不可读 |
| POST /children/{child}/recognitions | title、type=WORD_TABLE或TEXTBOOK、assetIds；202返回任务 |
| GET /children/{child}/jobs/{id} | 状态、result或errorCode/errorMessage |
| GET /children/{child}/lists | 词表和当前内容版本 |
| POST /children/{child}/lists | title、words[{text,meaning}]，可带recognitionJobId |
| GET /children/{child}/lists/{id} | 当前版本详情 |
| POST /children/{child}/lists/{id}/versions | 词表全量内容及expectedVersion，生成新版本 |
| POST /children/{child}/audio-preparations | text、language=en-US或zh-CN，202返回任务 |
| POST /children/{child}/word-lookups | text，202返回释义查询任务，可复用已成功的结果 |
| POST /children/{child}/practices | listId、expectedListVersion、itemIds、settings |
| GET /children/{child}/practices | 最近50个听写任务 |
| GET /children/{child}/practices/{id} | 固定词条顺序、设置、进度和版本 |
| POST /children/{child}/practices/{id}/events | expectedVersion、status、round、itemIndex、events |

创建识别、创建及修订词表、创建听写均须提供 `Idempotency-Key`（8—80位字母、数字、下划线或短横线）。相同键不同内容返回409。语音按账号、孩子及请求内容复用任务和音频，失败后可重新准备。

```json
{
  "mode": "ENGLISH",
  "countdownSeconds": 5,
  "intervalSeconds": 10,
  "repeats": 2,
  "repeatGapSeconds": 1.0,
  "rounds": 1,
  "randomOrder": false
}
```

轮次和项目索引从0开始。每个播放事件包含UUID `eventId`、`action`、`round`、`itemIndex` 和 `occurredAt`；`action` 支持 PLAY、PAUSE、PREVIOUS、NEXT、REPLAY、RESUME、COMPLETE、STOP。进度提交的status支持 READY、PAUSED、COMPLETED、STOPPED。事件有唯一约束，不以重复播放次数生成新的题目或成绩。

识别任务只返回候选单词，必须经家长确认后调用建表接口。AI未配置返回任务失败 `AI_NOT_CONFIGURED`；无法辨认内容不会自动当成孩子写错。批改采用家长手动标记，尚无手写识别自动判题接口。
