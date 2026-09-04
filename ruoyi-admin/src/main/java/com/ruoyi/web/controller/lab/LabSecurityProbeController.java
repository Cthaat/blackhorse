package com.ruoyi.web.controller.lab;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 永久、无副作用的认证链路探针。
 */
@RestController
@RequestMapping("/lab")
@Tag(name = "实验室安全探针")
public class LabSecurityProbeController
{
    @GetMapping("/security-probe")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "验证实验室接口认证链路", description = "认证成功时返回 204，且不返回响应体。")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "认证有效"),
        @ApiResponse(responseCode = "401", description = "未认证或登录状态已失效",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "已认证但无权访问",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> probe()
    {
        return ResponseEntity.noContent().build();
    }
}
