package com.ruoyi.lab.storage;

/** Internal result of storing one attachment. Disk paths are deliberately absent. */
public record StoredObject(String storageKey, String storedName, long sizeBytes, String sha256)
{
}
