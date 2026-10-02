# 微信小程序接入实施计划

## 1. 目标

把现有 `read-english` 前端和 `read-java` 后端接入真实微信小程序环境，完成以下闭环：

1. 用户在微信小程序中点击登录。
2. 前端调用 `uni.login` 获取一次性临时 `code`。
3. 前端把 `code` 发送到 `POST /api/v1/auth/wechat/login`。
4. 后端通过微信 `code2Session` 换取 `openid`，创建或找到本系统用户。
5. 后端签发本系统 access token 和 refresh token。
6. 后续业务请求继续经过现有公共请求层自动携带 access token，并能刷新、退出和重新登录。
7. 在微信开发者工具和至少一台真机上完成登录、学习进度保存、重新进入后恢复数据等验收。

本轮只接入微信身份登录和小程序运行环境，不同时引入手机号、微信昵称头像、支付、订阅消息、分享裂变或语音评测。

## 2. 当前项目已经具备的能力

这次不需要重写整套认证。

### 前端已有

- `client/src/services/auth.uts` 已在 `MP-WEIXIN` 环境调用 `uni.login({ provider: 'weixin' })`。
- 已将临时 `code` 发送到 `/auth/wechat/login`。
- `client/src/services/token.uts` 已统一保存 access token 和 refresh token。
- `client/src/services/http.uts` 已统一添加 `Authorization` 请求头，处理并发刷新、登录过期、错误提示和 Loading。
- 页面没有逐个接口硬编码 token 或微信请求头。

### 后端已有

- `POST /api/v1/auth/wechat/login` 接口和请求模型已经存在。
- `WechatLoginGateway` 已划出微信供应商适配边界。
- 本地 Fake Gateway 已可供非微信环境联调。
- `users` 表已有唯一 `wechat_openid` 和可空 `unionid`。
- 首次用户初始化、默认词库、学习流程、JWT、refresh token 轮换和退出接口已经存在。

### 目前缺少

- 真实微信 `code2Session` Gateway。
- 后端微信 AppId、AppSecret 的私有环境变量配置。
- 前端 `manifest.json` 中真实小程序 AppId。
- 微信公众平台的开发成员和服务器域名配置。
- HTTPS 线上 API 地址及真实小程序联调。
- 微信错误码、超时和重复登录的专项测试。

## 3. 你需要先准备的内容

### 3.1 微信小程序账号

