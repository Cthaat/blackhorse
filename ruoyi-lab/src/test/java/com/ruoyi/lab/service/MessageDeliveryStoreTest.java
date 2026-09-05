package com.ruoyi.lab.service;

import java.time.*;
import java.util.List;
import com.ruoyi.lab.domain.LabMessageDelivery;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.mapper.LabMessageDeliveryMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MessageDeliveryStoreTest
{
    private final LabMessageDeliveryMapper mapper=mock(LabMessageDeliveryMapper.class);
    private final MessageTemplateService templates=mock(MessageTemplateService.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"),ZoneOffset.UTC);
    private final LocalDateTime now=LocalDateTime.now(clock);
    private final MessageDeliveryStore store=new MessageDeliveryStore(mapper,templates,clock);

    @Test void registeredSnapshotIsNeverReRenderedOnRetry()
    {
        LabMessageDelivery row=row(1);
        when(mapper.byKey("history:1:RESERVATION_APPROVED:3")).thenReturn(row);
        assertThat(store.register(new NotificationCommand("history:1:RESERVATION_APPROVED:3",3L,"RESERVATION_APPROVED","new title","new content","RESERVATION",9L))).isEqualTo(8L);
        verifyNoInteractions(templates);
        verify(mapper,never()).register(any());
    }
    @Test void fifthFailureStopsAutomaticRetryAndAuditsResult()
    {
        LabMessageDelivery row=row(5);
        when(mapper.finish(eq(8L),eq(1),eq("MANUAL_REQUIRED"),eq("DELIVERY_ERROR"),isNull(),eq(now))).thenReturn(1);
        store.finish(row,"DELIVERY_ERROR",now);
        verify(mapper).audit(eq(8L),eq("RESULT"),eq(5),isNull(),isNull(),eq("MANUAL_REQUIRED"),eq("DELIVERY_ERROR"),isNull(),eq(now));
    }
    @Test void replayRequiresRealFactAndReasonAndRetainsPreviousAttempts()
    {
        LabMessageDelivery row=row(5);row.status="MANUAL_REQUIRED";row.sourceType="STATUS_HISTORY";row.sourceId=1L;
        when(mapper.locked(8L)).thenReturn(row);
        assertThatThrownBy(()->store.replay(8L," ",7L)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->store.replay(8L,"数据库已恢复",7L)).isInstanceOf(RuntimeException.class);
        when(mapper.historyExists(1L)).thenReturn(1);when(mapper.replay(8L,now)).thenReturn(1);
        store.replay(8L,"数据库已恢复",7L);
        verify(mapper).audit(8L,"REPLAY",5,7L,"数据库已恢复","PENDING",null,null,now);
    }
    @Test void expiredFifthAttemptRecognizesAlreadyDeliveredInbox()
    {
        LabMessageDelivery row=row(5);row.dedupeKey="history:1:RESERVATION_APPROVED:3";
        when(mapper.expired(now,10)).thenReturn(List.of(row));
        when(mapper.inboxSent(row.dedupeKey)).thenReturn(1);
        store.recover(now,10);
        verify(mapper).finish(8L,1,"DELIVERED",null,null,now);
    }
    @Test void earlyRetryPreservesAttemptBudgetAndAuditsReason()
    {
        LabMessageDelivery row=row(2);row.status="RETRY_WAIT";row.sourceType="STATUS_HISTORY";row.sourceId=1L;
        when(mapper.locked(8L)).thenReturn(row);when(mapper.historyExists(1L)).thenReturn(1);
        when(mapper.retryNow(8L,now)).thenReturn(1);
        try(var security=mockStatic(com.ruoyi.common.utils.SecurityUtils.class))
        {
            security.when(() -> com.ruoyi.common.utils.SecurityUtils.hasPermi("lab:delivery:retry")).thenReturn(true);
            security.when(com.ruoyi.common.utils.SecurityUtils::getUserId).thenReturn(7L);
            store.retryNow(8L,"数据库已恢复",7L);
        }
        verify(mapper).audit(8L,"RETRY_NOW",2,7L,"数据库已恢复","PENDING",null,null,now);
        verify(mapper,never()).replay(any(),any());
        verifyNoInteractions(templates);
    }
    @Test void earlyRetryRejectsExhaustedBudgetMissingFactAndUnauthorizedActor()
    {
        LabMessageDelivery row=row(5);row.status="RETRY_WAIT";row.sourceType="STATUS_HISTORY";row.sourceId=1L;
        when(mapper.locked(8L)).thenReturn(row);
        try(var security=mockStatic(com.ruoyi.common.utils.SecurityUtils.class))
        {
            assertThatThrownBy(() -> store.retryNow(8L,"原因",7L)).isInstanceOf(RuntimeException.class);
            security.when(() -> com.ruoyi.common.utils.SecurityUtils.hasPermi("lab:delivery:retry")).thenReturn(true);
            security.when(com.ruoyi.common.utils.SecurityUtils::getUserId).thenReturn(7L);
            assertThatThrownBy(() -> store.retryNow(8L,"原因",7L)).isInstanceOf(RuntimeException.class);
            row.attemptCount=2;
            assertThatThrownBy(() -> store.retryNow(8L,"原因",7L)).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> store.retryNow(8L," ",7L)).isInstanceOf(RuntimeException.class);
        }
        verify(mapper,never()).retryNow(any(),any());
    }
    private static LabMessageDelivery row(int attempt) {var row=new LabMessageDelivery();row.id=8L;row.attemptCount=attempt;row.executionVersion=1;return row;}
}
