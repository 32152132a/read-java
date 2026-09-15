package com.readenglish.library;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
class UserWordLibraryId implements Serializable {

  @Column(name = "user_id")
  private String userId;

  @Column(name = "library_id")
  private String libraryId;

  protected UserWordLibraryId() {}

  UserWordLibraryId(String userId, String libraryId) {
    this.userId = userId;
    this.libraryId = libraryId;
  }

  String getUserId() {
    return userId;
  }

  String getLibraryId() {
    return libraryId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof UserWordLibraryId that)) {
      return false;
    }
    return Objects.equals(userId, that.userId) && Objects.equals(libraryId, that.libraryId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, libraryId);
  }
}
