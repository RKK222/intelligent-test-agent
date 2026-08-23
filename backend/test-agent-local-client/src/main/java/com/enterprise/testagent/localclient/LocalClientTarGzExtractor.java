package com.enterprise.testagent.localclient;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 麒麟客户端发布单元的受限 USTAR 解包器。
 *
 * <p>它只接受普通文件和目录，并把固定归档解到同名根目录。这样即使签名清单配置错误，归档也不能覆盖
 * staging 中的 JAR、清单或另一个运行时。</p>
 */
final class LocalClientTarGzExtractor implements LocalClientReleaseDownloader.ArchiveExtractor {

    private static final int TAR_BLOCK_BYTES = 512;
    private static final int DEFAULT_MAX_ENTRIES = 100_000;
    private static final long DEFAULT_MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private static final Set<PosixFilePermission> PRIVATE_EXECUTABLE_PERMISSIONS =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);

    private final int maxEntries;
    private final long maxExpandedBytes;

    LocalClientTarGzExtractor() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_EXPANDED_BYTES);
    }

    /** 仅供边界测试注入更小配额；生产始终使用固定上限。 */
    LocalClientTarGzExtractor(int maxEntries, long maxExpandedBytes) {
        if (maxEntries < 1 || maxExpandedBytes < 1) {
            throw new IllegalArgumentException("archive limits must be positive");
        }
        this.maxEntries = maxEntries;
        this.maxExpandedBytes = maxExpandedBytes;
    }

    @Override
    public void extract(Path archive, Path releaseDirectory) throws Exception {
        Path archivePath = archive.toAbsolutePath().normalize();
        Path releaseRoot = releaseDirectory.toAbsolutePath().normalize();
        String expectedRoot = expectedRoot(archivePath);
        if (!Files.isRegularFile(archivePath, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(releaseRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(releaseRoot)) {
            throw new SecurityException("archive or release directory is not a regular local path");
        }

        int entryCount = 0;
        long expandedBytes = 0;
        byte[] header = new byte[TAR_BLOCK_BYTES];
        try (InputStream input = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(archivePath)))) {
            boolean terminated = false;
            while (readBlock(input, header)) {
                if (isZeroBlock(header)) {
                    byte[] second = input.readNBytes(TAR_BLOCK_BYTES);
                    if (second.length != TAR_BLOCK_BYTES || !isZeroBlock(second)) {
                        throw new SecurityException("USTAR archive has an invalid end marker");
                    }
                    ensureOnlyZeroPadding(input);
                    terminated = true;
                    break;
                }

                verifyUstarHeader(header);
                entryCount++;
                if (entryCount > maxEntries) {
                    throw new SecurityException("USTAR archive contains too many entries");
                }

                String entryName = entryName(header);
                Path target = resolveTarget(releaseRoot, expectedRoot, entryName);
                char type = (char) Byte.toUnsignedInt(header[156]);
                long size = parseOctal(header, 124, 12, "entry size");
                int mode = Math.toIntExact(parseOctal(header, 100, 8, "entry mode"));
                if (size > maxExpandedBytes - expandedBytes) {
                    throw new SecurityException("USTAR archive exceeds expanded size limit");
                }
                expandedBytes += size;

                if (type == '5') {
                    if (size != 0) {
                        throw new SecurityException("USTAR directory entry contains data");
                    }
                    createSecureDirectories(releaseRoot, target);
                    setPrivatePermissions(target, true, true);
                } else if (type == '0' || type == '\0') {
                    if (target.equals(releaseRoot.resolve(expectedRoot))) {
                        throw new SecurityException("USTAR root entry must be a directory");
                    }
                    createSecureDirectories(releaseRoot, target.getParent());
                    writeRegularFile(input, target, size);
                    setPrivatePermissions(target, false, (mode & 0111) != 0);
                } else {
                    throw new SecurityException("USTAR entry type is not allowed");
                }
                discardExactly(input, paddingFor(size));
            }
            if (!terminated) {
                throw new SecurityException("USTAR archive is missing its end marker");
            }
        }
    }

    private static String expectedRoot(Path archive) {
        String name = archive.getFileName().toString();
        return switch (name) {
            case "jdk.tar.gz" -> "jdk";
            case "opencode.tar.gz" -> "opencode";
            case "public-capabilities.tar.gz" -> "public-capabilities";
            default -> throw new SecurityException("archive name is not an approved release artifact");
        };
    }

    private static void verifyUstarHeader(byte[] header) {
        if (!matchesAscii(header, 257, "ustar") || (header[262] != 0 && header[262] != (byte) ' ')) {
            throw new SecurityException("archive entry is not USTAR");
        }
        long declaredChecksum = parseOctal(header, 148, 8, "header checksum");
        long calculatedChecksum = 0;
        for (int index = 0; index < header.length; index++) {
            calculatedChecksum += index >= 148 && index < 156
                    ? Byte.toUnsignedInt((byte) ' ')
                    : Byte.toUnsignedInt(header[index]);
        }
        if (declaredChecksum != calculatedChecksum) {
            throw new SecurityException("USTAR header checksum is invalid");
        }
        if (!readText(header, 157, 100).isEmpty()) {
            throw new SecurityException("USTAR link targets are not allowed");
        }
    }

    private static String entryName(byte[] header) {
        String name = readText(header, 0, 100);
        String prefix = readText(header, 345, 155);
        if (name.isEmpty()) {
            throw new SecurityException("USTAR entry name is empty");
        }
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    private static Path resolveTarget(Path releaseRoot, String expectedRoot, String entryName) {
        if (entryName.startsWith("/") || entryName.indexOf('\\') >= 0 || entryName.indexOf('\0') >= 0) {
            throw new SecurityException("USTAR entry path is unsafe");
        }
        String normalizedName = entryName.endsWith("/")
                ? entryName.substring(0, entryName.length() - 1)
                : entryName;
        if (normalizedName.isEmpty()) {
            throw new SecurityException("USTAR entry path is empty");
        }
        String[] segments = normalizedName.split("/", -1);
        if (!expectedRoot.equals(segments[0])) {
            throw new SecurityException("USTAR entry is outside its approved root");
        }
        Path target = releaseRoot;
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || containsControlCharacter(segment)) {
                throw new SecurityException("USTAR entry path contains an unsafe segment");
            }
            target = target.resolve(segment);
        }
        target = target.normalize();
        if (!target.startsWith(releaseRoot) || target.equals(releaseRoot)) {
            throw new SecurityException("USTAR entry escapes release directory");
        }
        return target;
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint));
    }

    private static void createSecureDirectories(Path releaseRoot, Path directory) throws IOException {
        if (directory == null || directory.equals(releaseRoot)) {
            return;
        }
        Path relative = releaseRoot.relativize(directory);
        Path current = releaseRoot;
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new SecurityException("USTAR parent is not a safe directory");
                }
            } else {
                Files.createDirectory(current);
            }
            setPrivatePermissions(current, true, true);
        }
    }

    private static void writeRegularFile(InputStream input, Path target, long size) throws IOException {
        OpenOption[] options = {
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS
        };
        try (OutputStream output = Files.newOutputStream(target, options)) {
            byte[] buffer = new byte[64 * 1024];
            long remaining = size;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new EOFException("USTAR file content is truncated");
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private static void setPrivatePermissions(Path path, boolean directory, boolean executable) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    path,
                    directory ? PRIVATE_DIRECTORY_PERMISSIONS
                            : executable ? PRIVATE_EXECUTABLE_PERMISSIONS : PRIVATE_FILE_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // 麒麟目标支持 POSIX；其它开发平台至少确保归档中声明的程序仍可执行。
            if (executable && !path.toFile().setExecutable(true, true)) {
                throw new IOException("cannot make extracted executable runnable");
            }
        }
    }

    private static long paddingFor(long size) {
        return (TAR_BLOCK_BYTES - size % TAR_BLOCK_BYTES) % TAR_BLOCK_BYTES;
    }

    private static void discardExactly(InputStream input, long count) throws IOException {
        long remaining = count;
        byte[] buffer = new byte[TAR_BLOCK_BYTES];
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new EOFException("USTAR entry padding is truncated");
            }
            remaining -= read;
        }
    }

    private static boolean readBlock(InputStream input, byte[] block) throws IOException {
        int offset = 0;
        while (offset < block.length) {
            int read = input.read(block, offset, block.length - offset);
            if (read < 0) {
                if (offset == 0) {
                    return false;
                }
                throw new EOFException("USTAR header is truncated");
            }
            offset += read;
        }
        return true;
    }

    private static void ensureOnlyZeroPadding(InputStream input) throws IOException {
        byte[] buffer = new byte[TAR_BLOCK_BYTES];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            for (int index = 0; index < read; index++) {
                if (buffer[index] != 0) {
                    throw new SecurityException("USTAR archive contains data after its end marker");
                }
            }
        }
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte value : block) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAscii(byte[] source, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        for (int index = 0; index < bytes.length; index++) {
            if (source[offset + index] != bytes[index]) {
                return false;
            }
        }
        return true;
    }

    private static String readText(byte[] source, int offset, int length) {
        int end = offset;
        while (end < offset + length && source[end] != 0) {
            end++;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(source, offset, end - offset))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new SecurityException("USTAR text field is not valid UTF-8", exception);
        }
    }

    private static long parseOctal(byte[] source, int offset, int length, String fieldName) {
        if ((source[offset] & 0x80) != 0) {
            throw new SecurityException("USTAR base-256 " + fieldName + " is not allowed");
        }
        int index = offset;
        int end = offset + length;
        while (index < end && (source[index] == 0 || source[index] == (byte) ' ')) {
            index++;
        }
        long value = 0;
        boolean digitSeen = false;
        while (index < end && source[index] != 0 && source[index] != (byte) ' ') {
            int digit = source[index] - (byte) '0';
            if (digit < 0 || digit > 7 || value > (Long.MAX_VALUE - digit) / 8) {
                throw new SecurityException("USTAR " + fieldName + " is invalid");
            }
            value = value * 8 + digit;
            digitSeen = true;
            index++;
        }
        while (index < end) {
            if (source[index] != 0 && source[index] != (byte) ' ') {
                throw new SecurityException("USTAR " + fieldName + " has trailing data");
            }
            index++;
        }
        if (!digitSeen) {
            return 0;
        }
        return value;
    }
}
