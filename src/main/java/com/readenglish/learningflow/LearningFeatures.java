package com.readenglish.learningflow;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class LearningFeatures {
  private final JdbcTemplate db;
  private final ObjectMapper mapper;

  public LearningFeatures(JdbcTemplate db, ObjectMapper mapper) {
    this.db = db;
    this.mapper = mapper;
  }

  public boolean phonemeAudioEnabled() {
    return Boolean.TRUE.equals(
        db.queryForObject(
            "SELECT enabled FROM learning_features WHERE code='phoneme_audio'", Boolean.class));
  }

  public boolean phonemeListeningEnabled() {
    if (!phonemeAudioEnabled()
        || !Boolean.TRUE.equals(
            db.queryForObject(
                "SELECT enabled FROM learning_features WHERE code='phoneme_listening'",
                Boolean.class))) return false;
    var ready =
        new HashSet<>(
            db.queryForList(
                "SELECT DISTINCT phoneme_id FROM phoneme_pronunciations WHERE audio_url IS NOT NULL AND TRIM(audio_url)<>''",
                String.class));
    var units =
        db.queryForList(
            "SELECT content_json FROM learning_units WHERE template_code='phoneme-quiz' AND enabled=TRUE",
            String.class);
    return !units.isEmpty()
        && units.stream()
            .allMatch(json -> ready.contains(mapper.readTree(json).path("phonemeId").asText()));
  }
}
