package com.ruoyi.web.service;

import java.util.UUID;
import java.util.concurrent.*;
import com.ruoyi.lab.mapper.BusinessTaskMapper;
import com.ruoyi.lab.task.BusinessTaskWorker;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.LoggerFactory;

@Configuration
@EnableScheduling
public class BusinessTaskRuntime
{
    private final BusinessTaskMapper mapper;
    private final BusinessTaskWorker worker;
    private final ConcurrentMap<Long,String> active=new ConcurrentHashMap<>();
    private final ExecutorService pool=Executors.newFixedThreadPool(2,r->{var t=new Thread(r,"lab-business-worker");t.setDaemon(true);return t;});
    public BusinessTaskRuntime(BusinessTaskMapper mapper,BusinessTaskWorker worker){this.mapper=mapper;this.worker=worker;}
    @Scheduled(initialDelay=10000,fixedDelay=5000) public void dispatch()
    {
        mapper.recover();
        for(long id:mapper.queued()) {
            if(active.size()>=2)break;
            String token=UUID.randomUUID().toString();
            if(mapper.claim(id,token)!=1)continue;
            active.put(id,token);
            pool.submit(()->{try{worker.run(id,token);}finally{active.remove(id);}});
        }
    }
    @Scheduled(initialDelay=15000,fixedDelay=15000) public void heartbeat()
    {active.forEach((id,token)->mapper.heartbeat(id,token));}
    @Scheduled(initialDelay=60000,fixedDelay=60000) public void cleanup(){worker.cleanup();}
    @PreDestroy public void close()
    {
        pool.shutdown();try{if(!pool.awaitTermination(30,TimeUnit.SECONDS))pool.shutdownNow();}
        catch(InterruptedException e){Thread.currentThread().interrupt();pool.shutdownNow();}
    }
}
