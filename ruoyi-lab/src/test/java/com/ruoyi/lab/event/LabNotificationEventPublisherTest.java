package com.ruoyi.lab.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class LabNotificationEventPublisherTest
{
    @Test
    void publishesOnlyPersistedFactReferences()
    {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        LabNotificationEventPublisher publisher =
                new LabNotificationEventPublisher(springPublisher);
        ArgumentCaptor<LabNotificationRequested> events =
                ArgumentCaptor.forClass(LabNotificationRequested.class);

        publisher.publishHistory(801L);
        publisher.publishInspectionOverdue(91L, 3L);
        publisher.publishHazardOverdue(92L, 4L);

        verify(springPublisher, org.mockito.Mockito.times(3)).publishEvent(events.capture());
        assertThat(events.getAllValues()).containsExactly(
                LabNotificationRequested.history(801L),
                LabNotificationRequested.inspectionOverdue(91L, 3L),
                LabNotificationRequested.hazardOverdue(92L, 4L));
    }

    @Test
    void containsSpringPublicationFailuresSoCoreTransactionsCanCommit()
    {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        doThrow(new IllegalStateException("event bus unavailable"))
                .when(springPublisher).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
        LabNotificationEventPublisher publisher =
                new LabNotificationEventPublisher(springPublisher);

        assertThatCode(() -> publisher.publishHistory(801L)).doesNotThrowAnyException();
    }
}
