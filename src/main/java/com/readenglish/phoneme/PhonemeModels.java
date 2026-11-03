package com.readenglish.phoneme;

import java.util.List;
import tools.jackson.databind.JsonNode;

public final class PhonemeModels {

  private PhonemeModels() {}

  public record PhonemeItem(
      String id, String ipa, String audioUrl, String audioUsUrl, String audioGbUrl, int order) {}

  public record PhonemeGroup(String code, String name, List<PhonemeItem> items) {}

  public record PhonemeListResponse(List<PhonemeGroup> groups) {}

  public record PhonemeDetailResponse(
      String id,
      String ipa,
      String group,
      String category,
      String audioUrl,
      String audioUsUrl,
      String audioGbUrl,
      String description,
      JsonNode mouth,
      List<String> pronunciationSteps,
      List<JsonNode> exampleWords,
      String memoryTip,
      JsonNode teachingConfig) {}
}
