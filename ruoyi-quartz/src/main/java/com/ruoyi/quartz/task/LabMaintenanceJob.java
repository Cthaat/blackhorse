package com.ruoyi.quartz.task;

import java.time.*;
import com.ruoyi.lab.config.LabJobProperties;
import com.ruoyi.lab.maintenance.MaintenanceService;
import org.slf4j.*;
import org.springframework.stereotype.Component;

/** Due time creates only a planned cycle; it never changes equipment availability. */
@Component("labMaintenanceJob")
public class LabMaintenanceJob
{
    private static final Logger LOG=LoggerFactory.getLogger(LabMaintenanceJob.class);
    private final MaintenanceService service;
    private final LabJobProperties properties;
    private final Clock clock;
    public LabMaintenanceJob(MaintenanceService service,LabJobProperties properties,Clock clock) {this.service=service;this.properties=properties;this.clock=clock;}
    public int generate()
    {
        LocalDateTime now=LocalDateTime.now(clock);int count=0;
        for (Long id:service.due(now,properties.getBatchSize()))
        {
            try { if (service.generate(id,now)) count++; }
            catch (RuntimeException failure) { LOG.error("Maintenance generation failed for plan {}",id,failure); }
        }
        return count;
    }
}
