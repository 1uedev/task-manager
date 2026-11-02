-- The tickets of one customer organization in one project (TASK-075, DEC-105), the list a portal account reads.
create index helpdesk_ticket_customer_portal on helpdesk_ticket_customer (project_id, customer_id) where customer_id is not null;
