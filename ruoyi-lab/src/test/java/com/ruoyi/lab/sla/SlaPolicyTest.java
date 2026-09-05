package com.ruoyi.lab.sla;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SlaPolicyTest
{
    @Test void maximumLengthResumeReasonIsPreservedWithoutExceedingStorageBound()
    {
        var mapper=org.mockito.Mockito.mock(com.ruoyi.lab.mapper.LabSlaMapper.class);
        var lifecycle=new SlaLifecycle(mapper);var record=new SlaRecord();record.id=1L;
        var now=LocalDateTime.of(2026,9,5,10,0);record.pausedAt=now.minusHours(1);record.processingDueAt=now.plusHours(2);record.totalPausedSeconds=0L;
        String reason="因".repeat(500);lifecycle.finishPause(record,now,2L,reason);
        org.mockito.Mockito.verify(mapper).trace(1L,"PAUSE_ENDED",reason,2L,now);
        assertThat(record.totalPausedSeconds).isEqualTo(3600L);
    }
    @Test void pausePreservesResponseDeadlineAndAddsOnlyElapsedProcessingSeconds()
    {
        assertThatCode(()-> {
            Class<?> policy=Class.forName("com.ruoyi.lab.sla.SlaPolicy");
            var due=LocalDateTime.of(2026,9,5,10,0);
            Object shifted=policy.getMethod("resumeDue",LocalDateTime.class,LocalDateTime.class,LocalDateTime.class)
                    .invoke(null,due,due.minusHours(2),due.minusHours(1));
            assertThat(shifted).isEqualTo(due.plusHours(1));
        }).doesNotThrowAnyException();
    }
    @Test void closedOrCompletedPhaseNeverAlerts()
    {
        assertThatCode(()-> {
            Class<?> policy=Class.forName("com.ruoyi.lab.sla.SlaPolicy");
            var now=LocalDateTime.of(2026,9,5,10,0);
            var stage=policy.getMethod("stage",LocalDateTime.class,int.class,LocalDateTime.class,boolean.class);
            assertThat(stage.invoke(null,now.minusHours(30),8,now,true)).isNull();
            assertThat(stage.invoke(null,now.minusHours(30),8,now,false)).isEqualTo("ESCALATED");
            assertThat(stage.invoke(null,now.plusMinutes(20),8,now,false)).isEqualTo("NEAR_DUE");
        }).doesNotThrowAnyException();
    }
}
