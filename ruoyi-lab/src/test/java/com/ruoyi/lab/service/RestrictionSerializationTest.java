package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.lab.restriction.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class RestrictionSerializationTest
{
    @Test void largeBusinessIdsAndEvidenceIdsRemainExactStrings() throws Exception
    {
        long large=9007199254740993L;
        var mapper=new ObjectMapper();
        var row=new RestrictionRecord();row.id=large;row.userId=large;
        assertThat(mapper.readTree(mapper.writeValueAsString(row)).path("id").isTextual()).isTrue();
        var appeal=new RestrictionAppeal();appeal.reviewerId=large;appeal.attachmentIds=List.of(large);
        var json=mapper.readTree(mapper.writeValueAsString(appeal));
        assertThat(json.path("attachmentIds").get(0).asText()).isEqualTo("9007199254740993");
        assertThat(json.path("attachmentIds").get(0).isTextual()).isTrue();
        assertThat(json.path("reviewerId").isTextual()).isTrue();
    }
}
