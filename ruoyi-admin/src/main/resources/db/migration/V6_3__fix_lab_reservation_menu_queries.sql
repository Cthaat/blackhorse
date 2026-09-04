-- RuoYi's sidebar parses sys_menu.query with JSON.parse.  The original
-- reservation seed used URL query-string syntax, which breaks menu rendering.
update sys_menu
set query = '{"mode":"mine"}',
    update_by = 'admin',
    update_time = now(3)
where menu_id = 2301
  and component = 'lab/reservation/index';

update sys_menu
set query = '{"mode":"approval"}',
    update_by = 'admin',
    update_time = now(3)
where menu_id = 2302
  and component = 'lab/reservation/index';
