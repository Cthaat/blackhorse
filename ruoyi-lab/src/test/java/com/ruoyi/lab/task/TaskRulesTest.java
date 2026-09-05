package com.ruoyi.lab.task;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskRulesTest
{
    @Test void limitsAndTerminalStatesAreExplicit()
    {
        assertThrows(IllegalArgumentException.class, () -> TaskRules.validateUpload(5 * 1024 * 1024 + 1L));
        assertThrows(IllegalArgumentException.class, () -> TaskRules.validateUpload(0));
        assertTrue(TaskRules.cancellable("RUNNING"));
        assertFalse(TaskRules.cancellable("SUCCEEDED"));
        assertEquals("PARTIAL", TaskRules.result(2, 1));
        assertEquals("FAILED", TaskRules.result(0, 1));
    }
}
