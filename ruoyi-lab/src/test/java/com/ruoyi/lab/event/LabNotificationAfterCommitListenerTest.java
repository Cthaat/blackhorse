package com.ruoyi.lab.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.service.LabNotificationDeliveryService;
import com.ruoyi.lab.service.NotificationExpectationResolver;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class LabNotificationAfterCommitListenerTest
{
    @Test
    void resolvesAndDeliversHistoryOnlyAfterCommit() throws Exception
    {
        NotificationExpectationResolver resolver = mock(NotificationExpectationResolver.class);
        LabNotificationDeliveryService deliveryService = mock(LabNotificationDeliveryService.class);
        LabNotificationAfterCommitListener listener =
                new LabNotificationAfterCommitListener(resolver, deliveryService);
        NotificationCommand command = new NotificationCommand(
                "history:801:RESERVATION_APPROVED:18", 18L,
                "RESERVATION_APPROVED", "预约已批准", "预约申请已批准",
                "RESERVATION", 91L);
        when(resolver.resolveHistory(801L)).thenReturn(List.of(command));

        listener.handle(LabNotificationRequested.history(801L));

        verify(resolver).resolveHistory(801L);
        verify(deliveryService).deliverSafely(command);
        TransactionalEventListener annotation = LabNotificationAfterCommitListener.class
                .getMethod("handle", LabNotificationRequested.class)
                .getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void resolvesBothOverdueFactTypesAndContainsProjectionFailures()
    {
        NotificationExpectationResolver resolver = mock(NotificationExpectationResolver.class);
        LabNotificationDeliveryService deliveryService = mock(LabNotificationDeliveryService.class);
        LabNotificationAfterCommitListener listener =
                new LabNotificationAfterCommitListener(resolver, deliveryService);
        when(resolver.resolveInspectionOverdue(91L, 3L)).thenReturn(List.of());
        when(resolver.resolveHazardOverdue(92L, 4L))
                .thenThrow(new IllegalStateException("projection unavailable"));

        listener.handle(LabNotificationRequested.inspectionOverdue(91L, 3L));
        assertThatCode(() -> listener.handle(
                LabNotificationRequested.hazardOverdue(92L, 4L)))
                .doesNotThrowAnyException();

        verify(resolver).resolveInspectionOverdue(91L, 3L);
        verify(resolver).resolveHazardOverdue(92L, 4L);
    }
}
