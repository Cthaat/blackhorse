package com.ruoyi.lab.security;

/**
 * Resolves the current user's laboratory visibility snapshot.
 */
public interface LabDataScopeService
{
    LabDataScope resolveCurrentScope();
}
