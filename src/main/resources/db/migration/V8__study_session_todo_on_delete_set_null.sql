alter table study_session
    drop foreign key fk_study_session_todo;

alter table study_session
    add constraint fk_study_session_todo
        foreign key (todo_id) references todo_item (id) on delete set null;
