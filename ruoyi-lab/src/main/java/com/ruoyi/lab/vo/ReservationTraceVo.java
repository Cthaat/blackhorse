package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

/** Visible evidence only; context is explicitly distinct from historical causality. */
public record ReservationTraceVo(ReservationVo reservation, Node usage, Node repair,
        Qualification qualification, Slice<Node> hazards, Slice<Node> notifications,
        Slice<StatusHistoryVo> history)
{
    public record Node(@LabBusinessId Long id, String title, String status,
            @LabBusinessTime LocalDateTime createTime, String reason, String basis) { }

    public record Qualification(String basis, int matchingCount,
            @LabBusinessTime LocalDateTime evaluatedAt) { }

    public record Slice<T>(List<T> items, boolean hasMore)
    {
        public Slice { items = List.copyOf(items); }
    }
}
