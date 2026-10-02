package com.readenglish.content;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ContentProjection {
  private final ContentService contents;
  private final ContentConfig configs;
  private final JdbcTemplate db;

  public ContentProjection(ContentService contents, ContentConfig configs, JdbcTemplate db) {
    this.contents = contents;
    this.configs = configs;
    this.db = db;
  }

  public JsonNode project(String user, JsonNode units) {
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
    return configs.publicView(units);
  }
}
