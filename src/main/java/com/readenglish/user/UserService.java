package com.readenglish.user;

import com.readenglish.auth.UserProfile;
import com.readenglish.common.api.ApiException;
import com.readenglish.learningflow.LearningFlowService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final LearningFlowService learningFlowService;

  public UserService(UserRepository userRepository, LearningFlowService learningFlowService) {
    this.userRepository = userRepository;
    this.learningFlowService = learningFlowService;
  }

  @Transactional
  public UserResult findOrCreateDevUser(String nickname) {
    var existing = userRepository.findById("dev-user");
    if (existing.isPresent()) {
      UserEntity user = existing.orElseThrow();
      user.updateNickname(nickname);
      return new UserResult(toProfile(user), false);
    }

    var user = userRepository.save(new UserEntity("dev-user", nickname));
    return new UserResult(toProfile(user), true);
  }

  @Transactional
  public UserResult findOrCreateWechatUser(String openid, String unionid) {
    var existing = userRepository.findByWechatOpenid(openid);
    if (existing.isPresent()) {
      return new UserResult(toProfile(existing.orElseThrow()), false);
    }
    String userId = "u_" + UUID.randomUUID().toString().replace("-", "");
    var user = userRepository.save(new UserEntity(userId, openid, unionid, "微信用户"));
    return new UserResult(toProfile(user), true);
  }

  @Transactional(readOnly = true)
  public UserProfile getProfile(String userId) {
    return toProfile(findRequired(userId));
  }

  @Transactional
  public UserProfile updateAccent(String userId, String accent) {
    UserEntity user = findRequired(userId);
    user.updateAccent(accent);
    return toProfile(user);
  }

  @Transactional(readOnly = true)
  public UserStats getStats(String userId) {
    findRequired(userId);
    var flow = learningFlowService.getCurrentFlow(userId);
    int completed = (int) flow.nodes().stream().filter(node -> node.reviewable()).count();
    return new UserStats(completed, flow.nodes().size(), 0, completed, 0);
  }

  private UserEntity findRequired(String userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "用户不存在"));
  }

  private static UserProfile toProfile(UserEntity user) {
    return new UserProfile(user.getId(), user.getNickname(), user.getAvatarUrl(), user.getAccent());
  }

  public record UserResult(UserProfile profile, boolean newUser) {}
}
