package com.ruoyi.lab.service;

import java.time.Clock;
import com.ruoyi.lab.domain.LabMessageTemplate;
import com.ruoyi.lab.dto.MessageTemplateDto;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.mapper.LabMessageTemplateMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageTemplateServiceTest
{
    private final LabMessageTemplateMapper mapper=mock(LabMessageTemplateMapper.class);
    private final MessageTemplateService service=new MessageTemplateService(mapper,Clock.systemUTC());
    @Test void customVersionIsRenderedAsPlainTextAndOversizeUsesVersionedFallback()
    {
        LabMessageTemplate template=new LabMessageTemplate();template.id=7L;template.title="${title}";template.content="事件 ${eventType}: ${content}";
        when(mapper.active("WAITLIST_OFFERED")).thenReturn(template);
        NotificationCommand command=new NotificationCommand("WAITLIST:1:OFFERED",3L,"WAITLIST_OFFERED","邀请","请确认","WAITLIST",1L);
        assertThat(service.snapshot(command)).isEqualTo(new MessageTemplateService.Snapshot("custom:7","邀请","事件 WAITLIST_OFFERED: 请确认"));
        template.content="${content}".repeat(200);
        assertThat(service.snapshot(command).version()).isEqualTo("builtin:1");
    }
    @Test void previewRejectsExpressionsAndDefaultPreferenceIsEnabled()
    {
        assertThatThrownBy(()->service.preview(new MessageTemplateDto("WAITLIST_OFFERED","${T(java.lang.Runtime)}","内容"))).isInstanceOf(RuntimeException.class);
        when(mapper.preference(9L)).thenReturn(null);
        assertThat(service.preference(9L)).isTrue();
        when(mapper.preference(9L)).thenReturn(false);
        assertThat(service.preference(9L)).isFalse();
    }
}
