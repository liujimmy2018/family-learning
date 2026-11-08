# UUID 主键与旧数据迁移

所有 12 张业务表都使用服务端生成的 UUID v4 主键 `id`，采用标准小写 36 位格式，例如 `550e8400-e29b-41d4-a716-446655440000`。主键及引用字段统一为 `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin`，不使用自增列。版本号、词条顺序、大小和时间戳仍保留数值类型。

## 新库初始化

执行 [建库脚本](../scripts/sql/00_create_local_database.sql) 创建 `family_learning_uuid`，填写实际数据库密码。启动服务时，Flyway 自动执行 [UUID V1](../server/src/main/resources/db/mysql51-uuid/V1__phase_one.sql) 和 [V2](../server/src/main/resources/db/mysql51-uuid/V2__idempotent_list_revisions.sql)，历史表为 `family_uuid_schema_history`。Flyway 自身的版本排序字段由框架管理，不属于业务主键。

MySQL 仍使用 5.1.42，Java 仍使用 JDK 1.8。MyBatis INSERT 显式写入已生成的 UUID，不读取数据库自增值。API 的业务 ID 均为 UUID 字符串；旧数字 ID 和缩写 UUID 返回 400。安卓端继续使用字符串 ID，当前 APK 可连接新服务。

## 已有数字 ID 数据

迁移采用“旧库只读、新库写入”的方式，不在原表上直接 ALTER。当前工具适用于本项目此前交付的 MySQL 5.1 数字 ID 版本。原 SQL 已归档到 [历史 SQL](legacy/mysql51-autoincrement/V1__phase_one.sql)，不再放在应用运行时资源目录中。MySQL 8 的历史 SQL 也仅供查阅，未验证跨数据库大版本的数据降级。

迁移会保留学习内容及关联，转换账号、孩子、图片、教材、AI 任务、词表、词表版本、词条、听写、播放事件及邀请码的主键，并重写 JSON 中的图片和历史听写词条引用。密码哈希、词表版本、进度、内容、创建时间和文件 object_key 保留。

## 操作步骤

1. 先让 App 同步未提交的进度和草稿，再退出登录。停止旧版本 API、Worker 及所有向旧库写入的程序。
2. 备份旧数据库和私有图片音频目录。新建独立目标数据库 `family_learning_uuid`，保留原数据库 `family_learning`；目标业务表必须为空。若 `family` 用户已存在，跳过建库脚本的 CREATE USER，只为新库授权。
3. 用 JDK 1.8 在 `server` 执行 `mvn -B -ntp clean package`。本次交付包也已更新为 UUID 版本，源码构建用于确保脚本的 `server/target` 文件与当前代码一致。
4. 在本机 PowerShell 安全设置源、目标密码环境变量，然后运行下面的命令。密码不填入脚本、命令历史或聊天；本机可从密码管理器读取或用隐藏输入设置。

```powershell
cd D:\code\ai-develop-team\repos\family-learning
$env:JAVA_HOME='D:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
$env:Path=$env:JAVA_HOME+'\bin;'+$env:Path

# MIGRATION_SOURCE_DB_PASSWORD：旧库账号密码。
# DB_PASSWORD：目标新库账号密码，执行前在当前进程中安全设置。
$query='?useUnicode=true&characterEncoding=UTF-8&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC'
./scripts/migrate-uuid.ps1 `
  -SourceDatabaseUrl ('jdbc:mysql://127.0.0.1:3306/family_learning'+$query) `
  -TargetDatabaseUrl ('jdbc:mysql://127.0.0.1:3306/family_learning_uuid'+$query) `
  -SourceDatabaseUser family `
  -TargetDatabaseUser family
```

可用以下隐藏输入方式设置密码，不在终端显示明文：

```powershell
function Set-LocalPassword([string]$Name) {
    $secure=Read-Host $Name -AsSecureString
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Environment]::SetEnvironmentVariable($Name,[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer),'Process') }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
Set-LocalPassword MIGRATION_SOURCE_DB_PASSWORD
Set-LocalPassword DB_PASSWORD
```

5. 命令成功后打印每张表的复制数量。核对源、目标表记录数与部分学习内容，再把 Eclipse 本地配置或 `.env` 的 DB_URL 指向 `family_learning_uuid`，启动新版本服务。迁移命令必须单独执行，目标库在迁移期间不运行 API 或 Worker。
6. `STORAGE_PATH` 沿用旧版私有文件目录，或先完整复制目录再指向副本。本工具不移动、上传或删除图片音频文件，文件 object_key 保持不变。
7. App 重新登录后检查孩子、词表及历史听写。迁移前未退出的旧客户端应在确认草稿与进度已同步后清理旧缓存或应用数据，再登录；旧数字 ID 的离线请求不能继续补传。
8. 清理当前终端中的密码变量：`$env:MIGRATION_SOURCE_DB_PASSWORD=$null; $env:DB_PASSWORD=$null`。验收期间保留原库和备份。

## 迁移边界与失败处理

- 源、目标数据库名称必须不同。目标非空会拒绝执行，脚本不会清空目标或覆盖已有记录。
- 转换数据在目标 InnoDB 事务中完成，关联缺失或复制失败会回滚本次目标写入；源库只做读取。若连接在提交时中断，请先核对目标记录数与日志，再决定后续操作，不能盲目重跑。
- 所有旧登录会话在新库中标为撤销，用户使用原账号密码重新登录。尚未结束的 AI 任务标为取消，需重新发起，避免旧 Worker 发布旧 ID 的结果。
- 已过期并从原库清理的图片无法恢复。对于这类 JSON 引用，工具分配一致的 UUID 占位并汇总数量，不创建虚假的图片记录；原本不可访问的图片仍不可访问。
- 目标迁移历史表记录的是新 UUID 结构，不复制旧历史表。不要手工修改旧迁移校验值或删除原库来绕过检查。

## 验证

自动化测试覆盖全部业务表的 UUID 列及无自增属性、数字 ID 拒绝、跨账号隔离、听写快照、4 Worker 并发，以及旧数据转换、异常回滚、非空目标拒绝。迁移测试使用两个额外专用数据库，库名须分别以 `_uuid_legacy_test`、`_uuid_copy_test` 结尾；测试会重建其中的业务表，不能使用真实学习数据库。

完整测试环境变量为 `TEST_DB_URL`、`TEST_DB_USER`、`TEST_DB_PASSWORD`，以及可选配对的 `TEST_LEGACY_DB_URL`、`TEST_MIGRATION_DB_URL`。未设置数据库变量时，对应集成测试跳过。实际执行结果见 [验证记录](verification.md)。
