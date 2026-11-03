package com.readenglish.content;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** 公共词条和发音的唯一读取、维护入口；页面展示字段均从资源表派生。 */
@Service
public class PronunciationResources {
  private final JdbcTemplate db;
  private final ContentConfig configs;

  public PronunciationResources(JdbcTemplate db, ContentConfig configs) {
    this.db = db;
    this.configs = configs;
  }

  public String preferredAccent(String user) {
    if (user == null || user.isBlank()) return "GB";
    return db.queryForList("SELECT accent FROM users WHERE id=?", String.class, user).stream()
        .findFirst()
        .orElse("GB");
  }

  public Map<String, ObjectNode> words(List<String> ids, String accent) {
    var result = new LinkedHashMap<String, ObjectNode>();
    if (ids.isEmpty()) return result;
    String slots = slots(ids.size());
    db.query(
        "SELECT id,display_word,meaning,variant_key FROM words WHERE id IN (" + slots + ")",
        (org.springframework.jdbc.core.RowCallbackHandler)
            row -> {
              ObjectNode c =
                  configs.emptyWord(
                      row.getString("display_word"), "", row.getString("meaning"), "");
              c.put("wordId", row.getString("id"));
              c.put("variantKey", row.getString("variant_key"));
              c.putObject("pronunciations");
              result.put(row.getString("id"), c);
            },
        ids.toArray());
    db.query(
        "SELECT * FROM word_pronunciations WHERE word_id IN (" + slots + ") ORDER BY accent",
        (org.springframework.jdbc.core.RowCallbackHandler)
            row -> {
              ObjectNode c = result.get(row.getString("word_id"));
              if (c != null)
                c.withObject("pronunciations")
                    .putObject(row.getString("accent"))
                    .put("ipa", row.getString("ipa"))
                    .put("audioUrl", Objects.toString(row.getString("audio_url"), ""));
            },
        ids.toArray());
    result.values().forEach(c -> selectWord(c, accent));
    return result;
  }

  public void selectWord(ObjectNode c, String preferred) {
    selectWord(c, preferred, false);
  }

  public void selectWord(ObjectNode c, String preferred, boolean exactAccent) {
    JsonNode options = c.path("pronunciations");
    String accent =
        exactAccent || options.has(preferred) ? preferred : options.has("GB") ? "GB" : "US";
    JsonNode chosen = options.path(accent);
    c.put("accent", accent).put("ipa", chosen.path("ipa").asText(""));
    var segments = c.putArray("ipaSegments");
    segments
        .addObject()
        .put("text", c.path("ipa").asText())
        .put("tone", "muted")
        .put("bold", false);
    c.putObject("audio")
        .put("url", chosen.path("audioUrl").asText(""))
        .put("usUrl", options.path("US").path("audioUrl").asText(""))
        .put("gbUrl", options.path("GB").path("audioUrl").asText(""));
  }

  public Map<String, ObjectNode> phonemes(List<String> ids, String accent) {
    var result = new LinkedHashMap<String, ObjectNode>();
    if (ids.isEmpty()) return result;
    db.query(
        "SELECT * FROM phonemes WHERE id IN (" + slots(ids.size()) + ")",
        (org.springframework.jdbc.core.RowCallbackHandler)
            row -> {
              ObjectNode c = configs.read(row.getString("detail_json"));
              c.put("schemaVersion", 1)
                  .put("kind", "PHONEME")
                  .put("ipa", row.getString("ipa"))
                  .put("category", row.getString("category"))
                  .put("group", row.getString("group_code"));
              if (!c.has("ipaSegments"))
                c.putArray("ipaSegments")
                    .addObject()
                    .put("text", row.getString("ipa"))
                    .put("tone", "primary")
                    .put("bold", false);
              if (!c.has("questions")) c.putArray("questions");
              c.putObject("pronunciations");
              c.putArray("exampleWords");
              result.put(row.getString("id"), c);
            },
        ids.toArray());
    db.query(
        "SELECT * FROM phoneme_pronunciations WHERE phoneme_id IN (" + slots(ids.size()) + ")",
        (org.springframework.jdbc.core.RowCallbackHandler)
            row ->
                result
                    .get(row.getString("phoneme_id"))
                    .withObject("pronunciations")
                    .putObject(row.getString("accent"))
                    .put("audioUrl", row.getString("audio_url")),
        ids.toArray());
    var examples =
        db.queryForList(
            "SELECT * FROM phoneme_examples WHERE phoneme_id IN ("
                + slots(ids.size())
                + ") ORDER BY sort_order",
            ids.toArray());
    var wordIds = examples.stream().map(row -> row.get("word_id").toString()).distinct().toList();
    var words = words(wordIds, accent);
    for (var example : examples) {
      ObjectNode word = words.get(example.get("word_id").toString()).deepCopy();
      selectWord(word, example.get("accent").toString(), true);
      word.put("audioUrl", word.path("audio").path("url").asText());
      result.get(example.get("phoneme_id").toString()).withArray("exampleWords").add(word);
    }
    for (ObjectNode c : result.values()) {
      var options = c.path("pronunciations");
      String selected = options.has(accent) ? accent : options.has("GB") ? "GB" : "US";
      c.put("accent", selected);
      c.putObject("audio")
          .put("url", options.path(selected).path("audioUrl").asText(""))
          .put("usUrl", options.path("US").path("audioUrl").asText(""))
          .put("gbUrl", options.path("GB").path("audioUrl").asText(""));
    }
    return result;
  }

