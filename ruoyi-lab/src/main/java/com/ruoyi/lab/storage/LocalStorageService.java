package com.ruoyi.lab.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import com.ruoyi.lab.config.LabStorageProperties;
import org.springframework.stereotype.Service;

/** Local private storage using generated keys and atomic publication. */
@Service
public class LocalStorageService implements StorageService
{
    private static final Pattern STORAGE_KEY = Pattern.compile(
            "objects/[0-9a-f]{32}\\.[a-z0-9]{1,10}");

    private final Path root;
    private final Path objectRoot;
    private final long maxFileSize;

    public LocalStorageService(LabStorageProperties properties) throws IOException
    {
        Objects.requireNonNull(properties, "properties");
        this.root = validateRoot(properties.getLocalRoot());
        this.objectRoot = root.resolve("objects");
        this.maxFileSize = properties.getMaxFileSize().toBytes();
        Files.createDirectories(objectRoot);
    }

    @Override
    public StoredObject store(InputStream input, long contentLength, String extension) throws IOException
    {
        Objects.requireNonNull(input, "input");
        String safeExtension = normalizeExtension(extension);
        if (contentLength <= 0 || contentLength > maxFileSize)
        {
            throw new IOException("attachment content length is invalid");
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + safeExtension;
        String storageKey = "objects/" + storedName;
        Path destination = resolve(storageKey);
        Path temporary = Files.createTempFile(objectRoot, ".upload-", ".tmp");
        MessageDigest digest = sha256();
        long written = 0;
        try
        {
            try (OutputStream output = new DigestOutputStream(
                    Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING), digest))
            {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0)
                {
                    if (read == 0)
                    {
                        continue;
                    }
                    written += read;
                    if (written > maxFileSize || written > contentLength)
                    {
                        throw new IOException("attachment stream exceeds declared size");
                    }
                    output.write(buffer, 0, read);
                }
            }
            if (written != contentLength)
            {
                throw new IOException("attachment stream length differs from declared size");
            }
            moveAtomically(temporary, destination);
            return new StoredObject(storageKey, storedName, written,
                    HexFormat.of().formatHex(digest.digest()));
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public InputStream load(String storageKey) throws IOException
    {
        Path target = resolve(storageKey);
        if (!Files.isRegularFile(target))
        {
            throw new IOException("attachment object is unavailable");
        }
        return Files.newInputStream(target, StandardOpenOption.READ);
    }

    @Override
    public void delete(String storageKey) throws IOException
    {
        Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String storageKey) throws IOException
    {
        if (storageKey == null || !STORAGE_KEY.matcher(storageKey).matches())
        {
            throw new IOException("attachment storage key is invalid");
        }
        Path resolved = root.resolve(storageKey).normalize().toAbsolutePath();
        if (!resolved.startsWith(root))
        {
            throw new IOException("attachment storage key escapes root");
        }
        return resolved;
    }

    private static Path validateRoot(Path configuredRoot) throws IOException
    {
        if (configuredRoot == null)
        {
            throw new IOException("attachment root is required");
        }
        Path normalized = configuredRoot.toAbsolutePath().normalize();
        String portable = normalized.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (portable.contains("/src/main/") || portable.endsWith("/src")
                || portable.endsWith("/static") || portable.endsWith("/public")
                || portable.contains("/target/classes") || portable.contains("/build/resources"))
        {
            throw new IOException("attachment root cannot be publicly served or source-controlled");
        }
        Files.createDirectories(normalized);
        if (Files.isSymbolicLink(normalized))
        {
            throw new IOException("attachment root cannot be a symbolic link");
        }
        return normalized.toRealPath();
    }

    private static String normalizeExtension(String extension) throws IOException
    {
        if (extension == null)
        {
            throw new IOException("attachment extension is required");
        }
        String normalized = extension.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9]{1,10}"))
        {
            throw new IOException("attachment extension is invalid");
        }
        return normalized;
    }

    private static MessageDigest sha256()
    {
        try
        {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException
    {
        try
        {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException exception)
        {
            Files.move(source, destination);
        }
    }
}
