package com.spot.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class StudyDayServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 27);

    @Test
    void goalDeadlineIsTenAmKst() {
        Clock atNineFiftyNine = Clock.fixed(Instant.parse("2026-06-27T00:59:00Z"), ZoneOffset.UTC); // 09:59 KST
        assertThat(new StudyDayService(atNineFiftyNine).isAfterGoalDeadline(TODAY)).isFalse();

        Clock atTen = Clock.fixed(Instant.parse("2026-06-27T01:00:00Z"), ZoneOffset.UTC); // 10:00 KST
        assertThat(new StudyDayService(atTen).isAfterGoalDeadline(TODAY)).isTrue();
    }
}
