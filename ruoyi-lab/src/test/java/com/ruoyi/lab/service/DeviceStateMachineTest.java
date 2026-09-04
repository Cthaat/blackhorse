package com.ruoyi.lab.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.dto.DeviceStatusCommandDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class DeviceStateMachineTest
{
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void allowsOnlyM2CandidateTransitions()
    {
        assertTrue(DeviceStatus.AVAILABLE.canMoveTo(DeviceStatus.FAULT));
        assertTrue(DeviceStatus.AVAILABLE.canMoveTo(DeviceStatus.DISABLED));
        assertTrue(DeviceStatus.FAULT.canMoveTo(DeviceStatus.DISABLED));
        assertTrue(DeviceStatus.DISABLED.canMoveTo(DeviceStatus.AVAILABLE));
    }

    @Test
    void rejectsSameUnknownAndFaultRecoveryTransitions()
    {
        assertFalse(DeviceStatus.AVAILABLE.canMoveTo(DeviceStatus.AVAILABLE));
        assertFalse(DeviceStatus.AVAILABLE.canMoveTo(null));
        assertFalse(DeviceStatus.FAULT.canMoveTo(DeviceStatus.AVAILABLE));
        assertFalse(DeviceStatus.IN_USE.canMoveTo(DeviceStatus.AVAILABLE));
        assertFalse(DeviceStatus.MAINTENANCE.canMoveTo(DeviceStatus.AVAILABLE));
    }

    @Test
    void reasonMustContainOneToFiveHundredTrimmedCharacters()
    {
        assertTrue(validator.validate(command("a")).isEmpty());
        assertTrue(validator.validate(command("a".repeat(500))).isEmpty());
        assertTrue(validator.validate(command("   ")).stream()
                .anyMatch(violation -> "reason".equals(violation.getPropertyPath().toString())));
        assertTrue(validator.validate(command("a".repeat(501))).stream()
                .anyMatch(violation -> "reason".equals(violation.getPropertyPath().toString())));
    }

    private static DeviceStatusCommandDto command(String reason)
    {
        DeviceStatusCommandDto command = new DeviceStatusCommandDto();
        command.setTargetStatus(DeviceStatus.DISABLED);
        command.setReason(reason);
        return command;
    }
}
