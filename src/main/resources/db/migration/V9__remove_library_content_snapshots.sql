DROP TABLE content_answers;

DELETE FROM content_snapshots WHERE library_id IS NOT NULL;
ALTER TABLE content_snapshots DROP COLUMN library_id;
ALTER TABLE content_snapshots RENAME TO learning_session_snapshots;

ALTER TABLE user_word_libraries ADD COLUMN content_version INT NOT NULL DEFAULT 1;

CREATE TABLE content_library_answers (
 user_id VARCHAR(64) NOT NULL,
 library_id VARCHAR(64) NOT NULL,
 question_id VARCHAR(160) NOT NULL,
 selected_option_id VARCHAR(64) NOT NULL,
 correct BOOLEAN NOT NULL,
 PRIMARY KEY (user_id, library_id, question_id),
 FOREIGN KEY (user_id, library_id) REFERENCES user_word_libraries(user_id, library_id) ON DELETE CASCADE
);
