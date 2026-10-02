# read-english 英语音标权威数据来源调研

调研日期：2026-10-02

## 1. 结论摘要

建议项目改为“英美双体系”，不要继续把“48 个音标”作为权威说法。当前目录实际是国内传统英式教学表：20 个元音 + 28 个辅音，其中 `/ɪə/ /eə/ /ʊə/ /əʊ/ /ɒ/ /ɜː/` 明显偏英式 RP/SSB；`/tr/ /dr/ /ts/ /dz/` 更适合当作音群、辅音连缀或词尾组合，不宜作为英语独立音素。美式 General American 不适合直接使用这套表，因为美式通常是 rhotic，`near /nɪr/`、`hair /her/`、`tour /tʊr/` 不分析成英式 centring diphthongs `/ɪə/ /eə/ /ʊə/`，GOAT 常写 `/oʊ/` 而不是 `/əʊ/`，LOT/PALM/THOUGHT 等元音合并也与英式表不同。

最推荐的实施路线是“混合方案”：以词典/API 购买或授权获取例词 IPA 与例词音频；以 IPA、大学/教学机构资料和自建审核表维护英美音素体系；单独音素音频优先采购或自录并取得完整版权，现有 Wikimedia/Wikipedia 音素录音只保留为过渡资源，且必须完善 CC BY-SA 署名、修改说明和同协议分发提示。

## 2. 英语音标体系

### 2.1 RP/SSB 与 General American

