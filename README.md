# 实验室安全与设备管理系统

基于 Spring Boot 3、Spring Security、MyBatis、MySQL、Redis、Vue 3 和 Element Plus 实现的前后端分离实验室管理系统。

当前业务覆盖实验室、设备、资格、预约、领用归还、维修、巡检、隐患整改、通知和角色权限。

Windows 本机一键启动（无 Docker）：

```powershell
.\scripts\start-local.ps1
```

停止本项目启动的所有本机进程：

```powershell
.\scripts\stop-local.ps1
```

启动后的本机运行状态和账号凭据保存在 `target/local-runtime` 下，不会进入版本库。
