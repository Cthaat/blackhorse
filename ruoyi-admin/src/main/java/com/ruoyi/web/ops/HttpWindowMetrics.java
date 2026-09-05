package com.ruoyi.web.ops;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Bounded MVC templates and aggregate counters; no raw URL, user or request data is retained. */
@Component
public class HttpWindowMetrics
{
    private static final int WINDOW_SECONDS=300;
    private final Clock clock;
    private final int sampleLimit;
    private final Instant started;
    private final Bucket[] buckets=new Bucket[WINDOW_SECONDS+1];
    private final ArrayDeque<Sample> latency=new ArrayDeque<>();
    private final Set<String> templates=new HashSet<>();
    private static final int TEMPLATE_LIMIT=100;

    @Autowired public HttpWindowMetrics(Clock clock) {this(clock,10000);}
    HttpWindowMetrics(Clock clock,int sampleLimit)
    {
        if(sampleLimit<1) throw new IllegalArgumentException("sampleLimit must be positive");
        this.clock=clock;this.sampleLimit=sampleLimit;this.started=clock.instant();
    }
    public synchronized void record(int status,double durationMillis)
    {record("OTHER",status,durationMillis);}
    public synchronized void record(String mvcTemplate,int status,double durationMillis)
    {
        long second=clock.instant().getEpochSecond();
        int index=Math.floorMod(second,buckets.length);
        Bucket bucket=buckets[index];
        if(bucket==null||bucket.second!=second) {bucket=new Bucket(second);buckets[index]=bucket;}
        bucket.requests++;
        if(status>=400&&status<500) bucket.clientRefusals++;
        if(status==400||status==409||status==422) bucket.businessRefusals++;
        if(status>=500) bucket.systemErrors++;
        String template=template(mvcTemplate);
        long[] categories=bucket.routes.computeIfAbsent(template,key->new long[6]);
        categories[Math.max(0,Math.min(5,status/100))]++;
        prune(second);
        if(latency.size()==sampleLimit) latency.removeFirst();
        latency.addLast(new Sample(second,Math.max(0,durationMillis)));
    }
    public synchronized Snapshot snapshot()
    {
        Instant now=clock.instant();long cutoff=now.getEpochSecond()-WINDOW_SECONDS;
        prune(now.getEpochSecond());
        long requests=0,client=0,business=0,errors=0;
        Map<String,long[]> routes=new java.util.TreeMap<>();
        for(Bucket bucket:buckets) if(bucket!=null&&bucket.second>cutoff&&bucket.second<=now.getEpochSecond())
        {
            requests+=bucket.requests;client+=bucket.clientRefusals;business+=bucket.businessRefusals;errors+=bucket.systemErrors;
            bucket.routes.forEach((name,counts)-> {
                long[] total=routes.computeIfAbsent(name,key->new long[6]);
                for(int i=0;i<counts.length;i++) total[i]+=counts[i];
            });
        }
        double[] values=latency.stream().mapToDouble(Sample::millis).toArray();Arrays.sort(values);
        Double p95=values.length==0?null:values[(int)Math.ceil(values.length*.95)-1];
        Instant start=Instant.ofEpochSecond(cutoff+1);if(start.isBefore(started)) start=started;
        return new Snapshot(start,now,requests,client,business,errors,requests==0?null:(double)errors/requests,
                p95,values.length,requests>values.length,sampleLimit,
                routes.entrySet().stream().map(entry->new RouteSnapshot(entry.getKey(),Arrays.stream(entry.getValue()).sum(),entry.getValue()[2],entry.getValue()[3],entry.getValue()[4],entry.getValue()[5])).toList(),
                "MVC route templates (maximum 100 plus OTHER), HTTP response status; 400/409/422 validation/conflict refusals; this instance");
    }
    private String template(String value)
    {
        if(value==null||value.length()>200||"OTHER".equals(value)) return "OTHER";
        if(templates.contains(value)) return value;
        if(templates.size()>=TEMPLATE_LIMIT) return "OTHER";
        templates.add(value);return value;
    }
    private void prune(long second) {while(!latency.isEmpty()&&latency.peekFirst().second<=second-WINDOW_SECONDS) latency.removeFirst();}
    private record Sample(long second,double millis) { }
    private static final class Bucket
    {
        final long second;long requests,clientRefusals,businessRefusals,systemErrors;
        final Map<String,long[]> routes=new HashMap<>();
        Bucket(long second) {this.second=second;}
    }
    public record Snapshot(Instant windowStart,Instant sampledAt,long requestCount,long clientRefusals,
            long businessRefusals,long systemErrors,Double systemErrorRate,Double p95Millis,int latencySampleCount,
            boolean latencyTruncated,int latencySampleLimit,List<RouteSnapshot> routes,String source) { }
    public record RouteSnapshot(String template,long requestCount,long success,long redirects,long clientErrors,long serverErrors) { }
}
