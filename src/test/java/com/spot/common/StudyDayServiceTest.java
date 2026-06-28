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
    void goalDeadlineIsElevenAmKst() {
        Clock atTenFiftyNine = Clock.fixed(Instant.parse("2026-06-27T01:59:00Z"), ZoneOffset.UTC); // 10:59 KST
        assertThat(new StudyDayService(atTenFiftyNine).isAfterGoalDeadline(TODAY)).isFalse();

        Clock atEleven = Clock.fixed(Instant.parse("2026-06-27T02:00:00Z"), ZoneOffset.UTC); // 11:00 KST
        assertThat(new StudyDayService(atEleven).isAfterGoalDeadline(TODAY)).isTrue();
    }
}
