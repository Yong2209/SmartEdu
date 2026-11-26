package com.smartedu.modules.system_monitoring.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.modules.system_monitoring.service.SystemLogExcelService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/system-logs")
@RequiredArgsConstructor
public class SystemLogExportController {

    private final SystemLogExcelService excelService;

    @GetMapping("/activities/export")
    public ResponseEntity<byte[]> exportActivities() {

        byte[] file = excelService.exportActivities();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=activity-logs.xlsx"
                )
                .body(file);
    }
}
