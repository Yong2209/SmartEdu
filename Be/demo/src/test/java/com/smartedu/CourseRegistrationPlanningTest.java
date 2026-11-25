package com.smartedu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.academic_management.entity.Course;
import com.smartedu.modules.academic_management.entity.CoursePrerequisite;
import com.smartedu.modules.academic_management.entity.CourseSection;
import com.smartedu.modules.academic_management.entity.SectionMeeting;
import com.smartedu.modules.academic_management.entity.Semester;
import com.smartedu.modules.academic_management.repository.CoursePrerequisiteRepository;
import com.smartedu.modules.academic_management.repository.CourseRepository;
import com.smartedu.modules.academic_management.repository.CourseSectionRepository;
import com.smartedu.modules.academic_management.repository.SectionMeetingRepository;
import com.smartedu.modules.academic_management.repository.SemesterRepository;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.AcademicRecordResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ConfirmImportRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CourseImportRowInput;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitCheckRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitCheckResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ImportValidationResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.PrerequisiteCheckRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.PrerequisiteCheckResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.RegistrationPlanResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ScheduleConflictCheckRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ScheduleConflictCheckResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.SchedulePreferenceRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.SchedulePreferenceResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.TranscriptImportResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.UpsertAcademicRecordRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ValidateCourseListRequest;
import com.smartedu.modules.course_registration.entity.RegistrationImport;
import com.smartedu.modules.course_registration.entity.RegistrationPlan;
import com.smartedu.modules.course_registration.entity.RegistrationPlanCourse;
import com.smartedu.modules.course_registration.entity.RegistrationPreference;
import com.smartedu.modules.course_registration.entity.StudentCourseRecord;
import com.smartedu.modules.course_registration.repository.RegistrationImportRepository;
import com.smartedu.modules.course_registration.repository.RegistrationPlanRepository;
import com.smartedu.modules.course_registration.repository.RegistrationPreferenceRepository;
import com.smartedu.modules.course_registration.repository.StudentCourseRecordRepository;
import com.smartedu.modules.course_registration.service.CourseImportService;
import com.smartedu.modules.course_registration.service.CourseValidationService;
import com.smartedu.modules.course_registration.service.RegistrationCheckService;
import com.smartedu.modules.course_registration.service.RegistrationPlanService;
import com.smartedu.modules.course_registration.service.RegistrationPreferenceService;
import com.smartedu.modules.course_registration.service.StudentAcademicRecordService;
import com.smartedu.modules.user_management.entity.User;

@ExtendWith(MockitoExtension.class)
class CourseRegistrationPlanningTest {

    @TempDir Path tempDir;

    @Mock private SemesterRepository semesterRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseSectionRepository courseSectionRepository;
    @Mock private SectionMeetingRepository sectionMeetingRepository;
    @Mock private CoursePrerequisiteRepository prerequisiteRepository;
    @Mock private RegistrationImportRepository importRepository;
    @Mock private RegistrationPlanRepository planRepository;
    @Mock private RegistrationPreferenceRepository preferenceRepository;
    @Mock private StudentCourseRecordRepository studentCourseRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CourseValidationService validationService;
    private RegistrationPlanService planService;
    private CourseImportService importService;
    private StudentAcademicRecordService academicRecordService;
    private RegistrationPreferenceService preferenceService;
    private RegistrationCheckService checkService;

    private User student;
    private User otherStudent;
    private Semester semester;
    private Course cs102;
    private Course cs201;
    private Course cs202;

