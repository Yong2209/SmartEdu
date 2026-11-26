package com.smartedu.modules.academic_management.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.academic_management.dto.CourseImportDtos;
import com.smartedu.modules.academic_management.entity.Course;
import com.smartedu.modules.academic_management.repository.CourseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourseExcelImportService {

    private final CourseRepository courseRepository;

    public CourseImportDtos.ImportResponse importCourses(
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    "Vui lòng chọn file Excel.",
                    HttpStatus.BAD_REQUEST
            );
        }

        List<CourseImportDtos.ImportRowResult> results = new ArrayList<>();

        DataFormatter formatter = new DataFormatter();

        try (InputStream input = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(input)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);

                if (row == null) {
                    continue;
                }

                String courseCode = formatter.formatCellValue(row.getCell(0)).trim();
                String courseName = formatter.formatCellValue(row.getCell(1)).trim();
                String creditsText = formatter.formatCellValue(row.getCell(2)).trim();
                String description = formatter.formatCellValue(row.getCell(3)).trim();

                try {
                    if (courseCode.isBlank() || courseName.isBlank()) {
                        throw new IllegalArgumentException(
                                "Mã học phần và tên học phần không được để trống."
                        );
                    }

                    BigDecimal credits = new BigDecimal(creditsText);

                    Course course = courseRepository
                            .findByCourseCodeIgnoreCase(courseCode)
                            .orElseGet(Course::new);

                    course.setCourseCode(courseCode);
                    course.setCourseName(courseName);
                    course.setCredits(credits);
                    course.setDescription(description);
                    course.setIsActive(true);

                    courseRepository.save(course);

                    results.add(new CourseImportDtos.ImportRowResult(
                            i + 1,
                            courseCode,
                            null,
                            true,
                            "Nhập học phần thành công."
                    ));

                } catch (Exception ex) {
                    results.add(new CourseImportDtos.ImportRowResult(
                            i + 1,
                            courseCode,
                            null,
                            false,
                            ex.getMessage()
                    ));
                }
            }

        } catch (Exception ex) {
            throw new BusinessException(
                    "Không thể đọc file Excel: " + ex.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        }

        int success = (int) results.stream()
                .filter(CourseImportDtos.ImportRowResult::success)
                .count();

        return new CourseImportDtos.ImportResponse(
                results.size(),
                success,
                results.size() - success,
                results
        );
    }
}
