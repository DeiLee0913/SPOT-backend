alter table group_member
    add constraint uq_group_member_group_user unique (group_id, user_id);
