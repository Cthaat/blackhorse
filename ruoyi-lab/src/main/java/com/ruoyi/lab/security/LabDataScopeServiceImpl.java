package com.ruoyi.lab.security;

import java.util.HashSet;
import java.util.List;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.mapper.LabDataScopeMapper;
import org.springframework.stereotype.Service;

/** Resolves RuoYi data-scope facts without introducing a system-module dependency. */
@Service
public class LabDataScopeServiceImpl implements LabDataScopeService
{
    private final LabDataScopeMapper dataScopeMapper;

    public LabDataScopeServiceImpl(LabDataScopeMapper dataScopeMapper)
    {
        this.dataScopeMapper = dataScopeMapper;
    }

    @Override
    public LabDataScope resolveCurrentScope()
    {
        Long userId = SecurityUtils.getUserId();
        if (SecurityUtils.isAdmin(userId) || dataScopeMapper.hasAllLaboratoryScope(userId))
        {
            return new LabDataScope(userId, true, java.util.Set.of());
        }
        List<Long> identifiers = dataScopeMapper.selectScopedLaboratoryIds(userId);
        return new LabDataScope(userId, false,
                identifiers == null ? java.util.Set.of() : new HashSet<>(identifiers));
    }
}
