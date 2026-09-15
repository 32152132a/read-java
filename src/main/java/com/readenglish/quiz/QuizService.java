package com.readenglish.quiz;

import com.readenglish.common.api.ApiException;
import com.readenglish.learningcontent.LearningStageService;
import com.readenglish.quiz.QuizModels.AnswerResponse;
import com.readenglish.quiz.QuizModels.SubmitAnswerRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {

  private final QuizQuestionRepository questionRepository;
  private final QuizOptionRepository optionRepository;
  private final QuizSubmissionRepository submissionRepository;
  private final LearningStageService learningStageService;

  public QuizService(
      QuizQuestionRepository questionRepository,
      QuizOptionRepository optionRepository,
      QuizSubmissionRepository submissionRepository,
      LearningStageService learningStageService) {
    this.questionRepository = questionRepository;
    this.optionRepository = optionRepository;
    this.submissionRepository = submissionRepository;
    this.learningStageService = learningStageService;
  }

  @Transactional
  public AnswerResponse submit(String userId, String questionId, SubmitAnswerRequest request) {
    QuizQuestionEntity question =
        questionRepository.findById(questionId).orElseThrow(() -> notFound("题目不存在"));
    String sessionTemplate =
        learningStageService.getOwnedSessionTemplate(userId, request.sessionId());
    if (!question.getTemplateCode().equals(sessionTemplate)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "题目不属于当前学习会话");
    }

    QuizOptionEntity selected =
        optionRepository
            .findByQuestionIdAndOptionCode(questionId, request.selectedOptionId())
            .orElseThrow(
                () -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "所选答案不存在"));
    QuizOptionEntity correct =
        optionRepository.findByQuestionIdOrderBySortOrderAsc(questionId).stream()
            .filter(QuizOptionEntity::isCorrect)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("题目未配置正确答案"));

    submissionRepository.save(
        new QuizSubmissionEntity(
            "qs_" + UUID.randomUUID().toString().replace("-", ""),
            userId,
            questionId,
            request.sessionId(),
            selected.getId(),
            selected.isCorrect()));
    return new AnswerResponse(
        selected.isCorrect(),
        selected.getOptionCode(),
        correct.getOptionCode(),
        question.getExplanation());
  }

  private static ApiException notFound(String message) {
    return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
  }
}
