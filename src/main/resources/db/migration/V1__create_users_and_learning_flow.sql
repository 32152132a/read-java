CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    wechat_openid VARCHAR(128) NULL,
    unionid VARCHAR(128) NULL,
    nickname VARCHAR(40) NOT NULL,
    avatar_url VARCHAR(500) NULL,
    accent VARCHAR(8) NOT NULL DEFAULT 'US',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_wechat_openid UNIQUE (wechat_openid)
);

CREATE TABLE feature_templates (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    short_name VARCHAR(40) NOT NULL,
    route VARCHAR(160) NOT NULL,
    icon VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL,
    repeatable BOOLEAN NOT NULL,
    sort_order INT NOT NULL
);

CREATE TABLE refresh_tokens (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

CREATE TABLE learning_flow_configs (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flow_config_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_flow_config_version UNIQUE (user_id, version)
);

CREATE TABLE learning_flow_nodes (
    id VARCHAR(64) PRIMARY KEY,
    config_id VARCHAR(64) NOT NULL,
    template_code VARCHAR(40) NOT NULL,
    position_index INT NOT NULL,
    CONSTRAINT fk_flow_node_config FOREIGN KEY (config_id) REFERENCES learning_flow_configs (id),
    CONSTRAINT fk_flow_node_template FOREIGN KEY (template_code) REFERENCES feature_templates (code),
    CONSTRAINT uk_flow_node_position UNIQUE (config_id, position_index)
);

CREATE TABLE learning_flow_runs (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    config_id VARCHAR(64) NOT NULL,
    config_version BIGINT NOT NULL,
    current_position INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_flow_run_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_flow_run_config FOREIGN KEY (config_id) REFERENCES learning_flow_configs (id)
);

CREATE TABLE learning_flow_run_nodes (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    source_node_id VARCHAR(64) NOT NULL,
    template_code VARCHAR(40) NOT NULL,
    position_index INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_run_node_run FOREIGN KEY (run_id) REFERENCES learning_flow_runs (id),
    CONSTRAINT fk_run_node_source FOREIGN KEY (source_node_id) REFERENCES learning_flow_nodes (id),
    CONSTRAINT uk_run_node_position UNIQUE (run_id, position_index)
);

CREATE TABLE idempotency_records (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    scope VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    response_json TEXT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_idempotency_key UNIQUE (user_id, scope, idempotency_key)
);

CREATE INDEX idx_flow_config_active ON learning_flow_configs (user_id, active);
CREATE INDEX idx_flow_run_active ON learning_flow_runs (user_id, active);
CREATE INDEX idx_idempotency_expiry ON idempotency_records (expires_at);
CREATE INDEX idx_refresh_token_expiry ON refresh_tokens (expires_at);

INSERT INTO feature_templates (code, name, short_name, route, icon, enabled, repeatable, sort_order) VALUES
('phoneme', '认识音标', '认识\n音标', '/pages/phoneme/detail', 'phoneme', TRUE, TRUE, 10),
('phoneme-overview', '音标总览', '音标\n总览', '/pages/phoneme/overview', 'phoneme-overview', TRUE, TRUE, 20),
('phoneme-compare', '易混对比', '易混\n对比', '/pages/phoneme/compare', 'phoneme-compare', TRUE, TRUE, 30),
('phoneme-quiz', '听音辨认', '听音\n辨认', '/pages/phoneme/quiz', 'phoneme-quiz', TRUE, TRUE, 40),
('syllable', '音素音节', '音素\n音节', '/pages/syllable/index', 'syllable', TRUE, TRUE, 50),
('ipa-decoding', '音标拼读', '音标\n拼读', '/pages/decoding/ipa', 'ipa-decoding', TRUE, TRUE, 60),
('word-decoding', '单词拼读', '单词\n拼读', '/pages/decoding/word', 'word-decoding', TRUE, TRUE, 70),
('evaluation', 'AI评测', 'AI\n评测', '/pages/evaluation/index', 'evaluation', TRUE, TRUE, 80);
