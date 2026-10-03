CREATE TABLE word_evaluations (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    word_id VARCHAR(64) NOT NULL,
    request_key VARCHAR(120) NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_json TEXT NULL,
    error_code VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_evaluation_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_evaluation_word FOREIGN KEY (word_id) REFERENCES words (id),
    CONSTRAINT uk_evaluation_request UNIQUE (user_id, request_key)
);
CREATE INDEX idx_evaluation_user_time ON word_evaluations (user_id, created_at);
