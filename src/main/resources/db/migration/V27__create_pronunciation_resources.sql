ALTER TABLE words ADD COLUMN variant_key VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE TABLE word_pronunciations (
    word_id VARCHAR(64) NOT NULL,
    accent VARCHAR(8) NOT NULL,
    ipa VARCHAR(160) NOT NULL,
    audio_url VARCHAR(500) NULL,
    PRIMARY KEY (word_id, accent),
    FOREIGN KEY (word_id) REFERENCES words(id),
    CHECK (accent IN ('US', 'GB'))
);

CREATE TABLE phoneme_pronunciations (
    phoneme_id VARCHAR(64) NOT NULL,
    accent VARCHAR(8) NOT NULL,
    audio_url VARCHAR(500) NOT NULL,
    PRIMARY KEY (phoneme_id, accent),
    FOREIGN KEY (phoneme_id) REFERENCES phonemes(id),
    CHECK (accent IN ('US', 'GB'))
);

CREATE TABLE phoneme_examples (
    phoneme_id VARCHAR(64) NOT NULL,
    word_id VARCHAR(64) NOT NULL,
    accent VARCHAR(8) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (phoneme_id, sort_order),
    FOREIGN KEY (phoneme_id) REFERENCES phonemes(id),
    FOREIGN KEY (word_id) REFERENCES words(id),
    CHECK (accent IN ('US', 'GB'))
);

CREATE TABLE learning_features (
    code VARCHAR(64) PRIMARY KEY,
    enabled BOOLEAN NOT NULL
);
INSERT INTO learning_features(code, enabled) VALUES
('phoneme_audio', FALSE), ('phoneme_listening', FALSE);
