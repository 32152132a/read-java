CREATE TABLE words (
    id VARCHAR(64) PRIMARY KEY,
    normalized_word VARCHAR(120) NOT NULL,
    display_word VARCHAR(120) NOT NULL,
    ipa VARCHAR(160) NOT NULL,
    meaning VARCHAR(300) NOT NULL,
    accent VARCHAR(8) NOT NULL,
    audio_url VARCHAR(500) NULL,
    CONSTRAINT uk_word_accent UNIQUE (normalized_word, accent)
);

CREATE TABLE word_libraries (
    id VARCHAR(64) PRIMARY KEY,
    owner_user_id VARCHAR(64) NULL,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_word_library_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
);

CREATE TABLE word_library_items (
    library_id VARCHAR(64) NOT NULL,
    word_id VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (library_id, word_id),
    CONSTRAINT fk_library_item_library FOREIGN KEY (library_id) REFERENCES word_libraries (id),
    CONSTRAINT fk_library_item_word FOREIGN KEY (word_id) REFERENCES words (id),
    CONSTRAINT uk_library_item_sort UNIQUE (library_id, sort_order)
);

CREATE TABLE user_word_libraries (
    user_id VARCHAR(64) NOT NULL,
    library_id VARCHAR(64) NOT NULL,
    added_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_position INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, library_id),
    CONSTRAINT fk_user_library_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_library_library FOREIGN KEY (library_id) REFERENCES word_libraries (id)
);

CREATE TABLE user_word_progress (
    user_id VARCHAR(64) NOT NULL,
    library_id VARCHAR(64) NOT NULL,
    word_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, library_id, word_id),
    CONSTRAINT fk_word_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_word_progress_library FOREIGN KEY (library_id) REFERENCES word_libraries (id),
    CONSTRAINT fk_word_progress_word FOREIGN KEY (word_id) REFERENCES words (id)
);

INSERT INTO word_libraries (id, owner_user_id, type, name, description, status) VALUES
('lib_base', NULL, 'BASE', '基础拼读词库', '覆盖常见音标、音节和基础拼读规则', 'SUCCEEDED'),
('lib_frontend', NULL, 'SYSTEM', '前端开发', 'HTML、CSS、JavaScript 常用词汇', 'SUCCEEDED'),
('lib_backend', NULL, 'SYSTEM', 'Java 与后端', 'Java、数据库和服务端常用词汇', 'SUCCEEDED');

INSERT INTO words (id, normalized_word, display_word, ipa, meaning, accent, audio_url) VALUES
('w_computer', 'computer', 'computer', '/kəmˈpjuːtər/', '计算机', 'US', NULL),
('w_teacher', 'teacher', 'teacher', '/ˈtiːtʃər/', '老师', 'US', NULL),
('w_development', 'development', 'development', '/dɪˈveləpmənt/', '开发', 'US', NULL),
('w_framework', 'framework', 'framework', '/ˈfreɪmwɜːrk/', '框架', 'US', NULL),
('w_repository', 'repository', 'repository', '/rɪˈpɑːzətɔːri/', '仓库', 'US', NULL),
('w_dependency', 'dependency', 'dependency', '/dɪˈpendənsi/', '依赖', 'US', NULL);

INSERT INTO word_library_items (library_id, word_id, sort_order) VALUES
('lib_base', 'w_computer', 1),
('lib_base', 'w_teacher', 2),
('lib_base', 'w_development', 3),
('lib_frontend', 'w_framework', 1),
('lib_backend', 'w_repository', 1),
('lib_backend', 'w_dependency', 2);
