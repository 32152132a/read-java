package com.readenglish.phoneme;

import com.readenglish.phoneme.PhonemeModels.PhonemeDetailResponse;
import com.readenglish.phoneme.PhonemeModels.PhonemeListResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/phonemes")
public class PhonemeController {

  private final PhonemeService phonemeService;

  public PhonemeController(PhonemeService phonemeService) {
    this.phonemeService = phonemeService;
  }

  @GetMapping
  public PhonemeListResponse list(@RequestParam(required = false) String group) {
    return phonemeService.list(group);
  }

  @GetMapping("/{id}")
  public PhonemeDetailResponse get(@PathVariable String id) {
    return phonemeService.get(id);
  }
}
