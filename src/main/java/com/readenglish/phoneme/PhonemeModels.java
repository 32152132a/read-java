package com.readenglish.phoneme;

import java.util.List;
import tools.jackson.databind.JsonNode;

public final class PhonemeModels {

  private PhonemeModels() {}

  public record PhonemeItem(
      String id,
      String ipa,
      String audioUrl,
      String audioUsUrl,
      String audioGbUrl,
      String fallbackWord,
      int order) {}

  public record PhonemeGroup(String code, String name, List<PhonemeItem> items) {}

  public record PhonemeListResponse(List<PhonemeGroup> groups) {}

  public record ExampleWord(
      String wordId, String word, String ipa, String meaning, String audioUrl) {}

  public record PhonemeDetailResponse(
      String id,
      String ipa,
      String group,
      String category,
      String audioUrl,
      String audioUsUrl,
      String audioGbUrl,
      String fallbackWord,
      String description,
      JsonNode mouth,
      List<String> pronunciationSteps,
      List<ExampleWord> exampleWords,
      String memoryTip,
      JsonNode teachingConfig) {}
}
