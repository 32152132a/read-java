# 单词与音标配置维护（第一版）

## 使用流程

1. 前端「专业词库 → 添加词库 → AI 定制词库」输入名称和 1–200 个单词，支持空格、逗号、换行分隔。
2. 后端返回持久化任务。逐词调用 DeepSeek 的非流式 JSON 接口，前端轮询进度；关闭页面不取消生成。
3. 生成结果是个人草稿。进入「核对与发布」修改内容，先校验预览，再保存或发布。
4. 词库至少有一个发布词条后出现在可添加列表，未发布词条不会进入学习。
5. 添加词库后，在「我的词库」点击开始学习；答案由后端判定，答对当前单词全部题目才能完成该词。
6. 「我的 → 内容维护」可搜索个人词条；管理员还可维护公共单词与现有音标。

第一版复杂数组使用分组 JSON 编辑，基础字段使用表单。历史版本可以加载后保存为新的草稿；不会原地修改历史记录。现有词条不可改成另一个单词。

## 数据组织

复用 `words`、`phonemes`、`word_libraries`、`word_library_items` 和学习进度表。V4 新增：

| 表 | 职责与关联 |
| --- | --- |
| `content_entries` | 内容索引，区分 WORD/PHONEME、公共/个人作用域，保存最新草稿和发布版本号；source_id 指向单词或音标 |
| `content_revisions` | 每次保存的完整 JSON 配置，不可变版本；通过 entry_id + version 关联 |
| `content_jobs` | 批量生成任务，关联用户和个人词库，保存幂等键和输入摘要 |
| `content_job_items` | 逐词状态、失败原因、尝试次数、生成词条 ID |
| `content_snapshots` | 学习开始时保存完整配置及答案；学习中不受后续发布影响 |
| `content_answers` | 按快照记录题目回答，避免前端自行判分 |

词库查询批量关联个人和公共的已发布版本，优先使用个人配置。未发布的草稿不参与学习。前端收到的是完整对象数组或分页 `items`，无需参与数据库关联。

旧课程仍保留原有结构，发布配置通过 `teachingConfig` 投影到音标详情、音标对比、音标拼读、单词拼读和评测目标。现有阶段测验仍使用原有 quiz 表和接口；本次未增加阶段测验的后台编辑页。新词库中的题目使用本次配置和快照判分。

## 配置结构

所有配置包含 `schemaVersion: 1`、`kind`、`accent: "US"`、`ipa`、`ipaSegments`、`audio`、`questions`。

| 字段 | 内容 |
| --- | --- |
| ipaSegments | `{text, tone, bold}` 数组；text 拼接须等于完整 IPA；tone 为 normal/primary/muted/stress/success |
| icon | 可选，限定现有图标 book-open/volume-2/headphones/audio-waveform/circle-check |
| audio | `{url, objectKey, provider}`；URL 为空或 HTTPS，最长 500 字符，与发布目标数据库字段一致；预留 COS 和有道资源信息 |
| questions | CHOICE 或 LISTENING；每题 `{id, type, prompt, options, correctOptionId, explanation}`；听辨题另需有效 audioUrl |
| WORD 专属 | word、meaning、tip、syllables、parts、commonTips、specialTips |
| syllables | `{text, ipa, stress, emphasized}`；stress 为 0/1/2，非空数组有且仅有一个主重音 |
| parts | `{letters, segments, tip}`；letters 拼接须等于单词；segments 使用音标片段结构，静音字母允许空数组 |
| PHONEME 专属 | group、category、description、mouth、pronunciationSteps、exampleWords、memoryTip |
| mouth | `{type, imageUrl?}`；type 支持 open/closed/relaxed/smile，自定义图片加载失败时回退内置示意图 |

学习接口移除 `correctOptionId` 和 `explanation`，提交答案后才返回判定与解释。维护接口只有词条所有者或公共内容管理员能够读取完整答案。

Web 单词发音统一经 `pronunciation.uts`：优先美式浏览器声线，无声线或朗读失败时回退已配置音频；音标和听辨题始终只播放资源，不合成 IPA。当前未调用有道、未上传 COS，内置口型图只是示意图。

## API

均使用既有 `/api/v1` 前缀、统一响应包裹及 Bearer 登录鉴权，不在页面单独维护 token。

