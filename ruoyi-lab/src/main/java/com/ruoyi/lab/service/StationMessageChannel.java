package com.ruoyi.lab.service;
import com.ruoyi.lab.dto.NotificationCommand;
import org.springframework.stereotype.Component;
@Component
public class StationMessageChannel implements MessageChannel
{
    private final LabNotificationPersistenceService persistence;
    public StationMessageChannel(LabNotificationPersistenceService persistence) { this.persistence=persistence; }
    public String code() { return "STATION"; }
    public long send(NotificationCommand command) { return persistence.insertSent(command); }
}
