package com.readenglish.library;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserWordLibraryRepository
    extends JpaRepository<UserWordLibraryEntity, UserWordLibraryId> {

  List<UserWordLibraryEntity> findByIdUserId(String userId);
}
