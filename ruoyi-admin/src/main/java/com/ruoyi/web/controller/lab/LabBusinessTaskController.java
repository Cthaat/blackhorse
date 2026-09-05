package com.ruoyi.web.controller.lab;

import java.io.IOException;
import java.util.Map;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.lab.task.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/lab/tasks")
@PreAuthorize("@ss.hasPermi('lab:task:list')")
public class LabBusinessTaskController extends LabBaseController
{
    public record ExportRequest(@NotBlank @Pattern(regexp="LABORATORY|DEVICE|RESERVATION|REPAIR|HAZARD") String kind,
            @NotNull @Size(max=12) Map<@Size(max=32) String,@Size(max=200) String> filters) { }
    private final BusinessTaskService service;
    private final TaskWorkbook workbook;
    private final TaskBusinessAdapter business;
    private final TaskActorContext actors;
    public LabBusinessTaskController(BusinessTaskService service,TaskWorkbook workbook,TaskBusinessAdapter business,TaskActorContext actors)
    {this.service=service;this.workbook=workbook;this.business=business;this.actors=actors;}
    @GetMapping public AjaxResult list(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="10") int pageSize)
    {return success(service.list(pageNum,pageSize));}
    @GetMapping("/{id}") public AjaxResult detail(@PathVariable @Positive long id){return success(service.detail(id));}
    @GetMapping("/{id}/rows") public AjaxResult rows(@PathVariable @Positive long id,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="100") int pageSize)
    {return success(service.rows(id,pageNum,pageSize));}
    @PostMapping("/precheck") public AjaxResult precheck(@RequestParam @Pattern(regexp="LABORATORY|DEVICE") String kind,@RequestParam MultipartFile file) throws IOException
    {TaskRules.validateUpload(file.getSize());return success(service.precheck(kind,file.getBytes()));}
    @PostMapping("/exports") public AjaxResult export(@RequestBody @Valid ExportRequest request){return success(service.export(request.kind(),request.filters()));}
    @PostMapping("/{id}/commands/submit") public AjaxResult submit(@PathVariable @Positive long id){return success(service.submit(id));}
    @PostMapping("/{id}/commands/cancel") public AjaxResult cancel(@PathVariable @Positive long id){return success(service.cancel(id));}
    @PostMapping("/{id}/commands/retry") public AjaxResult retry(@PathVariable @Positive long id){return success(service.retry(id));}
    @GetMapping("/template") public ResponseEntity<byte[]> template(@RequestParam @Pattern(regexp="LABORATORY|DEVICE") String kind) throws IOException
    {
        actors.asCurrentActor(getUserId(),()->{business.permission(kind,true);return null;});
        return file(workbook.write(TaskWorkbook.columns(kind),java.util.List.of(),null),"template-"+kind+".xlsx");
    }
    @GetMapping("/{id}/download") public ResponseEntity<byte[]> download(@PathVariable @Positive long id,@RequestParam(defaultValue="false") boolean errors)
    {return file(service.download(id,errors),"task-"+id+(errors?"-errors":"")+".xlsx");}
    private static ResponseEntity<byte[]> file(byte[] bytes,String name)
    {return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name).build().toString())
        .header(HttpHeaders.CACHE_CONTROL,"no-store").body(bytes);}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<AjaxResult> invalid(IllegalArgumentException ignored)
    {return ResponseEntity.badRequest().body(AjaxResult.error(400,"任务参数或工作簿不符合要求，请核对模板和限制"));}
}
