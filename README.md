# 家庭学习助手

面向小学家庭的安卓 App。第一阶段实现家长登录、孩子档案、图片导入、英语词表和可配置听写。服务端采用 **JDK 1.8 / Spring Boot 2.7.18 / MyBatis 2.3.2 / MySQL 5.1.42**，客户端采用 Kotlin / Jetpack Compose。服务端使用 Java 8 语法与 API，可在现有 Eclipse 中导入 Maven 工程后运行。

## 第一阶段功能

- 新增 Web 词表工作台：启动 Java 服务后访问 `/web/`，使用 App 的家长账号登录，下载 Excel 模板、预览校验并导入，按年级、上下册、单元维护中英文与音标。App“导入词库听写”读取同账号词库，支持按单元全部听写或随机抽取，以及读中文或读英文。
- 听写有效用时统计（暂停、切后台及音频准备不计时），完成后由家长逐轮逐题对照答案标记对错，自动计算百分制成绩并累计错词。App 与 Web 均可查看错词、标记掌握状态。不是拍照自动批改。操作与升级说明见 [词库与批改使用说明](docs/catalog-and-review.md)。
- 邀请码注册、账号密码登录、刷新凭证轮换、退出撤销会话。密码使用 BCrypt，服务端只存会话凭证哈希，安卓使用 Keystore 加密存储凭证。
- 登录页和“我的”提供“选择 / 管理服务器”，支持新增、命名、编辑、删除非当前地址及选择切换。切换后重新登录；本机草稿、孩子缓存和音频按服务器及账号隔离。原有单个地址与当前账号的旧版草稿自动迁移。
- 创建、编辑、切换多个孩子，昵称、1—6年级、教材版本；所有学习资源在服务端核验账号与孩子归属。
- CameraX 拍照、系统多图选择、旋转、裁剪、排序，最多5张；本地识别图片保存在手机私有目录，选择云端识别时才上传到服务端私有存储并鉴权读取。
- 默认使用手机本地 OCR 免费识别单词表或课文候选词；中英文模型随 APK 打包，识别无需密钥、模型下载或上传照片。也可选择百炼云端识别。家长勾选、纠正并补齐中文释义后保存；编辑产生新版本，不改变历史听写内容。
- 独立查询单词释义、播放读音、编辑后保存到新词表；选择词条、读英文或中文释义、倒计时、每词重复次数、重复间停顿、书写间隔、整轮次数、随机顺序。
- 正式听写前准备所有音频；本地识别成功后自动选择设备离线语音，其他已有词表沿用已保存的语音设置。暂停继续、重播、上一词、下一词、提前结束；后台或音频焦点丢失暂停。
- 听写快照、播放事件去重、Room 草稿与进度缓存、断网后补传，以及历史任务恢复。
- 当前18张业务表采用服务端生成的 UUID 主键及外键。历史第一阶段数字 ID 数据可复制迁移到独立 UUID 新库。

后续阶段包括背诵语音自动识别、拍照自动判题、数学、综合错题分析、自动出题检测和完整级联删除。`DELETE /children/{id}` 用于档案停用和任务取消。

## 语文学习

