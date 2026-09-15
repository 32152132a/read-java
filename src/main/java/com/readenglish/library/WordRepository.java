package com.readenglish.library;

import org.springframework.data.jpa.repository.JpaRepository;

interface WordRepository extends JpaRepository<WordEntity, String> {}
