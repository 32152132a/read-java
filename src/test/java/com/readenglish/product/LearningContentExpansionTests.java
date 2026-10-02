package com.readenglish.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class LearningContentExpansionTests {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void phonemeLessonsCoverEveryCatalogPhonemeOnce() throws Exception {
    Map<String, String> catalog = new HashMap<>();
    Map<String, JsonNode> catalogDetails = new HashMap<>();
    jdbc.queryForList("select id, ipa, detail_json from phonemes").stream()
        .forEach(
            row -> {
              String id = (String) row.get("id");
              catalog.put(id, (String) row.get("ipa"));
              catalogDetails.put(id, readJson((String) row.get("detail_json")));
            });

    Map<String, String> covered = new HashMap<>();
    Set<Integer> sortOrders = new HashSet<>();

    jdbc.queryForList(
            """
            select id, title, content_json, sort_order
            from learning_units
            where template_code = 'phoneme' and enabled = true
            order by sort_order
            """)
        .forEach(
            row -> {
              JsonNode content = readJson((String) row.get("content_json"));
              String phonemeId = content.path("phonemeId").asText();
              String ipa = content.path("ipa").asText();

              assertThat(phonemeId).as("unit %s phonemeId", row.get("id")).isNotBlank();
              assertThat(catalog).containsKey(phonemeId);
              assertThat(ipa).isEqualTo(catalog.get(phonemeId));
              assertThat(content.path("description").asText()).isNotBlank();
              assertThat(content.path("pronunciationSteps").size()).isBetween(2, 3);
              assertThat(content.path("exampleWords").size()).isBetween(2, 3);
              assertThat(content.path("memoryTip").asText()).isNotBlank();
              assertThat(content.path("speechFallback").asText()).isNotBlank();
              assertThat(catalogDetails.get(phonemeId).path("exampleWords").size())
                  .as("phoneme %s catalog examples", phonemeId)
                  .isBetween(2, 3);
              assertThat(catalogDetails.get(phonemeId).path("pronunciationSteps").size())
                  .as("phoneme %s catalog pronunciation steps", phonemeId)
                  .isBetween(2, 3);
              assertThat(sortOrders.add((Integer) row.get("sort_order")))
                  .as("unit %s sort_order should be unique", row.get("id"))
                  .isTrue();
              assertThat(covered).doesNotContainKey(phonemeId);

              covered.put(phonemeId, (String) row.get("id"));
            });

    assertThat(catalog).hasSize(48);
    assertThat(covered).containsOnlyKeys(catalog.keySet());
  }

  private JsonNode readJson(String contentJson) {
    try {
      return objectMapper.readTree(contentJson);
    } catch (Exception exception) {
      throw new AssertionError("learning unit content_json should be valid JSON", exception);
    }
  }
}
