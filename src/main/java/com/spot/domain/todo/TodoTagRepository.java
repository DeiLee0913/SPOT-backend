package com.spot.domain.todo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoTagRepository extends JpaRepository<TodoTag, Long> {

    List<TodoTag> findByUserIdOrderByNameAsc(Long userId);

    Optional<TodoTag> findByIdAndUserId(Long id, Long userId);

    Optional<TodoTag> findByUserIdAndName(Long userId, String name);
}
