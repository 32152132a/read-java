package com.readenglish.content;

import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ContentProjection {
  private final ContentService contents;
  private final ContentConfig configs;
  private final PronunciationResources resources;

  public ContentProjection(
      ContentService contents, ContentConfig configs, PronunciationResources resources) {
    this.contents = contents;
    this.configs = configs;
    this.resources = resources;
  }

  public JsonNode project(String user, JsonNode units) {
    var wordIds = new LinkedHashSet<String>();
    var phonemeIds = new LinkedHashSet<String>();
    collect(units, wordIds, phonemeIds);
    var words = contents.publicWordViews(user, List.copyOf(wordIds));
    var phonemes = resources.phonemes(List.copyOf(phonemeIds), resources.preferredAccent(user));
    hydrate(units, words, phonemes);
    return configs.publicView(units);
  }

  private void collect(JsonNode node, Set<String> words, Set<String> phonemes) {
    if (node.isObject()) {
      if (node.hasNonNull("wordId")) words.add(node.path("wordId").asText());
      if (node.hasNonNull("phonemeId")) phonemes.add(node.path("phonemeId").asText());
    }
    if (node.isObject() || node.isArray())
      for (JsonNode child : node) collect(child, words, phonemes);
  }

  private void hydrate(
      JsonNode node, Map<String, ContentService.WordView> words, Map<String, ObjectNode> phonemes) {
    if (node.isArray()) {
      for (JsonNode child : node) hydrate(child, words, phonemes);
      return;
    }
    if (!node.isObject()) return;
    ObjectNode c = (ObjectNode) node;
    var children = new ArrayList<JsonNode>();
    c.forEach(children::add);
    for (JsonNode child : children) hydrate(child, words, phonemes);
    if (c.hasNonNull("wordId")) {
      var view = words.get(c.path("wordId").asText());
      ContentConfig.require(view != null, "课程引用的单词不存在");
      ObjectNode w = (ObjectNode) view.config().deepCopy();
      if (c.has("accent") && !c.path("accent").asText().equals(w.path("accent").asText())) {
        resources.selectWord(w, c.path("accent").asText(), true);
        w.remove(List.of("parts", "syllables"));
      }
      for (String key : List.of("word", "ipa", "meaning", "accent", "pronunciations"))
        c.set(key, w.path(key));
      c.put("audioUrl", w.path("audio").path("url").asText());
      c.put("standardAudioUrl", w.path("audio").path("url").asText());
      c.set("teachingConfig", w);
    }
    if (c.hasNonNull("phonemeId")) {
      ObjectNode p = phonemes.get(c.path("phonemeId").asText());
      ContentConfig.require(p != null, "课程引用的音标不存在");
      if (c.has("id") && !c.has("questionId")) {
        c.put("label", p.path("ipa").asText());
        return;
      }
      if (c.has("questionId")) {
        String audio = p.path("audio").path("url").asText();
        var audios = c.putArray("audioUrls");
        if (!audio.isBlank()) audios.add(audio);
      } else {
        c.set("teachingConfig", p);
        for (String key :
            List.of(
                "ipa",
                "category",
                "description",
                "mouth",
                "pronunciationSteps",
                "exampleWords",
                "memoryTip",
                "pronunciations")) c.set(key, p.path(key));
        c.put("audioUrl", p.path("audio").path("url").asText());
        c.put("audioUsUrl", p.path("audio").path("usUrl").asText());
        c.put("audioGbUrl", p.path("audio").path("gbUrl").asText());
      }
    }
  }
}
