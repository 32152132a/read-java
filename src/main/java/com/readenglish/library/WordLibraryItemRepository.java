package com.readenglish.library;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface WordLibraryItemRepository
    extends JpaRepository<WordLibraryItemEntity, WordLibraryItemId> {

  long countByIdLibraryId(String libraryId);

  List<WordLibraryItemEntity> findByIdLibraryIdAndSortOrderGreaterThanOrderBySortOrderAsc(
      String libraryId, int sortOrder, Pageable pageable);
}
