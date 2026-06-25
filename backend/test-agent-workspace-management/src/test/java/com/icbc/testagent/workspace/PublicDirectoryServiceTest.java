package com.icbc.testagent.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * PublicDirectoryService 单元测试：覆盖未配置/不存在的根目录、正常 list/read/write 委托。
 */
class PublicDirectoryServiceTest {

    @Test
    void 未配置时isEnabled返回false且三个方法都返回NOT_FOUND() {
        WorkspaceFileService fileService = Mockito.mock(WorkspaceFileService.class);
        PublicDirectoryService service = new PublicDirectoryService(fileService, "  ");

        assertFalse(service.isEnabled(), "空配置应当视为禁用");

        for (var ignore : List.of("list", "read", "write")) {
            try {
                if ("list".equals(ignore)) {
                    service.listDirectory("sub");
                } else if ("read".equals(ignore)) {
                    service.readContent("sub");
                } else {
                    service.writeContent("sub", "data");
                }
                throw new AssertionError("未配置时调用 " + ignore + " 必须抛 NOT_FOUND");
            } catch (PlatformException exception) {
                assertEquals(ErrorCode.NOT_FOUND, exception.errorCode(), "未配置时 " + ignore + " 应当返回 NOT_FOUND");
            }
        }
        verify(fileService, never()).listDirectory(anyString(), anyString());
        verify(fileService, never()).readContent(anyString(), anyString());
        verify(fileService, never()).writeContent(anyString(), anyString(), anyString());
    }

    @Test
    void 根目录不存在时isEnabled返回false且list返回NOT_FOUND(@TempDir java.nio.file.Path tempDir) throws IOException {
        // 真实存在的根目录先建好，再被删除 → 模拟"配置了但目录不存在"。
        java.nio.file.Path root = Files.createDirectory(tempDir.resolve("public"));
        Files.delete(root);
        WorkspaceFileService fileService = Mockito.mock(WorkspaceFileService.class);
        PublicDirectoryService service = new PublicDirectoryService(fileService, root.toString());

        assertFalse(service.isEnabled(), "目录不存在时 isEnabled 必须为 false");

        PlatformException exception = assertThrows(
                PlatformException.class,
                () -> service.listDirectory(""),
                "目录不存在时 listDirectory 必须抛 NOT_FOUND");
        assertEquals(ErrorCode.NOT_FOUND, exception.errorCode());
        verify(fileService, never()).listDirectory(anyString(), anyString());
    }

    @Test
    void 配置正常时listReadWrite全部委托给WorkspaceFileService(@TempDir java.nio.file.Path tempDir) throws IOException {
        java.nio.file.Path root = Files.createDirectory(tempDir.resolve("public"));
        WorkspaceFileService fileService = Mockito.mock(WorkspaceFileService.class);
        FileTreeEntryResponse entry = new FileTreeEntryResponse("a.txt", "a.txt", false, 1L, java.time.Instant.parse("2025-01-01T00:00:00Z"));
        FileContentResponse content = new FileContentResponse("a.txt", "hello", 5L);
        when(fileService.listDirectory(anyString(), anyString())).thenReturn(List.of(entry));
        when(fileService.readContent(anyString(), anyString())).thenReturn(content);

        PublicDirectoryService service = new PublicDirectoryService(fileService, root.toString());

        assertTrue(service.isEnabled(), "正常目录 isEnabled 必须为 true");
        assertEquals(List.of(entry), service.listDirectory("sub"));
        assertNotNull(service.readContent("sub/a.txt"));
        service.writeContent("sub/a.txt", "data");

        // 解析后的根路径可能因 toRealPath 与符号链接有差异，用 anyString() 校验委托即可。
        verify(fileService, times(1)).listDirectory(anyString(), eq("sub"));
        verify(fileService, times(1)).readContent(anyString(), eq("sub/a.txt"));
        verify(fileService, times(1)).writeContent(anyString(), eq("sub/a.txt"), eq("data"));
    }

    @Test
    void 根目录不可访问时list会包装为NOT_FOUND(@TempDir java.nio.file.Path tempDir) throws IOException {
        // 真实存在的根目录先建好，再删除 → 模拟"配置了但目录不可访问"。
        // 这里通过 @TempDir 的"创建后立即删除子目录"覆盖 resolveRealRoot 中的 Files.isDirectory/toRealPath 分支。
        java.nio.file.Path root = Files.createDirectory(tempDir.resolve("public"));
        Files.delete(root);
        WorkspaceFileService fileService = Mockito.mock(WorkspaceFileService.class);
        PublicDirectoryService service = new PublicDirectoryService(fileService, root.toString());

        PlatformException exception = assertThrows(
                PlatformException.class,
                () -> service.listDirectory("sub"),
                "目录不可访问时 listDirectory 必须抛 NOT_FOUND");
        assertEquals(ErrorCode.NOT_FOUND, exception.errorCode());
        // 文件服务不应被调用，因为根目录解析阶段就抛了。
        verify(fileService, never()).listDirectory(anyString(), anyString());
    }
}
