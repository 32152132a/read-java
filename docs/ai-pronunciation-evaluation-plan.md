# AI 发音评测：一次性录音首版

## 已确认范围

2026-10-03 按用户决定简化原计划：完整英语单词、默认美式 US、一次性录音、不保留音频、不提供删除评测或历史页面。

首版直接等待腾讯云返回结果，建议使用规则文案。暂不引入 COS、录音表、后台队列、自动重试、音频转码或 DeepSeek 建议。

```text
微信小程序录制 4 秒 WAV
  -> 读取临时文件为 Base64，删除临时文件
  -> 已有登录请求层提交 Java API
  -> 校验录音，登记请求编号
  -> 腾讯云 SOE-N 录音模式
  -> 保存评分和规则建议，返回页面
```

服务器只在请求处理期间持有音频，不写本地文件、数据库或 COS，不记录音频或供应商签名 URL。前端只在请求结果不确定时暂存内存中的音频用于同编号重试，评测结束、重新录制或离开页面后释放引用。进程内存实际回收由运行时管理。

这描述的是本应用的处理方式，不代表腾讯云的内部留存承诺；开通时应核实供应商的数据处理条款。

## 当前实现

- 后端独立模块：`src/main/java/com/readenglish/evaluation`。
- 数据迁移：`V26__create_word_evaluations.sql`，只存请求摘要、状态、评分与建议，不存录音。
- 前端：`client/pages/evaluation` 中的局部控件及 composable，复用公共请求层、登录刷新和学习会话。
- 音频固定为 WAV PCM，16kHz、16bit、单声道，后端按实际数据校验 0.3 至 5 秒，拒绝明显静音及损坏文件。
- 普通单词模式 `eval_mode=0`、录音模式 `rec_mode=1`、英文引擎 `16k_en`；用 JDK WebSocket 客户端实现官方 WSS 协议，无新增运行时依赖。
- 显示准确度，以及供应商确实返回时的总分。单词模式的流利度、完整度没有有效意义，不显示、不编造。
- 保留供应商音素评分的结构化字段；首版不展示“识别 IPA”，不把供应商音素编码当作 IPA。
- 使用项目默认美式内容；当前未指定供应商自定义注音，不承诺严格区分英式和美式评分。
- 自动测试通过 Mockito 替换供应商；运行时没有 Fake 分数开关。缺少真实参数时明确返回尚未配置。

## API

### 创建或重放同一次评测

`POST /api/v1/evaluations`

使用现有 JSON 请求方式，避免 multipart 临时文件与新增上传基础设施：

```http
Authorization: Bearer <access-token>
Content-Type: application/json
Idempotency-Key: <本次录音唯一编号，仅字母数字下划线或连字符>
```

```json
{
  "wordId": "已有词条ID",
  "sessionId": "当前评测学习会话ID，可省略",
  "audioBase64": "完整 WAV 文件的 Base64，不带 data: 前缀"
}
```

正常返回 HTTP 200，沿用项目 `{code,message,data,requestId}` 包装。`data`：

```json
{
  "evaluationId": "eval_...",
  "status": "SUCCEEDED",
  "result": {
    "word": "hello",
    "accent": "US",
    "scores": {
      "overall": 85,
      "accuracy": 86,
      "phonemes": [],
      "providerRequestId": "..."
    },
    "summary": "发音比较清楚，继续保持。",
    "suggestions": ["注意保持自然的单词重音。"],
    "adviceSource": "RULE"
  },
  "errorCode": null
}
```

示例分数仅用于接口说明，不代表真实评测结果。