在[微信公众平台](https://mp.weixin.qq.com/)完成小程序注册，并确认：

- 小程序主体和管理员可正常登录。
- 已获得小程序 **AppId**。
- 管理员能在“开发管理/开发设置”查看或重置 **AppSecret**。
- 参与真机调试的人已添加为开发成员或体验成员。

AppId 不是密钥，可以写入前端 `manifest.json`。AppSecret 是服务端密钥，不能写进前端、聊天记录、仓库或截图。

### 3.2 微信开发者工具

安装并登录[微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)，确保当前微信账号有该小程序的开发权限。HBuilderX 运行到微信小程序时会生成项目，再由微信开发者工具加载。

### 3.3 可公网访问的后端域名

准备一个供小程序访问的 API 地址，例如：

```text
https://api.example.com/api/v1
```

需要满足：

- 使用有效 HTTPS 证书。
- 域名能从公网访问后端服务。
- 在微信公众平台配置为 `request` 合法域名。
- 正式构建不能依赖 `127.0.0.1`、`localhost`、内网地址或本机 SSH 隧道。
- 服务器反向代理把 `/api/v1` 正确转发到 `read-java`。

开发者工具可临时关闭域名校验做早期验证，但真机和发布验收必须使用已配置的 HTTPS 合法域名。

### 3.4 后端私有配置

最终需要在服务器私有环境中配置以下变量，变量名可由实施会话结合项目风格确定：

```text
WECHAT_APP_ID=真实小程序AppId
WECHAT_APP_SECRET=真实小程序AppSecret
JWT_ACCESS_SECRET=足够长的随机值
```

推荐你自己把 AppSecret 写入服务器的私有环境文件或部署平台 Secret，不要把值发给开发会话。开发会话只需要知道变量已经配置成功。

## 4. 代码实施计划

### 阶段 A：接入真实微信 Gateway

后端新增真实 `WechatLoginGateway` 实现，调用微信服务端登录接口：

```text
GET https://api.weixin.qq.com/sns/jscode2session
  ?appid=APPID
  &secret=APPSECRET
  &js_code=前端临时code
  &grant_type=authorization_code
```

实现要求：

1. AppId、AppSecret 只从后端环境变量读取。
2. 配置连接超时和响应超时，不能无限等待微信接口。
3. 成功时只向业务层返回 `openid` 和可能为空的 `unionid`。
4. 当前功能不需要保存或返回 `session_key`。
5. 微信返回 `errcode` 时统一映射为项目的 `WECHAT_LOGIN_FAILED`，对用户给出可理解提示。
6. 日志可记录微信错误码和项目 `requestId`，不得记录 AppSecret、完整临时 code、session_key、access token 或 refresh token。
7. Fake Gateway 继续只用于本地 profile；生产环境必须选择真实 Gateway，凭据缺失时启动失败或明确拒绝登录。
8. 不让 Controller 依赖微信响应结构，继续通过 `WechatLoginGateway` 隔离第三方接口。

需要顺手评估一个现有问题：`initializeWechatUser` 当前在事务方法里先调用外部微信接口，可能让数据库事务在网络等待期间保持开启。建议把“换取微信身份”和“数据库用户初始化”拆开，让远程请求发生在事务外，数据库写入仍保持一个短事务。这个调整涉及认证服务边界，实施前应按仓库规则说明影响范围。

### 阶段 B：后端稳定性与安全

1. 相同 `openid` 多次登录必须返回同一用户，不能重复创建用户或默认学习流程。
2. 并发首次登录要依靠数据库唯一约束兜底，并正确处理唯一键竞争。
3. `unionid` 为空必须正常工作；只有满足微信开放平台条件时它才可能返回。
4. 微信临时 code 只能使用一次，不能缓存或重放。
5. AppSecret 不进入接口响应、异常正文和应用日志。
6. 保持当前本系统 JWT + refresh token 方案，不把微信 session_key 当成本系统登录凭据。
7. 正式环境保持开发登录关闭、Fake Gateway 关闭。

### 阶段 C：前端小程序配置

1. 将真实 AppId 写入 `client/manifest.json` 的 `mp-weixin.appid`。
2. 为小程序构建注入真实 HTTPS `VITE_API_BASE_URL`。
3. 保留现有 `uni.login -> /auth/wechat/login -> 保存本系统 token` 流程。
4. 检查首页登录按钮的开发态/小程序文案及失败后的重试体验。
5. 保持所有鉴权头由公共请求层处理，页面不直接读写 token。
6. 检查小程序端 UTS 编译差异、网络失败提示、页面重进和 token 刷新。

本轮不应调用 `wx.getUserProfile` 来完成基础登录。微信身份登录只需要 `uni.login`；昵称、头像如果以后确有产品需求，再设计为用户主动触发的独立资料完善流程。

### 阶段 D：平台和部署配置

1. 部署包含真实 Gateway 的后端版本。
2. 在服务器私有环境配置 `WECHAT_APP_ID` 和 `WECHAT_APP_SECRET`。
3. 确认生产 profile 下 Fake Gateway 和开发登录均关闭。
4. 配置 Nginx/网关 HTTPS，并验证健康检查和 `/api/v1/auth/wechat/login` 可达。
5. 在微信公众平台添加 `request` 合法域名。
6. 若后续直接从小程序上传 COS 或播放受限资源，再分别评估 `uploadFile`、`downloadFile` 合法域名；本轮所有业务请求走后端时无需提前扩大域名清单。
7. 按实际使用的微信能力补齐隐私保护指引。只有登录且不读取敏感用户资料时，不要提前申请无关权限。

平台配置和服务器环境变量会影响全局及生产环境。实施会话应先完成本地代码和测试，再列出具体配置项，由你确认后操作服务器。

## 5. 联调顺序

### 第一步：纯后端测试

通过 Mock HTTP 服务模拟微信响应，至少覆盖：

- 正常返回 `openid`。
- 正常返回 `openid + unionid`。
- 无效或已使用的 code。
- AppId/AppSecret 错误。
- 微信接口超时、网络失败、非 JSON 或字段缺失。
- 日志和错误响应不泄漏敏感字段。

保留现有 Fake Gateway 测试，并确保认证、刷新、退出测试全部通过。

### 第二步：微信开发者工具联调

1. HBuilderX 将 `client` 运行到微信开发者工具。
2. 确认构建使用真实 AppId 和测试 API 地址。
3. 点击登录，确认前端取得 code，后端换取 openid 并返回本系统 token。
4. 进入首页、词库和学习流程，确认所有请求正常携带本系统 token。
5. 退出后确认 refresh token 被撤销，再次登录仍回到同一个微信用户。

### 第三步：真机联调

至少验证以下场景：

- 首次登录会初始化用户、默认词库和学习流程。
- 第二次登录不会重复初始化。
- 完成一个学习节点后退出小程序，再进入能恢复进度。
- 手机切换网络、弱网和请求超时时提示合理且可以重试。
- access token 过期时公共请求层只刷新一次，页面请求能继续。
- refresh token 失效时回到登录入口，不出现重复弹窗或循环跳转。
- 同一个微信账号在另一台设备登录时仍识别为同一应用用户。
- 后端不可用、微信 code 无效、域名未配置等问题能区分定位。

### 第四步：体验版与发布前检查

- 上传小程序体验版，使用体验成员账号复测。
- 确认版本号、图标、名称、隐私保护指引、服务类目和业务内容一致。
- 确认生产 API、数据库、JWT 密钥和微信密钥均来自生产私有配置。
- 确认代码仓库和构建产物中没有 AppSecret、数据库密码或生产 token。
- 最后再提交微信审核；审核与发布由你在公众平台操作。

## 6. 验收标准

满足以下条件才算完成微信接入：

1. 微信开发者工具和真机都能通过真实 `uni.login` 登录。
2. 后端真实调用 `code2Session`，未使用 Fake 身份。
3. 相同微信账号重复登录始终对应同一个本系统用户。
4. 首次登录初始化只执行一次，学习数据不会被覆盖。
5. access token 刷新和退出撤销在小程序端工作正常。
6. 前端页面没有硬编码 token、AppSecret 或微信 session_key。
7. 生产日志和接口响应不泄漏敏感数据。
8. 正式环境关闭开发登录和 Fake Gateway。
9. HTTPS API 域名已加入微信合法域名，体验版在真机可用。
10. 前后端相关测试、构建和现有回归测试全部通过。

## 7. 暂不纳入本轮的能力

以下功能与基础微信登录不同，先不要混在同一个改动中：

- 获取手机号。
- 微信昵称和头像授权。
- 微信支付。
- 订阅消息。
- 分享、邀请和二维码场景值。
- 微信开放平台 UnionID 体系改造。
- 录音权限、语音识别和语音评测。
- COS 客户端直传。

需要时分别设计权限、隐私声明、接口和验收流程。

## 8. 交给新会话的任务说明

复制下面内容到新的 Codex 会话：

```text
开始 read-english 微信小程序真实登录接入。

前端：D:\works\trae\read-english
后端：D:\works\trae\read-java
实施计划：D:\works\trae\read-java\docs\wechat-mini-program-integration-plan.md
接口产品文档：D:\works\trae\read-english\docs\backend-api-product-spec.md

先检查两个仓库 Git 状态，保留已有改动，不覆盖、不回退。先阅读实施计划并核对当前代码，不要重写现有 token、refresh token 和公共请求层。

本轮目标是接入真实微信 code2Session Gateway、后端私有配置、前端小程序 AppId/API 环境配置，并完成自动测试、微信开发者工具和真机联调准备。基础登录不获取昵称、头像、手机号，不接支付、订阅消息、语音评测和 COS 直传。

先做代码与配置缺口审查，列出准备修改的文件和影响。涉及全局配置、公共认证服务或公共请求层时，按 AGENTS.md 先说明修改内容和影响范围，取得我同意后再改。

安全要求：AppSecret 只允许存在后端服务器私有环境变量中，不要求我把密钥粘贴到聊天里，不得写入前端、Git、日志或测试快照。session_key 不返回客户端，当前没有明确用途时不保存。正式环境必须关闭开发登录和 Fake Gateway。

开发完成后运行后端认证测试、完整相关回归测试、前端 lint/HTTP 测试和小程序构建检查。然后给我一份平台配置清单、服务器环境变量清单、微信开发者工具联调步骤和真机验收记录。未经确认不要提交、推送、部署或修改服务器配置。
```

## 9. 官方资料入口

- [微信小程序登录流程](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/login.html)
- [服务端登录凭证校验 code2Session](https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html)
- [小程序网络能力与服务器域名](https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html)
- [微信开发者工具下载](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)
- [微信公众平台](https://mp.weixin.qq.com/)

开始实施前应再次以微信官方文档和公众平台当前控制台要求为准。
