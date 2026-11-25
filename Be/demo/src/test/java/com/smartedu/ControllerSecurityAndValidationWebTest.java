package com.smartedu;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.common.exception.GlobalExceptionHandler;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.academic_management.controller.AcademicCatalogController;
import com.smartedu.modules.academic_management.dto.AcademicDtos.SemesterDto;
import com.smartedu.modules.academic_management.service.AcademicCatalogService;
import com.smartedu.modules.ai_integration.controller.AiLearningController;
import com.smartedu.modules.ai_integration.service.AiQuestionService;
import com.smartedu.modules.ai_integration.service.QuizService;
import com.smartedu.modules.course_registration.controller.CourseImportController;
import com.smartedu.modules.course_registration.controller.RegistrationCheckController;
import com.smartedu.modules.course_registration.controller.RegistrationPlanController;
import com.smartedu.modules.course_registration.controller.RegistrationPreferenceController;
import com.smartedu.modules.course_registration.controller.StudentAcademicRecordController;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitCheckResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitResponse;
import com.smartedu.modules.course_registration.service.CourseImportService;
import com.smartedu.modules.course_registration.service.RegistrationCheckService;
import com.smartedu.modules.course_registration.service.RegistrationPlanService;
import com.smartedu.modules.course_registration.service.RegistrationPreferenceService;
import com.smartedu.modules.course_registration.service.StudentAcademicRecordService;
import com.smartedu.modules.user_management.entity.User;

@ExtendWith(MockitoExtension.class)
class ControllerSecurityAndValidationWebTest {

    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private AiQuestionService aiQuestionService;
    @Mock private QuizService quizService;
    @Mock private AcademicCatalogService academicCatalogService;
    @Mock private CourseImportService courseImportService;
    @Mock private RegistrationPlanService registrationPlanService;
    @Mock private StudentAcademicRecordService studentAcademicRecordService;
    @Mock private RegistrationPreferenceService registrationPreferenceService;
    @Mock private RegistrationCheckService registrationCheckService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("student1");

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AiLearningController(aiQuestionService, quizService, currentUserResolver),
                        new AcademicCatalogController(academicCatalogService),
                        new CourseImportController(courseImportService, currentUserResolver),
                        new RegistrationPlanController(registrationPlanService, currentUserResolver),
                        new StudentAcademicRecordController(studentAcademicRecordService, currentUserResolver),
                        new RegistrationPreferenceController(registrationPreferenceService, currentUserResolver),
                        new RegistrationCheckController(registrationCheckService, currentUserResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        when(currentUserResolver.requireCurrentUser())
                .thenThrow(new BusinessException("Vui lòng đăng nhập", HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/api/ai/question-sets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void invalidPayloadReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/ai/questions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\": 99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void malformedJsonReturns400Not500() throws Exception {
        mockMvc.perform(post("/api/registration/checks/schedule-conflicts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void endpointsReturnExpectedSuccessStructures() throws Exception {
        when(currentUserResolver.requireCurrentUser()).thenReturn(user);
        when(academicCatalogService.listSemesters()).thenReturn(List.of(
                SemesterDto.builder().id(11001L).semesterCode("DEMO-HK1-2026").semesterName("Học kỳ 1").build()));
        when(registrationPreferenceService.saveCreditLimits(eq(user), eq(700L), any()))
                .thenReturn(CreditLimitResponse.builder()
                        .planId(700L).semesterId(11001L)
                        .minCredits(new BigDecimal("6")).maxCredits(new BigDecimal("18")).configured(true).build());
        when(registrationCheckService.checkCreditLimits(eq(user), any()))
                .thenReturn(CreditLimitCheckResponse.builder()
                        .planId(700L).minCredits(new BigDecimal("6")).maxCredits(new BigDecimal("18"))
                        .totalChecked(1).validCount(1).rejectedCount(0).validCombinations(List.of()).rejectedCombinations(List.of()).build());

        mockMvc.perform(get("/api/academic/semesters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].semesterCode").value("DEMO-HK1-2026"));

        mockMvc.perform(put("/api/registration/plans/700/credit-limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minCredits\":6,\"maxCredits\":18}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.configured").value(true));

        mockMvc.perform(post("/api/registration/checks/credit-limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":700,\"combinations\":[[1,2]]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.validCount").value(1));
    }
}
