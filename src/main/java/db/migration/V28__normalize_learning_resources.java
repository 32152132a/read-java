package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 一次性归并教学种子；保留课程与词条 ID，运行时不再读取旧格式。 */
public class V28__normalize_learning_resources extends BaseJavaMigration {
  private final ObjectMapper mapper = new ObjectMapper();
  private Connection db;

  @Override
  public void migrate(Context context) throws Exception {
    db = context.getConnection();
    boolean mysql =
        db.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("mysql");
    update(
        mysql
            ? "ALTER TABLE words DROP INDEX uk_word_accent"
            : "ALTER TABLE words DROP CONSTRAINT uk_word_accent");
    for (var row : rows("SELECT * FROM words")) {
      update(
          "INSERT INTO word_pronunciations(word_id,accent,ipa,audio_url) VALUES(?,?,?,?)",
          row.get("id"),
          row.get("accent"),
          row.get("ipa"),
          row.get("audio_url"));
    }
    // 已有人工配置优先，之后所有音标页面都从 detail_json 读取。
    for (var row :
        rows(
            "SELECT p.*,e.config_json FROM phonemes p LEFT JOIN content_entries e ON e.kind='PHONEME' AND e.source_id=p.id AND e.scope_id=''")) {
      String id = string(row, "id");
      ObjectNode detail =
          json(row.get("config_json") == null ? row.get("detail_json") : row.get("config_json"));
      for (String accent : List.of("US", "GB")) {
        String audio =
            detail.path("audio").path(accent.equals("US") ? "usUrl" : "gbUrl").asText("");
        if (audio.isBlank())
          audio = string(row, accent.equals("US") ? "audio_us_url" : "audio_gb_url");
        // 未注明口音的地址不自动冒充英音或美音。
        if (!audio.isBlank())
          update("INSERT INTO phoneme_pronunciations VALUES(?,?,?)", id, accent, audio);
      }
      int order = 0;
      for (JsonNode example : detail.path("exampleWords")) {
        String wordId = word(example, "GB");
        update("INSERT INTO phoneme_examples VALUES(?,?,?,?)", id, wordId, "GB", order++);
      }
      String hint = hints().get(string(row, "ipa").replace("/", ""));
      if (hint != null) detail.put("description", detail.path("description").asText() + " " + hint);
      detail.remove(
          List.of(
              "audio",
              "audioUrl",
              "audioUsUrl",
              "audioGbUrl",
              "exampleWords",
              "speechFallback",
              "ipa",
              "category",
              "group",
              "kind",
              "schemaVersion",
              "accent"));
      if (!detail.has("questions")) detail.putArray("questions");
      update("UPDATE phonemes SET detail_json=? WHERE id=?", detail.toString(), id);
    }

    for (var row : rows("SELECT * FROM learning_units ORDER BY sort_order")) {
      ObjectNode c = json(row.get("content_json"));
      String type = string(row, "content_type");
      if (type.equals("PHONEME_DETAIL")) {
        if (!c.hasNonNull("phonemeId")) {
          var found = rows("SELECT id FROM phonemes WHERE ipa=?", c.path("ipa").asText());
          if (found.isEmpty()) throw new IllegalStateException("课程音标不存在: " + row.get("id"));
          c.put("phonemeId", string(found.getFirst(), "id"));
        }
        c.remove(
            List.of(
                "ipa",
                "category",
                "description",
                "mouth",
                "pronunciationSteps",
                "exampleWords",
                "memoryTip",
                "audioUrl",
                "speechFallback"));
      } else {
        normalizeReferences(c, string(row, "template_code").equals("evaluation") ? "US" : "GB");
      }
      if (string(row, "template_code").equals("phoneme-quiz")) {
        var targets =
            rows(
                "SELECT p.id FROM quiz_questions q JOIN quiz_options o ON o.question_id=q.id AND o.correct=TRUE JOIN phonemes p ON p.ipa=o.label WHERE q.id=?",
                c.path("questionId").asText());
        if (targets.isEmpty()) throw new IllegalStateException("听音题缺少目标音标: " + row.get("id"));
        c.put("phonemeId", string(targets.getFirst(), "id"));
        c.remove("audioUrls");
        c.put("prompt", "点击播放按钮可重复播放");
        c.put("description", "听发音，选择对应的音标。");
        for (JsonNode option : c.path("options")) {
          var found = rows("SELECT id FROM phonemes WHERE ipa=?", option.path("label").asText());
          if (found.isEmpty()) throw new IllegalStateException("听音选项缺少对应音标: " + option);
          ((ObjectNode) option).put("phonemeId", string(found.getFirst(), "id"));
          ((ObjectNode) option).remove("label");
        }
      }
      update("UPDATE learning_units SET content_json=? WHERE id=?", c.toString(), row.get("id"));
    }

    // 拆读资料一次性写入教学配置，删除运行时扫描课程补资料的分支。
    for (var row : rows("SELECT * FROM words")) {
      String id = string(row, "id");
      var entries =
          rows(
              "SELECT id,config_json FROM content_entries WHERE kind='WORD' AND source_id=? AND scope_id=''",
              id);
      if (entries.isEmpty()) {
        update(
            "INSERT INTO content_entries(id,kind,source_id,scope_id,label,version,config_json) VALUES(?,'WORD',?,'',?,0,?)",
            "content_" + id,
            id,
            row.get("display_word"),
            teaching(id).toString());
      } else if (entries.getFirst().get("config_json") == null) {
        update(
            "UPDATE content_entries SET config_json=? WHERE id=?",
            teaching(id).toString(),
            entries.getFirst().get("id"));
      }
    }
    for (var row : rows("SELECT * FROM content_entries WHERE config_json IS NOT NULL")) {
      ObjectNode c = json(row.get("config_json"));
      if (string(row, "kind").equals("PHONEME")) {
        update("UPDATE content_entries SET config_json=NULL WHERE id=?", row.get("id"));
      } else {
        c.remove(List.of("audio", "pronunciations", "word", "wordId", "ipa"));
        if (string(row, "scope_id").isBlank()) c.remove("meaning");
        update("UPDATE content_entries SET config_json=? WHERE id=?", c.toString(), row.get("id"));
      }
    }
    // 旧公共地址字段彻底移除；API 可返回派生展示字段，但没有第二份存储。
    update("ALTER TABLE words DROP COLUMN ipa");
    update("ALTER TABLE words DROP COLUMN accent");
    update("ALTER TABLE words DROP COLUMN audio_url");
    update("ALTER TABLE words ADD CONSTRAINT uk_word_variant UNIQUE(normalized_word,variant_key)");
    for (String column : List.of("audio_url", "audio_us_url", "audio_gb_url"))
      update("ALTER TABLE phonemes DROP COLUMN " + column);
  }

