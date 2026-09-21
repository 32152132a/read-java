CREATE TABLE learning_units (
    id VARCHAR(64) PRIMARY KEY,
    template_code VARCHAR(40) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content_json TEXT NOT NULL,
    sort_order INT NOT NULL,
    enabled BOOLEAN NOT NULL,
    CONSTRAINT fk_learning_unit_template FOREIGN KEY (template_code) REFERENCES feature_templates (code),
    CONSTRAINT uk_learning_unit_sort UNIQUE (template_code, sort_order)
);

CREATE TABLE learning_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    run_node_id VARCHAR(64) NULL,
    flow_node_id VARCHAR(64) NULL,
    template_code VARCHAR(40) NOT NULL,
    review_mode BOOLEAN NOT NULL,
    current_unit_index INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_session_run_node FOREIGN KEY (run_node_id) REFERENCES learning_flow_run_nodes (id)
);

CREATE TABLE phonemes (
    id VARCHAR(64) PRIMARY KEY,
    ipa VARCHAR(32) NOT NULL,
    group_code VARCHAR(20) NOT NULL,
    category VARCHAR(40) NOT NULL,
    audio_url VARCHAR(500) NULL,
    sort_order INT NOT NULL,
    detail_json TEXT NOT NULL,
    CONSTRAINT uk_phoneme_ipa UNIQUE (ipa)
);

CREATE TABLE quiz_questions (
    id VARCHAR(64) PRIMARY KEY,
    template_code VARCHAR(40) NOT NULL,
    unit_id VARCHAR(64) NOT NULL,
    prompt VARCHAR(300) NOT NULL,
    explanation VARCHAR(500) NOT NULL,
    CONSTRAINT fk_quiz_question_unit FOREIGN KEY (unit_id) REFERENCES learning_units (id)
);

CREATE TABLE quiz_options (
    id VARCHAR(64) PRIMARY KEY,
    question_id VARCHAR(64) NOT NULL,
    option_code VARCHAR(8) NOT NULL,
    label VARCHAR(120) NOT NULL,
    correct BOOLEAN NOT NULL,
    sort_order INT NOT NULL,
    CONSTRAINT fk_quiz_option_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id),
    CONSTRAINT uk_quiz_option_code UNIQUE (question_id, option_code)
);

CREATE TABLE quiz_submissions (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    question_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    selected_option_id VARCHAR(64) NOT NULL,
    correct BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_quiz_submission_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_quiz_submission_question FOREIGN KEY (question_id) REFERENCES quiz_questions (id),
    CONSTRAINT fk_quiz_submission_session FOREIGN KEY (session_id) REFERENCES learning_sessions (id)
);

CREATE INDEX idx_learning_unit_template ON learning_units (template_code, enabled, sort_order);
CREATE INDEX idx_learning_session_user ON learning_sessions (user_id, flow_node_id);

INSERT INTO phonemes (id, ipa, group_code, category, audio_url, sort_order, detail_json) VALUES
('p_1', '/ɪ/', 'VOWEL', '短元音', NULL, 1, '{"description":"发音短促，嘴角自然放松","mouth":{"type":"relaxed","color":"#3154ff"},"pronunciationSteps":["舌尖轻触下齿","嘴唇自然放松","快速发出短音"],"exampleWords":[{"wordId":"w_sit","word":"sit","ipa":"/sɪt/","meaning":"坐","audioUrl":null}],"memoryTip":"声音短而轻，不要拖长。"}'),
('p_2', '/iː/', 'VOWEL', '长元音', NULL, 2, '{"description":"发音较长，嘴角向两侧展开","mouth":{"type":"smile","color":"#3154ff"},"pronunciationSteps":["舌前部抬高","嘴角微微展开","保持声音长度"],"exampleWords":[{"wordId":"w_see","word":"see","ipa":"/siː/","meaning":"看见","audioUrl":null}],"memoryTip":"保持长音，嘴角比 /ɪ/ 更展开。"}'),
('p_3', '/p/', 'CONSONANT', '爆破音', NULL, 1, '{"description":"双唇闭合后快速送气","mouth":{"type":"closed","color":"#3154ff"},"pronunciationSteps":["双唇闭合","积聚气流","快速张开送气"],"exampleWords":[{"wordId":"w_pen","word":"pen","ipa":"/pen/","meaning":"钢笔","audioUrl":null}],"memoryTip":"声带不振动，注意送气。"}');

