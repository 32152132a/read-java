package com.readenglish.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, String> {

  Optional<UserEntity> findByWechatOpenid(String wechatOpenid);

  @Query(
      """
      select node.completedAt from LearningFlowRunNodeEntity node, LearningFlowRunEntity run
      where node.runId = run.id and run.userId = :userId and node.status = 'COMPLETED'
      """)
  List<Instant> findStageCompletionTimes(@Param("userId") String userId);

  @Query(
      """
      select count(unit) from LearningUnitEntity unit where unit.enabled = true and exists (
        select node.id from LearningFlowRunNodeEntity node, LearningFlowRunEntity run
        where node.runId = run.id and run.userId = :userId
          and node.status = 'COMPLETED' and node.templateCode = unit.templateCode
      )
      """)
  long countLearnedUnits(@Param("userId") String userId);

  @Query("select count(unit) from LearningUnitEntity unit where unit.enabled = true")
  long countTotalUnits();
}
