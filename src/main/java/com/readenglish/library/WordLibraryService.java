package com.readenglish.library;

import com.readenglish.common.api.ApiException;
import com.readenglish.library.WordLibraryModels.AddLibrariesRequest;
import com.readenglish.library.WordLibraryModels.AddLibrariesResponse;
import com.readenglish.library.WordLibraryModels.LibraryDetailResponse;
import com.readenglish.library.WordLibraryModels.LibraryListResponse;
import com.readenglish.library.WordLibraryModels.LibrarySummary;
import com.readenglish.library.WordLibraryModels.LibraryWord;
import com.readenglish.library.WordLibraryModels.LibraryWordPage;
import com.readenglish.library.WordLibraryModels.RemoveLibraryResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WordLibraryService {

  private static final String BASE_LIBRARY_ID = "lib_base";

  private final WordLibraryRepository libraryRepository;
  private final WordLibraryItemRepository itemRepository;
  private final WordRepository wordRepository;
  private final UserWordLibraryRepository userLibraryRepository;
  private final UserWordProgressRepository progressRepository;

  public WordLibraryService(
      WordLibraryRepository libraryRepository,
      WordLibraryItemRepository itemRepository,
      WordRepository wordRepository,
      UserWordLibraryRepository userLibraryRepository,
      UserWordProgressRepository progressRepository) {
    this.libraryRepository = libraryRepository;
    this.itemRepository = itemRepository;
    this.wordRepository = wordRepository;
    this.userLibraryRepository = userLibraryRepository;
    this.progressRepository = progressRepository;
  }

  @Transactional
  public void initializeForUser(String userId) {
    var id = new UserWordLibraryId(userId, BASE_LIBRARY_ID);
    if (!userLibraryRepository.existsById(id)) {
      userLibraryRepository.save(new UserWordLibraryEntity(userId, BASE_LIBRARY_ID));
    }
  }

  @Transactional(readOnly = true)
  public LibraryListResponse mine(String userId) {
    List<UserWordLibraryEntity> owned = userLibraryRepository.findByIdUserId(userId);
    Map<String, WordLibraryEntity> libraries =
        libraryRepository
            .findAllById(owned.stream().map(UserWordLibraryEntity::getLibraryId).toList())
            .stream()
            .collect(Collectors.toMap(WordLibraryEntity::getId, Function.identity()));
    List<LibrarySummary> items =
        owned.stream()
            .map(
                relation ->
                    toSummary(
                        userId, libraries.get(relation.getLibraryId()), relation.getLastPosition()))
            .toList();
    return new LibraryListResponse(items);
  }

  @Transactional(readOnly = true)
  public LibraryListResponse available(String userId) {
    var ownedIds =
        userLibraryRepository.findByIdUserId(userId).stream()
            .map(UserWordLibraryEntity::getLibraryId)
            .collect(Collectors.toSet());
    List<LibrarySummary> items =
        libraryRepository.findByStatusOrderByNameAsc("SUCCEEDED").stream()
            .filter(library -> !ownedIds.contains(library.getId()))
            .filter(
                library ->
                    library.getOwnerUserId() == null || userId.equals(library.getOwnerUserId()))
            .map(library -> toSummary(userId, library, 0))
            .toList();
    return new LibraryListResponse(items);
  }

  @Transactional
  public AddLibrariesResponse add(String userId, AddLibrariesRequest request) {
    List<String> requestedIds = request.libraryIds().stream().distinct().toList();
    Map<String, WordLibraryEntity> libraries =
        libraryRepository.findAllById(requestedIds).stream()
            .collect(Collectors.toMap(WordLibraryEntity::getId, Function.identity()));
    if (libraries.size() != requestedIds.size()) {
      throw notFound("部分词库不存在");
    }
    for (WordLibraryEntity library : libraries.values()) {
      if (!"SUCCEEDED".equals(library.getStatus())
          || (library.getOwnerUserId() != null && !userId.equals(library.getOwnerUserId()))) {
        throw new ApiException(HttpStatus.FORBIDDEN, "RESOURCE_FORBIDDEN", "无权添加该词库");
      }
    }

    var ownedIds =
        new HashSet<>(
            userLibraryRepository.findByIdUserId(userId).stream()
                .map(UserWordLibraryEntity::getLibraryId)
                .toList());
    List<String> added = new ArrayList<>();
    for (String libraryId : requestedIds) {
      if (ownedIds.add(libraryId)) {
        userLibraryRepository.save(new UserWordLibraryEntity(userId, libraryId));
        added.add(libraryId);
      }
    }
    return new AddLibrariesResponse(added);
  }

  @Transactional
  public RemoveLibraryResponse remove(String userId, String libraryId) {
    WordLibraryEntity library = getAccessible(userId, libraryId);
    if ("BASE".equals(library.getType())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "LIBRARY_BASE_NOT_REMOVABLE", "基础词库不能删除");
    }
    var relationId = new UserWordLibraryId(userId, libraryId);
    if (!userLibraryRepository.existsById(relationId)) {
      throw notFound("我的词库中不存在该词库");
    }
    int cleared = progressRepository.deleteForLibrary(userId, libraryId);
    userLibraryRepository.deleteById(relationId);
    return new RemoveLibraryResponse(libraryId, cleared);
  }

  @Transactional(readOnly = true)
  public LibraryDetailResponse get(String userId, String libraryId) {
    WordLibraryEntity library = getAccessible(userId, libraryId);
    boolean owned = userLibraryRepository.existsById(new UserWordLibraryId(userId, libraryId));
    return new LibraryDetailResponse(
        library.getId(),
        library.getName(),
        library.getType(),
        library.getDescription(),
        itemRepository.countByIdLibraryId(libraryId),
        owned,
        owned && !"BASE".equals(library.getType()));
  }

  @Transactional(readOnly = true)
  public LibraryWordPage words(String userId, String libraryId, String cursor, int size) {
    getAccessible(userId, libraryId);
    int lastOrder = decodeCursor(cursor);
    List<WordLibraryItemEntity> result =
        itemRepository.findByIdLibraryIdAndSortOrderGreaterThanOrderBySortOrderAsc(
            libraryId, lastOrder, PageRequest.of(0, size + 1));
    boolean hasMore = result.size() > size;
    List<WordLibraryItemEntity> pageItems = hasMore ? result.subList(0, size) : result;
    Map<String, WordEntity> words =
        wordRepository
            .findAllById(pageItems.stream().map(WordLibraryItemEntity::getWordId).toList())
            .stream()
            .collect(Collectors.toMap(WordEntity::getId, Function.identity()));
    List<LibraryWord> items =
        pageItems.stream()
            .map(item -> toWord(userId, libraryId, item, words.get(item.getWordId())))
            .toList();
    String nextCursor =
        hasMore ? encodeCursor(pageItems.get(pageItems.size() - 1).getSortOrder()) : null;
    return new LibraryWordPage(items, nextCursor, hasMore);
  }

  private LibrarySummary toSummary(String userId, WordLibraryEntity library, int lastPosition) {
    long wordCount = itemRepository.countByIdLibraryId(library.getId());
    long learnedCount =
        progressRepository.countByIdUserIdAndIdLibraryIdAndStatus(
            userId, library.getId(), "COMPLETED");
    return new LibrarySummary(
        library.getId(),
        library.getName(),
        library.getType(),
        library.getDescription(),
        wordCount,
        learnedCount,
        !"BASE".equals(library.getType()),
        lastPosition);
  }

  private LibraryWord toWord(
      String userId, String libraryId, WordLibraryItemEntity item, WordEntity word) {
    boolean learned =
        progressRepository.existsByIdUserIdAndIdLibraryIdAndIdWordIdAndStatus(
            userId, libraryId, word.getId(), "COMPLETED");
    return new LibraryWord(
        word.getId(),
        word.getDisplayWord(),
        word.getIpa(),
        word.getMeaning(),
        word.getAudioUrl(),
        item.getSortOrder(),
        learned);
  }

  private WordLibraryEntity getAccessible(String userId, String libraryId) {
    WordLibraryEntity library =
        libraryRepository.findById(libraryId).orElseThrow(() -> notFound("词库不存在"));
    if (library.getOwnerUserId() != null && !userId.equals(library.getOwnerUserId())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "RESOURCE_FORBIDDEN", "无权访问该词库");
    }
    return library;
  }

  private static int decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return -1;
    }
    try {
      String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      return Integer.parseInt(value);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "分页游标无效");
    }
  }

  private static String encodeCursor(int sortOrder) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(Integer.toString(sortOrder).getBytes(StandardCharsets.UTF_8));
  }

  private static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
  }
}
