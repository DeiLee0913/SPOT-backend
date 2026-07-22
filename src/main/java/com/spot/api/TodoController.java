package com.spot.api;

import com.spot.api.dto.TodoDtos.CategoryResponse;
import com.spot.api.dto.TodoDtos.CreateCategoryRequest;
import com.spot.api.dto.TodoDtos.CreateTagRequest;
import com.spot.api.dto.TodoDtos.CreateTodoRequest;
import com.spot.api.dto.TodoDtos.QuickCreateRequest;
import com.spot.api.dto.TodoDtos.TagResponse;
import com.spot.api.dto.TodoDtos.TodoBoardResponse;
import com.spot.api.dto.TodoDtos.TodoDayResponse;
import com.spot.api.dto.TodoDtos.TodoItemResponse;
import com.spot.api.dto.TodoDtos.TodoSearchResponse;
import com.spot.api.dto.TodoDtos.UpdateCategoryRequest;
import com.spot.api.dto.TodoDtos.UpdateTagRequest;
import com.spot.api.dto.TodoDtos.UpdateTodoRequest;
import com.spot.auth.AuthenticatedUser;
import com.spot.auth.CurrentUser;
import com.spot.common.ApiResponse;
import com.spot.common.StudyDayService;
import com.spot.domain.todo.TodoCategory;
import com.spot.domain.todo.TodoItem;
import com.spot.domain.todo.TodoService;
import com.spot.domain.todo.TodoService.TodoDayView;
import com.spot.domain.todo.TodoTag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/todos")
public class TodoController {

    private final TodoService todoService;
    private final StudyDayService studyDayService;

    public TodoController(TodoService todoService, StudyDayService studyDayService) {
        this.todoService = todoService;
        this.studyDayService = studyDayService;
    }

    @GetMapping
    public ApiResponse<TodoDayResponse> list(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDay
    ) {
        LocalDate day = studyDay != null ? studyDay : studyDayService.currentStudyDay();
        TodoDayView view = todoService.listForStudyDay(currentUser.userId(), day);
        return ApiResponse.ok(toDayResponse(view));
    }

    @GetMapping("/picker")
    public ApiResponse<List<TodoItemResponse>> picker(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam(required = false) String q
    ) {
        return ApiResponse.ok(todoService.listPickerForToday(currentUser.userId(), q));
    }

    @GetMapping("/search")
    public ApiResponse<TodoSearchResponse> search(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam(required = false) String q,
        @RequestParam(required = false, defaultValue = "ALL") String status,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long tagId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Long cursor
    ) {
        return ApiResponse.ok(todoService.search(
            currentUser.userId(),
            q,
            status,
            categoryId,
            tagId,
            startFrom,
            startTo,
            limit,
            cursor
        ));
    }

    @GetMapping("/board")
    public ApiResponse<TodoBoardResponse> board(
        @CurrentUser AuthenticatedUser currentUser,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long tagId
    ) {
        return ApiResponse.ok(todoService.board(
            currentUser.userId(),
            from,
            to,
            categoryId,
            tagId
        ));
    }

