package com.ruoyi.lab.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import org.springframework.stereotype.Component;

/**
 * Resolves client sort keys to trusted SQL fragments.
 */
@Component
public final class LabSortWhitelist
{
    private static final Map<String, Map<String, String>> COLUMNS = Map.of(
            "laboratory", Map.of(
                    "name", "l.name",
                    "labCode", "l.lab_code",
                    "createTime", "l.create_time"),
            "device", Map.of(
                    "assetNo", "d.asset_no",
                    "name", "d.name",
                    "status", "d.status",
                    "createTime", "d.create_time"),
            "qualification", Map.of(
                    "validUntil", "q.valid_until",
                    "createTime", "q.create_time"),
            "reservation", Map.of(
                    "reservationNo", "r.reservation_no",
                    "startTime", "r.start_time",
                    "createTime", "r.create_time",
                    "status", "r.status"));

    private static final Set<String> ALLOWED_COLUMNS = COLUMNS.values().stream()
            .flatMap(columns -> columns.values().stream())
            .collect(Collectors.toUnmodifiableSet());

    public SortClause resolve(String resource, String key, String direction)
    {
        if (resource == null || key == null)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "非法排序字段");
        }
        Map<String, String> resourceColumns = COLUMNS.get(resource);
        String column = resourceColumns == null ? null : resourceColumns.get(key);
        if (column == null)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "非法排序字段");
        }
        if (direction == null)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "非法排序方向");
        }

        String normalizedDirection = switch (direction.toLowerCase(Locale.ROOT))
        {
            case "asc" -> "ASC";
            case "desc" -> "DESC";
            default -> throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "非法排序方向");
        };
        return new SortClause(column, normalizedDirection);
    }

    /**
     * Trusted values suitable for the fixed ORDER BY slots in mapper XML.
     */
    public record SortClause(String column, String direction)
    {
        public SortClause
        {
            if (column == null || !ALLOWED_COLUMNS.contains(column))
            {
                throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "非法排序字段");
            }
            if (!"ASC".equals(direction) && !"DESC".equals(direction))
            {
                throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "非法排序方向");
            }
        }
    }
}
