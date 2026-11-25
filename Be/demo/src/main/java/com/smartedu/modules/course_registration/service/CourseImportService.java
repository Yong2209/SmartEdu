package com.smartedu.modules.course_registration.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ConfirmImportRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CourseImportRow;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CourseImportRowInput;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ImportRowErrorDto;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ImportTemplateGuideResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ImportValidationResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.RegistrationPlanResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.TemplateColumn;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ValidateCourseListRequest;
import com.smartedu.modules.course_registration.entity.RegistrationImport;
import com.smartedu.modules.course_registration.entity.RegistrationImportError;
import com.smartedu.modules.course_registration.entity.RegistrationPlan;
import com.smartedu.modules.course_registration.entity.RegistrationPlanCourse;
import com.smartedu.modules.course_registration.repository.RegistrationImportRepository;
import com.smartedu.modules.course_registration.repository.RegistrationPlanRepository;
import com.smartedu.modules.course_registration.service.CourseValidationService.RawParsedRow;
import com.smartedu.modules.course_registration.service.CourseValidationService.ValidationResult;
import com.smartedu.modules.user_management.entity.User;

@Service
public class CourseImportService {

    private static final String SAMPLE_CSV =
            "course_code,course_name,credits,section_code\n"
            + "DEMO-CS201,Cơ sở dữ liệu,3.0,DEMO-CS201-01\n"
            + "DEMO-CS202,Lập trình hướng đối tượng,3.0,DEMO-CS202-01\n";

    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final RegistrationImportRepository importRepository;
    private final RegistrationPlanRepository planRepository;
    private final CourseValidationService validationService;
    private final RegistrationPlanService planService;
    private final Path importDir;

    public CourseImportService(
            SemesterRepository semesterRepository,
            CourseRepository courseRepository,
            RegistrationImportRepository importRepository,
            RegistrationPlanRepository planRepository,
            CourseValidationService validationService,
            RegistrationPlanService planService,
            @Value("${smartedu.storage.upload-dir:uploads}") String uploadDir) {
        this.semesterRepository = semesterRepository;
        this.courseRepository = courseRepository;
        this.importRepository = importRepository;
        this.planRepository = planRepository;
        this.validationService = validationService;
        this.planService = planService;
        this.importDir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("imports");
    }

    public ImportTemplateGuideResponse getTemplateGuide() {
        return ImportTemplateGuideResponse.builder()
                .supportedFormats(List.of("CSV", "XLSX"))
                .maxFileSizeBytes(CourseValidationService.MAX_IMPORT_FILE_SIZE)
                .columns(List.of(
                        TemplateColumn.builder()
                                .fieldName("course_code")
                                .headerName("course_code")
                                .required(true)
                                .description("Mã học phần theo danh mục hệ thống")
                                .example("DEMO-CS201")
                                .build(),
                        TemplateColumn.builder()
                                .fieldName("course_name")
                                .headerName("course_name")
                                .required(true)
                                .description("Tên học phần")
                                .example("Cơ sở dữ liệu")
                                .build(),
                        TemplateColumn.builder()
                                .fieldName("credits")
                                .headerName("credits")
                                .required(true)
                                .description("Số tín chỉ (> 0)")
                                .example("3.0")
                                .build(),
                        TemplateColumn.builder()
                                .fieldName("section_code")
                                .headerName("section_code")
                                .required(false)
                                .description("Mã lớp học phần dự kiến (nếu có)")
                                .example("DEMO-CS201-01")
                                .build()))
                .sampleCsv(SAMPLE_CSV)
                .build();
    }

    public byte[] downloadSampleTemplate(String format) {
        if ("xlsx".equalsIgnoreCase(format)) {
            return validationService.buildSampleCourseXlsx();
        }
        return SAMPLE_CSV.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public ImportValidationResponse uploadCourseFile(User user, Long semesterId, MultipartFile file) {
        Semester semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy học kỳ.", HttpStatus.NOT_FOUND));

        String format = validationService.validateAndDetectFormat(file);
        String originalName = file.getOriginalFilename() != null
                ? Paths.get(file.getOriginalFilename()).getFileName().toString()
                : "import." + format.toLowerCase();

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Không thể đọc nội dung tệp tải lên.");
        }

        List<RawParsedRow> rawRows = validationService.parseFileRows(new ByteArrayInputStream(bytes), format);
        List<ImportRowErrorDto> parseErrors = new ArrayList<>();
        List<CourseImportRowInput> inputs = validationService.toCourseInputs(rawRows, parseErrors);

        ValidationResult result = validationService.validateCourseRows(semesterId, inputs);
        List<ImportRowErrorDto> mergedErrors = mergeErrors(parseErrors, result.errors());

        Path destination = storeFile(bytes, format.toLowerCase());
        boolean allValid = mergedErrors.isEmpty() && !result.validItems().isEmpty();

        RegistrationImport imp = RegistrationImport.builder()
                .user(user)
                .semester(semester)
                .importType("STUDENT_REGISTRATION")
                .fileName(originalName)
                .storagePath(destination.toString())
                .fileFormat(format)
                .importStatus(allValid ? "VALIDATED" : "VALIDATION_FAILED")
                .totalRows((long) inputs.size())
                .validRows((long) result.validItems().size())
                .errorRows((long) mergedErrors.size())
                .processedAt(LocalDateTime.now())
                .build();

        attachErrors(imp, mergedErrors);
        imp = importRepository.save(imp);

        return toImportResponse(imp, result.validItems(), mergedErrors);
    }

