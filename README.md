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

将响应中的 `accessToken` 作为 `Authorization: Bearer <token>` 请求 `GET /api/v1/users/me`。接口文档地址为 `http://localhost:8080/swagger-ui/index.html`。

非本地环境必须通过 `JWT_ACCESS_SECRET` 提供至少 32 字节的随机密钥，且不会开放临时登录接口。

测试会在随机端口启动应用，实际请求健康检查接口并验证结果。命令行启动后按 Ctrl+C 停止服务。

## 当前范围

已提供可运行的基础工程、健康检查、请求 ID、统一响应、参数校验、异常处理、OpenAPI、JWT 鉴权、开发环境临时登录和当前用户接口。尚未配置 MySQL，用户与刷新令牌持久化将在数据库可用后接入。后续数据库密码不要提交到仓库。
