DELETE FROM quiz_submissions WHERE question_id LIKE 'q_phoneme_%';
DELETE FROM quiz_options WHERE question_id LIKE 'q_phoneme_%';
DELETE FROM quiz_questions WHERE id LIKE 'q_phoneme_%';
DELETE FROM learning_units WHERE id LIKE 'unit_quiz_%';

CREATE TEMPORARY TABLE tmp_phoneme_quiz_ordered AS
SELECT id, ipa, audio_url, ROW_NUMBER() OVER (ORDER BY CASE group_code WHEN 'VOWEL' THEN 0 ELSE 1 END, sort_order, id) rn
FROM phonemes;

CREATE TEMPORARY TABLE tmp_phoneme_quiz_choices AS
SELECT o.id, o.ipa, o.audio_url, o.rn,
       d1.ipa distractor_1, d2.ipa distractor_2, d3.ipa distractor_3,
       MOD(o.rn - 1, 4) correct_slot
FROM tmp_phoneme_quiz_ordered o
JOIN tmp_phoneme_quiz_ordered d1 ON d1.rn = MOD(o.rn, 48) + 1
JOIN tmp_phoneme_quiz_ordered d2 ON d2.rn = MOD(o.rn + 1, 48) + 1
JOIN tmp_phoneme_quiz_ordered d3 ON d3.rn = MOD(o.rn + 2, 48) + 1;

INSERT INTO learning_units (id, template_code, content_type, title, content_json, sort_order, enabled)
SELECT CASE WHEN rn = 1 THEN 'unit_quiz_1' ELSE CONCAT('unit_quiz_', LPAD(rn, 2, '0')) END, 'phoneme-quiz', 'PHONEME_QUIZ', CONCAT('听音辨认 · ', ipa),
       CONCAT('{"questionId":"', CASE WHEN rn = 1 THEN 'q_phoneme_1' ELSE CONCAT('q_phoneme_', LPAD(rn, 2, '0')) END, '","title":"听一听，选出正确音标","description":"点击播放按钮可重复播放","phonemeId":"', id, '","audioUrls":[', CASE WHEN audio_url IS NULL THEN '' ELSE CONCAT('"', audio_url, '"') END, '],"options":[{"id":"A","label":"', CASE WHEN correct_slot=0 THEN ipa ELSE distractor_1 END, '"},{"id":"B","label":"', CASE WHEN correct_slot=1 THEN ipa ELSE distractor_2 END, '"},{"id":"C","label":"', CASE WHEN correct_slot=2 THEN ipa ELSE distractor_3 END, '"},{"id":"D","label":"', CASE WHEN correct_slot=3 THEN ipa ELSE distractor_1 END, '"}]}'),
       rn, TRUE
FROM tmp_phoneme_quiz_choices;

INSERT INTO quiz_questions (id, template_code, unit_id, prompt, explanation)
SELECT CASE WHEN rn = 1 THEN 'q_phoneme_1' ELSE CONCAT('q_phoneme_', LPAD(rn, 2, '0')) END,
       'phoneme-quiz',
       CASE WHEN rn = 1 THEN 'unit_quiz_1' ELSE CONCAT('unit_quiz_', LPAD(rn, 2, '0')) END,
       '听一听，选出正确音标',
       CONCAT('这段音频对应 ', ipa, '。')
FROM tmp_phoneme_quiz_ordered;

INSERT INTO quiz_options (id, question_id, option_code, label, correct, sort_order)
SELECT CONCAT('qo_phoneme_', CASE WHEN rn = 1 THEN '1' ELSE LPAD(rn, 2, '0') END, '_a'), CASE WHEN rn = 1 THEN 'q_phoneme_1' ELSE CONCAT('q_phoneme_', LPAD(rn, 2, '0')) END, 'A', CASE WHEN correct_slot=0 THEN ipa ELSE distractor_1 END, correct_slot = 0, 1 FROM tmp_phoneme_quiz_choices
UNION ALL
SELECT CONCAT('qo_phoneme_', CASE WHEN rn = 1 THEN '1' ELSE LPAD(rn, 2, '0') END, '_b'), CASE WHEN rn = 1 THEN 'q_phoneme_1' ELSE CONCAT('q_phoneme_', LPAD(rn, 2, '0')) END, 'B', CASE WHEN correct_slot=1 THEN ipa ELSE distractor_2 END, correct_slot = 1, 2 FROM tmp_phoneme_quiz_choices
UNION ALL
SELECT CONCAT('qo_phoneme_', CASE WHEN rn = 1 THEN '1' ELSE LPAD(rn, 2, '0') END, '_c'), CASE WHEN rn = 1 THEN 'q_phoneme_1' ELSE CONCAT('q_phoneme_', LPAD(rn, 2, '0')) END, 'C', CASE WHEN correct_slot=2 THEN ipa ELSE distractor_3 END, correct_slot = 2, 3 FROM tmp_phoneme_quiz_choices
UNION ALL
SELECT CONCAT('qo_phoneme_', CASE WHEN rn = 1 THEN '1' ELSE LPAD(rn, 2, '0') END, '_d'), CASE WHEN rn = 1 THEN 'q_phoneme_1' ELSE CONCAT('q_phoneme_', LPAD(rn, 2, '0')) END, 'D', CASE WHEN correct_slot=3 THEN ipa ELSE distractor_1 END, correct_slot = 3, 4 FROM tmp_phoneme_quiz_choices;

DROP TABLE tmp_phoneme_quiz_choices;
DROP TABLE tmp_phoneme_quiz_ordered;
