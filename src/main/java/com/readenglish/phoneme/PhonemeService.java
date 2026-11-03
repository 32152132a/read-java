package com.readenglish.phoneme;

import com.readenglish.common.api.ApiException;
import com.readenglish.content.ContentConfig;
import com.readenglish.content.PronunciationResources;
import com.readenglish.phoneme.PhonemeModels.*;
import java.util.*;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class PhonemeService {
  private static final Map<String, String> GROUP_NAMES = Map.of("VOWEL", "元音", "CONSONANT", "辅音");
  private final PhonemeRepository repository;
  private final PronunciationResources resources;
  private final ContentConfig configs;

  public PhonemeService(
      PhonemeRepository repository, PronunciationResources resources, ContentConfig configs) {
    this.repository = repository;
    this.resources = resources;
    this.configs = configs;
  }

  @Transactional(readOnly = true)
  public PhonemeListResponse list(String group) {
    if (group != null && !GROUP_NAMES.containsKey(group))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "音标分组只能是 VOWEL 或 CONSONANT");
    var groups = new ArrayList<PhonemeGroup>();
    for (String code : group == null ? List.of("VOWEL", "CONSONANT") : List.of(group)) {
      var entities = repository.findByGroupCodeOrderBySortOrderAsc(code);
      var data = resources.phonemes(entities.stream().map(PhonemeEntity::getId).toList(), "GB");
      var items =
          entities.stream()
              .map(
                  p -> {
                    var audio = data.get(p.getId()).path("audio");
                    return new PhonemeItem(
                        p.getId(),
                        p.getIpa(),
                        audio.path("url").asText(),
                        audio.path("usUrl").asText(),
                        audio.path("gbUrl").asText(),
                        p.getSortOrder());
                  })
              .toList();
      groups.add(new PhonemeGroup(code, GROUP_NAMES.get(code), items));
    }
    return new PhonemeListResponse(groups);
  }

  @Transactional(readOnly = true)
  public PhonemeDetailResponse get(String id) {
    var p =
        repository
            .findById(id)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "音标不存在"));
    JsonNode c = configs.publicView(resources.phonemes(List.of(id), "GB").get(id));
    var steps =
        StreamSupport.stream(c.path("pronunciationSteps").spliterator(), false)
            .map(JsonNode::asText)
            .toList();
    var examples = StreamSupport.stream(c.path("exampleWords").spliterator(), false).toList();
    return new PhonemeDetailResponse(
        id,
        p.getIpa(),
        p.getGroupCode(),
        p.getCategory(),
        c.path("audio").path("url").asText(),
        c.path("audio").path("usUrl").asText(),
        c.path("audio").path("gbUrl").asText(),
        c.path("description").asText(),
        c.path("mouth"),
        steps,
        examples,
        c.path("memoryTip").asText(),
        c);
  }
}
