package com.readenglish.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

interface QuizSubmissionRepository extends JpaRepository<QuizSubmissionEntity, String> {}
