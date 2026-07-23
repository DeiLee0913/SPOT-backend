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
 * - 일자 전환: 계정별 {@code studyDayResetHour} (기본 06:00 KST)
 * - 일일 목표 마감: 해당 study day 달력일 11:00 KST
 * - 주간: 월요일~일요일
 */
@Service
public class StudyDayService {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 기본 일자 전환 시각 (하위 호환·미설정 계정). */
    public static final int RESET_HOUR = 6;
    public static final int MIN_RESET_HOUR = 0;
    public static final int MAX_RESET_HOUR = 23;
    public static final int GOAL_DEADLINE_HOUR = 11;

    private final Clock clock;

    public StudyDayService(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    /** 기본 리셋(06:00) 기준. 사용자 컨텍스트가 없을 때만 사용. */
    public LocalDate currentStudyDay() {
        return currentStudyDay(RESET_HOUR);
    }

    public LocalDate currentStudyDay(int resetHour) {
        return toStudyDay(now(), resetHour);
    }

    /** 기본 리셋(06:00) 기준. */
    public LocalDate toStudyDay(Instant instant) {
        return toStudyDay(instant, RESET_HOUR);
    }

    public LocalDate toStudyDay(Instant instant, int resetHour) {
        return instant.atZone(KST).minusHours(normalizeResetHour(resetHour)).toLocalDate();
    }

    public Instant studyDayStart(LocalDate studyDay, int resetHour) {
        return studyDay.atTime(normalizeResetHour(resetHour), 0).atZone(KST).toInstant();
    }

    public Instant studyDayEndExclusive(LocalDate studyDay, int resetHour) {
        return studyDayStart(studyDay.plusDays(1), resetHour);
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

    public static int normalizeResetHour(int resetHour) {
        if (resetHour < MIN_RESET_HOUR || resetHour > MAX_RESET_HOUR) {
            throw new BadRequestException(
                "INVALID_RESET_HOUR",
                "일자 전환 시각은 0~23시(KST)여야 합니다."
            );
        }
        return resetHour;
    }
}
