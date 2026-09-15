package com.readenglish.quiz;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuizOptionRepository extends JpaRepository<QuizOptionEntity, String> {

  Optional<QuizOptionEntity> findByQuestionIdAndOptionCode(String questionId, String optionCode);

  List<QuizOptionEntity> findByQuestionIdOrderBySortOrderAsc(String questionId);
}
