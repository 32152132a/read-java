package com.readenglish.library;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_word_progress")
class UserWordProgressEntity {

  @EmbeddedId private UserWordProgressId id;

  private String status;

  protected UserWordProgressEntity() {}
}
