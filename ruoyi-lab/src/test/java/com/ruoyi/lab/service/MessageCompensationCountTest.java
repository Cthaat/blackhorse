package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.List;
import com.ruoyi.lab.domain.LabReservationWaitlist;
import com.ruoyi.lab.mapper.*;
import com.ruoyi.lab.service.impl.LabNotificationCompensationServiceImpl;
import org.junit.jupiter.api.Test;

class MessageCompensationCountTest
{
    @Test void reconciliationCountIncludesWaitlistFactsRegisteredForDelivery()
    {
        var mapper=mock(LabMessageDeliveryMapper.class);
        var waitlist=new LabReservationWaitlist();waitlist.setId(1L);waitlist.setApplicantId(2L);
        when(mapper.missingWaitlists(10)).thenReturn(List.of(waitlist));
        var engine=new MessageDeliveryEngine(mock(MessageDeliveryStore.class),mapper,mock(MessageChannel.class),Clock.systemUTC());
        var service=new LabNotificationCompensationServiceImpl(mock(LabStatusHistoryMapper.class),
                mock(LabInspectionTaskMapper.class),mock(LabHazardMapper.class),mock(NotificationExpectationResolver.class),
                mock(LabNotificationDeliveryService.class),engine);
        assertThat(service.reconcileStatusHistory(LocalDateTime.now(),10)).isEqualTo(1);
    }
}
