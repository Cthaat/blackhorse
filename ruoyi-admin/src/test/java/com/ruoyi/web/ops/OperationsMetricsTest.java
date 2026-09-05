package com.ruoyi.web.ops;

import java.time.*;
import java.util.UUID;
import com.ruoyi.framework.web.filter.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class OperationsMetricsTest
{
    @Test void routeLabelsComeFromMvcTemplatesAndHaveBoundedCardinality() throws Exception
    {
        HttpWindowMetrics metrics=new HttpWindowMetrics(Clock.systemUTC(),10);
        var filter=new HttpMetricsFilter(metrics);
        var request=new MockHttpServletRequest("GET","/lab/deliveries/987654321");
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)-> {
            req.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/lab/deliveries/{id}");
            ((jakarta.servlet.http.HttpServletResponse)res).setStatus(409);
        });
        var route=metrics.snapshot().routes().get(0);
        assertThat(route.template()).isEqualTo("/lab/deliveries/{id}");
        assertThat(route.clientErrors()).isEqualTo(1);
        for(int i=0;i<150;i++) metrics.record("/fixed-route-"+i,200,1);
        assertThat(metrics.snapshot().routes()).hasSizeLessThanOrEqualTo(101);
        assertThat(metrics.snapshot().routes()).anyMatch(row->"OTHER".equals(row.template()));
        assertThat(metrics.snapshot().toString()).doesNotContain("987654321");
    }
    @Test void boundedWindowSeparatesRejectionsFromSystemErrorsAndReportsTruncation()
    {
        Instant now=Instant.parse("2026-09-05T12:00:00Z");
        HttpWindowMetrics metrics=new HttpWindowMetrics(Clock.fixed(now,ZoneOffset.UTC),2);
        metrics.record(200,10);metrics.record(409,20);metrics.record(500,30);
        var snapshot=metrics.snapshot();
        assertThat(snapshot.requestCount()).isEqualTo(3);
        assertThat(snapshot.clientRefusals()).isEqualTo(1);
        assertThat(snapshot.businessRefusals()).isEqualTo(1);
        assertThat(snapshot.systemErrors()).isEqualTo(1);
        assertThat(snapshot.latencySampleCount()).isEqualTo(2);
        assertThat(snapshot.latencyTruncated()).isTrue();
        assertThat(snapshot.p95Millis()).isEqualTo(30);
        assertThat(new HttpWindowMetrics(Clock.fixed(now,ZoneOffset.UTC),2).snapshot().p95Millis()).isNull();
    }
    @Test void traceIdRejectsHeaderInjectionAndRestoresParentMdcEvenOnFailure() throws Exception
    {
        var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
        request.addHeader("X-Trace-Id","bad-header\r\nsecret");
        MDC.put("traceId","outer");
        try {
            assertThatThrownBy(()->new TraceIdFilter().doFilter(request,response,(req,res)-> {
                assertThat(MDC.get("traceId")).isEqualTo(response.getHeader("X-Trace-Id"));
                UUID.fromString(MDC.get("traceId"));
                throw new java.io.IOException("synthetic");
            })).isInstanceOf(java.io.IOException.class);
            assertThat(MDC.get("traceId")).isEqualTo("outer");
        } finally {MDC.remove("traceId");}
    }
    @Test void oldRequestsLeaveTheFiveMinuteWindow()
    {
        var instant=new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-09-05T12:00:00Z"));
        Clock clock=new Clock() {
            public ZoneId getZone() {return ZoneOffset.UTC;}
            public Clock withZone(ZoneId zone) {return this;}
            public Instant instant() {return instant.get();}
        };
        HttpWindowMetrics metrics=new HttpWindowMetrics(clock,10);
        metrics.record(500,120);
        instant.set(instant.get().plusSeconds(301));
        assertThat(metrics.snapshot().requestCount()).isZero();
        assertThat(metrics.snapshot().p95Millis()).isNull();
        assertThat(metrics.snapshot().systemErrorRate()).isNull();
    }
}
