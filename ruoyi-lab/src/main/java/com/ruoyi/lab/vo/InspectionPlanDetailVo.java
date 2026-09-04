package com.ruoyi.lab.vo;

import java.util.List;
import java.util.Objects;
import com.ruoyi.lab.domain.LabInspectionPlan;
import com.ruoyi.lab.domain.LabInspectionPlanItem;

/** Editable inspection plan detail with its current item templates. */
public record InspectionPlanDetailVo(LabInspectionPlan plan,
        List<LabInspectionPlanItem> items)
{
    public InspectionPlanDetailVo
    {
        plan = Objects.requireNonNull(plan, "plan");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }
}
