package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Input for creating a device. */
public class DeviceCreateDto
{
    @NotBlank @Size(max = 64)
    private String assetNo;
    @NotNull @Positive
    private Long laboratoryId;
    @NotBlank @Size(max = 100)
    private String name;
    @NotBlank @Size(max = 32)
    private String categoryCode;
    @Size(max = 100)
    private String model;
    @NotBlank @Size(max = 20)
    private String riskLevel;
    @NotBlank @Size(max = 200)
    private String location;
    @NotNull @Positive
    private Long managerId;
    @Size(max = 1000)
    private String description;

    public String getAssetNo() { return assetNo; }
    public void setAssetNo(String assetNo) { this.assetNo = assetNo; }
    public Long getLaboratoryId() { return laboratoryId; }
    public void setLaboratoryId(Long laboratoryId) { this.laboratoryId = laboratoryId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
