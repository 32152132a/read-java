package com.readenglish.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationRegressionTests {
  @LocalServerPort private int port;
  @Autowired private ObjectMapper mapper;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void localWebPreflightAllowsRequiredHeadersButRejectsUnknownOrigins() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      for (String origin : new String[] {"http://127.0.0.1:15173", "https://unknown.example"}) {
        var response =
            client.send(
                HttpRequest.newBuilder(uri("/learning-flow/current/complete"))
                    .header("Origin", origin)
                    .header("Access-Control-Request-Method", "POST")
                    .header(
                        "Access-Control-Request-Headers",
                        "authorization,content-type,idempotency-key")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        if (origin.startsWith("http://127")) {
          assertThat(response.statusCode()).isEqualTo(200);
          assertThat(response.headers().firstValue("access-control-allow-origin")).contains(origin);
        } else {
          assertThat(response.statusCode()).isEqualTo(403);
          assertThat(response.headers().firstValue("access-control-allow-origin")).isEmpty();
        }
      }
    }
  }

  @Test
  void flowChangesPreserveHistoricalStatsAndRepeatedLessonsAreNotCountedTwice() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      String token = login(client);
      JsonNode flow = call(client, token, "GET", "/learning-flow", null);
      complete(client, token, flow.path("nodes").get(0).path("id").stringValue());
      JsonNode before = call(client, token, "GET", "/users/me/stats", null);
      JsonNode changed =
          call(
              client,
              token,
              "PUT",
              "/learning-flow",
              "{\"version\":"
                  + flow.path("version").longValue()
                  + ",\"nodes\":[{\"templateCode\":\"phoneme\"}]}");
      assertThat(call(client, token, "GET", "/users/me/stats", null)).isEqualTo(before);
      complete(client, token, changed.path("nodes").get(0).path("id").stringValue());
      JsonNode after = call(client, token, "GET", "/users/me/stats", null);
      assertThat(after.path("completedStages").intValue()).isEqualTo(2);
      assertThat(after.path("learnedUnits")).isEqualTo(before.path("learnedUnits"));
      assertThat(after.path("streakDays").intValue()).isEqualTo(1);
    }
  }

  @Test
  void homeAndReopenedSessionUseSavedPosition() throws Exception {
    String unitId = "integration_test_second_phoneme";
    Integer unitIndex =
        jdbc.queryForObject(
            "select count(*) from learning_units where template_code = 'phoneme' and enabled = true",
            Integer.class);
    Integer sortOrder =
        jdbc.queryForObject(
            "select coalesce(max(sort_order), 0) + 1 from learning_units where template_code = 'phoneme'",
            Integer.class);
    jdbc.update(
        """
        insert into learning_units (id, template_code, content_type, title, content_json, sort_order, enabled)
        values (?, 'phoneme', 'PHONEME_DETAIL', 'second', '{}', ?, true)
        """,
        unitId,
        sortOrder);
    try (var client = HttpClient.newHttpClient()) {
      String token = login(client);
      JsonNode flow = call(client, token, "GET", "/learning-flow", null);
      String nodeId = flow.path("nodes").get(0).path("id").stringValue();
      String path = "/learning-stages/phoneme/session?flowNodeId=" + nodeId;
      JsonNode session = call(client, token, "GET", path, null);
      call(
          client,
          token,
          "PUT",
          "/learning-stages/sessions/" + session.path("sessionId").stringValue() + "/position",
          "{\"unitId\":\"" + unitId + "\",\"unitIndex\":" + unitIndex + "}");
      assertThat(
              call(client, token, "GET", "/home", null)
                  .path("todayTask")
                  .path("current")
                  .intValue())
          .isEqualTo(unitIndex + 1);
      assertThat(call(client, token, "GET", path, null).path("currentUnitIndex").intValue())
          .isEqualTo(unitIndex);
      assertThat(
              call(client, token, "GET", "/learning-flow", null)
                  .path("currentNodeIndex")
                  .intValue())
          .isZero();
    } finally {
      jdbc.update("delete from learning_units where id = ?", unitId);
    }
  }

  @Test
  void reopenedSessionProjectsCurrentPhonemeContentWithoutSnapshot() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      String token = login(client);
      JsonNode flow = call(client, token, "GET", "/learning-flow", null);
      String nodeId = flow.path("nodes").get(0).path("id").stringValue();
      String path = "/learning-stages/phoneme/session?flowNodeId=" + nodeId;
      JsonNode session = call(client, token, "GET", path, null);
      JsonNode phonemeUnit = firstPhonemeDetail(session);
      String phonemeId = phonemeUnit.path("content").path("phonemeId").asText();
      String marker = "updated phoneme detail " + UUID.randomUUID();
      String originalEntryConfig =
          jdbc.queryForObject(
              "select config_json from content_entries where kind='PHONEME' and source_id=? and scope_id=''",
              String.class,
              phonemeId);
      String originalDetail =
          jdbc.queryForObject(
              "select detail_json from phonemes where id=?", String.class, phonemeId);

      try {
        var updated =
            (ObjectNode)
                mapper.readTree(originalEntryConfig == null ? originalDetail : originalEntryConfig);
        updated.put("description", marker);
        var question = updated.withArray("questions").addObject();
        question
            .put("id", "snapshot_regression")
            .put("type", "CHOICE")
            .put("prompt", "hidden answer regression")
            .put("correctOptionId", "a")
            .put("explanation", "this should not leak");
        question.putArray("options").addObject().put("id", "a").put("label", "A");
        question.withArray("options").addObject().put("id", "b").put("label", "B");
        jdbc.update(
            "update content_entries set config_json=? where kind='PHONEME' and source_id=? and scope_id=''",
            updated.toString(),
            phonemeId);

        JsonNode reopened = call(client, token, "GET", path, null);
        JsonNode currentPhoneme = firstPhonemeDetail(reopened).path("content");
        assertThat(currentPhoneme.path("description").asText()).isEqualTo(marker);
        assertThat(currentPhoneme.path("teachingConfig").path("description").asText())
            .isEqualTo(marker);
        assertThat(currentPhoneme.toString()).doesNotContain("correctOptionId");
        assertThat(currentPhoneme.toString()).doesNotContain("this should not leak");
      } finally {
        jdbc.update(
            "update content_entries set config_json=? where kind='PHONEME' and source_id=? and scope_id=''",
            originalEntryConfig,
            phonemeId);
        jdbc.update("update phonemes set detail_json=? where id=?", originalDetail, phonemeId);
      }
    }
  }

  private String login(HttpClient client) throws Exception {
    return call(
            client,
            "",
            "POST",
            "/auth/wechat/login",
            "{\"code\":\"integration-test-" + UUID.randomUUID() + "\"}")
        .path("accessToken")
        .stringValue();
  }

  private void complete(HttpClient client, String token, String nodeId) throws Exception {
    var request =
        HttpRequest.newBuilder(uri("/learning-flow/current/complete"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", "integration-" + nodeId)
            .POST(HttpRequest.BodyPublishers.ofString("{\"flowNodeId\":\"" + nodeId + "\"}"))
            .build();
    assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(200);
  }

  private JsonNode call(HttpClient client, String token, String method, String path, String body)
      throws Exception {
    var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
    if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
    var response =
        client.send(
            request
                .method(
                    method,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return mapper.readTree(response.body()).path("data");
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + "/api/v1" + path);
  }

  private JsonNode firstPhonemeDetail(JsonNode session) {
    for (JsonNode unit : session.path("units")) {
      if (unit.path("contentType").asText().equals("PHONEME_DETAIL")) {
        return unit;
      }
    }
    throw new AssertionError("phoneme session should include a PHONEME_DETAIL unit");
  }
}
