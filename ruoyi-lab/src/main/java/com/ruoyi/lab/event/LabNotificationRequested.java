package com.ruoyi.lab.event;

/** Persisted business-fact reference published inside the core transaction. */
public record LabNotificationRequested(Type type, long referenceId, long version)
{
    public enum Type
    {
        STATUS_HISTORY,
        INSPECTION_OVERDUE,
        HAZARD_OVERDUE
    }

    public static LabNotificationRequested history(long historyId)
    {
        return new LabNotificationRequested(Type.STATUS_HISTORY, historyId, 0L);
    }

    public static LabNotificationRequested inspectionOverdue(long taskId,
            long overdueEventVersion)
    {
        return new LabNotificationRequested(Type.INSPECTION_OVERDUE, taskId,
                overdueEventVersion);
    }

    public static LabNotificationRequested hazardOverdue(long hazardId,
            long overdueEventVersion)
    {
        return new LabNotificationRequested(Type.HAZARD_OVERDUE, hazardId,
                overdueEventVersion);
    }
}