  @Transactional
  public String ensureWord(JsonNode c, String candidate) {
    if (!db.queryForList("SELECT id FROM words WHERE id=?", String.class, candidate).isEmpty())
      return candidate;
    String name = c.path("word").asText().trim();
    String variant = c.path("variantKey").asText("default");
    var found =
        db.queryForList(
            "SELECT id FROM words WHERE normalized_word=? AND variant_key=?",
            String.class,
            name.toLowerCase(Locale.ROOT),
            variant);
    if (!found.isEmpty()) return found.getFirst();
    db.update(
        "INSERT INTO words(id,normalized_word,display_word,meaning,variant_key) VALUES(?,?,?,?,?)",
        candidate,
        name.toLowerCase(Locale.ROOT),
        name,
        c.path("meaning").asText(),
        variant);
    saveWordPronunciations(candidate, c.path("pronunciations"));
    db.update(
        "INSERT INTO content_entries(id,kind,source_id,scope_id,label,version,config_json) VALUES(?,'WORD',?,'',?,0,NULL)",
        "content_" + candidate,
        candidate,
        name);
    return candidate;
  }

  @Transactional
  public void saveWord(String id, JsonNode c) {
    db.update("UPDATE words SET meaning=? WHERE id=?", c.path("meaning").asText(), id);
    saveWordPronunciations(id, c.path("pronunciations"));
  }

  private void saveWordPronunciations(String id, JsonNode options) {
    db.update("DELETE FROM word_pronunciations WHERE word_id=?", id);
    for (String accent : List.of("US", "GB"))
      if (options.has(accent)) {
        JsonNode p = options.path(accent);
        db.update(
            "INSERT INTO word_pronunciations VALUES(?,?,?,?)",
            id,
            accent,
            p.path("ipa").asText(),
            p.path("audioUrl").asText(""));
      }
  }

  @Transactional
  public void savePhoneme(String id, JsonNode c) {
    ObjectNode detail = (ObjectNode) c.deepCopy();
    detail.remove(
        List.of(
            "ipa",
            "category",
            "group",
            "kind",
            "schemaVersion",
            "accent",
            "audio",
            "pronunciations",
            "exampleWords"));
    db.update(
        "UPDATE phonemes SET ipa=?,category=?,group_code=?,detail_json=? WHERE id=?",
        c.path("ipa").asText(),
        c.path("category").asText(),
        c.path("group").asText(),
        detail.toString(),
        id);
    db.update("DELETE FROM phoneme_pronunciations WHERE phoneme_id=?", id);
    for (String accent : List.of("US", "GB")) {
      String audio = c.path("pronunciations").path(accent).path("audioUrl").asText("");
      if (!audio.isBlank())
        db.update("INSERT INTO phoneme_pronunciations VALUES(?,?,?)", id, accent, audio);
    }
    db.update("DELETE FROM phoneme_examples WHERE phoneme_id=?", id);
    int order = 0;
    for (JsonNode example : c.path("exampleWords")) {
      String wordId = example.path("wordId").asText();
      String accent = example.path("accent").asText();
      ContentConfig.require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM word_pronunciations WHERE word_id=? AND accent=?",
                  Integer.class,
                  wordId,
                  accent)
              > 0,
          "例词必须引用已有单词及对应口音");
      db.update("INSERT INTO phoneme_examples VALUES(?,?,?,?)", id, wordId, accent, order++);
    }
  }

  private String slots(int count) {
    return String.join(",", Collections.nCopies(count, "?"));
  }
}
