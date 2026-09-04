package com.ruoyi.lab.dto;

import com.ruoyi.lab.domain.RepairStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class RepairQueryDto
{
    @Size(max = 32)
    private String repairNo;
    @Positive
    private Long deviceId;
    private RepairStatus status;
    @Pattern(regexp = "id|repairNo|status|createTime")
    private String sortBy = "createTime";
    @Pattern(regexp = "asc|desc")
    private String sortDirection = "desc";

    public String getRepairNo() { return repairNo; }
    public void setRepairNo(String repairNo) { this.repairNo = repairNo; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public RepairStatus getStatus() { return status; }
    public void setStatus(RepairStatus status) { this.status = status; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
}
