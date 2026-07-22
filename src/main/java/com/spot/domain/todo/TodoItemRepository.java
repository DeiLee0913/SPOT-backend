package com.spot.domain.todo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    @EntityGraph(attributePaths = {"category", "tags"})
    Optional<TodoItem> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"category", "tags"})
    List<TodoItem> findByUserIdAndStatusAndStartDay(Long userId, TodoItemStatus status, LocalDate startDay);

    @EntityGraph(attributePaths = {"category", "tags"})
    List<TodoItem> findByUserIdAndStatusAndStartDayIsNull(Long userId, TodoItemStatus status);

    @EntityGraph(attributePaths = {"category", "tags"})
    List<TodoItem> findByUserIdAndStatusAndStartDayBefore(Long userId, TodoItemStatus status, LocalDate before);

    @EntityGraph(attributePaths = {"category", "tags"})
    @Query("""
        select t from TodoItem t
        where t.userId = :userId
          and t.status = com.spot.domain.todo.TodoItemStatus.DONE
          and (
            t.startDay = :studyDay
            or (t.startDay is null and t.doneAt >= :dayStart and t.doneAt < :dayEnd)
          )
        """)
    List<TodoItem> findDoneForStudyDay(
        @Param("userId") Long userId,
        @Param("studyDay") LocalDate studyDay,
        @Param("dayStart") Instant dayStart,
        @Param("dayEnd") Instant dayEnd
    );

    @EntityGraph(attributePaths = {"category", "tags"})
    List<TodoItem> findByUserIdAndStatus(Long userId, TodoItemStatus status);

    @EntityGraph(attributePaths = {"category", "tags"})
    List<TodoItem> findByUserIdAndStatusAndStartDayBetween(
        Long userId,
        TodoItemStatus status,
        LocalDate from,
        LocalDate to
    );

    @EntityGraph(attributePaths = {"category", "tags"})
    @Query("""
        select distinct t from TodoItem t
        left join t.category c
        left join t.tags tag
        where t.userId = :userId
          and (:status is null or t.status = :status)
          and (:uncategorized = false or t.category is null)
          and (:categoryId is null or c.id = :categoryId)
          and (:untagged = false or t.tags is empty)
          and (:tagId is null or tag.id = :tagId)
          and (
            (:startFrom is null and :startTo is null)
            or (
              t.startDay is not null
              and (:startFrom is null or t.startDay >= :startFrom)
              and (:startTo is null or t.startDay <= :startTo)
            )
          )
          and (
            :q is null
            or lower(t.title) like lower(concat('%', :q, '%'))
            or lower(t.description) like lower(concat('%', :q, '%'))
            or lower(c.name) like lower(concat('%', :q, '%'))
            or lower(tag.name) like lower(concat('%', :q, '%'))
          )
        """)
    List<TodoItem> search(
        @Param("userId") Long userId,
        @Param("q") String q,
        @Param("status") TodoItemStatus status,
        @Param("categoryId") Long categoryId,
        @Param("uncategorized") boolean uncategorized,
        @Param("tagId") Long tagId,
        @Param("untagged") boolean untagged,
        @Param("startFrom") LocalDate startFrom,
        @Param("startTo") LocalDate startTo
    );

    @EntityGraph(attributePaths = {"category", "tags"})
    List<TodoItem> findByIdIn(Collection<Long> ids);

    List<TodoItem> findByUserIdAndCategory_Id(Long userId, Long categoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update TodoItem t
        set t.category = null
        where t.userId = :userId and t.category.id = :categoryId
        """)
    void clearCategory(@Param("userId") Long userId, @Param("categoryId") Long categoryId);

    @Query("""
        select distinct t from TodoItem t join t.tags tag
        where t.userId = :userId and tag.id = :tagId
        """)
    List<TodoItem> findByUserIdAndTagId(@Param("userId") Long userId, @Param("tagId") Long tagId);
}
