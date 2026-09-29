ALTER TABLE phonemes MODIFY COLUMN detail_json LONGTEXT NOT NULL;
CREATE TABLE content_entries (
 id VARCHAR(64) PRIMARY KEY,
 kind VARCHAR(16) NOT NULL,
 source_id VARCHAR(64) NOT NULL,
 scope_id VARCHAR(64) NOT NULL DEFAULT '',
 label VARCHAR(120) NOT NULL,
 version INT NOT NULL DEFAULT 0,
 published_version INT NULL,
 UNIQUE (kind, source_id, scope_id)
);
CREATE TABLE content_revisions (
 entry_id VARCHAR(64) NOT NULL,
 version INT NOT NULL,
 config_json LONGTEXT NOT NULL,
 editor_id VARCHAR(64) NOT NULL,
 origin VARCHAR(32) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY (entry_id, version),
 FOREIGN KEY (entry_id) REFERENCES content_entries(id)
);
CREATE TABLE content_jobs (
 id VARCHAR(64) PRIMARY KEY,
 user_id VARCHAR(64) NOT NULL,
 request_key VARCHAR(120) NOT NULL,
 input_hash VARCHAR(64) NOT NULL,
 library_id VARCHAR(64) NOT NULL,
 status VARCHAR(24) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE (user_id, request_key),
 FOREIGN KEY (user_id) REFERENCES users(id),
 FOREIGN KEY (library_id) REFERENCES word_libraries(id)
);
CREATE TABLE content_job_items (
 job_id VARCHAR(64) NOT NULL,
 position INT NOT NULL,
 word VARCHAR(120) NOT NULL,
 status VARCHAR(24) NOT NULL,
 entry_id VARCHAR(64) NULL,
 error_message VARCHAR(500) NULL,
 attempts INT NOT NULL DEFAULT 0,
 started_at TIMESTAMP(6) NULL,
 PRIMARY KEY (job_id, position),
 FOREIGN KEY (job_id) REFERENCES content_jobs(id),
 FOREIGN KEY (entry_id) REFERENCES content_entries(id)
);
CREATE TABLE content_snapshots (
 id VARCHAR(64) PRIMARY KEY,
 user_id VARCHAR(64) NOT NULL,
 library_id VARCHAR(64) NULL,
 content_json LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE content_answers (
 snapshot_id VARCHAR(64) NOT NULL,
 question_id VARCHAR(160) NOT NULL,
 selected_option_id VARCHAR(64) NOT NULL,
 correct BOOLEAN NOT NULL,
 PRIMARY KEY (snapshot_id, question_id),
 FOREIGN KEY (snapshot_id) REFERENCES content_snapshots(id)
);
INSERT INTO content_entries (id, kind, source_id, scope_id, label)
 SELECT CONCAT('content_', id), 'WORD', id, '', display_word FROM words;
INSERT INTO content_entries (id, kind, source_id, scope_id, label)
 SELECT CONCAT('content_', id), 'PHONEME', id, '', ipa FROM phonemes;
CREATE INDEX idx_content_scope ON content_entries(scope_id, kind, label);
CREATE INDEX idx_content_job_status ON content_job_items(status, started_at);
