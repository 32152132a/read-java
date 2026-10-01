# 下一窗口接续说明

## 项目与工作规则

- 前端：`D:\works\trae\read-english`，UniApp X / Vue / UTS，HBuilderX H5 编译。
- 后端：`D:\works\trae\read-java`，Java 21、Spring Boot、MySQL / Flyway。
- 参考项目：`D:\works\trae\ollama-node`，已有 COS 与模型接入经验。
- 先检查两个仓库 Git 状态，保留已有改动；不改写历史。公共配置、公共组件或整体架构扩大变更需先说明范围并取得同意。
- 本轮用户授权复查、修复和本地提交，**没有授权 push 或部署**。不要把本轮提交授权延伸到下一批新开发。
- 不把密钥写进代码或文档；不直接修改远程仓库、服务或数据库。数据库变更使用版本化迁移。

## 已有主体提交

- 后端 `c8f56ad`：内容管理、版本、生成任务、学习快照、DeepSeek 非流式适配。
- 前端 `b79c353`：配置展示、内容维护、导入与词库学习。
- 主体提交在额度中断后已存在，本轮保留，仅追加复查修复及文档。最终最新提交请用各仓库 `git log -3 --oneline` 确认。

## 当前已经实现

1. 前端把批量输入解析为 1–200 项单词数组，建立个人词库生成任务；后台逐词非流式生成，前端轮询，失败项最多尝试三次。
2. 内容只保留一份当前配置。AI 生成并校验成功后立即进入词库；人工保存或词库成员变化会重置该词库的学习进度，下次进入读取最新内容。
3. 内容维护列表、单词/音标编辑页和词库学习页已经接入。基础字段是表单，复杂拆分、音节和题目是分组 JSON，**尚不是可视化编辑器**。
4. WORD / PHONEME 配置包含音标片段、颜色与加粗、重音/拆分、口型与图片、提示、题目、音频信息。
5. 学习接口返回对象数组并隐藏答案，由后端判题；完成单词会记录词库进度。进行中的练习固定快照。
6. Web 单词优先美式浏览器朗读，无声线或朗读失败再尝试配置音频。音标和听辨题只播放资源，不合成 IPA。
7. 鉴权仍统一在公共请求层；未在页面分散写 token。公共内容维护默认无管理员，需要配置明确用户 ID。

本轮复查关注并修复：历史任务状态不同步、选择任务时的请求竞争、学习卡片宽度、音频 URL 校验超过数据库 500 字符上限、单词大小写变化后未复用人工配置。

## 数据库与配置

V4 建立内容任务和学习数据表；V8 将最新配置迁入 `content_entries.config_json`，删除 `content_revisions` 和 `published_version`；V9 删除词库内容快照，V10 删除词库内容版本号。词库学习始终读取当前配置，`learning_session_snapshots` 仅服务普通课程会话。

私有环境变量：`DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`、`CONTENT_ADMIN_USER_IDS`。可放在已有 `.local/mysql.env`，由本地启动脚本加载。不得输出真实值或提交 Git。

真实 DeepSeek `deepseek-flash` 已完成 7 个词的小批量抽样；最终 `knight` 一次成功并正确用空 segments 表达静音 `k`。适配器显式关闭思考模式，统一 IPA 外层斜杠，允许静音字母使用空发音片段，并记录不含密钥和完整响应的失败原因。AI 结果通过结构校验后直接使用，个别教学内容错误从内容维护页人工修正。

## 运行与检查

后端测试（在 read-java）：

```powershell
.\mvnw.cmd '-Dmaven.repo.local=D:\works\trae\read-java\.local\maven-repository' verify
```

实际 MySQL 联调：先确认 SSH 隧道 `127.0.0.1:3307` 和私有配置，再运行 `scripts/start-local.ps1 -CheckOnly`。启动后端会触发 Flyway；执行真实迁移前明确目标环境及影响。端口优先 8080，被占用则保留原服务并选其他端口。

前端构建（在 read-english/client）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/web.ps1 -Build
```

开发运行使用同一脚本去掉 `-Build`，通过 `-ApiBaseUrl http://127.0.0.1:实际端口/api/v1` 跟随后端。脚本使用 HBuilderX 自带 Node；Java 项目只需要正确的 JDK 21。

最终验证：后端 22 项测试、前端 24 项测试通过；ESLint、H5 构建、后端打包与 Spotless 通过；浏览器四项流程及任务历史/卡片宽度断言通过。H5 保留原有 7 条模板索引类型警告。

测试与验收记录见后端 `docs/content-catalog.md`、前端 `docs/content-catalog-review.md`。本地 `.local` 中保留验收日志和截图，均不提交。主机 8080 曾有其他服务；不要根据旧 PID 停止进程。

## 建议后续顺序

1. **真实集成闭环**：在 MySQL V8 上用少量单词验证自动生成、直接入库、添加词库、学习和个别人工修改，并记录一次实际用量。
2. **编辑体验**：与用户确认后，把音标片段、拆分、重音和选择题改成可视化表单，保留 JSON 作为高级入口。用户当前尚未明确批准这一轮新 UI 开发。
3. **内容与资源**：用户提供官方音标网站及购买资源，核对授权与文件结构，确定首批内容规范。不能把 AI 生成结果直接当作权威发音数据。
4. **COS + 有道**：复用 ollama-node 的接入经验，由后端生成/上传音频，记录对象标识和 URL；统一播放入口切换策略，避免每个页面各接一遍。
5. **部署与微信**：部署阶段配置正式登录、HTTPS、权限和凭据；再进行微信真机验收。语音评测、原有阶段题库可视化后台、自动纠错另列后续需求。

## 下一窗口可直接发送

> 继续 read-english/read-java。先读 D:\works\trae\read-java\docs\next-session-handoff.md 和两个仓库的 Git 状态，确认最新提交。内容已经简化为单一当前配置；接下来验证 AI 生成后直接进入词库、学习和个别人工修改的闭环。保留已有改动，未经授权不要 push、部署或直接修改远程数据库。
