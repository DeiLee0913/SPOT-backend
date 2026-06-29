package com.spot.api.dto;

import com.spot.domain.todo.TodoCategory;
import com.spot.domain.todo.TodoItem;
import com.spot.domain.todo.TodoTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class TodoDtos {

    private TodoDtos() {
    }

    public record QuickCreateRequest(
        @NotBlank(message = "할 일 제목을 입력해주세요.") String title
    ) {
    }

    public record CreateTodoRequest(
        @NotBlank(message = "할 일 제목을 입력해주세요.") String title,
        Long categoryId,
        List<Long> tagIds,
        @Min(1) @Max(4) Integer priority,
        LocalDate dueStudyDay
    ) {
    }

    public record UpdateTodoRequest(
        String title,
        Long categoryId,
        Boolean clearCategory,
        List<Long> tagIds,
        @Min(1) @Max(4) Integer priority,
        LocalDate dueStudyDay,
        Boolean clearDue
    ) {
    }

    public record CreateCategoryRequest(
        @NotBlank String name,
        String color
    ) {
    }

    public record CreateTagRequest(
        @NotBlank String name
    ) {
    }

    public record UpdateCategoryRequest(
        String name,
        String color
    ) {
    }

    public record UpdateTagRequest(
        String name
    ) {
    }

    public record CategoryResponse(Long categoryId, String name, String color) {
        public static CategoryResponse from(TodoCategory category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getColor());
        }
    }

    public record TagResponse(Long tagId, String name) {
        public static TagResponse from(TodoTag tag) {
            return new TagResponse(tag.getId(), tag.getName());
        }
    }

    public record TodoItemResponse(
        Long todoId,
        String title,
        CategoryResponse category,
        List<TagResponse> tags,
        Integer priority,
        LocalDate dueStudyDay,
        String status,
        Instant doneAt,
        Instant createdAt
    ) {
        public static TodoItemResponse from(TodoItem item) {
            CategoryResponse category = item.getCategory() == null
                ? null
                : CategoryResponse.from(item.getCategory());
            List<TagResponse> tags = item.getTags().stream()
                .map(TagResponse::from)
                .toList();
            return new TodoItemResponse(
                item.getId(),
                item.getTitle(),
                category,
                tags,
                item.getPriority(),
                item.getDueStudyDay(),
                item.getStatus().name(),
                item.getDoneAt(),
                item.getCreatedAt()
            );
        }
    }

    public record TodoDayResponse(
        LocalDate studyDay,
        List<TodoItemResponse> today,
        List<TodoItemResponse> undated,
        List<TodoItemResponse> outdated,
        List<TodoItemResponse> done
    ) {
    }

    public record LinkTodoRequest(Long todoId) {
    }
}
