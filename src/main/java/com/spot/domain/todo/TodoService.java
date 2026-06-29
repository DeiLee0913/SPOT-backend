package com.spot.domain.todo;

import com.spot.api.dto.TodoDtos.TodoItemResponse;
import com.spot.common.BadRequestException;
import com.spot.common.ConflictException;
import com.spot.common.NotFoundException;
import com.spot.common.StudyDayService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TodoService {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_NAME_LENGTH = 50;
    private static final String DEFAULT_CATEGORY_COLOR = "#64748B";

    private final TodoItemRepository todoItemRepository;
    private final TodoCategoryRepository categoryRepository;
    private final TodoTagRepository tagRepository;
    private final StudyDayService studyDayService;

    public TodoService(
        TodoItemRepository todoItemRepository,
        TodoCategoryRepository categoryRepository,
        TodoTagRepository tagRepository,
        StudyDayService studyDayService
    ) {
        this.todoItemRepository = todoItemRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.studyDayService = studyDayService;
    }

    @Transactional(readOnly = true)
    public TodoDayView listForStudyDay(Long userId, LocalDate studyDay) {
        List<TodoItem> today = sortItems(
            todoItemRepository.findByUserIdAndStatusAndDueStudyDay(userId, TodoItemStatus.OPEN, studyDay));
        List<TodoItem> undated = sortItems(
            todoItemRepository.findByUserIdAndStatusAndDueStudyDayIsNull(userId, TodoItemStatus.OPEN));
        List<TodoItem> outdated = sortItems(
            todoItemRepository.findByUserIdAndStatusAndDueStudyDayBefore(userId, TodoItemStatus.OPEN, studyDay));
        List<TodoItem> done = sortItems(loadDoneForDay(userId, studyDay));

        return new TodoDayView(
            studyDay,
            today.stream().map(TodoItemResponse::from).toList(),
            undated.stream().map(TodoItemResponse::from).toList(),
            outdated.stream().map(TodoItemResponse::from).toList(),
            done.stream().map(TodoItemResponse::from).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<TodoItemResponse> listPickerForToday(Long userId) {
        LocalDate today = studyDayService.currentStudyDay();
        List<TodoItem> items = new ArrayList<>();
        items.addAll(todoItemRepository.findByUserIdAndStatusAndDueStudyDay(userId, TodoItemStatus.OPEN, today));
        items.addAll(todoItemRepository.findByUserIdAndStatusAndDueStudyDayIsNull(userId, TodoItemStatus.OPEN));
        items.addAll(todoItemRepository.findByUserIdAndStatusAndDueStudyDayBefore(userId, TodoItemStatus.OPEN, today));
        return sortItems(items).stream().map(TodoItemResponse::from).toList();
    }

    @Transactional
    public TodoItem quickCreate(Long userId, String rawTitle) {
        String title = validateTitle(rawTitle);
        return todoItemRepository.save(new TodoItem(
            userId,
            title,
            studyDayService.currentStudyDay()
        ));
    }

    @Transactional
    public TodoItem create(
        Long userId,
        String rawTitle,
        Long categoryId,
        List<Long> tagIds,
        Integer priority,
        LocalDate dueStudyDay
    ) {
        String title = validateTitle(rawTitle);
        validatePriority(priority);
        TodoItem item = new TodoItem(userId, title, dueStudyDay);
        item.setPriority(priority);
        applyCategory(userId, item, categoryId);
        applyTags(userId, item, tagIds);
        return todoItemRepository.save(item);
    }

    @Transactional
    public TodoItem update(
        Long userId,
        Long todoId,
        String rawTitle,
        Long categoryId,
        List<Long> tagIds,
        Integer priority,
        LocalDate dueStudyDay,
        boolean clearCategory,
        boolean clearDue
    ) {
        TodoItem item = getOwned(userId, todoId);
        if (rawTitle != null) {
            item.setTitle(validateTitle(rawTitle));
        }
        if (clearCategory) {
            item.assignCategory(null);
        } else if (categoryId != null) {
            applyCategory(userId, item, categoryId);
        }
        if (clearDue) {
            item.setDueStudyDay(null);
        } else if (dueStudyDay != null) {
            item.setDueStudyDay(dueStudyDay);
        }
        if (tagIds != null) {
            applyTags(userId, item, tagIds);
            validatePriority(priority);
            item.setPriority(priority);
        } else if (priority != null) {
            validatePriority(priority);
            item.setPriority(priority);
        }
        return getOwned(userId, todoId);
    }

    @Transactional
    public TodoItem complete(Long userId, Long todoId) {
        TodoItem item = getOwned(userId, todoId);
        if (item.getStatus() == TodoItemStatus.DONE) {
            return item;
        }
        item.complete(studyDayService.now());
        return item;
    }

    @Transactional
    public TodoItem reopen(Long userId, Long todoId) {
        TodoItem item = getOwned(userId, todoId);
        item.reopen();
        return item;
    }

    @Transactional
    public TodoItem rescheduleToday(Long userId, Long todoId) {
        TodoItem item = getOwned(userId, todoId);
        item.rescheduleTo(studyDayService.currentStudyDay());
        return item;
    }

    @Transactional
    public TodoItem getOwned(Long userId, Long todoId) {
        return todoItemRepository.findByIdAndUserId(todoId, userId)
            .orElseThrow(() -> new NotFoundException("TODO_NOT_FOUND", "할 일을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public String resolveTitle(Long todoId) {
        if (todoId == null) {
            return null;
        }
        return todoItemRepository.findById(todoId).map(TodoItem::getTitle).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<TodoCategory> listCategories(Long userId) {
        return categoryRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional
    public TodoCategory createCategory(Long userId, String rawName, String color) {
        String name = validateName(rawName);
        if (categoryRepository.findByUserIdAndName(userId, name).isPresent()) {
            throw new ConflictException("CATEGORY_ALREADY_EXISTS", "이미 있는 카테고리입니다.");
        }
        return categoryRepository.save(new TodoCategory(userId, name, normalizeColor(color)));
    }

    @Transactional(readOnly = true)
    public List<TodoTag> listTags(Long userId) {
        return tagRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional
    public TodoTag createTag(Long userId, String rawName) {
        String name = validateName(rawName);
        return tagRepository.findByUserIdAndName(userId, name)
            .orElseGet(() -> tagRepository.save(new TodoTag(userId, name)));
    }

    private List<TodoItem> loadDoneForDay(Long userId, LocalDate studyDay) {
        Instant dayStart = studyDay.atTime(StudyDayService.RESET_HOUR, 0).atZone(StudyDayService.KST).toInstant();
        Instant dayEnd = studyDay.plusDays(1).atTime(StudyDayService.RESET_HOUR, 0).atZone(StudyDayService.KST).toInstant();
        return todoItemRepository.findDoneForStudyDay(userId, studyDay, dayStart, dayEnd);
    }

    private List<TodoItem> sortItems(List<TodoItem> items) {
        return items.stream()
            .sorted(Comparator
                .comparing(TodoItem::getPriority, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoItem::getDueStudyDay, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoItem::getCreatedAt))
            .toList();
    }

    private void applyCategory(Long userId, TodoItem item, Long categoryId) {
        if (categoryId == null) {
            item.assignCategory(null);
            return;
        }
        TodoCategory category = categoryRepository.findByIdAndUserId(categoryId, userId)
            .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
        item.assignCategory(category);
    }

    private void applyTags(Long userId, TodoItem item, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        Set<TodoTag> tags = new HashSet<>();
        for (Long tagId : tagIds) {
            TodoTag tag = tagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new NotFoundException("TAG_NOT_FOUND", "태그를 찾을 수 없습니다."));
            tags.add(tag);
        }
        item.replaceTags(tags);
    }

    private String validateTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (!StringUtils.hasText(title)) {
            throw new BadRequestException("TITLE_REQUIRED", "할 일 제목을 입력해주세요.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("TITLE_TOO_LONG", "제목은 200자 이하여야 합니다.");
        }
        return title;
    }

    private String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (!StringUtils.hasText(name)) {
            throw new BadRequestException("NAME_REQUIRED", "이름을 입력해주세요.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("NAME_TOO_LONG", "이름은 50자 이하여야 합니다.");
        }
        return name;
    }

    private void validatePriority(Integer priority) {
        if (priority == null) {
            return;
        }
        if (priority < 1 || priority > 4) {
            throw new BadRequestException("INVALID_PRIORITY", "우선순위는 1~4 사이여야 합니다.");
        }
    }

    private String normalizeColor(String color) {
        if (!StringUtils.hasText(color)) {
            return DEFAULT_CATEGORY_COLOR;
        }
        return color.trim();
    }

    public record TodoDayView(
        LocalDate studyDay,
        List<TodoItemResponse> today,
        List<TodoItemResponse> undated,
        List<TodoItemResponse> outdated,
        List<TodoItemResponse> done
    ) {
    }
}
