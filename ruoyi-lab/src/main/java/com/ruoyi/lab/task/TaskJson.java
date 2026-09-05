package com.ruoyi.lab.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TaskJson
{
    private final ObjectMapper mapper;
    public TaskJson(ObjectMapper mapper) { this.mapper = mapper; }
    public String write(Object value)
    { try { return mapper.writeValueAsString(value); } catch (java.io.IOException e) { throw new IllegalStateException("任务序列化失败", e); } }
    public <T> T read(String value, Class<T> type)
    { try { return mapper.readValue(value, type); } catch (java.io.IOException e) { throw new IllegalArgumentException("任务参数无效", e); } }
    public <T> T convert(Object value, Class<T> type) { return mapper.convertValue(value, type); }
}