INSERT INTO learning_units (id, template_code, content_type, title, content_json, sort_order, enabled) VALUES
('unit_phoneme_1', 'phoneme', 'PHONEME_DETAIL', '短元音 · 第 1 课', '{"category":"短元音","ipa":"/ɪ/","description":"发音短促，嘴角自然放松","audioUrl":null,"mouth":{"type":"relaxed","color":"#3154ff"},"pronunciationSteps":["舌尖轻触下齿","嘴唇自然放松","快速发出短音"],"exampleWords":[{"wordId":"w_sit","word":"sit","ipa":"/sɪt/","meaning":"坐","audioUrl":null}],"memoryTip":"声音短而轻，不要拖长。"}', 1, TRUE),
('unit_compare_1', 'phoneme-compare', 'PHONEME_COMPARE', '短元音与长元音', '{"category":"短元音与长元音","title":"/ɪ/ 和 /iː/","description":"注意时长和嘴角变化","phonemes":[{"phonemeId":"p_1","ipa":"/ɪ/","description":"短而放松","mouth":{"type":"relaxed"},"audioUrl":null,"examples":["sit","ship"]},{"phonemeId":"p_2","ipa":"/iː/","description":"长而展开","mouth":{"type":"smile"},"audioUrl":null,"examples":["seat","sheep"]}],"audioPairs":[],"memoryTip":"先听长度，再看嘴角。"}', 1, TRUE),
('unit_quiz_1', 'phoneme-quiz', 'PHONEME_QUIZ', '听音辨认 · 第 1 题', '{"questionId":"q_phoneme_1","title":"听一听，选出正确音标","description":"点击播放按钮可重复播放","audioUrls":[],"options":[{"id":"A","label":"/ɪ/"},{"id":"B","label":"/iː/"},{"id":"C","label":"/e/"},{"id":"D","label":"/æ/"}]}', 1, TRUE),
('unit_syllable_1', 'syllable', 'SYLLABLE', '音节是什么', '{"lessonTitle":"音节是什么","description":"一个音节通常围绕一个元音核心形成","segments":["com","pu","ter"],"rules":["先找元音声音","围绕元音划分音节"],"memoryTip":"数元音声音，不是简单数字母。","question":{"questionId":"q_syllable_1","prompt":"computer 有几个音节？","options":[{"id":"A","label":"1"},{"id":"B","label":"2"},{"id":"C","label":"3"},{"id":"D","label":"4"}]}}', 1, TRUE),
('unit_ipa_1', 'ipa-decoding', 'IPA_DECODING', '音标拼读 · teacher', '{"wordId":"w_teacher","word":"teacher","hiddenWord":true,"ipa":"/ˈtiːtʃər/","audioUrl":null,"syllables":["/tiː/","/tʃər/"],"tip":"先按重音和音节读，再揭晓单词。"}', 1, TRUE),
('unit_word_1', 'word-decoding', 'WORD_DECODING', '单词拼读 · computer', '{"wordId":"w_computer","word":"computer","ipa":"/kəmˈpjuːtər/","audioUrl":null,"parts":[{"letters":"com","ipa":"/kəm/"},{"letters":"pu","ipa":"/pjuː/"},{"letters":"ter","ipa":"/tər/"}],"commonTips":["先听元音声音：每个音节通常有一个元音核心。","两个辅音相邻时，通常从中间尝试拆开。"],"specialTips":["这个词的重音落在第二个音节。"]}', 1, TRUE),
('unit_evaluation_1', 'evaluation', 'EVALUATION', 'AI 评测 · development', '{"wordId":"w_development","word":"development","ipa":"/dɪˈveləpmənt/","stressParts":["de","VEL","op","ment"],"standardAudioUrl":null,"recordingMaxSeconds":4,"prompt":"点击开始录音，4 秒后会自动结束"}', 1, TRUE);

INSERT INTO quiz_questions (id, template_code, unit_id, prompt, explanation) VALUES
('q_phoneme_1', 'phoneme-quiz', 'unit_quiz_1', '听一听，选出正确音标', '示例音频对应短元音 /ɪ/。'),
('q_syllable_1', 'syllable', 'unit_syllable_1', 'computer 有几个音节？', 'computer 包含三个元音声音，因此有三个音节。');

INSERT INTO quiz_options (id, question_id, option_code, label, correct, sort_order) VALUES
('qo_p_1_a', 'q_phoneme_1', 'A', '/ɪ/', TRUE, 1),
('qo_p_1_b', 'q_phoneme_1', 'B', '/iː/', FALSE, 2),
('qo_p_1_c', 'q_phoneme_1', 'C', '/e/', FALSE, 3),
('qo_p_1_d', 'q_phoneme_1', 'D', '/æ/', FALSE, 4),
('qo_s_1_a', 'q_syllable_1', 'A', '1', FALSE, 1),
('qo_s_1_b', 'q_syllable_1', 'B', '2', FALSE, 2),
('qo_s_1_c', 'q_syllable_1', 'C', '3', TRUE, 3),
('qo_s_1_d', 'q_syllable_1', 'D', '4', FALSE, 4);
