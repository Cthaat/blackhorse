package com.ruoyi.integration.web.openapi;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.ruoyi.lab.service.AttachmentService;
import com.ruoyi.lab.service.DashboardService;
import com.ruoyi.lab.service.DeviceService;
import com.ruoyi.lab.service.DeviceStatusCommandService;
import com.ruoyi.lab.service.HazardService;
import com.ruoyi.lab.service.InspectionPlanService;
import com.ruoyi.lab.service.InspectionTaskService;
import com.ruoyi.lab.service.LaboratoryService;
import com.ruoyi.lab.service.NotificationService;
import com.ruoyi.lab.service.QualificationService;
import com.ruoyi.lab.service.RectificationService;
import com.ruoyi.lab.service.RepairOrderService;
import com.ruoyi.lab.service.RepairQueryService;
import com.ruoyi.lab.service.ReservationCommandService;
import com.ruoyi.lab.service.ReservationQueryService;
import com.ruoyi.lab.service.StatusHistoryQueryService;
import com.ruoyi.lab.service.UsageCommandService;
import com.ruoyi.lab.service.UsageQueryService;
import com.ruoyi.lab.service.LabOptionsService;

/** Shared controller dependencies for both documentation profiles; no database is needed. */
abstract class LabOpenApiServiceMocks
{
    @MockitoBean AttachmentService attachmentService;
    @MockitoBean DashboardService dashboardService;
    @MockitoBean DeviceService deviceService;
    @MockitoBean DeviceStatusCommandService deviceStatusCommandService;
    @MockitoBean HazardService hazardService;
    @MockitoBean InspectionPlanService inspectionPlanService;
    @MockitoBean InspectionTaskService inspectionTaskService;
    @MockitoBean LaboratoryService laboratoryService;
    @MockitoBean NotificationService notificationService;
    @MockitoBean QualificationService qualificationService;
    @MockitoBean RectificationService rectificationService;
    @MockitoBean RepairOrderService repairOrderService;
    @MockitoBean RepairQueryService repairQueryService;
    @MockitoBean ReservationCommandService reservationCommandService;
    @MockitoBean ReservationQueryService reservationQueryService;
    @MockitoBean StatusHistoryQueryService statusHistoryQueryService;
    @MockitoBean UsageCommandService usageCommandService;
    @MockitoBean UsageQueryService usageQueryService;
    @MockitoBean LabOptionsService labOptionsService;
}
