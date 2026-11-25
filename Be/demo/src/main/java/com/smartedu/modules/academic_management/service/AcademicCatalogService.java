package com.smartedu.modules.academic_management.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.academic_management.dto.AcademicDtos.CourseDto;
import com.smartedu.modules.academic_management.dto.AcademicDtos.CourseSectionDto;
import com.smartedu.modules.academic_management.dto.AcademicDtos.PrerequisiteRefDto;
import com.smartedu.modules.academic_management.dto.AcademicDtos.SectionMeetingDto;
import com.smartedu.modules.academic_management.dto.AcademicDtos.SemesterDto;
import com.smartedu.modules.academic_management.entity.Course;
import com.smartedu.modules.academic_management.entity.CoursePrerequisite;
import com.smartedu.modules.academic_management.entity.CourseSection;
import com.smartedu.modules.academic_management.entity.SectionMeeting;
import com.smartedu.modules.academic_management.entity.Semester;
import com.smartedu.modules.academic_management.repository.CoursePrerequisiteRepository;
import com.smartedu.modules.academic_management.repository.CourseRepository;
import com.smartedu.modules.academic_management.repository.CourseSectionRepository;
import com.smartedu.modules.academic_management.repository.SemesterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AcademicCatalogService {

    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final CoursePrerequisiteRepository prerequisiteRepository;

    @Transactional(readOnly = true)
    public List<SemesterDto> listSemesters() {
        return semesterRepository.findAllByOrderByAcademicYearDescTermNoDesc().stream()
                .map(this::toSemesterDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseDto> searchCatalogCourses(String keyword) {
        String cleanKeyword = keyword != null ? keyword.trim() : null;
        List<Course> courses = courseRepository.searchActiveCourses(cleanKeyword);
        return enrichCourses(courses, null);
    }

    @Transactional(readOnly = true)
    public List<CourseDto> getCoursesInSemester(Long semesterId, String keyword) {
        if (!semesterRepository.existsById(semesterId)) {
            throw new BusinessException("Không tìm thấy học kỳ.", HttpStatus.NOT_FOUND);
        }
        String cleanKeyword = keyword != null ? keyword.trim() : null;
        List<Course> courses = courseRepository.findOfferedCoursesInSemester(semesterId, cleanKeyword);
        return enrichCourses(courses, semesterId);
    }

    @Transactional(readOnly = true)
    public List<CourseSectionDto> getSectionsInSemester(Long semesterId, Long courseId) {
        if (!semesterRepository.existsById(semesterId)) {
            throw new BusinessException("Không tìm thấy học kỳ.", HttpStatus.NOT_FOUND);
        }
        List<CourseSection> sections = courseId != null
                ? courseSectionRepository.findBySemesterIdAndCourseIdOrderBySectionCodeAsc(semesterId, courseId)
                : courseSectionRepository.findBySemesterIdOrderBySectionCodeAsc(semesterId);
        return sections.stream().map(this::toSectionDto).toList();
    }

    private List<CourseDto> enrichCourses(List<Course> courses, Long semesterId) {
        if (courses.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        Map<Long, List<PrerequisiteRefDto>> prereqMap = prerequisiteRepository.findByCourseIdIn(courseIds).stream()
                .collect(Collectors.groupingBy(
                        cp -> cp.getCourse().getId(),
                        Collectors.mapping(cp -> PrerequisiteRefDto.builder()
                                .courseId(cp.getPrerequisiteCourse().getId())
                                .courseCode(cp.getPrerequisiteCourse().getCourseCode())
                                .courseName(cp.getPrerequisiteCourse().getCourseName())
                                .build(), Collectors.toList())));

        Map<Long, Long> sectionCounts = semesterId != null
                ? courseSectionRepository.findBySemesterIdOrderBySectionCodeAsc(semesterId).stream()
                        .filter(s -> "OPEN".equalsIgnoreCase(s.getSectionStatus()))
                        .collect(Collectors.groupingBy(s -> s.getCourse().getId(), Collectors.counting()))
                : Map.of();

        return courses.stream()
                .map(c -> CourseDto.builder()
                        .id(c.getId())
                        .courseCode(c.getCourseCode())
                        .courseName(c.getCourseName())
                        .credits(c.getCredits())
                        .description(c.getDescription())
                        .isActive(c.getIsActive())
                        .openSectionsCount(semesterId != null ? sectionCounts.getOrDefault(c.getId(), 0L).intValue() : null)
                        .prerequisites(prereqMap.getOrDefault(c.getId(), List.of()))
                        .build())
                .toList();
    }

    public SemesterDto toSemesterDto(Semester s) {
        return SemesterDto.builder()
                .id(s.getId())
                .semesterCode(s.getSemesterCode())
                .semesterName(s.getSemesterName())
                .academicYear(s.getAcademicYear())
                .termNo(s.getTermNo())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .registrationStartAt(s.getRegistrationStartAt())
                .registrationEndAt(s.getRegistrationEndAt())
                .semesterStatus(s.getSemesterStatus())
                .build();
    }

    public CourseSectionDto toSectionDto(CourseSection cs) {
        return CourseSectionDto.builder()
                .id(cs.getId())
                .semesterId(cs.getSemester().getId())
                .courseId(cs.getCourse().getId())
                .courseCode(cs.getCourse().getCourseCode())
                .courseName(cs.getCourse().getCourseName())
                .credits(cs.getCourse().getCredits())
                .sectionCode(cs.getSectionCode())
                .instructorName(cs.getInstructorName())
                .maxCapacity(cs.getMaxCapacity())
                .currentEnrollment(cs.getCurrentEnrollment())
                .sectionStatus(cs.getSectionStatus())
                .meetings(cs.getMeetings().stream().map(this::toMeetingDto).toList())
                .build();
    }

    private SectionMeetingDto toMeetingDto(SectionMeeting m) {
        return SectionMeetingDto.builder()
                .id(m.getId())
                .weekday(m.getWeekday())
                .startPeriod(m.getStartPeriod())
                .endPeriod(m.getEndPeriod())
                .startTime(m.getStartTime())
                .endTime(m.getEndTime())
                .roomCode(m.getRoom() != null ? m.getRoom().getRoomCode() : null)
                .roomName(m.getRoom() != null ? m.getRoom().getRoomName() : null)
                .campusName(m.getRoom() != null && m.getRoom().getCampus() != null
                        ? m.getRoom().getCampus().getCampusName()
                        : null)
                .build();
    }
}
