package com.readenglish.content;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ContentProjection {
  private final ContentService contents;
  private final ContentConfig configs;
  private final JdbcTemplate db;
  private final ObjectMapper mapper;

  public ContentProjection(
      ContentService contents, ContentConfig configs, JdbcTemplate db, ObjectMapper mapper) {
    this.contents = contents;
    this.configs = configs;
    this.db = db;
    this.mapper = mapper;
  }

  public JsonNode snapshot(String session, String user, JsonNode units) {
    // 锁住会话后首次保存配置快照，同一会话后续读取始终保持相同版本。
    db.queryForObject(
        "SELECT id FROM learning_sessions WHERE id=? FOR UPDATE", String.class, session);
    List<String> old =
        db.queryForList(
            "SELECT content_json FROM learning_session_snapshots WHERE id=? AND user_id=?",
            String.class,
            session,
            user);
    if (!old.isEmpty()) return configs.publicView(mapper.readTree(old.getFirst()));
    for (JsonNode unit : units) {
      ObjectNode content = (ObjectNode) unit.path("content");
      String word = content.path("wordId").asText();
      if (!word.isBlank()) {
        ObjectNode c = contents.current(user, "WORD", word);
        if (c != null) {
          content.set("teachingConfig", c);
          content
              .put("word", c.path("word").asText())
              .put("ipa", c.path("ipa").asText())
              .put("audioUrl", c.path("audio").path("url").asText())
              .put("standardAudioUrl", c.path("audio").path("url").asText());
        }
      } else if (unit.path("contentType").asText().equals("PHONEME_DETAIL")) {
        List<String> ids =
            content.has("phonemeId")
                ? List.of(content.path("phonemeId").asText())
                : db.queryForList(
                    "SELECT id FROM phonemes WHERE ipa=?",
                    String.class,
                    content.path("ipa").asText());
        if (!ids.isEmpty()) {
          JsonNode c = contents.publicPhoneme(ids.getFirst());
          if (c != null) {
            content.set("teachingConfig", c);
            for (String key :
                List.of(
                    "ipa",
                    "category",
                    "description",
                    "mouth",
                    "pronunciationSteps",
                    "exampleWords",
                    "memoryTip")) content.set(key, c.path(key));
            content.put("audioUrl", c.path("audio").path("url").asText());
            content
                .put("audioUsUrl", c.path("audio").path("usUrl").asText())
                .put("audioGbUrl", c.path("audio").path("gbUrl").asText())
                .put("fallbackWord", c.path("speechFallback").asText());
          }
        }
      }
      if (content.path("phonemes").isArray())
        for (JsonNode p : content.path("phonemes")) {
          String id = p.path("phonemeId").asText();
          JsonNode c = contents.publicPhoneme(id);
          if (c != null) {
            ((ObjectNode) p).set("teachingConfig", c);
            ((ObjectNode) p).set("mouth", c.path("mouth"));
            ((ObjectNode) p)
                .put("ipa", c.path("ipa").asText())
                .put("description", c.path("description").asText())
                .put("audioUrl", c.path("audio").path("url").asText());
          }
        }
    }
    db.update(
        "INSERT INTO learning_session_snapshots(id,user_id,content_json) VALUES(?,?,?)",
        session,
        user,
        units.toString());
    return configs.publicView(units);
  }
}