| 方法与路径 | 功能 |
| --- | --- |
| GET /content/capabilities | 管理员权限、生成是否启用、当前用户 ID |
| GET /content?kind=WORD&q= | 搜索可维护内容，最多 100 条 |
| GET /content/{id}?revision= | 读取草稿或指定历史版本及最近 30 次历史 |
| POST /content/{id}/validate | 校验并返回预览对象，不写库 |
| PUT /content/{id} | `{version, config}` 乐观锁保存草稿 |
| POST /content/{id}/publish | `{version}` 发布当前草稿，过期版本返回 409 |
| POST /word-libraries/custom-jobs | `{name, wordsText}` 创建任务，必需 Idempotency-Key；返回 202 |
| GET /word-libraries/custom-jobs | 最近 30 个本人任务 |
| GET /word-libraries/custom-jobs/{id} | 任务与逐词结果 |
| POST /word-libraries/custom-jobs/{id}/retry | 仅重试失败项，每词最多 3 次尝试 |
| POST /word-libraries/{id}/study | 为已添加词库创建快照，返回 `{sessionId, items}` |
| POST /content-study/{id}/answers | `{questionId: "wordId:questionId", selectedOptionId}` |
| POST /content-study/{id}/words/{wordId}/complete | 校验答题并记录学习进度，重复调用不重复累计 |

相同用户、相同幂等键、相同输入返回原任务；同键不同输入返回 409。每个用户最多两个未完成生成任务。已有个人配置优先复用，不被 AI 重写；已发布词条可复用到新词库。进程中断后超时任务标记失败，由用户明确重试，避免无限自动重复收费。

## 私有配置与启用

在后端私有环境文件或环境变量中设置以下值，禁止提交真实密钥：

```dotenv
DEEPSEEK_API_KEY=你的私有密钥
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-flash
CONTENT_ADMIN_USER_IDS=需要授予公共内容编辑权的用户ID
```

管理员可配置多个 ID，用逗号分隔。默认没有管理员；不要为了调试开放给所有用户。当前用户 ID 可从 capabilities 接口读取。模型名称可按账号可用模型调整。

现有 `scripts/start-local.ps1` 会加载 `.local/mysql.env`；加入这些变量后重启后端即可。缺少 DeepSeek 密钥时生成入口显示未配置，已有内容仍可维护和学习。结构化生成显式关闭模型思考模式，减少等待和额外消耗。

V4 还将 `phonemes.detail_json` 扩展为 LONGTEXT，并新增内容表。迁移由 Flyway 执行；真实 MySQL 已确认处于 V4，后续环境仍须按部署流程单独验证迁移目标和私有凭据。

## 验证与当前边界

- 后端复查后 `mvnw verify`：22 项测试通过，包含原有 14 项回归、7 项内容流程及 1 项本地 HTTP 适配器测试；打包、Spotless 检查通过。新增覆盖音频地址上限/类型和大小写变化后的人工内容复用。
- 前端公共请求层和朗读：24 项通过；ESLint 通过；H5 构建通过，保留项目原有的 7 条模板索引类型警告。
- 独立浏览器四项验收通过：批量生成草稿、人工编辑校验与发布、添加词库并答题更新进度、管理员音标维护；未捕获运行时异常。后端使用 18080 和独立内存 H2，AI 使用本地非流式固定样本服务。
- 提交前复查修复了历史任务状态更新与学习卡片宽度，并通过浏览器状态和实际几何尺寸断言。
- 真实 MySQL 8.0.45 已确认位于 Flyway V4；真实 DeepSeek `deepseek-flash` 已完成 7 个单词的草稿生成抽样。最终静音字母样本 `knight` 一次生成成功并以空 segments 表达静音 `k`。生成格式可以通过校验，但拆读和音标仍需人工审核，不能直接批量发布。
- 真实联调发现并修复了 IPA 外层斜杠导致片段拼接失败、静音字母无法用空发音片段表达、默认思考模式增加等待，以及任务错误缺少诊断日志的问题。
- 微信小程序及真实音频资源尚未验证；本地固定样本和少量真实抽样都不能代表批量内容准确率。
- 音标资源网站尚待提供；未抓取资源、未接有道或 COS 上传、未实现自动纠错模型。
- 任务执行采用单实例轻量调度。暂无管理分页、取消任务、批量发布或可视化拖拽编辑；当前上限和人工发布适合首批资源整理。
