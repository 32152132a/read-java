# read-java

英语阅读项目的 Spring Boot 后端基础工程，使用 Java 21、Maven Wrapper、Spring MVC 和 Actuator。

## 在 IntelliJ IDEA 中打开

打开本目录或 `pom.xml`，将 Project SDK 设置为已安装的 JDK 21，等待 Maven 依赖加载完成。Maven 使用项目自带 Wrapper，无需单独安装。

启动类：`src/main/java/com/readenglish/ReadJavaApplication.java`。可通过 main 方法旁的运行或调试按钮启动。

## Windows 命令

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
.\mvnw.cmd clean package
.\mvnw.cmd spotless:apply
.\mvnw.cmd spotless:check
```

提交代码前执行 `spotless:apply` 自动格式化 Java 文件。`clean verify` 会自动检查格式，不符合规范时构建失败。

服务默认监听 8080 端口。启动后访问 `http://localhost:8080/actuator/health`，应收到 HTTP 200 和 `{"status":"UP"}`。业务联调健康接口为 `http://localhost:8080/api/v1/health`，返回统一的 `{ code, message, data, requestId }` 响应。

本地默认启用 `local` profile，可调用 `POST /api/v1/auth/dev/login` 获取临时 JWT：

```json
{ "nickname": "永庆" }
```

将响应中的 `accessToken` 作为 `Authorization: Bearer <token>` 请求私有接口。`refreshToken` 用于调用 `POST /api/v1/auth/refresh` 换取新令牌，退出时调用 `POST /api/v1/auth/logout` 撤销刷新令牌。

本地微信登录使用 Fake Gateway，可向 `POST /api/v1/auth/wechat/login` 传入任意非空 `code` 完成联调；非本地环境未接入真实微信配置时会返回 `WECHAT_LOGIN_FAILED`。接口文档地址为 `http://localhost:8080/swagger-ui/index.html`。

## 数据库

本地 profile 默认使用 `.local/read-java` 下的 H2 文件数据库，数据会在重启后保留，因此 MySQL 未安装完成时也能开发和联调。测试使用独立的内存 H2，Flyway 会自动执行三组迁移并导入少量联调内容。

切换 MySQL 时先创建 `read_english` 数据库，再配置：

```text
DB_URL=jdbc:mysql://localhost:3306/read_english?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=read_english
DB_PASSWORD=你的数据库密码
```

Flyway 会自动创建用户、学习流程、课程内容、题目、音标、单词和词库相关表。生产数据库账号需要具备执行迁移的建表权限。

非本地环境必须通过 `JWT_ACCESS_SECRET` 提供至少 32 字节的随机密钥，且不会开放临时登录接口。

测试会在随机端口启动应用，实际请求健康检查接口并验证结果。命令行启动后按 Ctrl+C 停止服务。

## 当前接口范围

第一阶段接口已经包含登录与令牌、用户资料与统计、首页聚合、学习流程配置和幂等推进、统一课程 session、答题、音标总览，以及词库查看、添加、删除和分页单词。用户、流程、进度、答题和词库关系都写入数据库。

AI 定制词库任务、录音上传、腾讯云口语评测、对象存储和大模型建议属于下一阶段，需要先确定供应商账号和数据授权。后续数据库密码及第三方密钥不要提交到仓库。
