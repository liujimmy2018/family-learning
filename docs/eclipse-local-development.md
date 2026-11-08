# Eclipse 本地开发环境

使用 Eclipse 导入 `server` 目录的 Maven 工程，配置 JDK 1.8 和本机 MySQL 5.1.42-community 后，直接运行 `LearningApplication`。安卓工程位于 `android`，使用 Android Studio；两者通过 HTTP API 联调。

## 准备 Java 和 MySQL

需要支持 Java 8 的 Eclipse Java 开发环境及 Maven 集成 m2e。Maven 工程不依赖额外安装的 Tomcat，Spring Boot 会启动内嵌服务器。项目的 Java 版本由 `server/pom.xml` 中的 `java.version=1.8` 指定，使用 Spring Boot 2.7.18、MyBatis 2.3.2、Flyway 4.2.0 和 MySQL Connector/J 5.1.49。JDBC 驱动类为 `com.mysql.jdbc.Driver`，已在应用配置中指定。

本机已有本次兼容性构建使用的 JDK 1.8，可在 Eclipse 中选用：

```text
D:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot
```

在 `Window → Preferences → Java → Installed JREs → Add → Standard VM` 添加该目录。项目的 `Java Build Path → Libraries` 使用 JavaSE-1.8，`Java Compiler` 使用 1.8；启动配置的 JRE 也选择该 JDK。请选择完整 JDK，而非仅有运行环境的 JRE。

命令行执行 `java -version` 和 `mvn -version`，确认二者均为 1.8。本机原有 Maven 曾指向另一个旧 JDK；可只在当前 PowerShell 中设置，不影响其他项目：

```powershell
$env:JAVA_HOME='D:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
$env:Path=$env:JAVA_HOME+'\bin;'+$env:Path
java -version
mvn -version
```

安卓 Gradle 构建仍使用较新 JDK，按 Android Studio 的工程设置单独配置；无需更改服务端 JDK。

以下步骤使用你已有的 MySQL 5.1.42，端口按实际安装设置填写，示例为 3306。先启动本机 MySQL 服务，再用管理员账号连接。本次兼容测试使用官方同版本安装包的独立实例 33081，未更改你的现有服务、端口或数据。

MySQL 关闭二进制日志时可直接使用。若启用了二进制日志，任务队列的 InnoDB READ COMMITTED 事务要求 `binlog_format=ROW`；应用启动会检查并在不满足条件时给出错误，不自动修改服务器设置。可用管理员执行 `SELECT @@log_bin, @@binlog_format;` 核对；启用日志但非 ROW 时，请由管理员在现有 MySQL 配置中调整后再启动本工程。

批量识别结果会以 JSON 文本写入数据库。请同时检查 `SHOW VARIABLES LIKE 'max_allowed_packet';`，完整词表容量需要至少 4 MB，建议在 MySQL 配置的 `[mysqld]` 下设置 `max_allowed_packet=16M` 后重启 MySQL。本次隔离测试使用 16 MB。较小的旧默认值通常可完成少量单词测试，但可能拒绝大词表的数据包。

## 建库和建表

先编辑 [建库脚本](../scripts/sql/00_create_local_database.sql)，将 `CHANGE_ME_to_your_own_local_password` 改成自己的数据库密码，再使用 MySQL 管理员执行。脚本只创建 `family_learning_uuid` 库和本机账号 `family`，并授予该库的权限，不创建或删除业务表。

可以在数据库客户端中打开并执行脚本，也可在 MySQL 命令行中执行：

```text
mysql -h 127.0.0.1 -P 3306 -u root -p
```

登录后输入：

```sql
SOURCE D:/code/ai-develop-team/repos/family-learning/scripts/sql/00_create_local_database.sql;
```

业务建表 SQL 已在工程中：

- [V1__phase_one.sql](../server/src/main/resources/db/mysql51-uuid/V1__phase_one.sql)：MySQL 5.1 专用的 12 张业务表、索引及外键。
- [V2__idempotent_list_revisions.sql](../server/src/main/resources/db/mysql51-uuid/V2__idempotent_list_revisions.sql)：词表版本的标题与幂等请求字段。
- [V3__catalog_and_dictation_review.sql](../server/src/main/resources/db/mysql51-uuid/V3__catalog_and_dictation_review.sql)：Excel词库、家长批改和错词的4张UUID表。

