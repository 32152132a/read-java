package com.readenglish.learningcontent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface LearningUnitRepository extends JpaRepository<LearningUnitEntity, String> {

  List<LearningUnitEntity> findByTemplateCodeAndEnabledTrueOrderBySortOrderAsc(String templateCode);
}
