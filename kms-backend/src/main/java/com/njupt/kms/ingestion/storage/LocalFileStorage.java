package com.njupt.kms.ingestion.storage;

import com.njupt.kms.common.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    /**
     * 允许的扩展名白名单。不放行 .exe/.sh/.jsp 等可执行或脚本类型。
     *
     * <p>{@code doc}（旧版 OLE2 格式）在列：它无法被解析（见 DocxParser 的说明），
     * 但文件本身仍可存储与下载。拒绝上传而让用户猜原因，不如存下来并明确告知解析失败。
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "md", "markdown", "txt", "png", "jpg", "jpeg", "gif", "webp", "mp4");

    private static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path root;

    public LocalFileStorage(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void prepare() throws IOException {
        Files.createDirectories(root);
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException("文件不能超过 20MB");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("不支持的文件类型：" + (extension.isEmpty() ? "未知" : extension));
        }

        // 文件名完全丢弃客户端输入，只用扩展名 + UUID，从根源消除路径穿越
        String relativeDir = LocalDate.now().format(MONTH_DIR);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String relativePath = relativeDir + "/" + storedName;

        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("非法的存储路径");
        }

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            log.error("文件写入失败 target={}", target, e);
            throw new BusinessException("文件保存失败，请重试");
        }
        return relativePath;
    }

    @Override
    public Resource load(String storedPath) {
        Path target = root.resolve(storedPath).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("非法的文件路径");
        }
        return new PathResource(target);
    }

    @Override
    public void delete(String storedPath) {
        if (!StringUtils.hasText(storedPath)) {
            return;
        }
        Path target = root.resolve(storedPath).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("文件删除失败 path={}", target, e);
        }
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
