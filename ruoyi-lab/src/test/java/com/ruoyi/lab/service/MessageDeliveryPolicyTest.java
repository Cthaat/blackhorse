package com.ruoyi.lab.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.Map;

class MessageDeliveryPolicyTest
{
    @Test void finiteRetryBudgetAndSafeTemplates()
    {
        assertThat(MessageDeliveryPolicy.retryMinutes(1)).isEqualTo(1);
        assertThat(MessageDeliveryPolicy.retryMinutes(4)).isEqualTo(60);
        assertThat(MessageDeliveryPolicy.retryMinutes(5)).isNull();
        assertThat(MessageDeliveryPolicy.render("状态：${eventType}", Map.of("eventType", "APPROVED")))
                .isEqualTo("状态：APPROVED");
        assertThatThrownBy(() -> MessageDeliveryPolicy.validateTemplate("${receiverId}", 128)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> MessageDeliveryPolicy.validateTemplate("<script>x</script>", 128)).isInstanceOf(RuntimeException.class);
        assertThat(MessageDeliveryPolicy.optional("WAITLIST_OFFERED")).isFalse();
        assertThat(MessageDeliveryPolicy.optional("HAZARD_OVERDUE")).isFalse();
        assertThat(MessageDeliveryPolicy.optional("INSPECTION_TASK_OVERDUE")).isTrue();
    }
}
