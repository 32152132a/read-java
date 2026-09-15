package com.readenglish.library;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "word_library_items")
class WordLibraryItemEntity {

  @EmbeddedId private WordLibraryItemId id;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected WordLibraryItemEntity() {}

  String getWordId() {
    return id.getWordId();
  }

  int getSortOrder() {
    return sortOrder;
  }
}
