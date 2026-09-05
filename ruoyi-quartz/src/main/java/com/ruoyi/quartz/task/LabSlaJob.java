package com.ruoyi.quartz.task;
import java.time.*;
import com.ruoyi.lab.config.LabJobProperties;
import com.ruoyi.lab.sla.SlaAlertService;
import org.springframework.stereotype.Component;
import org.slf4j.*;
@Component("labSlaJob")
public class LabSlaJob
{
    private static final Logger LOG=LoggerFactory.getLogger(LabSlaJob.class);
    private final SlaAlertService service;private final LabJobProperties properties;private final Clock clock;
    public LabSlaJob(SlaAlertService service,LabJobProperties properties,Clock clock){this.service=service;this.properties=properties;this.clock=clock;}
    public int scan(){int count=0;LocalDateTime now=LocalDateTime.now(clock);for(Long id:service.candidates(properties.getBatchSize())) {
        try {service.scan(id,now);count++;}catch(RuntimeException failure){LOG.error("SLA scan failed for record {}",id,failure);}
    }return count;}
}
