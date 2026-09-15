package com.readenglish.library;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
class WordLibraryItemId implements Serializable {

  @Column(name = "library_id")
  private String libraryId;

  @Column(name = "word_id")
  private String wordId;

  protected WordLibraryItemId() {}

  WordLibraryItemId(String libraryId, String wordId) {
    this.libraryId = libraryId;
    this.wordId = wordId;
  }

  String getLibraryId() {
    return libraryId;
  }

  String getWordId() {
    return wordId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof WordLibraryItemId that)) {
      return false;
    }
    return Objects.equals(libraryId, that.libraryId) && Objects.equals(wordId, that.wordId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(libraryId, wordId);
  }
}
