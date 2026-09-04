package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import com.ruoyi.lab.domain.RepairSourceType;
import com.ruoyi.lab.domain.RepairStatus;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.service.impl.RepairQueryServiceImpl;
import com.ruoyi.lab.vo.AttachmentVo;
import com.ruoyi.lab.vo.RepairOrderDetailVo;
import com.ruoyi.lab.vo.RepairOrderVo;
import com.ruoyi.lab.vo.StatusHistoryVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepairDetailQueryTest
{
    @Mock private LabRepairOrderMapper repairMapper;
    @Mock private LabDataScopeService dataScopeService;
    @Mock private StatusHistoryQueryService statusHistoryQueryService;
    @Mock private AttachmentService attachmentService;

    @Test
    void returnsScopedRepairWithFullHistoryAndSafeAttachmentMetadata()
    {
        RepairQueryService service = new RepairQueryServiceImpl(repairMapper, dataScopeService,
                statusHistoryQueryService, attachmentService);
        LabDataScope scope = new LabDataScope(55L, false, Set.of(3L));
        RepairOrderVo order = order();
        StatusHistoryVo history = new StatusHistoryVo(91L, "REPAIR_ORDER", 7L,
                "IN_PROGRESS", "WAIT_ACCEPTANCE", 55L, "维修人员", "提交维修结果",
                "trace-91", LocalDateTime.of(2026, 9, 3, 11, 30));
        AttachmentVo attachment = new AttachmentVo(31L, "REPAIR_ORDER", 7L,
                "result.pdf", "application/pdf", 128L, "abc123", "worker",
                LocalDateTime.of(2026, 9, 3, 11, 20));
        when(dataScopeService.resolveCurrentScope()).thenReturn(scope);
        when(repairMapper.selectScopedDetail(7L, 55L, scope)).thenReturn(order);
        when(statusHistoryQueryService.list("REPAIR_ORDER", 7L, 55L))
                .thenReturn(List.of(history));
        when(attachmentService.list("REPAIR_ORDER", 7L)).thenReturn(List.of(attachment));

        RepairOrderDetailVo result = service.detail(7L, 55L);

        assertThat(result.order()).isEqualTo(order);
        assertThat(result.statusHistory()).containsExactly(history);
        assertThat(result.attachments()).containsExactly(attachment);
        verify(statusHistoryQueryService).list("REPAIR_ORDER", 7L, 55L);
        verify(attachmentService).list("REPAIR_ORDER", 7L);
    }

    private static RepairOrderVo order()
    {
        return new RepairOrderVo(7L, "REP-7", 17L, "AST-17", "显微镜",
                RepairSourceType.ACTIVE_REPORT, null, 55L, "无法启动", 66L, null, null,
                "已更换电源", null, null, null, null, null,
                RepairStatus.WAIT_ACCEPTANCE, 3, LocalDateTime.of(2026, 9, 3, 10, 0));
    }
}
