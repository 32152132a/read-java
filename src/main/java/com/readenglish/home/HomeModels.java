package com.readenglish.home;

import com.readenglish.learningflow.LearningFlowModels.FlowNodeResponse;
import java.util.List;

public final class HomeModels {

  private HomeModels() {}

  public record HomeUser(String nickname) {}

  public record TodayTask(
      String flowNodeId,
      String templateCode,
      String title,
      String description,
      int estimatedMinutes,
      String route,
      int current,
      int total) {}

  public record QuickAction(String code, String name, String route) {}

  public record HomeResponse(
      HomeUser user,
      String greeting,
      String subtitle,
      TodayTask todayTask,
      List<FlowNodeResponse> learningPath,
      List<QuickAction> quickActions,
      boolean flowCompleted) {}
}
