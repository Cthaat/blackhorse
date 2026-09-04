package com.ruoyi.web.controller.lab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.service.AttachmentService;
import com.ruoyi.lab.vo.AttachmentContent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Authorized private attachment API. */
@Validated
@RestController
@RequestMapping("/lab/attachments")
public class LabAttachmentController extends BaseController
{
    private final AttachmentService attachmentService;

    public LabAttachmentController(AttachmentService attachmentService)
    {
        this.attachmentService = attachmentService;
    }

    @PreAuthorize("@ss.hasPermi('lab:attachment:read')")
    @GetMapping
    public AjaxResult list(@RequestParam @NotBlank String businessType,
            @RequestParam @Positive Long businessId)
    {
        return success(attachmentService.list(businessType, businessId));
    }

    @PreAuthorize("@ss.hasPermi('lab:attachment:manage')")
    @Log(title = "实验室附件", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult upload(@RequestParam @NotBlank String businessType,
            @RequestParam @Positive Long businessId, @RequestParam("file") MultipartFile file)
    {
        try
        {
            return success(attachmentService.upload(businessType, businessId,
                    file.getOriginalFilename(), file.getContentType(), file.getBytes(), getUsername()));
        }
        catch (IOException exception)
        {
            throw new LabBusinessException(LabErrorCode.INTERNAL_ERROR, "附件读取失败");
        }
    }

    @PreAuthorize("@ss.hasPermi('lab:attachment:read')")
    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> download(@PathVariable @Positive Long id)
    {
        AttachmentContent content = attachmentService.download(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.originalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(content.input()));
    }

    @PreAuthorize("@ss.hasPermi('lab:attachment:manage')")
    @Log(title = "实验室附件", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable @Positive Long id)
    {
        attachmentService.delete(id);
        return success();
    }
}
