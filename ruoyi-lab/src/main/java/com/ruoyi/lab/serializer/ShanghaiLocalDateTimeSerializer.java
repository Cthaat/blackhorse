package com.ruoyi.lab.serializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.ruoyi.lab.config.LabTimeConfig;

/** Writes a business LocalDateTime as ISO-8601 with the Asia/Shanghai offset. */
public final class ShanghaiLocalDateTimeSerializer extends JsonSerializer<LocalDateTime>
{
    @Override
    public void serialize(LocalDateTime value, JsonGenerator generator,
            SerializerProvider serializers) throws IOException
    {
        generator.writeString(format(value));
    }

    static String format(LocalDateTime value)
    {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                value.atZone(LabTimeConfig.LAB_ZONE).toOffsetDateTime());
    }
}
