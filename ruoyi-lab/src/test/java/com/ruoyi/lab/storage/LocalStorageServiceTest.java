package com.ruoyi.lab.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.ruoyi.lab.config.LabStorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesWithRandomNameAndDigestThenDeletesIdempotently() throws Exception
    {
        LocalStorageService storage = storage();
        byte[] content = "laboratory-pdf".getBytes(StandardCharsets.UTF_8);

        StoredObject first = storage.store(new ByteArrayInputStream(content), content.length, "pdf");
        StoredObject second = storage.store(new ByteArrayInputStream(content), content.length, "pdf");

        assertThat(first.storageKey()).startsWith("objects/").doesNotContain("laboratory");
        assertThat(first.storedName()).isNotEqualTo(second.storedName());
        assertThat(first.sha256()).hasSize(64).isEqualTo(second.sha256());
        assertThat(storage.load(first.storageKey()).readAllBytes()).isEqualTo(content);
        storage.delete(first.storageKey());
        storage.delete(first.storageKey());
        assertThat(Files.exists(temporaryDirectory.resolve(first.storageKey()))).isFalse();
    }

    @Test
    void refusesTraversalAndAbsoluteKeys() throws Exception
    {
        LocalStorageService storage = storage();
        assertThatThrownBy(() -> storage.load("../secret.pdf")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> storage.load(temporaryDirectory.resolve("secret.pdf").toString()))
                .isInstanceOf(IOException.class);
    }

    private LocalStorageService storage() throws IOException
    {
        LabStorageProperties properties = new LabStorageProperties();
        properties.setLocalRoot(temporaryDirectory);
        return new LocalStorageService(properties);
    }
}
