create table if not exists todo_category (
    id         bigint       not null auto_increment primary key,
    user_id    bigint       not null,
    name       varchar(50)  not null,
    color      varchar(7)   not null,
    created_at datetime(6)  not null,
    constraint uq_todo_category_user_name unique (user_id, name),
    constraint fk_todo_category_user foreign key (user_id) references users (id)
) engine = InnoDB default charset = utf8mb4;

create table if not exists todo_tag (
    id         bigint       not null auto_increment primary key,
    user_id    bigint       not null,
    name       varchar(50)  not null,
    created_at datetime(6)  not null,
    constraint uq_todo_tag_user_name unique (user_id, name),
    constraint fk_todo_tag_user foreign key (user_id) references users (id)
) engine = InnoDB default charset = utf8mb4;

create table if not exists todo_item (
    id             bigint       not null auto_increment primary key,
    user_id        bigint       not null,
    category_id    bigint       null,
    title          varchar(200) not null,
    due_study_day  date         null,
    priority       tinyint      null,
    status         varchar(20)  not null,
    done_at        datetime(6)  null,
    created_at     datetime(6)  not null,
    updated_at     datetime(6)  not null,
    constraint fk_todo_item_user foreign key (user_id) references users (id),
    constraint fk_todo_item_category foreign key (category_id) references todo_category (id)
) engine = InnoDB default charset = utf8mb4;

set @idx_due_exists := (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 'todo_item'
      and index_name = 'idx_todo_item_user_due'
);
set @create_idx_due_sql := if(
    @idx_due_exists = 0,
    'create index idx_todo_item_user_due on todo_item (user_id, due_study_day)',
    'select 1'
);
prepare create_idx_due_stmt from @create_idx_due_sql;
execute create_idx_due_stmt;
deallocate prepare create_idx_due_stmt;

set @idx_status_exists := (
    select count(*)
    from information_schema.statistics
    where table_schema = database()
      and table_name = 'todo_item'
      and index_name = 'idx_todo_item_user_status'
);
set @create_idx_status_sql := if(
    @idx_status_exists = 0,
    'create index idx_todo_item_user_status on todo_item (user_id, status)',
    'select 1'
);
prepare create_idx_status_stmt from @create_idx_status_sql;
execute create_idx_status_stmt;
deallocate prepare create_idx_status_stmt;

create table if not exists todo_item_tag (
    todo_id bigint not null,
    tag_id  bigint not null,
    primary key (todo_id, tag_id),
    constraint fk_todo_item_tag_item foreign key (todo_id) references todo_item (id) on delete cascade,
    constraint fk_todo_item_tag_tag foreign key (tag_id) references todo_tag (id) on delete cascade
) engine = InnoDB default charset = utf8mb4;

set @todo_id_exists := (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'study_session'
      and column_name = 'todo_id'
);

set @add_todo_id_sql := if(
    @todo_id_exists = 0,
    'alter table study_session add column todo_id bigint null after study_day',
    'select 1'
);
prepare add_todo_id_stmt from @add_todo_id_sql;
execute add_todo_id_stmt;
deallocate prepare add_todo_id_stmt;

set @fk_exists := (
    select count(*)
    from information_schema.table_constraints
    where table_schema = database()
      and table_name = 'study_session'
      and constraint_name = 'fk_study_session_todo'
);

set @add_fk_sql := if(
    @fk_exists = 0,
    'alter table study_session add constraint fk_study_session_todo foreign key (todo_id) references todo_item (id)',
    'select 1'
);
prepare add_fk_stmt from @add_fk_sql;
execute add_fk_stmt;
deallocate prepare add_fk_stmt;

set @category_exists := (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'study_session'
      and column_name = 'category'
);

set @migrate_legacy_sql := if(
    @category_exists > 0,
    'insert into todo_item (user_id, category_id, title, due_study_day, priority, status, done_at, created_at, updated_at)
     select s.user_id,
            null,
            s.category,
            s.study_day,
            null,
            ''OPEN'',
            null,
            min(s.created_at),
            min(s.created_at)
     from study_session s
     left join todo_item t
         on t.user_id = s.user_id
             and t.title = s.category
             and t.due_study_day = s.study_day
     where char_length(trim(s.category)) > 0
       and t.id is null
     group by s.user_id, s.category, s.study_day',
    'select 1'
);
prepare migrate_legacy_stmt from @migrate_legacy_sql;
execute migrate_legacy_stmt;
deallocate prepare migrate_legacy_stmt;

set @link_sessions_sql := if(
    @category_exists > 0,
    'update study_session s
         inner join todo_item t
             on t.user_id = s.user_id
                 and t.title = s.category
                 and t.due_study_day = s.study_day
     set s.todo_id = t.id
     where char_length(trim(s.category)) > 0
       and s.todo_id is null',
    'select 1'
);
prepare link_sessions_stmt from @link_sessions_sql;
execute link_sessions_stmt;
deallocate prepare link_sessions_stmt;

set @drop_category_sql := if(
    @category_exists > 0,
    'alter table study_session drop column category',
    'select 1'
);
prepare drop_category_stmt from @drop_category_sql;
execute drop_category_stmt;
deallocate prepare drop_category_stmt;
