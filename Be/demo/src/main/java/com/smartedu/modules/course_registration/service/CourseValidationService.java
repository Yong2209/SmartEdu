package com.smartedu.modules.course_registration.service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.academic_management.entity.Course;
import com.smartedu.modules.academic_management.entity.CourseSection;
import com.smartedu.modules.academic_management.repository.CourseRepository;
import com.smartedu.modules.academic_management.repository.CourseSectionRepository;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CourseImportRow;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CourseImportRowInput;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ImportRowErrorDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourseValidationService {

    public static final long MAX_IMPORT_FILE_SIZE = 5L * 1024L * 1024L;

    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final ObjectMapper objectMapper;

    public record RawParsedRow(long rowNumber, Map<String, String> values) {}

    public record ValidationResult(List<CourseImportRow> validItems, List<ImportRowErrorDto> errors) {}

    public String validateAndDetectFormat(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Vui lòng chọn tệp để tải lên.");
        }
        if (file.getSize() > MAX_IMPORT_FILE_SIZE) {
            throw new BusinessException("Dung lượng tệp vượt quá giới hạn tối đa 5MB.");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename().trim() : "";
        int dotIdx = originalName.lastIndexOf('.');
        String ext = dotIdx >= 0 ? originalName.substring(dotIdx + 1).toUpperCase(Locale.ROOT) : "";
        if (!"CSV".equals(ext) && !"XLSX".equals(ext)) {
            throw new BusinessException("Định dạng tệp không hợp lệ. Chỉ hỗ trợ tệp CSV hoặc XLSX.");
        }
        return ext;
    }

    public List<RawParsedRow> parseFileRows(InputStream inputStream, String format) {
        try {
            if ("CSV".equalsIgnoreCase(format)) {
                return parseCsv(inputStream);
            }
            if ("XLSX".equalsIgnoreCase(format)) {
                return parseXlsx(inputStream);
            }
            throw new BusinessException("Định dạng tệp không được hỗ trợ: " + format);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Không thể đọc dữ liệu từ tệp. Vui lòng kiểm tra cấu trúc tệp.", HttpStatus.BAD_REQUEST);
        }
    }

    public ValidationResult validateCourseRows(Long semesterId, List<CourseImportRowInput> inputs) {
        List<CourseImportRow> validItems = new ArrayList<>();
        List<ImportRowErrorDto> errors = new ArrayList<>();

        if (inputs == null || inputs.isEmpty()) {
            errors.add(ImportRowErrorDto.builder()
                    .rowNumber(0L)
                    .fieldName("file")
                    .errorCode("EMPTY_DATA")
                    .errorMessage("Danh sách học phần không có dữ liệu.")
                    .build());
            return new ValidationResult(validItems, errors);
        }

        Set<String> seenCourseCodes = new HashSet<>();
        long rowNum = 1;
        for (CourseImportRowInput input : inputs) {
            long currentRow = rowNum++;
            String code = trimToNull(input.getCourseCode());
            String name = trimToNull(input.getCourseName());
            BigDecimal credits = input.getCredits();
            String sectionCode = trimToNull(input.getSectionCode());
            String rowJson = serializeRowData(input);

            boolean rowHasError = false;

            if (code == null) {
                errors.add(err(currentRow, "course_code", "REQUIRED_FIELD",
                        "Thiếu mã học phần.", rowJson));
                rowHasError = true;
            } else if (code.length() > 30) {
                errors.add(err(currentRow, "course_code", "INVALID_FORMAT",
                        "Mã học phần vượt quá 30 ký tự.", rowJson));
                rowHasError = true;
            }

            if (name == null) {
                errors.add(err(currentRow, "course_name", "REQUIRED_FIELD",
                        "Thiếu tên học phần.", rowJson));
                rowHasError = true;
            } else if (name.length() > 255) {
                errors.add(err(currentRow, "course_name", "INVALID_FORMAT",
                        "Tên học phần vượt quá 255 ký tự.", rowJson));
                rowHasError = true;
            }

            if (credits == null) {
                errors.add(err(currentRow, "credits", "REQUIRED_FIELD",
                        "Thiếu số tín chỉ.", rowJson));
                rowHasError = true;
            } else if (credits.compareTo(BigDecimal.ZERO) <= 0 || credits.compareTo(new BigDecimal("20")) > 0) {
                errors.add(err(currentRow, "credits", "INVALID_CREDITS",
                        "Số tín chỉ phải lớn hơn 0 và không vượt quá 20.", rowJson));
                rowHasError = true;
            }

            Course catalogCourse = null;
            if (code != null) {
                String normalizedCode = code.toUpperCase(Locale.ROOT);
                if (!seenCourseCodes.add(normalizedCode)) {
                    errors.add(err(currentRow, "course_code", "DUPLICATE_COURSE",
                            "Mã học phần '" + code + "' bị trùng lặp trong danh sách.", rowJson));
                    rowHasError = true;
                } else {
                    catalogCourse = courseRepository.findByCourseCodeIgnoreCase(code).orElse(null);
                    if (catalogCourse == null || !Boolean.TRUE.equals(catalogCourse.getIsActive())) {
                        errors.add(err(currentRow, "course_code", "COURSE_NOT_FOUND",
                                "Mã học phần '" + code + "' không tồn tại hoặc đã ngừng mở trong danh mục hệ thống.", rowJson));
                        rowHasError = true;
                    } else if (credits != null && catalogCourse.getCredits().compareTo(credits) != 0) {
                        errors.add(err(currentRow, "credits", "CREDITS_MISMATCH",
                                "Số tín chỉ (" + credits + ") không khớp với danh mục hệ thống (" + catalogCourse.getCredits() + ").",
                                rowJson));
                        rowHasError = true;
                    }
                }
            }

            if (sectionCode != null && semesterId != null) {
                CourseSection section = courseSectionRepository
                        .findBySemesterIdAndSectionCodeIgnoreCase(semesterId, sectionCode)
                        .orElse(null);
                if (section == null) {
                    errors.add(err(currentRow, "section_code", "SECTION_NOT_FOUND",
                            "Lớp học phần '" + sectionCode + "' không tồn tại trong học kỳ đã chọn.", rowJson));
                    rowHasError = true;
                } else if (catalogCourse != null && !section.getCourse().getId().equals(catalogCourse.getId())) {
                    errors.add(err(currentRow, "section_code", "SECTION_COURSE_MISMATCH",
                            "Lớp học phần '" + sectionCode + "' không thuộc học phần '" + catalogCourse.getCourseCode() + "'.",
                            rowJson));
                    rowHasError = true;
                }
            }

            if (!rowHasError && catalogCourse != null) {
                validItems.add(CourseImportRow.builder()
                        .rowNumber(currentRow)
                        .courseId(catalogCourse.getId())
                        .courseCode(catalogCourse.getCourseCode())
                        .courseName(catalogCourse.getCourseName())
                        .credits(catalogCourse.getCredits())
                        .sectionCode(sectionCode)
                        .valid(true)
                        .build());
            }
        }
        return new ValidationResult(validItems, errors);
    }

    public List<CourseImportRowInput> toCourseInputs(List<RawParsedRow> rawRows, List<ImportRowErrorDto> parseErrors) {
        List<CourseImportRowInput> result = new ArrayList<>();
        for (RawParsedRow raw : rawRows) {
            String code = getField(raw.values(), "course_code", "coursecode", "mahocphan", "ma_hoc_phan", "code");
            String name = getField(raw.values(), "course_name", "coursename", "tenhocphan", "ten_hoc_phan", "name");
            String creditsStr = getField(raw.values(), "credits", "sotinchi", "so_tin_chi", "tc");
            String section = getField(raw.values(), "section_code", "sectioncode", "malophocphan", "ma_lop", "section");

            BigDecimal credits = null;
            if (creditsStr != null && !creditsStr.isBlank()) {
                try {
                    credits = new BigDecimal(creditsStr.replace(",", ".").trim());
                } catch (NumberFormatException ex) {
                    parseErrors.add(err(raw.rowNumber(), "credits", "INVALID_FORMAT",
                            "Định dạng số tín chỉ không hợp lệ: '" + creditsStr + "'.",
                            serializeRowData(raw.values())));
                    credits = new BigDecimal("-1");
                }
            }
            result.add(new CourseImportRowInput(code, name, credits, section));
        }
        return result;
    }

    public String getField(Map<String, String> row, String... aliases) {
        for (String alias : aliases) {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (normalizeHeader(entry.getKey()).equals(alias)) {
                    String val = entry.getValue();
                    return (val == null || val.isBlank()) ? null : val.trim();
                }
            }
        }
        return null;
    }

    public byte[] buildSampleCourseXlsx() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Courses");
            Row header = sheet.createRow(0);
            String[] cols = {"course_code", "course_name", "credits", "section_code"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("DEMO-CS201");
            row1.createCell(1).setCellValue("Cơ sở dữ liệu");
            row1.createCell(2).setCellValue(3.0);
            row1.createCell(3).setCellValue("DEMO-CS201-01");

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("DEMO-CS202");
            row2.createCell(1).setCellValue("Lập trình hướng đối tượng");
            row2.createCell(2).setCellValue(3.0);
            row2.createCell(3).setCellValue("DEMO-CS202-01");

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Không thể tạo tệp mẫu XLSX.");
        }
    }

    public String serializeRowData(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private List<RawParsedRow> parseCsv(InputStream inputStream) throws IOException {
        List<RawParsedRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException("Tệp CSV rỗng, không có dòng tiêu đề.");
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            char delimiter = headerLine.contains(";") && !headerLine.contains(",") ? ';' : ',';
            List<String> headers = splitCsvLine(headerLine, delimiter);
            if (headers.isEmpty()) {
                throw new BusinessException("Tệp CSV thiếu dòng tiêu đề.");
            }

            String line;
            long rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = splitCsvLine(line, delimiter);
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String h = headers.get(i).trim();
                    String v = i < values.size() ? values.get(i).trim() : "";
                    rowMap.put(h, v);
                }
                rows.add(new RawParsedRow(rowNumber++, rowMap));
            }
        }
        return rows;
    }

    private List<RawParsedRow> parseXlsx(InputStream inputStream) throws Exception {
        List<RawParsedRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(inputStream)) {
            if (wb.getNumberOfSheets() == 0) {
                throw new BusinessException("Tệp Excel không có sheet dữ liệu.");
            }
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BusinessException("Tệp Excel thiếu dòng tiêu đề.");
            }

            List<String> headers = new ArrayList<>();
            int lastCell = headerRow.getLastCellNum();
            for (int c = 0; c < lastCell; c++) {
                Cell cell = headerRow.getCell(c);
                headers.add(cell != null ? formatter.formatCellValue(cell).trim() : "col_" + c);
            }

            long rowNum = 1;
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> rowMap = new LinkedHashMap<>();
                boolean hasAnyText = false;
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    String val = cell != null ? formatter.formatCellValue(cell).trim() : "";
                    if (!val.isBlank()) {
                        hasAnyText = true;
                    }
                    rowMap.put(headers.get(c), val);
                }
                if (hasAnyText) {
                    rows.add(new RawParsedRow(rowNum++, rowMap));
                }
            }
        }
        return rows;
    }

    private List<String> splitCsvLine(String line, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == delimiter && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        result.add(current.toString());
        return result;
    }

    private String normalizeHeader(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "_")
                .replace("-", "_");
    }

    private ImportRowErrorDto err(long rowNum, String field, String code, String msg, String rowData) {
        return ImportRowErrorDto.builder()
                .rowNumber(rowNum)
                .fieldName(field)
                .errorCode(code)
                .errorMessage(msg)
                .rowData(rowData)
                .build();
    }

    private String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
