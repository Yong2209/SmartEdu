package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.TimetableDtos.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimetableService {

    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final CourseSectionRepository sectionRepository;
    private final CoursePrerequisiteRepository prerequisiteRepository;
    private final StudentCourseRecordRepository studentRecordRepository;
    private final RegistrationImportRepository regImportRepository;
    private final RegistrationImportErrorRepository regImportErrorRepository;
    private final RegistrationPlanRepository planRepository;
    private final RegistrationPreferenceRepository preferenceRepository;
    private final ScheduleGenerationRepository generationRepository;
    private final ScheduleOptionRepository optionRepository;
    private final SavedScheduleRepository savedScheduleRepository;
    private final ScheduleExportRepository exportRepository;
    private final ObjectMapper objectMapper;

    @Value("${smartedu.storage.upload-dir:./uploads}")
    private String uploadDir;

    public List<Semester> getAllSemesters() {
        return semesterRepository.findAll();
    }

    public List<Course> getActiveCourses() {
        return courseRepository.findByIsActiveTrueOrderByCourseCodeAsc();
    }

    public List<CourseSectionDto> getOpenSectionsBySemester(Long semesterId, List<Long> courseIds) {
        List<CourseSection> sections = (courseIds == null || courseIds.isEmpty())
                ? sectionRepository.findBySemesterIdAndSectionStatus(semesterId, "OPEN")
                : sectionRepository.findBySemesterIdAndCourseIdInAndSectionStatus(semesterId, courseIds, "OPEN");
        return sections.stream().map(this::toSectionDto).toList();
    }

    // US20 & US21: Nhập danh sách học phần dự kiến đăng ký từ Excel/CSV (BR-TT-01)
    @Transactional
    public Map<String, Object> importRegistrationCourses(User user, Long semesterId, MultipartFile file) {
        Semester semester = semesterRepository.findById(semesterId)
                .orElseThrow(() -> new BusinessException("SEMESTER_NOT_FOUND", "Không tìm thấy học kỳ."));

        if (file == null || file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "Vui lòng chọn tệp danh sách đăng ký.");
        }

        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "registration.xlsx");
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!List.of("xlsx", "xls", "csv").contains(ext)) {
            throw new BusinessException("BR-TT-01", "Hệ thống chỉ hỗ trợ tệp định dạng .xlsx, .xls hoặc .csv.");
        }

        try {
            Path dir = Paths.get(uploadDir, "registrations", String.valueOf(user.getId()));
            Files.createDirectories(dir);
            Path savedPath = dir.resolve(UUID.randomUUID() + "_" + fileName);
            Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);

            RegistrationImport regImport = RegistrationImport.builder()
                    .user(user)
                    .semester(semester)
                    .fileName(fileName)
                    .storagePath(savedPath.toString().replace('\\', '/'))
                    .fileFormat(ext.toUpperCase())
                    .importStatus("PROCESSING")
                    .build();
            regImport = regImportRepository.save(regImport);

            List<String> rawCourseCodes = new ArrayList<>();
            if ("csv".equals(ext)) {
                List<String> lines = Files.readAllLines(savedPath, StandardCharsets.UTF_8);
                for (int i = 1; i < lines.size(); i++) {
                    String[] cols = lines.get(i).split(",");
                    if (cols.length > 0 && !cols[0].isBlank()) rawCourseCodes.add(cols[0].trim());
                }
            } else {
                try (InputStream is = Files.newInputStream(savedPath); Workbook wb = new XSSFWorkbook(is)) {
                    Sheet sheet = wb.getSheetAt(0);
                    for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                        Row row = sheet.getRow(i);
                        if (row != null && row.getCell(0) != null) {
                            String code = row.getCell(0).toString().trim();
                            if (!code.isBlank()) rawCourseCodes.add(code);
                        }
                    }
                }
            }

            long validCount = 0, errorCount = 0;
            List<Course> matchedCourses = new ArrayList<>();
            List<Map<String, Object>> rowErrors = new ArrayList<>();
            Set<String> seenCodes = new HashSet<>();

            for (int i = 0; i < rawCourseCodes.size(); i++) {
                long rowNum = i + 2L;
                String code = rawCourseCodes.get(i).toUpperCase();
                if (seenCodes.contains(code)) {
                    errorCount++;
                    regImportErrorRepository.save(RegistrationImportError.builder()
                            .registrationImport(regImport)
                            .rowNumber(rowNum)
                            .fieldName("course_code")
                            .errorCode("DUPLICATE_COURSE")
                            .errorMessage("Trùng mã học phần trong tệp: " + code)
                            .rowData(code)
                            .build());
                    rowErrors.add(Map.of("row", rowNum, "code", code, "reason", "Trùng mã học phần trong tệp"));
                    continue;
                }
                seenCodes.add(code);

                Optional<Course> cOpt = courseRepository.findByCourseCodeIgnoreCase(code);
                if (cOpt.isEmpty() || !Boolean.TRUE.equals(cOpt.get().getIsActive())) {
                    errorCount++;
                    regImportErrorRepository.save(RegistrationImportError.builder()
                            .registrationImport(regImport)
                            .rowNumber(rowNum)
                            .fieldName("course_code")
                            .errorCode("COURSE_NOT_FOUND")
                            .errorMessage("Mã học phần không tồn tại trong danh mục mở lớp: " + code)
                            .rowData(code)
                            .build());
                    rowErrors.add(Map.of("row", rowNum, "code", code, "reason", "Mã học phần không tồn tại"));
                } else {
                    validCount++;
                    matchedCourses.add(cOpt.get());
                }
            }

            regImport.setTotalRows((long) rawCourseCodes.size());
            regImport.setValidRows(validCount);
            regImport.setErrorRows(errorCount);
            regImport.setImportStatus("COMPLETED");
            regImportRepository.save(regImport);

            RegistrationPlan plan = null;
            if (!matchedCourses.isEmpty()) {
                plan = createPlanInternal(user, semester, regImport,
                        "Kế hoạch Import " + semester.getSemesterCode(),
                        matchedCourses.stream().map(Course::getId).toList(),
                        "EXCEL_IMPORT");
            }

            return Map.of(
                    "importId", regImport.getId(),
                    "totalRows", rawCourseCodes.size(),
                    "validRows", validCount,
                    "errorRows", errorCount,
                    "errors", rowErrors,
                    "planId", plan != null ? plan.getId() : 0L,
                    "matchedCourses", matchedCourses
            );
        } catch (Exception e) {
            throw new BusinessException("IMPORT_FAILED", "Lỗi xử lý tệp danh sách đăng ký: " + e.getMessage());
        }
    }

    // US22: Chọn học phần dự kiến đăng ký thủ công
    @Transactional
    public Map<String, Object> createRegistrationPlan(User user, CreatePlanRequest req) {
        Semester semester = semesterRepository.findById(req.getSemesterId())
                .orElseThrow(() -> new BusinessException("SEMESTER_NOT_FOUND", "Không tìm thấy học kỳ."));

        String planName = (req.getPlanName() != null && !req.getPlanName().isBlank())
                ? req.getPlanName().trim()
                : "Kế hoạch đăng ký " + semester.getSemesterCode();

        RegistrationPlan plan = createPlanInternal(user, semester, null, planName, req.getCourseIds(), "MANUAL");
        List<String> prereqWarnings = checkPrerequisitesForCourses(user.getId(), req.getCourseIds());

        return Map.of(
                "planId", plan.getId(),
                "planName", plan.getPlanName(),
                "semesterCode", semester.getSemesterCode(),
                "courseCount", plan.getPlanCourses().size(),
                "prerequisiteWarnings", prereqWarnings
        );
    }

    private RegistrationPlan createPlanInternal(User user, Semester semester, RegistrationImport sourceImport,
                                                String planName, List<Long> courseIds, String sourceType) {
        RegistrationPlan plan = RegistrationPlan.builder()
                .user(user)
                .semester(semester)
                .sourceImport(sourceImport)
                .planName(planName)
                .planStatus("READY")
                .build();
        plan = planRepository.save(plan);

        Set<Long> distinctIds = new LinkedHashSet<>(courseIds);
        for (Long cid : distinctIds) {
            Course c = courseRepository.findById(cid)
                    .orElseThrow(() -> new BusinessException("COURSE_NOT_FOUND", "Không tìm thấy học phần ID=" + cid));
            plan.getPlanCourses().add(RegistrationPlanCourse.builder()
                    .registrationPlan(plan)
                    .course(c)
                    .selectionSource(sourceType)
                    .build());
        }
        plan = planRepository.save(plan);

        RegistrationPreference pref = RegistrationPreference.builder()
                .registrationPlan(plan)
                .minCredits(new BigDecimal("12.0"))
                .maxCredits(new BigDecimal("24.0"))
                .preferredShift("ANY")
                .build();
        preferenceRepository.save(pref);
        return plan;
    }

    // US23: Cấu hình tiêu chí ưu tiên xếp lịch (BR-TT-03)
    @Transactional
    public RegistrationPreference savePreferences(User user, Long planId, PreferenceRequest req) {
        RegistrationPlan plan = getOwnedPlan(user, planId);
        if (req.getMinCredits().compareTo(req.getMaxCredits()) > 0) {
            throw new BusinessException("BR-TT-03", "Số tín chỉ tối thiểu không được lớn hơn số tín chỉ tối đa.");
        }

        RegistrationPreference pref = preferenceRepository.findByRegistrationPlanId(planId)
                .orElse(RegistrationPreference.builder().registrationPlan(plan).build());

        pref.setMinCredits(req.getMinCredits());
        pref.setMaxCredits(req.getMaxCredits());
        pref.setPreferredShift(req.getPreferredShift() != null ? req.getPreferredShift() : "ANY");
        pref.setAvoidSaturday(Boolean.TRUE.equals(req.getAvoidSaturday()));
        pref.setCompactDays(Boolean.TRUE.equals(req.getCompactDays()));
        pref.setMinimizeGaps(Boolean.TRUE.equals(req.getMinimizeGaps()));
        pref.setMaxGapPeriods(req.getMaxGapPeriods());

        try {
            if (req.getPreferredFreeDays() != null) {
                pref.setPreferredFreeDays(objectMapper.writeValueAsString(req.getPreferredFreeDays()));
            }
        } catch (Exception ignored) {}

        return preferenceRepository.save(pref);
    }

    // US24, US25, US26, US27: Sinh phương án TKB tối ưu bằng Backtracking + Chấm điểm đa tiêu chí
    @Transactional
    public GenerationResultResponse generateOptimalSchedules(User user, Long planId) {
        RegistrationPlan plan = getOwnedPlan(user, planId);
        RegistrationPreference pref = preferenceRepository.findByRegistrationPlanId(planId)
                .orElse(RegistrationPreference.builder()
                        .registrationPlan(plan)
                        .minCredits(new BigDecimal("0.0"))
                        .maxCredits(new BigDecimal("30.0"))
                        .preferredShift("ANY")
                        .build());

        List<Course> selectedCourses = plan.getPlanCourses().stream()
                .map(RegistrationPlanCourse::getCourse)
                .toList();

        if (selectedCourses.isEmpty()) {
            throw new BusinessException("EMPTY_PLAN", "Kế hoạch chưa có học phần nào.");
        }

        List<List<CourseSection>> sectionsPerCourse = new ArrayList<>();
        for (Course c : selectedCourses) {
            List<CourseSection> openSections = sectionRepository.findBySemesterIdAndCourseId(plan.getSemester().getId(), c.getId())
                    .stream()
                    .filter(s -> "OPEN".equals(s.getSectionStatus()))
                    .toList();
            if (openSections.isEmpty()) {
                throw new BusinessException("NO_OPEN_SECTION",
                        "Học phần " + c.getCourseCode() + " (" + c.getCourseName() + ") hiện không còn lớp mở.");
            }
            sectionsPerCourse.add(openSections);
        }

        int nextGenNo = generationRepository.findByRegistrationPlanIdOrderByGenerationNoDesc(planId).size() + 1;
        ScheduleGeneration generation = ScheduleGeneration.builder()
                .registrationPlan(plan)
                .generationNo(nextGenNo)
                .algorithmVersion("smartedu-backtrack-2.0")
                .generationStatus("RUNNING")
                .startedAt(LocalDateTime.now())
                .build();
        generation = generationRepository.save(generation);

        List<List<CourseSection>> feasibleCombos = new ArrayList<>();
        long[] candidateCounter = {0L};
        backtrackSchedules(sectionsPerCourse, 0, new ArrayList<>(), feasibleCombos, candidateCounter, 5000);

        List<String> prereqWarnings = checkPrerequisitesForCourses(user.getId(),
                selectedCourses.stream().map(Course::getId).toList());

        List<ScoredCandidate> scoredList = new ArrayList<>();
        for (List<CourseSection> combo : feasibleCombos) {
            scoredList.add(evaluateCandidate(combo, pref, prereqWarnings));
        }

        scoredList.sort((a, b) -> b.totalScore.compareTo(a.totalScore));
        List<ScoredCandidate> topOptions = scoredList.stream().limit(10).toList();

        generation.setTotalCandidates(candidateCounter[0]);
        generation.setFeasibleCandidates((long) feasibleCombos.size());
        generation.setCompletedAt(LocalDateTime.now());

        if (topOptions.isEmpty()) {
            generation.setGenerationStatus("NO_SOLUTION");
            generation.setErrorMessage("Các học phần đã chọn bị trùng lịch ở tất cả các lớp học phần mở.");
            generationRepository.save(generation);
            return GenerationResultResponse.builder()
                    .generationId(generation.getId())
                    .planId(plan.getId())
                    .planName(plan.getPlanName())
                    .semesterCode(plan.getSemester().getSemesterCode())
                    .totalCandidates(candidateCounter[0])
                    .feasibleCandidates(0L)
                    .status("NO_SOLUTION")
                    .message("Không tìm thấy phương án thời khóa biểu khả thi do trùng lịch toàn bộ các lớp mở.")
                    .options(List.of())
                    .build();
        }

        generation.setGenerationStatus("COMPLETED");
        generation = generationRepository.save(generation);

        List<ScheduleOptionDto> savedDtos = new ArrayList<>();
        int rank = 1;
        for (ScoredCandidate sc : topOptions) {
            ScheduleOption opt = ScheduleOption.builder()
                    .scheduleGeneration(generation)
                    .optionName("Phương án #" + rank + " (Điểm tối ưu: " + sc.totalScore + "/100)")
                    .optionRank(rank++)
                    .totalCredits(sc.totalCredits)
                    .totalScore(sc.totalScore)
                    .isFeasible(true)
                    .optionType("GENERATED")
                    .build();

            for (CourseSection cs : sc.sections) {
                opt.getSections().add(ScheduleOptionSection.builder()
                        .scheduleOption(opt)
                        .courseSection(cs)
                        .build());
            }
            for (EvaluationCriterionDto ev : sc.evaluations) {
                opt.getEvaluations().add(ScheduleOptionEvaluation.builder()
                        .scheduleOption(opt)
                        .criterionCode(ev.getCriterionCode())
                        .criterionName(ev.getCriterionName())
                        .scoreValue(ev.getScoreValue())
                        .weightValue(ev.getWeightValue())
                        .explanation(ev.getExplanation())
                        .build());
            }
            for (WarningDto w : sc.warnings) {
                opt.getWarnings().add(ScheduleOptionWarning.builder()
                        .scheduleOption(opt)
                        .warningType(w.getWarningType())
                        .severity(w.getSeverity())
                        .warningMessage(w.getWarningMessage())
                        .build());
            }

            opt = optionRepository.save(opt);
            savedDtos.add(toOptionDto(opt));
        }

        return GenerationResultResponse.builder()
                .generationId(generation.getId())
                .planId(plan.getId())
                .planName(plan.getPlanName())
                .semesterCode(plan.getSemester().getSemesterCode())
                .totalCandidates(candidateCounter[0])
                .feasibleCandidates((long) feasibleCombos.size())
                .status("COMPLETED")
                .message("Đã sinh thành công " + savedDtos.size() + " phương án thời khóa biểu tối ưu nhất.")
                .options(savedDtos)
                .build();
    }

    private void backtrackSchedules(List<List<CourseSection>> groups, int idx, List<CourseSection> current,
                                    List<List<CourseSection>> results, long[] counter, int maxCap) {
        if (results.size() >= maxCap) return;
        if (idx == groups.size()) {
            counter[0]++;
            results.add(new ArrayList<>(current));
            return;
        }
        for (CourseSection candidate : groups.get(idx)) {
            if (!hasTimeConflict(current, candidate)) {
                current.add(candidate);
                backtrackSchedules(groups, idx + 1, current, results, counter, maxCap);
                current.remove(current.size() - 1);
            } else {
                counter[0]++;
            }
        }
    }

    private boolean hasTimeConflict(List<CourseSection> selected, CourseSection candidate) {
        for (CourseSection existing : selected) {
            for (SectionMeeting m1 : existing.getMeetings()) {
                for (SectionMeeting m2 : candidate.getMeetings()) {
                    if (Objects.equals(m1.getWeekday(), m2.getWeekday())) {
                        boolean overlap = Math.max(m1.getStartPeriod(), m2.getStartPeriod())
                                <= Math.min(m1.getEndPeriod(), m2.getEndPeriod());
                        if (overlap) return true;
                    }
                }
            }
        }
        return false;
    }

    private record ScoredCandidate(List<CourseSection> sections, BigDecimal totalCredits, BigDecimal totalScore,
                                   List<EvaluationCriterionDto> evaluations, List<WarningDto> warnings) {}

    private ScoredCandidate evaluateCandidate(List<CourseSection> sections, RegistrationPreference pref, List<String> prereqWarnings) {
        BigDecimal totalCredits = sections.stream()
                .map(s -> s.getCourse().getCredits())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<EvaluationCriterionDto> evals = new ArrayList<>();
        List<WarningDto> warnings = new ArrayList<>();

        for (String pw : prereqWarnings) {
            warnings.add(WarningDto.builder().warningType("PREREQUISITE").severity("WARNING").warningMessage(pw).build());
        }
        if (pref.getMinCredits() != null && totalCredits.compareTo(pref.getMinCredits()) < 0) {
            warnings.add(WarningDto.builder().warningType("CREDIT_LOW").severity("WARNING")
                    .warningMessage("Tổng tín chỉ (" + totalCredits + ") thấp hơn mức tối thiểu (" + pref.getMinCredits() + ").").build());
        }
        if (pref.getMaxCredits() != null && totalCredits.compareTo(pref.getMaxCredits()) > 0) {
            warnings.add(WarningDto.builder().warningType("CREDIT_HIGH").severity("WARNING")
                    .warningMessage("Tổng tín chỉ (" + totalCredits + ") vượt quá giới hạn tối đa (" + pref.getMaxCredits() + ").").build());
        }

        List<SectionMeeting> allMeetings = sections.stream().flatMap(s -> s.getMeetings().stream()).toList();
       Set<Short> studyDays = allMeetings.stream().map(SectionMeeting::getWeekday).collect(Collectors.toSet());

        // 1. Tiêu chí Buổi học ưu tiên (Trọng số 0.30)
        long matchingShift = allMeetings.stream().filter(m -> {
            if ("MORNING".equalsIgnoreCase(pref.getPreferredShift())) return m.getEndPeriod() <= 6;
            if ("AFTERNOON".equalsIgnoreCase(pref.getPreferredShift())) return m.getStartPeriod() >= 7;
            return true;
        }).count();
        double shiftScore = allMeetings.isEmpty() ? 100.0 : (matchingShift * 100.0 / allMeetings.size());
        evals.add(EvaluationCriterionDto.builder()
                .criterionCode("PREFERRED_SHIFT").criterionName("Phù hợp buổi học ưu tiên")
                .scoreValue(BigDecimal.valueOf(shiftScore).setScale(2, RoundingMode.HALF_UP))
                .weightValue(new BigDecimal("0.30"))
                .explanation(matchingShift + "/" + allMeetings.size() + " buổi học khớp khung giờ ưu tiên (" + pref.getPreferredShift() + ").")
                .build());

        // 2. Tiêu chí Tránh học thứ 7 (Trọng số 0.25)
        boolean hasSaturday = studyDays.contains(6);
        double satScore = Boolean.TRUE.equals(pref.getAvoidSaturday()) ? (hasSaturday ? 20.0 : 100.0) : 100.0;
        if (Boolean.TRUE.equals(pref.getAvoidSaturday()) && hasSaturday) {
            warnings.add(WarningDto.builder().warningType("SATURDAY_CLASS").severity("INFO")
                    .warningMessage("Phương án có lịch học vào Thứ 7.").build());
        }
        evals.add(EvaluationCriterionDto.builder()
                .criterionCode("AVOID_SATURDAY").criterionName("Hạn chế lịch học Thứ 7")
                .scoreValue(BigDecimal.valueOf(satScore).setScale(2, RoundingMode.HALF_UP))
                .weightValue(new BigDecimal("0.25"))
                .explanation(hasSaturday ? "Có lịch học vào Thứ 7." : "Không có lịch học vào Thứ 7.")
                .build());

        // 3. Tiêu chí Gom ngày học / Số ngày trống (Trọng số 0.25)
        double compactScore = Math.max(20.0, 100.0 - (studyDays.size() - 1) * 14.0);
        evals.add(EvaluationCriterionDto.builder()
                .criterionCode("COMPACT_DAYS").criterionName("Mức độ gom ngày học trong tuần")
                .scoreValue(BigDecimal.valueOf(compactScore).setScale(2, RoundingMode.HALF_UP))
                .weightValue(new BigDecimal("0.25"))
                .explanation("Lịch học phân bổ trên " + studyDays.size() + " ngày/tuần (Nghỉ " + (7 - studyDays.size()) + " ngày).")
                .build());

        // 4. Tiêu chí Hạn chế tiết trống giữa giờ & Cảnh báo chuyển cơ sở (Trọng số 0.20)
        int gapPeriods = 0;
        Map<Short, List<SectionMeeting>> byDay = allMeetings.stream().collect(Collectors.groupingBy(SectionMeeting::getWeekday));
           for (Map.Entry<Short, List<SectionMeeting>> entry : byDay.entrySet()) {
            List<SectionMeeting> dayList = new ArrayList<>(entry.getValue());
            dayList.sort(Comparator.comparing(SectionMeeting::getStartPeriod));
            for (int i = 0; i < dayList.size() - 1; i++) {
                SectionMeeting m1 = dayList.get(i);
                SectionMeeting m2 = dayList.get(i + 1);
                int gap = Math.max(0, m2.getStartPeriod() - m1.getEndPeriod() - 1);
                gapPeriods += gap;
                if (m1.getRoom() != null && m2.getRoom() != null
                        && !Objects.equals(m1.getRoom().getCampus().getId(), m2.getRoom().getCampus().getId())
                        && gap <= 1) {
                    warnings.add(WarningDto.builder().warningType("CAMPUS_TRANSFER").severity("WARNING")
                            .warningMessage("Thứ " + (entry.getKey() + 1) + ": Chuyển cơ sở gấp giữa "
                                    + m1.getRoom().getCampus().getCampusName() + " và " + m2.getRoom().getCampus().getCampusName()).build());
                }
            }
        }
        double gapScore = Math.max(10.0, 100.0 - gapPeriods * 10.0);
        evals.add(EvaluationCriterionDto.builder()
                .criterionCode("MINIMIZE_GAPS").criterionName("Tối thiểu tiết trống giữa giờ")
                .scoreValue(BigDecimal.valueOf(gapScore).setScale(2, RoundingMode.HALF_UP))
                .weightValue(new BigDecimal("0.20"))
                .explanation("Tổng số tiết trống giữa các ca học trong tuần: " + gapPeriods + " tiết.")
                .build());

        double weightedTotal = shiftScore * 0.30 + satScore * 0.25 + compactScore * 0.25 + gapScore * 0.20;
        BigDecimal finalScore = BigDecimal.valueOf(weightedTotal).setScale(2, RoundingMode.HALF_UP);

        return new ScoredCandidate(sections, totalCredits, finalScore, evals, warnings);
    }

    // US28 & US29: Chỉnh sửa thủ công & Kiểm tra xung đột thời gian thực (BR-TT-07)
    @Transactional
    public ScheduleOptionDto createManualEditedOption(User user, Long baseOptionId, ManualEditOptionRequest req) {
        ScheduleOption base = optionRepository.findById(baseOptionId)
                .orElseThrow(() -> new BusinessException("OPTION_NOT_FOUND", "Không tìm thấy phương án gốc."));

        if (!base.getScheduleGeneration().getRegistrationPlan().getUser().getId().equals(user.getId())) {
            throw new BusinessException("FORBIDDEN", "Bạn không có quyền chỉnh sửa phương án này.");
        }

        List<CourseSection> newSections = new ArrayList<>();
        Set<Long> courseSeen = new HashSet<>();
        for (Long secId : req.getCourseSectionIds()) {
            CourseSection cs = sectionRepository.findById(secId)
                    .orElseThrow(() -> new BusinessException("SECTION_NOT_FOUND", "Không tìm thấy lớp học phần ID=" + secId));
            if (!courseSeen.add(cs.getCourse().getId())) {
                throw new BusinessException("DUPLICATE_COURSE", "Không thể chọn 2 lớp học phần của cùng một môn " + cs.getCourse().getCourseCode());
            }
            if (hasTimeConflict(newSections, cs)) {
                throw new BusinessException("BR-TT-07",
                        "Xung đột lịch học! Lớp " + cs.getSectionCode() + " (" + cs.getCourse().getCourseName() + ") bị trùng tiết với lớp đã chọn.");
            }
            newSections.add(cs);
        }

        RegistrationPreference pref = preferenceRepository
                .findByRegistrationPlanId(base.getScheduleGeneration().getRegistrationPlan().getId())
                .orElse(RegistrationPreference.builder().preferredShift("ANY").build());

        List<String> prereqWarnings = checkPrerequisitesForCourses(user.getId(),
                newSections.stream().map(s -> s.getCourse().getId()).toList());
        ScoredCandidate sc = evaluateCandidate(newSections, pref, prereqWarnings);

        ScheduleOption edited = ScheduleOption.builder()
                .scheduleGeneration(base.getScheduleGeneration())
                .parentOption(base)
                .optionName(req.getOptionName() != null && !req.getOptionName().isBlank()
                        ? req.getOptionName().trim() : base.getOptionName() + " (Chỉnh sửa)")
                .totalCredits(sc.totalCredits)
                .totalScore(sc.totalScore)
                .isFeasible(true)
                .optionType("MANUAL_EDITED")
                .build();

        for (CourseSection cs : newSections) {
            edited.getSections().add(ScheduleOptionSection.builder().scheduleOption(edited).courseSection(cs).build());
        }
        for (EvaluationCriterionDto ev : sc.evaluations) {
            edited.getEvaluations().add(ScheduleOptionEvaluation.builder()
                    .scheduleOption(edited).criterionCode(ev.getCriterionCode()).criterionName(ev.getCriterionName())
                    .scoreValue(ev.getScoreValue()).weightValue(ev.getWeightValue()).explanation(ev.getExplanation()).build());
        }
        for (WarningDto w : sc.warnings) {
            edited.getWarnings().add(ScheduleOptionWarning.builder()
                    .scheduleOption(edited).warningType(w.getWarningType()).severity(w.getSeverity()).warningMessage(w.getWarningMessage()).build());
        }

        return toOptionDto(optionRepository.save(edited));
    }

    // US31 & US32: Lưu phương án TKB yêu thích (BR-TT-08: Tối đa 5 phương án / học kỳ)
    @Transactional
    public SavedScheduleDto saveFavoriteSchedule(User user, SaveScheduleRequest req) {
        ScheduleOption opt = optionRepository.findById(req.getScheduleOptionId())
                .orElseThrow(() -> new BusinessException("OPTION_NOT_FOUND", "Không tìm thấy phương án thời khóa biểu."));

        Long semesterId = opt.getScheduleGeneration().getRegistrationPlan().getSemester().getId();
        long existingCount = savedScheduleRepository
                .countByUserIdAndScheduleOptionScheduleGenerationRegistrationPlanSemesterId(user.getId(), semesterId);

        if (existingCount >= 5) {
            throw new BusinessException("BR-TT-08", "Bạn chỉ được lưu tối đa 5 phương án thời khóa biểu cho mỗi học kỳ.");
        }

        SavedSchedule saved = SavedSchedule.builder()
                .user(user)
                .scheduleOption(opt)
                .displayName(req.getDisplayName().trim())
                .savedAt(LocalDateTime.now())
                .build();
        saved = savedScheduleRepository.save(saved);

        return SavedScheduleDto.builder()
                .id(saved.getId())
                .displayName(saved.getDisplayName())
                .savedAt(saved.getSavedAt())
                .option(toOptionDto(opt))
                .build();
    }

    public List<SavedScheduleDto> listMySavedSchedules(User user) {
        return savedScheduleRepository.findByUserIdOrderBySavedAtDesc(user.getId()).stream()
                .map(s -> SavedScheduleDto.builder()
                        .id(s.getId())
                        .displayName(s.getDisplayName())
                        .savedAt(s.getSavedAt())
                        .option(toOptionDto(s.getScheduleOption()))
                        .build())
                .toList();
    }

    @Transactional
    public void deleteSavedSchedule(User user, Long savedId) {
        SavedSchedule ss = savedScheduleRepository.findById(savedId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Không tìm thấy thời khóa biểu đã lưu."));
        if (!ss.getUser().getId().equals(user.getId())) {
            throw new BusinessException("FORBIDDEN", "Không có quyền xóa.");
        }
        savedScheduleRepository.delete(ss);
    }

    // US33 & US34: Xuất dữ liệu thời khóa biểu & ghi nhận schedule_exports
    @Transactional
    public Map<String, Object> recordExportSchedule(User user, Long optionId, String format) {
        ScheduleOption opt = optionRepository.findById(optionId)
                .orElseThrow(() -> new BusinessException("OPTION_NOT_FOUND", "Không tìm thấy phương án thời khóa biểu."));

        String fmt = format.toUpperCase();
        if (!List.of("PNG", "PDF").contains(fmt)) {
            throw new BusinessException("INVALID_FORMAT", "Định dạng xuất phải là PNG hoặc PDF.");
        }

        ScheduleExport export = ScheduleExport.builder()
                .user(user)
                .scheduleOption(opt)
                .exportFormat(fmt)
                .filePath("/exports/tkb_" + optionId + "_" + System.currentTimeMillis() + "." + fmt.toLowerCase())
                .exportStatus("COMPLETED")
                .fileSizeBytes(245760L)
                .exportedAt(LocalDateTime.now())
                .build();
        export = exportRepository.save(export);

        return Map.of(
                "exportId", export.getId(),
                "format", export.getExportFormat(),
                "status", export.getExportStatus(),
                "option", toOptionDto(opt)
        );
    }

    private List<String> checkPrerequisitesForCourses(Long userId, List<Long> courseIds) {
        Set<Long> passedIds = studentRecordRepository.findByUserIdAndResultStatus(userId, "PASSED").stream()
                .map(r -> r.getCourse().getId()).collect(Collectors.toSet());
        List<CoursePrerequisite> prereqs = prerequisiteRepository.findByCourseIdIn(courseIds);
        List<String> warnings = new ArrayList<>();
        for (CoursePrerequisite cp : prereqs) {
            if (!passedIds.contains(cp.getPrerequisiteCourse().getId())) {
                warnings.add("Học phần " + cp.getCourse().getCourseCode() + " yêu cầu môn tiên quyết: "
                        + cp.getPrerequisiteCourse().getCourseCode() + " (" + cp.getPrerequisiteCourse().getCourseName() + ")");
            }
        }
        return warnings;
    }

    private RegistrationPlan getOwnedPlan(User user, Long planId) {
        RegistrationPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("PLAN_NOT_FOUND", "Không tìm thấy kế hoạch đăng ký."));
        if (!plan.getUser().getId().equals(user.getId())) {
            throw new BusinessException("FORBIDDEN", "Bạn không có quyền truy cập kế hoạch này.");
        }
        return plan;
    }

    public CourseSectionDto toSectionDto(CourseSection cs) {
        return CourseSectionDto.builder()
                .id(cs.getId())
                .courseId(cs.getCourse().getId())
                .courseCode(cs.getCourse().getCourseCode())
                .courseName(cs.getCourse().getCourseName())
                .credits(cs.getCourse().getCredits())
                .sectionCode(cs.getSectionCode())
                .maxCapacity(cs.getMaxCapacity())
                .enrolledCount(0)
                .sectionStatus(cs.getSectionStatus())
                .meetings(cs.getMeetings().stream().map(m -> SectionMeetingDto.builder()
                        .dayOfWeek(m.getWeekday())
                        .startPeriod(m.getStartPeriod())
                        .endPeriod(m.getEndPeriod())
                        .campusCode(m.getRoom() != null ? m.getRoom().getCampus().getCampusCode() : "ONLINE")
                        .roomCode(m.getRoom() != null ? m.getRoom().getRoomCode() : "ONLINE")
                        .build()).toList())
                .build();
    }

    public ScheduleOptionDto toOptionDto(ScheduleOption opt) {
        return ScheduleOptionDto.builder()
                .id(opt.getId())
                .optionName(opt.getOptionName())
                .optionRank(opt.getOptionRank())
                .totalCredits(opt.getTotalCredits())
                .totalScore(opt.getTotalScore())
                .isFeasible(opt.getIsFeasible())
                .optionType(opt.getOptionType())
                .sections(opt.getSections().stream().map(s -> toSectionDto(s.getCourseSection())).toList())
                .evaluations(opt.getEvaluations().stream().map(e -> EvaluationCriterionDto.builder()
                        .criterionCode(e.getCriterionCode())
                        .criterionName(e.getCriterionName())
                        .scoreValue(e.getScoreValue())
                        .weightValue(e.getWeightValue())
                        .explanation(e.getExplanation())
                        .build()).toList())
                .warnings(opt.getWarnings().stream().map(w -> WarningDto.builder()
                        .warningType(w.getWarningType())
                        .severity(w.getSeverity())
                        .warningMessage(w.getWarningMessage())
                        .build()).toList())
                .build();
    }
}