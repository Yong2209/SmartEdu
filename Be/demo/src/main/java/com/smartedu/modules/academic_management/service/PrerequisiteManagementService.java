package com.smartedu.modules.academic_management.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.academic_management.dto.PrerequisiteDtos;
import com.smartedu.modules.academic_management.entity.Course;
import com.smartedu.modules.academic_management.entity.CoursePrerequisite;
import com.smartedu.modules.academic_management.repository.CoursePrerequisiteRepository;
import com.smartedu.modules.academic_management.repository.CourseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrerequisiteManagementService {

    private final CourseRepository courseRepository;
    private final CoursePrerequisiteRepository prerequisiteRepository;

    @Transactional
    public PrerequisiteDtos.PrerequisiteResponse create(
            PrerequisiteDtos.CreateRequest request
    ) {
        if (request.courseId().equals(request.prerequisiteCourseId())) {
            throw new BusinessException(
                    "Một học phần không thể là điều kiện tiên quyết của chính nó.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Course course = getCourse(request.courseId());
        Course prerequisite = getCourse(request.prerequisiteCourseId());

        if (prerequisiteRepository.existsByCourseIdAndPrerequisiteCourseId(
                course.getId(),
                prerequisite.getId()
        )) {
            throw new BusinessException(
                    "Điều kiện tiên quyết đã tồn tại.",
                    HttpStatus.CONFLICT
            );
        }

        if (wouldCreateCycle(course.getId(), prerequisite.getId())) {
            throw new BusinessException(
                    "Không thể thêm điều kiện này vì sẽ tạo chu trình tiên quyết.",
                    HttpStatus.BAD_REQUEST
            );
        }

        CoursePrerequisite entity = CoursePrerequisite.builder()
                .course(course)
                .prerequisiteCourse(prerequisite)
                .build();

        return toResponse(prerequisiteRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<PrerequisiteDtos.PrerequisiteResponse> getByCourse(
            Long courseId
    ) {
        getCourse(courseId);

        return prerequisiteRepository.findByCourseId(courseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long courseId, Long prerequisiteCourseId) {
        if (!prerequisiteRepository.existsByCourseIdAndPrerequisiteCourseId(
                courseId,
                prerequisiteCourseId
        )) {
            throw new BusinessException(
                    "Không tìm thấy điều kiện tiên quyết.",
                    HttpStatus.NOT_FOUND
            );
        }

        prerequisiteRepository.deleteByCourseIdAndPrerequisiteCourseId(
                courseId,
                prerequisiteCourseId
        );
    }

    private boolean wouldCreateCycle(
            Long courseId,
            Long prerequisiteCourseId
    ) {
        // Nếu prerequisite đã phụ thuộc trực tiếp hoặc gián tiếp vào course,
        // thêm course -> prerequisite sẽ tạo chu trình.
        return canReach(
                prerequisiteCourseId,
                courseId,
                new HashSet<>()
        );
    }

    private boolean canReach(
            Long currentCourseId,
            Long targetCourseId,
            Set<Long> visited
    ) {
        if (currentCourseId.equals(targetCourseId)) {
            return true;
        }

        if (!visited.add(currentCourseId)) {
            return false;
        }

        List<CoursePrerequisite> edges =
                prerequisiteRepository.findByCourseId(currentCourseId);

        for (CoursePrerequisite edge : edges) {
            if (canReach(
                    edge.getPrerequisiteCourse().getId(),
                    targetCourseId,
                    visited
            )) {
                return true;
            }
        }

        return false;
    }

    private Course getCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "Không tìm thấy học phần.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private PrerequisiteDtos.PrerequisiteResponse toResponse(
            CoursePrerequisite entity
    ) {
        return new PrerequisiteDtos.PrerequisiteResponse(
                entity.getId(),
                entity.getCourse().getId(),
                entity.getCourse().getCourseCode(),
                entity.getCourse().getCourseName(),
                entity.getPrerequisiteCourse().getId(),
                entity.getPrerequisiteCourse().getCourseCode(),
                entity.getPrerequisiteCourse().getCourseName()
        );
    }
}
