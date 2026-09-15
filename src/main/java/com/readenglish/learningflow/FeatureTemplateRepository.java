package com.readenglish.learningflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface FeatureTemplateRepository extends JpaRepository<FeatureTemplateEntity, String> {

  List<FeatureTemplateEntity> findByEnabledTrueOrderBySortOrderAsc();
}
