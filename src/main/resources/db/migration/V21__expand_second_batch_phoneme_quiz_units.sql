CREATE TEMPORARY TABLE tmp_phoneme_quiz_second_ordered AS
SELECT id, ipa, audio_url, ROW_NUMBER() OVER (ORDER BY CASE group_code WHEN 'VOWEL' THEN 0 ELSE 1 END, sort_order, id) rn
FROM phonemes;

CREATE TEMPORARY TABLE tmp_phoneme_quiz_second_choices AS
SELECT o.id, o.ipa, o.audio_url, o.rn + 48 unit_no,
       d1.ipa distractor_1, d2.ipa distractor_2, d3.ipa distractor_3,
       MOD(o.rn, 4) correct_slot
FROM tmp_phoneme_quiz_second_ordered o
JOIN tmp_phoneme_quiz_second_ordered d1 ON d1.rn = MOD(o.rn + 5, 48) + 1
JOIN tmp_phoneme_quiz_second_ordered d2 ON d2.rn = MOD(o.rn + 11, 48) + 1
JOIN tmp_phoneme_quiz_second_ordered d3 ON d3.rn = MOD(o.rn + 17, 48) + 1;

INSERT INTO learning_units (id, template_code, content_type, title, content_json, sort_order, enabled)
SELECT CONCAT('unit_quiz_', LPAD(unit_no, 2, '0')), 'phoneme-quiz', 'PHONEME_QUIZ', CONCAT('听音辨认复习 · ', ipa),
       CONCAT('{"questionId":"q_phoneme_', LPAD(unit_no, 2, '0'), '","title":"听一听，选出正确音标","description":"复习题：点击播放按钮可重复播放","phonemeId":"', id, '","audioUrls":[', CASE WHEN audio_url IS NULL THEN '' ELSE CONCAT('"', audio_url, '"') END, '],"options":[{"id":"A","label":"', CASE WHEN correct_slot=0 THEN ipa ELSE distractor_1 END, '"},{"id":"B","label":"', CASE WHEN correct_slot=1 THEN ipa ELSE distractor_2 END, '"},{"id":"C","label":"', CASE WHEN correct_slot=2 THEN ipa ELSE distractor_3 END, '"},{"id":"D","label":"', CASE WHEN correct_slot=3 THEN ipa ELSE distractor_1 END, '"}]}'),
       unit_no, TRUE
FROM tmp_phoneme_quiz_second_choices;

INSERT INTO quiz_questions (id, template_code, unit_id, prompt, explanation)
SELECT CONCAT('q_phoneme_', LPAD(unit_no, 2, '0')), 'phoneme-quiz', CONCAT('unit_quiz_', LPAD(unit_no, 2, '0')), '听一听，选出正确音标', CONCAT('这段音频对应 ', ipa, '。')
FROM tmp_phoneme_quiz_second_choices;

INSERT INTO quiz_options (id, question_id, option_code, label, correct, sort_order)
SELECT CONCAT('qo_phoneme_', LPAD(unit_no, 2, '0'), '_a'), CONCAT('q_phoneme_', LPAD(unit_no, 2, '0')), 'A', CASE WHEN correct_slot=0 THEN ipa ELSE distractor_1 END, correct_slot = 0, 1 FROM tmp_phoneme_quiz_second_choices
UNION ALL
SELECT CONCAT('qo_phoneme_', LPAD(unit_no, 2, '0'), '_b'), CONCAT('q_phoneme_', LPAD(unit_no, 2, '0')), 'B', CASE WHEN correct_slot=1 THEN ipa ELSE distractor_2 END, correct_slot = 1, 2 FROM tmp_phoneme_quiz_second_choices
UNION ALL
SELECT CONCAT('qo_phoneme_', LPAD(unit_no, 2, '0'), '_c'), CONCAT('q_phoneme_', LPAD(unit_no, 2, '0')), 'C', CASE WHEN correct_slot=2 THEN ipa ELSE distractor_3 END, correct_slot = 2, 3 FROM tmp_phoneme_quiz_second_choices
UNION ALL
SELECT CONCAT('qo_phoneme_', LPAD(unit_no, 2, '0'), '_d'), CONCAT('q_phoneme_', LPAD(unit_no, 2, '0')), 'D', CASE WHEN correct_slot=3 THEN ipa ELSE distractor_1 END, correct_slot = 3, 4 FROM tmp_phoneme_quiz_second_choices;

DROP TABLE tmp_phoneme_quiz_second_choices;
DROP TABLE tmp_phoneme_quiz_second_ordered;
