# AI 发音评测实施计划

## 1. 本轮目标

为 `read-english` 增加真实的英语单词跟读评测：用户听标准发音后录制自己的读音，系统返回总分、准确度、流利度、完整度、重音或音素问题，并给出简短、可执行的中文改进建议。

第一版只评测完整英语单词，不评测孤立音标、句子、自由说或实时对话。先把一个单词的完整闭环做稳定，再接入词库学习和正式学习流程。

完整链路：

```text
小程序录音
  -> Java 后端上传与校验
  -> 私有 COS 临时存储
  -> 异步评测任务
  -> 腾讯云智聆口语评测（新版 SOE-N）
  -> 规范化评分与音素错误
  -> DeepSeek 生成教学建议（失败时使用规则建议）
  -> 前端轮询并展示结果
```

腾讯云负责专业语音评分，大模型不直接听录音，也不自行判断发音是否正确。DeepSeek 只根据腾讯云返回的结构化结果组织教学建议，避免评分不稳定和不必要的音频隐私风险。

## 2. 当前项目状态

### 已有能力

- 前端已有 `/pages/evaluation/index` 页面路由。
- 已有 `RecordingPanel` 的录音按钮、4 秒倒计时和动画外观。
- 学习内容支持 `EVALUATION` 配置，包含目标单词、IPA、重音、标准音频和录音时长。
- 后端已有用户、学习流程、词库、内容、JWT 和异步 AI 词库任务的实现经验。
- 已有腾讯云 COS 存储桶及公开音标资源使用经验。
- 后端已接入 DeepSeek，可复用密钥管理、JSON 解析和失败处理方式。
- `docs/backend-api-product-spec.md` 已定义评测接口、数据方向和安全原则。

### 尚未实现

- `RecordingPanel` 当前只做计时和动画，没有调用真实录音 API，也不会产生音频文件。
- 前端没有录音权限、上传、评测轮询和结果展示。
- 后端没有评测 Controller、数据库表、异步任务和结果查询。
- 没有私有录音对象存储 Gateway 和生命周期清理。
- 没有腾讯云 SOE-N Gateway。
- 没有将供应商结果统一成产品评分和教学建议。

## 3. 你需要准备的内容

新会话开始后应先完成不依赖账号的代码审查和 Fake 评测闭环。到了真实供应商验证阶段，它必须主动提醒你完成以下事项，不应提前索要或让你把密钥发到聊天中。

### 3.1 腾讯云智聆口语评测（新版）

你需要：

