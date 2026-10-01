# 单词与音标配置维护

## 使用流程

1. 前端「专业词库 → 添加词库 → AI 定制词库」输入名称和 1–200 个单词。
2. 后端建立持久化任务，每批最多 5 个新单词调用一次 DeepSeek；前端只轮询汇总进度，离开页面不会取消任务。
3. 每个结果先经过服务端结构校验。校验成功后立即写入当前配置、创建单词并关联词库，无需逐条发布。
4. 用户添加词库后即可学习。发现个别内容有误时，从生成结果或「我的 → 内容维护」进入编辑，保存后立即用于新练习。
5. 单词配置或词库成员发生变化时，该词库的答题记录和学习进度会清空；下次进入时读取最新内容。

复杂数组暂时使用分组 JSON 编辑，基础字段使用普通表单。现有词条不能在编辑时改成另一个单词。

## 数据组织

复用 `words`、`phonemes`、`word_libraries`、`word_library_items` 和学习进度表。内容相关表为：

| 表 | 职责与关联 |
| --- | --- |
| `content_entries` | WORD/PHONEME 的当前完整配置，区分公共和个人作用域；`version` 仅用于并发保存检查 |
| `content_jobs` | 批量生成任务，关联用户和个人词库，保存幂等键和输入摘要 |
| `content_job_items` | 逐词生成状态、失败原因、尝试次数和内容 ID |
| `content_library_answers` | 按用户和词库记录当前题目回答，由后端判分；词库变化时清空 |
| `learning_session_snapshots` | 仅供普通课程会话保持页面稳定，不参与词库学习 |

V8 把原 `content_revisions` 中每个词条的最新配置迁入 `content_entries.config_json`，随后删除历史版本表及 `published_version`。V9 删除词库内容快照和旧答题表；V10 删除不需要的词库内容版本号。

查询学习内容时优先使用当前用户配置，再使用公共配置。前端接收完整对象，无需参与数据库关联。

## 配置结构

配置包含 `schemaVersion`、`kind`、`accent`、`ipa`、`ipaSegments`、`audio` 和 `questions`。

- WORD 还包含 `word`、`meaning`、`tip`、`syllables`、`parts`、`commonTips`、`specialTips`。
- PHONEME 还包含 `group`、`category`、`description`、`mouth`、`pronunciationSteps`、`exampleWords`、`memoryTip`。
- `audio.url` 是通用地址；音标可配置 `usUrl`、`gbUrl`。`objectKey` 是以后管理 COS 文件的内部可选字段，不提供人工表单。
- `questions` 支持 CHOICE 和 LISTENING；正确答案必须对应一个选项，听辨题必须提供标准音频。

保存和 AI 生成都会执行同一套完整校验。学习接口会移除 `correctOptionId` 和 `explanation`，提交答案后才返回判定与解释。

## API

| 方法与路径 | 功能 |
| --- | --- |
| GET `/content/capabilities` | 管理员权限、生成是否启用、当前用户 ID |
| GET `/content?kind=WORD&q=` | 搜索可维护的当前内容，最多 100 条 |
| GET `/content/{id}` | 读取当前配置 |
| PUT `/content/{id}` | `{version, config}` 校验并保存当前配置；保存后立即生效 |
| POST `/word-libraries/custom-jobs` | 传入 `{name, words}` 创建批量生成任务；`words` 为 1–200 项数组，必需 `Idempotency-Key` |
| GET `/word-libraries/custom-jobs` | 最近 30 个本人任务 |
| GET `/word-libraries/custom-jobs/{id}` | 任务汇总进度：总数、已完成、成功、失败 |
| GET `/word-libraries/custom-jobs/{id}/items` | 任务汇总与逐词结果，任务结束或查看详情时读取 |
| POST `/word-libraries/custom-jobs/{id}/retry` | 重试失败项，每词最多 3 次 |
| POST `/word-libraries/{id}/study` | 读取当前内容并开始词库学习 |
| POST `/content-study/{id}/answers` | 提交题目答案 |
| POST `/content-study/{id}/words/{wordId}/complete` | 完成一个单词并记录进度 |

生成任务状态为 `PENDING / PROCESSING / SUCCEEDED / PARTIAL_FAILED`，它表示后台任务进度，不是内容草稿状态。同一用户相同单词会复用当前人工内容，不会再次调用 AI 覆盖。一次最多把 5 个缺失单词放进同一个非流式请求，各结果仍独立校验和保存；某一项不合格只标记该项失败。

## 私有配置

```dotenv
DEEPSEEK_API_KEY=你的私有密钥
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-flash
CONTENT_ADMIN_USER_IDS=需要维护公共内容的用户ID
```

密钥只放在本地或部署环境。首批 32 个音素使用公开 COS 音频，来源、许可、映射和对象路径见 [音标音频署名与许可](licenses/phoneme-audio-wikipedia.md)。其余 16 个音素暂时不套用不准确的素材，标准音频为空时 Web 使用浏览器英式或美式声线朗读例词。有道尚未接入。
