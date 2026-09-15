package com.readenglish.quiz;

import com.readenglish.quiz.QuizModels.AnswerResponse;
import com.readenglish.quiz.QuizModels.SubmitAnswerRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quiz-questions")
public class QuizController {

  private final QuizService quizService;

  public QuizController(QuizService quizService) {
    this.quizService = quizService;
  }

  @PostMapping("/{questionId}/answers")
  public AnswerResponse submit(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String questionId,
      @Valid @RequestBody SubmitAnswerRequest request) {
    return quizService.submit(jwt.getSubject(), questionId, request);
  }
}