正常搭建只执行建库脚本，然后启动应用。显式配置的 Flyway 4 自动依次执行 V1、V2、V3，并维护 `family_uuid_schema_history`；Spring Boot 自带的新版本 Flyway 自动配置已排除。V3 新增词库、批改和错词的4张表。不要先手工执行迁移脚本再启动迁移，否则非空库缺少迁移历史时会失败。已执行的迁移文件不要修改，后续结构变化在 `db/mysql51-uuid` 新增版本脚本。独立 Worker 可通过 `DB_MIGRATIONS_ENABLED=false` 关闭迁移，由 API 先完成建表。

脚本中的 `CREATE USER` 用于首次创建账号；如果 `family@localhost` 已存在，跳过该语句，只授权并在应用配置使用原密码。脚本不会覆盖已有用户密码。已有数据库含重要数据时，不要通过删库解决迁移问题。

业务主键和外键已改为 UUID，新库默认名为 `family_learning_uuid`。旧数字 ID 数据请按 [UUID 迁移说明](uuid-migration.md) 复制到新库，原库保留。旧 SQL 已归档在 `docs/legacy`，不随运行时资源发布；未提供 MySQL 8 数据自动降级流程。

## 导入 Eclipse 工程

1. 选择 `File → Import → Maven → Existing Maven Projects`。
2. Root Directory 选择 `D:\code\ai-develop-team\repos\family-learning\server`。
3. 勾选检测到的 `pom.xml`，点击 Finish。不要勾选复制到工作区，以便继续使用当前代码目录。
4. 右键工程，执行 `Maven → Update Project`，等待依赖下载完成。
5. 确认项目使用 JDK 1.8；如果之前已导入过，执行 Maven Update Project，并将原 JavaSE-21 的构建路径和启动配置改为 JavaSE-1.8。缺少 Maven 导入菜单时，需要先为 Eclipse 安装 m2e。
6. 在工程 `Properties → Resource → Text file encoding` 中选择 UTF-8，避免中文源码在旧 Eclipse 默认编码下显示异常。

无需执行 `mvn eclipse:eclipse`。Eclipse 的 Maven 集成会根据 POM 管理依赖。

## 配置本地参数

复制配置模板，在 PowerShell 执行：

```powershell
cd D:\code\ai-develop-team\repos\family-learning
if (Test-Path server\config\application-local.yml) { throw '本地配置已存在，请直接编辑。' }
Copy-Item server\config\application-local.yml.example server\config\application-local.yml
```

该命令用于首次创建本地配置；文件已经存在时保留现有配置，不重复复制覆盖。在 Eclipse 中刷新项目后，编辑 `application-local.yml`：

| 配置 | 填写内容 |
| --- | --- |
| `spring.datasource.url` | 本机 MySQL 地址、端口和库名，模板使用 127.0.0.1:3306/family_learning_uuid |
| `spring.datasource.username` | `family` |
| `spring.datasource.password` | 与建库脚本或现有数据库账号一致的密码 |
| `app.role` | 保持 `all`，同一进程运行 API 和 AI 任务 Worker |
| `app.invite-code` | 自己设置的非空注册邀请码，启动时写入哈希，可供 App 注册 |
| `app.ai-key` | 实际百炼 API Key；未申请时可先留空 |
| `app.storage-path` | 本机私有文件保存目录，模板已填写当前工程下路径 |

`server/config/application-local.yml` 已加入 Git 忽略规则，密码和密钥不应提交。配置保存在源码资源目录之外，不会打入服务端 JAR；启动时以 `server` 为工作目录，由 Spring Boot 从 `./config/` 加载。

Eclipse 直接运行 Java 不会自动加载根目录 `.env`；该文件目前由 PowerShell 启动脚本和 Compose 使用。本说明使用 `local` Profile，无需再把 `.env` 逐项导入 Eclipse。已有 Eclipse Environment 配置时，请检查是否覆盖了本地参数。

## 启动和调试

