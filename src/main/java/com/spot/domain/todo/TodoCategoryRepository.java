package com.spot.domain.todo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoCategoryRepository extends JpaRepository<TodoCategory, Long> {

    List<TodoCategory> findByUserIdOrderByNameAsc(Long userId);

    Optional<TodoCategory> findByIdAndUserId(Long id, Long userId);

    Optional<TodoCategory> findByUserIdAndName(Long userId, String name);
}