1. 使用腾讯云账号并完成实名认证。
2. 在[智聆口语评测（新版）](https://cloud.tencent.com/document/product/1774)控制台开通服务。
3. 购买适合开发验证的小额资源包或开通后付费，并设置预算告警。
4. 创建专用子用户或最小权限密钥，不使用主账号全权限密钥。
5. 准备腾讯云账号 AppID、SecretId、SecretKey。

密钥由你写入本地忽略文件或服务器私有环境变量。不要将 SecretKey 发到聊天、写入前端、提交 Git 或放进数据库。

建议环境变量：

```text
TENCENT_SOE_APP_ID=
TENCENT_SOE_SECRET_ID=
TENCENT_SOE_SECRET_KEY=
TENCENT_SOE_ENABLED=true
```

### 3.2 私有 COS 录音目录

确认现有 COS 存储桶是否允许存储用户私有录音。建议使用独立前缀：

```text
read-english/private/evaluations/{userId}/{yyyy-MM}/{randomId}.wav
```

要求：

- 对象默认私有，不能使用当前公开音标文件的直接公网 URL 策略。
- 后端使用对象键访问，不在数据库长期保存签名 URL。
- 设置生命周期规则，例如录音上传 7 天后自动删除。
- COS 子账号只允许指定存储桶和前缀所需操作。

如果第一阶段技术验证尚未准备好私有 COS，可以只在测试环境使用受控临时文件验证一次；正式功能不能依赖服务器永久本地文件。

建议环境变量：

```text
COS_BUCKET=
COS_REGION=
COS_SECRET_ID=
COS_SECRET_KEY=
COS_EVALUATION_PREFIX=read-english/private/evaluations
AUDIO_RETENTION_DAYS=7
```

### 3.3 DeepSeek

现有 DeepSeek 接入可以复用。新会话需要确认服务器已经配置：

```text
DEEPSEEK_API_KEY=
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=
```

评测打分不依赖 DeepSeek。DeepSeek 暂时不可用时，后端仍必须返回腾讯云评分和规则模板建议。

### 3.4 产品与隐私决定

真实上线前需要你确认：

- 用户录音默认保留几天，建议 7 天。
- 是否允许用户主动删除单条评测记录和录音，建议允许。
- 隐私政策中说明录音用途、处理方、保留时间和删除方式。
- 评测失败的录音是否保留用于重试，建议最多保留到生命周期到期。
- 首版是否展示历史记录，建议暂不做历史页面，只保存数据供后续统计。

## 4. 第一阶段：供应商技术验证

先做一个独立、可删除的后端集成测试或受保护的本地验证入口，不直接铺完整产品页面。

使用一段已知内容为 `hello` 的短录音验证：

1. 调用腾讯云 SOE-N 新版接口，不能接旧版产品。
2. 使用英文引擎 `16k_en`。
3. 验证单词评测模式和单词纠错模式的返回差异。
4. 优先验证 `eval_mode=4` 是否满足音素纠错需求；若结果或计费不合适，再评估普通单词模式 `eval_mode=0`。
5. 验证能否返回总分、准确度、流利度、完整度、重音和音素级结果。
6. 验证开启 IPA 转换后的音素结果是否能和项目采用的英式/美式 IPA 体系对应。
7. 验证 WAV、MP3 等小程序实际可录制格式，确定最终上传格式和转码方案。
8. 记录一次调用耗时、计费次数、失败错误码和原始响应样例，原始响应必须脱敏。

腾讯云新版接口为 WSS 流式协议，官方要求 16 kHz、16 bit、单声道，并支持 pcm、wav、mp3、speex。后端应优先使用腾讯官方 Java SDK或经过测试的 WSS 客户端，不手写未经验证的签名和音频节奏逻辑。

技术验证完成后输出一页结论：最终评测模式、音频格式、SDK 版本、主要返回字段、单次耗时、单次成本和已知限制。验证失败时先停在此阶段，不建设后续数据表和 UI。

## 5. 第二阶段：后端领域与数据库

建议新增三张核心表，保持 MVP 简洁：

### `media_assets`

记录私有录音元数据：

- `id`
- `owner_user_id`
- `kind`：`EVALUATION_RECORDING`
- `object_key`
- `mime_type`
- `size_bytes`
- `duration_ms`
- `sha256`
- `privacy`：`PRIVATE`
- `expires_at`
- `created_at`
- `deleted_at`

### `evaluation_records`

记录任务及规范化结果：

- `id`
- `user_id`
- `word_id`
- `session_id`
- `media_id`
- `client_request_id`
- `status`：`PENDING | PROCESSING | SUCCEEDED | FAILED`
- `provider`
- `provider_request_id`
- `scores_json`
- `phonemes_json`
- `provider_result_json`
- `error_code`
- `error_message`
- `attempts`
- `next_attempt_at`
- `created_at`
- `completed_at`

### `evaluation_advice`

记录教学建议：

- `evaluation_id`
- `summary`
- `issues_json`
- `suggestions_json`
- `encouragement`
- `source`：`DEEPSEEK | RULE`
- `model`
- `created_at`

约束：

- `(user_id, client_request_id)` 唯一，防止重复点击产生重复收费。
- 每条评测记录只能访问自己的媒体和结果。
- 数据库只保存 COS `object_key`，不保存长期签名 URL。
- `provider_result_json` 用于排障和将来重新归一化，访问权限要受限，日志不打印原文。

Flyway 版本号必须先检查当前仓库最大版本，不能假定下一个编号，也不能修改已经执行过的迁移。

## 6. 第三阶段：后端 API 与异步流程

### 创建评测

```text
POST /api/v1/evaluations
Content-Type: multipart/form-data
Idempotency-Key: 客户端唯一ID
```

字段：

- `wordId`
- `audio`
- `durationMs`
- `sessionId`
- `clientRequestId`

后端校验：

- 用户身份和学习 session 所属关系。
- 目标词存在且可评测。
- 最长 5 秒、最大 2 MB。
- 文件扩展名、MIME 和真实媒体头一致。
- 音频可以解码，时长与客户端值大致一致。
- 禁止空音频、明显静音或超长音频进入收费接口。

返回 HTTP 202：

```json
{
  "evaluationId": "eval_101",
  "status": "PENDING"
}
```

### 查询结果

```text
GET /api/v1/evaluations/{evaluationId}
```

查询只读取数据库，绝不再次调用收费供应商。状态返回：

- `PENDING`：等待处理。
- `PROCESSING`：正在评测。
- `SUCCEEDED`：返回评分、音素问题和建议。
- `FAILED`：返回稳定的业务错误码和可重试提示。

### 删除评测

建议提供：

```text
DELETE /api/v1/evaluations/{evaluationId}
```

删除当前用户的评测记录并安排删除对应私有录音。对象删除暂时失败时进入后台重试，不向其他用户暴露资源。

### 异步处理顺序

1. 接收文件并完成本地校验。
2. 上传私有 COS。
3. 创建 `PENDING` 记录并立即返回。
4. Worker 原子领取任务并标记 `PROCESSING`。
5. 下载或读取录音，必要时转为供应商要求格式。
6. 调用 SOE-N 并保存脱敏原始结果。
7. 归一化为 0–100 的产品评分和音素问题。
8. 调用 DeepSeek 生成教学建议。
9. DeepSeek 失败时立即使用规则模板，不让评测失败。
10. 更新为 `SUCCEEDED`；前端下一次轮询即可读取。

供应商超时、限流和短暂网络失败可以指数退避重试，最多 2–3 次。无效音频、无法解码、目标文本不支持等永久错误不重试。服务重启后任务必须可以从数据库恢复，不能只放在线程内存或进程内队列。

## 7. 第四阶段：供应商隔离与评分规范化

后端保持清晰的供应商边界：

```java
public interface OralEvaluationGateway {
    ProviderEvaluation evaluate(EvaluationRequest request);
}
```

至少提供：

- `TencentSoeEvaluationGateway`：真实环境。
- `FakeOralEvaluationGateway`：本地前后端联调和自动测试。
- 未配置真实密钥时的明确失败实现，防止生产环境静默使用 Fake 分数。

统一输出建议包含：

- `overall`
- `accuracy`
- `fluency`
- `integrity`
- `stress`（供应商可用时）
- `phonemes[]`
- `detectedIpa`（可靠时）
- `providerRequestId`

不要把腾讯云原始 DTO 直接暴露给 Controller 或前端。评分映射、缺失字段和版本差异集中在适配层处理。

首批评测前必须明确项目采用的英式或美式目标发音。若内容词条允许两种口音，应根据用户口音偏好选择目标 IPA 和指定发音参数，不能拿英式目标音标解释美式录音结果。

## 8. 第五阶段：DeepSeek 教学建议

输入只包含：

- 目标单词和目标 IPA。
- 音节与重音配置。
- 腾讯云规范化分数。
- 错读、漏读、重音和低分音素。
- 用户选择的英式或美式口音。

不把用户原始录音、COS 私有地址、用户昵称、用户 ID 或密钥发送给大模型。

要求模型输出固定 JSON：

```json
{
  "summary": "整体清楚，第二音节重音需要更明显。",
  "issues": [
    {
      "target": "/ˈve/",
      "type": "STRESS",
      "message": "第二音节重音偏弱"
    }
  ],
  "suggestions": ["先分音节慢读，再连起来", "第二音节稍微拉长并提高响度"],
  "encouragement": "已经很接近标准发音，再强化重音即可。"
}
```

限制建议为 1 条总结、最多 3 个问题、最多 3 条练习建议。模型解析失败最多重试一次，仍失败则使用规则建议，避免评测页面长时间等待。

## 9. 第六阶段：前端真实录音与页面

### 录音组件

改造 `RecordingPanel` 或新增局部评测录音组件，实现：

- 请求麦克风权限。
- 调用 UniApp X/微信小程序支持的录音管理接口。
- 最长 4 秒自动停止，也允许用户提前停止。
- 页面隐藏、题目切换、组件卸载时停止并释放录音。
- 返回临时文件路径、时长、格式和大小。
- 处理用户拒绝权限、系统中断、录音过短、无声和录音失败。
- 支持重新录制，提交前不自动上传多次。

现有 `RecordingPanel` 是多个页面使用的公共组件。若直接改变它的事件参数或行为，新会话必须先说明影响范围并取得你同意；也可以先新增只供评测页面使用的局部组件，验证稳定后再决定是否统一。

### 评测页面状态

页面状态保持简单：

```text
准备录音 -> 正在录音 -> 可试听/重新录制 -> 正在评测 -> 结果/失败
```

结果页展示：

- 目标单词、IPA 和标准发音按钮。
- 总分和准确度、流利度、完整度。
- 具体错误音素或重音位置。
- 2–3 条改进建议。
- “再读一次”和“继续学习”。

前端每 1 秒轮询一次，最多约 30 秒。超时后停止轮询并允许用户稍后重试查询，不能因页面轮询重复创建评测或重复计费。页面离开时取消计时器。

### 小程序平台配置

真机联调时需要：

- 在小程序隐私保护指引中声明录音用途。
- 正确处理首次麦克风授权和用户拒绝后的设置引导。
- 后端 API 域名已加入微信 `request` 合法域名。
- 如果音频通过前端直接上传才需要 `uploadFile` 域名；本方案上传到 Java API，应结合最终 UniApp 请求方式核实微信域名类型。

## 10. 测试计划

### 后端自动测试

- 合法短音频创建任务并返回 202。
- 幂等键重复提交只产生一条记录和一次供应商调用。
- 空文件、伪造格式、超长、过大、静音和损坏音频被拒绝。
- 用户不能查看或删除他人的评测和录音。
- Fake Gateway 成功、暂时失败、永久失败和超时。
- Worker 重试、最大尝试次数和服务重启恢复。
- DeepSeek 失败时仍返回规则建议。
- 查询接口不重复调用供应商。
- COS 上传失败不会留下无法追踪的半成品记录。
- 删除和生命周期清理不会删除其他用户对象。

### 前端测试

- 权限允许、拒绝和系统设置恢复。
- 4 秒自动停止、手动停止、重新录制。
- 页面离开、切题和组件卸载会释放录音与轮询计时器。
- 上传失败可重试且不会重复收费。
- PENDING、PROCESSING、SUCCEEDED、FAILED 和轮询超时 UI。
- 结果字段缺失时页面仍能正常显示。
- 微信开发者工具和至少两种真机系统验证。

### 真实供应商验收样本

准备不少于 10 个单词，覆盖：

- 单音节和多音节。
- 清辅音/浊辅音易混。
- 长短元音易混。
- 重音位置不同。
- 英式和美式读音差异。
- 正确读音、明显错读、漏读和环境噪声。

不要用一条录音判断供应商质量。记录目标口音、设备、录音格式、结果和人工判断，确认评分方向基本一致后再开放入口。

## 11. 验收标准

1. 微信真机能真实录音并提交单词评测。
2. 相同 `clientRequestId` 不会重复调用收费服务。
3. 腾讯云返回结果能稳定归一化为产品评分和音素问题。
4. DeepSeek 不参与打分，失败时仍有规则建议。
5. 用户只能访问自己的评测和私有录音。
6. 录音按配置到期删除，数据库不暴露长期私有 URL。
7. 页面切换不会继续录音或无限轮询。
8. 供应商短暂故障可恢复，永久错误有明确提示。
9. Fake 评测只在本地测试环境启用，生产不能返回伪造分数。
10. 相关后端测试、前端检查、小程序构建和真机验收全部通过。

## 12. 推荐实施顺序

按以下提交边界推进，每一步通过后再开始下一步：

1. **SOE-N 技术验证**：真实账号、单词录音、返回字段和成本结论。
2. **后端 Fake 闭环**：数据库、API、异步任务、权限和规则建议。
3. **真实供应商 Gateway**：腾讯云调用、错误映射和评分规范化。
4. **私有 COS**：上传、对象键、删除和生命周期策略。
5. **前端真实录音**：权限、临时音频、上传和轮询。
6. **结果体验**：评分卡、音素问题、建议、重试和继续学习。
7. **DeepSeek 建议**：严格 JSON、超时和规则回退。
8. **学习流程接入**：评测成功后满足当前节点完成条件。
9. **真机与体验版验收**：隐私、弱网、真实账号和成本观察。

不要在第一步就同时建设历史页面、排行榜、统计报表或客户端直连腾讯云。

## 13. 交给新会话的任务说明

复制下面内容到新的 Codex 会话：

```text
开始 read-english AI 发音评测接入。

前端：D:\works\trae\read-english
后端：D:\works\trae\read-java
实施计划：D:\works\trae\read-java\docs\ai-pronunciation-evaluation-plan.md
接口产品文档：D:\works\trae\read-english\docs\backend-api-product-spec.md

先检查两个仓库 Git 状态，识别并保留已有改动，不覆盖、不回退。完整阅读实施计划，并审查现有 RecordingPanel、evaluation 页面、内容模型、DeepSeek 和 COS 相关实现。

第一版只实现完整英语单词跟读评测。采用“小程序录音 -> Java 后端 -> 私有 COS -> 腾讯云智聆口语评测新版 SOE-N -> 规范化结果 -> DeepSeek 教学建议”的流程。腾讯云负责评分，DeepSeek 只解释结构化结果；不要把原始录音发给大模型。

先做供应商接入和录音格式技术验证计划，再列出准备修改的数据库、后端和前端文件。涉及全局配置、公共请求层、公共 RecordingPanel、整体异步架构或部署配置时，按 AGENTS.md 先说明影响范围并取得我同意。

当工作推进到需要腾讯云实名认证、开通 SOE-N、购买测试资源包、创建最小权限 SecretId/SecretKey、配置私有 COS 生命周期、设置服务器环境变量、确认录音保留期或修改小程序隐私保护指引时，主动停下来明确告诉我：需要进入哪个控制台、完成什么操作、值应该配置到哪里、如何验证成功。不要要求我把 SecretKey 粘贴到聊天中。

没有真实密钥时先使用 Fake Gateway 完成可测试的前后端闭环。Fake 只能用于本地测试，生产环境配置缺失时必须明确失败，不能返回伪造评分。

每个阶段完成后运行相应后端测试、前端检查、小程序构建和真机验证，并说明改动文件、验证结果、供应商成本与遗留风险。未经确认不要提交、推送、部署、购买服务或修改服务器/微信/腾讯云配置。
```

## 14. 官方资料

- [腾讯云智聆口语评测（新版）](https://cloud.tencent.com/document/product/1774)
- [SOE-N 快速入门](https://cloud.tencent.com/document/product/1774/107347)
- [新版接口说明](https://cloud.tencent.com/document/product/1774/107497)
- [单词评测模式](https://cloud.tencent.com/document/product/1774/107386)
- [单词纠错评测模式](https://cloud.tencent.com/document/product/1774/107390)
- [Java SDK](https://cloud.tencent.com/document/product/1774/107361)
- [计费概述](https://cloud.tencent.com/document/product/1774/107342)

供应商接口、SDK、价格和小程序权限可能调整，真正开发和上线前必须再次查看官方最新文档及控制台说明。
