package com.enterprise.testagent.workspace;

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 以工作区根目录文件描述符为锚点执行不覆盖目标的原子移动，避免校验后的路径替换竞态。
 */
final class SecureWorkspaceMover {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecureWorkspaceMover.class);

    private static final int O_RDONLY = 0;
    private static final int MAC_O_NOFOLLOW = 0x00000100;
    private static final int MAC_O_DIRECTORY = 0x00100000;
    private static final int MAC_O_CLOEXEC = 0x01000000;
    private static final int MAC_O_NOFOLLOW_ANY = 0x20000000;
    private static final int MAC_RENAME_EXCL = 0x00000004;
    private static final int MAC_RENAME_NOFOLLOW_ANY = 0x00000010;
    private static final int LINUX_X86_64_O_DIRECTORY = 00200000;
    private static final int LINUX_X86_64_O_NOFOLLOW = 00400000;
    private static final int LINUX_ARM64_O_DIRECTORY = 040000;
    private static final int LINUX_ARM64_O_NOFOLLOW = 0100000;
    private static final int LINUX_O_CLOEXEC = 02000000;
    private static final int LINUX_RENAME_NOREPLACE = 1;
    private static final long LINUX_X86_64_RENAMEAT2_SYSCALL = 316;
    private static final long LINUX_ARM64_RENAMEAT2_SYSCALL = 276;

    private static final int WINDOWS_DELETE = 0x00010000;
    private static final int WINDOWS_SYNCHRONIZE = 0x00100000;
    private static final int WINDOWS_FILE_TRAVERSE = 0x00000020;
    private static final int WINDOWS_FILE_READ_ATTRIBUTES = 0x00000080;
    private static final int WINDOWS_FILE_SHARE_READ = 0x00000001;
    private static final int WINDOWS_FILE_SHARE_WRITE = 0x00000002;
    private static final int WINDOWS_FILE_SHARE_DELETE = 0x00000004;
    private static final int WINDOWS_OPEN_EXISTING = 3;
    private static final int WINDOWS_FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000;
    private static final int WINDOWS_FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;
    private static final int WINDOWS_FILE_ATTRIBUTE_REPARSE_POINT = 0x00000400;
    private static final int WINDOWS_FILE_ATTRIBUTE_TAG_INFO = 9;
    private static final int WINDOWS_FILE_RENAME_INFO = 3;
    private static final int WINDOWS_ERROR_FILE_NOT_FOUND = 2;
    private static final int WINDOWS_ERROR_PATH_NOT_FOUND = 3;
    private static final int WINDOWS_ERROR_ACCESS_DENIED = 5;
    private static final int WINDOWS_ERROR_NOT_SAME_DEVICE = 17;
    private static final int WINDOWS_ERROR_FILE_EXISTS = 80;
    private static final int WINDOWS_ERROR_ALREADY_EXISTS = 183;

    private static final int ENOENT = 2;
    private static final int EACCES = 13;
    private static final int EEXIST = 17;
    private static final int EXDEV = 18;
    private static final int ENOTDIR = 20;
    private static final int LINUX_ENOTEMPTY = 39;
    private static final int LINUX_ELOOP = 40;
    private static final int MAC_ELOOP = 62;
    private static final int MAC_ENOTEMPTY = 66;
    private static final int EPERM = 1;

    private SecureWorkspaceMover() {}

    /**
     * 相对真实工作区根逐段打开源、目标父目录，并用平台原子重命名接口完成一次整体移动。
     */
    static void move(Path realRoot, Path realSource, Path realTarget) throws IOException {
        try {
            if (Platform.isMac()) {
                moveOnDarwin(DarwinHolder.INSTANCE, realRoot, realSource, realTarget);
                return;
            }
            if (Platform.isLinux()) {
                moveOnLinux(LinuxHolder.INSTANCE, realRoot, realSource, realTarget);
                return;
            }
            if (Platform.isWindows()) {
                moveOnWindows(WindowsHolder.INSTANCE, realSource, realTarget);
                return;
            }
        } catch (LinkageError error) {
            throw new IOException("当前运行环境缺少安全的工作区原子移动接口", error);
        }
        throw new IOException("当前操作系统不支持安全的工作区原子移动");
    }

    private static void moveOnDarwin(
            DarwinLibC nativeApi,
            Path realRoot,
            Path realSource,
            Path realTarget) throws IOException {
        int directoryFlags = O_RDONLY | MAC_O_DIRECTORY | MAC_O_NOFOLLOW | MAC_O_CLOEXEC;
        // 根路径使用 O_NOFOLLOW_ANY，确保从绝对路径任意层级替换为符号链接时也失败关闭。
        int rootFlags = O_RDONLY | MAC_O_DIRECTORY | MAC_O_CLOEXEC | MAC_O_NOFOLLOW_ANY;
        try (DirectoryDescriptor root = openRoot(nativeApi, realRoot, rootFlags);
                DirectoryDescriptor sourceParent = openRelativeParent(
                        nativeApi, root, realRoot.relativize(realSource.getParent()), directoryFlags);
                DirectoryDescriptor targetParent = openRelativeParent(
                        nativeApi, root, realRoot.relativize(realTarget.getParent()), directoryFlags)) {
            try {
                nativeApi.renameatx_np(
                        sourceParent.value(),
                        realSource.getFileName().toString(),
                        targetParent.value(),
                        realTarget.getFileName().toString(),
                        MAC_RENAME_EXCL | MAC_RENAME_NOFOLLOW_ANY);
            } catch (LastErrorException exception) {
                throw nativeFailure(realSource, realTarget, exception);
            }
        }
    }

    private static void moveOnLinux(
            LinuxLibC nativeApi,
            Path realRoot,
            Path realSource,
            Path realTarget) throws IOException {
        int directoryFlags = linuxDirectoryFlags();
        long renameat2SystemCall = linuxRenameat2SystemCall();
        try (DirectoryDescriptor root = openLinuxRoot(nativeApi, realRoot, directoryFlags);
                DirectoryDescriptor sourceParent = openRelativeParent(
                        nativeApi, root, realRoot.relativize(realSource.getParent()), directoryFlags);
                DirectoryDescriptor targetParent = openRelativeParent(
                        nativeApi, root, realRoot.relativize(realTarget.getParent()), directoryFlags)) {
            try {
                // Alpine 3.23 的 musl 尚未导出 renameat2 包装函数，直接调用同一内核原子接口。
                nativeApi.syscall(
                        renameat2SystemCall,
                        sourceParent.value(),
                        realSource.getFileName().toString(),
                        targetParent.value(),
                        realTarget.getFileName().toString(),
                        LINUX_RENAME_NOREPLACE);
            } catch (LastErrorException exception) {
                throw nativeFailure(realSource, realTarget, exception);
            }
        }
    }

    private static long linuxRenameat2SystemCall() throws IOException {
        if (Platform.isIntel() && Platform.is64Bit()) {
            return LINUX_X86_64_RENAMEAT2_SYSCALL;
        }
        if (Platform.isARM() && Platform.is64Bit()) {
            return LINUX_ARM64_RENAMEAT2_SYSCALL;
        }
        throw new IOException("当前 Linux CPU 架构不支持安全的工作区原子移动：" + Platform.ARCH);
    }

    private static int linuxDirectoryFlags() throws IOException {
        if (Platform.isIntel() && Platform.is64Bit()) {
            return O_RDONLY | LINUX_X86_64_O_DIRECTORY | LINUX_X86_64_O_NOFOLLOW | LINUX_O_CLOEXEC;
        }
        if (Platform.isARM() && Platform.is64Bit()) {
            return O_RDONLY | LINUX_ARM64_O_DIRECTORY | LINUX_ARM64_O_NOFOLLOW | LINUX_O_CLOEXEC;
        }
        throw new IOException("当前 Linux CPU 架构不支持安全的工作区目录句柄：" + Platform.ARCH);
    }

    /**
     * Windows 以源条目和目标父目录句柄执行相对重命名，ReplaceIfExists 保持为 false。
     */
    private static void moveOnWindows(WindowsKernel32 nativeApi, Path realSource, Path realTarget) throws IOException {
        try (WindowsHandle source = openWindowsHandle(
                        nativeApi,
                        realSource,
                        WINDOWS_DELETE | WINDOWS_SYNCHRONIZE | WINDOWS_FILE_READ_ATTRIBUTES);
                WindowsHandle targetParent = openWindowsHandle(
                        nativeApi,
                        realTarget.getParent(),
                        WINDOWS_SYNCHRONIZE | WINDOWS_FILE_TRAVERSE | WINDOWS_FILE_READ_ATTRIBUTES)) {
            verifyWindowsHandle(nativeApi, source, realSource);
            verifyWindowsHandle(nativeApi, targetParent, realTarget.getParent());
            Memory renameInformation = windowsRenameInformation(
                    targetParent.value(), realTarget.getFileName().toString());
            if (!nativeApi.SetFileInformationByHandle(
                    source.value(),
                    WINDOWS_FILE_RENAME_INFO,
                    renameInformation,
                    (int) renameInformation.size())) {
                throw windowsFailure(realSource, realTarget, new LastErrorException(Native.getLastError()));
            }
        }
    }

    private static WindowsHandle openWindowsHandle(
            WindowsKernel32 nativeApi,
            Path path,
            int desiredAccess) throws IOException {
        Pointer handle = nativeApi.CreateFileW(
                new WString(path.toString()),
                desiredAccess,
                WINDOWS_FILE_SHARE_READ | WINDOWS_FILE_SHARE_WRITE | WINDOWS_FILE_SHARE_DELETE,
                Pointer.NULL,
                WINDOWS_OPEN_EXISTING,
                WINDOWS_FILE_FLAG_BACKUP_SEMANTICS | WINDOWS_FILE_FLAG_OPEN_REPARSE_POINT,
                Pointer.NULL);
        if (handle == null || Pointer.nativeValue(handle) == -1L) {
            throw windowsFailure(path, path, new LastErrorException(Native.getLastError()));
        }
        return new WindowsHandle(nativeApi, handle);
    }

    /**
     * 句柄打开后同时核对重解析点属性和最终规范路径，阻断绝对路径中间祖先被替换的竞态。
     */
    private static void verifyWindowsHandle(
            WindowsKernel32 nativeApi,
            WindowsHandle handle,
            Path expectedPath) throws IOException {
        Memory attributeInformation = new Memory(8);
        if (!nativeApi.GetFileInformationByHandleEx(
                handle.value(),
                WINDOWS_FILE_ATTRIBUTE_TAG_INFO,
                attributeInformation,
                (int) attributeInformation.size())) {
            throw windowsFailure(expectedPath, expectedPath, new LastErrorException(Native.getLastError()));
        }
        if ((attributeInformation.getInt(0) & WINDOWS_FILE_ATTRIBUTE_REPARSE_POINT) != 0) {
            throw new FileSystemException(
                    expectedPath.toString(),
                    null,
                    "移动路径在校验后变成重解析点");
        }
        String actualPath = windowsFinalPath(nativeApi, handle, expectedPath);
        if (!normalizeWindowsPath(actualPath).equalsIgnoreCase(normalizeWindowsPath(expectedPath.toString()))) {
            throw new FileSystemException(
                    expectedPath.toString(),
                    actualPath,
                    "移动路径在校验后发生变化");
        }
    }

    private static String windowsFinalPath(
            WindowsKernel32 nativeApi,
            WindowsHandle handle,
            Path expectedPath) throws IOException {
        char[] buffer = new char[32768];
        int length = nativeApi.GetFinalPathNameByHandleW(handle.value(), buffer, buffer.length, 0);
        if (length <= 0) {
            throw windowsFailure(expectedPath, expectedPath, new LastErrorException(Native.getLastError()));
        }
        if (length >= buffer.length) throw new IOException("工作区移动句柄的最终路径超过系统上限");
        return new String(buffer, 0, length);
    }

    private static String normalizeWindowsPath(String path) {
        String normalized = path;
        if (normalized.startsWith("\\\\?\\UNC\\")) {
            normalized = "\\\\" + normalized.substring(8);
        } else if (normalized.startsWith("\\\\?\\")) {
            normalized = normalized.substring(4);
        }
        return normalized.replace('/', '\\');
    }

    private static Memory windowsRenameInformation(Pointer targetParent, String targetName) {
        byte[] targetNameBytes = targetName.getBytes(StandardCharsets.UTF_16LE);
        long rootDirectoryOffset = Native.POINTER_SIZE;
        long fileNameLengthOffset = rootDirectoryOffset + Native.POINTER_SIZE;
        long fileNameOffset = fileNameLengthOffset + Integer.BYTES;
        Memory information = new Memory(fileNameOffset + targetNameBytes.length + Character.BYTES);
        information.clear();
        information.setPointer(rootDirectoryOffset, targetParent);
        information.setInt(fileNameLengthOffset, targetNameBytes.length);
        information.write(fileNameOffset, targetNameBytes, 0, targetNameBytes.length);
        return information;
    }

    private static DirectoryDescriptor openRoot(PosixLibC nativeApi, Path realRoot, int flags) throws IOException {
        try {
            return new DirectoryDescriptor(nativeApi, nativeApi.open(realRoot.toString(), flags));
        } catch (LastErrorException exception) {
            throw nativeFailure(realRoot, realRoot, exception);
        }
    }

    /**
     * Linux 的 O_NOFOLLOW 只保护最后一级，因此从根目录句柄开始逐段打开真实 workspace root。
     */
    private static DirectoryDescriptor openLinuxRoot(PosixLibC nativeApi, Path realRoot, int flags) throws IOException {
        try (DirectoryDescriptor fileSystemRoot = openRoot(nativeApi, realRoot.getRoot(), flags)) {
            return openRelativeParent(nativeApi, fileSystemRoot, realRoot, flags);
        }
    }

    /**
     * 每次 openat 只接收一个已真实化的目录名，且携带 O_NOFOLLOW，避免重新解析可变字符串路径。
     */
    private static DirectoryDescriptor openRelativeParent(
            PosixLibC nativeApi,
            DirectoryDescriptor root,
            Path relativePath,
            int flags) throws IOException {
        DirectoryDescriptor current;
        try {
            current = new DirectoryDescriptor(nativeApi, nativeApi.openat(root.value(), ".", flags));
        } catch (LastErrorException exception) {
            throw nativeFailure(relativePath, relativePath, exception);
        }
        try {
            for (Path segment : relativePath) {
                if (segment.toString().isEmpty()) continue;
                DirectoryDescriptor next;
                try {
                    next = new DirectoryDescriptor(
                            nativeApi,
                            nativeApi.openat(current.value(), segment.toString(), flags));
                } catch (LastErrorException exception) {
                    throw nativeFailure(relativePath, relativePath, exception);
                }
                DirectoryDescriptor previous = current;
                current = next;
                previous.close();
            }
            return current;
        } catch (IOException | RuntimeException exception) {
            current.close();
            throw exception;
        }
    }

    private static IOException nativeFailure(Path source, Path target, LastErrorException exception) {
        IOException failure;
        int errorCode = exception.getErrorCode();
        if (errorCode == EEXIST || errorCode == LINUX_ENOTEMPTY || errorCode == MAC_ENOTEMPTY) {
            failure = new FileAlreadyExistsException(target.toString());
        } else if (errorCode == ENOENT) {
            failure = new NoSuchFileException(source.toString(), target.toString(), "移动路径不存在");
        } else if (errorCode == EPERM
                || errorCode == EACCES
                || errorCode == EXDEV
                || errorCode == ENOTDIR
                || errorCode == LINUX_ELOOP
                || errorCode == MAC_ELOOP) {
            failure = new FileSystemException(
                    source.toString(),
                    target.toString(),
                    "移动路径发生变化、越界或包含符号链接，errno=" + errorCode);
        } else {
            failure = new IOException("安全移动失败，errno=" + errorCode);
        }
        failure.initCause(exception);
        return failure;
    }

    private static IOException windowsFailure(Path source, Path target, LastErrorException exception) {
        IOException failure;
        int errorCode = exception.getErrorCode();
        if (errorCode == WINDOWS_ERROR_FILE_EXISTS || errorCode == WINDOWS_ERROR_ALREADY_EXISTS) {
            failure = new FileAlreadyExistsException(target.toString());
        } else if (errorCode == WINDOWS_ERROR_FILE_NOT_FOUND || errorCode == WINDOWS_ERROR_PATH_NOT_FOUND) {
            failure = new NoSuchFileException(source.toString(), target.toString(), "移动路径不存在");
        } else if (errorCode == WINDOWS_ERROR_ACCESS_DENIED || errorCode == WINDOWS_ERROR_NOT_SAME_DEVICE) {
            failure = new FileSystemException(
                    source.toString(),
                    target.toString(),
                    "移动路径发生变化、越界或不可访问，winError=" + errorCode);
        } else {
            failure = new IOException("安全移动失败，winError=" + errorCode);
        }
        failure.initCause(exception);
        return failure;
    }

    private interface PosixLibC extends Library {
        int open(String path, int flags) throws LastErrorException;

        int openat(int directoryDescriptor, String path, int flags) throws LastErrorException;

        int close(int descriptor) throws LastErrorException;
    }

    private interface DarwinLibC extends PosixLibC {
        int renameatx_np(
                int sourceDirectoryDescriptor,
                String sourceName,
                int targetDirectoryDescriptor,
                String targetName,
                int flags) throws LastErrorException;
    }

    private interface LinuxLibC extends PosixLibC {
        long syscall(
                long systemCallNumber,
                int sourceDirectoryDescriptor,
                String sourceName,
                int targetDirectoryDescriptor,
                String targetName,
                int flags) throws LastErrorException;
    }

    private interface WindowsKernel32 extends StdCallLibrary {
        Pointer CreateFileW(
                WString path,
                int desiredAccess,
                int shareMode,
                Pointer securityAttributes,
                int creationDisposition,
                int flagsAndAttributes,
                Pointer templateFile);

        boolean GetFileInformationByHandleEx(
                Pointer handle,
                int fileInformationClass,
                Pointer fileInformation,
                int bufferSize);

        int GetFinalPathNameByHandleW(
                Pointer handle,
                char[] path,
                int pathLength,
                int flags);

        boolean SetFileInformationByHandle(
                Pointer handle,
                int fileInformationClass,
                Pointer fileInformation,
                int bufferSize);

        boolean CloseHandle(Pointer handle);
    }

    private static final class DarwinHolder {
        private static final DarwinLibC INSTANCE = Native.load(Platform.C_LIBRARY_NAME, DarwinLibC.class);

        private DarwinHolder() {}
    }

    private static final class LinuxHolder {
        private static final LinuxLibC INSTANCE = Native.load(Platform.C_LIBRARY_NAME, LinuxLibC.class);

        private LinuxHolder() {}
    }

    private static final class WindowsHolder {
        private static final WindowsKernel32 INSTANCE = Native.load("kernel32", WindowsKernel32.class);

        private WindowsHolder() {}
    }

    private static final class DirectoryDescriptor implements AutoCloseable {

        private final PosixLibC nativeApi;
        private int value;

        private DirectoryDescriptor(PosixLibC nativeApi, int value) {
            this.nativeApi = nativeApi;
            this.value = value;
        }

        private int value() {
            return value;
        }

        @Override
        public void close() {
            if (value < 0) return;
            int descriptor = value;
            value = -1;
            try {
                nativeApi.close(descriptor);
            } catch (LastErrorException exception) {
                // close 错误不能反转已经提交的 rename；Linux 也明确禁止在错误后重试 close。
                LOGGER.warn(
                        "event=workspace_move_descriptor_close_failed fd={} errno={}",
                        descriptor,
                        exception.getErrorCode());
            }
        }
    }

    private static final class WindowsHandle implements AutoCloseable {

        private final WindowsKernel32 nativeApi;
        private Pointer value;

        private WindowsHandle(WindowsKernel32 nativeApi, Pointer value) {
            this.nativeApi = nativeApi;
            this.value = value;
        }

        private Pointer value() {
            return value;
        }

        @Override
        public void close() {
            if (value == null) return;
            Pointer handle = value;
            value = null;
            if (!nativeApi.CloseHandle(handle)) {
                // 与 POSIX close 相同，句柄关闭错误只能记录，不能反转已经完成的原子重命名。
                LOGGER.warn(
                        "event=workspace_move_handle_close_failed winError={}",
                        Native.getLastError());
            }
        }
    }
}
