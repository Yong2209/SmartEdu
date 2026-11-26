package com.smartedu.modules.schedule_management.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.modules.schedule_management.dto.ScheduleExportDtos;
import com.smartedu.modules.schedule_management.service.ScheduleExportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleExportController {

    private final ScheduleExportService exportService;

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(
            @Valid @RequestBody ScheduleExportDtos.ExportRequest request
    ) {
        byte[] file = exportService.export(request);

        boolean isPdf = "PDF".equalsIgnoreCase(request.format());

        String filename = isPdf ? "thoi-khoa-bieu.pdf" : "thoi-khoa-bieu.png";

        MediaType mediaType = isPdf
                ? MediaType.APPLICATION_PDF
                : MediaType.IMAGE_PNG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .body(file);
    }
}
