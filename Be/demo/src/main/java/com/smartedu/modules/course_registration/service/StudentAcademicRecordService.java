package com.smartedu.modules.course_registration.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.academic_management.entity.Course;
import com.smartedu.modules.academic_management.entity.Semester;
import com.smartedu.modules.academic_management.repository.CourseRepository;
import com.smartedu.modules.academic_management.repository.SemesterRepository;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.AcademicRecordResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ImportRowErrorDto;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.TranscriptImportResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.UpsertAcademicRecordRequest;
import com.smartedu.modules.course_registration.entity.RegistrationImport;
import com.smartedu.modules.course_registration.entity.RegistrationImportError;
import com.smartedu.modules.course_registration.entity.StudentCourseRecord;
import com.smartedu.modules.course_registration.repository.RegistrationImportRepository;
import com.smartedu.modules.course_registration.repository.StudentCourseRecordRepository;
import com.smartedu.modules.course_registration.service.CourseValidationService.RawParsedRow;
import com.smartedu.modules.user_management.entity.User;

@Service
public class StudentAcademicRecordService {

    private static final Set<String> VALID_STATUSES = Set.of("PASSED", "FAILED");

    private final StudentCourseRecordRepository recordRepository;
    private final CourseRepository courseRepository;
    private final SemesterRepository semesterRepository;
    private final RegistrationImportRepository importRepository;
    private final CourseValidationService validationService;
    private final Path transcriptDir;

    public StudentAcademicRecordService(
            StudentCourseRecordRepository recordRepository,
            CourseRepository courseRepository,
            SemesterRepository semesterRepository,
            RegistrationImportRepository importRepository,
            CourseValidationService validationService,
            @Value("${smartedu.storage.upload-dir:uploads}") String uploadDir) {
        this.recordRepository = recordRepository;
        this.courseRepository = courseRepository;
        this.semesterRepository = semesterRepository;
        this.importRepository = importRepository;
        this.validationService = validationService;
        this.transcriptDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("transcripts");
    }

