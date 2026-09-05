package com.ruoyi.lab.maintenance;

import java.time.*;
import com.ruoyi.lab.exception.*;

public final class MaintenancePolicy
{
    private MaintenancePolicy() { }
    public static LocalDateTime nextDue(LocalDateTime acceptedAt,int days)
    {
        if (days<1 || days>3650) throw invalid("周期必须为1至3650天");
        return acceptedAt.plusDays(days);
    }
    public static boolean overlaps(LocalDateTime start,LocalDateTime end,LocalDateTime otherStart,LocalDateTime otherEnd)
    { return start.isBefore(otherEnd) && end.isAfter(otherStart); }
    public static LocalDateTime time(OffsetDateTime value)
    {
        if (value==null) throw invalid("时间不能为空");
        return value.atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
    }
    public static String reason(String value)
    {
        if (value==null || value.isBlank() || value.length()>500) throw invalid("操作原因必须为1至500字");
        return value.trim();
    }
    public static void window(LocalDateTime start,LocalDateTime end,LocalDateTime now)
    {
        if (!start.isBefore(end) || !end.isAfter(now) || Duration.between(start,end).compareTo(Duration.ofDays(30))>0)
            throw invalid("停用窗口必须结束于未来且持续不超过30天");
    }
    public static LabBusinessException invalid(String text) { return new LabBusinessException(LabErrorCode.VALIDATION_ERROR,text); }
    public static LabBusinessException conflict(String text) { return new LabBusinessException(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION,text); }
}
