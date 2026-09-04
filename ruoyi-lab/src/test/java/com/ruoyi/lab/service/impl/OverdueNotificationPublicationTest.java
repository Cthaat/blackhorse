package com.ruoyi.lab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.domain.LabInspectionTask;
import com.ruoyi.lab.event.LabNotificationEventPublisher;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.service.LabSystemOperator;
import com.ruoyi.lab.service.LabSystemOperatorProvider;
import org.junit.jupiter.api.Test;

class OverdueNotificationPublicationTest
{
    @Test
    void inspectionPublishesTheVersionReadBackAfterConditionalUpdate()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 9, 0);
        LabInspectionTask candidate = new LabInspectionTask();
        candidate.setId(41L);
        candidate.setVersion(3);
        LabInspectionTask persisted = new LabInspectionTask();
        persisted.setId(41L);
        persisted.setOverdueEventVersion(7L);
        LabInspectionTaskMapper mapper = mock(LabInspectionTaskMapper.class);
        when(mapper.selectOverdueCandidates(now, 100)).thenReturn(List.of(candidate));
        when(mapper.markOneOverdue(41L, 3, now, "system")).thenReturn(1);
        when(mapper.selectActiveById(41L)).thenReturn(persisted);
        LabNotificationEventPublisher publisher = mock(LabNotificationEventPublisher.class);
        LabSystemOperatorProvider operators = mock(LabSystemOperatorProvider.class);
        when(operators.requiredOperator()).thenReturn(new LabSystemOperator(1L, "system"));

        int changed = new InspectionLifecycleServiceImpl(mapper, operators, publisher)
                .markOverdue(now, 100);

        assertThat(changed).isEqualTo(1);
        verify(publisher).publishInspectionOverdue(41L, 7L);
    }
}
