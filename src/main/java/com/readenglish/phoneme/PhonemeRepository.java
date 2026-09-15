package com.readenglish.phoneme;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PhonemeRepository extends JpaRepository<PhonemeEntity, String> {

  List<PhonemeEntity> findByGroupCodeOrderBySortOrderAsc(String groupCode);
}
