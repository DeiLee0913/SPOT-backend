package com.spot.scheduler;

import com.spot.common.StudyDayService;
import com.spot.domain.goal.GoalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyGoalScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyGoalScheduler.class);

    private final GoalService goalService;
    private final StudyDayService studyDayService;

    public DailyGoalScheduler(GoalService goalService, StudyDayService studyDayService) {
        this.goalService = goalService;
        this.studyDayService = studyDayService;
    }

    /**
     * 매일 11:00 KST — 일일 목표 미설정 사용자에게 DEFAULT 목표를 확정한다.
     */
    @Scheduled(cron = "0 0 11 * * *", zone = "Asia/Seoul")
    public void applyDefaultGoals() {
        int created = goalService.applyDefaultGoals(studyDayService.currentStudyDay());
        log.info("[ApplyDefaultGoals] studyDay={} created={}", studyDayService.currentStudyDay(), created);
    }
}