    @BeforeEach
    void setUp() {
        validationService = new CourseValidationService(courseRepository, courseSectionRepository, objectMapper);
        planService = new RegistrationPlanService(
                planRepository, semesterRepository, courseRepository, courseSectionRepository, objectMapper);
        importService = new CourseImportService(
                semesterRepository, courseRepository, importRepository, planRepository,
                validationService, planService, tempDir.toString());
        academicRecordService = new StudentAcademicRecordService(
                studentCourseRecordRepository, courseRepository, semesterRepository,
                importRepository, validationService, tempDir.toString());
        preferenceService = new RegistrationPreferenceService(
                planService, preferenceRepository, objectMapper, null, null);
        checkService = new RegistrationCheckService(
                planService, planRepository, preferenceRepository, courseRepository,
                courseSectionRepository, sectionMeetingRepository, prerequisiteRepository,
                studentCourseRecordRepository);

        student = new User();
        student.setId(9001L);
        student.setUsername("student.demo");

        otherStudent = new User();
        otherStudent.setId(9002L);
        otherStudent.setUsername("student.demo2");

        semester = Semester.builder()
                .id(11001L).semesterCode("DEMO-HK1-2026").semesterName("Học kỳ 1")
                .academicYear("2026-2027").termNo(1).semesterStatus("ACTIVE").build();

        cs102 = Course.builder().id(11102L).courseCode("DEMO-CS102").courseName("Lập trình căn bản")
                .credits(new BigDecimal("3.0")).isActive(true).build();
        cs201 = Course.builder().id(11103L).courseCode("DEMO-CS201").courseName("Cơ sở dữ liệu")
                .credits(new BigDecimal("3.0")).isActive(true).build();
        cs202 = Course.builder().id(11104L).courseCode("DEMO-CS202").courseName("Lập trình hướng đối tượng")
                .credits(new BigDecimal("4.0")).isActive(true).build();

        lenient().when(semesterRepository.findById(11001L)).thenReturn(Optional.of(semester));
        lenient().when(courseRepository.findByCourseCodeIgnoreCase("DEMO-CS102")).thenReturn(Optional.of(cs102));
        lenient().when(courseRepository.findByCourseCodeIgnoreCase("DEMO-CS201")).thenReturn(Optional.of(cs201));
        lenient().when(courseRepository.findByCourseCodeIgnoreCase("DEMO-CS202")).thenReturn(Optional.of(cs202));
    }

