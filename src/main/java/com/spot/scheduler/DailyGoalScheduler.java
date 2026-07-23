package com.spot.scheduler;

import com.spot.domain.goal.GoalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyGoalScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyGoalScheduler.class);

    private final GoalService goalService;

    public DailyGoalScheduler(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * 매일 11:00 KST — 계정별 현재 study day에 일일 목표가 없으면 DEFAULT를 확정한다.
     */
    @Scheduled(cron = "0 0 11 * * *", zone = "Asia/Seoul")
    public void applyDefaultGoals() {
        int created = goalService.applyDefaultGoalsForAllUsers();
        log.info("[ApplyDefaultGoals] created={}", created);
    }
}
