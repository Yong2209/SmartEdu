package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.AuthDtos.UserProfileResponse;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final CourseSectionRepository sectionRepository;
    private final CampusRepository campusRepository;
    private final RoomRepository roomRepository;
    private final CourseCatalogImportRepository catalogImportRepository;
    private final CourseCatalogImportErrorRepository catalogErrorRepository;
    private final AiCallLogRepository aiCallLogRepository;
    private final EmailLogRepository emailLogRepository;
    private final FileProcessingLogRepository fileLogRepository;
    private final ActivityLogRepository activityLogRepository;
    private final AuthService authService;

    @Value("${smartedu.storage.upload-dir:./uploads}")
    private String uploadDir;

    // US35 & US36: Import danh mục học phần & lớp học phần mở từ Excel (BR-ADM-01)
    @Transactional
    public Map<String, Object> importCourseCatalog(User admin, Long semesterId, MultipartFile file) {
        Semester semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> new BusinessException("SEMESTER_NOT_FOUND", "Không tìm thấy học kỳ."));

        if (file == null || file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "Vui lòng chọn tệp Excel danh mục học phần.");
        }

        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "catalog.xlsx");
        try {
            Path dir = Paths.get(uploadDir, "catalogs");
            Files.createDirectories(dir);
            Path savedPath = dir.resolve(UUID.randomUUID() + "_" + fileName);
            Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);

            CourseCatalogImport imp = CourseCatalogImport.builder()
                    .adminUser(admin)
                    .semester(semester)
                    .fileName(fileName)
                    .storagePath(savedPath.toString().replace('\\', '/'))
                    .importStatus("PROCESSING")
                    .build();
            imp = catalogImportRepository.save(imp);

            long total = 0, success = 0, errors = 0;
                List<Map<String, Object>> errorDetails = new ArrayList<>();
            Campus defaultCampus = campusRepository.findByCampusCode("CS1")
                    .orElseGet(() -> campusRepository.save(Campus.builder().campusCode("CS1").campusName("Cơ sở Chính").build()));

            try (InputStream is = Files.newInputStream(savedPath); Workbook wb = new XSSFWorkbook(is)) {
                Sheet sheet = wb.getSheetAt(0);
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    total++;
                    long rowNum = i + 1L;
                    try {
                        String courseCode = getCellString(row, 0).toUpperCase();
                        String courseName = getCellString(row, 1);
                        double creditsVal = Double.parseDouble(getCellString(row, 2));
                        String sectionCode = getCellString(row, 3).toUpperCase();
                        short dayOfWeek = Short.parseShort(getCellString(row, 5));
                        short startPeriod = Short.parseShort(getCellString(row, 6));
                        short endPeriod = Short.parseShort(getCellString(row, 7));
                        String roomCode = getCellString(row, 8);
                        int maxCap = Integer.parseInt(getCellString(row, 9).isBlank() ? "60" : getCellString(row, 9));
                        if (courseCode.isBlank() || courseName.isBlank() || sectionCode.isBlank()
                                || dayOfWeek < 1 || dayOfWeek > 7 || startPeriod < 1 || endPeriod > 15 || startPeriod > endPeriod) {
                            throw new IllegalArgumentException("Dữ liệu mã môn, mã lớp hoặc thứ/tiết không hợp lệ.");
                        }

                        Course course = courseRepository.findByCourseCodeIgnoreCase(courseCode)
                                .orElseGet(() -> courseRepository.save(Course.builder()
                                        .courseCode(courseCode)
                                        .courseName(courseName)
                                        .credits(BigDecimal.valueOf(creditsVal))
                                        .isActive(true)
                                        .build()));

                        Room room = roomRepository.findByCampusIdAndRoomCode(defaultCampus.getId(), roomCode.isBlank() ? "A101" : roomCode)
                                .orElseGet(() -> roomRepository.save(Room.builder()
                                        .campus(defaultCampus)
                                        .roomCode(roomCode.isBlank() ? "A101" : roomCode)
                                        .capacity(maxCap)
                                        .build()));

                        CourseSection section = sectionRepository.findBySemesterIdAndSectionCode(semester.getId(), sectionCode)
                                .orElse(CourseSection.builder()
                                        .semester(semester)
                                        .course(course)
                                        .sectionCode(sectionCode)
                                        .build());

                        section.setMaxCapacity(maxCap);
                        section.setSectionStatus("OPEN");
                        section.getMeetings().clear();
                        section.getMeetings().add(SectionMeeting.builder()
                                .courseSection(section)
                                .room(room)
                                .weekday(dayOfWeek)
                                .startPeriod(startPeriod)
                                .endPeriod(endPeriod)
                                .build());

                        sectionRepository.save(section);
                        success++;
                    } catch (Exception rowEx) {
                        errors++;
                        catalogErrorRepository.save(CourseCatalogImportError.builder()
                                .courseCatalogImport(imp)
                                .rowNumber(rowNum)
                                .errorCode("ROW_INVALID")
                                .errorMessage(rowEx.getMessage())
                                .build());
                        errorDetails.add(Map.of("row", rowNum, "error", rowEx.getMessage()));
                    }
                }
            }

            imp.setTotalRows(total);
            imp.setSuccessRows(success);
            imp.setErrorRows(errors);
            imp.setImportStatus("COMPLETED");
            imp.setCompletedAt(LocalDateTime.now());
            catalogImportRepository.save(imp);

            logActivity(admin, null, "IMPORT_COURSE_CATALOG", "CourseCatalogImport", imp.getId(),
                    "Import " + fileName + ": thành công " + success + "/" + total + " dòng.");

            return Map.of(
                    "importId", imp.getId(),
                    "totalRows", total,
                    "successRows", success,
                    "errorRows", errors,
                    "errors", errorDetails
            );
        } catch (Exception e) {
            throw new BusinessException("CATALOG_IMPORT_FAILED", "Lỗi import danh mục học phần: " + e.getMessage());
        }
    }

    private String getCellString(Row row, int idx) {
        Cell cell = row.getCell(idx);
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    // US37: Quản lý danh sách người dùng (BR-ADM-02)
    public Map<String, Object> listUsers(String role, String status, String keyword, int page, int size) {
        Page<User> userPage = userRepository.searchUsers(
                (role == null || role.isBlank()) ? null : role,
                (status == null || status.isBlank()) ? null : status,
                (keyword == null || keyword.isBlank()) ? null : keyword,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return Map.of(
                "content", userPage.getContent().stream().map(authService::toProfile).toList(),
                "totalElements", userPage.getTotalElements(),
                "totalPages", userPage.getTotalPages(),
                "page", page
        );
    }

    // US38: Khóa/Mở khóa và phân quyền tài khoản (BR-ADM-03: Không tự khóa chính mình)
    @Transactional
    public UserProfileResponse updateUserStatus(User admin, Long targetUserId, String newStatus, String reason) {
        if (admin.getId().equals(targetUserId)) {
            throw new BusinessException("BR-ADM-03", "Quản trị viên không thể tự khóa hoặc thay đổi trạng thái chính tài khoản của mình.");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy người dùng."));

        String status = newStatus.toUpperCase();
        if (!List.of("ACTIVE", "LOCKED").contains(status)) {
            throw new BusinessException("INVALID_STATUS", "Trạng thái phải là ACTIVE hoặc LOCKED.");
        }

        target.setAccountStatus(status);
        if ("LOCKED".equals(status)) {
            target.setLockedAt(LocalDateTime.now());
        } else {
            target.setLockedAt(null);
        }
        userRepository.save(target);

        logActivity(admin, target, "UPDATE_USER_STATUS_" + status, "User", target.getId(),
                "Thay đổi trạng thái tài khoản sang " + status + ". Lý do: " + Objects.requireNonNullElse(reason, "N/A"));
        return authService.toProfile(target);
    }

    @Transactional
    public UserProfileResponse updateUserRole(User admin, Long targetUserId, String roleCode) {
        if (admin.getId().equals(targetUserId)) {
            throw new BusinessException("BR-ADM-03", "Quản trị viên không thể tự hạ quyền chính mình.");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy người dùng."));

        Role role = roleRepository.findByCode(roleCode.toUpperCase())
                .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "Không tìm thấy vai trò " + roleCode));

        target.setRole(role);
        userRepository.save(target);

        logActivity(admin, target, "UPDATE_USER_ROLE", "User", target.getId(), "Gán vai trò mới: " + role.getCode());
        return authService.toProfile(target);
    }

    // US39: Theo dõi nhật ký vận hành hệ thống (AI Logs, Email Logs, File Logs, Activity Logs)
    public Map<String, Object> getSystemMonitoringLogs() {
        return Map.of(
                "summary", Map.of(
                        "totalUsers", userRepository.count(),
                        "totalCourses", courseRepository.count(),
                        "totalSections", sectionRepository.count()
                ),
                "aiCallLogs", aiCallLogRepository.findTop100ByOrderByCalledAtDesc().stream().map(l -> Map.of(
                        "id", l.getId(),
                        "operationType", l.getOperationType(),
                        "provider", Objects.requireNonNullElse(l.getProvider(), ""),
                        "modelName", Objects.requireNonNullElse(l.getModelName(), ""),
                        "responseTimeMs", Objects.requireNonNullElse(l.getResponseTimeMs(), 0L),
                        "totalTokens", Objects.requireNonNullElse(l.getTotalTokens(), 0L),
                        "callStatus", l.getCallStatus(),
                        "calledAt", l.getCalledAt()
                )).toList(),
                "emailLogs", emailLogRepository.findTop100ByOrderByCreatedAtDesc().stream().map(e -> Map.of(
                        "id", e.getId(),
                        "recipientEmail", e.getRecipientEmail(),
                        "emailType", e.getEmailType(),
                        "sendStatus", e.getSendStatus(),
                        "sentAt", Objects.requireNonNullElse(e.getSentAt(), e.getCreatedAt())
                )).toList(),
                "fileLogs", fileLogRepository.findTop100ByOrderByProcessedAtDesc().stream().map(f -> Map.of(
                        "id", f.getId(),
                        "processingType", f.getProcessingType(),
                        "processingStatus", f.getProcessingStatus(),
                        "durationMs", Objects.requireNonNullElse(f.getDurationMs(), 0L),
                        "processedAt", f.getProcessedAt()
                )).toList(),
                "activityLogs", activityLogRepository.findTop100ByOrderByCreatedAtDesc().stream().map(a -> Map.of(
                        "id", a.getId(),
                        "actionCode", a.getActionCode(),
                        "entityType", Objects.requireNonNullElse(a.getEntityType(), ""),
                        "detail", Objects.requireNonNullElse(a.getDetail(), ""),
                        "createdAt", Objects.requireNonNullElse(a.getCreatedAt(), LocalDateTime.now())
                )).toList()
        );
    }

    public void logActivity(User actor, User target, String actionCode, String entityType, Long entityId, String detail) {
        activityLogRepository.save(ActivityLog.builder()
                .actorUser(actor)
                .targetUser(target)
                .actionCode(actionCode)
                .entityType(entityType)
                .entityId(entityId)
                .detail(detail)
                .build());
    }
}