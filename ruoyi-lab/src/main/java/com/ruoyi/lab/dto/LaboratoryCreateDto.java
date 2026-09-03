package com.ruoyi.lab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Input for creating a laboratory. */
public class LaboratoryCreateDto
{
    @NotBlank @Size(max = 32)
    private String labCode;
    @NotBlank @Size(max = 100)
    private String name;
    @NotNull @Positive
    private Long deptId;
    @NotNull @Positive
    private Long managerId;
    @NotBlank @Size(max = 200)
    private String location;
    @Size(max = 500)
    private String description;

    public String getLabCode() { return labCode; }
    public void setLabCode(String labCode) { this.labCode = labCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
