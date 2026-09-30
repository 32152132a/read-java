/*!80000 ALTER TABLE phonemes DROP INDEX uk_phoneme_ipa */;

INSERT IGNORE INTO phonemes
  (id, ipa, group_code, category, audio_url, audio_us_url, audio_gb_url, sort_order, detail_json)
VALUES
  ('p_c_10', '/ð/', 'CONSONANT', '摩擦音', NULL, NULL, NULL, 10,
   '{"description":"摩擦音 /ð/，注意气流和声带状态。","mouth":{"type":"relaxed","color":"#3154ff"},"pronunciationSteps":["摆好舌位和唇形","感受气流与声带状态","连到例词中练习"],"exampleWords":[{"wordId":null,"word":"this","ipa":"/ðɪs/","meaning":"这个","audioUrl":null}],"memoryTip":"用 this 记住 /ð/。","speechFallback":"this"}');

INSERT INTO content_entries (id, kind, source_id, scope_id, label)
SELECT 'content_p_c_10', 'PHONEME', 'p_c_10', '', '/ð/'
WHERE NOT EXISTS (
  SELECT 1 FROM content_entries e
  WHERE e.kind = 'PHONEME' AND e.source_id = 'p_c_10' AND e.scope_id = ''
);
