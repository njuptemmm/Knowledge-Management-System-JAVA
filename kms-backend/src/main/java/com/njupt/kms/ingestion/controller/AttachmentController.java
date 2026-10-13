package com.njupt.kms.ingestion.controller;

import com.njupt.kms.common.Result;
import com.njupt.kms.ingestion.dto.AttachmentVO;
import com.njupt.kms.ingestion.entity.Attachment;
import com.njupt.kms.ingestion.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final IngestionService ingestionService;

    @PostMapping("/upload")
    public Result<AttachmentVO> upload(@RequestParam("itemId") Long itemId,
                                       @RequestParam("file") MultipartFile file) {
        return Result.ok(ingestionService.upload(itemId, file));
    }

    @GetMapping("/item/{itemId}")
    public Result<List<AttachmentVO>> listByItem(@PathVariable Long itemId) {
        return Result.ok(ingestionService.listByItem(itemId));
    }

    /** 解析状态与文本预览。前端轮询此接口展示解析进度。 */
    @GetMapping("/{id}/text")
    public Result<AttachmentVO> text(@PathVariable Long id) {
        return Result.ok(AttachmentVO.from(ingestionService.get(id)));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Attachment attachment = ingestionService.get(id);
        Resource resource = ingestionService.download(id);
        // 原始文件名可能含中文，用 RFC 5987 的 filename* 才能正确传递
        String encodedName = URLEncoder.encode(attachment.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ingestionService.delete(id);
        return Result.ok();
    }
}
