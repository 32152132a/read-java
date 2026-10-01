package com.readenglish.content;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.readenglish.auth.JwtTokenService;
import com.readenglish.auth.UserProfile;
import com.readenglish.common.api.ApiException;
import jakarta.validation.Validator;
import java.net.URI;
import java.net.http.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:content-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
      "app.content.worker-initial-delay-ms=3600000",
      "app.content.admin-user-ids=content-admin"
    })
class ContentApiTests {
  @LocalServerPort int port;
  @Autowired ObjectMapper mapper;
  @Autowired ContentConfig configs;
  @Autowired ContentService contents;
  @Autowired ContentJobs jobs;
  @Autowired JdbcTemplate db;
  @Autowired JwtTokenService tokens;
  @Autowired Validator validator;
  @MockitoBean WordGenerator generator;
  String token, user;

  @BeforeEach
  void prepare() throws Exception {
    var login =
        call(
            "",
            "POST",
            "/auth/wechat/login",
            mapper.createObjectNode().put("code", UUID.randomUUID().toString()),
            200);
    token = login.path("accessToken").asText();
    user = login.path("user").path("id").asText();
    when(generator.available()).thenReturn(true);
    when(generator.generate(anyList()))
        .thenAnswer(
            invocation -> {
              List<String> words = invocation.getArgument(0);
              return words.stream()
                  .map(value -> WordGenerator.Result.success(value, word(value)))
                  .toList();
            });
  }

  ObjectNode word(String word) {
    var c = configs.emptyWord(word, "/test/", "测试", "");
    var q =
        c.withArray("questions")
            .addObject()
            .put("id", "q1")
            .put("type", "CHOICE")
            .put("prompt", "选择正确答案")
            .put("correctOptionId", "a")
            .put("explanation", "原版本解释");
    q.putArray("options").addObject().put("id", "a").put("label", "第一项");
    q.withArray("options").addObject().put("id", "b").put("label", "第二项");
    return c;
  }

