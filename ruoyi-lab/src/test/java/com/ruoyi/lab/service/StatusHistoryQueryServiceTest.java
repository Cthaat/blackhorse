package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.mapper.LabStatusHistoryMapper;
import com.ruoyi.lab.security.LabStatusHistoryObjectAuthorizer;
import com.ruoyi.lab.service.impl.StatusHistoryQueryServiceImpl;
import com.ruoyi.lab.vo.StatusHistoryVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatusHistoryQueryServiceTest
{
    @Mock private LabStatusHistoryMapper historyMapper;
    @Mock private LabStatusHistoryObjectAuthorizer objectAuthorizer;

    @Test
    void authorizesTheObjectBeforeReturningItsOrderedHistory()
    {
        StatusHistoryQueryService service = new StatusHistoryQueryServiceImpl(historyMapper,
                objectAuthorizer);
        StatusHistoryVo history = new StatusHistoryVo(81L, "REPAIR_ORDER", 7L,
                "IN_PROGRESS", "WAIT_ACCEPTANCE", 55L, "维修人员", "提交维修结果",
                "trace-81", LocalDateTime.of(2026, 9, 3, 11, 30));
        when(objectAuthorizer.normalizeObjectType("repair_order")).thenReturn("REPAIR_ORDER");
        when(historyMapper.selectByObject("REPAIR_ORDER", 7L)).thenReturn(List.of(history));

        List<StatusHistoryVo> result = service.list("repair_order", 7L, 55L);

        assertThat(result).containsExactly(history);
        verify(objectAuthorizer).assertReadable("REPAIR_ORDER", 7L, 55L);
    }
}
