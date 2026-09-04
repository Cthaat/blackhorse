package com.ruoyi.lab.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Safe filters for project-local user selectors. */
public class LabUserOptionQueryDto
{
    @Pattern(regexp = "lab_(student|manager|safety_officer|repair_worker|system_admin)",
            message = "实验室角色标识无效")
    private String roleKey;

    @Size(max = 50, message = "关键词长度不能超过50个字符")
    private String keyword;

    public String getRoleKey()
    {
        return roleKey;
    }

    public void setRoleKey(String roleKey)
    {
        this.roleKey = trimToNull(roleKey);
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = trimToNull(keyword);
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
