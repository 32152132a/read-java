package com.readenglish.evaluation;

import com.readenglish.common.api.ApiException;
import com.readenglish.learningcontent.LearningStageService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class EvaluationService {
  private final JdbcTemplate db;
  private final ObjectMapper mapper;
  private final OralEvaluationGateway gateway;
  private final LearningStageService stages;
  private final TransactionTemplate transaction;

  public EvaluationService(
      JdbcTemplate db,
      ObjectMapper mapper,
      OralEvaluationGateway gateway,
      LearningStageService stages,
      PlatformTransactionManager manager) {
    this.db = db;
    this.mapper = mapper;
    this.gateway = gateway;
    this.stages = stages;
    transaction = new TransactionTemplate(manager);
  }

  public EvaluationModels.Response create(String user, String key, EvaluationModels.Create input) {
    require(key != null && key.matches("[A-Za-z0-9_-]{1,120}"), "需要有效的 Idempotency-Key");
    if (input.sessionId() != null && !input.sessionId().isBlank()) {
      require(
          "evaluation".equals(stages.getOwnedSessionTemplate(user, input.sessionId())),
          "学习会话不属于评测阶段");
    }
    byte[] wav = EvaluationAudio.decode(input.audioBase64());
    try {
      String hash = hash(input, wav);
      String id = "eval_" + UUID.randomUUID().toString().replace("-", "");
      String word = transaction.execute(status -> reserve(user, key, input.wordId(), hash, id));
      if (word == null) {
        String existing =
            db.queryForObject(
                "SELECT id FROM word_evaluations WHERE user_id=? AND request_key=?",
                String.class,
                user,
                key);
        return get(user, existing);
      }
      try {
        var score = gateway.evaluate(word, wav);
        var result =
            new EvaluationModels.Result(
                word,
                "US",
                score,
                score.accuracy() >= 80 ? "发音比较清楚，继续保持。" : "可以再听一次标准发音，放慢速度练习。",
                score.accuracy() >= 80
                    ? List.of("注意保持自然的单词重音。")
                    : List.of("先听标准发音，再分音节跟读。", "连起来读一遍，注意每个音都发清楚。"),
                "RULE");
        db.update(
            "UPDATE word_evaluations SET status='SUCCEEDED',result_json=?,completed_at=? WHERE id=? AND status='PROCESSING'",
            mapper.writeValueAsString(result),
            Timestamp.from(Instant.now()),
            id);
      } catch (ApiException ex) {
        fail(id, ex.getCode());
      } catch (RuntimeException ex) {
        // Provider exceptions can contain signed URLs; never log the exception or recording.
        fail(id, "EVALUATION_FAILED");
      }
      return get(user, id);
    } finally {
      Arrays.fill(wav, (byte) 0);
    }
  }

  private String reserve(String user, String key, String wordId, String hash, String id) {
    if (db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", String.class, user).isEmpty())
      throw notFound();
    var existing =
        db.queryForList(
            "SELECT input_hash FROM word_evaluations WHERE user_id=? AND request_key=?",
            String.class,
            user,
            key);
    if (!existing.isEmpty()) {
      if (!existing.getFirst().equals(hash))
        throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "相同请求编号不能用于不同录音");
      return null;
    }
    var words = db.queryForList("SELECT display_word FROM words WHERE id=?", String.class, wordId);
    if (words.isEmpty()) throw notFound();
    String word = words.getFirst();
    require(word.matches("[A-Za-z]+(?:['-][A-Za-z]+)*") && word.length() <= 120, "第一版仅支持完整英语单词");
    if (!gateway.available())
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "EVALUATION_NOT_CONFIGURED", "发音评测服务尚未配置");
    expire(user);
    Integer active =
        db.queryForObject(
            "SELECT COUNT(*) FROM word_evaluations WHERE user_id=? AND status='PROCESSING'",
            Integer.class,
            user);
    Integer recent =
        db.queryForObject(
            "SELECT COUNT(*) FROM word_evaluations WHERE user_id=? AND created_at>?",
            Integer.class,
            user,
            Timestamp.from(Instant.now().minusSeconds(60)));
    if (active > 0 || recent >= 6)
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EVALUATION_BUSY", "评测太频繁，请稍后再试");
    db.update(
        "INSERT INTO word_evaluations(id,user_id,word_id,request_key,input_hash,status,created_at) VALUES(?,?,?,?,?,'PROCESSING',?)",
        id,
        user,
        wordId,
        key,
        hash,
        Timestamp.from(Instant.now()));
    return word;
  }

  public EvaluationModels.Response get(String user, String id) {
    expire(user);
    var rows =
        db.query(
            "SELECT id,status,result_json,error_code FROM word_evaluations WHERE user_id=? AND id=?",
            (rs, row) ->
                new EvaluationModels.Response(
                    rs.getString("id"),
                    rs.getString("status"),
                    rs.getString("result_json") == null
                        ? null
                        : mapper.readValue(
                            rs.getString("result_json"), EvaluationModels.Result.class),
                    rs.getString("error_code")),
            user,
            id);
    if (rows.isEmpty()) throw notFound();
    return rows.getFirst();
  }

  private void expire(String user) {
    // No audio is retained, so interrupted requests must finish as failures, never be replayed.
    db.update(
        "UPDATE word_evaluations SET status='FAILED',error_code='EVALUATION_INTERRUPTED',completed_at=? WHERE user_id=? AND status='PROCESSING' AND created_at<?",
        Timestamp.from(Instant.now()),
        user,
        Timestamp.from(Instant.now().minusSeconds(60)));
  }

  private void fail(String id, String code) {
    db.update(
        "UPDATE word_evaluations SET status='FAILED',error_code=?,completed_at=? WHERE id=? AND status='PROCESSING'",
        code,
        Timestamp.from(Instant.now()),
        id);
  }

  private static String hash(EvaluationModels.Create input, byte[] wav) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(
          (input.wordId() + "\n" + (input.sessionId() == null ? "" : input.sessionId()) + "\nUS\n")
              .getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest.digest(wav));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }

  private static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "评测或单词不存在");
  }
}