  private ObjectNode teaching(String wordId) throws SQLException {
    ObjectNode result = mapper.createObjectNode();
    for (var row :
        rows(
            "SELECT content_json FROM learning_units WHERE content_type IN ('IPA_DECODING','WORD_DECODING') ORDER BY sort_order")) {
      ObjectNode c = json(row.get("content_json"));
      if (!c.path("wordId").asText().equals(wordId)) continue;
      result.put("accent", c.path("accent").asText());
      for (String key : List.of("tip", "commonTips", "specialTips"))
        if (c.has(key)) result.set(key, c.path(key));
      if (c.has("parts") && !result.has("parts")) {
        var parts = result.putArray("parts");
        for (JsonNode part : c.path("parts")) {
          var p = parts.addObject().put("letters", part.path("letters").asText()).put("tip", "");
          p.putArray("segments")
              .addObject()
              .put("text", part.path("ipa").asText())
              .put("tone", "primary")
              .put("bold", false);
        }
      }
    }
    return result;
  }

  private void normalizeReferences(ObjectNode node, String accent) throws SQLException {
    for (JsonNode pair : node.path("audioPairs")) {
      for (String side : List.of("left", "right"))
        if (pair.path(side).isString()) {
          String name = pair.path(side).asText();
          ObjectNode reference = mapper.createObjectNode().put("word", name).put("audioUrl", "");
          ((ObjectNode) pair).set(side, reference);
        }
    }
    if (node.has("wordId") || (node.has("word") && node.has("audioUrl"))) {
      String id = word(node, accent);
      node.put("wordId", id).put("accent", accent);
      node.remove(List.of("word", "ipa", "meaning", "audioUrl", "standardAudioUrl"));
    }
    if (node.has("phonemeId")) node.remove(List.of("ipa", "description", "mouth", "audioUrl"));
    for (JsonNode child : node) {
      if (child.isObject()) normalizeReferences((ObjectNode) child, accent);
      else if (child.isArray())
        for (JsonNode item : child)
          if (item.isObject()) normalizeReferences((ObjectNode) item, accent);
    }
  }

