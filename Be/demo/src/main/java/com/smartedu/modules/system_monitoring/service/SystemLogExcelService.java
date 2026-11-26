package com.smartedu.modules.system_monitoring.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.smartedu.modules.system_monitoring.dto.SystemLogDtos;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SystemLogExcelService {

    private final SystemLogService systemLogService;

    public byte[] exportActivities() {

        List<SystemLogDtos.ActivityLogResponse> logs =
                systemLogService.getActivityLogs(null, null);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Activity Logs");

            Row header = sheet.createRow(0);

            String[] columns = {
                    "ID",
                    "Actor",
                    "Action",
                    "Entity Type",
                    "Entity ID",
                    "Detail",
                    "IP Address",
                    "Created At"
            };

            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            int rowIndex = 1;

            for (var log : logs) {

                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(log.id() == null ? 0 : log.id());
                row.createCell(1).setCellValue(log.actorName() == null ? "" : log.actorName());
                row.createCell(2).setCellValue(log.actionCode() == null ? "" : log.actionCode());
                row.createCell(3).setCellValue(log.entityType() == null ? "" : log.entityType());
                row.createCell(4).setCellValue(log.entityId() == null ? 0 : log.entityId());
                row.createCell(5).setCellValue(log.detail() == null ? "" : log.detail());
                row.createCell(6).setCellValue(log.ipAddress() == null ? "" : log.ipAddress());
                row.createCell(7).setCellValue(
                        log.createdAt() == null ? "" : log.createdAt().toString()
                );
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(output);

            return output.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Không thể xuất file Excel nhật ký.",
                    e
            );
        }
    }
}
