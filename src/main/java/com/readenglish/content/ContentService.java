package com.readenglish.content;

import com.readenglish.common.api.ApiException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ContentService {
  private final JdbcTemplate db;
  private final ContentConfig configs;
  private final ObjectMapper mapper;
  private final Set<String> admins;

  public ContentService(
      JdbcTemplate db,
      ContentConfig configs,
      ObjectMapper mapper,
      @Value("${app.content.admin-user-ids:}") String admins) {
    this.db = db;
    this.configs = configs;
    this.mapper = mapper;
    this.admins =
        new HashSet<>(
            Arrays.stream(admins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
  }

  public boolean isAdmin(String user) {
    return admins.contains(user);
  }

  public record Entry(
      String id,
      String kind,
      String sourceId,
      String scope,
      String label,
      int version,
      Integer publishedVersion) {}

  public record Detail(
      String id,
      String kind,
      String label,
      int version,
      Integer publishedVersion,
      JsonNode config,
      List<Map<String, Object>> history) {}

  public record SaveRequest(int version, JsonNode config) {}

  public record VersionRequest(int version) {}

  public List<Entry> list(String user, String kind, String query) {
    ContentConfig.require(Set.of("WORD", "PHONEME").contains(kind), "内容类型无效");
    String scope = isAdmin(user) ? "(scope_id='' OR scope_id=?)" : "scope_id=?";
    return db.query(
        "SELECT * FROM content_entries WHERE "
            + scope
            + " AND kind=? AND label LIKE ? ORDER BY label LIMIT 100",
        (r, n) -> entry(r),
        user,
        kind,
        "%" + (query == null ? "" : query.replace("%", "\\%").replace("_", "\\_")) + "%");
  }

  private Entry entry(java.sql.ResultSet r) throws java.sql.SQLException {
    return new Entry(
        r.getString("id"),
        r.getString("kind"),
        r.getString("source_id"),
        r.getString("scope_id"),
        r.getString("label"),
        r.getInt("version"),
        (Integer) r.getObject("published_version"));
  }

  public Entry get(String id) {
    return db.query("SELECT * FROM content_entries WHERE id=?", (r, n) -> entry(r), id).stream()
        .findFirst()
        .orElseThrow(() -> missing("内容不存在"));
  }

  private Entry editable(String user, String id) {
    Entry e = get(id);
    if (!(e.scope().equals(user) || (e.scope().isEmpty() && isAdmin(user))))
      throw new ApiException(HttpStatus.FORBIDDEN, "RESOURCE_FORBIDDEN", "无权维护该内容");
    return e;
  }

  public Detail detail(String user, String id, Integer revision) {
    Entry e = editable(user, id);
    int v = revision == null ? e.version() : revision;
    return new Detail(
        e.id(),
        e.kind(),
        e.label(),
        e.version(),
        e.publishedVersion(),
        read(e, v),
        db.query(
            "SELECT version, origin, editor_id AS `editorId`, created_at AS `createdAt` FROM content_revisions WHERE entry_id=? ORDER BY version DESC LIMIT 30",
            (row, n) ->
                Map.<String, Object>of(
                    "version",
                    row.getInt("version"),
                    "origin",
                    row.getString("origin"),
                    "editorId",
                    row.getString("editorId"),
                    "createdAt",
                    row.getTimestamp("createdAt").toInstant().toString()),
            id));
  }

  public ObjectNode read(Entry e, int version) {
    ContentConfig.require(version >= 0, "版本不能为负数");
    if (version > 0)
      return db
          .query(
              "SELECT config_json FROM content_revisions WHERE entry_id=? AND version=?",
              (r, n) -> configs.read(r.getString(1)),
              e.id(),
              version)
          .stream()
          .findFirst()
          .orElseThrow(() -> missing("版本不存在"));
    if (e.kind().equals("WORD")) {
      ObjectNode c =
          db
              .query(
                  "SELECT * FROM words WHERE id=?",
                  (r, n) ->
                      configs.emptyWord(
                          r.getString("display_word"),
                          r.getString("ipa"),
                          r.getString("meaning"),
                          r.getString("audio_url")),
                  e.sourceId())
              .stream()
              .findFirst()
              .orElseThrow(() -> missing("单词不存在"));
      // 把现有课程的拆读数据引入编辑草稿，不覆盖正在学习的会话。
      for (String json :
          db.queryForList(
              "SELECT content_json FROM learning_units WHERE content_type IN ('IPA_DECODING','WORD_DECODING')",
              String.class)) {
        JsonNode old = mapper.readTree(json);
        if (!old.path("wordId").asText().equals(e.sourceId())) continue;
        if (old.has("tip")) c.put("tip", old.path("tip").asText());
        if (old.has("parts"))
          for (JsonNode p : old.path("parts")) {
            ObjectNode part = c.withArray("parts").addObject();
            part.put("letters", p.path("letters").asText()).put("tip", "");
            part.putArray("segments")
                .addObject()
                .put("text", p.path("ipa").asText())
                .put("tone", "primary")
                .put("bold", false);
          }
        if (old.has("commonTips")) c.set("commonTips", old.path("commonTips"));
        if (old.has("specialTips")) c.set("specialTips", old.path("specialTips"));
      }
      return c;
    }
    return db
        .query(
            "SELECT * FROM phonemes WHERE id=?",
            (r, n) -> {
              ObjectNode c = configs.read(r.getString("detail_json"));
              c.put("schemaVersion", 1)
                  .put("kind", "PHONEME")
                  .put("accent", "BOTH")
                  .put("ipa", r.getString("ipa"))
                  .put("category", r.getString("category"))
                  .put("group", r.getString("group_code"));
              c.putArray("ipaSegments")
                  .addObject()
                  .put("text", r.getString("ipa"))
                  .put("tone", "primary")
                  .put("bold", false);
              c.putArray("questions");
              c.putObject("audio")
                  .put("url", r.getString("audio_url") == null ? "" : r.getString("audio_url"))
                  .put(
                      "usUrl",
                      r.getString("audio_us_url") == null ? "" : r.getString("audio_us_url"))
                  .put(
                      "gbUrl",
                      r.getString("audio_gb_url") == null ? "" : r.getString("audio_gb_url"))
                  .put("objectKey", "")
                  .put("provider", "");
              return c;
            },
            e.sourceId())
        .stream()
        .findFirst()
        .orElseThrow(() -> missing("音标不存在"));
  }

  @Transactional
  public Detail save(String user, String id, SaveRequest request) {
    Entry e = editable(user, id);
    configs.validate(request.config(), e.kind());
    if (e.kind().equals("WORD"))
      ContentConfig.require(
          request.config().path("word").asText().equalsIgnoreCase(e.label()), "不能在编辑中更换单词，请创建新词条");
    int v = request.version() + 1;
    if (db.update(
            "UPDATE content_entries SET version=?, label=? WHERE id=? AND version=?",
            v,
            request.config().path(e.kind().equals("WORD") ? "word" : "ipa").asText(),
            id,
            request.version())
        != 1) throw conflict();
    db.update(
        "INSERT INTO content_revisions(entry_id,version,config_json,editor_id,origin) VALUES(?,?,?,?,?)",
        id,
        v,
        request.config().toString(),
        user,
        "MANUAL");
    return detail(user, id, null);
  }

  public JsonNode validate(String user, String id, JsonNode config) {
    Entry e = editable(user, id);
    configs.validate(config, e.kind());
    return config;
  }

  @Transactional
  public Detail publish(String user, String id, int version) {
    Entry e = editable(user, id);
    if (e.version() != version || version < 1) throw conflict();
    ObjectNode c = read(e, version);
    configs.validate(c, e.kind());
    if (db.update(
            "UPDATE content_entries SET published_version=? WHERE id=? AND version=?",
            version,
            id,
            version)
        != 1) throw conflict();
    if (e.kind().equals("WORD")) {
      String wordId = ensureWord(c, e.sourceId());
      db.update("UPDATE content_entries SET source_id=? WHERE id=?", wordId, id);
      if (e.scope().isEmpty())
        db.update(
            "UPDATE words SET ipa=?,meaning=?,audio_url=? WHERE id=?",
            c.path("ipa").asText(),
            c.path("meaning").asText(),
            c.path("audio").path("url").asText(""),
            wordId);
      linkLibraries(id, wordId);
    } else if (e.scope().isEmpty()) {
      // 旧课程以 IPA 关联音标，首次发布前补充稳定 ID，避免修改 IPA 后断开引用。
      String oldIpa =
          db.queryForObject("SELECT ipa FROM phonemes WHERE id=?", String.class, e.sourceId());
      for (var unit :
          db.queryForList(
              "SELECT id,content_json FROM learning_units WHERE content_type='PHONEME_DETAIL'")) {
        ObjectNode content = configs.read((String) unit.get("content_json"));
        if (!content.has("phonemeId") && content.path("ipa").asText().equals(oldIpa)) {
          content.put("phonemeId", e.sourceId());
          db.update(
              "UPDATE learning_units SET content_json=? WHERE id=?",
              content.toString(),
              unit.get("id"));
        }
      }
      db.update(
          "UPDATE phonemes SET ipa=?,category=?,group_code=?,audio_url=?,audio_us_url=?,audio_gb_url=?,detail_json=? WHERE id=?",
          c.path("ipa").asText(),
          c.path("category").asText(),
          c.path("group").asText(),
          c.path("audio").path("url").asText(""),
          c.path("audio").path("usUrl").asText(""),
          c.path("audio").path("gbUrl").asText(""),
          c.toString(),
          e.sourceId());
    }
    return detail(user, id, null);
  }

  public void linkPublishedEntry(String id) {
    Entry entry = get(id);
    if (entry.publishedVersion() != null) linkLibraries(id, entry.sourceId());
  }

  private void linkLibraries(String id, String wordId) {
    for (Map<String, Object> item :
        db.queryForList(
            "SELECT j.library_id,i.position FROM content_job_items i JOIN content_jobs j ON j.id=i.job_id WHERE i.entry_id=?",
            id)) {
      String library = (String) item.get("library_id");
      Integer order = ((Number) item.get("position")).intValue();
      if (db.queryForObject(
              "SELECT COUNT(*) FROM word_library_items WHERE library_id=? AND word_id=?",
              Integer.class,
              library,
              wordId)
          == 0)
        db.update(
            "INSERT INTO word_library_items(library_id,word_id,sort_order) VALUES(?,?,?)",
            library,
            wordId,
            order);
      db.update("UPDATE word_libraries SET status='SUCCEEDED' WHERE id=?", library);
    }
  }

  private String ensureWord(JsonNode c, String candidate) {
    String word = c.path("word").asText().toLowerCase(Locale.ROOT);
    List<String> ids =
        db.queryForList(
            "SELECT id FROM words WHERE normalized_word=? AND accent='US'", String.class, word);
    if (!ids.isEmpty()) return ids.getFirst();
    // INSERT 后若并发发布碰到唯一约束，事务回滚，调用方可安全重试。
    db.update(
        "INSERT INTO words(id,normalized_word,display_word,ipa,meaning,accent,audio_url) VALUES(?,?,?,?,?,'US',?)",
        candidate,
        word,
        c.path("word").asText(),
        c.path("ipa").asText(),
        c.path("meaning").asText(),
        c.path("audio").path("url").asText(""));
    return candidate;
  }

  @Transactional
  public String generated(String user, ObjectNode config) {
    configs.validate(config, "WORD");
    String word = config.path("word").asText();
    List<Entry> old =
        db.query(
            "SELECT * FROM content_entries WHERE kind='WORD' AND scope_id=? AND LOWER(label)=?",
            (r, n) -> entry(r),
            user,
            word.toLowerCase(Locale.ROOT));
    if (!old.isEmpty()) return old.getFirst().id(); // 已有人工草稿/发布内容优先，生成任务不覆盖。
    String id = id("content"), source = id("w");
    db.update(
        "INSERT INTO content_entries(id,kind,source_id,scope_id,label,version) VALUES(?,'WORD',?,?,?,1)",
        id,
        source,
        user,
        word);
    db.update(
        "INSERT INTO content_revisions(entry_id,version,config_json,editor_id,origin) VALUES(?,1,?,?,?)",
        id,
        config.toString(),
        user,
        "DEEPSEEK");
    return id;
  }

  public ObjectNode published(String user, String kind, String source) {
    List<Entry> found =
        db.query(
            "SELECT * FROM content_entries WHERE kind=? AND source_id=? AND (scope_id='' OR scope_id=?) AND published_version IS NOT NULL ORDER BY CASE WHEN scope_id='' THEN 1 ELSE 0 END",
            (r, n) -> entry(r),
            kind,
            source,
            user);
    return found.isEmpty() ? null : read(found.getFirst(), found.getFirst().publishedVersion());
  }

  public JsonNode publicWord(String user, String source) {
    ObjectNode c = published(user, "WORD", source);
    if (c == null) {
      Entry e = new Entry("", "WORD", source, "", "", 0, null);
      c = read(e, 0);
    }
    return configs.publicView(c);
  }

  public JsonNode publicPhoneme(String source) {
    ObjectNode c = published("", "PHONEME", source);
    if (c == null) {
      Entry e = new Entry("", "PHONEME", source, "", "", 0, null);
      c = read(e, 0);
    }
    return configs.publicView(c);
  }

  public Map<String, JsonNode> publicWords(String user, List<String> ids) {
    Map<String, JsonNode> result = new HashMap<>();
    wordConfigs(user, ids).forEach((id, config) -> result.put(id, configs.publicView(config)));
    return result;
  }

  private Map<String, ObjectNode> wordConfigs(String user, List<String> ids) {
    Map<String, ObjectNode> result = new LinkedHashMap<>();
    if (ids.isEmpty()) return result;
    String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
    List<Object> parameters = new ArrayList<>();
    parameters.add(user);
    parameters.addAll(ids);
    // 一次关联公共/个人的已发布版本，前端只接收完整对象。
    db.query(
        """
      SELECT w.*, COALESCE(pr.config_json,gr.config_json) AS config_json
      FROM words w
      LEFT JOIN content_entries pe ON pe.kind='WORD' AND pe.source_id=w.id AND pe.scope_id=?
      LEFT JOIN content_revisions pr ON pr.entry_id=pe.id AND pr.version=pe.published_version
      LEFT JOIN content_entries ge ON ge.kind='WORD' AND ge.source_id=w.id AND ge.scope_id=''
      LEFT JOIN content_revisions gr ON gr.entry_id=ge.id AND gr.version=ge.published_version
      WHERE w.id IN (
      """
            + placeholders
            + ")",
        (org.springframework.jdbc.core.RowCallbackHandler)
            row -> {
              String json = row.getString("config_json");
              ObjectNode config =
                  json == null
                      ? configs.emptyWord(
                          row.getString("display_word"),
                          row.getString("ipa"),
                          row.getString("meaning"),
                          row.getString("audio_url"))
                      : configs.read(json);
              result.put(row.getString("id"), config);
            },
        parameters.toArray());
    return result;
  }

  @Transactional
  public Map<String, Object> study(String user, String library) {
    boolean allowed =
        db.queryForObject(
                "SELECT COUNT(*) FROM user_word_libraries WHERE user_id=? AND library_id=?",
                Integer.class,
                user,
                library)
            > 0;
    if (!allowed) throw missing("请先添加该词库");
    var items = mapper.createArrayNode();
    List<String> words =
        db.queryForList(
            "SELECT word_id FROM word_library_items WHERE library_id=? ORDER BY sort_order",
            String.class,
            library);
    Map<String, ObjectNode> configured = wordConfigs(user, words);
    for (String word : words) {
      ObjectNode c = configured.get(word);
      c.put("wordId", word);
      items.add(c);
    }
    ContentConfig.require(!items.isEmpty(), "词库暂无已发布单词");
    String id = id("study");
    db.update(
        "INSERT INTO content_snapshots(id,user_id,library_id,content_json) VALUES(?,?,?,?)",
        id,
        user,
        library,
        items.toString());
    return Map.of("sessionId", id, "items", configs.publicView(items));
  }

  @Transactional
  public Map<String, Object> answer(
      String user, String snapshot, String question, String selected) {
    ContentConfig.require(question != null && selected != null, "题目和选项不能为空");
    db.queryForList(
        "SELECT id FROM content_snapshots WHERE id=? AND user_id=? FOR UPDATE", snapshot, user);
    JsonNode items =
        db
            .query(
                "SELECT content_json FROM content_snapshots WHERE id=? AND user_id=?",
                (r, n) -> mapper.readTree(r.getString(1)),
                snapshot,
                user)
            .stream()
            .findFirst()
            .orElseThrow(() -> missing("练习不存在"));
    String[] key = question.split(":", 2);
    ContentConfig.require(key.length == 2, "题目编号无效");
    JsonNode q = null;
    for (JsonNode item : items)
      if (item.path("wordId").asText().equals(key[0]))
        for (JsonNode candidate : item.path("questions"))
          if (candidate.path("id").asText().equals(key[1])) q = candidate;
    if (q == null) throw missing("题目不存在");
    boolean exists = false;
    for (JsonNode o : q.path("options")) if (o.path("id").asText().equals(selected)) exists = true;
    ContentConfig.require(exists, "选项不存在");
    boolean correct = q.path("correctOptionId").asText().equals(selected);
    db.update(
        "DELETE FROM content_answers WHERE snapshot_id=? AND question_id=?", snapshot, question);
    db.update(
        "INSERT INTO content_answers(snapshot_id,question_id,selected_option_id,correct) VALUES(?,?,?,?)",
        snapshot,
        question,
        selected,
        correct);
    return Map.of(
        "correct",
        correct,
        "correctOptionId",
        q.path("correctOptionId").asText(),
        "selectedOptionId",
        selected,
        "explanation",
        q.path("explanation").asText());
  }

  @Transactional
  public Map<String, Object> completeWord(String user, String snapshot, String word) {
    var row =
        db
            .queryForList(
                "SELECT library_id,content_json FROM content_snapshots WHERE id=? AND user_id=? AND library_id IS NOT NULL FOR UPDATE",
                snapshot,
                user)
            .stream()
            .findFirst()
            .orElseThrow(() -> missing("练习不存在"));
    JsonNode target = null;
    for (JsonNode item : mapper.readTree((String) row.get("content_json")))
      if (item.path("wordId").asText().equals(word)) target = item;
    if (target == null) throw missing("单词不属于当前练习");
    for (JsonNode q : target.path("questions"))
      ContentConfig.require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM content_answers WHERE snapshot_id=? AND question_id=? AND correct=true",
                  Integer.class,
                  snapshot,
                  word + ":" + q.path("id").asText())
              > 0,
          "请先完成该词的练习题");
    String library = (String) row.get("library_id");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM user_word_libraries WHERE user_id=? AND library_id=?",
            Integer.class,
            user,
            library)
        == 0) throw missing("词库已移除");
    if (db.update(
            "UPDATE user_word_progress SET status='COMPLETED' WHERE user_id=? AND library_id=? AND word_id=?",
            user,
            library,
            word)
        == 0)
      db.update(
          "INSERT INTO user_word_progress(user_id,library_id,word_id,status,attempts) VALUES(?,?,?,'COMPLETED',1)",
          user,
          library,
          word);
    return Map.of("wordId", word, "completed", true);
  }

  public static String id(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }

  private static ApiException missing(String m) {
    return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", m);
  }

  private static ApiException conflict() {
    return new ApiException(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", "内容已被修改，请重新加载后保存");
  }
}
