-- Name of the company in the appearance (TASK-081): shown in the head of every page, in the window title, on the login page,
-- in the portal and later in mails. Without a choice the product name stays.
alter table appearance_setting
    add column name text not null default 'Task Manager',
    add constraint appearance_setting_name check (char_length(name) between 1 and 80 and name = btrim(name));
