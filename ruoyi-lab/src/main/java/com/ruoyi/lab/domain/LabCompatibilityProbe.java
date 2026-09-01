package com.ruoyi.lab.domain;

import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Probe entity used to verify the shared MyBatis compatibility layer.
 */
@TableName("lab_compatibility_probe")
public class LabCompatibilityProbe implements Serializable
{
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String probeName;

    private Integer sortOrder;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getProbeName()
    {
        return probeName;
    }

    public void setProbeName(String probeName)
    {
        this.probeName = probeName;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }
}
