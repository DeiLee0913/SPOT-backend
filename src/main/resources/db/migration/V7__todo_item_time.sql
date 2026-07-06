alter table todo_item
    add column start_time time null after due_study_day,
    add column end_time time null after start_time;
