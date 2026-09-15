package com.readenglish.library;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
class UserWordProgressId implements Serializable {

  @Column(name = "user_id")
  private String userId;

  @Column(name = "library_id")
  private String libraryId;

  @Column(name = "word_id")
  private String wordId;

  protected UserWordProgressId() {}

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof UserWordProgressId that)) {
      return false;
    }
    return Objects.equals(userId, that.userId)
        && Objects.equals(libraryId, that.libraryId)
        && Objects.equals(wordId, that.wordId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, libraryId, wordId);
  }
}
