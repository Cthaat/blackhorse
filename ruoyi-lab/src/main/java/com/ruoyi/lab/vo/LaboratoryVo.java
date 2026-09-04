package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

/** Read model for a laboratory. */
public class LaboratoryVo
{
    @LabBusinessId
    private Long id;
    private String labCode;
    private String name;
    @LabBusinessId
    private Long deptId;
    @LabBusinessId
    private Long managerId;
    private String location;
    private String description;
    private LaboratoryStatus status;
    private Integer version;
    @LabBusinessTime
    private LocalDateTime createTime;
    @LabBusinessTime
    private LocalDateTime updateTime;

    public static LaboratoryVo from(LabLaboratory source)
    {
        LaboratoryVo target = new LaboratoryVo();
        target.id = source.getId();
        target.labCode = source.getLabCode();
        target.name = source.getName();
        target.deptId = source.getDeptId();
        target.managerId = source.getManagerId();
        target.location = source.getLocation();
        target.description = source.getDescription();
        target.status = source.getStatus();
        target.version = source.getVersion();
        target.createTime = source.getCreateTime();
        target.updateTime = source.getUpdateTime();
        return target;
    }

    public Long getId() { return id; }
    public String getLabCode() { return labCode; }
    public String getName() { return name; }
    public Long getDeptId() { return deptId; }
    public Long getManagerId() { return managerId; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public LaboratoryStatus getStatus() { return status; }
    public Integer getVersion() { return version; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
