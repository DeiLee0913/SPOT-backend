create table users (
    id                   bigint not null auto_increment primary key,
    provider             varchar(20) not null,
    provider_id          varchar(255) not null,
    email                varchar(255),
    nickname             varchar(255) not null,
    default_goal_minutes integer not null,
    status               varchar(20) not null,
    deleted_at           datetime(6),
    created_at           datetime(6) not null,
    updated_at           datetime(6) not null,
    constraint uq_users_provider_provider_id unique (provider, provider_id)
) engine = InnoDB default charset = utf8mb4;

create table study_group (
    id              bigint not null auto_increment primary key,
    name            varchar(50) not null,
    invite_code     varchar(8) not null unique,
    creator_user_id bigint not null,
    max_members     integer not null default 20,
    status          varchar(20) not null,
    created_at      datetime(6) not null,
    constraint fk_study_group_creator foreign key (creator_user_id) references users (id)
) engine = InnoDB default charset = utf8mb4;

create table group_member (
    id         bigint not null auto_increment primary key,
    group_id   bigint not null,
    user_id    bigint not null,
    status     varchar(20) not null,
    joined_at  datetime(6),
    created_at datetime(6) not null,
    constraint fk_group_member_group foreign key (group_id) references study_group (id),
    constraint fk_group_member_user foreign key (user_id) references users (id)
) engine = InnoDB default charset = utf8mb4;

create index idx_group_member_group_status on group_member (group_id, status);
create index idx_group_member_user_status on group_member (user_id, status);

create table daily_goal (
    id           bigint not null auto_increment primary key,
    user_id      bigint not null,
    study_day    date not null,
    goal_minutes integer not null,
    source       varchar(20) not null,
    created_at   datetime(6) not null,
    updated_at   datetime(6) not null,
    constraint uq_daily_goal_user_day unique (user_id, study_day),
    constraint fk_daily_goal_user foreign key (user_id) references users (id)
) engine = InnoDB default charset = utf8mb4;

create table study_session (
    id               bigint not null auto_increment primary key,
    user_id          bigint not null,
    study_day        date not null,
    category         varchar(50) not null,
    source           varchar(20) not null,
    status           varchar(20) not null,
    started_at       datetime(6) not null,
    ended_at         datetime(6),
    duration_minutes integer,
    created_at       datetime(6) not null,
    constraint fk_study_session_user foreign key (user_id) references users (id)
) engine = InnoDB default charset = utf8mb4;

create index idx_study_session_user_status on study_session (user_id, status);
create index idx_study_session_user_day on study_session (user_id, study_day);
create index idx_study_session_day on study_session (study_day);