    @PostMapping("/quick")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TodoItemResponse> quickCreate(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody QuickCreateRequest request
    ) {
        TodoItem item = todoService.quickCreate(currentUser.userId(), request.title());
        return ApiResponse.ok(TodoItemResponse.from(item));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TodoItemResponse> create(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody CreateTodoRequest request
    ) {
        TodoItem item = todoService.create(
            currentUser.userId(),
            request.title(),
            request.description(),
            request.categoryId(),
            request.tagIds(),
            request.priority(),
            request.startDay(),
            request.startTime(),
            request.endTime(),
            request.endDay()
        );
        return ApiResponse.ok(TodoItemResponse.from(item));
    }

    @PatchMapping("/{todoId}")
    public ApiResponse<TodoItemResponse> update(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long todoId,
        @Valid @RequestBody UpdateTodoRequest request
    ) {
        TodoItem item = todoService.update(
            currentUser.userId(),
            todoId,
            request.title(),
            request.description(),
            request.categoryId(),
            request.tagIds(),
            request.priority(),
            request.startDay(),
            Boolean.TRUE.equals(request.clearCategory()),
            Boolean.TRUE.equals(request.clearStartDay()),
            request.startTime(),
            request.endTime(),
            request.endDay(),
            Boolean.TRUE.equals(request.clearStartTime()),
            Boolean.TRUE.equals(request.clearEndTime()),
            Boolean.TRUE.equals(request.clearEndDay()),
            Boolean.TRUE.equals(request.clearDescription())
        );
        return ApiResponse.ok(TodoItemResponse.from(item));
    }

    @PostMapping("/{todoId}/complete")
    public ApiResponse<TodoItemResponse> complete(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long todoId
    ) {
        return ApiResponse.ok(TodoItemResponse.from(todoService.complete(currentUser.userId(), todoId)));
    }

    @PostMapping("/{todoId}/reopen")
    public ApiResponse<TodoItemResponse> reopen(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long todoId
    ) {
        return ApiResponse.ok(TodoItemResponse.from(todoService.reopen(currentUser.userId(), todoId)));
    }

    @PostMapping("/{todoId}/reschedule-today")
    public ApiResponse<TodoItemResponse> rescheduleToday(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long todoId
    ) {
        return ApiResponse.ok(TodoItemResponse.from(todoService.rescheduleToday(currentUser.userId(), todoId)));
    }

    @DeleteMapping("/{todoId}")
    public ApiResponse<Void> delete(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long todoId
    ) {
        todoService.delete(currentUser.userId(), todoId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{todoId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TodoItemResponse> duplicate(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long todoId
    ) {
        TodoItem item = todoService.duplicate(currentUser.userId(), todoId);
        return ApiResponse.ok(TodoItemResponse.from(item));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> listCategories(@CurrentUser AuthenticatedUser currentUser) {
        List<CategoryResponse> categories = todoService.listCategories(currentUser.userId()).stream()
            .map(CategoryResponse::from)
            .toList();
        return ApiResponse.ok(categories);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryResponse> createCategory(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody CreateCategoryRequest request
    ) {
        TodoCategory category = todoService.createCategory(
            currentUser.userId(),
            request.name(),
            request.color()
        );
        return ApiResponse.ok(CategoryResponse.from(category));
    }

    @PatchMapping("/categories/{categoryId}")
    public ApiResponse<CategoryResponse> updateCategory(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long categoryId,
        @Valid @RequestBody UpdateCategoryRequest request
    ) {
        TodoCategory category = todoService.updateCategory(
            currentUser.userId(),
            categoryId,
            request.name(),
            request.color()
        );
        return ApiResponse.ok(CategoryResponse.from(category));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long categoryId
    ) {
        todoService.deleteCategory(currentUser.userId(), categoryId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/tags")
    public ApiResponse<List<TagResponse>> listTags(@CurrentUser AuthenticatedUser currentUser) {
        List<TagResponse> tags = todoService.listTags(currentUser.userId()).stream()
            .map(TagResponse::from)
            .toList();
        return ApiResponse.ok(tags);
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TagResponse> createTag(
        @CurrentUser AuthenticatedUser currentUser,
        @Valid @RequestBody CreateTagRequest request
    ) {
        TodoTag tag = todoService.createTag(currentUser.userId(), request.name());
        return ApiResponse.ok(TagResponse.from(tag));
    }

    @PatchMapping("/tags/{tagId}")
    public ApiResponse<TagResponse> updateTag(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long tagId,
        @Valid @RequestBody UpdateTagRequest request
    ) {
        TodoTag tag = todoService.updateTag(currentUser.userId(), tagId, request.name());
        return ApiResponse.ok(TagResponse.from(tag));
    }

    @DeleteMapping("/tags/{tagId}")
    public ApiResponse<Void> deleteTag(
        @CurrentUser AuthenticatedUser currentUser,
        @PathVariable Long tagId
    ) {
        todoService.deleteTag(currentUser.userId(), tagId);
        return ApiResponse.ok(null);
    }

    private static TodoDayResponse toDayResponse(TodoDayView view) {
        return new TodoDayResponse(
            view.studyDay(),
            view.today(),
            view.undated(),
            view.outdated(),
            view.done()
        );
    }
}
