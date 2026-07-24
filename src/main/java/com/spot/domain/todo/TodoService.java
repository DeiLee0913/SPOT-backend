package com.spot.domain.todo;

import com.spot.api.dto.TodoDtos.TodoBoardCategoryBreakdown;
import com.spot.api.dto.TodoDtos.TodoBoardDayStats;
import com.spot.api.dto.TodoDtos.TodoBoardResponse;
import com.spot.api.dto.TodoDtos.TodoBoardSummary;
import com.spot.api.dto.TodoDtos.TodoBoardTagBreakdown;
import com.spot.api.dto.TodoDtos.TodoItemResponse;
import com.spot.api.dto.TodoDtos.TodoSearchResponse;
import com.spot.common.BadRequestException;
import com.spot.common.ConflictException;
import com.spot.common.NotFoundException;
import com.spot.common.StudyDayService;
import com.spot.domain.session.SessionStatus;
import com.spot.domain.session.StudySession;
import com.spot.domain.session.StudySessionRepository;
import com.spot.domain.user.User;
import com.spot.domain.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TodoService {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 10_000;
    public static final int MAX_NAME_LENGTH = 50;
    private static final int DEFAULT_SEARCH_LIMIT = 50;
    private static final int MAX_SEARCH_LIMIT = 100;
    private static final int MAX_BOARD_RANGE_DAYS = 60;
    private static final String DEFAULT_CATEGORY_COLOR = "#64748B";
    private static final String UNCategorized = "Uncategorized";
    private static final Comparator<TodoItem> SEARCH_SORT = Comparator
        .comparing((TodoItem item) -> item.getStatus() == TodoItemStatus.DONE)
        .thenComparing(TodoItem::getPriority, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(TodoItem::getStartDay, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(TodoItem::getCreatedAt, Comparator.reverseOrder());

    private final TodoItemRepository todoItemRepository;
    private final TodoCategoryRepository categoryRepository;
    private final TodoTagRepository tagRepository;
    private final StudySessionRepository sessionRepository;
    private final StudyDayService studyDayService;
    private final UserRepository userRepository;

    public TodoService(
        TodoItemRepository todoItemRepository,
        TodoCategoryRepository categoryRepository,
        TodoTagRepository tagRepository,
        StudySessionRepository sessionRepository,
        StudyDayService studyDayService,
        UserRepository userRepository
    ) {
        this.todoItemRepository = todoItemRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.sessionRepository = sessionRepository;
        this.studyDayService = studyDayService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public TodoDayView listForStudyDay(Long userId, LocalDate studyDay) {
        List<TodoItem> today = sortItems(
            todoItemRepository.findByUserIdAndStatusAndStartDay(userId, TodoItemStatus.OPEN, studyDay));
        List<TodoItem> undated = sortItems(
            todoItemRepository.findByUserIdAndStatusAndStartDayIsNull(userId, TodoItemStatus.OPEN));
        List<TodoItem> outdated = sortItems(
            todoItemRepository.findByUserIdAndStatusAndStartDayBefore(userId, TodoItemStatus.OPEN, studyDay));
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
        return listPickerForToday(userId, null);
    }

    /**
     * 타이머·수동 세션용 todo 후보.
     * {@code q}가 없으면 오늘+undated+outdated OPEN.
     * {@code q}가 있으면 저장된 OPEN 전체에서 제목·설명·카테고리·태그 검색 (수동 세션 타입ahead).
     */
    @Transactional(readOnly = true)
    public List<TodoItemResponse> listPickerForToday(Long userId, String rawQuery) {
        String query = normalizeSearchQuery(rawQuery);
        if (query != null) {
            List<TodoItem> matched = todoItemRepository.search(
                userId,
                query,
                TodoItemStatus.OPEN,
                null,
                null,
                null,
                null
            );
            matched.sort(SEARCH_SORT);
            int limit = Math.min(matched.size(), DEFAULT_SEARCH_LIMIT);
            return matched.subList(0, limit).stream().map(TodoItemResponse::from).toList();
        }

        LocalDate today = studyDayService.currentStudyDay(resetHour(userId));
        List<TodoItem> items = new ArrayList<>();
        items.addAll(todoItemRepository.findByUserIdAndStatusAndStartDay(userId, TodoItemStatus.OPEN, today));
        items.addAll(todoItemRepository.findByUserIdAndStatusAndStartDayIsNull(userId, TodoItemStatus.OPEN));
        items.addAll(todoItemRepository.findByUserIdAndStatusAndStartDayBefore(userId, TodoItemStatus.OPEN, today));
        return sortItems(items).stream().map(TodoItemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TodoSearchResponse search(
        Long userId,
        String rawQuery,
        String rawStatus,
        Long categoryId,
        Long tagId,
        LocalDate startFrom,
        LocalDate startTo,
        Integer limit,
        Long cursor
    ) {
        validateOptionalCategory(userId, categoryId);
        validateOptionalTag(userId, tagId);

        TodoItemStatus status = parseSearchStatus(rawStatus);
        String query = normalizeSearchQuery(rawQuery);
        int pageSize = resolveSearchLimit(limit);

        List<TodoItem> matched = todoItemRepository.search(
            userId,
            query,
            status,
            categoryId,
            tagId,
            startFrom,
            startTo
        );
        matched.sort(SEARCH_SORT);

        int startIndex = 0;
        if (cursor != null) {
            for (int i = 0; i < matched.size(); i++) {
                if (matched.get(i).getId().equals(cursor)) {
                    startIndex = i + 1;
                    break;
                }
            }
        }

        int endIndex = Math.min(startIndex + pageSize, matched.size());
        List<TodoItemResponse> items = matched.subList(startIndex, endIndex).stream()
            .map(TodoItemResponse::from)
            .toList();
        Long nextCursor = endIndex < matched.size() && !items.isEmpty()
            ? items.get(items.size() - 1).todoId()
            : null;

        return new TodoSearchResponse(items, nextCursor, matched.size());
    }

    @Transactional(readOnly = true)
    public TodoBoardResponse board(
        Long userId,
        LocalDate from,
        LocalDate to,
        Long categoryId,
        Long tagId
    ) {
        validateDateRange(from, to);
        validateOptionalCategory(userId, categoryId);
        validateOptionalTag(userId, tagId);

        List<TodoItem> openInRange = todoItemRepository.findByUserIdAndStatusAndStartDayBetween(
            userId,
            TodoItemStatus.OPEN,
            from,
            to
        ).stream()
            .filter(item -> matchesBoardFilter(item, categoryId, tagId))
            .toList();
        Map<LocalDate, List<TodoItem>> openByDay = openInRange.stream()
            .collect(Collectors.groupingBy(TodoItem::getStartDay));

        int summaryOpenCount = (int) todoItemRepository.findByUserIdAndStatus(userId, TodoItemStatus.OPEN).stream()
            .filter(item -> matchesBoardFilter(item, categoryId, tagId))
            .count();

        List<StudySession> sessions = sessionRepository.findByUserIdAndStatusAndStudyDayBetween(
            userId,
            SessionStatus.CLOSED,
            from,
            to
        );
        Map<Long, TodoItem> todosById = loadTodosForSessions(sessions);

        List<TodoBoardDayStats> days = new ArrayList<>();
        int summaryCompleted = 0;
        int summaryStudyMinutes = 0;

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            BoardDayAccumulator accumulator = new BoardDayAccumulator(day);

            for (TodoItem item : loadDoneForDay(userId, day)) {
                if (!matchesBoardFilter(item, categoryId, tagId)) {
                    continue;
                }
                accumulator.addCompleted(item);
            }

            for (TodoItem item : openByDay.getOrDefault(day, List.of())) {
                accumulator.addOpen(item);
            }

            for (StudySession session : sessions) {
                if (!session.getStudyDay().equals(day)) {
                    continue;
                }
                if (!sessionMatchesBoardFilter(session, todosById, categoryId, tagId)) {
                    continue;
                }
                int minutes = session.getDurationMinutes() != null ? session.getDurationMinutes() : 0;
                TodoItem linkedTodo = session.getTodoId() == null ? null : todosById.get(session.getTodoId());
                accumulator.addStudyMinutes(linkedTodo, minutes);
            }

            if (accumulator.hasActivity()) {
                days.add(accumulator.toResponse());
                summaryCompleted += accumulator.completedCount;
                summaryStudyMinutes += accumulator.studyMinutes;
            }
        }

        TodoBoardSummary summary = new TodoBoardSummary(
            summaryCompleted,
            summaryOpenCount,
            summaryStudyMinutes,
            summaryStudyMinutes
        );
        return new TodoBoardResponse(from, to, summary, days);
    }

    @Transactional
    public TodoItem quickCreate(Long userId, String rawTitle) {
        String title = validateTitle(rawTitle);
        return todoItemRepository.save(new TodoItem(
            userId,
            title,
            studyDayService.currentStudyDay(resetHour(userId))
        ));
    }

    @Transactional
    public TodoItem create(
        Long userId,
        String rawTitle,
        String rawDescription,
        Long categoryId,
        List<Long> tagIds,
        Integer priority,
        LocalDate startDay,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate endDay
    ) {
        String title = validateTitle(rawTitle);
        validatePriority(priority);
        LocalDate effectiveEndDay = resolveEndDay(startDay, endDay, endTime);
        validateSchedule(startDay, startTime, effectiveEndDay, endTime);
        TodoItem item = new TodoItem(userId, title, startDay);
        item.setDescription(validateDescription(rawDescription));
        item.setPriority(priority);
        item.setStartTime(startTime);
        item.setEndTime(endTime);
        item.setEndDay(effectiveEndDay);
        applyCategory(userId, item, categoryId);
        applyTags(userId, item, tagIds);
        return todoItemRepository.save(item);
    }

    @Transactional
    public TodoItem update(
        Long userId,
        Long todoId,
        String rawTitle,
        String rawDescription,
        Long categoryId,
        List<Long> tagIds,
        Integer priority,
        LocalDate startDay,
        boolean clearCategory,
        boolean clearStartDay,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate endDay,
        boolean clearStartTime,
        boolean clearEndTime,
        boolean clearEndDay,
        boolean clearDescription
    ) {
        TodoItem item = getOwned(userId, todoId);
        if (rawTitle != null) {
            item.setTitle(validateTitle(rawTitle));
        }
        if (clearDescription) {
            item.setDescription(null);
        } else if (rawDescription != null) {
            item.setDescription(validateDescription(rawDescription));
        }
        if (clearCategory) {
            item.assignCategory(null);
        } else if (categoryId != null) {
            applyCategory(userId, item, categoryId);
        }
        if (clearStartDay) {
            item.setStartDay(null);
        } else if (startDay != null) {
            item.setStartDay(startDay);
        }
        applySchedule(
            item,
            startTime,
            endTime,
            endDay,
            clearStartTime,
            clearEndTime,
            clearEndDay
        );
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
        item.rescheduleTo(studyDayService.currentStudyDay(resetHour(userId)));
        return item;
    }

    @Transactional
    public void delete(Long userId, Long todoId) {
        getOwned(userId, todoId);
        sessionRepository.clearTodoId(userId, todoId);
        todoItemRepository.deleteById(todoId);
    }

    @Transactional
    public TodoItem duplicate(Long userId, Long todoId) {
        TodoItem source = getOwned(userId, todoId);
        TodoItem copy = new TodoItem(userId, copyTitle(source.getTitle()), source.getStartDay());
        copy.setDescription(source.getDescription());
        copy.setPriority(source.getPriority());
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        copy.setEndDay(source.getEndDay());
        copy.assignCategory(source.getCategory());
        if (!source.getTags().isEmpty()) {
            copy.replaceTags(new HashSet<>(source.getTags()));
        }
        return todoItemRepository.save(copy);
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

    @Transactional
    public TodoCategory updateCategory(Long userId, Long categoryId, String rawName, String color) {
        TodoCategory category = getOwnedCategory(userId, categoryId);
        if (rawName != null) {
            String name = validateName(rawName);
            ensureUniqueCategoryName(userId, categoryId, name);
            category.setName(name);
        }
        if (color != null) {
            category.setColor(normalizeColor(color));
        }
        return category;
    }

    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        getOwnedCategory(userId, categoryId);
        todoItemRepository.clearCategory(userId, categoryId);
        categoryRepository.deleteById(categoryId);
    }

    @Transactional
    public TodoTag updateTag(Long userId, Long tagId, String rawName) {
        TodoTag tag = getOwnedTag(userId, tagId);
        if (rawName != null) {
            String name = validateName(rawName);
            ensureUniqueTagName(userId, tagId, name);
            tag.setName(name);
        }
        return tag;
    }

    @Transactional
    public void deleteTag(Long userId, Long tagId) {
        TodoTag tag = getOwnedTag(userId, tagId);
        todoItemRepository.findByUserIdAndTagId(userId, tagId)
            .forEach(item -> item.getTags().remove(tag));
        tagRepository.delete(tag);
    }

    private TodoCategory getOwnedCategory(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
            .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
    }

    private TodoTag getOwnedTag(Long userId, Long tagId) {
        return tagRepository.findByIdAndUserId(tagId, userId)
            .orElseThrow(() -> new NotFoundException("TAG_NOT_FOUND", "태그를 찾을 수 없습니다."));
    }

    private void ensureUniqueCategoryName(Long userId, Long categoryId, String name) {
        categoryRepository.findByUserIdAndName(userId, name)
            .filter(existing -> !existing.getId().equals(categoryId))
            .ifPresent(existing -> {
                throw new ConflictException("CATEGORY_ALREADY_EXISTS", "이미 있는 카테고리입니다.");
            });
    }

    private void ensureUniqueTagName(Long userId, Long tagId, String name) {
        tagRepository.findByUserIdAndName(userId, name)
            .filter(existing -> !existing.getId().equals(tagId))
            .ifPresent(existing -> {
                throw new ConflictException("TAG_ALREADY_EXISTS", "이미 있는 태그입니다.");
            });
    }

    private List<TodoItem> loadDoneForDay(Long userId, LocalDate studyDay) {
        int hour = resetHour(userId);
        Instant dayStart = studyDayService.studyDayStart(studyDay, hour);
        Instant dayEnd = studyDayService.studyDayEndExclusive(studyDay, hour);
        return todoItemRepository.findDoneForStudyDay(userId, studyDay, dayStart, dayEnd);
    }

    private int resetHour(Long userId) {
        return userRepository.findById(userId)
            .map(User::getStudyDayResetHour)
            .orElse(StudyDayService.RESET_HOUR);
    }

    private List<TodoItem> sortItems(List<TodoItem> items) {
        return items.stream()
            .sorted(Comparator
                .comparing(TodoItem::getPriority, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoItem::getStartDay, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoItem::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
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

    private String validateDescription(String rawDescription) {
        if (rawDescription == null) {
            return null;
        }
        String trimmed = rawDescription.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestException("DESCRIPTION_TOO_LONG", "설명은 10000자 이하여야 합니다.");
        }
        return trimmed;
    }

    private String copyTitle(String title) {
        String base = title == null ? "" : title;
        String suffix = "_copy";
        String candidate = base + suffix;
        if (candidate.length() > MAX_TITLE_LENGTH) {
            candidate = base.substring(0, MAX_TITLE_LENGTH - suffix.length()) + suffix;
        }
        return candidate;
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

    private void applySchedule(
        TodoItem item,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate endDay,
        boolean clearStartTime,
        boolean clearEndTime,
        boolean clearEndDay
    ) {
        LocalTime newStart = item.getStartTime();
        LocalTime newEnd = item.getEndTime();
        LocalDate newEndDay = item.getEndDay();
        if (clearStartTime) {
            newStart = null;
        } else if (startTime != null) {
            newStart = startTime;
        }
        if (clearEndTime) {
            newEnd = null;
        } else if (endTime != null) {
            newEnd = endTime;
        }
        if (clearEndDay) {
            newEndDay = null;
        } else if (endDay != null) {
            newEndDay = endDay;
        }
        newEndDay = resolveEndDay(item.getStartDay(), newEndDay, newEnd);
        validateSchedule(item.getStartDay(), newStart, newEndDay, newEnd);
        item.setStartTime(newStart);
        item.setEndTime(newEnd);
        item.setEndDay(newEndDay);
    }

    private LocalDate resolveEndDay(LocalDate startDay, LocalDate endDay, LocalTime endTime) {
        if (endDay != null) {
            return endDay;
        }
        if (endTime != null) {
            return startDay;
        }
        return null;
    }

    private void validateSchedule(
        LocalDate startDay,
        LocalTime startTime,
        LocalDate endDay,
        LocalTime endTime
    ) {
        if (startTime == null && endTime == null && endDay == null) {
            return;
        }
        if (startDay == null) {
            throw new BadRequestException("START_DATE_REQUIRED", "종료·시간을 설정하려면 시작 날짜를 먼저 지정해주세요.");
        }
        LocalDate effectiveEndDay = endDay != null ? endDay : startDay;
        LocalDateTime start = LocalDateTime.of(startDay, startTime != null ? startTime : LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(effectiveEndDay, endTime != null ? endTime : LocalTime.MAX);
        if (!end.isAfter(start)) {
            throw new BadRequestException("INVALID_TIME_RANGE", "종료 시각은 시작 시각 이후여야 합니다.");
        }
    }

    private String normalizeColor(String color) {
        if (!StringUtils.hasText(color)) {
            return DEFAULT_CATEGORY_COLOR;
        }
        String trimmed = color.trim();
        if (trimmed.length() > 7) {
            throw new BadRequestException("INVALID_COLOR", "색상 코드 형식이 올바르지 않습니다.");
        }
        return trimmed;
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("INVALID_DATE_RANGE", "from과 to는 필수입니다.");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("INVALID_DATE_RANGE", "시작일은 종료일 이전이어야 합니다.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_BOARD_RANGE_DAYS) {
            throw new BadRequestException("INVALID_DATE_RANGE", "조회 기간은 최대 60일입니다.");
        }
    }

    private void validateOptionalCategory(Long userId, Long categoryId) {
        if (categoryId == null) {
            return;
        }
        categoryRepository.findByIdAndUserId(categoryId, userId)
            .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
    }

    private void validateOptionalTag(Long userId, Long tagId) {
        if (tagId == null) {
            return;
        }
        tagRepository.findByIdAndUserId(tagId, userId)
            .orElseThrow(() -> new NotFoundException("TAG_NOT_FOUND", "태그를 찾을 수 없습니다."));
    }

    private TodoItemStatus parseSearchStatus(String rawStatus) {
        if (!StringUtils.hasText(rawStatus) || "ALL".equalsIgnoreCase(rawStatus)) {
            return null;
        }
        try {
            return TodoItemStatus.valueOf(rawStatus.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("INVALID_STATUS", "status는 OPEN, DONE, ALL 중 하나여야 합니다.");
        }
    }

    private String normalizeSearchQuery(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return null;
        }
        String trimmed = rawQuery.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private int resolveSearchLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(limit, MAX_SEARCH_LIMIT);
    }

    private boolean matchesBoardFilter(TodoItem item, Long categoryId, Long tagId) {
        if (categoryId != null) {
            if (item.getCategory() == null || !categoryId.equals(item.getCategory().getId())) {
                return false;
            }
        }
        if (tagId != null) {
            return item.getTags().stream().anyMatch(tag -> tagId.equals(tag.getId()));
        }
        return true;
    }

    private boolean sessionMatchesBoardFilter(
        StudySession session,
        Map<Long, TodoItem> todosById,
        Long categoryId,
        Long tagId
    ) {
        if (categoryId == null && tagId == null) {
            return true;
        }
        if (session.getTodoId() == null) {
            return false;
        }
        TodoItem todo = todosById.get(session.getTodoId());
        return todo != null && matchesBoardFilter(todo, categoryId, tagId);
    }

    private Map<Long, TodoItem> loadTodosForSessions(List<StudySession> sessions) {
        Set<Long> todoIds = sessions.stream()
            .map(StudySession::getTodoId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (todoIds.isEmpty()) {
            return Map.of();
        }
        return todoItemRepository.findByIdIn(todoIds).stream()
            .collect(Collectors.toMap(TodoItem::getId, Function.identity()));
    }

    private static final class BoardDayAccumulator {
        private final LocalDate studyDay;
        private int completedCount;
        private int openCount;
        private int studyMinutes;
        private final Map<CategoryKey, CategoryStats> categories = new HashMap<>();
        private final Map<Long, TagStats> tags = new HashMap<>();

        private BoardDayAccumulator(LocalDate studyDay) {
            this.studyDay = studyDay;
        }

        private void addCompleted(TodoItem item) {
            completedCount++;
            categoryOf(item).addCompleted();
            for (TodoTag tag : item.getTags()) {
                tags.computeIfAbsent(tag.getId(), id -> new TagStats(tag.getId(), tag.getName())).addCompleted();
            }
        }

        private void addOpen(TodoItem item) {
            openCount++;
            categoryOf(item).addOpen();
        }

        private void addStudyMinutes(TodoItem item, int minutes) {
            studyMinutes += minutes;
            categoryOf(item).addStudyMinutes(minutes);
            if (item != null) {
                for (TodoTag tag : item.getTags()) {
                    tags.computeIfAbsent(tag.getId(), id -> new TagStats(tag.getId(), tag.getName()))
                        .addStudyMinutes(minutes);
                }
            }
        }

        private CategoryStats categoryOf(TodoItem item) {
            TodoCategory category = item == null ? null : item.getCategory();
            CategoryKey key = category == null
                ? CategoryKey.uncategorized()
                : new CategoryKey(category.getId(), category.getName(), category.getColor());
            return categories.computeIfAbsent(key, CategoryStats::new);
        }

        private boolean hasActivity() {
            return completedCount > 0 || openCount > 0 || studyMinutes > 0;
        }

        private TodoBoardDayStats toResponse() {
            List<TodoBoardCategoryBreakdown> byCategory = categories.values().stream()
                .map(CategoryStats::toResponse)
                .sorted(Comparator.comparing(TodoBoardCategoryBreakdown::name))
                .toList();
            List<TodoBoardTagBreakdown> byTag = tags.values().stream()
                .map(TagStats::toResponse)
                .sorted(Comparator.comparing(TodoBoardTagBreakdown::name))
                .toList();
            return new TodoBoardDayStats(
                studyDay,
                completedCount,
                openCount,
                studyMinutes,
                byCategory,
                byTag
            );
        }
    }

    private record CategoryKey(Long categoryId, String name, String color) {
        private static CategoryKey uncategorized() {
            return new CategoryKey(null, UNCategorized, null);
        }
    }

    private static final class CategoryStats {
        private final CategoryKey key;
        private int completedCount;
        private int openCount;
        private int studyMinutes;

        private CategoryStats(CategoryKey key) {
            this.key = key;
        }

        private void addCompleted() {
            completedCount++;
        }

        private void addOpen() {
            openCount++;
        }

        private void addStudyMinutes(int minutes) {
            studyMinutes += minutes;
        }

        private TodoBoardCategoryBreakdown toResponse() {
            return new TodoBoardCategoryBreakdown(
                key.categoryId(),
                key.name(),
                key.color(),
                completedCount,
                openCount,
                studyMinutes
            );
        }
    }

    private static final class TagStats {
        private final Long tagId;
        private final String name;
        private int completedCount;
        private int studyMinutes;

        private TagStats(Long tagId, String name) {
            this.tagId = tagId;
            this.name = name;
        }

        private void addCompleted() {
            completedCount++;
        }

        private void addStudyMinutes(int minutes) {
            studyMinutes += minutes;
        }

        private TodoBoardTagBreakdown toResponse() {
            return new TodoBoardTagBreakdown(tagId, name, completedCount, studyMinutes);
        }
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
