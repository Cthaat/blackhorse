package com.ruoyi.lab.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.ruoyi.lab.domain.LabMessageTemplate;
import com.ruoyi.lab.dto.MessageTemplateDto;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.mapper.LabMessageTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageTemplateService
{
    private final LabMessageTemplateMapper mapper;
    private final Clock clock;
    public MessageTemplateService(LabMessageTemplateMapper mapper, Clock clock) { this.mapper=mapper; this.clock=clock; }
    public List<LabMessageTemplate> list(String type) { return LabPage.query(() -> mapper.list(type)); }
    public boolean preference(Long user) { return !Boolean.FALSE.equals(mapper.preference(user)); }
    public void preference(Long user, boolean enabled) { mapper.preferenceUpdate(user,enabled,LocalDateTime.now(clock)); }

    @Transactional
    public Long save(Long id, MessageTemplateDto dto, Long user)
    {
        validate(dto);
        LabMessageTemplate row=new LabMessageTemplate();
        row.id=id; row.eventType=dto.eventType(); row.title=dto.title(); row.content=dto.content();
        row.operatorId=user; row.createTime=LocalDateTime.now(clock);
        if (id==null) mapper.insert(row);
        else if (mapper.edit(row)!=1) throw MessageDeliveryPolicy.invalid("模板不存在或已发布，请创建新草稿");
        return row.id;
    }
    @Transactional
    public void publish(Long id,Long user)
    {
        LabMessageTemplate row=mapper.byId(id);
        if (row==null) throw MessageDeliveryPolicy.invalid("模板不存在");
        validate(new MessageTemplateDto(row.eventType,row.title,row.content));
        if(mapper.publish(id,user,LocalDateTime.now(clock))!=1) throw MessageDeliveryPolicy.invalid("模板已发布，不能重复发布");
    }
    public Map<String,String> preview(MessageTemplateDto dto)
    {
        validate(dto);
        return Map.of("title",MessageDeliveryPolicy.render(dto.title(),samples(dto.eventType())),
                "content",MessageDeliveryPolicy.render(dto.content(),samples(dto.eventType())));
    }
    public Snapshot snapshot(NotificationCommand command)
    {
        LabMessageTemplate template=mapper.active(command.notificationType());
        if(template==null) return new Snapshot("builtin:1",command.title(),command.content());
        Map<String,String> values=Map.of("eventType",command.notificationType(),"businessType",command.businessType(),
                "businessId",command.businessId().toString(),"title",command.title(),"content",command.content());
        String title=MessageDeliveryPolicy.render(template.title,values);
        String content=MessageDeliveryPolicy.render(template.content,values);
        // A custom template can grow beyond inbox limits after substitution; the versioned fallback is safe.
        if(title.isBlank()||content.isBlank()||title.length()>128||content.length()>500)
            return new Snapshot("builtin:1",command.title(),command.content());
        return new Snapshot("custom:"+template.id,title,content);
    }
    private static Map<String,String> samples(String event) { return Map.of("eventType",event,"businessType","RESERVATION","businessId","123","title","业务状态已更新","content","业务单据状态已更新"); }
    private static void validate(MessageTemplateDto dto)
    {
        if(dto==null||dto.eventType()==null||!dto.eventType().matches("(?:RESERVATION|REPAIR_ORDER|INSPECTION_TASK|HAZARD|RESTRICTION)_[A-Z_]+|WAITLIST_OFFERED")||dto.eventType().length()>32)
            throw MessageDeliveryPolicy.invalid("事件类型无效");
        MessageDeliveryPolicy.validateTemplate(dto.title(),128); MessageDeliveryPolicy.validateTemplate(dto.content(),500);
    }
    public record Snapshot(String version,String title,String content) { }
}
