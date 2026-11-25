package com.smartedu.modules.course_registration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.course_registration.entity.RegistrationPlanCourse;

public interface RegistrationPlanCourseRepository extends JpaRepository<RegistrationPlanCourse, Long> {
    Optional<RegistrationPlanCourse> findByRegistrationPlanIdAndCourseId(Long registrationPlanId, Long courseId);
}