权威 IPA 只提供国际音标符号系统，不规定“英语必须有多少个音素”。英语音素数量取决于口音、分析粒度和转写传统。IPA 官方资源见 [IPA charts and sub-charts](https://www.internationalphoneticassociation.org/content/ipa-charts) 与 [IPA chart license](https://www.internationalphoneticassociation.org/content/ipa-chart)。

英式 RP/SSB 教学中常见的是约 44 个音素：约 20 个元音（12 个单元音 + 8 个双元音）和 24 个辅音。British Council 的交互式 phonemic chart 按 44 sounds 展示，链接：[British Council LearnEnglish Sounds Right](https://learnenglish.britishcouncil.org/grammar/english-sounds-right)。Cambridge Dictionary 提供英式和美式发音及 IPA，且其 pronunciation guide 明确区分 UK/US 符号，链接：[Cambridge pronunciation symbols](https://dictionary.cambridge.org/help/phonetics.html)。

General American 常见分析通常约 39-44 个音素，取决于是否把弱元音、音位化的 /ər/、cot-caught 合并、Mary-marry-merry 合并等纳入同一套教学表。美式词典实际教学通常不强调英式长短符号 `ː`，会使用 `/i/ /ɪ/ /eɪ/ /ɛ/ /æ/ /ɑ/ /ɔ/ /oʊ/ /ʊ/ /u/ /ʌ/ /ə/ /ər/ /aɪ/ /aʊ/ /ɔɪ/` 等集合；辅音通常约 24 个。Merriam-Webster 官方 API 提供音标和音频，但其发音体系不是严格 IPA，见 [Merriam-Webster Dictionary API](https://dictionaryapi.com/) 与 [API documentation](https://dictionaryapi.com/products/api-collegiate-dictionary)。

### 2.2 为什么会出现 44、48 或其他数量

- 44：多数现代英语教学按英式 RP/SSB 的 20 元音 + 24 辅音统计。
- 48：国内传统“国际音标”教学常把 `/tr/ /dr/ /ts/ /dz/` 加入辅音，把辅音数从 24 扩到 28；这些通常不是英语核心音素。
- 其他数量：美式口音存在 rhotic 元音处理、弱元音处理和区域合并；部分资料把音位、音位变体、音群、词尾组合混在一起；不同词典使用 broad transcription 或 narrow/allophonic hints 的程度不同。

### 2.3 当前 48 个项目音标评估

当前项目音标来自 `V5__add_phoneme_catalog_and_accent_audio.sql`：

- 偏英式体系：`/iː/ /ɪ/ /e/ /æ/ /ɜː/ /ə/ /ʌ/ /uː/ /ʊ/ /ɔː/ /ɒ/ /ɑː/ /eɪ/ /aɪ/ /ɔɪ/ /əʊ/ /aʊ/ /ɪə/ /eə/ /ʊə/`。
- 英美均可保留但需按口音标注细节：`/p/ /b/ /t/ /d/ /k/ /ɡ/ /f/ /v/ /θ/ /ð/ /s/ /z/ /ʃ/ /ʒ/ /h/ /tʃ/ /dʒ/ /m/ /n/ /ŋ/ /l/ /r/ /j/ /w/`。其中 `/r/` 应标注实际常见实现为英语近音 `[ɹ]`，不是 IPA 颤音 `[r]`。
- 不适合作为美式独立音素：`/ɪə/ /eə/ /ʊə/ /əʊ/ /ɒ/ /ɜː/`。美式中通常用 `/ɪr/ /er/ /ʊr/ /oʊ/ /ɑ/ /ər/` 或类似词典体系处理。
- 可能重复、音群化或不宜作为独立音素：`/tr/ /dr/ /ts/ /dz/`。它们可作为“辅音连缀/词尾组合/发音现象”单元保留，但不应计入核心音素表。
- 美式体系缺失或需另列：`/oʊ/`、r-colored vowel `/ər/` 或词典体系中的 `ər`，以及 LOT/PALM/THOUGHT 等元音差异字段。

## 3. 来源调研

| 来源 | IPA | 口音 | 单词发音 | 单独音素音频 | 发音说明/口型 | API/获取方式 | 价格/申请 | 商用 | 音频存 COS | 署名 | 抓取限制 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| International Phonetic Association | 提供 IPA 符号和图表，不提供英语词条 IPA | 非特定英语口音 | 否 | 图表相关资源，不是英语教学词库 | IPA 图表、术语 | [官方图表](https://www.internationalphoneticassociation.org/content/ipa-charts) | 图表可下载；授权见官网 | 图表按官方许可使用 | 不适用于词音频 | 需要按图表许可 | 不适用 |
| Cambridge Dictionary | 是 | UK/US | 是，网页提供音频 | 否 | 有 pronunciation symbols guide | 官方词典网站；商业数据需联系 Cambridge | 未公开统一 API 价格，需要商务 | 需要授权 | 需要确认/购买授权 | 取决于合同 | 网站内容不应抓取；需授权 |
| Oxford Learner's Dictionaries / Oxford API | 是 | UK/US | 是 | 否 | 有词典说明 | [Oxford Dictionaries API](https://developer.oxforddictionaries.com/)；[Oxford Learner's Dictionaries](https://www.oxfordlearnersdictionaries.com/) | API 有开发者注册和套餐，当前具体商用价格以官网为准 | API 条款约束 | 需要确认合同是否允许缓存/再分发音频 | 取决于合同 | 禁止未授权抓取 |
| Longman Dictionary / Pearson | 是 | UK/US 常见 | 是 | 否 | 有学习词典说明 | [Longman Dictionary](https://www.ldoceonline.com/)；商业授权需 Pearson | 未公开稳定开放 API，需商务确认 | 需要授权 | 需要确认 | 取决于合同 | 禁止未授权抓取 |
| Merriam-Webster API | 非严格 IPA，提供 MW 音标 | 美式为主，部分产品含英式信息有限 | 是，API 返回音频文件名规则 | 否 | 词典解释，不是口型系统 | [DictionaryAPI.com](https://dictionaryapi.com/) | 免费开发者 key；商业/高流量需看条款或联系 | 需遵守 [Terms of Use](https://www.merriam-webster.com/terms-of-use) | 需要确认，默认不应再分发 | 通常要求遵守品牌/条款 | 禁止超出 API/条款使用 |
| Collins Dictionary | 是 | UK/US 取决于词典 | 是 | 否 | 有词典内容 | [Collins API](https://www.collinsdictionary.com/api/) | 需申请 key；商用需授权 | 需要授权 | 需要确认 | 取决于合同 | 禁止未授权抓取 |
| Wiktionary | 常有 IPA，质量由社区维护 | 多口音，词条不一致 | 部分有音频 | 否 | 少量说明 | [Wiktionary dumps](https://dumps.wikimedia.org/)、[MediaWiki API](https://www.mediawiki.org/wiki/API:Main_page) | 免费 | 许可允许但需遵守条款 | 通常可按原许可保存与再分发 | 需要署名和许可继承 | 使用 API/dump，不要抓页面 |
| Wikimedia Commons | 文件页含音频和许可 | 多语言、多说话人 | 有大量单词和 IPA 音素文件 | 有 IPA chart 音素音频 | 文件描述页可能有说明 | [Commons API](https://commons.wikimedia.org/w/api.php)、文件页 | 免费 | 按单文件许可 | 可保存到 COS，但需保留许可、作者、来源、修改说明；SA 文件需同协议 | 是 | 使用 API/dump，遵守 Wikimedia 使用政策 |
| CMU Pronouncing Dictionary | 否，ARPAbet | 美式为主 | 无音频，只有发音词典 | 否 | 否 | [官方项目页](http://www.speech.cs.cmu.edu/cgi-bin/cmudict)、[GitHub 镜像](https://github.com/cmusphinx/cmudict) | 免费 | BSD-like 许可，见仓库 | 无音频 | 保留许可 | 可下载数据集 |
| Forvo | 通常不提供 IPA | 多口音真人发音 | 是 | 否 | 否 | [Forvo API](https://api.forvo.com/) | 需申请，价格/配额以官网为准 | 需授权 | 需要确认，真人音频再托管通常受限 | 取决于合同 | 禁止未授权下载/抓取 |
| 有道智云词典/语音 | 词典可能返回音标，TTS 提供语音 | 中英等，口音能力按产品 | 词典/语音服务可提供 | 否 | 否 | [有道智云](https://ai.youdao.com/)、[文本翻译/词典/语音合成文档](https://ai.youdao.com/DOCSIRMA/html/trans/api/wbfy/index.html) | 需注册、实名认证/创建应用；价格以控制台为准 | 可商用但受服务条款 | 需要确认是否允许把合成或词典音频持久化到 COS | 取决于合同 | 只能走 API，不能抓取 |

许可证与条款原始链接：

- IPA chart license: https://www.internationalphoneticassociation.org/content/ipa-chart
- Cambridge terms: https://dictionary.cambridge.org/help/terms.html
- Oxford API: https://developer.oxforddictionaries.com/
- Merriam-Webster API: https://dictionaryapi.com/
- Merriam-Webster terms: https://www.merriam-webster.com/terms-of-use
- Collins API: https://www.collinsdictionary.com/api/
- Wiktionary dumps: https://dumps.wikimedia.org/
- Wikimedia Commons licensing guide: https://commons.wikimedia.org/wiki/Commons:Licensing
- Wikimedia API etiquette: https://www.mediawiki.org/wiki/API:Etiquette
- CMUdict GitHub license: https://github.com/cmusphinx/cmudict
- Forvo API: https://api.forvo.com/
- 有道智云： https://ai.youdao.com/

## 4. 可落地资源评估

### 4.1 英式和美式音标符号表

可直接建立自有审核表，来源参考 IPA、Cambridge pronunciation symbols、British Council phonemic chart、Oxford/Cambridge 词典转写实践。符号表本身不应复制词典大段内容；应记录“系统、符号、口音、例词、备注、来源链接”。

### 4.2 单独音素标准音频

最稳妥是购买或自录。词典 API 多数提供单词音频，不提供“孤立音素”音频。Wikimedia Commons 有 IPA 音素音频，许可清晰但不是专为英语教学录制，部分是通用语音学音值而不是英式/美式音位读法。

可作为过渡保留的现有音频：`/ɪ/ /æ/ /ə/ /ʊ/ /p/ /b/ /t/ /d/ /k/ /ɡ/ /f/ /v/ /θ/ /ð/ /s/ /z/ /ʃ/ /ʒ/ /h/ /tʃ/ /dʒ/ /m/ /n/ /ŋ/ /l/ /j/ /w/`，前提是每个文件补齐作者、文件页、许可、是否改名/转码等元数据。

需要替换或谨慎使用：`/e/` 当前映射到 `[ɛ]`，作为英语 DRESS 元音可解释但符号不完全一致；`/ɒ/` 是通用 IPA 音值，适合英式 LOT 过渡，不适合美式；`/r/` 当前映射到 `[ɹ]` 是正确方向，但 UI 应显示“英语 /r/ 常实现为 [ɹ]”；`/ts/ /dz/` 不宜作为核心音素音频继续扩充。

暂缺且不应硬套通用 IPA 单音的项目：`/iː/ /ɜː/ /ʌ/ /uː/ /ɔː/ /ɑː/ /eɪ/ /aɪ/ /ɔɪ/ /əʊ/ /aʊ/ /ɪə/ /eə/ /ʊə/ /tr/ /dr/`。

### 4.3 例词 IPA 与例词音频

优先级：

1. Cambridge/Oxford/Collins/Longman 商业授权：英美 IPA 与真人词音频质量高，适合产品化。
2. Merriam-Webster API：适合美式例词音频和美式词典信息，但音标不是 IPA，需另配 IPA 来源。
3. Wiktionary + Commons：许可开放但质量不稳定，需要人工审核、版本锁定和署名系统。
4. CMUdict：适合生成美式发音候选、搜索押韵/音素结构和校验拼读，但不能直接当 IPA 权威源。
5. 浏览器 TTS/有道 TTS：适合临时朗读例词，不适合替代标准真人例词音频或孤立音素音频。

### 4.4 口型图、舌位图和发音说明

IPA 官方图表与大学语音学资料可作为理论依据，但项目内若要展示口型图/舌位图，建议使用自绘图或委托绘制，避免复制教材或词典图片。Wikimedia Commons 可找到部分矢量图，但每张图需单独核查许可。发音说明可以基于公开语音学事实自写，经人工审核后归项目所有。

## 5. 现有 Wikimedia 音频映射检查

当前项目使用 `docs/licenses/phoneme-audio-wikipedia.json` 记录 32 个映射，来源为 `joshstephenson/PhoneticFlashCards` 仓库转换后的 MP3，仓库声明原始录音来自 Wikipedia IPA vowel/consonant charts 并按 CC BY-SA 3.0 提供。项目已上传到腾讯云 COS。

主要问题：

- 许可证链条不够细：JSON 记录了仓库和 sourceFile，但没有逐文件 Wikimedia Commons 文件页、作者、原始许可、是否由 OGG 转 MP3、修改说明。
- CC BY-SA 3.0 可以再分发和存 COS，但必须保留署名、许可证链接、来源链接、修改说明；如果 MP3 转码被视为改编，衍生音频仍需按相同或兼容 SA 许可分发。官方许可见 https://creativecommons.org/licenses/by-sa/3.0/。
- 通用 IPA 音值不等于英语音素教学标准音。尤其元音和 `/r/` 的解释要非常谨慎。
- `/tr/ /dr/` 当前没有映射是正确的；不要用 `/t/+/r/` 或 `/d/+/r/` 拼接来冒充音素音频。
- `/ts/ /dz/` 有映射，但作为英语教学核心音素不合适，应移动到词尾组合或辅音连缀专题。

建议保留范围：

- 可过渡保留：大多数基础辅音音频，`/ɪ/ /æ/ /ə/ /ʊ/` 等少数短元音。
- 需标注后保留：`/e/` 映射 `[ɛ]`，`/r/` 映射 `[ɹ]`。
- 不再扩充为核心音素：`/ts/ /dz/`。
- 必须另找资源或自录：长元音、双元音、英美差异明显元音、centering diphthongs。

## 6. CMUdict、浏览器语音合成与音素音频

CMUdict 使用 ARPAbet，不是 IPA。它适合美式发音计算，但 ARPAbet 到 IPA 会有误差：重音数字需要转换成 IPA 重音符；`ER`、`AH`、`AA`、`AO` 等与具体美式合并有关；弱读和词典转写粒度不等于 Cambridge/Oxford IPA；同一单词多读音需要上下文选择。官方来源见 [CMUdict](http://www.speech.cs.cmu.edu/cgi-bin/cmudict) 与 [cmusphinx/cmudict](https://github.com/cmusphinx/cmudict)。

浏览器语音合成适合做例词兜底、无障碍朗读和临时预览；不适合做标准音频资产。原因是不同浏览器、系统、语音包、地区设置会产生不同发音；无法保证英式/美式、音质、重音和弱读一致；也无法稳定播放单独音素。浏览器朗读例词不能完全替代单独音素音频，因为例词里的音素会受邻音、重音、音节位置和连读影响，学习者听不到孤立目标音的稳定边界。

## 7. 三个实施方案

### A. 权威付费方案

内容：购买 Oxford/Cambridge/Collins/Longman 之一或多家的词典数据/API 授权，取得英美 IPA、例词音频、词典例词；单独音素音频另行采购或委托专业英美播音员录制。

- 准确度：最高。
- 授权风险：最低，但取决于合同是否允许缓存和 COS 分发。
- 成本：最高，需要商务沟通和授权费。
- 接入难度：中高，需要 API、缓存、授权审计和署名/版权页面。
- 英美支持：强。
- COS：必须写入合同。
- 维护成本：中等，API/合同续费与版本更新。

### B. 合法开源方案

内容：IPA/大学资料确定体系；Wiktionary dumps 获取 IPA 候选；Wikimedia Commons 获取许可明确的例词和音素音频；CMUdict 辅助美式发音候选；所有数据人工审核并锁定来源版本。

- 准确度：中等，取决于人工审核。
- 授权风险：中等，需要逐文件许可和署名系统，避免混入不兼容许可。
- 成本：低。
- 接入难度：高，需要 dump/API 解析、去重、质量审核、许可元数据建模。
- 英美支持：可以支持，但覆盖不均。
- COS：通常可以，但每个文件按许可处理；CC BY-SA 需署名和相同方式共享。
- 维护成本：高，社区数据变化和质量不稳定。

### C. 推荐混合方案

内容：项目自建英美双体系音素表；单独音素音频采用自录/采购，确保可永久存 COS；例词 IPA 首批由 Cambridge/Oxford/Collins/Longman 官方词典人工核验，商业化阶段购买 API 或数据授权；美式候选用 CMUdict 辅助；现有 Wikimedia 音频仅作过渡和内部审核参考。

- 准确度：高。
- 授权风险：低到中，核心音频自有版权，词典内容在购买前只做人工核验不批量复制。
- 成本：中等。
- 接入难度：中等。
- 英美支持：强。
- COS：自录/采购音频可明确允许；词典音频需另签。
- 维护成本：中等，数据结构稳定后按批次补资源。

## 8. 最终建议

项目应该采用“英美双体系”：英式以 RP/SSB 教学宽式 IPA 为主，美式以 General American 教学宽式 IPA 为主。UI 可以默认展示用户选择的口音，并在对比模式显示 UK/US 差异。

不建议继续使用“48 个音标”作为产品说法。更准确的说法是“英语常用音素与发音组合”，其中核心英式约 44 个，当前额外的 `/tr/ /dr/ /ts/ /dz/` 移到“辅音组合/词尾发音”。

第一批应准备：

- 英式核心音素表：20 元音 + 24 辅音。
- 美式核心音素表：单独建表，不从英式表硬映射；至少覆盖 `/i/ /ɪ/ /eɪ/ /ɛ/ /æ/ /ɑ/ /ɔ/ /oʊ/ /ʊ/ /u/ /ʌ/ /ə/ /ər/ /aɪ/ /aʊ/ /ɔɪ/` 和 24 个辅音。
- 每个音素 1 条标准音素音频，UK/US 分开。
- 每个音素 2-3 个例词，分别记录 UK IPA、US IPA、UK word audio、US word audio。
- 每个音素自写中文发音说明、常见错误、口型/舌位描述。
- 许可证表：source、source_url、license、license_url、author、attribution_text、can_store_cos、can_redistribute、requires_share_alike、review_status。

需要你注册、实名、购买或申请：

- Oxford Dictionaries API：注册开发者并确认商业套餐、缓存和音频再分发条款。
- Cambridge Dictionary / Cambridge University Press：需要商务授权，尤其是 IPA 与音频缓存。
- Collins API：申请 key 并确认商用、缓存、音频存储。
- Longman/Pearson：联系商务确认数据授权。
- Merriam-Webster API：注册 key，可用于美式参考；确认商用条款。
- Forvo API：如果要真人多口音例词音频，需申请并确认是否允许再托管。
- 有道智云：注册、实名认证、创建应用；如果用 TTS 或词典 API，确认音频/结果是否可持久化到 COS。

可以直接合法使用但需合规处理：

- IPA 官方图表和符号资料：按 IPA 官方许可使用。
- CMUdict：按其许可证用于美式发音候选和工具链，不能当 IPA 或音频来源。
- Wiktionary/Wikimedia Commons：可以使用 API/dump 和单文件许可资源，但必须逐条记录许可、作者、来源、修改说明；不要抓页面。

推荐数据字段：

```text
phoneme_system(id, code, name, accent, description, source_notes)
phoneme(id, system_id, symbol, normalized_symbol, category, manner, place, rounded, rhotic, sort_order, is_core)
phoneme_variant(id, phoneme_id, accent, display_symbol, ipa_symbol, example_hint, notes)
phoneme_audio(id, phoneme_id, accent, url, object_key, speaker, source_type, source_url, license, attribution, can_store_cos, review_status)
phoneme_example(id, phoneme_id, word, meaning_zh, cefr, uk_ipa, us_ipa, uk_audio_url, us_audio_url, source, review_status)
pronunciation_note(id, phoneme_id, accent, mouth_shape, tongue_position, voicing, common_errors, text_zh, reviewer)
```

英美音频映射方式：

- 不使用一个 `audio_url` 混合代表所有口音。
- 拆为 `audio_gb_url`、`audio_us_url` 或独立 `phoneme_audio` 表。
- 例词音频也按 `word_audio(accent, source, license)` 分开。
- 每条音频都必须有 `review_status`，未审核不得进入听辨题。

在资源确认前，不要继续扩充：

- 基于当前 48 表的“正式听音辨认题”。
- `/tr/ /dr/ /ts/ /dz/` 的核心音素学习单元。
- 长元音、双元音、英美差异元音的 Wikimedia 通用音值替代音频。
- 未经词典核验的例词 IPA、音节和重音。
- 未确认授权的词典音频、Forvo 音频、有道语音或浏览器 TTS 录制音频。

