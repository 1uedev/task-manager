-- The rule of the name of the appearance (TASK-087, DEC-117; finding F1 and hint H1 of docs/REVIEW_TASK-081_PR-243.md).
-- V0046 only refused plain spaces at the edges, so a name with a protected space at its start or end was stored, and the
-- interface then refused the whole appearance. From here on the database checks the same rule as the server
-- (AppearanceService.EDGE and FORBIDDEN) and the interface (frontend/src/api/client.ts): no space of any kind at either
-- end, no control, line separator or format character anywhere, 1 to 80 characters. V0046 stays as it was shipped.

alter table appearance_setting drop constraint appearance_setting_name;

-- A stored name loses the spaces at its edges; a name that is still not valid afterwards becomes the product name again.
update appearance_setting
   set name = regexp_replace(name,
       '^[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+|[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]+$',
       '', 'g');

update appearance_setting
   set name = 'Task Manager'
 where char_length(name) not between 1 and 80
    or name ~ '[\u0001-\u001f\u007f-\u009f\u00ad\u0600-\u0605\u061c\u06dd\u070f\u0890\u0891\u08e2\u180e\u200b-\u200f\u2028-\u202e\u2060-\u2064\u2066-\u206f\ufeff\ufff9-\ufffb\U000110bd\U000110cd\U00013430-\U0001343f\U0001bca0-\U0001bca3\U0001d173-\U0001d17a\U000e0001\U000e0020-\U000e007f]';

alter table appearance_setting add constraint appearance_setting_name check (
    char_length(name) between 1 and 80
    and name !~ '^[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]|[\u0009-\u000d\u001c-\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000\ufeff]$'
    and name !~ '[\u0001-\u001f\u007f-\u009f\u00ad\u0600-\u0605\u061c\u06dd\u070f\u0890\u0891\u08e2\u180e\u200b-\u200f\u2028-\u202e\u2060-\u2064\u2066-\u206f\ufeff\ufff9-\ufffb\U000110bd\U000110cd\U00013430-\U0001343f\U0001bca0-\U0001bca3\U0001d173-\U0001d17a\U000e0001\U000e0020-\U000e007f]');
