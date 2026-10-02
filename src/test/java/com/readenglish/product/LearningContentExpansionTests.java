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

  @Test
  void phonemeCompareLessonsCoverFirstBatchPairs() {
    Map<String, String> catalog = new HashMap<>();
    jdbc.queryForList("select id, ipa from phonemes")
        .forEach(row -> catalog.put((String) row.get("id"), (String) row.get("ipa")));

    Set<Integer> sortOrders = new HashSet<>();
    Map<Integer, Set<String>> coveredPairs = new HashMap<>();

    jdbc.queryForList(
            """
            select id, title, content_json, sort_order
            from learning_units
            where template_code = 'phoneme-compare' and enabled = true
            order by sort_order
            """)
        .forEach(
            row -> {
              JsonNode content = readJson((String) row.get("content_json"));
              JsonNode phonemes = content.path("phonemes");
              JsonNode audioPairs = content.path("audioPairs");

              assertThat(content.path("category").asText())
                  .as("unit %s category", row.get("id"))
                  .isNotBlank();
              assertThat(content.path("title").asText())
                  .as("unit %s title", row.get("id"))
                  .isNotBlank();
              assertThat(content.path("description").asText())
                  .as("unit %s description", row.get("id"))
                  .isNotBlank();
              assertThat(content.path("memoryTip").asText())
                  .as("unit %s memoryTip", row.get("id"))
                  .isNotBlank();
              assertThat(phonemes.size()).as("unit %s phonemes", row.get("id")).isEqualTo(2);
              assertThat(audioPairs.size()).as("unit %s audioPairs", row.get("id")).isGreaterThanOrEqualTo(2);
              assertThat(sortOrders.add((Integer) row.get("sort_order")))
                  .as("unit %s sort_order should be unique", row.get("id"))
                  .isTrue();

              Set<String> pair = new HashSet<>();
              phonemes.forEach(
                  phoneme -> {
                    String phonemeId = phoneme.path("phonemeId").asText();
                    String ipa = phoneme.path("ipa").asText();

                    assertThat(phonemeId).as("unit %s phonemeId", row.get("id")).isNotBlank();
                    assertThat(catalog).containsKey(phonemeId);
                    assertThat(ipa).isEqualTo(catalog.get(phonemeId));
                    assertThat(phoneme.path("description").asText()).isNotBlank();
                    assertThat(phoneme.path("examples").size()).isGreaterThanOrEqualTo(2);

                    pair.add(phonemeId);
                  });
              audioPairs.forEach(
                  audioPair -> {
                    assertThat(audioPair.path("left").asText()).isNotBlank();
                    assertThat(audioPair.path("right").asText()).isNotBlank();
                    assertThat(audioPair.path("tip").asText()).isNotBlank();
                  });

              coveredPairs.put((Integer) row.get("sort_order"), pair);
            });

    assertThat(coveredPairs).hasSize(24);
    assertThat(coveredPairs.get(1)).containsExactlyInAnyOrder("p_1", "p_2");
    assertThat(coveredPairs.get(2)).containsExactlyInAnyOrder("p_v_03", "p_v_04");
    assertThat(coveredPairs.get(3)).containsExactlyInAnyOrder("p_v_09", "p_v_08");
    assertThat(coveredPairs.get(4)).containsExactlyInAnyOrder("p_v_11", "p_v_10");
    assertThat(coveredPairs.get(5)).containsExactlyInAnyOrder("p_v_07", "p_v_12");
    assertThat(coveredPairs.get(6)).containsExactlyInAnyOrder("p_v_06", "p_v_05");
    assertThat(coveredPairs.get(7)).containsExactlyInAnyOrder("p_v_13", "p_v_14");
    assertThat(coveredPairs.get(8)).containsExactlyInAnyOrder("p_v_16", "p_v_17");
    assertThat(coveredPairs.get(9)).containsExactlyInAnyOrder("p_3", "p_c_02");
    assertThat(coveredPairs.get(10)).containsExactlyInAnyOrder("p_c_03", "p_c_04");
    assertThat(coveredPairs.get(11)).containsExactlyInAnyOrder("p_c_05", "p_c_06");
    assertThat(coveredPairs.get(12)).containsExactlyInAnyOrder("p_c_07", "p_c_08");
    assertThat(coveredPairs.get(13)).containsExactlyInAnyOrder("p_c_11", "p_c_12");
    assertThat(coveredPairs.get(14)).containsExactlyInAnyOrder("p_c_09", "p_c_10");
    assertThat(coveredPairs.get(15)).containsExactlyInAnyOrder("p_c_13", "p_c_14");
    assertThat(coveredPairs.get(16)).containsExactlyInAnyOrder("p_c_16", "p_c_17");
  }

  @Test
  void expandedPracticeStagesReachFirstBatchTargets() {
    assertThat(countUnits("phoneme-quiz")).isEqualTo(96);
    assertThat(countUnits("syllable")).isEqualTo(48);
    assertThat(countUnits("ipa-decoding")).isEqualTo(100);
    assertThat(countUnits("word-decoding")).isEqualTo(100);

    assertThat(countQuestions("phoneme-quiz")).isEqualTo(96);
    assertThat(countQuestions("syllable")).isEqualTo(48);

    jdbc.queryForList(
            """
            select id, content_json
            from learning_units
            where template_code in ('phoneme-quiz', 'syllable', 'ipa-decoding', 'word-decoding')
              and enabled = true
            """)
        .forEach(
            row -> {
              String id = (String) row.get("id");
              JsonNode content = readJson((String) row.get("content_json"));

              assertThat(content.size()).as("unit %s content should not be empty", id).isPositive();
              if (id.startsWith("unit_quiz_")) {
                assertThat(content.path("questionId").asText()).isNotBlank();
                assertThat(content.path("options").size()).isEqualTo(4);
              } else if (id.startsWith("unit_syllable_")) {
                assertThat(content.path("segments").size()).isGreaterThanOrEqualTo(2);
                assertThat(content.path("rules").size()).isGreaterThanOrEqualTo(2);
                assertThat(content.path("question").path("options").size()).isEqualTo(4);
              } else if (id.startsWith("unit_ipa_")) {
                assertThat(content.path("hiddenWord").booleanValue()).isTrue();
                assertThat(content.path("ipa").asText()).isNotBlank();
                assertThat(content.path("syllables").size()).isGreaterThanOrEqualTo(2);
              } else if (id.startsWith("unit_word_")) {
                assertThat(content.path("word").asText()).isNotBlank();
                assertThat(content.path("ipa").asText()).isNotBlank();
                assertThat(content.path("parts").size()).isGreaterThanOrEqualTo(2);
                assertThat(content.path("commonTips").size()).isGreaterThanOrEqualTo(1);
                assertThat(content.path("specialTips").size()).isGreaterThanOrEqualTo(1);
              }
            });
  }

  private Integer countUnits(String templateCode) {
    return jdbc.queryForObject(
        "select count(*) from learning_units where template_code = ? and enabled = true",
        Integer.class,
        templateCode);
  }

  private Integer countQuestions(String templateCode) {
    return jdbc.queryForObject(
        "select count(*) from quiz_questions where template_code = ?", Integer.class, templateCode);
  }

  private JsonNode readJson(String contentJson) {
    try {
      return objectMapper.readTree(contentJson);
    } catch (Exception exception) {
      throw new AssertionError("learning unit content_json should be valid JSON", exception);
    }
  }
}
