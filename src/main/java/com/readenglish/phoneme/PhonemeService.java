package com.readenglish.phoneme;

import com.readenglish.common.api.ApiException;
import com.readenglish.phoneme.PhonemeModels.ExampleWord;
import com.readenglish.phoneme.PhonemeModels.PhonemeDetailResponse;
import com.readenglish.phoneme.PhonemeModels.PhonemeGroup;
import com.readenglish.phoneme.PhonemeModels.PhonemeItem;
import com.readenglish.phoneme.PhonemeModels.PhonemeListResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PhonemeService {

  private static final Map<String, String> GROUP_NAMES = Map.of("VOWEL", "元音", "CONSONANT", "辅音");

  private final PhonemeRepository phonemeRepository;
  private final ObjectMapper objectMapper;

  public PhonemeService(PhonemeRepository phonemeRepository, ObjectMapper objectMapper) {
    this.phonemeRepository = phonemeRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public PhonemeListResponse list(String group) {
    if (group != null && !GROUP_NAMES.containsKey(group)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "音标分组只能是 VOWEL 或 CONSONANT");
    }

    List<String> groups = group == null ? List.of("VOWEL", "CONSONANT") : List.of(group);
    List<PhonemeGroup> responses = new ArrayList<>();
    for (String groupCode : groups) {
      List<PhonemeItem> items =
          phonemeRepository.findByGroupCodeOrderBySortOrderAsc(groupCode).stream()
              .map(
                  phoneme ->
                      new PhonemeItem(
                          phoneme.getId(),
                          phoneme.getIpa(),
                          phoneme.getAudioUrl(),
                          phoneme.getSortOrder()))
              .toList();
      responses.add(new PhonemeGroup(groupCode, GROUP_NAMES.get(groupCode), items));
    }
    return new PhonemeListResponse(responses);
  }

  @Transactional(readOnly = true)
  public PhonemeDetailResponse get(String id) {
    PhonemeEntity phoneme =
        phonemeRepository
            .findById(id)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "音标不存在"));
    JsonNode detail = readDetail(phoneme.getDetailJson());
    List<String> steps =
        StreamSupport.stream(detail.path("pronunciationSteps").spliterator(), false)
            .map(JsonNode::stringValue)
            .toList();
    List<ExampleWord> examples =
        StreamSupport.stream(detail.path("exampleWords").spliterator(), false)
            .map(
                item ->
                    new ExampleWord(
                        textOrNull(item, "wordId"),
                        textOrNull(item, "word"),
                        textOrNull(item, "ipa"),
                        textOrNull(item, "meaning"),
                        textOrNull(item, "audioUrl")))
            .toList();
    return new PhonemeDetailResponse(
        phoneme.getId(),
        phoneme.getIpa(),
        phoneme.getGroupCode(),
        phoneme.getCategory(),
        phoneme.getAudioUrl(),
        textOrNull(detail, "description"),
        detail.path("mouth"),
        steps,
        examples,
        textOrNull(detail, "memoryTip"));
  }

  private JsonNode readDetail(String detailJson) {
    try {
      return objectMapper.readTree(detailJson);
    } catch (JacksonException exception) {
      throw new IllegalStateException("音标内容格式不正确", exception);
    }
  }

  private static String textOrNull(JsonNode node, String fieldName) {
    JsonNode value = node.path(fieldName);
    return value.isMissingNode() || value.isNull() ? null : value.stringValue();
  }
}
