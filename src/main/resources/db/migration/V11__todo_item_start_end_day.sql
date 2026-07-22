alter table todo_item
    change column due_study_day start_day date null,
    change column end_study_day end_day date null;
