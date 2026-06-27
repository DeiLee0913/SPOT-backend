alter table users
    add column naver_nickname varchar(255) null after email,
    add column display_name varchar(50) null after naver_nickname;

update users
set naver_nickname = nickname,
    display_name = nickname
where nickname is not null;

alter table users drop column nickname;
