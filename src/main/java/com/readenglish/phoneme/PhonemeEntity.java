package com.readenglish.phoneme;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "phonemes")
class PhonemeEntity {

  @Id private String id;

  @Column(nullable = false)
  private String ipa;

  @Column(name = "group_code", nullable = false)
  private String groupCode;

  @Column(nullable = false)
  private String category;

  @Column(name = "audio_url")
  private String audioUrl;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "detail_json", nullable = false)
  private String detailJson;

  protected PhonemeEntity() {}

  String getId() {
    return id;
  }

  String getIpa() {
    return ipa;
  }

  String getGroupCode() {
    return groupCode;
  }

  String getCategory() {
    return category;
  }

  String getAudioUrl() {
    return audioUrl;
  }

  int getSortOrder() {
    return sortOrder;
  }

  String getDetailJson() {
    return detailJson;
  }
}
