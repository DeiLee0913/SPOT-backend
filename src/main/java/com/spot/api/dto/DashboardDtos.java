package com.spot.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardResponse(LocalDate studyDay, List<MemberDashboard> members) {
    }

    public record MemberDashboard(
        Long userId,
        String nickname,
        int todayMinutes,
        Integer goalMinutes,
        double rawAchievementRate,
        double displayAchievementRate,
        long weeklyScore,
        int weeklyRank,
        ScoreBreakdown weeklyScoreBreakdown,
        ScoreRange weeklyScoreRange,
        List<HistoryDay> history,
        List<DashboardSession> sessions
    ) {
    }

    public record ScoreBreakdown(long achievementPoints, long volumeBonus) {
    }

    public record ScoreRange(LocalDate from, LocalDate to) {
    }

    public record HistoryDay(LocalDate studyDay, int actualMinutes, Integer goalMinutes) {
    }

    public record DashboardSession(
        Long sessionId,
        String category,
        Instant startedAt,
        Instant endedAt,
        Integer durationMinutes
    ) {
    }
}
