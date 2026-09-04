package com.ruoyi.lab.storage;

import java.io.IOException;
import java.io.InputStream;

/** Private binary storage port. */
public interface StorageService
{
    StoredObject store(InputStream input, long contentLength, String extension) throws IOException;

    InputStream load(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
