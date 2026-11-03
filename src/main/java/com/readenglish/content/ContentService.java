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
  private final PronunciationResources resources;

  public ContentService(
      JdbcTemplate db,
      ContentConfig configs,
      ObjectMapper mapper,
      PronunciationResources resources,
      @Value("${app.content.admin-user-ids:}") String admins) {
    this.db = db;
    this.configs = configs;
    this.mapper = mapper;
    this.resources = resources;
    this.admins =
        new HashSet<>(
            Arrays.stream(admins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
  }

  public boolean isAdmin(String user) {
    return admins.contains(user);
  }

  public record Entry(
      String id, String kind, String sourceId, String scope, String label, int version) {}

  public record Detail(
      String id, String kind, String label, int version, boolean publicResource, JsonNode config) {}

  public record WordView(String contentId, boolean editable, JsonNode config) {}

  public record SaveRequest(int version, JsonNode config) {}

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
        r.getInt("version"));
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

  public Detail detail(String user, String id) {
    Entry e = editable(user, id);
    return new Detail(e.id(), e.kind(), e.label(), e.version(), e.scope().isEmpty(), current(e));
  }

  private ObjectNode current(Entry e) {
    if (e.kind().equals("PHONEME"))
      return resources.phonemes(List.of(e.sourceId()), "GB").get(e.sourceId());
    var stored =
        db.queryForList(
            "SELECT config_json FROM content_entries WHERE id=? AND config_json IS NOT NULL",
            String.class,
            e.id());
    String accent =
        stored.isEmpty() ? "US" : configs.read(stored.getFirst()).path("accent").asText("US");
    ObjectNode base = resources.words(List.of(e.sourceId()), accent).get(e.sourceId());
    return teaching(base, stored.isEmpty() ? null : stored.getFirst());
  }

  private ObjectNode teaching(ObjectNode base, String json) {
    if (base == null) throw missing("单词不存在");
    if (json == null) return base;
    ObjectNode extra = configs.read(json);
    for (String key : List.of("tip", "commonTips", "specialTips", "questions", "meaning"))
      if (extra.has(key)) base.set(key, extra.path(key));
    if (extra.path("accent").asText().equals(base.path("accent").asText()))
      for (String key : List.of("parts", "syllables"))
        if (extra.has(key)) base.set(key, extra.path(key));
    StringBuilder ipa = new StringBuilder();
    for (JsonNode s : extra.path("ipaSegments")) ipa.append(s.path("text").asText());
    if (ipa.toString().equals(base.path("ipa").asText()))
      base.set("ipaSegments", extra.path("ipaSegments"));
    return base;
  }

  @Transactional
  public Detail save(String user, String id, SaveRequest request) {
    Entry e = editable(user, id);
    configs.validate(request.config(), e.kind());
    ObjectNode before = current(e);
    boolean studyChanged =
        !request.config().path("questions").equals(before.path("questions"))
            || !request.config().path("parts").equals(before.path("parts"))
            || !request.config().path("syllables").equals(before.path("syllables"));
    if (!e.scope().isEmpty())
      ContentConfig.require(
          request.config().path("pronunciations").equals(before.path("pronunciations")),
          "个人教学内容不能修改公共发音资源");
    if (e.kind().equals("WORD"))
      ContentConfig.require(
          request.config().path("word").asText().equalsIgnoreCase(e.label()), "不能在编辑中更换单词，请创建新词条");
    int v = request.version() + 1;
    String sourceId = e.sourceId();
    ObjectNode stored = (ObjectNode) request.config().deepCopy();
    stored.remove(List.of("audio", "pronunciations", "word", "wordId", "ipa", "variantKey"));
    if (e.scope().isEmpty()) stored.remove("meaning");
    if (db.update(
            "UPDATE content_entries SET version=?,label=?,source_id=?,config_json=? WHERE id=? AND version=?",
            v,
            request.config().path(e.kind().equals("WORD") ? "word" : "ipa").asText(),
            sourceId,
            e.kind().equals("PHONEME") ? null : stored.toString(),
            id,
            request.version())
        != 1) throw conflict();
    if (e.kind().equals("WORD")) {
      if (e.scope().isEmpty()) resources.saveWord(sourceId, request.config());
      linkLibraries(id, sourceId);
      if (studyChanged) resetLibrariesForWord(e.scope(), sourceId);
    } else if (e.scope().isEmpty()) {
      resources.savePhoneme(e.sourceId(), request.config());
    }
    return detail(user, id);
  }

  @Transactional
  public void linkCurrentEntry(String id) {
    Entry entry = get(id);
    if (!entry.kind().equals("WORD")) return;
    linkLibraries(id, entry.sourceId());
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
          == 0) {
        db.update(
            "INSERT INTO word_library_items(library_id,word_id,sort_order) VALUES(?,?,?)",
            library,
            wordId,
            order);
        resetLibrary(library, null);
      }
      db.update("UPDATE word_libraries SET status='SUCCEEDED' WHERE id=?", library);
    }
  }

  private void resetLibrariesForWord(String scope, String wordId) {
    for (String library :
        db.queryForList(
            "SELECT library_id FROM word_library_items WHERE word_id=?", String.class, wordId))
      resetLibrary(library, scope.isEmpty() ? null : scope);
  }

  private void resetLibrary(String library, String user) {
    if (user == null) {
      db.update("DELETE FROM content_library_answers WHERE library_id=?", library);
      db.update("DELETE FROM user_word_progress WHERE library_id=?", library);
      db.update("UPDATE user_word_libraries SET last_position=0 WHERE library_id=?", library);
      return;
    }
    db.update(
        "DELETE FROM content_library_answers WHERE user_id=? AND library_id=?", user, library);
    db.update("DELETE FROM user_word_progress WHERE user_id=? AND library_id=?", user, library);
    db.update(
        "UPDATE user_word_libraries SET last_position=0 WHERE user_id=? AND library_id=?",
        user,
        library);
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
    if (!old.isEmpty()) return old.getFirst().id(); // 已有人工内容优先，生成任务不覆盖。
    String id = id("content"), source = id("w");
    source = resources.ensureWord(config, source);
    ObjectNode stored = config.deepCopy();
    stored.remove(List.of("word", "wordId", "ipa", "audio", "pronunciations", "variantKey"));
    db.update(
        "INSERT INTO content_entries(id,kind,source_id,scope_id,label,version,config_json) VALUES(?,'WORD',?,?,?,1,?)",
        id,
        source,
        user,
        word,
        stored.toString());
    return id;
  }

  public ObjectNode current(String user, String kind, String source) {
    if (kind.equals("PHONEME"))
      return resources.phonemes(List.of(source), resources.preferredAccent(user)).get(source);
    var views = wordViews(user, List.of(source));
    return views.containsKey(source) ? (ObjectNode) views.get(source).config() : null;
  }

  public JsonNode publicWord(String user, String source) {
    ObjectNode c = current(user, "WORD", source);
    if (c == null) {
      throw missing("单词不存在");
    }
    return configs.publicView(c);
  }

  public JsonNode publicPhoneme(String source) {
    ObjectNode c = current("", "PHONEME", source);
    if (c == null) {
      throw missing("音标不存在");
    }
    return configs.publicView(c);
  }

  public Map<String, WordView> publicWordViews(String user, List<String> ids) {
    Map<String, WordView> result = new LinkedHashMap<>();
    wordViews(user, ids)
        .forEach(
            (id, view) ->
                result.put(
                    id,
                    new WordView(
                        view.contentId(), view.editable(), configs.publicView(view.config()))));
    return result;
  }

  private Map<String, WordView> wordViews(String user, List<String> ids) {
    Map<String, WordView> result = new LinkedHashMap<>();
    if (ids.isEmpty()) return result;
    String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
    List<Object> parameters = new ArrayList<>();
    parameters.add(user);
    parameters.addAll(ids);
    // 一次关联公共/个人当前配置，前端只接收完整对象。
    var bases = resources.words(ids, resources.preferredAccent(user));
    db.query(
        """
      SELECT w.*, COALESCE(pe.config_json,ge.config_json) AS config_json,
        pe.id AS personal_content_id, ge.id AS global_content_id
      FROM words w
      LEFT JOIN content_entries pe ON pe.kind='WORD' AND pe.source_id=w.id AND pe.scope_id=?
      LEFT JOIN content_entries ge ON ge.kind='WORD' AND ge.source_id=w.id AND ge.scope_id=''
      WHERE w.id IN (
      """
            + placeholders
            + ")",
        (org.springframework.jdbc.core.RowCallbackHandler)
            row -> {
              String json = row.getString("config_json");
              ObjectNode config = teaching(bases.get(row.getString("id")), json);
              String personalId = row.getString("personal_content_id");
              String globalId = row.getString("global_content_id");
              String contentId = personalId != null ? personalId : globalId;
              boolean editable = personalId != null || (globalId != null && isAdmin(user));
              result.put(row.getString("id"), new WordView(contentId, editable, config));
            },
        parameters.toArray());
    return result;
  }

  private Map<String, ObjectNode> wordConfigs(String user, List<String> ids) {
    Map<String, ObjectNode> result = new LinkedHashMap<>();
    wordViews(user, ids).forEach((id, view) -> result.put(id, (ObjectNode) view.config()));
    return result;
  }

  @Transactional
  public Map<String, Object> study(String user, String library) {
    if (db.queryForObject(
            "SELECT COUNT(*) FROM user_word_libraries WHERE user_id=? AND library_id=?",
            Integer.class,
            user,
            library)
        == 0) throw missing("请先添加该词库");
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
    ContentConfig.require(!items.isEmpty(), "词库暂无可学习单词");
    return Map.of("sessionId", library, "items", configs.publicView(items));
  }

  @Transactional
  public Map<String, Object> answer(
      String user, String sessionId, String question, String selected) {
    ContentConfig.require(question != null && selected != null, "题目和选项不能为空");
    LibrarySession session = librarySession(user, sessionId);
    String[] key = question.split(":", 2);
    ContentConfig.require(key.length == 2, "题目编号无效");
    ContentConfig.require(
        db.queryForObject(
                "SELECT COUNT(*) FROM word_library_items WHERE library_id=? AND word_id=?",
                Integer.class,
                session.library(),
                key[0])
            > 0,
        "单词不属于当前词库");
    ObjectNode item = wordConfigs(user, List.of(key[0])).get(key[0]);
    if (item == null) throw missing("单词不存在");
    JsonNode q = null;
    for (JsonNode candidate : item.path("questions"))
      if (candidate.path("id").asText().equals(key[1])) q = candidate;
    if (q == null) throw missing("题目不存在");
    boolean exists = false;
    for (JsonNode o : q.path("options")) if (o.path("id").asText().equals(selected)) exists = true;
    ContentConfig.require(exists, "选项不存在");
    boolean correct = q.path("correctOptionId").asText().equals(selected);
    db.update(
        "DELETE FROM content_library_answers WHERE user_id=? AND library_id=? AND question_id=?",
        user,
        session.library(),
        question);
    db.update(
        "INSERT INTO content_library_answers(user_id,library_id,question_id,selected_option_id,correct) VALUES(?,?,?,?,?)",
        user,
        session.library(),
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
    LibrarySession session = librarySession(user, snapshot);
    ContentConfig.require(
        db.queryForObject(
                "SELECT COUNT(*) FROM word_library_items WHERE library_id=? AND word_id=?",
                Integer.class,
                session.library(),
                word)
            > 0,
        "单词不属于当前词库");
    ObjectNode target = wordConfigs(user, List.of(word)).get(word);
    if (target == null) throw missing("单词不存在");
    for (JsonNode q : target.path("questions"))
      ContentConfig.require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM content_library_answers WHERE user_id=? AND library_id=? AND question_id=? AND correct=true",
                  Integer.class,
                  user,
                  session.library(),
                  word + ":" + q.path("id").asText())
              > 0,
          "请先完成该词的练习题");
    if (db.update(
            "UPDATE user_word_progress SET status='COMPLETED' WHERE user_id=? AND library_id=? AND word_id=?",
            user,
            session.library(),
            word)
        == 0)
      db.update(
          "INSERT INTO user_word_progress(user_id,library_id,word_id,status,attempts) VALUES(?,?,?,'COMPLETED',1)",
          user,
          session.library(),
          word);
    return Map.of("wordId", word, "completed", true);
  }

  private LibrarySession librarySession(String user, String sessionId) {
    if (db.queryForList(
            "SELECT library_id FROM user_word_libraries WHERE user_id=? AND library_id=? FOR UPDATE",
            String.class,
            user,
            sessionId)
        .isEmpty()) throw missing("词库已移除");
    return new LibrarySession(sessionId);
  }

  private record LibrarySession(String library) {}

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
