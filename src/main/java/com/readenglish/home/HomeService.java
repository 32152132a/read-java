package com.readenglish.home;

import com.readenglish.auth.UserProfile;
import com.readenglish.home.HomeModels.HomeResponse;
import com.readenglish.home.HomeModels.HomeUser;
import com.readenglish.home.HomeModels.QuickAction;
import com.readenglish.home.HomeModels.TodayTask;
import com.readenglish.learningcontent.LearningStageService;
import com.readenglish.learningflow.LearningFlowModels.CurrentFlow;
import com.readenglish.learningflow.LearningFlowModels.FlowNodeResponse;
import com.readenglish.learningflow.LearningFlowService;
import com.readenglish.user.UserService;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomeService {

  private static final String SUBTITLE = "看到陌生单词，也能试着读出来";
  private static final Map<String, String> TASK_DESCRIPTIONS =
      Map.of(
          "phoneme", "观察口型和发音步骤，认识单个音标",
          "phoneme-compare", "对比声音和口型，分清易混音标",
          "phoneme-quiz", "听声音，辨认正确的音标",
          "syllable", "理解音素、音节和分节规则",
          "ipa-decoding", "根据音标主动拼读单词",
          "word-decoding", "对应字母块和音标片段",
          "evaluation", "录制发音并获取评测建议");

  private final UserService userService;
  private final LearningFlowService learningFlowService;
  private final LearningStageService learningStageService;

  public HomeService(
      UserService userService,
      LearningFlowService learningFlowService,
      LearningStageService learningStageService) {
    this.userService = userService;
    this.learningFlowService = learningFlowService;
    this.learningStageService = learningStageService;
  }

  @Transactional(readOnly = true)
  public HomeResponse get(String userId) {
    UserProfile user = userService.getProfile(userId);
    CurrentFlow flow = learningFlowService.getCurrentFlow(userId);
    boolean completed = flow.currentPosition() >= flow.nodes().size();
    TodayTask todayTask = completed ? null : toTodayTask(flow.nodes().get(flow.currentPosition()));
    return new HomeResponse(
        new HomeUser(user.nickname()),
        greeting(user.nickname()),
        SUBTITLE,
        todayTask,
        flow.nodes(),
        List.of(
            new QuickAction("phoneme-overview", "音标总览", "/pages/phoneme/overview"),
            new QuickAction("word-library", "专业词库", "/pages/word-list/index")),
        completed);
  }

  private TodayTask toTodayTask(FlowNodeResponse node) {
    int total = learningStageService.countUnits(node.templateCode());
    return new TodayTask(
        node.id(),
        node.templateCode(),
        node.name(),
        TASK_DESCRIPTIONS.getOrDefault(node.templateCode(), "继续今天的发音学习"),
        Math.max(3, total * 3),
        node.route(),
        total == 0 ? 0 : 1,
        total);
  }

  private static String greeting(String nickname) {
    int hour = LocalTime.now(ZoneId.of("Asia/Shanghai")).getHour();
    String period = hour < 6 ? "夜深了" : hour < 12 ? "上午好" : hour < 18 ? "下午好" : "晚上好";
    return period + "，" + nickname;
  }
}
