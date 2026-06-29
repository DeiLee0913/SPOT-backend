package com.spot.domain.todo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    @EntityGraph(attributePaths = {"category", "tags"})
    Optional<TodoItem> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"category", "tags"})
    List<TodoItem> findByUserIdAndStatusAndDueStudyDay(Long userId, TodoItemStatus status, LocalDate dueStudyDay);

    @EntityGraph(attributePaths = {"category", "tags"})
    List<TodoItem> findByUserIdAndStatusAndDueStudyDayIsNull(Long userId, TodoItemStatus status);

    @EntityGraph(attributePaths = {"category", "tags"})
    List<TodoItem> findByUserIdAndStatusAndDueStudyDayBefore(Long userId, TodoItemStatus status, LocalDate before);

    @EntityGraph(attributePaths = {"category", "tags"})
    @Query("""
        select t from TodoItem t
        where t.userId = :userId
          and t.status = com.spot.domain.todo.TodoItemStatus.DONE
          and (
            t.dueStudyDay = :studyDay
            or (t.dueStudyDay is null and t.doneAt >= :dayStart and t.doneAt < :dayEnd)
          )
        """)
    List<TodoItem> findDoneForStudyDay(
        @Param("userId") Long userId,
        @Param("studyDay") LocalDate studyDay,
        @Param("dayStart") Instant dayStart,
        @Param("dayEnd") Instant dayEnd
    );

    List<TodoItem> findByIdIn(Collection<Long> ids);
}
