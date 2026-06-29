create table todo_category (
    id         bigint       not null auto_increment primary key,
    user_id    bigint       not null,
    name       varchar(50)  not null,
    color      varchar(7)   not null,
    created_at datetime(6)  not null,
    constraint uq_todo_category_user_name unique (user_id, name),
    constraint fk_todo_category_user foreign key (user_id) references users (id)
) engine = InnoDB default charset = utf8mb4;

create table todo_tag (
    id         bigint       not null auto_increment primary key,
    user_id    bigint       not null,
    name       varchar(50)  not null,
    created_at datetime(6)  not null,
    constraint uq_todo_tag_user_name unique (user_id, name),
    constraint fk_todo_tag_user foreign key (user_id) references users (id)
) engine = InnoDB default charset = utf8mb4;

create table todo_item (
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

create index idx_todo_item_user_due on todo_item (user_id, due_study_day);
create index idx_todo_item_user_status on todo_item (user_id, status);

create table todo_item_tag (
    todo_id bigint not null,
    tag_id  bigint not null,
    primary key (todo_id, tag_id),
    constraint fk_todo_item_tag_item foreign key (todo_id) references todo_item (id) on delete cascade,
    constraint fk_todo_item_tag_tag foreign key (tag_id) references todo_tag (id) on delete cascade
) engine = InnoDB default charset = utf8mb4;

alter table study_session
    add column todo_id bigint null after study_day,
    add constraint fk_study_session_todo foreign key (todo_id) references todo_item (id);

-- legacy category → todo_item (dedupe by user + title + study_day)
insert into todo_item (user_id, category_id, title, due_study_day, priority, status, done_at, created_at, updated_at)
select s.user_id,
       null,
       s.category,
       s.study_day,
       null,
       'OPEN',
       null,
       min(s.created_at),
       min(s.created_at)
from study_session s
where trim(s.category) <> ''
group by s.user_id, s.category, s.study_day;

update study_session s
    inner join todo_item t
        on t.user_id = s.user_id
            and t.title = s.category
            and t.due_study_day = s.study_day
set s.todo_id = t.id
where trim(s.category) <> '';

alter table study_session drop column category;
