package com.readenglish.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductApiTests {

  @LocalServerPort private int port;

  @Autowired private ObjectMapper objectMapper;

  @Test
  @Order(1)
  void loginInitializesHomeDefaultFlowAndBaseLibrary() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      String token = login(client);

      JsonNode home = getData(client, token, "/api/v1/home");
      JsonNode flow = getData(client, token, "/api/v1/learning-flow");
      JsonNode libraries = getData(client, token, "/api/v1/word-libraries/mine");
      JsonNode phonemes = getData(client, token, "/api/v1/phonemes?group=VOWEL");

      assertThat(home.path("todayTask").path("templateCode").stringValue()).isEqualTo("phoneme");
      assertThat(flow.path("version").longValue()).isPositive();
      assertThat(flow.path("nodes").size()).isEqualTo(7);
      assertThat(libraries.path("items").get(0).path("id").stringValue()).isEqualTo("lib_base");
      assertThat(libraries.path("items").get(0).path("removable").booleanValue()).isFalse();
      assertThat(phonemes.path("groups").get(0).path("items").size()).isPositive();
    }
  }

  @Test
  @Order(2)
  void repeatedFlowNodesKeepIndependentIdsAndCompletionIsIdempotent() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      String token = login(client);
      JsonNode current = getData(client, token, "/api/v1/learning-flow");
      long version = current.path("version").longValue();
      String requestBody =
          """
          {
            "version": %d,
            "nodes": [
              {"clientNodeId":"first","templateCode":"phoneme"},
              {"clientNodeId":"second","templateCode":"phoneme"}
            ]
          }
          """
              .formatted(version);
      JsonNode saved =
          sendData(
              client,
              authorized(token, "/api/v1/learning-flow")
                  .header("Content-Type", "application/json")
                  .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                  .build(),
              200);

      String firstNodeId = saved.path("nodes").get(0).path("id").stringValue();
      String secondNodeId = saved.path("nodes").get(1).path("id").stringValue();
      assertThat(firstNodeId).isNotEqualTo(secondNodeId);

      String completeBody = "{\"flowNodeId\":\"" + firstNodeId + "\"}";
      HttpRequest firstRequest =
          authorized(token, "/api/v1/learning-flow/current/complete")
              .header("Content-Type", "application/json")
              .header("Idempotency-Key", "product-test-complete")
              .POST(HttpRequest.BodyPublishers.ofString(completeBody))
              .build();
      JsonNode firstResponse = sendData(client, firstRequest, 200);
      JsonNode repeatedResponse = sendData(client, firstRequest, 200);
      JsonNode updated = getData(client, token, "/api/v1/learning-flow");

      assertThat(firstResponse).isEqualTo(repeatedResponse);
      assertThat(firstResponse.path("nextNode").path("id").stringValue()).isEqualTo(secondNodeId);
      assertThat(updated.path("currentNodeIndex").intValue()).isEqualTo(1);
    }
  }

  @Test
  @Order(3)
  void quizHidesAnswerUntilSubmissionAndLibrariesAreProtected() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      String token = login(client);
      JsonNode current = getData(client, token, "/api/v1/learning-flow");
      long version = current.path("version").longValue();
      JsonNode saved =
          sendData(
              client,
              authorized(token, "/api/v1/learning-flow")
                  .header("Content-Type", "application/json")
                  .PUT(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"version\":"
                              + version
                              + ",\"nodes\":[{\"templateCode\":\"phoneme-quiz\"}]}"))
                  .build(),
              200);
      String flowNodeId = saved.path("nodes").get(0).path("id").stringValue();
      JsonNode session =
          getData(
              client,
              token,
              "/api/v1/learning-stages/phoneme-quiz/session?flowNodeId=" + flowNodeId);
      JsonNode quizContent = session.path("units").get(0).path("content");

      assertThat(quizContent.toString()).doesNotContain("correctOptionId", "correctAnswer");

      String sessionId = session.path("sessionId").stringValue();
      JsonNode answer =
          sendData(
              client,
              authorized(token, "/api/v1/quiz-questions/q_phoneme_1/answers")
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"selectedOptionId\":\"B\",\"sessionId\":\"" + sessionId + "\"}"))
                  .build(),
              200);
      assertThat(answer.path("correct").booleanValue()).isFalse();
      assertThat(answer.path("correctOptionId").stringValue()).isEqualTo("A");

      JsonNode added =
          sendData(
              client,
              authorized(token, "/api/v1/word-libraries/mine")
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"libraryIds\":[\"lib_frontend\",\"lib_frontend\"]}"))
                  .build(),
              200);
      JsonNode repeatedAdd =
          sendData(
              client,
              authorized(token, "/api/v1/word-libraries/mine")
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("{\"libraryIds\":[\"lib_frontend\"]}"))
                  .build(),
              200);
      JsonNode words = getData(client, token, "/api/v1/word-libraries/lib_frontend/words?size=1");

      assertThat(added.path("addedLibraryIds").size()).isEqualTo(1);
      assertThat(repeatedAdd.path("addedLibraryIds").isEmpty()).isTrue();
      assertThat(words.path("items").size()).isEqualTo(1);

      HttpResponse<String> removeBase =
          client.send(
              authorized(token, "/api/v1/word-libraries/mine/lib_base").DELETE().build(),
              HttpResponse.BodyHandlers.ofString());
      JsonNode removeBaseBody = objectMapper.readTree(removeBase.body());
      assertThat(removeBase.statusCode()).isEqualTo(400);
      assertThat(removeBaseBody.path("code").stringValue()).isEqualTo("LIBRARY_BASE_NOT_REMOVABLE");
    }
  }

  private String login(HttpClient client) throws Exception {
    JsonNode data =
        sendData(
            client,
            HttpRequest.newBuilder()
                .uri(uri("/api/v1/auth/dev/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"nickname\":\"永庆\"}"))
                .build(),
            200);
    return data.path("accessToken").stringValue();
  }

  private JsonNode getData(HttpClient client, String token, String path) throws Exception {
    return sendData(client, authorized(token, path).GET().build(), 200);
  }

  private JsonNode sendData(HttpClient client, HttpRequest request, int expectedStatus)
      throws Exception {
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
    return objectMapper.readTree(response.body()).path("data");
  }

  private HttpRequest.Builder authorized(String token, String path) {
    return HttpRequest.newBuilder().uri(uri(path)).header("Authorization", "Bearer " + token);
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