- 在电脑打开 `/web/`，用家长账号进入“语文生字词”，下载 Excel 模板。按年级、上下册、所属单元、生字词、拼音五列填写。单字和词语均可，拼音可填 `chūn tiān` 或 `chun1 tian1`。预览通过后确认导入。
- 在 App 打开“语文 → 生字听写”，选择单元或整册。手机使用设备普通话语音读汉字词，拼音用于查看卡片及家长批改。多音字可导入带语境的词语。听写记录和错词按孩子、科目查询。
- 在 Web 的“背诵课文”选择“导入课文”，填写年级、册别、单元、标题，粘贴正文或选择 UTF-8 TXT，每篇最多12000字。App 可查看原文、遮住正文练习，由家长记录“已完成”或“需再练习”。背诵自动识别留到统一接入 AI 的阶段。
- 在 App 的“汉字笔顺”输入一个字，使用上一笔、下一笔或自动播放。离线数据覆盖9,574个字形，来源为 [Make Me a Hanzi](https://github.com/skishore/makemeahanzi/tree/bddc96d41bef78427ed0e034e9f7e31d71fd1b92)。授权和来源说明位于 `android/app/src/main/assets/strokes/`。

升级时先在 Eclipse 刷新工程并重启服务端。Flyway 自动执行 V4，给旧词库和听写标记英语科目，新增课文和背诵记录表。已有英语资料可直接使用。服务启动后覆盖安装 0.5.0 APK。

## 目录

```text
android/       安卓工程与 Gradle Wrapper
server/        Java API、Worker、MySQL迁移、测试
scripts/       本地环境初始化与运行脚本
docs/          接口和验证说明
compose.yml    API、Worker容器，连接已有数据库
.env.example   配置模板，不包含实际密钥
```

## 启动服务

使用 Eclipse 和本机 MySQL 开发时，见 [Eclipse 本地开发环境](docs/eclipse-local-development.md)，其中包含导入、建库、自动建表和调试步骤。建库脚本位于 `scripts/sql/00_create_local_database.sql`，MySQL 5.1 业务表由 `server/src/main/resources/db/mysql51-uuid` 下的迁移自动创建，迁移历史记录在 `family_uuid_schema_history`。

本机开发需要 JDK 1.8、Maven 3.5+ 和已有 MySQL 5.1.42。确认 `java -version` 和 `mvn -version` 均使用预期的 JDK 1.8。开发机使用 HTTP 仅用于调试；公网或正式使用应在入口配置 HTTPS。

先执行建库脚本并配置数据库账号。Eclipse 使用 `server/config/application-local.yml`；选择 PowerShell 脚本启动时，在工程根目录执行：

```powershell
./scripts/init-env.ps1
# 本机已有 .env 时跳过初始化，直接编辑现有配置。
# 将 .env 的 DB_URL、DB_USER、DB_PASSWORD 填成刚创建的实际数据库连接。
./scripts/run-server.ps1 -Role all
```

初始化脚本只在 `.env` 不存在时创建连接模板、随机密码占位及邀请码，不输出密码，也不自动更改 MySQL 用户密码。必须把 `DB_PASSWORD` 改成数据库账号的实际密码。需要云端 AI 时填写 `DASHSCOPE_API_KEY`，获取 `INVITE_CODE` 后用于 App 注册。邀请码初始20次、有效30天；同一邀请码重启不会重置使用次数。

服务默认 `http://localhost:8080`，`GET /health` 检查进程存活。使用 UTF-8 三字节字符集、秒级数据库时间、JSON 文本列及原子 UPDATE 任务领取。表情和扩展汉字不能保存到普通文字字段，API 会在写库前拒绝；JSON 快照使用转义文本保留原值。MySQL 开启二进制日志时须使用 `binlog_format=ROW`，启动时会检查，不自动更改数据库设置。

大词表需要 `max_allowed_packet` 至少 4 MB，建议配置为 16 MB；检查与配置步骤见本地开发说明。本次改造使用历史版本的数据库和配套迁移工具满足本地试用环境，长期线上维护需要另行规划支持与升级。

管理员可在本机已配置Java与数据库后执行 `./scripts/reset-password.ps1 -LoginName 家长账号`，通过隐藏输入设置新密码并撤销该账号所有旧会话；没有开放远程重置接口。

另可用 Compose 运行 API 和 Worker，连接已有 MySQL。Compose 不再创建 MySQL 8 容器，也不更改本机 5.1 服务。Windows Docker Desktop 中连接宿主机可设置：

```powershell
$env:DB_URL='jdbc:mysql://host.docker.internal:3306/family_learning_uuid?useUnicode=true&characterEncoding=UTF-8&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC'
docker compose up --build -d
```

数据库账号需要允许容器实际来源连接；仅授权 `family@localhost` 的账号用于 Eclipse 本机启动。API 和 Worker 共享私有文件卷，只有 API 负责迁移。也可用脚本分别以 `-Role api`、`-Role worker` 启动，需给后者设置不同 `PORT`。本机数据目录默认 `server/data/uploads`，通过 API 鉴权读取；云端私有对象存储适配属于后续工作。

当前业务表全部采用 UUID 主键，新库默认名为 `family_learning_uuid`。旧数字 ID 版本的数据可用 [UUID 迁移工具](docs/uuid-migration.md) 复制到独立新库，工具会重写外键和历史快照，并保留源库。历史 SQL 归档在 `docs/legacy`，当前启动不扫描这些脚本。

## App 页面与听写流程

服务端0.4.1补充听写范围：新建单元听写标题显示具体单元（例如“词库名 · 3年级上册 · Unit 1”），整册显示“整册”，听写页面、批改及历史共用此标题。仅标题改进兼容0.4.0 App；单词卡片需更新0.4.1 App，默认隐藏答案，暂停查看英文、中文及音标，继续听写或切词时收起。已创建的旧听写保留原始标题，新建一次听写即可看到单元。无需SQL或重新导入词库。

底部入口为英语、语文、数学、我的。孩子在“我的”切换，当前孩子在科目首页显示。英语 → 开始听写 → 选择词库、年级和上下册 → 选择单个单元或整册 → 读中文/英文、等待1—10秒 → 听写 → 家长批改 → 保存分数和错词。听写记录可按进行中、待批改、已批改筛选并继续听写或查看成绩。随机抽词与次数等放在更多设置。

## AI 配置与设备语音

安卓默认选择“本地识别 · 免费”：我的 → 词表工具 → 拍照或选图识词 → 本地识别 → 拍照/选图并裁剪 → 本地识别单词 → 核对英文和中文释义 → 确认并保存词表 → 开始所选单词听写。本地路径不调用云端 AI，无需 `DASHSCOPE_API_KEY` 或 Worker；Java 服务和 MySQL 继续负责登录、档案、词表保存及听写记录，保存仍需联网。

本地识别使用随 APK 打包的 ML Kit 中英文 OCR 模型（`com.google.mlkit:text-recognition-chinese:16.0.1`），不使用 Play Services 动态下载版本。它提取照片中的文字，不提供自动翻译：只有英文时中文栏留空，需补齐才能保存；清晰的同一行中英文以及左右分列且行对齐的释义可自动配对，必须人工复核。课文模式返回去重的单词候选，不自动按课程筛选重点词；最多保留500项并提示分批处理。照片和文字识别过程留在手机，确认后的词表按原流程保存到家庭服务。

本地识别成功后会把语音来源设为“设备语音”，需要在手机系统的文字转语音设置中安装英语、中文离线语音包。若设备没有可用语音包，App 会明确提示；不会自动回退到付费云端服务。手动切换“云端AI识别”或“云端语音”后，仍需下面的百炼配置。

最新安装包为 `artifacts/family-learning-0.5.0-debug.apk`（versionCode=7），服务端包为 `artifacts/family-learning-server-0.5.0.jar`。打包分发服务端请使用 `mvn -Pportable package`，该 profile 使用环境变量配置，避免把个人开发数据库密码写入 JAR。Eclipse 使用原有本地配置。

0.2.1 已加入的服务器列表与键盘避让继续保留：登录页上方或“我的” → 选择 / 管理服务器 → 新增服务器 → 填写名称和根地址 → 保存 → 在列表点击选择 → 登录。地址编辑页支持随键盘调整空间和焦点滚动；切换服务器仅清除本机当前登录凭证，旧服务器的草稿保留，切回并登录原账号后可恢复。删除地址只移除本机地址记录，当前地址需先切换后才可删除。

本地 OCR 操作说明参见本节，验证结果见 [verification.md](docs/verification.md)。SDK 集成方式见 [ML Kit 官方 Android 文档](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)。

照片识别调用百炼兼容接口 `/compatible-mode/v1/chat/completions`，默认 `qwen3-vl-plus`；语音调用 `/api/v1/services/aigc/multimodal-generation/generation`，默认 `qwen3-tts-flash`、Cherry 音色。按账号实际开通的地域和模型设置 `AI_BASE_URL`、`VISION_MODEL`、`SPEECH_MODEL`。新版或其他模型若使用不同协议，需要调整对应适配器，不能只替换名称假定兼容。

每账号每天默认最多创建1000个AI任务，可通过 `AI_DAILY_JOB_LIMIT` 调整。复用已成功任务不额外创建；单任务瞬时失败最多重试2次。任务数限制不是精确的费用预算，实际费用仍应在供应商控制台设置限额并核对用量。

尚未配置密钥时，识别和云端语音返回明确错误，不产生伪造识别结果。仍可手动建立词表，并在“我的 → 听写默认设置”中选择设备语音；手机需安装英文或中文语音包。**设备语音是显式试用备选，系统不会悄悄切换语音来源。** 云端正式接入需在目标账号上用真实教材图片及发音样本再验证。

## 构建和安装安卓

使用 Java 21（或兼容的Android构建JDK）、Android SDK 35与Build Tools 35.0.0。安装 Android Studio 后设置 `ANDROID_HOME`，或在 `android/local.properties` 写入本机 `sdk.dir`；该文件不进入版本控制。

这里的较新 JDK 仅用于安卓 Gradle 构建；服务端 Eclipse/Maven 继续使用 JDK 1.8。两个工程分别配置 JDK，无需将系统所有项目统一切换版本。

```powershell
cd android
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

模拟器的服务地址填写 `http://10.0.2.2:8080`；真机填写开发电脑的局域网地址并处于同一网络。也可通过USB执行 `adb reverse tcp:8080 tcp:8080`，然后填写 `http://127.0.0.1:8080`。在手机上填写 `localhost` 访问的是手机自身。

调试 APK 允许 HTTP；release 默认禁止明文流量，不带调试签名。第一阶段交付的是调试试用包，正式签名文件由项目方单独保管。

## 验证

服务端纯单元测试：`mvn -B -ntp test`。MySQL 集成测试必须显式提供专用数据库，缺少配置时显示跳过，不用 H2 替代 MySQL：

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/family_learning_uuid_test?useUnicode=true&characterEncoding=UTF-8&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC'
$env:TEST_DB_USER='family_test'
# 在本机安全设置 TEST_DB_PASSWORD，不把密码写进仓库或聊天。
./scripts/test-server.ps1
```

测试会迁移并写入所指定的库，**请勿指向实际家庭学习库**。识别协议测试使用本地 HTTP 模拟供应商，不调用付费AI。最新实际结果及验证边界见 `docs/verification.md`。

UUID 数据复制测试另需配对设置 `TEST_LEGACY_DB_URL`、`TEST_MIGRATION_DB_URL`，库名分别以 `_uuid_legacy_test`、`_uuid_copy_test` 结尾。这两个专用库的业务表会被测试重建；配置与迁移操作见 [UUID 迁移说明](docs/uuid-migration.md)。

服务启动后，还可设置本机 `SMOKE_PASSWORD` 并执行 `./scripts/smoke-test.ps1 -BaseUrl http://127.0.0.1:8080 -LoginName 测试账号`。该脚本使用专用测试账号建立并停用 SmokeChild，验证词表、听写快照、进度和退出，不调用付费AI；停用不是物理删除。