工程提供 [FamilyLearningLocal.launch](../server/FamilyLearningLocal.launch)。在 Eclipse 刷新工程后，可右键该文件，使用 Run As 或 Debug As 中对应的启动项。配置已分别填写主类、程序参数、JVM 参数、工作目录和 UTF-8 控制台编码，JDK 沿用项目设置。默认 Eclipse 工程名为 `family-learning-server`；如果导入时改过名称，请在启动配置中同步修改 Project 和 Working directory。

选择 `Run → Run Configurations → Java Application`，新建配置：

| 页签 | 设置 |
| --- | --- |
| Main | Project 选择导入的服务端工程；Main class 为 `cn.family.learning.LearningApplication` |
| Arguments | Program arguments 填 `--spring.profiles.active=local` |
| Arguments | VM arguments 可填 `-Dfile.encoding=UTF-8 -Djava.awt.headless=true` |
| JRE | 选择 JDK 1.8 |
| Arguments 中的 Working directory | 选择 `server` 工程目录 |

点击 Run 启动，或点击 Debug 启动断点调试。无需安装 Spring Tools 插件；已安装时也可使用 Spring Boot App 启动方式，但同样需要设置 Profile 和 JDK。

也可以在配置好上述 JDK 的 PowerShell 中启动本地 Profile：

```powershell
cd D:\code\ai-develop-team\repos\family-learning\server
mvn -B -ntp clean verify
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

普通 `verify` 会运行单元和协议测试；MySQL 集成测试需要按 README 设置专用测试库环境变量，不要指向实际学习数据库。直接运行打包文件也要保持工作目录为 `server`：

```powershell
java -jar target\family-learning-server-0.5.0.jar --spring.profiles.active=local
```

成功启动应看到 Flyway 迁移成功及应用启动日志。浏览器访问：

```text
http://127.0.0.1:8080/health
```

在 MySQL 中确认：

```sql
USE family_learning_uuid;
SHOW TABLES;
SELECT version, description, success
FROM family_uuid_schema_history
ORDER BY installed_rank;
```

0.5.0应有18张业务表和 Flyway 记录表，共19张表。Flyway 自动执行 V4，新增课文与背诵记录表，旧词库和听写归入英语。家长账号通过 App 使用本地配置中的邀请码注册。

兼容改造的实际验证结果和边界见 [验证记录](verification.md)。

## 安卓联调

手机与电脑在同一局域网时，服务地址填写 `http://电脑的IPv4地址:8080`；模拟器使用 `http://10.0.2.2:8080`。地址不带 `/api/v1`。安卓0.2.1通过登录页上方或“我的 → 选择 / 管理服务器”新增、编辑并选择地址；可保存多个名称（如家里电脑、办公室电脑），切换后重新登录。输入地址时页面随键盘调整并滚动到焦点字段。先在手机浏览器打开对应地址的 `/health` 确认网络可达。

当前调试 APK 支持 HTTP。未配置 AI Key 时，可以验证注册登录、孩子档案、免费本地图片识别和手动词表。本地识别不自动翻译，没有照片释义的词需手动补齐；成功后会选择设备语音，手机需要对应离线语音包。云端图片识别、云端释义查询和云端语音需要配置有效 AI Key；单进程 Eclipse 启动时还需 `APP_ROLE=all` 并重启服务。

## 常见启动问题

2026-11-05 配置覆盖修复：便携打包资源已从 `config/portable/application.yml` 移到 `packaging/portable/application.yml`。Spring Boot 的默认外部配置搜索会包含 `./config/*/`，旧位置会覆盖 Eclipse 开发配置，使日志出现 `family` 用户且密码为空。现已修正 Maven profile 和 Dockerfile 的资源路径，并在原工作目录验证了开发配置生效；原数据库连接字段保持不变。遇到旧日志时，在 Eclipse 刷新工程、Maven Update Project、Clean 后重新运行。

