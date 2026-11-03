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
    var libraryWords =
        call(token, "GET", "/word-libraries/" + library + "/words?size=20", null, 200);
    assertThat(libraryWords.path("items").get(0).path("contentId").asText()).isEqualTo(entry);
    assertThat(libraryWords.path("items").get(0).path("editable").asBoolean()).isTrue();
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
  void customJobExplainsUnsupportedWordCharacters() throws Exception {
    var input = mapper.createObjectNode().put("name", "存储");
    input.putArray("words").add("啊啊").add("config");

    var response = callResponse(token, "POST", "/word-libraries/custom-jobs", input, 400);

    assertThat(response.path("message").asText()).isEqualTo("单词格式无效：啊啊");
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
  void phonemeSavingUpdatesReopenedSessionProjection() throws Exception {
    String admin =
        tokens
            .issueFor(new UserProfile("content-admin", "管理员", null, "US"), "test", false)
            .accessToken();
    var flow = call(token, "GET", "/learning-flow", null, 200);
    String sessionPath =
        "/learning-stages/phoneme/session?flowNodeId="
            + flow.path("nodes").get(0).path("id").asText();
    call(token, "GET", sessionPath, null, 200);
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
    var reopened = call(token, "GET", sessionPath, null, 200);
    assertThat(reopened.path("units").get(0).path("content").path("ipa").asText())
        .isEqualTo("/ɪˑ/");
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

  @Test
  void changingOneWordAudioUpdatesExamplesLessonsAndPersonalLibraries() throws Exception {
    String wordId =
        db.queryForObject(
            "SELECT w.id FROM words w JOIN word_pronunciations p ON p.word_id=w.id AND p.accent='GB' WHERE w.normalized_word='green'",
            String.class);
    String original =
        db.queryForObject(
            "SELECT audio_url FROM word_pronunciations WHERE word_id=? AND accent='GB'",
            String.class,
            wordId);
    try {
      var task =
          jobs.create(
              user,
              UUID.randomUUID().toString(),
              new ContentJobs.Create("统一资源验证", List.of("green")));
      jobs.work();
      String library = (String) task.get("libraryId");
      String audio = "https://example.test/green-gb.mp3";
      String admin =
          tokens
              .issueFor(new UserProfile("content-admin", "管理员", null, "US"), "test", false)
              .accessToken();
      String publicEntry =
          db.queryForObject(
              "SELECT id FROM content_entries WHERE kind='WORD' AND source_id=? AND scope_id=''",
              String.class,
              wordId);
      var editable = call(admin, "GET", "/content/" + publicEntry, null, 200);
      var edited = (ObjectNode) editable.path("config").deepCopy();
      edited.withObject("pronunciations").withObject("GB").put("audioUrl", audio);
      call(
          admin,
          "PUT",
          "/content/" + publicEntry,
          mapper
              .createObjectNode()
              .put("version", editable.path("version").asInt())
              .set("config", edited),
          200);
      var detail = call(token, "GET", "/phonemes/p_2", null, 200);
      JsonNode example = null;
      for (JsonNode w : detail.path("exampleWords"))
        if (w.path("wordId").asText().equals(wordId)) example = w;
      assertThat(example).isNotNull();
      assertThat(example.path("audioUrl").asText()).isEqualTo(audio);
      var flow = call(token, "GET", "/learning-flow", null, 200);
      var session =
          call(
              token,
              "GET",
              "/learning-stages/phoneme/session?flowNodeId="
                  + flow.path("nodes").get(0).path("id").asText(),
              null,
              200);
      assertThat(session.path("units").toString()).contains(audio);
      var list = call(token, "GET", "/word-libraries/" + library + "/words", null, 200);
      assertThat(list.path("items").get(0).path("audioUrl").asText()).isEqualTo(audio);
      var add = mapper.createObjectNode();
      add.putArray("libraryIds").add(library);
      call(token, "POST", "/word-libraries/mine", add, 200);
      var study = call(token, "POST", "/word-libraries/" + library + "/study", null, 200);
      assertThat(study.path("items").get(0).path("audio").path("url").asText()).isEqualTo(audio);
      String entry = list.path("items").get(0).path("contentId").asText();
      String stored =
          db.queryForObject(
              "SELECT config_json FROM content_entries WHERE id=?", String.class, entry);
      assertThat(mapper.readTree(stored).has("audio")).isFalse();
      assertThat(mapper.readTree(stored).has("pronunciations")).isFalse();
    } finally {
      db.update(
          "UPDATE word_pronunciations SET audio_url=? WHERE word_id=? AND accent='GB'",
          original,
          wordId);
    }
  }

  @Test
  void audioAndIpaAlwaysSelectTheSameAccentAndPersonalContentCannotReplaceThem() {
    var c = word("accentcheck");
    c.withObject("pronunciations")
        .putObject("GB")
        .put("ipa", "/ɡb/")
        .put("audioUrl", "https://example.test/gb.mp3");
    c.withObject("pronunciations").withObject("US").put("audioUrl", "https://example.test/us.mp3");
    String entry = contents.generated(user, c);
    String wordId = contents.get(entry).sourceId();
    var us = contents.publicWord(user, wordId);
    assertThat(us.path("ipa").asText()).isEqualTo("/test/");
    assertThat(us.path("audio").path("url").asText()).endsWith("us.mp3");
    db.update("UPDATE users SET accent='GB' WHERE id=?", user);
    var gb = contents.publicWord(user, wordId);
    assertThat(gb.path("ipa").asText()).isEqualTo("/ɡb/");
    assertThat(gb.path("audio").path("url").asText()).endsWith("gb.mp3");
    var edited = (ObjectNode) contents.detail(user, entry).config().deepCopy();
    edited
        .withObject("pronunciations")
        .withObject("US")
        .put("audioUrl", "https://example.test/other.mp3");
    assertThatThrownBy(() -> contents.save(user, entry, new ContentService.SaveRequest(1, edited)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("公共发音资源");
    assertThat(contents.get(entry).version()).isEqualTo(1);
    var onlyUs = word("onlyus");
    String only = contents.generated(user, onlyUs);
    assertThat(contents.publicWord(user, contents.get(only).sourceId()).path("accent").asText())
        .isEqualTo("US");
  }

  @Test
  void flagsReopenListeningOnlyWhenEveryTargetHasAudio() throws Exception {
    var existing = db.queryForList("SELECT * FROM phoneme_pronunciations");
    try {
      db.update("UPDATE learning_features SET enabled=TRUE");
      assertThat(
              call(token, "GET", "/content/capabilities", null, 200)
                  .path("phonemeAudioEnabled")
                  .asBoolean())
          .isTrue();
      assertThat(
              call(token, "GET", "/content/capabilities", null, 200)
                  .path("phonemeListeningEnabled")
                  .asBoolean())
          .isFalse();
      for (String id : db.queryForList("SELECT id FROM phonemes", String.class)) {
        db.update("DELETE FROM phoneme_pronunciations WHERE phoneme_id=?", id);
        db.update(
            "INSERT INTO phoneme_pronunciations VALUES(?,'GB',?)",
            id,
            "https://example.test/phonemes/" + id + ".mp3");
      }
      assertThat(
              call(token, "GET", "/content/capabilities", null, 200)
                  .path("phonemeListeningEnabled")
                  .asBoolean())
          .isTrue();
      assertThat(call(token, "GET", "/learning-flow/templates", null, 200).toString())
          .contains("phoneme-quiz");
      var flow = call(token, "GET", "/learning-flow", null, 200);
      var input = mapper.createObjectNode().put("version", flow.path("version").asInt());
      input.putArray("nodes").addObject().put("templateCode", "phoneme-quiz");
      var saved = call(token, "PUT", "/learning-flow", input, 200);
      var session =
          call(
              token,
              "GET",
              "/learning-stages/phoneme-quiz/session?flowNodeId="
                  + saved.path("nodes").get(0).path("id").asText(),
              null,
              200);
      assertThat(session.path("total").asInt()).isEqualTo(96);
      for (JsonNode unit : session.path("units")) {
        JsonNode content = unit.path("content");
        assertThat(content.path("audioUrls").size()).isEqualTo(1);
        assertThat(content.path("audioUrls").get(0).asText())
            .endsWith(content.path("phonemeId").asText() + ".mp3");
      }
      db.update("UPDATE learning_features SET enabled=FALSE WHERE code='phoneme_listening'");
      assertThat(call(token, "GET", "/learning-flow/templates", null, 200).toString())
          .doesNotContain("phoneme-quiz");
      assertThat(call(token, "GET", "/learning-flow", null, 200).path("nodes").isEmpty()).isTrue();
    } finally {
      db.update("UPDATE learning_features SET enabled=FALSE");
      db.update("DELETE FROM phoneme_pronunciations");
      for (var row : existing)
        db.update(
            "INSERT INTO phoneme_pronunciations VALUES(?,?,?)",
            row.get("phoneme_id"),
            row.get("accent"),
            row.get("audio_url"));
    }
  }

  @Test
  void invalidExampleReferenceRollsBackTheWholePublicSave() throws Exception {
    String admin =
        tokens
            .issueFor(new UserProfile("content-admin", "管理员", null, "US"), "test", false)
            .accessToken();
    var before = call(admin, "GET", "/content/content_p_2", null, 200);
    var edited = (ObjectNode) before.path("config").deepCopy();
    edited.put("description", "不应保存的说明");
    ((ObjectNode) edited.path("exampleWords").get(0)).put("wordId", "missing-word");
    call(
        admin,
        "PUT",
        "/content/content_p_2",
        mapper
            .createObjectNode()
            .put("version", before.path("version").asInt())
            .set("config", edited),
        400);
    var after = call(admin, "GET", "/content/content_p_2", null, 200);
    assertThat(after).isEqualTo(before);
  }

  JsonNode call(String token, String method, String path, JsonNode body, int expected)
      throws Exception {
    return callResponse(token, method, path, body, expected).path("data");
  }

  JsonNode callResponse(String token, String method, String path, JsonNode body, int expected)
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
      return mapper.readTree(response.body());
    }
  }
}
