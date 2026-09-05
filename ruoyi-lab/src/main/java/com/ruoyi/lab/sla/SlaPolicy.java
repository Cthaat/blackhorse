package com.ruoyi.lab.sla;
import java.time.*;
import com.ruoyi.lab.exception.*;
public final class SlaPolicy
{
    private SlaPolicy() { }
    public static LocalDateTime resumeDue(LocalDateTime due,LocalDateTime pausedAt,LocalDateTime now)
    { return due.plusSeconds(pausedSeconds(pausedAt,now)); }
    public static long pausedSeconds(LocalDateTime pausedAt,LocalDateTime now)
    { return Math.max(0,Duration.between(pausedAt,now).getSeconds()); }
    public static String stage(LocalDateTime due,int hours,LocalDateTime now,boolean stopped)
    {
        if (stopped) return null;
        if (!now.isBefore(due.plusHours(24))) return "ESCALATED";
        if (!now.isBefore(due)) return "DUE";
        return !now.isBefore(due.minusSeconds(hours*720L))?"NEAR_DUE":null;
    }
    public static String state(SlaRecord r,LocalDateTime now)
    {
        if (r.closedAt!=null) return "CLOSED";
        String response=stage(r.responseDueAt,r.responseHours,now,r.respondedAt!=null);
        String processing=stage(r.processingDueAt,r.processingHours,r.pausedAt==null?now:r.pausedAt,r.completedAt!=null);
        if (overdue(response)||overdue(processing)) return "OVERDUE";
        if (r.pausedAt!=null) return "PAUSED";
        return response!=null||processing!=null?"NEAR_DUE":"OPEN";
    }
    private static boolean overdue(String stage) { return "DUE".equals(stage)||"ESCALATED".equals(stage); }
    public static String reason(String value)
    { if(value==null||value.isBlank()||value.length()>500)throw invalid("原因必须为1至500字");return value.trim(); }
    public static LabBusinessException invalid(String message) {return new LabBusinessException(LabErrorCode.VALIDATION_ERROR,message);}
}
