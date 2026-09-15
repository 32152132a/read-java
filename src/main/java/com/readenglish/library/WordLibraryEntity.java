package com.readenglish.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "word_libraries")
class WordLibraryEntity {

  @Id private String id;

  @Column(name = "owner_user_id")
  private String ownerUserId;

  @Column(nullable = false)
  private String type;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private String status;

  protected WordLibraryEntity() {}

  String getId() {
    return id;
  }

  String getOwnerUserId() {
    return ownerUserId;
  }

  String getType() {
    return type;
  }

  String getName() {
    return name;
  }

  String getDescription() {
    return description;
  }

  String getStatus() {
    return status;
  }
}
