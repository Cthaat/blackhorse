-- Reuse existing device read permission; QR access never grants business permissions.
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
VALUES(9820,'扫描资产',2000,26,'asset-scan','lab/asset-scan/index',1,1,'C','0','0','lab:device:query','search','admin',NOW());
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT rm.role_id,9820 FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE m.perms='lab:device:query' AND m.menu_id<>9820;
