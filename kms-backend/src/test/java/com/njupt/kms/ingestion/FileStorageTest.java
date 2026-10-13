package com.njupt.kms.ingestion;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.ingestion.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageTest {

    @TempDir
    Path tempDir;

    private LocalFileStorage storage() {
        return new LocalFileStorage(tempDir.toString());
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    @Test
    void stores_file_under_dated_directory_and_keeps_extension() throws Exception {
        String stored = storage().store(
                file("需求规格说明书.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8)));

        assertThat(stored).endsWith(".pdf").contains("/");
        assertThat(Files.exists(tempDir.resolve(stored))).isTrue();
        // 路径形如 yyyyMM/uuid.pdf
        assertThat(stored).matches("\\d{6}/[0-9a-f]{32}\\.pdf");
    }

    @Test
    void rejects_empty_file() {
        assertThatThrownBy(() -> storage().store(
                file("empty.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上传文件为空");
    }

    @Test
    void rejects_null_file() {
        assertThatThrownBy(() -> storage().store(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上传文件为空");
    }

    @Test
    void rejects_executable_extension() {
        assertThatThrownBy(() -> storage().store(
                file("virus.exe", "application/octet-stream", "MZ".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    void rejects_file_without_extension() {
        assertThatThrownBy(() -> storage().store(
                file("noextension", "text/plain", "x".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    void sanitizes_path_traversal_in_filename() throws Exception {
        String stored = storage().store(file(
                "../../etc/passwd.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8)));

        // 存储路径必须落在上传根目录内，不允许跳出
        Path resolved = tempDir.resolve(stored).normalize();
        assertThat(resolved.startsWith(tempDir.normalize())).isTrue();
        assertThat(stored).doesNotContain("..");
    }

    @Test
    void stored_name_does_not_reuse_client_filename() throws Exception {
        String stored = storage().store(file(
                "很长的中文文件名带空格 和符号.pdf", "application/pdf",
                "x".getBytes(StandardCharsets.UTF_8)));

        // 文件名完全丢弃客户端输入，只保留扩展名
        assertThat(stored).doesNotContain("中文");
        assertThat(stored).endsWith(".pdf");
    }

    @Test
    void rejects_oversized_file() {
        byte[] big = new byte[21 * 1024 * 1024];

        assertThatThrownBy(() -> storage().store(file("big.pdf", "application/pdf", big)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过 20MB");
    }

    @Test
    void delete_removes_stored_file() throws Exception {
        LocalFileStorage storage = storage();
        String stored = storage.store(
                file("a.md", "text/markdown", "content".getBytes(StandardCharsets.UTF_8)));

        storage.delete(stored);

        assertThat(Files.exists(tempDir.resolve(stored))).isFalse();
    }

    @Test
    void delete_ignores_blank_path() {
        storage().delete(null);
        storage().delete("  ");
        // 不抛异常即通过
    }

    @Test
    void load_rejects_path_outside_root() {
        assertThatThrownBy(() -> storage().load("../../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("非法的文件路径");
    }

    @Test
    void load_returns_readable_resource() throws Exception {
        LocalFileStorage storage = storage();
        String stored = storage.store(
                file("a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)));

        assertThat(storage.load(stored).getContentAsString(StandardCharsets.UTF_8)).isEqualTo("hello");
    }
}
