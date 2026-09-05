package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.mapper.LabMessageDeliveryMapper;
import com.ruoyi.lab.vo.MessageDeliveryVo;
import com.ruoyi.lab.vo.MessageAttemptVo;
import org.springframework.stereotype.Service;

@Service
public class MessageDeliveryQueryService
{
    private final LabMessageDeliveryMapper mapper;
    public MessageDeliveryQueryService(LabMessageDeliveryMapper mapper) { this.mapper=mapper; }
    public List<MessageDeliveryVo> list(String status,String eventType) { return LabPage.query(() -> mapper.list(status,eventType)); }
    public Detail detail(Long id)
    {
        MessageDeliveryVo row=mapper.metadata(id);
        if(row==null) throw MessageDeliveryPolicy.invalid("投递记录不存在");
        return new Detail(row,mapper.attempts(id));
    }
    public record Detail(MessageDeliveryVo delivery,List<MessageAttemptVo> attempts) { }
}
