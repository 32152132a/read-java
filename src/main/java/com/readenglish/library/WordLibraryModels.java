package com.readenglish.library;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public final class WordLibraryModels {

  private WordLibraryModels() {}

  public record LibrarySummary(
      String id,
      String name,
      String type,
      String description,
      long wordCount,
      long learnedCount,
      boolean removable,
      int lastPosition) {}

  public record LibraryListResponse(List<LibrarySummary> items) {}

  public record AddLibrariesRequest(
      @NotEmpty(message = "请选择要添加的词库") List<@NotBlank String> libraryIds) {}

  public record AddLibrariesResponse(List<String> addedLibraryIds) {}

  public record RemoveLibraryResponse(String libraryId, int clearedProgressCount) {}

  public record LibraryDetailResponse(
      String id,
      String name,
      String type,
      String description,
      long wordCount,
      boolean owned,
      boolean removable) {}

  public record LibraryWord(
      String id,
      String word,
      String ipa,
      String meaning,
      String audioUrl,
      int order,
      boolean learned) {}

  public record LibraryWordPage(List<LibraryWord> items, String nextCursor, boolean hasMore) {}
}