  private String word(JsonNode c, String accent) throws SQLException {
    String name = c.path("word").asText().trim();
    String ipa = c.path("ipa").asText();
    String id = c.path("wordId").asText();
    var known =
        id.isBlank() ? List.<Map<String, Object>>of() : rows("SELECT * FROM words WHERE id=?", id);
    if (known.isEmpty()) {
      var matches =
          rows(
              "SELECT w.* FROM words w LEFT JOIN word_pronunciations p ON p.word_id=w.id AND p.accent=? WHERE w.normalized_word=? AND (?='' OR p.ipa=? OR p.ipa IS NULL) ORDER BY w.id",
              accent,
              name.toLowerCase(Locale.ROOT),
              ipa,
              ipa);
      if (!matches.isEmpty()) id = string(matches.getFirst(), "id");
      else {
        if (name.isBlank()) throw new IllegalStateException("例词缺少单词: " + c);
        if (id.isBlank())
          id =
              "w_ref_"
                  + UUID.nameUUIDFromBytes((name + "\n" + ipa).getBytes(StandardCharsets.UTF_8))
                      .toString()
                      .replace("-", "");
        String variant =
            rows("SELECT id FROM words WHERE normalized_word=?", name.toLowerCase(Locale.ROOT))
                    .isEmpty()
                ? "default"
                : id;
        update(
            "INSERT INTO words(id,normalized_word,display_word,meaning,ipa,accent,audio_url,variant_key) VALUES(?,?,?,?,?,'GB',NULL,?)",
            id,
            name.toLowerCase(Locale.ROOT),
            name,
            c.path("meaning").asText(""),
            ipa,
            variant);
      }
    }
    if (!ipa.isBlank()
        && rows("SELECT word_id FROM word_pronunciations WHERE word_id=? AND accent=?", id, accent)
            .isEmpty()) {
      update(
          "INSERT INTO word_pronunciations VALUES(?,?,?,?)",
          id,
          accent,
          ipa,
          c.path("audioUrl").asText(""));
    }
    return id;
  }

  private ObjectNode json(Object value) {
    return (ObjectNode) mapper.readTree(value.toString());
  }

  private String string(Map<String, Object> row, String key) {
    return row.get(key) == null ? "" : row.get(key).toString();
  }

  private void update(String sql, Object... args) throws SQLException {
    try (var statement = db.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
      statement.executeUpdate();
    }
  }

