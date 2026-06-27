package com.spot.domain.goal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyGoalRepository extends JpaRepository<DailyGoal, Long> {

    Optional<DailyGoal> findByUserIdAndStudyDay(Long userId, LocalDate studyDay);

    List<DailyGoal> findByUserIdAndStudyDayBetween(Long userId, LocalDate from, LocalDate to);

    List<DailyGoal> findByStudyDay(LocalDate studyDay);
}
