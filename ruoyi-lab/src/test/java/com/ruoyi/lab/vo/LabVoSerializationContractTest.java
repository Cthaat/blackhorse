package com.ruoyi.lab.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.domain.LabInspectionItem;
import com.ruoyi.lab.domain.LabInspectionPlan;
import com.ruoyi.lab.domain.LabInspectionPlanItem;
import com.ruoyi.lab.domain.LabInspectionTask;
import com.ruoyi.lab.domain.LabRectification;
import com.ruoyi.lab.domain.RepairSourceType;
import com.ruoyi.lab.domain.RepairStatus;
import org.junit.jupiter.api.Test;

class LabVoSerializationContractTest
{
    private static final List<Class<?>> DIRECT_BUSINESS_VOS = List.of(AttachmentVo.class,
            DeviceVo.class, LaboratoryVo.class, NotificationVo.class, OccupiedRangeVo.class,
            QualificationVo.class, RepairOrderVo.class, ReservationVo.class,
            StatusHistoryVo.class, UsageRecordDetailVo.class, UsageRecordVo.class,
            LabInspectionPlan.class, LabInspectionPlanItem.class, LabInspectionTask.class,
            LabInspectionItem.class, LabHazard.class, LabRectification.class);

    private final ObjectMapper objectMapper = mapperWithLegacyLocalTimeFallback();

    @Test
    void serializesBusinessIdsAsStringsAndShanghaiTimesWithOffsets() throws Exception
    {
        long unsafeJavaScriptId = 9_007_199_254_740_993L;
        LocalDateTime businessTime = LocalDateTime.of(2026, 9, 3, 12, 0);
        RepairOrderVo order = new RepairOrderVo(unsafeJavaScriptId, "REP-1", 17L,
                "AST-17", "显微镜", RepairSourceType.ACTIVE_REPORT, 18L, 19L,
                "无法启动", 20L, businessTime, businessTime, "更换电源", businessTime,
                null, null, 21L, businessTime, RepairStatus.CLOSED, 7, businessTime);
        StatusHistoryVo history = new StatusHistoryVo(31L, "REPAIR_ORDER",
                unsafeJavaScriptId, "WAIT_ACCEPTANCE", "CLOSED", 21L, "管理员",
                "验收通过", "trace-31", businessTime);
        AttachmentVo attachment = new AttachmentVo(41L, "REPAIR_ORDER",
                unsafeJavaScriptId, "result.pdf", "application/pdf", 2_048L,
                "sha256", "worker", businessTime);
        RepairOrderDetailVo detail = new RepairOrderDetailVo(order, List.of(history),
                List.of(attachment));
        LabInspectionPlan plan = new LabInspectionPlan();
        plan.setId(51L);
        plan.setLaboratoryId(52L);
        plan.setOwnerId(53L);
        plan.setNextRunAt(businessTime);
        plan.setVersion(4);
        plan.setCreateTime(businessTime);
        LabInspectionPlanItem item = new LabInspectionPlanItem();
        item.setId(61L);
        item.setPlanId(51L);
        item.setSortOrder(1);
        item.setCreateTime(businessTime);
        InspectionPlanDetailVo planDetail = new InspectionPlanDetailVo(plan, List.of(item));
        LabInspectionTask task = new LabInspectionTask();
        task.setId(71L);
        task.setPlanId(51L);
        task.setLaboratoryId(52L);
        task.setAssigneeId(72L);
        task.setScheduledAt(businessTime);
        task.setOverdueEventVersion(3L);
        LabHazard hazard = new LabHazard();
        hazard.setId(81L);
        hazard.setSourceItemId(61L);
        hazard.setRelatedHazardId(82L);
        hazard.setTargetId(52L);
        hazard.setOwnerId(83L);
        hazard.setDeadline(businessTime);
        hazard.setOverdueEventVersion(4L);

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(
                Map.of("detail", detail, "count", new LabMetricVo("CLOSED", 12L),
                        "planDetail", planDetail, "task", task, "hazard", hazard)));

