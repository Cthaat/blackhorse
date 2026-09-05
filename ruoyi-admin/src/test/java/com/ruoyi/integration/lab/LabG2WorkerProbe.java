package com.ruoyi.integration.lab;

import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.lab.mapper.BusinessTaskMapper;
import com.ruoyi.lab.service.LabIdempotencyStore;
import com.ruoyi.lab.task.BusinessTaskWorker;
import com.ruoyi.lab.task.TaskBusinessAdapter;
import com.ruoyi.quartz.service.ISysJobService;
import com.ruoyi.web.service.BusinessTaskRuntime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

/** Test-only JVM: real worker/transactions, with a deterministic kill point before row two commits. */
@SpringBootTest(classes=RuoYiApplication.class, properties={"spring.quartz.auto-startup=false","lab.demo-data.enabled=false"})
@ActiveProfiles("test")
public class LabG2WorkerProbe
{
    @Autowired BusinessTaskWorker worker;
    @Autowired BusinessTaskMapper mapper;
    @MockitoSpyBean TaskBusinessAdapter business;
    @MockitoBean BusinessTaskRuntime runtime;
    @MockitoBean RedisCache redis;
    @MockitoBean TokenService tokens;
    @MockitoBean LabIdempotencyStore idempotency;
    @MockitoBean ISysJobService jobs;

    public static void main(String[] args) throws Exception
    {
        if (!"true".equals(System.getenv("LAB_TEST_WRAPPER_ACTIVE"))
                || !System.getenv().getOrDefault("LAB_TEST_DB_URL", "").contains("/lab_test_"))
            throw new IllegalStateException("Isolated test database required");
        var probe = new LabG2WorkerProbe();
        new TestContextManager(LabG2WorkerProbe.class).prepareTestInstance(probe);
        long id = Long.parseLong(args[0]);
        if ("interrupt".equals(args[1])) {
            var count = new AtomicInteger();
            doAnswer(call -> {
                if (count.incrementAndGet() == 2) {
                    System.out.println("G2_CHECKPOINT_READY");
                    System.out.flush();
                    new CountDownLatch(1).await(); // Parent forcibly terminates this exact child JVM.
                }
                return call.callRealMethod();
            }).when(probe.business).create(anyString(), anyMap(), any());
        } else {
            probe.mapper.recover();
        }
        String token = UUID.randomUUID().toString();
        if (probe.mapper.claim(id, token) != 1) throw new IllegalStateException("Task not claimable");
        probe.worker.run(id, token);
        System.out.println("G2_PROCESS_RESULT=" + probe.mapper.get(id).status);
        System.out.flush();
        System.exit("SUCCEEDED".equals(probe.mapper.get(id).status) ? 0 : 2);
    }
}
