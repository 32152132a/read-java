package com.readenglish.content;

import com.readenglish.common.api.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@EnableScheduling
public class ContentJobs {
  private final JdbcTemplate db;
  private final ContentService contents;
  private final WordGenerator generator;
  private final TransactionTemplate transaction;

  public ContentJobs(
      JdbcTemplate db,
      ContentService contents,
      WordGenerator generator,
      PlatformTransactionManager manager) {
    this.db = db;
    this.contents = contents;
    this.generator = generator;
    transaction = new TransactionTemplate(manager);
  }

  public record Create(
      @NotBlank @Size(min = 2, max = 40) String name,
      @NotBlank @Size(max = 30000) String wordsText) {}

  @Transactional
  public Map<String, Object> create(String user, String key, Create input) {
    ContentConfig.require(
        key != null && !key.isBlank() && key.length() <= 120, "需要 Idempotency-Key");
    List<String> words =
        Arrays.stream(input.wordsText().split("[\\s,，;；]+"))
            .map(s -> s.trim().toLowerCase(Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .distinct()
            .toList();
    ContentConfig.require(!words.isEmpty() && words.size() <= 200, "每次支持 1–200 个不同单词");
    for (String word : words)
      ContentConfig.require(
          word.length() <= 120 && word.matches("[a-z]+(?:['’-][a-z]+)*"),
          "单词格式无效：" + word.substring(0, Math.min(word.length(), 30)));
    String hash;
    try {
      hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          (input.name().trim() + "\n" + String.join("\n", words))
                              .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    // 对同一用户串行创建，防止并发重复任务和超出配额。
    db.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", String.class, user);
    List<Map<String, Object>> old =
        db.queryForList(
            "SELECT id,input_hash FROM content_jobs WHERE user_id=? AND request_key=?", user, key);
    if (!old.isEmpty()) {
      if (!old.getFirst().get("input_hash").equals(hash))
        throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "相同请求编号不能用于不同输入");
      return get(user, (String) old.getFirst().get("id"));
    }
    if (!generator.available())
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "尚未配置 DeepSeek 服务，请联系管理员");
    ContentConfig.require(
        db.queryForObject(
                "SELECT COUNT(*) FROM content_jobs WHERE user_id=? AND status IN ('PENDING','PROCESSING')",
                Integer.class,
                user)
            < 2,
        "请等待已有任务完成");
    String id = ContentService.id("job"), library = ContentService.id("lib");
    db.update(
        "INSERT INTO word_libraries(id,owner_user_id,type,name,description,status) VALUES(?,?,'AI_CUSTOM',?,'AI 生成后经审核发布的个人词库','PENDING')",
        library,
        user,
        input.name().trim());
    db.update(
        "INSERT INTO content_jobs(id,user_id,request_key,input_hash,library_id,status) VALUES(?,?,?,?,?,'PENDING')",
        id,
        user,
        key,
        hash,
        library);
    for (int i = 0; i < words.size(); i++)
      db.update(
          "INSERT INTO content_job_items(job_id,position,word,status) VALUES(?,?,?,'PENDING')",
          id,
          i,
          words.get(i));
    return get(user, id);
  }

  public List<Map<String, Object>> list(String user) {
    return db.query(
        "SELECT j.id,j.status,j.library_id AS `libraryId`,l.name FROM content_jobs j JOIN word_libraries l ON l.id=j.library_id WHERE j.user_id=? ORDER BY j.created_at DESC LIMIT 30",
        (row, n) -> jobSummary(row),
        user);
  }

  private Map<String, Object> jobSummary(java.sql.ResultSet row) throws java.sql.SQLException {
    return Map.of(
        "id",
        row.getString("id"),
        "status",
        row.getString("status"),
        "libraryId",
        row.getString("libraryId"),
        "name",
        row.getString("name"));
  }

  public record JobItem(
      int position,
      String word,
      String status,
      String entryId,
      String errorMessage,
      int attempts) {}

  public Map<String, Object> get(String user, String id) {
    Map<String, Object> job =
        db
            .query(
                "SELECT j.id,j.status,j.library_id AS `libraryId`,l.name FROM content_jobs j JOIN word_libraries l ON l.id=j.library_id WHERE j.id=? AND j.user_id=?",
                (row, n) -> jobSummary(row),
                id,
                user)
            .stream()
            .findFirst()
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "任务不存在"));
    var result = new LinkedHashMap<String, Object>(job);
    result.put(
        "items",
        db.query(
            "SELECT position,word,status,entry_id AS `entryId`,error_message AS `errorMessage`,attempts FROM content_job_items WHERE job_id=? ORDER BY position",
            (row, n) ->
                new JobItem(
                    row.getInt("position"),
                    row.getString("word"),
                    row.getString("status"),
                    row.getString("entryId"),
                    row.getString("errorMessage"),
                    row.getInt("attempts")),
            id));
    return result;
  }

  @Transactional
  public Map<String, Object> retry(String user, String id) {
    get(user, id);
    db.update(
        "UPDATE content_job_items SET status='PENDING',error_message=NULL WHERE job_id=? AND status='FAILED' AND attempts<3",
        id);
    refresh(id);
    return get(user, id);
  }

  @Scheduled(
      fixedDelayString = "${app.content.worker-delay-ms:2000}",
      initialDelayString = "${app.content.worker-initial-delay-ms:2000}")
  public void work() {
    // 单实例轻量任务执行器；超时租约恢复中断任务，不重复覆盖人工内容。
    db.update(
        "UPDATE content_job_items SET status='FAILED',error_message='任务执行中断，请重试' WHERE status='PROCESSING' AND started_at<?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(180)));
    for (String id :
        db.queryForList(
            "SELECT id FROM content_jobs WHERE status IN ('PENDING','PROCESSING')", String.class))
      refresh(id);
    var pending =
        db.queryForList(
            "SELECT i.job_id,i.position,i.word,j.user_id FROM content_job_items i JOIN content_jobs j ON j.id=i.job_id WHERE i.status='PENDING' ORDER BY j.created_at,i.position LIMIT 1");
    if (pending.isEmpty()) return;
    var row = pending.getFirst();
    String job = (String) row.get("job_id"), user = (String) row.get("user_id");
    int position = ((Number) row.get("position")).intValue();
    if (db.update(
            "UPDATE content_job_items SET status='PROCESSING',attempts=attempts+1,started_at=CURRENT_TIMESTAMP WHERE job_id=? AND position=? AND status='PENDING'",
            job,
            position)
        != 1) return;
    db.update("UPDATE content_jobs SET status='PROCESSING' WHERE id=?", job);
    try {
      String word = (String) row.get("word");
      var existing =
          db.queryForList(
              "SELECT id FROM content_entries WHERE kind='WORD' AND scope_id=? AND LOWER(label)=?",
              String.class,
              user,
              word);
      var config = existing.isEmpty() ? generator.generate(word) : null;
      transaction.executeWithoutResult(
          status -> {
            String entry =
                existing.isEmpty() ? contents.generated(user, config) : existing.getFirst();
            db.update(
                "UPDATE content_job_items SET status='SUCCEEDED',entry_id=?,error_message=NULL WHERE job_id=? AND position=?",
                entry,
                job,
                position);
            contents.linkPublishedEntry(entry);
          });
    } catch (Exception ex) {
      db.update(
          "UPDATE content_job_items SET status='FAILED',error_message='生成失败或数据不完整，请重试' WHERE job_id=? AND position=?",
          job,
          position);
    }
    refresh(job);
  }

  private void refresh(String id) {
    int pending =
        db.queryForObject(
            "SELECT COUNT(*) FROM content_job_items WHERE job_id=? AND status IN ('PENDING','PROCESSING')",
            Integer.class,
            id);
    int failed =
        db.queryForObject(
            "SELECT COUNT(*) FROM content_job_items WHERE job_id=? AND status='FAILED'",
            Integer.class,
            id);
    db.update(
        "UPDATE content_jobs SET status=? WHERE id=?",
        pending > 0 ? "PROCESSING" : failed > 0 ? "PARTIAL_FAILED" : "SUCCEEDED",
        id);
  }
}
