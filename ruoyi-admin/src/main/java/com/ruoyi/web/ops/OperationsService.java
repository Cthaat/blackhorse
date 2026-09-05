package com.ruoyi.web.ops;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import javax.sql.DataSource;
import com.alibaba.druid.pool.DruidDataSource;
import com.ruoyi.lab.mapper.OperationsMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

/** Authenticated, explicitly selected runtime measurements; never expose configuration or connection URLs. */
@Service
public class OperationsService
{
    private final DataSource dataSource;
    private final ObjectProvider<RedisConnectionFactory> redis;
    private final OperationsMapper mapper;
    private final HttpWindowMetrics http;
    private final Clock clock;
    public OperationsService(@Qualifier("masterDataSource") DataSource dataSource,
            ObjectProvider<RedisConnectionFactory> redis,OperationsMapper mapper,HttpWindowMetrics http,Clock clock)
    {this.dataSource=dataSource;this.redis=redis;this.mapper=mapper;this.http=http;this.clock=clock;}

    public Snapshot snapshot()
    {
        Section<Health> database=probeDatabase();
        Section<Health> redisHealth=probeRedis();
        Section<QueueMetrics> queues="UP".equals(database.status())
                ? sample("MySQL aggregate queries","current states; successful duration in past 24 hours",this::queues)
                : new Section<>("UNKNOWN",null,clock.instant(),"MySQL aggregate queries","current states; successful duration in past 24 hours","DATABASE_UNAVAILABLE");
        Runtime runtime=Runtime.getRuntime();
        Jvm jvm=new Jvm(ManagementFactory.getRuntimeMXBean().getUptime(),runtime.totalMemory()-runtime.freeMemory(),
                runtime.maxMemory(),ManagementFactory.getThreadMXBean().getThreadCount(),runtime.availableProcessors());
        return new Snapshot(clock.instant(),new Section<>("UP",new Health("Application request completed"),clock.instant(),"current application process","point in time",null),
                database,redisHealth,new Section<>("UP",jvm,clock.instant(),"JVM management beans","point in time",null),
                pool(),http.snapshot(),queues);
    }
    private Section<Health> probeDatabase()
    {
        try(Connection connection=dataSource instanceof DruidDataSource druid?druid.getConnection(2000):dataSource.getConnection();
                var statement=connection.createStatement())
        {
            statement.setQueryTimeout(2);
            try(var result=statement.executeQuery("SELECT 1"))
            {
                if(!result.next()||result.getInt(1)!=1) throw new IllegalStateException("Probe failed");
            }
            return new Section<>("UP",new Health("SELECT 1 succeeded"),clock.instant(),"primary database SELECT 1","point in time",null);
        }
        catch(Exception failure) {return new Section<>("DOWN",null,clock.instant(),"primary database SELECT 1","point in time","DATABASE_UNAVAILABLE");}
    }
    private Section<Health> probeRedis()
    {
        RedisConnectionFactory factory=redis.getIfAvailable();
        if(factory==null) return new Section<>("UNKNOWN",null,clock.instant(),"Redis PING","point in time","NOT_CONFIGURED");
        try(var connection=factory.getConnection())
        {
            if(!"PONG".equalsIgnoreCase(connection.ping())) throw new IllegalStateException("Probe failed");
            return new Section<>("UP",new Health("PING succeeded"),clock.instant(),"Redis PING","point in time",null);
        }
        catch(Exception failure) {return new Section<>("DEGRADED",null,clock.instant(),"Redis PING","point in time","REDIS_UNAVAILABLE");}
    }
    private Section<Pool> pool()
    {
        if(dataSource instanceof DruidDataSource druid)
            return new Section<>("UP",new Pool(druid.getActiveCount(),druid.getPoolingCount(),druid.getMaxActive()),clock.instant(),"Druid primary pool counters","point in time",null);
        return new Section<>("UNKNOWN",null,clock.instant(),"Druid primary pool counters","point in time","POOL_METRICS_UNAVAILABLE");
    }
    private QueueMetrics queues()
    {
        var deliveries=mapper.deliveries();var tasks=mapper.tasks();
        LocalDateTime oldest=mapper.oldestDelivery();
        Long age=oldest==null?null:Math.max(0,Duration.between(oldest,LocalDateTime.now(clock)).getSeconds());
        var duration=mapper.taskDuration(LocalDateTime.now(clock).minusHours(24));
        long backlog=deliveries.stream().filter(row->List.of("PENDING","PROCESSING","RETRY_WAIT").contains(row.status())).mapToLong(OperationsMapper.StateCount::count).sum();
        return new QueueMetrics(deliveries,backlog,age,tasks,duration,
                new QueueReferences(id(mapper.oldestDeliveryId()),id(mapper.failedDeliveryId()),id(mapper.oldestTaskId()),id(mapper.failedTaskId())));
    }
    private static String id(Long value) {return value==null?null:value.toString();}
    private <T> Section<T> sample(String source,String window,Supplier<T> supplier)
    {
        try {return new Section<>("UP",supplier.get(),clock.instant(),source,window,null);}
        catch(RuntimeException failure) {return new Section<>("UNKNOWN",null,clock.instant(),source,window,"METRICS_UNAVAILABLE");}
    }
    public record Section<T>(String status,T data,Instant sampledAt,String source,String window,String errorCode) { }
    public record Health(String result) { }
    public record Jvm(long uptimeMillis,long heapUsedBytes,long heapMaxBytes,int threadCount,int processors) { }
    public record Pool(int activeConnections,int idleConnections,int maximumConnections) { }
    public record QueueMetrics(List<OperationsMapper.StateCount> deliveries,long deliveryBacklog,Long oldestDeliveryAgeSeconds,
            List<OperationsMapper.StateCount> tasks,OperationsMapper.DurationSummary successfulTaskDuration,QueueReferences references) { }
    public record QueueReferences(String oldestDeliveryId,String failedDeliveryId,String oldestTaskId,String failedTaskId) { }
    public record Snapshot(Instant sampledAt,Section<Health> backend,Section<Health> database,Section<Health> redis,
            Section<Jvm> jvm,Section<Pool> pool,HttpWindowMetrics.Snapshot http,Section<QueueMetrics> queues) { }
}
