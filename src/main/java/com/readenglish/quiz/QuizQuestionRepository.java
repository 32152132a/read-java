package com.readenglish.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

interface QuizQuestionRepository extends JpaRepository<QuizQuestionEntity, String> {}
