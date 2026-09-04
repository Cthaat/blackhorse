package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.service.LabNotificationDeliveryService;
import com.ruoyi.lab.service.LabNotificationPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Converts delivery failures into retryable facts without escaping after commit. */
@Service
public class LabNotificationDeliveryServiceImpl implements LabNotificationDeliveryService
{
    private static final Logger LOG =
            LoggerFactory.getLogger(LabNotificationDeliveryServiceImpl.class);

    private final LabNotificationPersistenceService persistenceService;
    private final Clock clock;

    public LabNotificationDeliveryServiceImpl(
            LabNotificationPersistenceService persistenceService, Clock clock)
    {
        this.persistenceService = persistenceService;
        this.clock = clock;
    }

    @Override
    public void deliverSafely(NotificationCommand command)
    {
        try
        {
            persistenceService.insertSent(command);
        }
        catch (RuntimeException deliveryFailure)
        {
            String errorCode = deliveryFailure instanceof DataAccessException
                    ? "DATA_ACCESS_ERROR" : "DELIVERY_ERROR";
            try
            {
                persistenceService.recordFailed(command, errorCode,
                        LocalDateTime.now(clock).plusMinutes(1));
            }
            catch (RuntimeException recordFailure)
            {
                LOG.error("Unable to persist failed laboratory notification; dedupeKey={}; code={}",
                        safeKey(command), errorCode);
            }
        }
    }

    private static String safeKey(NotificationCommand command)
    {
        return command == null || command.dedupeKey() == null ? "missing" : command.dedupeKey();
    }
}
