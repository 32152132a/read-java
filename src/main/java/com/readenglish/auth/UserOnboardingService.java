package com.readenglish.auth;

import com.readenglish.integration.wechat.WechatLoginGateway;
import com.readenglish.learningflow.LearningFlowService;
import com.readenglish.library.WordLibraryService;
import com.readenglish.user.UserService;
import com.readenglish.user.UserService.UserResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserOnboardingService {

  private final UserService userService;
  private final LearningFlowService learningFlowService;
  private final WordLibraryService wordLibraryService;
  private final WechatLoginGateway wechatLoginGateway;

  public UserOnboardingService(
      UserService userService,
      LearningFlowService learningFlowService,
      WordLibraryService wordLibraryService,
      WechatLoginGateway wechatLoginGateway) {
    this.userService = userService;
    this.learningFlowService = learningFlowService;
    this.wordLibraryService = wordLibraryService;
    this.wechatLoginGateway = wechatLoginGateway;
  }

  @Transactional
  public UserResult initializeDevelopmentUser(String nickname) {
    UserResult result = userService.findOrCreateDevUser(nickname);
    learningFlowService.initializeForUser(result.profile().id());
    wordLibraryService.initializeForUser(result.profile().id());
    return result;
  }

  @Transactional
  public UserResult initializeWechatUser(String code) {
    var identity = wechatLoginGateway.exchange(code);
    UserResult result = userService.findOrCreateWechatUser(identity.openid(), identity.unionid());
    learningFlowService.initializeForUser(result.profile().id());
    wordLibraryService.initializeForUser(result.profile().id());
    return result;
  }
}