  @Test
  void generationIsIdempotentAndContentChangesResetLibraryStudy() throws Exception {
    var input = mapper.createObjectNode().put("name", "测试词库");
    input.putArray("words").add("computer").add("teacher");
    var task = call(token, "POST", "/word-libraries/custom-jobs", input, 202);
    String job = task.path("id").asText();
    assertThat(task.path("total").asInt()).isEqualTo(2);
    assertThat(task.path("completed").asInt()).isZero();
    assertThat(call(token, "POST", "/word-libraries/custom-jobs", input, 202).path("id"))
        .isEqualTo(task.path("id"));
    var conflictingInput = input.deepCopy();
    conflictingInput.withArray("words").removeAll().add("other");
    call(token, "POST", "/word-libraries/custom-jobs", conflictingInput, 409);
    jobs.work();
    jobs.work();
    task = call(token, "GET", "/word-libraries/custom-jobs/" + job + "/items", null, 200);
    assertThat(task.path("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(task.path("completed").asInt()).isEqualTo(2);
    String entry = task.path("items").get(0).path("entryId").asText();
    assertThat(entry).isNotBlank();
    String library = task.path("libraryId").asText();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM word_library_items WHERE library_id=?",
                Integer.class,
                library))
        .isEqualTo(2);
    var detail = call(token, "GET", "/content/" + entry, null, 200);
    assertThat(detail.has("publishedVersion")).isFalse();
    call(
        token,
        "PUT",
        "/content/" + entry,
        mapper.createObjectNode().put("version", 0).set("config", detail.path("config")),
        409);
    var add = mapper.createObjectNode();
    add.putArray("libraryIds").add(library);
    call(token, "POST", "/word-libraries/mine", add, 200);
    var study = call(token, "POST", "/word-libraries/" + library + "/study", null, 200);
    assertThat(study.toString()).doesNotContain("correctOptionId", "explanation");
    String oldSession = study.path("sessionId").asText();
    String wordId = study.path("items").get(0).path("wordId").asText();
    String oldBase = "/content-study/" + oldSession;
    var oldAnswer =
        call(
            token,
            "POST",
            oldBase + "/answers",
            mapper
                .createObjectNode()
                .put("questionId", wordId + ":q1")
                .put("selectedOptionId", "a"),
            200);
    assertThat(oldAnswer.path("correct").asBoolean()).isTrue();
    call(token, "POST", oldBase + "/words/" + wordId + "/complete", null, 200);
    var changed = (ObjectNode) detail.path("config").deepCopy();
    ((ObjectNode) changed.path("questions").get(0))
        .put("correctOptionId", "b")
        .put("explanation", "新版解释");
    call(
        token,
        "PUT",
        "/content/" + entry,
        mapper.createObjectNode().put("version", 1).set("config", changed),
        200);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM user_word_progress WHERE user_id=? AND library_id=?",
                Integer.class,
                user,
                library))
        .isZero();
    var restarted = call(token, "POST", "/word-libraries/" + library + "/study", null, 200);
    String currentSession = restarted.path("sessionId").asText();
    assertThat(currentSession).isEqualTo(library);
    String base = "/content-study/" + currentSession;
    var answer =
        call(
            token,
            "POST",
            base + "/answers",
            mapper
                .createObjectNode()
                .put("questionId", wordId + ":q1")
                .put("selectedOptionId", "b"),
            200);
    assertThat(answer.path("correct").asBoolean()).isTrue();
    assertThat(answer.path("explanation").asText()).isEqualTo("新版解释");
    call(token, "POST", base + "/words/" + wordId + "/complete", null, 200);
    call(token, "POST", base + "/words/" + wordId + "/complete", null, 200);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM user_word_progress WHERE user_id=? AND library_id=?",
                Integer.class,
                user,
                library))
        .isEqualTo(1);
    verify(generator, times(1)).generate(List.of("computer", "teacher"));
  }

  @Test
  void customJobCountsDuplicateArrayItemsTowardLimit() {
    assertThat(validator.validate(new ContentJobs.Create("边界词库", Collections.nCopies(200, "word"))))
        .isEmpty();
    assertThat(validator.validate(new ContentJobs.Create("边界词库", Collections.nCopies(201, "word"))))
        .isNotEmpty();
  }

  @Test
  void generationUsesAtMostFiveNewWordsPerProviderRequest() {
    var firstBatch = List.of("alpha", "bravo", "charlie", "delta", "echo");
    var allWords = new java.util.ArrayList<>(firstBatch);
    allWords.add("foxtrot");
    var task =
        jobs.create(user, UUID.randomUUID().toString(), new ContentJobs.Create("批次词库", allWords));

    jobs.work();
    assertThat(jobs.get(user, (String) task.get("id")).get("completed")).isEqualTo(5);
    jobs.work();
    assertThat(jobs.get(user, (String) task.get("id")).get("completed")).isEqualTo(6);

    verify(generator).generate(firstBatch);
    verify(generator).generate(List.of("foxtrot"));
  }

  @Test
  void permissionsSeparateOwnersAndPublicEditors() throws Exception {
    String id = contents.generated(user, word("ownership"));
    var other =
        call(
                "",
                "POST",
                "/auth/wechat/login",
                mapper.createObjectNode().put("code", UUID.randomUUID().toString()),
                200)
            .path("accessToken")
            .asText();
    call(other, "GET", "/content/" + id, null, 403);
    call(
        other,
        "PUT",
        "/content/" + id,
        mapper.createObjectNode().put("version", 1).set("config", word("ownership")),
        403);
    String publicId =
        db.queryForObject(
            "SELECT id FROM content_entries WHERE kind='PHONEME' ORDER BY id LIMIT 1",
            String.class);
    call(token, "GET", "/content/" + publicId, null, 403);
    String admin =
        tokens
            .issueFor(new UserProfile("content-admin", "管理员", null, "US"), "test", false)
            .accessToken();
    var detail = call(admin, "GET", "/content/" + publicId, null, 200);
    call(
        admin,
        "PUT",
        "/content/" + publicId,
        mapper
            .createObjectNode()
            .put("version", detail.path("version").asInt())
            .set("config", detail.path("config")),
        200);
  }

  @Test
  void failureCanRetryWithoutReplacingHumanContent() {
    when(generator.generate(List.of("failure")))
        .thenThrow(new IllegalStateException("provider unavailable"));
    var task =
        jobs.create(
            user, UUID.randomUUID().toString(), new ContentJobs.Create("重试词库", List.of("failure")));
    String id = (String) task.get("id");
    jobs.work();
    assertThat(jobs.get(user, id).get("status")).isEqualTo("PARTIAL_FAILED");
    String entry = contents.generated(user, word("failure"));
    jobs.retry(user, id);
    jobs.work();
    assertThat(jobs.get(user, id).get("status")).isEqualTo("SUCCEEDED");
    assertThat(contents.get(entry).version()).isEqualTo(1);
    verify(generator, times(1)).generate(List.of("failure"));
  }

  @Test
  void malformedConfigIsRejectedAndAnswerFieldsAreStrippedRecursively() {
    var c = word("validation");
    c.withArray("ipaSegments")
        .removeAll()
        .addObject()
        .put("text", "wrong")
        .put("tone", "normal")
        .put("bold", false);
    assertThatThrownBy(() -> configs.validate(c, "WORD")).isInstanceOf(ApiException.class);
    var invalidAnswer = word("validation");
    ((ObjectNode) invalidAnswer.path("questions").get(0)).put("correctOptionId", "unknown");
    assertThatThrownBy(() -> configs.validate(invalidAnswer, "WORD"))
        .isInstanceOf(ApiException.class);
    assertThat(configs.publicView(word("validation")).toString())
        .doesNotContain("correctOptionId", "explanation");
  }

  @Test
  void oversizedOrNonStringAudioIsRejectedBeforeSaving() throws Exception {
    String entry = contents.generated(user, word("recording"));
    var config = word("recording");
    config.withObject("audio").put("url", "https://example.com/" + "a".repeat(500));
    call(
        token,
        "PUT",
        "/content/" + entry,
        mapper.createObjectNode().put("version", 1).set("config", config),
        400);
    config.withObject("audio").putObject("url");
    call(
        token,
        "PUT",
        "/content/" + entry,
        mapper.createObjectNode().put("version", 1).set("config", config),
        400);
    assertThat(contents.get(entry).version()).isEqualTo(1);
  }

  @Test
  void importingDifferentCaseReusesReviewedContentAndLibraryLink() {
    String entry = contents.generated(user, word("capitalized"));
    var edited = word("Capitalized");
    edited.put("meaning", "人工校正释义");
    contents.save(user, entry, new ContentService.SaveRequest(1, edited));
    var job =
        jobs.create(
            user,
            UUID.randomUUID().toString(),
            new ContentJobs.Create("复用词库", List.of("capitalized")));
    jobs.work();
    assertThat(jobs.get(user, (String) job.get("id")).get("status")).isEqualTo("SUCCEEDED");
    assertThat(
            db.queryForObject(
                "SELECT entry_id FROM content_job_items WHERE job_id=?",
                String.class,
                job.get("id")))
        .isEqualTo(entry);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM word_library_items WHERE library_id=?",
                Integer.class,
                job.get("libraryId")))
        .isEqualTo(1);
    assertThat(contents.get(entry).version()).isEqualTo(2);
    verify(generator, never()).generate(anyList());
  }

  @Test
  void phonemeSavingUpdatesNewLessonsButPreservesOpenedSession() throws Exception {
    String admin =
        tokens
            .issueFor(new UserProfile("content-admin", "管理员", null, "US"), "test", false)
            .accessToken();
    var flow = call(token, "GET", "/learning-flow", null, 200);
    String sessionPath =
        "/learning-stages/phoneme/session?flowNodeId="
            + flow.path("nodes").get(0).path("id").asText();
    var original = call(token, "GET", sessionPath, null, 200);
    String entry = "content_p_1";
    var detail = call(admin, "GET", "/content/" + entry, null, 200);
    var config = (ObjectNode) detail.path("config").deepCopy();
    config.put("ipa", "/ɪˑ/");
    ((ObjectNode) config.path("ipaSegments").get(0)).put("text", "/ɪˑ/").put("bold", true);
    call(
        admin,
        "PUT",
        "/content/" + entry,
        mapper
            .createObjectNode()
            .put("version", detail.path("version").asInt())
            .set("config", config),
        200);
    assertThat(call(token, "GET", sessionPath, null, 200).path("units"))
        .isEqualTo(original.path("units"));
    var other =
        call(
                "",
                "POST",
                "/auth/wechat/login",
                mapper.createObjectNode().put("code", UUID.randomUUID().toString()),
                200)
            .path("accessToken")
            .asText();
    var newFlow = call(other, "GET", "/learning-flow", null, 200);
    var newLesson =
        call(
            other,
            "GET",
            "/learning-stages/phoneme/session?flowNodeId="
                + newFlow.path("nodes").get(0).path("id").asText(),
            null,
            200);
    assertThat(newLesson.path("units").get(0).path("content").path("ipa").asText())
        .isEqualTo("/ɪˑ/");
    assertThat(
            newLesson
                .path("units")
                .get(0)
                .path("content")
                .path("teachingConfig")
                .path("ipaSegments")
                .get(0)
                .path("bold")
                .asBoolean())
        .isTrue();
  }

  JsonNode call(String token, String method, String path, JsonNode body, int expected)
      throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
              .header("Content-Type", "application/json")
              .header("Idempotency-Key", "test-" + user);
      if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
      var response =
          client.send(
              request
                  .method(
                      method,
                      body == null
                          ? HttpRequest.BodyPublishers.noBody()
                          : HttpRequest.BodyPublishers.ofString(body.toString()))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
      return mapper.readTree(response.body()).path("data");
    }
  }
}
