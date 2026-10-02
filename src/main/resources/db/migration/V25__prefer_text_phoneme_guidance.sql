-- 当前阶段先采用文字发音说明，不继续把公开 IPA 音频当作英语音素标准音频。
-- 音频字段保留为空，后续确认授权或采购资源后再按英/美口音补回。

UPDATE phonemes
SET audio_url = NULL,
    audio_us_url = NULL,
    audio_gb_url = NULL;

-- 公共音标配置从 phonemes.detail_json 重新投影，避免旧配置继续携带 COS 音频地址。
UPDATE content_entries
SET config_json = NULL
WHERE kind = 'PHONEME'
  AND scope_id = '';

-- 听音题暂时不提供音频资源，保留题目数据但避免继续暴露未确认标准性的音频链接。
UPDATE learning_units
SET content_json = REPLACE(
    REPLACE(
      REGEXP_REPLACE(content_json, '"audioUrls":\\[[^]]*\\]', '"audioUrls":[]'),
      '点击播放按钮可重复播放',
      '请根据题目中的音标说明完成练习'
    ),
    '复习题：点击播放按钮可重复播放',
    '复习题：请根据题目中的音标说明完成练习'
  )
WHERE template_code = 'phoneme-quiz';

UPDATE quiz_questions
SET prompt = '请根据题目中的音标说明完成练习',
    explanation = REPLACE(explanation, '这段音频对应', '目标音标是')
WHERE template_code = 'phoneme-quiz';
