package com.readenglish.content;

import com.readenglish.common.api.ApiException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ContentConfig {
  private final ObjectMapper mapper;
  private static final Set<String> TONES =
      Set.of("primary", "muted", "stress", "success", "normal");

  public ContentConfig(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public ObjectNode read(String json) {
    return (ObjectNode) mapper.readTree(json);
  }

  public ObjectNode emptyWord(String word, String ipa, String meaning, String audio) {
    ObjectNode result = mapper.createObjectNode();
    result
        .put("schemaVersion", 1)
        .put("kind", "WORD")
        .put("word", word)
        .put("accent", "US")
        .put("ipa", ipa)
        .put("meaning", meaning)
        .put("tip", "");
    result
        .putArray("ipaSegments")
        .addObject()
        .put("text", ipa)
        .put("tone", "muted")
        .put("bold", false);
    result.putArray("syllables");
    result.putArray("parts");
    result.putArray("questions");
    result.putArray("commonTips");
    result.putArray("specialTips");
    result.putObject("audio").put("url", audio == null ? "" : audio).put("objectKey", "");
    return result;
  }

  public void validate(JsonNode c, String kind) {
    require(c != null && c.isObject() && c.toString().length() <= 100000, "配置必须是有效对象且不超过 10 万字符");
    require(
        c.path("schemaVersion").asInt() == 1 && kind.equals(c.path("kind").asText()), "配置类型或版本不正确");
    text(c, "ipa", 160);
    require(Set.of("US", "GB", "BOTH").contains(c.path("accent").asText()), "口音只能是 US、GB 或 BOTH");
    segments(c.path("ipaSegments"));
    StringBuilder ipa = new StringBuilder();
    for (JsonNode segment : c.path("ipaSegments")) ipa.append(segment.path("text").asText());
    require(ipa.toString().equals(c.path("ipa").asText()), "音标片段拼接后必须等于完整 IPA");
    if (kind.equals("WORD")) {
      text(c, "word", 120);
      require(c.path("word").asText().matches("[A-Za-z]+(?:['’-][A-Za-z]+)*"), "单词格式不正确");
      text(c, "meaning", 300);
      array(c, "syllables", 30);
      array(c, "parts", 40);
      int primary = 0;
      for (JsonNode s : c.path("syllables")) {
        text(s, "text", 80);
        text(s, "ipa", 160);
        int stress = s.path("stress").asInt(-1);
        require(stress >= 0 && stress <= 2, "重音必须是 0、1、2");
        if (stress == 1) primary++;
        require(s.path("emphasized").isBoolean(), "音节 emphasized 必须是布尔值");
      }
      require(c.path("syllables").isEmpty() || primary == 1, "非空音节必须有且只有一个主重音");
      StringBuilder letters = new StringBuilder();
      for (JsonNode p : c.path("parts")) {
        text(p, "letters", 120);
        letters.append(p.path("letters").asText());
        partSegments(p.path("segments"));
        optionalText(p, "tip", 500);
      }
      require(
          c.path("parts").isEmpty() || letters.toString().equalsIgnoreCase(c.path("word").asText()),
          "拆分字母拼接后必须等于单词");
      strings(c, "commonTips");
      strings(c, "specialTips");
      optionalText(c, "tip", 500);
    } else {
      text(c, "category", 40);
      text(c, "description", 1000);
      require(Set.of("VOWEL", "CONSONANT").contains(c.path("group").asText()), "音标分组无效");
      JsonNode mouth = c.path("mouth");
      require(
          Set.of("open", "closed", "smile", "relaxed").contains(mouth.path("type").asText()),
          "口型类型无效");
      url(mouth.path("imageUrl").asText(""));
      strings(c, "pronunciationSteps");
      optionalText(c, "memoryTip", 1000);
      array(c, "exampleWords", 40);
      for (JsonNode w : c.path("exampleWords")) {
        text(w, "word", 120);
        text(w, "ipa", 160);
        text(w, "meaning", 300);
        url(w.path("audioUrl").asText(""));
      }
    }
    require(c.path("audio").isObject(), "audio 配置不能为空");
    // 保存时同时写入 words/phonemes 的 VARCHAR(500) 字段。
    optionalText(c.path("audio"), "url", 500);
    url(c.path("audio").path("url").asText(""));
    optionalText(c.path("audio"), "usUrl", 500);
    url(c.path("audio").path("usUrl").asText(""));
    optionalText(c.path("audio"), "gbUrl", 500);
    url(c.path("audio").path("gbUrl").asText(""));
    optionalText(c.path("audio"), "objectKey", 500);
    array(c, "questions", 20);
    Set<String> ids = new HashSet<>();
    for (JsonNode q : c.path("questions")) {
      text(q, "id", 64);
      require(
          q.path("id").asText().matches("[A-Za-z0-9_-]+") && ids.add(q.path("id").asText()),
          "题目 ID 无效或重复");
      require(Set.of("CHOICE", "LISTENING").contains(q.path("type").asText()), "题型无效");
      text(q, "prompt", 500);
      text(q, "explanation", 1000);
      array(q, "options", 6);
      require(q.path("options").size() >= 2, "题目至少两个选项");
      Set<String> options = new HashSet<>();
      Set<String> labels = new HashSet<>();
      for (JsonNode o : q.path("options")) {
        text(o, "id", 64);
        text(o, "label", 300);
        require(
            options.add(o.path("id").asText()) && labels.add(o.path("label").asText().trim()),
            "选项不能重复");
      }
      require(options.contains(q.path("correctOptionId").asText()), "正确答案必须对应一个选项");
      if (q.path("type").asText().equals("LISTENING")) {
        url(q.path("audioUrl").asText(""));
        require(!q.path("audioUrl").asText("").isBlank(), "听辨题必须有标准音频");
      }
    }
  }

  public JsonNode publicView(JsonNode config) {
    JsonNode copy = config.deepCopy();
    stripAnswers(copy);
    return copy;
  }

  private void stripAnswers(JsonNode node) {
    if (node.isObject()) {
      ((ObjectNode) node).remove("correctOptionId");
      ((ObjectNode) node).remove("explanation");
    }
    if (node.isObject() || node.isArray()) for (JsonNode child : node) stripAnswers(child);
  }

  private static void segments(JsonNode items) {
    require(items.isArray() && !items.isEmpty() && items.size() <= 60, "音标片段必须是非空数组");
    validateSegments(items);
  }

  private static void partSegments(JsonNode items) {
    require(items.isArray() && items.size() <= 60, "拆读音标片段必须是数组且不超过 60 项");
    validateSegments(items);
  }

  private static void validateSegments(JsonNode items) {
    for (JsonNode s : items) {
      text(s, "text", 160);
      require(TONES.contains(s.path("tone").asText()), "强调类型无效");
      require(s.path("bold").isBoolean(), "bold 必须是布尔值");
    }
  }

  private static void strings(JsonNode c, String field) {
    array(c, field, 30);
    for (JsonNode s : c.path(field))
      require(s.isString() && s.asText().length() <= 1000, "提示必须是字符串");
  }

  private static void array(JsonNode c, String field, int max) {
    require(
        c.path(field).isArray() && c.path(field).size() <= max, field + " 必须是数组且不超过 " + max + " 项");
  }

  private static void text(JsonNode c, String field, int max) {
    require(
        c.path(field).isString()
            && !c.path(field).asText().isBlank()
            && c.path(field).asText().length() <= max,
        field + " 不能为空或超过 " + max + " 字符");
  }

  private static void optionalText(JsonNode c, String field, int max) {
    if (c.has(field))
      require(c.path(field).isString() && c.path(field).asText().length() <= max, field + " 格式不正确");
  }

  private static void url(String value) {
    require(
        value.isEmpty() || (value.startsWith("https://") && value.length() <= 1500),
        "媒体地址必须为空或 HTTPS 地址");
  }

  public static void require(boolean condition, String message) {
    if (!condition) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }
}