    @Transactional(readOnly = true)
    public List<AcademicRecordResponse> listMyRecords(User user) {
        return recordRepository.findByUserIdOrderByCompletedAtDescIdDesc(user.getId()).stream()
                .map(this::toRecordResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AcademicRecordResponse> listPassedRecords(User user) {
        return recordRepository.findByUserIdAndResultStatusIgnoreCase(user.getId(), "PASSED").stream()
                .map(this::toRecordResponse)
                .toList();
    }

    @Transactional
    public AcademicRecordResponse upsertRecord(User user, UpsertAcademicRecordRequest req) {
        Course course = resolveCourse(req.getCourseId(), req.getCourseCode());
        Semester semester = req.getSemesterId() != null
                ? semesterRepository.findById(req.getSemesterId())
                        .orElseThrow(() -> new BusinessException("Không tìm thấy học kỳ.", HttpStatus.NOT_FOUND))
                : null;

        String status = normalizeResultStatus(req.getResultStatus());
        validateGrade(req.getGrade());

        StudentCourseRecord record = upsertSingleRecord(user, course, semester, null, req.getGrade(), status);
        return toRecordResponse(record);
    }

    @Transactional
    public void deleteRecord(User user, Long recordId) {
        StudentCourseRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy kết quả học tập.", HttpStatus.NOT_FOUND));
        if (!record.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền xóa kết quả học tập này.", HttpStatus.FORBIDDEN);
        }
        recordRepository.delete(record);
    }

    @Transactional
    public TranscriptImportResponse importTranscript(User user, Long semesterId, MultipartFile file) {
        Semester defaultSemester;
        if (semesterId != null) {
            defaultSemester = semesterRepository.findById(semesterId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy học kỳ.", HttpStatus.NOT_FOUND));
        } else {
            defaultSemester = semesterRepository.findAllByOrderByAcademicYearDescTermNoDesc().stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Hệ thống chưa có học kỳ nào để ghi nhận bảng điểm."));
        }

        String format = validationService.validateAndDetectFormat(file);
        String originalName = file.getOriginalFilename() != null
                ? Paths.get(file.getOriginalFilename()).getFileName().toString()
                : "transcript." + format.toLowerCase();

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Không thể đọc nội dung tệp bảng điểm.");
        }

        List<RawParsedRow> rawRows = validationService.parseFileRows(new ByteArrayInputStream(bytes), format);
        if (rawRows.isEmpty()) {
            throw new BusinessException("Tệp bảng điểm không có dữ liệu.");
        }

        Path destination = storeTranscriptFile(bytes, format.toLowerCase());
        RegistrationImport imp = RegistrationImport.builder()
                .user(user)
                .semester(defaultSemester)
                .importType("TRANSCRIPT")
                .fileName(originalName)
                .storagePath(destination.toString())
                .fileFormat(format)
                .importStatus("PROCESSING")
                .build();
        imp = importRepository.save(imp);

        List<ImportRowErrorDto> errors = new ArrayList<>();
        List<AcademicRecordResponse> savedRecords = new ArrayList<>();

        for (RawParsedRow raw : rawRows) {
            String code = validationService.getField(raw.values(), "course_code", "coursecode", "mahocphan", "ma_hoc_phan", "code");
            String gradeStr = validationService.getField(raw.values(), "grade", "diem", "diem_so", "score");
            String statusStr = validationService.getField(raw.values(), "result_status", "resultstatus", "status", "ket_qua", "trang_thai");
            String semCode = validationService.getField(raw.values(), "semester_code", "semestercode", "hoc_ky");
            String rowJson = validationService.serializeRowData(raw.values());

            if (code == null) {
                errors.add(err(raw.rowNumber(), "course_code", "REQUIRED_FIELD", "Thiếu mã học phần.", rowJson));
                continue;
            }
            Course course = courseRepository.findByCourseCodeIgnoreCase(code).orElse(null);
            if (course == null) {
                errors.add(err(raw.rowNumber(), "course_code", "COURSE_NOT_FOUND",
                        "Không tìm thấy học phần '" + code + "' trong danh mục.", rowJson));
                continue;
            }

            BigDecimal grade = null;
            if (gradeStr != null) {
                try {
                    grade = new BigDecimal(gradeStr.replace(",", "."));
                    if (grade.compareTo(BigDecimal.ZERO) < 0 || grade.compareTo(new BigDecimal("10")) > 0) {
                        errors.add(err(raw.rowNumber(), "grade", "INVALID_GRADE",
                                "Điểm số phải nằm trong khoảng 0 đến 10.", rowJson));
                        continue;
                    }
                } catch (NumberFormatException ex) {
                    errors.add(err(raw.rowNumber(), "grade", "INVALID_FORMAT",
                            "Điểm số không hợp lệ: '" + gradeStr + "'.", rowJson));
                    continue;
                }
            }

            String resolvedStatus;
            if (statusStr == null) {
                if (grade == null) {
                    errors.add(err(raw.rowNumber(), "result_status", "REQUIRED_FIELD",
                            "Thiếu trạng thái đạt/không đạt hoặc điểm số.", rowJson));
                    continue;
                }
                resolvedStatus = grade.compareTo(new BigDecimal("4.0")) >= 0 ? "PASSED" : "FAILED";
            } else {
                String norm = statusStr.trim().toUpperCase(Locale.ROOT);
                if (norm.equals("PASSED") || norm.equals("PASS") || norm.equals("ĐẠT") || norm.equals("DAT")) {
                    resolvedStatus = "PASSED";
                } else if (norm.equals("FAILED") || norm.equals("FAIL") || norm.equals("KHÔNG ĐẠT") || norm.equals("KHONG DAT")) {
                    resolvedStatus = "FAILED";
                } else {
                    errors.add(err(raw.rowNumber(), "result_status", "INVALID_STATUS",
                            "Trạng thái kết quả không hợp lệ: '" + statusStr + "'.", rowJson));
                    continue;
                }
            }

            Semester rowSemester = semCode != null
                    ? semesterRepository.findBySemesterCodeIgnoreCase(semCode).orElse(null)
                    : (semesterId != null ? defaultSemester : null);

            StudentCourseRecord saved = upsertSingleRecord(user, course, rowSemester, imp, grade, resolvedStatus);
            savedRecords.add(toRecordResponse(saved));
        }

        imp.setTotalRows((long) rawRows.size());
        imp.setValidRows((long) savedRecords.size());
        imp.setErrorRows((long) errors.size());
        imp.setImportStatus(errors.isEmpty() ? "COMPLETED" : (savedRecords.isEmpty() ? "FAILED" : "COMPLETED_WITH_ERRORS"));
        imp.setProcessedAt(LocalDateTime.now());
        for (ImportRowErrorDto e : errors) {
            imp.getErrors().add(RegistrationImportError.builder()
                    .registrationImport(imp)
                    .rowNumber(e.getRowNumber())
                    .fieldName(e.getFieldName())
                    .errorCode(e.getErrorCode())
                    .errorMessage(e.getErrorMessage())
                    .rowData(e.getRowData())
                    .build());
        }
        importRepository.save(imp);

        return TranscriptImportResponse.builder()
                .importId(imp.getId())
                .fileName(imp.getFileName())
                .importStatus(imp.getImportStatus())
                .totalRows(imp.getTotalRows())
                .validRows(imp.getValidRows())
                .errorRows(imp.getErrorRows())
                .savedRecords(savedRecords)
                .errors(errors)
                .build();
    }

    private StudentCourseRecord upsertSingleRecord(
            User user,
            Course course,
            Semester semester,
            RegistrationImport sourceImport,
            BigDecimal grade,
            String resultStatus) {
        StudentCourseRecord record = recordRepository.findByUserIdAndCourseId(user.getId(), course.getId())
                .orElseGet(() -> StudentCourseRecord.builder()
                        .user(user)
                        .course(course)
                        .build());

        if (semester != null) {
            record.setSemester(semester);
        }
        if (sourceImport != null) {
            record.setSourceImport(sourceImport);
        }
        record.setGrade(grade);
        record.setResultStatus(resultStatus);
        record.setCompletedAt(LocalDateTime.now());
        return recordRepository.save(record);
    }

    private Course resolveCourse(Long courseId, String courseCode) {
        if (courseId != null) {
            return courseRepository.findById(courseId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy học phần.", HttpStatus.NOT_FOUND));
        }
        if (courseCode != null && !courseCode.isBlank()) {
            return courseRepository.findByCourseCodeIgnoreCase(courseCode.trim())
                    .orElseThrow(() -> new BusinessException(
                            "Không tìm thấy học phần mã '" + courseCode.trim() + "'.", HttpStatus.NOT_FOUND));
        }
        throw new BusinessException("Vui lòng cung cấp courseId hoặc courseCode.");
    }

    private String normalizeResultStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("Trạng thái kết quả không được để trống.");
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT);
        if (norm.equals("PASS") || norm.equals("ĐẠT") || norm.equals("DAT")) {
            return "PASSED";
        }
        if (norm.equals("FAIL") || norm.equals("KHÔNG ĐẠT") || norm.equals("KHONG DAT")) {
            return "FAILED";
        }
        if (!VALID_STATUSES.contains(norm)) {
            throw new BusinessException("Trạng thái kết quả chỉ chấp nhận PASSED hoặc FAILED.");
        }
        return norm;
    }

    private void validateGrade(BigDecimal grade) {
        if (grade != null && (grade.compareTo(BigDecimal.ZERO) < 0 || grade.compareTo(new BigDecimal("10")) > 0)) {
            throw new BusinessException("Điểm số phải nằm trong khoảng từ 0 đến 10.");
        }
    }

    private Path storeTranscriptFile(byte[] bytes, String ext) {
        try {
            Files.createDirectories(transcriptDir);
            Path dest = transcriptDir.resolve(UUID.randomUUID() + "." + ext).normalize();
            Files.write(dest, bytes);
            return dest;
        } catch (IOException e) {
            throw new BusinessException("Không thể lưu tệp bảng điểm.");
        }
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

    private AcademicRecordResponse toRecordResponse(StudentCourseRecord r) {
        return AcademicRecordResponse.builder()
                .recordId(r.getId())
                .courseId(r.getCourse().getId())
                .courseCode(r.getCourse().getCourseCode())
                .courseName(r.getCourse().getCourseName())
                .credits(r.getCourse().getCredits())
                .semesterId(r.getSemester() != null ? r.getSemester().getId() : null)
                .semesterCode(r.getSemester() != null ? r.getSemester().getSemesterCode() : null)
                .grade(r.getGrade())
                .resultStatus(r.getResultStatus())
                .completedAt(r.getCompletedAt())
                .build();
    }
}
