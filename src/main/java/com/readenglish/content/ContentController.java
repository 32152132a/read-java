package com.readenglish.content;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ContentController {
  private final ContentService contents;
  private final ContentJobs jobs;
  private final WordGenerator generator;

  public ContentController(ContentService contents, ContentJobs jobs, WordGenerator generator) {
    this.contents = contents;
    this.jobs = jobs;
    this.generator = generator;
  }

  @GetMapping("/content/capabilities")
  public Object capabilities(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
        "admin",
        contents.isAdmin(jwt.getSubject()),
        "generationEnabled",
        generator.available(),
        "userId",
        jwt.getSubject());
  }

  @GetMapping("/content")
  public Object list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "WORD") String kind,
      @RequestParam(defaultValue = "") String q) {
    return Map.of("items", contents.list(jwt.getSubject(), kind, q));
  }

  @GetMapping("/content/{id}")
  public Object detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    return contents.detail(jwt.getSubject(), id);
  }

  @PutMapping("/content/{id}")
  public Object save(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String id,
      @RequestBody ContentService.SaveRequest request) {
    return contents.save(jwt.getSubject(), id, request);
  }

  @PostMapping("/word-libraries/custom-jobs")
  @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
  public Object create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody ContentJobs.Create request) {
    return jobs.create(jwt.getSubject(), key, request);
  }

  @GetMapping("/word-libraries/custom-jobs")
  public Object jobs(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("items", jobs.list(jwt.getSubject()));
  }

  @GetMapping("/word-libraries/custom-jobs/{id}")
  public Object job(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    return jobs.get(jwt.getSubject(), id);
  }

  @GetMapping("/word-libraries/custom-jobs/{id}/items")
  public Object jobItems(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    return jobs.details(jwt.getSubject(), id);
  }

  @PostMapping("/word-libraries/custom-jobs/{id}/retry")
  public Object retry(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    return jobs.retry(jwt.getSubject(), id);
  }

  @PostMapping("/word-libraries/{id}/study")
  public Object study(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    return contents.study(jwt.getSubject(), id);
  }

  public record Answer(String questionId, String selectedOptionId) {}

  @PostMapping("/content-study/{id}/answers")
  public Object answer(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestBody Answer request) {
    return contents.answer(jwt.getSubject(), id, request.questionId(), request.selectedOptionId());
  }

  @PostMapping("/content-study/{id}/words/{word}/complete")
  public Object complete(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String id, @PathVariable String word) {
    return contents.completeWord(jwt.getSubject(), id, word);
  }
}