  private List<Map<String, Object>> rows(String sql, Object... args) throws SQLException {
    try (var statement = db.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
      try (var result = statement.executeQuery()) {
        var rows = new ArrayList<Map<String, Object>>();
        while (result.next()) {
          var row = new LinkedHashMap<String, Object>();
          for (int i = 1; i <= result.getMetaData().getColumnCount(); i++)
            row.put(
                result.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT),
                result.getObject(i));
          rows.add(row);
        }
        return rows;
      }
    }
  }

  private Map<String, String> hints() {
    return Map.ofEntries(
        Map.entry("ɪ", "可联想短“衣”，但更放松、嘴稍张开，不要拉长。"),
        Map.entry("iː", "可联想“衣——”，保持一个平稳长音。"),
        Map.entry("e", "可从“诶”联想，只保留短音，不滑向“衣”。"),
        Map.entry("æ", "可从“诶”联想，把嘴张大，不读成完整的“哎”。"),
        Map.entry("ɜː", "可联想不卷舌的“呃——”，这是英式提示。"),
        Map.entry("ə", "像很轻、很短的“呃”，一带而过。"),
        Map.entry("ʌ", "可联想短“啊”，嘴自然张开，舌头不要压得太低。"),
        Map.entry("ɑː", "可联想“啊——”，舌头放低、偏后。"),
        Map.entry("ɒ", "从短“啊”联想，再收圆嘴唇，不是普通话的“哦”。"),
        Map.entry("ɔː", "可联想“哦——”的圆唇感，嘴张得稍大。"),
        Map.entry("ʊ", "像短“乌”，但嘴唇和舌头更放松。"),
        Map.entry("uː", "可联想“乌——”，收圆嘴唇，保持一个音。"),
        Map.entry("eɪ", "可联想“诶”，滑向短“衣”，连成一个音。"),
        Map.entry("aɪ", "可联想“爱”，从“啊”滑向短“衣”。"),
        Map.entry("ɔɪ", "从圆唇的“哦”滑向短“衣”。"),
        Map.entry("əʊ", "可联想“欧”，从放松的中间音滑向“乌”。"),
        Map.entry("aʊ", "可联想“奥”，从“啊”滑向“乌”。"),
        Map.entry("ɪə", "从放松的短“衣”滑向轻“呃”，不卷舌。"),
        Map.entry("eə", "从张口的“诶”滑向轻“呃”，不卷舌。"),
        Map.entry("ʊə", "从放松的短“乌”滑向轻“呃”，这是传统英式记法。"),
        Map.entry("p", "取“坡”开头的气声，不带“哦”。"),
        Map.entry("b", "取“波”开头的闭唇动作，轻轻发声，不带“哦”。"),
        Map.entry("t", "取“特”开头的气声，不带“呃”。"),
        Map.entry("d", "取“的”开头的舌尖动作，轻轻发声，不带元音。"),
        Map.entry("k", "取“科”开头的气声，不带“哦”。"),
        Map.entry("ɡ", "取“哥”开头的舌后部动作，轻轻发声，不带元音。"),
        Map.entry("f", "像轻吹“夫”，只留摩擦气声，不带“乌”。"),
        Map.entry("v", "从“夫”的气声联想，上齿轻碰下唇，让喉咙振动。"),
        Map.entry("θ", "中文没有直接同音：轻露舌尖吹气，不读成“思”。"),
        Map.entry("ð", "轻露舌尖并让喉咙振动，不读成“的”或“兹”。"),
        Map.entry("s", "像“嘶——”的漏气声，不带后面的元音。"),
        Map.entry("z", "从“嘶”的气声联想，让喉咙振动，不读完整的“兹”。"),
        Map.entry("ʃ", "像让人安静时“嘘”的气声，不带“乌”，不卷舌。"),
        Map.entry("ʒ", "从 /ʃ/ 的气声联想，让喉咙振动。"),
        Map.entry("h", "像对玻璃哈气，只留气声，不带“啊”。"),
        Map.entry("tʃ", "可联想“吃”开头的破擦声，但不卷舌、不带元音。"),
        Map.entry("dʒ", "和 /tʃ/ 动作相近，加上喉咙振动，不直接读成“鸡”。"),
        Map.entry("m", "像闭嘴发“嗯——”，声音从鼻腔出来。"),
        Map.entry("n", "取“呢”开头的鼻音，不带“呃”。"),
        Map.entry("ŋ", "取“昂”末尾的鼻音，不加“啊”或“哥”。"),
        Map.entry("l", "取“了”开头的感觉，舌尖抵上齿龈，不带“呃”。"),
        Map.entry("r", "可联想“日”的舌头后抬感，但不碰上腭、不摩擦、不带元音。"),
        Map.entry("j", "像“呀”开头一闪而过的“衣”感，不拉长。"),
        Map.entry("w", "像“哇”开头的圆唇动作，不单独读出“乌”。"),
        Map.entry("tr", "先 /t/ 后 /r/ 连起来，不读成“特—瑞”两拍。"),
        Map.entry("dr", "先 /d/ 后 /r/ 连起来，中间不加“呃”。"),
        Map.entry("ts", "可联想“次”开头，先 /t/ 后 /s/，不带元音。"),
        Map.entry("dz", "先 /d/ 后 /z/ 连起来，喉咙振动，不读完整的“兹”。"));
  }
}