    @Transactional
    public ImportValidationResponse validateCourseList(User user, ValidateCourseListRequest req) {
        Semester semester = semesterRepository.findById(req.getSemesterId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy học kỳ.", HttpStatus.NOT_FOUND));

        ValidationResult result = validationService.validateCourseRows(semester.getId(), req.getItems());
        boolean allValid = result.errors().isEmpty() && !result.validItems().isEmpty();

        RegistrationImport imp;
        if (req.getImportId() != null) {
            imp = findOwnedImport(user, req.getImportId());
            if (!imp.getSemester().getId().equals(semester.getId())) {
                imp.setSemester(semester);
            }
        } else {
            Path path = storeFile(serializeCsv(req.getItems()).getBytes(StandardCharsets.UTF_8), "csv");
            imp = RegistrationImport.builder()
                    .user(user)
                    .semester(semester)
                    .importType("STUDENT_REGISTRATION")
                    .fileName("manual-course-list.csv")
                    .storagePath(path.toString())
                    .fileFormat("CSV")
                    .build();
        }

        if (allValid && imp.getStoragePath() != null && "CSV".equalsIgnoreCase(imp.getFileFormat())) {
            try {
                Files.writeString(Paths.get(imp.getStoragePath()), serializeCsv(req.getItems()), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }

        imp.setImportStatus(allValid ? "VALIDATED" : "VALIDATION_FAILED");
        imp.setTotalRows((long) req.getItems().size());
        imp.setValidRows((long) result.validItems().size());
        imp.setErrorRows((long) result.errors().size());
        imp.setProcessedAt(LocalDateTime.now());
        imp.getErrors().clear();
        importRepository.saveAndFlush(imp);
        attachErrors(imp, result.errors());
        imp = importRepository.save(imp);

        return toImportResponse(imp, result.validItems(), result.errors());
    }

    @Transactional
    public RegistrationPlanResponse confirmImport(User user, Long importId, ConfirmImportRequest req) {
        RegistrationImport imp = findOwnedImport(user, importId);
        if (!"STUDENT_REGISTRATION".equalsIgnoreCase(imp.getImportType())) {
            throw new BusinessException("Bản ghi nhập này không phải danh sách học phần dự kiến.");
        }

        List<CourseImportRowInput> inputs;
        if (req != null && req.getItems() != null && !req.getItems().isEmpty()) {
            inputs = req.getItems();
        } else {
            inputs = loadInputsFromStoredFile(imp);
        }

        ValidationResult result = validationService.validateCourseRows(imp.getSemester().getId(), inputs);
        if (!result.errors().isEmpty() || result.validItems().isEmpty()) {
            imp.setImportStatus("VALIDATION_FAILED");
            imp.setTotalRows((long) inputs.size());
            imp.setValidRows((long) result.validItems().size());
            imp.setErrorRows((long) result.errors().size());
            imp.setProcessedAt(LocalDateTime.now());
            imp.getErrors().clear();
            importRepository.saveAndFlush(imp);
            attachErrors(imp, result.errors());
            importRepository.save(imp);
            throw new BusinessException("Danh sách học phần còn lỗi hợp lệ (" + result.errors().size()
                    + " lỗi). Vui lòng kiểm tra và chỉnh sửa trước khi xác nhận.");
        }

        imp.setImportStatus("CONFIRMED");
        imp.setTotalRows((long) inputs.size());
        imp.setValidRows((long) result.validItems().size());
        imp.setErrorRows(0L);
        imp.setProcessedAt(LocalDateTime.now());
        imp.getErrors().clear();
        importRepository.save(imp);

        String planName = (req != null && req.getPlanName() != null && !req.getPlanName().isBlank())
                ? req.getPlanName().trim()
                : "Kế hoạch từ tệp " + imp.getFileName();

        RegistrationPlan plan = RegistrationPlan.builder()
                .user(user)
                .semester(imp.getSemester())
                .sourceImport(imp)
                .planName(planName)
                .planStatus("DRAFT")
                .build();

        Set<Long> addedCourseIds = new LinkedHashSet<>();
        for (CourseImportRow item : result.validItems()) {
            if (addedCourseIds.add(item.getCourseId())) {
                Course course = courseRepository.getReferenceById(item.getCourseId());
                plan.getPlanCourses().add(RegistrationPlanCourse.builder()
                        .registrationPlan(plan)
                        .course(course)
                        .selectionSource("IMPORT")
                        .build());
            }
        }
        plan = planRepository.save(plan);
        return planService.toPlanResponse(plan);
    }

    @Transactional(readOnly = true)
    public ImportValidationResponse getImportDetail(User user, Long importId) {
        RegistrationImport imp = findOwnedImport(user, importId);
        List<ImportRowErrorDto> errorDtos = imp.getErrors().stream()
                .map(e -> ImportRowErrorDto.builder()
                        .rowNumber(e.getRowNumber())
                        .fieldName(e.getFieldName())
                        .errorCode(e.getErrorCode())
                        .errorMessage(e.getErrorMessage())
                        .rowData(e.getRowData())
                        .build())
                .toList();

        List<CourseImportRow> validItems = List.of();
        if ("VALIDATED".equalsIgnoreCase(imp.getImportStatus())
                || "CONFIRMED".equalsIgnoreCase(imp.getImportStatus())) {
            try {
                List<CourseImportRowInput> inputs = loadInputsFromStoredFile(imp);
                validItems = validationService.validateCourseRows(imp.getSemester().getId(), inputs).validItems();
            } catch (Exception ignored) {
            }
        }
        return toImportResponse(imp, validItems, errorDtos);
    }

    @Transactional(readOnly = true)
    public List<ImportValidationResponse> listMyImports(User user) {
        return importRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(imp -> "STUDENT_REGISTRATION".equalsIgnoreCase(imp.getImportType()))
                .map(imp -> toImportResponse(imp, List.of(), List.of()))
                .toList();
    }

    private RegistrationImport findOwnedImport(User user, Long importId) {
        RegistrationImport imp = importRepository.findById(importId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy bản ghi nhập học phần.", HttpStatus.NOT_FOUND));
        if (!imp.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền truy cập danh sách học phần này.", HttpStatus.FORBIDDEN);
        }
        return imp;
    }

    private List<CourseImportRowInput> loadInputsFromStoredFile(RegistrationImport imp) {
        Path path = Paths.get(imp.getStoragePath());
        if (!Files.isRegularFile(path)) {
            throw new BusinessException("Không tìm thấy tệp dữ liệu đã tải lên để xác nhận.");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            List<RawParsedRow> raw = validationService.parseFileRows(new ByteArrayInputStream(bytes), imp.getFileFormat());
            List<ImportRowErrorDto> parseErrors = new ArrayList<>();
            List<CourseImportRowInput> inputs = validationService.toCourseInputs(raw, parseErrors);
            if (!parseErrors.isEmpty()) {
                throw new BusinessException("Tệp dữ liệu còn lỗi định dạng, vui lòng kiểm tra lại.");
            }
            return inputs;
        } catch (IOException e) {
            throw new BusinessException("Không thể đọc tệp đã lưu.");
        }
    }

    private Path storeFile(byte[] bytes, String ext) {
        try {
            Files.createDirectories(importDir);
            String filename = UUID.randomUUID() + "." + ext;
            Path dest = importDir.resolve(filename).normalize();
            Files.write(dest, bytes);
            return dest;
        } catch (IOException e) {
            throw new BusinessException("Không thể lưu tệp tải lên.");
        }
    }

    private String serializeCsv(List<CourseImportRowInput> items) {
        StringBuilder sb = new StringBuilder("course_code,course_name,credits,section_code\n");
        for (CourseImportRowInput item : items) {
            sb.append(csvCell(item.getCourseCode())).append(',')
                    .append(csvCell(item.getCourseName())).append(',')
                    .append(item.getCredits() != null ? item.getCredits().toPlainString() : "").append(',')
                    .append(csvCell(item.getSectionCode())).append('\n');
        }
        return sb.toString();
    }

    private String csvCell(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private List<ImportRowErrorDto> mergeErrors(
            List<ImportRowErrorDto> parseErrors,
            List<ImportRowErrorDto> validationErrors) {
        List<ImportRowErrorDto> merged = new ArrayList<>(parseErrors);
        Set<String> parseKeys = new LinkedHashSet<>();
        for (ImportRowErrorDto pe : parseErrors) {
            parseKeys.add(pe.getRowNumber() + ":" + pe.getFieldName());
        }
        for (ImportRowErrorDto ve : validationErrors) {
            if (!parseKeys.contains(ve.getRowNumber() + ":" + ve.getFieldName())) {
                merged.add(ve);
            }
        }
        return merged;
    }

    private void attachErrors(RegistrationImport imp, List<ImportRowErrorDto> errors) {
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
    }

    private ImportValidationResponse toImportResponse(
            RegistrationImport imp,
            List<CourseImportRow> validItems,
            List<ImportRowErrorDto> errors) {
        return ImportValidationResponse.builder()
                .importId(imp.getId())
                .semesterId(imp.getSemester().getId())
                .fileName(imp.getFileName())
                .fileFormat(imp.getFileFormat())
                .importStatus(imp.getImportStatus())
                .valid(imp.getErrorRows() == 0 && imp.getValidRows() > 0)
                .totalRows(imp.getTotalRows())
                .validRows(imp.getValidRows())
                .errorRows(imp.getErrorRows())
                .processedAt(imp.getProcessedAt())
                .validItems(validItems)
                .errors(errors)
                .build();
    }
}
