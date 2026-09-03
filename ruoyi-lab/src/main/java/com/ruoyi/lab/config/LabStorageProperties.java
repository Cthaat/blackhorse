package com.ruoyi.lab.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/** Configuration for private laboratory attachment storage. */
@Component
@ConfigurationProperties(prefix = "lab.storage")
public class LabStorageProperties
{
    private Path localRoot = Paths.get(System.getProperty("user.home"), ".blackhorse", "lab-files");

    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    private int maxFilesPerObject = 5;

    public Path getLocalRoot()
    {
        return localRoot;
    }

    public void setLocalRoot(Path localRoot)
    {
        this.localRoot = localRoot;
    }

    public DataSize getMaxFileSize()
    {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize)
    {
        this.maxFileSize = maxFileSize;
    }

    public int getMaxFilesPerObject()
    {
        return maxFilesPerObject;
    }

    public void setMaxFilesPerObject(int maxFilesPerObject)
    {
        this.maxFilesPerObject = maxFilesPerObject;
    }
}
