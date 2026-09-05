package com.ruoyi.web.ops;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.controller.lab.LabBaseController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lab/operations")
public class LabOperationsController extends LabBaseController
{
    private final OperationsService service;
    public LabOperationsController(OperationsService service) {this.service=service;}
    @GetMapping
    @PreAuthorize("@ss.hasPermi('lab:operations:view')")
    public AjaxResult snapshot() {return success(service.snapshot());}
}
