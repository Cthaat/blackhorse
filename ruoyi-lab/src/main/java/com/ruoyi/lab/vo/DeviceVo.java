package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

/** Read model for a device. */
public class DeviceVo
{
    @LabBusinessId
    private Long id;
    private String assetNo;
    @LabBusinessId
    private Long laboratoryId;
    private String name;
    private String categoryCode;
    private String model;
    private String riskLevel;
    private String location;
    @LabBusinessId
    private Long managerId;
    private String description;
    private DeviceStatus status;
    private Integer version;
    @LabBusinessTime
    private LocalDateTime createTime;
    @LabBusinessTime
    private LocalDateTime updateTime;

    public static DeviceVo from(LabDevice source)
    {
        DeviceVo target = new DeviceVo();
        target.id = source.getId();
        target.assetNo = source.getAssetNo();
        target.laboratoryId = source.getLaboratoryId();
        target.name = source.getName();
        target.categoryCode = source.getCategoryCode();
        target.model = source.getModel();
        target.riskLevel = source.getRiskLevel();
        target.location = source.getLocation();
        target.managerId = source.getManagerId();
        target.description = source.getDescription();
        target.status = source.getStatus();
        target.version = source.getVersion();
        target.createTime = source.getCreateTime();
        target.updateTime = source.getUpdateTime();
        return target;
    }

    public Long getId() { return id; }
    public String getAssetNo() { return assetNo; }
    public Long getLaboratoryId() { return laboratoryId; }
    public String getName() { return name; }
    public String getCategoryCode() { return categoryCode; }
    public String getModel() { return model; }
    public String getRiskLevel() { return riskLevel; }
    public String getLocation() { return location; }
    public Long getManagerId() { return managerId; }
    public String getDescription() { return description; }
    public DeviceStatus getStatus() { return status; }
    public Integer getVersion() { return version; }
    public LocalDateTime getCreateTime() { return createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
}
