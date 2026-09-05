package com.ruoyi.web.ops;

import java.sql.SQLException;
import java.time.Clock;
import javax.sql.DataSource;
import com.ruoyi.lab.mapper.OperationsMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationsServiceTest
{
    @Test void dependencyFailuresAreUnknownMetricsNotHealthyZeros() throws Exception
    {
        DataSource dataSource=mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("sensitive database URL"));
        @SuppressWarnings("unchecked") ObjectProvider<RedisConnectionFactory> provider=mock(ObjectProvider.class);
        RedisConnectionFactory redis=mock(RedisConnectionFactory.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        when(redis.getConnection()).thenThrow(new IllegalStateException("sensitive redis endpoint"));
        OperationsMapper mapper=mock(OperationsMapper.class);
        OperationsService service=new OperationsService(dataSource,provider,mapper,new HttpWindowMetrics(Clock.systemUTC()),Clock.systemUTC());
        var snapshot=service.snapshot();
        assertThat(snapshot.database().status()).isEqualTo("DOWN");
        assertThat(snapshot.redis().status()).isEqualTo("DEGRADED");
        assertThat(snapshot.queues().data()).isNull();
        assertThat(snapshot.pool().data()).isNull();
        assertThat(snapshot.jvm().data().uptimeMillis()).isPositive();
        assertThat(snapshot.toString()).doesNotContain("sensitive");
        verifyNoInteractions(mapper);
    }
    @Test void operationsRouteRequiresDedicatedPermission() throws Exception
    {
        var authorization=LabOperationsController.class.getMethod("snapshot").getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
        assertThat(authorization.value()).isEqualTo("@ss.hasPermi('lab:operations:view')");
    }
}
