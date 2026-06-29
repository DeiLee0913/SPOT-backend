package com.spot.domain.todo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "todo_item")
public class TodoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private TodoCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "due_study_day")
    private LocalDate dueStudyDay;

    @Column
    private Integer priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TodoItemStatus status;

    @Column(name = "done_at")
    private Instant doneAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "todo_item_tag",
        joinColumns = @JoinColumn(name = "todo_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<TodoTag> tags = new HashSet<>();

    protected TodoItem() {
    }

    public TodoItem(Long userId, String title, LocalDate dueStudyDay) {
        this.userId = userId;
        this.title = title;
        this.dueStudyDay = dueStudyDay;
        this.status = TodoItemStatus.OPEN;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void complete(Instant now) {
        this.status = TodoItemStatus.DONE;
        this.doneAt = now;
    }

    public void reopen() {
        this.status = TodoItemStatus.OPEN;
        this.doneAt = null;
    }

    public void rescheduleTo(LocalDate studyDay) {
        this.dueStudyDay = studyDay;
    }

    public void assignCategory(TodoCategory category) {
        this.category = category;
    }

    public void replaceTags(Set<TodoTag> newTags) {
        this.tags.clear();
        this.tags.addAll(newTags);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public TodoCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getDueStudyDay() {
        return dueStudyDay;
    }

    public void setDueStudyDay(LocalDate dueStudyDay) {
        this.dueStudyDay = dueStudyDay;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public TodoItemStatus getStatus() {
        return status;
    }

    public Instant getDoneAt() {
        return doneAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<TodoTag> getTags() {
        return tags;
    }
}