        assertThat(root.at("/detail/order/id").isTextual()).isTrue();
        assertThat(root.at("/detail/order/id").textValue())
                .isEqualTo("9007199254740993");
        assertThat(root.at("/detail/order/deviceId").isTextual()).isTrue();
        assertThat(root.at("/detail/order/acceptedBy").isTextual()).isTrue();
        assertThat(root.at("/detail/statusHistory/0/objectId").isTextual()).isTrue();
        assertThat(root.at("/detail/statusHistory/0/operatorId").isTextual()).isTrue();
        assertThat(root.at("/detail/attachments/0/businessId").isTextual()).isTrue();
        assertThat(root.at("/detail/order/createTime").textValue())
                .isEqualTo("2026-09-03T12:00:00+08:00");
        assertThat(root.at("/detail/order/version").isIntegralNumber()).isTrue();
        assertThat(root.at("/detail/attachments/0/size").isIntegralNumber()).isTrue();
        assertThat(root.at("/count/value").isIntegralNumber()).isTrue();
        assertThat(root.at("/planDetail/plan/id").isTextual()).isTrue();
        assertThat(root.at("/planDetail/plan/laboratoryId").isTextual()).isTrue();
        assertThat(root.at("/planDetail/plan/ownerId").isTextual()).isTrue();
        assertThat(root.at("/planDetail/plan/nextRunAt").textValue())
                .isEqualTo("2026-09-03T12:00:00+08:00");
        assertThat(root.at("/planDetail/plan/version").isIntegralNumber()).isTrue();
        assertThat(root.at("/planDetail/items/0/id").isTextual()).isTrue();
        assertThat(root.at("/planDetail/items/0/planId").isTextual()).isTrue();
        assertThat(root.at("/planDetail/items/0/sortOrder").isIntegralNumber()).isTrue();
        assertThat(root.at("/task/id").isTextual()).isTrue();
        assertThat(root.at("/task/planId").isTextual()).isTrue();
        assertThat(root.at("/task/assigneeId").isTextual()).isTrue();
        assertThat(root.at("/task/scheduledAt").textValue())
                .isEqualTo("2026-09-03T12:00:00+08:00");
        assertThat(root.at("/task/overdueEventVersion").isIntegralNumber()).isTrue();
        assertThat(root.at("/hazard/id").isTextual()).isTrue();
        assertThat(root.at("/hazard/sourceItemId").isTextual()).isTrue();
        assertThat(root.at("/hazard/targetId").isTextual()).isTrue();
        assertThat(root.at("/hazard/deadline").textValue())
                .isEqualTo("2026-09-03T12:00:00+08:00");
        assertThat(root.at("/hazard/overdueEventVersion").isIntegralNumber()).isTrue();
    }

    @Test
    void everyDirectBusinessIdAndLocalTimeDeclaresTheSameJsonContract()
    {
        DIRECT_BUSINESS_VOS.forEach(type -> Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .forEach(field -> {
                    if (field.getType() == Long.class && !field.getName().equals("size")
                            && !field.getName().equals("overdueEventVersion"))
                    {
                        assertThat(hasAnnotation(field, "LabBusinessId"))
                                .as("%s.%s", type.getSimpleName(), field.getName()).isTrue();
                    }
                    if (field.getType() == LocalDateTime.class)
                    {
                        assertThat(hasAnnotation(field, "LabBusinessTime"))
                                .as("%s.%s", type.getSimpleName(), field.getName()).isTrue();
                    }
                }));
    }

    private static boolean hasAnnotation(java.lang.reflect.Field field, String simpleName)
    {
        return Arrays.stream(field.getDeclaredAnnotations())
                .anyMatch(annotation -> annotation.annotationType().getSimpleName()
                        .equals(simpleName));
    }

    private static ObjectMapper mapperWithLegacyLocalTimeFallback()
    {
        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>()
        {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator generator,
                    SerializerProvider serializers) throws java.io.IOException
            {
                generator.writeString(value.toString());
            }
        });
        return new ObjectMapper().registerModule(module);
    }
}
