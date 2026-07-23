package com.spot.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StudyDayServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 27);

    @Test
    void goalDeadlineIsElevenAmKst() {
        Clock atTenFiftyNine = Clock.fixed(Instant.parse("2026-06-27T01:59:00Z"), ZoneOffset.UTC); // 10:59 KST
        assertThat(new StudyDayService(atTenFiftyNine).isAfterGoalDeadline(TODAY)).isFalse();

        Clock atEleven = Clock.fixed(Instant.parse("2026-06-27T02:00:00Z"), ZoneOffset.UTC); // 11:00 KST
        assertThat(new StudyDayService(atEleven).isAfterGoalDeadline(TODAY)).isTrue();
    }

    @Test
    void toStudyDayUsesAccountResetHour() {
        Clock atFiveThirty = Clock.fixed(Instant.parse("2026-06-26T20:30:00Z"), ZoneOffset.UTC); // 05:30 KST
        StudyDayService service = new StudyDayService(atFiveThirty);

        assertThat(service.toStudyDay(service.now(), 6)).isEqualTo(LocalDate.of(2026, 6, 26));
        assertThat(service.toStudyDay(service.now(), 0)).isEqualTo(LocalDate.of(2026, 6, 27));
        assertThat(service.currentStudyDay(5)).isEqualTo(LocalDate.of(2026, 6, 27));
    }

    @Test
    void studyDayBoundariesFollowResetHour() {
        StudyDayService service = new StudyDayService(Clock.systemUTC());
        LocalDate day = LocalDate.of(2026, 6, 27);

        assertThat(service.studyDayStart(day, 6)).isEqualTo(Instant.parse("2026-06-26T21:00:00Z"));
        assertThat(service.studyDayEndExclusive(day, 6)).isEqualTo(Instant.parse("2026-06-27T21:00:00Z"));
        assertThat(service.studyDayStart(day, 0)).isEqualTo(Instant.parse("2026-06-26T15:00:00Z"));
        assertThat(service.studyDayEndExclusive(day, 0)).isEqualTo(Instant.parse("2026-06-27T15:00:00Z"));
    }

    @Test
    void normalizeResetHourRejectsOutOfRange() {
        assertThatThrownBy(() -> StudyDayService.normalizeResetHour(-1))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> StudyDayService.normalizeResetHour(24))
            .isInstanceOf(BadRequestException.class);
        assertThat(StudyDayService.normalizeResetHour(0)).isZero();
        assertThat(StudyDayService.normalizeResetHour(23)).isEqualTo(23);
    }
}
