-- MySQL 的默认不区分重音排序规则会把 /d/ 与 /ð/ 视为相同。
-- H2 会忽略 MySQL 可执行注释；测试库原有唯一约束保持不变。
/*!80000 ALTER TABLE phonemes MODIFY ipa VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL */;
/*!80000 ALTER TABLE phonemes ADD UNIQUE KEY uk_phoneme_ipa (ipa) */;