| 现象 | 处理 |
| --- | --- |
| 仍提示 release 21 或类版本不兼容 | 执行 Maven Update Project 和 Project Clean；确认是本次更新的 POM，并将编译和启动 JDK 均设为 1.8，删除旧 target 后重新构建 |
| Maven 依赖标红 | 执行 Maven Update Project，检查网络与 Maven settings.xml |
| `Communications link failure` | 确认 MySQL 已启动，URL 端口对应实际实例 |
| `Access denied` | 检查数据库账号、密码和账号 host 授权 |
| 提示不能解析 `DB_PASSWORD` | 检查 server/config/application-local.yml 是否存在、工作目录是否为 server，以及是否设置 local Profile |
| AI 任务一直排队 | 检查 `app.role=all`，只启动 api 角色不会执行 Worker |
| 8080 端口占用 | 修改本地配置 server.port，并同步调整 App 服务地址 |
| 非空库缺少 Flyway 历史 | 确认是否手工执行过迁移；新试用环境改用新的空库，保留原库 |
| 找不到或无法加载主类 Dfile.encoding=UTF-8 | JVM 将参数当作主类。确认 Main class 为 cn.family.learning.LearningApplication；把完整的 -Dfile.encoding=UTF-8 放在 Arguments 的 VM arguments，前面必须有英文半角短横线 -；可新建 Java Application 配置或使用提供的 .launch 文件 |
| Eclipse 控制台中文乱码 | Common 页签的 Encoding 设为 UTF-8，与 VM arguments 中的 -Dfile.encoding=UTF-8 一致 |
| 仍在执行 JSON、DATETIME(3) 或 utf8mb4 建表语句 | 确认使用更新后的代码和 db/mysql51 SQL，执行 Maven Update Project 后 clean 构建 |
| 提示 binlog_format 不满足要求 | 管理员核对二进制日志；开启时设置 ROW，本工程不会自动改变全局配置 |
| 输入表情或扩展汉字返回 400 | MySQL 5.1 的 utf8 仅支持三字节字符，请改用常用中英文 |

MySQL 5.1 兼容方案使用三字节 `utf8`、普通 `DATETIME`、单个自动 `TIMESTAMP` 和 JSON 文本列。时间精度为秒，JSON 合法性由 Java 校验；普通文字字段在写库前拒绝表情及扩展汉字。自定义 MyBatis 时间处理器将旧驱动的 JDBC 4.0 时间类型统一按 UTC 读写。

如果已经手工执行 UUID 建表 SQL，出现 `Found non-empty schema ... without metadata table`，需要先核对现有字段、默认值、索引、外键及 UUID 格式。完整匹配 V1 时可登记版本 1 的基线，再正常执行 V2；完整匹配 V2 时才能登记版本 2。数字 ID 库应使用 UUID 迁移工具，不能直接设置 baselineOnMigrate 跳过检查。当前工程的 Flyway 4 由 Java 显式配置，单独增加 Spring Boot 的 baseline 配置项不会替代这项核对。

2026-10-31 本地启动修复：现有 `family_learning` 库经与独立 MySQL 5.1.42 参考库比对，完整匹配 UUID V1，已登记 V1 基线并执行 V2，业务记录数量保持不变。该库可继续使用原库名；新环境仍按上文使用 `family_learning_uuid` 空库初始化。

示例 JDBC URL 的 `useSSL=false` 适用于可信本机连接，不应直接复制用于公网连接。

## 官方操作参考

- [Spring Boot 2.7.18 的 Java 8 要求](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/getting-started.html#getting-started.system-requirements)
- [MyBatis Starter 2.3 与 Java 8 兼容关系](https://github.com/mybatis/spring-boot-starter/blob/master/mybatis-spring-boot-autoconfigure/src/site/markdown/index.md)
- [Eclipse Maven 集成](https://eclipse.dev/m2e/documentation/m2e-documentation.html)
- [Java Application 启动配置](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.user/tasks/tasks-java-local-configuration.htm)
- [为启动配置选择 JRE](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.user/tasks/task-choose_config_jre.htm)
- [Spring Boot 外部配置与 Profile](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [MySQL Connector/J 5.1 官方文档](https://downloads.mysql.com/docs/connector-j-5.1-en.pdf)
- [Flyway 4.2 MySQL 实现](https://github.com/flyway/flyway/blob/flyway-4.2.0/flyway-core/src/main/java/org/flywaydb/core/internal/dbsupport/mysql/MySQLDbSupport.java)

Spring Boot 2.7 已结束开源维护。本次采用该分支满足现有 JDK 1.8 环境；用于长期生产部署时需要制定框架支持与安全更新方案，详见 [Spring 官方说明](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/)。
