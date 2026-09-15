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
  private String ipa;

  @Column(nullable = false)
  private String meaning;

  @Column(name = "audio_url")
  private String audioUrl;

  protected WordEntity() {}

  String getId() {
    return id;
  }

  String getDisplayWord() {
    return displayWord;
  }

  String getIpa() {
    return ipa;
  }

  String getMeaning() {
    return meaning;
  }

  String getAudioUrl() {
    return audioUrl;
  }
}
