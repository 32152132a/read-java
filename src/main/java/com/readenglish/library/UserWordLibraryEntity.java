package com.readenglish.library;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_word_libraries")
class UserWordLibraryEntity {

  @EmbeddedId private UserWordLibraryId id;

  @Column(name = "last_position", nullable = false)
  private int lastPosition;

  protected UserWordLibraryEntity() {}

  UserWordLibraryEntity(String userId, String libraryId) {
    this.id = new UserWordLibraryId(userId, libraryId);
    this.lastPosition = 0;
  }

  String getLibraryId() {
    return id.getLibraryId();
  }

  int getLastPosition() {
    return lastPosition;
  }
}
