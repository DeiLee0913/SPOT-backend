package com.spot.common;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Service;

/**
 * KST 기준 study day 규칙.
 * - 일자 전환: 매일 06:00 KST
 * - 일일 목표 마감: 10:00 KST
 * - 주간: 월요일~일요일
 */
@Service
public class StudyDayService {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final int RESET_HOUR = 6;
    public static final int GOAL_DEADLINE_HOUR = 10;

    private final Clock clock;

    public StudyDayService(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate currentStudyDay() {
        return toStudyDay(now());
    }

    public LocalDate toStudyDay(Instant instant) {
        return instant.atZone(KST).minusHours(RESET_HOUR).toLocalDate();
    }

    public boolean isAfterGoalDeadline(LocalDate studyDay) {
        ZonedDateTime deadline = studyDay.atTime(GOAL_DEADLINE_HOUR, 0).atZone(KST);
        return !now().atZone(KST).isBefore(deadline);
    }

    public LocalDate weekMonday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public LocalDate weekSunday(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }
}
