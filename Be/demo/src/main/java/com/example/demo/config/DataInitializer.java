package com.example.demo.config;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SemesterRepository semesterRepository;
    private final CampusRepository campusRepository;
    private final RoomRepository roomRepository;
    private final CourseRepository courseRepository;
    private final CourseSectionRepository sectionRepository;
    private final CoursePrerequisiteRepository prerequisiteRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        Role studentRole = roleRepository.findByCode("STUDENT")
                .orElseGet(() -> roleRepository.save(Role.builder().code("STUDENT").name("Sinh viên").description("Tài khoản sinh viên").build()));
        Role adminRole = roleRepository.findByCode("ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().code("ADMIN").name("Quản trị viên").description("Quản trị hệ thống").build()));

        // Tạo tài khoản Admin & Student mẫu (Password: SmartEdu@123)
        if (!userRepository.existsByEmail("admin@smartedu.edu.vn")) {
            userRepository.save(User.builder()
                    .role(adminRole)
                    .username("admin")
                    .fullName("Quản trị viên SmartEdu")
                    .email("admin@smartedu.edu.vn")
                    .passwordHash(passwordEncoder.encode("SmartEdu@123"))
                    .accountStatus("ACTIVE")
                    .emailVerifiedAt(LocalDateTime.now())
                    .build());
        }

        if (!userRepository.existsByEmail("sinhvien@smartedu.edu.vn")) {
            userRepository.save(User.builder()
                    .role(studentRole)
                    .studentCode("2021600123")
                    .username("sinhvien01")
                    .fullName("Nguyễn Văn An")
                    .email("sinhvien@smartedu.edu.vn")
                    .passwordHash(passwordEncoder.encode("SmartEdu@123"))
                    .accountStatus("ACTIVE")
                    .emailVerifiedAt(LocalDateTime.now())
                    .build());
        }

        // Khởi tạo Học kỳ, Cơ sở, Phòng học, Học phần & Lớp học phần mẫu phục vụ Demo TKB
        Semester sem = semesterRepository.findBySemesterCode("2025-2026-2")
                .orElseGet(() -> semesterRepository.save(Semester.builder()
                        .semesterCode("2025-2026-2")
                        .semesterName("Học kỳ II năm học 2025 - 2026")
                        .academicYear("2025-2026")
                        .termNo((short) 2)
                        .startDate(LocalDate.of(2026, 1, 12))
                        .endDate(LocalDate.of(2026, 5, 30))
                        .build()));

        Campus cs1 = campusRepository.findByCampusCode("CS1")
                .orElseGet(() -> campusRepository.save(Campus.builder().campusCode("CS1").campusName("Cơ sở 1 - Hà Nội").address("298 Cầu Diễn").build()));
        Campus cs2 = campusRepository.findByCampusCode("CS2")
                .orElseGet(() -> campusRepository.save(Campus.builder().campusCode("CS2").campusName("Cơ sở 2 - Hà Nam").address("Phủ Lý").build()));

        Room rA101 = roomRepository.findByCampusIdAndRoomCode(cs1.getId(), "A1-301")
                .orElseGet(() -> roomRepository.save(Room.builder().campus(cs1).roomCode("A1-301").capacity(65).build()));
        Room rB202 = roomRepository.findByCampusIdAndRoomCode(cs2.getId(), "B2-202")
                .orElseGet(() -> roomRepository.save(Room.builder().campus(cs2).roomCode("B2-202").capacity(60).build()));

        if (courseRepository.count() == 0) {
            Course c1 = courseRepository.save(Course.builder().courseCode("IT6001").courseName("Công nghệ Phần mềm").credits(new BigDecimal("3.0")).isActive(true).build());
            Course c2 = courseRepository.save(Course.builder().courseCode("IT6002").courseName("Cơ sở dữ liệu Phân tán").credits(new BigDecimal("3.0")).isActive(true).build());
            Course c3 = courseRepository.save(Course.builder().courseCode("IT6003").courseName("Trí tuệ Nhân tạo & Học máy").credits(new BigDecimal("4.0")).isActive(true).build());
            Course c4 = courseRepository.save(Course.builder().courseCode("IT6004").courseName("An toàn Bảo mật Hệ thống").credits(new BigDecimal("3.0")).isActive(true).build());
            Course c5 = courseRepository.save(Course.builder().courseCode("IT6005").courseName("Phát triển Ứng dụng Web Hiện đại").credits(new BigDecimal("3.0")).isActive(true).build());

            // Ràng buộc tiên quyết mẫu: IT6003 yêu cầu IT6001
            if (!prerequisiteRepository.existsByCourseIdAndPrerequisiteCourseId(c3.getId(), c1.getId())) {
                prerequisiteRepository.save(CoursePrerequisite.builder().course(c3).prerequisiteCourse(c1).build());
            }

            createSection(sem, c1, "IT6001.1", rA101, (short) 2, (short) 1, (short) 3);
            createSection(sem, c1, "IT6001.2", rA101, (short) 4, (short) 7, (short) 9);

            createSection(sem, c2, "IT6002.1", rA101, (short) 2, (short) 4, (short) 6);
            createSection(sem, c2, "IT6002.2", rB202, (short) 5, (short) 1, (short) 3);

            createSection(sem, c3, "IT6003.1", rA101, (short) 3, (short) 1, (short) 4);
            createSection(sem, c3, "IT6003.2", rA101, (short) 7, (short) 1, (short) 4);

            createSection(sem, c4, "IT6004.1", rA101, (short) 4, (short) 1, (short) 3);
            createSection(sem, c4, "IT6004.2", rA101, (short) 6, (short) 7, (short) 9);

            createSection(sem, c5, "IT6005.1", rA101, (short) 3, (short) 7, (short) 9);
            createSection(sem, c5, "IT6005.2", rA101, (short) 6, (short) 1, (short) 3);
        }
        log.info("SmartEdu DataInitializer completed.");
    }

    private void createSection(Semester sem, Course course, String code, Room room,
                               short day, short start, short end) {
        CourseSection cs = CourseSection.builder()
                .semester(sem)
                .course(course)
                .sectionCode(code)
                .maxCapacity(60)
                .sectionStatus("OPEN")
                .build();
        cs.getMeetings().add(SectionMeeting.builder()
                .courseSection(cs)
                .room(room)
                .weekday(day)
                .startPeriod(start)
                .endPeriod(end)
                .build());
        sectionRepository.save(cs);
    }
}