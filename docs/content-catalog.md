# 单词与音标配置维护

## 使用流程

1. 前端「专业词库 → 词库中心 → AI 定制词库」输入名称和 1–200 个单词。
2. 后端建立持久化任务，每批最多 5 个新单词调用一次 DeepSeek；前端只轮询汇总进度，离开页面不会取消任务。
3. 每个结果先经过服务端结构校验。校验成功后立即写入当前配置、创建单词并关联词库，无需逐条发布。
4. 生成完成后进入词库预览；个人 AI 词条显示编辑入口，用户检查后再把词库添加到「我的词库」。发现个别内容有误时，也可从预览或「我的 → 内容维护」进入编辑，保存后立即用于新练习。
5. 单词题目、拆读结构或词库成员发生变化时，重置相关词库的答题和学习进度；仅补换公共音频不重置学习进度。下次进入时读取最新内容。

复杂数组暂时使用分组 JSON 编辑，基础字段使用普通表单。现有词条不能在编辑时改成另一个单词。

## 数据组织

复用 `words`、`phonemes`、`word_libraries`、`word_library_items` 和学习进度表。内容相关表为：

| 表 | 职责与关联 |
| --- | --- |
| `content_entries` | 内容编辑入口、作用域与并发版本；WORD 的 JSON 只保存教学内容和个人释义，PHONEME 不重复保存正文 |
| `words`、`word_pronunciations` | 单词基础资料，以及按 US/GB 区分的 IPA、音频；同形异音词用独立词条和 `variant_key` 区分 |
| `phonemes`、`phoneme_pronunciations` | 音标唯一正文，以及按 US/GB 区分的录音 |
| `phoneme_examples` | 音标例词的单词 ID、口音和顺序 |
| `learning_features` | 音标播放、听音练习的数据库开关 |
| `content_jobs` | 批量生成任务，关联用户和个人词库，保存幂等键和输入摘要 |
| `content_job_items` | 逐词生成状态、失败原因、尝试次数和内容 ID |
| `content_library_answers` | 按用户和词库记录当前题目回答，由后端判分；词库变化时清空 |

V8 把原 `content_revisions` 中每个词条的最新配置迁入 `content_entries.config_json`，随后删除历史版本表及 `published_version`。V9 删除词库内容快照和旧答题表；V10 删除不需要的词库内容版本号；V14 删除普通课程会话快照。

教学内容优先使用个人配置，再使用公共配置；基础 IPA 与公共音频始终由资源表提供，个人内容不能覆盖。普通课程、例词和词库学习统一组装最新资料，前端接收完整对象。课程引用保留稳定 ID，不在运行时根据拼写或 IPA 猜测关联。

## 配置结构

配置包含 `schemaVersion`、`kind`、`accent`、`ipa`、`ipaSegments`、`audio` 和 `questions`。

- WORD 还包含 `word`、`meaning`、`tip`、`syllables`、`parts`、`commonTips`、`specialTips`。
- PHONEME 还包含 `group`、`category`、`description`、`mouth`、`pronunciationSteps`、`exampleWords`、`memoryTip`。
- `pronunciations` 是公共资源维护字段，如 `{"US":{"ipa":"/sɪt/","audioUrl":"https://…"},"GB":{"ipa":"/sɪt/","audioUrl":""}}`。音标资源只需 `audioUrl`。没有提供的口音不自动复制或推算。
- `audio.url`、`audio.usUrl`、`audio.gbUrl` 是接口派生的展示字段，不单独存储；以实际选中的口音同时确定 IPA 和音频。
- 音标 `exampleWords` 保存时引用 `wordId`、`accent`，返回时由服务端补齐拼写、IPA、释义和音频。
- `questions` 支持 CHOICE 和 LISTENING；正确答案必须对应一个选项，听辨题必须提供标准音频。

保存和 AI 生成都会执行同一套完整校验。学习接口会移除 `correctOptionId` 和 `explanation`，提交答案后才返回判定与解释。

## API

| 方法与路径 | 功能 |
| --- | --- |
| GET `/content/capabilities` | 管理员权限、生成是否启用、当前用户 ID、音标播放及听音练习开关 |
| GET `/content?kind=WORD&q=` | 搜索可维护的当前内容，最多 100 条 |
| GET `/content/{id}` | 读取当前配置 |
| PUT `/content/{id}` | `{version, config}` 校验并保存当前配置；保存后立即生效 |
| POST `/word-libraries/custom-jobs` | 传入 `{name, words}` 创建批量生成任务；`words` 为 1–200 项数组，必需 `Idempotency-Key` |
| GET `/word-libraries/custom-jobs` | 最近 30 个本人任务 |
| GET `/word-libraries/custom-jobs/{id}` | 任务汇总进度：总数、已完成、成功、失败 |
| GET `/word-libraries/custom-jobs/{id}/items` | 任务汇总与逐词结果，任务结束或查看详情时读取 |
| POST `/word-libraries/custom-jobs/{id}/retry` | 重试失败项，每词最多 3 次 |
| GET `/word-libraries/{id}/words` | 预览词库内容；每项包含内容 ID 与当前用户是否可编辑 |
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

密钥只放在本地或部署环境。当前音标以数据库里的文字说明为主，音标播放与听音阶段默认关闭。接入已确认授权和口音的音频后，按 [发音资源维护](pronunciation-resources.md) 更新唯一资源与数据库开关。单词无音频时仅 Web 支持浏览器朗读，微信小程序没有这个兜底；浏览器朗读不用于单独音标。有道尚未接入。