    // PB20 & PB21: Upload CSV/XLSX, validate errors, edit & confirm
    @Test
    void pb20_pb21_uploadValidateAndConfirmFlow() {
        String badCsv = "course_code,course_name,credits,section_code\n"
                + "DEMO-CS201,Cơ sở dữ liệu,3.0,\n"
                + "DEMO-CS201,Cơ sở dữ liệu,3.0,\n"
                + "UNKNOWN-999,Môn lạ,3.0,\n"
                + "DEMO-CS202,,abc,\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "courses.csv", "text/csv", badCsv.getBytes(StandardCharsets.UTF_8));

        AtomicLong importSeq = new AtomicLong(500);
        when(importRepository.save(any(RegistrationImport.class))).thenAnswer(inv -> {
            RegistrationImport imp = inv.getArgument(0);
            if (imp.getId() == null) imp.setId(importSeq.incrementAndGet());
            return imp;
        });

        ImportValidationResponse res = importService.uploadCourseFile(student, 11001L, file);
        assertThat(res.isValid()).isFalse();
        assertThat(res.getImportStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(res.getValidRows()).isEqualTo(1L);
        assertThat(res.getErrorRows()).isGreaterThanOrEqualTo(3L);
        assertThat(res.getErrors()).extracting("errorCode")
                .contains("DUPLICATE_COURSE", "COURSE_NOT_FOUND");

        // Confirm with unresolved errors -> rejected
        RegistrationImport savedImp = RegistrationImport.builder()
                .id(res.getImportId()).user(student).semester(semester)
                .importType("STUDENT_REGISTRATION").fileName("courses.csv")
                .storagePath(tempDir.resolve("imports").toFile().listFiles()[0].getAbsolutePath())
                .fileFormat("CSV").importStatus("VALIDATION_FAILED").build();
        when(importRepository.findById(res.getImportId())).thenReturn(Optional.of(savedImp));

        assertThatThrownBy(() -> importService.confirmImport(student, res.getImportId(), null))
                .isInstanceOf(BusinessException.class);

        // Fix via validate endpoint (PB20.6/PB21.8)
        ValidateCourseListRequest fixReq = new ValidateCourseListRequest(
                11001L, res.getImportId(),
                List.of(
                        new CourseImportRowInput("DEMO-CS201", "Cơ sở dữ liệu", new BigDecimal("3.0"), null),
                        new CourseImportRowInput("DEMO-CS202", "Lập trình hướng đối tượng", new BigDecimal("4.0"), null)
                ));
        ImportValidationResponse fixed = importService.validateCourseList(student, fixReq);
        assertThat(fixed.isValid()).isTrue();
        assertThat(fixed.getErrorRows()).isEqualTo(0L);

        // Confirm creates RegistrationPlan with totalCredits = 7.0
        when(courseRepository.getReferenceById(11103L)).thenReturn(cs201);
        when(courseRepository.getReferenceById(11104L)).thenReturn(cs202);
        when(planRepository.save(any(RegistrationPlan.class))).thenAnswer(inv -> {
            RegistrationPlan p = inv.getArgument(0);
            if (p.getId() == null) p.setId(700L);
            return p;
        });
        when(courseSectionRepository.findBySemesterIdOrderBySectionCodeAsc(11001L)).thenReturn(List.of());

        RegistrationPlanResponse confirmedPlan = importService.confirmImport(
                student, res.getImportId(), new ConfirmImportRequest("HK1 Plan", null));
        assertThat(confirmedPlan.getCourseCount()).isEqualTo(2);
        assertThat(confirmedPlan.getTotalCredits()).isEqualByComparingTo("7.0");
    }

    // PB22: Select semester & courses offered in semester
    @Test
    void pb22_selectSemesterAndOfferedCourses() {
        when(courseRepository.findById(11103L)).thenReturn(Optional.of(cs201));
        when(courseRepository.findById(11102L)).thenReturn(Optional.of(cs102));
        when(courseSectionRepository.existsBySemesterIdAndCourseId(11001L, 11103L)).thenReturn(true);
        when(courseSectionRepository.existsBySemesterIdAndCourseId(11001L, 11102L)).thenReturn(false);

        RegistrationPlan plan = RegistrationPlan.builder()
                .id(710L).user(student).semester(semester).planName("My Plan").planCourses(new ArrayList<>()).build();
        when(planRepository.findById(710L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(RegistrationPlan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(courseSectionRepository.findBySemesterIdOrderBySectionCodeAsc(11001L)).thenReturn(List.of());

        RegistrationPlanResponse added = planService.addCourseToPlan(student, 710L, 11103L);
        assertThat(added.getTotalCredits()).isEqualByComparingTo("3.0");

        // Adding course not offered in semester -> rejected
        assertThatThrownBy(() -> planService.addCourseToPlan(student, 710L, 11102L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được mở lớp");

        // Removing course updates totalCredits to 0
        RegistrationPlanResponse removed = planService.removeCourseFromPlan(student, 710L, 11103L);
        assertThat(removed.getCourseCount()).isEqualTo(0);
        assertThat(removed.getTotalCredits()).isEqualByComparingTo("0");
    }

    // PB23: Manual entry + Excel transcript import + ownership
    @Test
    void pb23_academicRecordsManualAndExcelImport() throws Exception {
        when(studentCourseRecordRepository.findByUserIdAndCourseId(9001L, 11102L)).thenReturn(Optional.empty());
        when(studentCourseRecordRepository.save(any(StudentCourseRecord.class))).thenAnswer(inv -> {
            StudentCourseRecord r = inv.getArgument(0);
            if (r.getId() == null) r.setId(801L);
            return r;
        });

        UpsertAcademicRecordRequest manualReq = new UpsertAcademicRecordRequest(
                null, "DEMO-CS102", 11001L, new BigDecimal("8.5"), "PASSED");
        AcademicRecordResponse rec = academicRecordService.upsertRecord(student, manualReq);
        assertThat(rec.getResultStatus()).isEqualTo("PASSED");
        assertThat(rec.getCourseCode()).isEqualTo("DEMO-CS102");

        // Build real XLSX transcript
        byte[] xlsxBytes;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Transcript");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("course_code");
            h.createCell(1).setCellValue("grade");
            h.createCell(2).setCellValue("result_status");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("DEMO-CS102");
            r1.createCell(1).setCellValue(8.0);
            r1.createCell(2).setCellValue("PASSED");
            Row r2 = s.createRow(2);
            r2.createCell(0).setCellValue("MISSING-CODE");
            r2.createCell(1).setCellValue(7.0);
            r2.createCell(2).setCellValue("PASSED");
            wb.write(out);
            xlsxBytes = out.toByteArray();
        }

        when(importRepository.save(any(RegistrationImport.class))).thenAnswer(inv -> {
            RegistrationImport imp = inv.getArgument(0);
            if (imp.getId() == null) imp.setId(550L);
            return imp;
        });
        MockMultipartFile xlsxFile = new MockMultipartFile(
                "file", "transcript.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes);
        TranscriptImportResponse impRes = academicRecordService.importTranscript(student, 11001L, xlsxFile);
        assertThat(impRes.getValidRows()).isEqualTo(1L);
        assertThat(impRes.getErrorRows()).isEqualTo(1L);
        assertThat(impRes.getSavedRecords()).hasSize(1);

        // Ownership check on delete
        StudentCourseRecord ownedRecord = StudentCourseRecord.builder()
                .id(801L).user(student).course(cs102).resultStatus("PASSED").build();
        when(studentCourseRecordRepository.findById(801L)).thenReturn(Optional.of(ownedRecord));
        assertThatThrownBy(() -> academicRecordService.deleteRecord(otherStudent, 801L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // PB24 & PB25: Credit limits & Soft schedule preferences
    @Test
    void pb24_pb25_creditLimitsAndSoftSchedulePreferences() {
        RegistrationPlan plan = RegistrationPlan.builder()
                .id(720L).user(student).semester(semester).planName("Plan 720").build();
        when(planRepository.findById(720L)).thenReturn(Optional.of(plan));

        RegistrationPreference storedPref = RegistrationPreference.builder()
                .id(901L).registrationPlan(plan)
                .minCredits(BigDecimal.ZERO).maxCredits(BigDecimal.ZERO).build();
        when(preferenceRepository.findByRegistrationPlanId(720L)).thenReturn(Optional.of(storedPref));
        when(preferenceRepository.save(any(RegistrationPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        // Invalid min > max rejected
        assertThatThrownBy(() -> preferenceService.saveCreditLimits(
                student, 720L, new CreditLimitRequest(new BigDecimal("18"), new BigDecimal("12"))))
                .isInstanceOf(BusinessException.class);

        CreditLimitResponse savedLimits = preferenceService.saveCreditLimits(
                student, 720L, new CreditLimitRequest(new BigDecimal("6"), new BigDecimal("18")));
        assertThat(savedLimits.isConfigured()).isTrue();
        assertThat(savedLimits.getMinCredits()).isEqualByComparingTo("6");

        // Invalid preferredShift rejected
        SchedulePreferenceRequest badShift = new SchedulePreferenceRequest(
                "NIGHT", true, true, true, List.of(1, 7), 2);
        assertThatThrownBy(() -> preferenceService.saveSchedulePreferences(student, 720L, badShift))
                .isInstanceOf(BusinessException.class);

        SchedulePreferenceRequest goodPref = new SchedulePreferenceRequest(
                "MORNING", true, true, true, List.of(7, 3, 3), 2);
        SchedulePreferenceResponse savedPref = preferenceService.saveSchedulePreferences(student, 720L, goodPref);
        assertThat(savedPref.getPreferredShift()).isEqualTo("MORNING");
        assertThat(savedPref.getPreferredFreeDays()).containsExactly(3, 7);
        assertThat(savedPref.getConstraintType()).isEqualTo("SOFT_CONSTRAINT");
        // Verify PB24 limits were preserved
        assertThat(storedPref.getMaxCredits()).isEqualByComparingTo("18");
    }

    // PB26, PB27, PB28: Separate verifiable backend checks
    @Test
    void pb26_scheduleConflictCheck_detectsOverlapAndKeepsNonOverlapping() {
        CourseSection sec1 = CourseSection.builder().id(1L).semester(semester).course(cs201).sectionCode("CS201-01").build();
        CourseSection sec2 = CourseSection.builder().id(2L).semester(semester).course(cs202).sectionCode("CS202-01").build();
        CourseSection sec3 = CourseSection.builder().id(3L).semester(semester).course(cs202).sectionCode("CS202-02").build();

        // sec1: Mon periods 1-3; sec2: Mon periods 3-5 (OVERLAPS on period 3); sec3: Mon periods 4-6 (NO overlap!)
        SectionMeeting m1 = SectionMeeting.builder().id(11L).courseSection(sec1).weekday(2).startPeriod(1).endPeriod(3).build();
        SectionMeeting m2 = SectionMeeting.builder().id(12L).courseSection(sec2).weekday(2).startPeriod(3).endPeriod(5).build();
        SectionMeeting m3 = SectionMeeting.builder().id(13L).courseSection(sec3).weekday(2).startPeriod(4).endPeriod(6).build();

        when(courseSectionRepository.findAllById(any())).thenReturn(List.of(sec1, sec2, sec3));
        when(sectionMeetingRepository.findByCourseSectionIdIn(anyList())).thenReturn(List.of(m1, m2, m3));

        ScheduleConflictCheckRequest req = new ScheduleConflictCheckRequest(
                null, null, List.of(List.of(1L, 2L), List.of(1L, 3L)));
        ScheduleConflictCheckResponse res = checkService.checkScheduleConflicts(student, req);

        assertThat(res.getTotalChecked()).isEqualTo(2);
        assertThat(res.getValidCount()).isEqualTo(1);
        assertThat(res.getRejectedCount()).isEqualTo(1);
        assertThat(res.getValidCombinations().get(0).getSectionIds()).containsExactly(1L, 3L);
        assertThat(res.getRejectedCombinations().get(0).getConflicts().get(0).getReason()).contains("Trùng lịch Thứ 2");
    }

    @Test
    void pb27_prerequisiteCheck_usesStudentPassedRecords() {
        RegistrationPlan plan = RegistrationPlan.builder()
                .id(730L).user(student).semester(semester).planName("Plan 730")
                .planCourses(List.of(
                        RegistrationPlanCourse.builder().course(cs201).build(),
                        RegistrationPlanCourse.builder().course(cs202).build()
                )).build();
        when(planRepository.findById(730L)).thenReturn(Optional.of(plan));

        // cs201 requires cs102; cs202 has no prerequisite
        CoursePrerequisite prereq = CoursePrerequisite.builder()
                .id(1L).course(cs201).prerequisiteCourse(cs102).build();
        when(prerequisiteRepository.findByCourseIdIn(List.of(11103L, 11104L))).thenReturn(List.of(prereq));

        // Case 1: student has NOT passed cs102
        when(studentCourseRecordRepository.findByUserIdAndResultStatusIgnoreCase(9001L, "PASSED"))
                .thenReturn(List.of());
        PrerequisiteCheckResponse resFail = checkService.checkPrerequisites(
                student, new PrerequisiteCheckRequest(730L, null));
        assertThat(resFail.getEligibleCount()).isEqualTo(1);
        assertThat(resFail.getIneligibleCount()).isEqualTo(1);
        assertThat(resFail.getIneligibleCourses().get(0).getCourseCode()).isEqualTo("DEMO-CS201");
        assertThat(resFail.getIneligibleCourses().get(0).getReason()).contains("DEMO-CS102");

        // Case 2: student PASSED cs102
        when(studentCourseRecordRepository.findByUserIdAndResultStatusIgnoreCase(9001L, "PASSED"))
                .thenReturn(List.of(StudentCourseRecord.builder().user(student).course(cs102).resultStatus("PASSED").build()));
        PrerequisiteCheckResponse resPass = checkService.checkPrerequisites(
                student, new PrerequisiteCheckRequest(730L, null));
        assertThat(resPass.getEligibleCount()).isEqualTo(2);
        assertThat(resPass.getIneligibleCount()).isEqualTo(0);
    }

    @Test
    void pb28_creditLimitCheck_requiresConfiguredLimitsAndFiltersCombinations() {
        RegistrationPlan plan = RegistrationPlan.builder()
                .id(740L).user(student).semester(semester).planName("Plan 740").build();
        when(planRepository.findById(740L)).thenReturn(Optional.of(plan));

        // Unconfigured credit limits -> fail
        when(preferenceRepository.findByRegistrationPlanId(740L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> checkService.checkCreditLimits(
                student, new CreditLimitCheckRequest(740L, null, null, List.of(List.of(1L)), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PB24");

        // Configured: min 4.0, max 7.0
        when(preferenceRepository.findByRegistrationPlanId(740L)).thenReturn(Optional.of(
                RegistrationPreference.builder()
                        .registrationPlan(plan)
                        .minCredits(new BigDecimal("4.0"))
                        .maxCredits(new BigDecimal("7.0"))
                        .build()));

        CourseSection s1 = CourseSection.builder().id(1L).semester(semester).course(cs201).sectionCode("S1").build(); // 3.0
        CourseSection s2 = CourseSection.builder().id(2L).semester(semester).course(cs202).sectionCode("S2").build(); // 4.0
        CourseSection s3 = CourseSection.builder().id(3L).semester(semester).course(cs102).sectionCode("S3").build(); // 3.0
        when(courseSectionRepository.findAllById(any())).thenReturn(List.of(s1, s2, s3));

        // Combo 1: [1] = 3.0 (< 4.0) -> BELOW_MIN_CREDITS
        // Combo 2: [1, 2] = 7.0 (in [4, 7]) -> PASSED
        // Combo 3: [1, 2, 3] = 10.0 (> 7.0) -> EXCEEDS_MAX_CREDITS
        CreditLimitCheckResponse res = checkService.checkCreditLimits(
                student,
                new CreditLimitCheckRequest(740L, null, null,
                        List.of(List.of(1L), List.of(1L, 2L), List.of(1L, 2L, 3L)), null));

        assertThat(res.getTotalChecked()).isEqualTo(3);
        assertThat(res.getValidCount()).isEqualTo(1);
        assertThat(res.getRejectedCount()).isEqualTo(2);
        assertThat(res.getValidCombinations().get(0).getTotalCredits()).isEqualByComparingTo("7.0");
        assertThat(res.getRejectedCombinations().get(0).getViolationType()).isEqualTo("BELOW_MIN_CREDITS");
        assertThat(res.getRejectedCombinations().get(1).getViolationType()).isEqualTo("EXCEEDS_MAX_CREDITS");
    }
}
