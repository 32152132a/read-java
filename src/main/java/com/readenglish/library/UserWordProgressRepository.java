package com.readenglish.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserWordProgressRepository
    extends JpaRepository<UserWordProgressEntity, UserWordProgressId> {

  long countByIdUserIdAndIdLibraryIdAndStatus(String userId, String libraryId, String status);

  boolean existsByIdUserIdAndIdLibraryIdAndIdWordIdAndStatus(
      String userId, String libraryId, String wordId, String status);

  @Modifying
  @Query(
      "delete from UserWordProgressEntity progress where progress.id.userId = :userId and progress.id.libraryId = :libraryId")
  int deleteForLibrary(@Param("userId") String userId, @Param("libraryId") String libraryId);
}
