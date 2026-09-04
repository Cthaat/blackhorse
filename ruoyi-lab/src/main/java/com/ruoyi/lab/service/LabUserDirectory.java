package com.ruoyi.lab.service;

/** Active laboratory-role validation shared by assignment workflows. */
public interface LabUserDirectory
{
    void assertActiveRole(Long userId, String roleKey);

    void assertActiveBusinessParticipant(Long userId);
}
