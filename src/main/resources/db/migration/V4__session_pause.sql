alter table study_session
    add column active_duration_seconds int not null default 0 after duration_minutes,
    add column last_resumed_at datetime(6) null after active_duration_seconds,
    add column paused_at datetime(6) null after last_resumed_at;

update study_session
set last_resumed_at = started_at
where status = 'OPEN';
