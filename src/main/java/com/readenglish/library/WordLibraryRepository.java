package com.readenglish.library;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface WordLibraryRepository extends JpaRepository<WordLibraryEntity, String> {

  List<WordLibraryEntity> findByStatusOrderByNameAsc(String status);
}
