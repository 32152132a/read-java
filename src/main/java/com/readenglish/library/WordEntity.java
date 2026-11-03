package com.readenglish.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "words")
class WordEntity {

  @Id private String id;

  @Column(name = "display_word", nullable = false)
  private String displayWord;

  @Column(nullable = false)
  private String meaning;

  protected WordEntity() {}

  String getId() {
    return id;
  }

  String getDisplayWord() {
    return displayWord;
  }

  String getMeaning() {
    return meaning;
  }
}
