package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabRectification;
import org.apache.ibatis.annotations.Param;

public interface LabRectificationMapper extends BaseMapper<LabRectification>
{
    LabRectification selectForUpdate(@Param("rectificationId") Long rectificationId);
    List<LabRectification> selectByHazard(@Param("hazardId") Long hazardId);
    int selectMaxRound(@Param("hazardId") Long hazardId);
    int reviewConditionally(@Param("rectificationId") Long rectificationId,
            @Param("reviewerId") Long reviewerId, @Param("result") String result,
            @Param("reason") String reason, @Param("reviewedAt") LocalDateTime reviewedAt,
            @Param("expectedVersion") Integer expectedVersion);
}
