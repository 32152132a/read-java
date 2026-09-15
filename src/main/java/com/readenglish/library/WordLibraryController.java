package com.readenglish.library;

import com.readenglish.library.WordLibraryModels.AddLibrariesRequest;
import com.readenglish.library.WordLibraryModels.AddLibrariesResponse;
import com.readenglish.library.WordLibraryModels.LibraryDetailResponse;
import com.readenglish.library.WordLibraryModels.LibraryListResponse;
import com.readenglish.library.WordLibraryModels.LibraryWordPage;
import com.readenglish.library.WordLibraryModels.RemoveLibraryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/word-libraries")
public class WordLibraryController {

  private final WordLibraryService wordLibraryService;

  public WordLibraryController(WordLibraryService wordLibraryService) {
    this.wordLibraryService = wordLibraryService;
  }

  @GetMapping("/mine")
  public LibraryListResponse mine(@AuthenticationPrincipal Jwt jwt) {
    return wordLibraryService.mine(jwt.getSubject());
  }

  @GetMapping("/available")
  public LibraryListResponse available(@AuthenticationPrincipal Jwt jwt) {
    return wordLibraryService.available(jwt.getSubject());
  }

  @PostMapping("/mine")
  public AddLibrariesResponse add(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddLibrariesRequest request) {
    return wordLibraryService.add(jwt.getSubject(), request);
  }

  @DeleteMapping("/mine/{libraryId}")
  public RemoveLibraryResponse remove(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String libraryId) {
    return wordLibraryService.remove(jwt.getSubject(), libraryId);
  }

  @GetMapping("/{libraryId}")
  public LibraryDetailResponse get(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String libraryId) {
    return wordLibraryService.get(jwt.getSubject(), libraryId);
  }

  @GetMapping("/{libraryId}/words")
  public LibraryWordPage words(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String libraryId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return wordLibraryService.words(jwt.getSubject(), libraryId, cursor, size);
  }
}