- 状态只有 `PROCESSING | SUCCEEDED | FAILED`。并发提交相同录音和编号时可能返回 `PROCESSING`。
- 相同用户、相同编号、相同输入返回原记录，不再调用供应商；换录音必须换编号。编号相同但输入不同返回 409。
- 每个用户最多同时处理 1 次，每分钟最多创建 6 次。
- 供应商调用总等待上限约 25 秒；前端提交等待 35 秒。
- 参数、格式、静音或权限错误在收费调用前返回 400/404；未配置返回 503；频繁提交返回 429。
- 已登记请求的供应商失败保存为 `FAILED`。前端依据 `errorCode` 提示重新录制，后端不自动重复收费请求。
- 网络结果不确定时前端使用同一份录音和编号重试；拿到 `PROCESSING` 后改为 GET 查询。

### 查询当前评测

`GET /api/v1/evaluations/{evaluationId}`

仅本人可查。查询不调用供应商。超过 60 秒仍处于处理中则标记 `EVALUATION_INTERRUPTED`，要求重新录音；不保留音频，因此不做重启恢复或后台重试。

没有 DELETE、列表或录音下载接口。结果和幂等编号会保存在数据库，音频不会。

## 腾讯云需要准备什么

1. 开通 [智聆口语评测（新版 SOE-N）](https://cloud.tencent.com/document/product/1774)，确认是新版服务，并确保测试调用有可用额度。
2. 在腾讯云账号信息及访问管理中准备 **AppID、SecretId、SecretKey**。AppID 是腾讯云账号的数字 ID，不是微信小程序 AppID。
3. 使用有该服务权限的专用访问密钥，把以下参数写入本地私有配置或后端进程环境变量：

```dotenv
TENCENT_SOE_ENABLED=true
TENCENT_SOE_APP_ID=
TENCENT_SOE_SECRET_ID=
TENCENT_SOE_SECRET_KEY=
```

本地现有 `scripts/start-local.ps1` 会加载 `.local/mysql.env` 中的所有配置项，可将这四项追加到该私有文件；只配置真实值，不修改启动脚本。仅写入 `.env` 不会自动被 Spring Boot 读取。启动会执行未执行的 Flyway 迁移，连接共享或远程数据库前需单独确认。

SecretKey 不发到聊天、不放前端、不提交 Git。暂时不需要 COS 参数和 DeepSeek 参数，也不需要提供录音存储桶。

## 验证与剩余工作

自动测试覆盖：合法录音、校验失败、鉴权、会话所有权、结果隔离、幂等冲突、并发去重、服务重启后失败处理、供应商错误脱敏、WSS 签名、分片响应、缺失分数、录音中断、离页清理和网络重试。

本地验证命令：

```powershell
.\mvnw.cmd verify
# 在 read-english/client 目录中
node --experimental-vm-modules scripts/test-evaluation.mjs
npm exec -- eslint pages/evaluation
```

本次已通过后端 46 项测试、前端 7 项评测交互测试、评测页面 ESLint、小程序构建，以及本次 Java 文件的格式检查和打包。全量 `mvnw verify` 的测试通过，但最后的全局格式检查报告原有 `LearningContentExpansionTests.java` 第 111 行附近存在格式问题，本次未修改该无关文件。

真实账号准备后还必须完成：

- 至少一段真实 `hello` 录音验证 WSS 握手、录音模式发送、结果字段和超时配置。
- 微信真机核实 WAV 的采样参数、麦克风首次授权、拒绝后恢复及系统中断。
- 微信小程序录音用途声明、后端 request 合法域名和 HTTPS；首版不用 `uploadFile`。
- 正确与明显错读的多组单词对比评分。无法只靠模拟测试确认供应商效果与实际费用。
- 当前“继续学习”复用既有流程完成接口；尚未新增服务端强制“评测成功才能完成节点”的规则。

## 官方依据

开发时核对过以下官方文档；真实服务行为仍以账号联调为准：

- [SOE-N 接口、签名与录音模式](https://cloud.tencent.com/document/product/1774/107497)
- [官方接口引用的评分数据结构](https://cloud.tencent.com/document/api/884/19320)
- [官方 Java SDK](https://cloud.tencent.com/document/product/1774/107361)
- [uni-app x 录音管理器与平台兼容性](https://doc.dcloud.net.cn/uni-app-x/api/get-recorder-manager.html)
