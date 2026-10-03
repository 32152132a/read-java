package com.readenglish.evaluation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.readenglish.auth.JwtTokenService;
import com.readenglish.auth.UserProfile;
import com.readenglish.common.api.ApiException;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:evaluation-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
      "app.content.worker-initial-delay-ms=3600000"
    })
class EvaluationApiTests {
  @LocalServerPort int port;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate db;
  @Autowired JwtTokenService tokens;
  @Autowired EvaluationService service;
  @MockitoBean OralEvaluationGateway gateway;
  String user, token, wordId;

  @BeforeEach
  void setup() {
    user = "eval-test-" + UUID.randomUUID();
    db.update("INSERT INTO users(id,nickname,accent) VALUES(?,?,'US')", user, "Evaluation test");
    token =
        tokens
            .issueFor(new UserProfile(user, "Evaluation test", null, "US"), "", false)
            .accessToken();
    wordId = db.queryForList("SELECT id FROM words ORDER BY id", String.class).getFirst();
    when(gateway.available()).thenReturn(true);
    when(gateway.evaluate(anyString(), any()))
        .thenReturn(new EvaluationModels.Score(85.0, 86, List.of(), "test-provider"));
  }

  EvaluationModels.Create input() {
    return new EvaluationModels.Create(
        wordId, null, EvaluationAudioTests.encoded(EvaluationAudioTests.wav(1000, 3000)));
  }

  @Test
  void returnsScoresWithoutAudioAndReplaysWithoutAnotherProviderCall() throws Exception {
    var first = call("POST", "/evaluations", "same-key", input(), 200).path("data");
    assertThat(first.path("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(first.path("result").path("accent").asText()).isEqualTo("US");
    assertThat(first.path("result").path("adviceSource").asText()).isEqualTo("RULE");
    assertThat(first.toString()).doesNotContain("audioBase64", "audioUrl", "fluency", "integrity");
    var second = call("POST", "/evaluations", "same-key", input(), 200).path("data");
    assertThat(second).isEqualTo(first);
    var loaded =
        call("GET", "/evaluations/" + first.path("evaluationId").asText(), null, null, 200)
            .path("data");
    assertThat(loaded).isEqualTo(first);
    verify(gateway, times(1)).evaluate(anyString(), any());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM word_evaluations WHERE user_id=?", Integer.class, user))
        .isOne();
  }

  @Test
  void rejectsChangedInputAndOtherUsersResults() throws Exception {
    var first = call("POST", "/evaluations", "key", input(), 200).path("data");
    var changed =
        new EvaluationModels.Create(
            wordId, null, EvaluationAudioTests.encoded(EvaluationAudioTests.wav(800, 2000)));
    assertThat(call("POST", "/evaluations", "key", changed, 409).path("code").asText())
        .isEqualTo("IDEMPOTENCY_CONFLICT");
    assertThatThrownBy(() -> service.get("another-user", first.path("evaluationId").asText()))
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(gateway, times(1)).evaluate(anyString(), any());
  }

  @Test
  void validatesAuthenticationAudioSessionAndConfigurationBeforeCharging() throws Exception {
    String savedToken = token;
    token = "";
    call("POST", "/evaluations", "key", input(), 401);
    token = savedToken;
    call("POST", "/evaluations", null, input(), 400);
    call("POST", "/evaluations", "key", new EvaluationModels.Create(wordId, null, "invalid"), 400);
    call(
        "POST",
        "/evaluations",
        "key",
        new EvaluationModels.Create(wordId, "someone-elses-session", input().audioBase64()),
        404);
    when(gateway.available()).thenReturn(false);
    assertThat(call("POST", "/evaluations", "key", input(), 503).path("code").asText())
        .isEqualTo("EVALUATION_NOT_CONFIGURED");
    verify(gateway, never()).evaluate(anyString(), any());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM word_evaluations WHERE user_id=?", Integer.class, user))
        .isZero();
  }

  @Test
  void providerFailureIsStoredAndNeverAutomaticallyRetried() throws Exception {
    when(gateway.evaluate(anyString(), any()))
        .thenThrow(
            new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "EVALUATION_TIMEOUT",
                "private upstream information"));
    var response = call("POST", "/evaluations", "key", input(), 200).path("data");
    assertThat(response.path("status").asText()).isEqualTo("FAILED");
    assertThat(response.path("errorCode").asText()).isEqualTo("EVALUATION_TIMEOUT");
    assertThat(response.toString()).doesNotContain("private upstream");
    call("POST", "/evaluations", "key", input(), 200);
    verify(gateway, times(1)).evaluate(anyString(), any());
  }

  @Test
  void concurrentDuplicateOnlyChargesOnceAndAnotherRecordingIsBlocked() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    when(gateway.evaluate(anyString(), any()))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              if (!finish.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Test timed out");
              return new EvaluationModels.Score(80.0, 80, List.of(), "test");
            });
    var first = CompletableFuture.supplyAsync(() -> service.create(user, "key", input()));
    try {
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(service.create(user, "key", input()).status()).isEqualTo("PROCESSING");
      assertThatThrownBy(() -> service.create(user, "another-key", input()))
          .isInstanceOfSatisfying(
              ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("EVALUATION_BUSY"));
    } finally {
      finish.countDown();
    }
    assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo("SUCCEEDED");
    verify(gateway, times(1)).evaluate(anyString(), any());
  }

  @Test
  void interruptedRequestBecomesFailureAndKeepsIdempotencyTombstone() {
    var original = service.create(user, "key", input());
    db.update(
        "UPDATE word_evaluations SET status='PROCESSING',result_json=NULL,completed_at=NULL,created_at=? WHERE id=?",
        Timestamp.from(Instant.now().minusSeconds(120)),
        original.evaluationId());
    var replayed = service.create(user, "key", input());
    assertThat(replayed.status()).isEqualTo("FAILED");
    assertThat(replayed.errorCode()).isEqualTo("EVALUATION_INTERRUPTED");
    verify(gateway, times(1)).evaluate(anyString(), any());
  }

  JsonNode call(String method, String path, String key, Object body, int status) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
    if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);
    if (key != null) builder.header("Idempotency-Key", key);
    if (body != null) builder.header("Content-Type", "application/json");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
      return mapper.readTree(response.body());
    }
  }
}
