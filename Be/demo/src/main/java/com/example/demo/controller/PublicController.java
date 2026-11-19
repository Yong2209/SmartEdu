package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;

    // US01: Xem thông tin giới thiệu & tình trạng hệ thống (Không cần đăng nhập)
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> getSystemOverview() {
        return ApiResponse.ok("Hệ thống SmartEdu sẵn sàng hoạt động", Map.of(
                "systemName", "SmartEdu - Nền tảng Hỗ trợ Học tập AI & Tối ưu Thời khóa biểu Đại học",
                "version", "2.0.0",
                "features", new String[]{
                        "US08-US15: Trích xuất học liệu PDF/DOCX và tạo câu hỏi ôn tập tự động bằng AI kèm trích dẫn nguồn gốc",
                        "US16-US19: Làm bài kiểm tra trực tuyến theo thời gian thực, chấm điểm & ôn tập câu sai",
                        "US20-US34: Thuật toán Backtracking xếp thời khóa biểu tối ưu đa tiêu chí & kiểm tra tiên quyết",
                        "US35-US39: Quản trị danh mục học phần Excel, phân quyền RBAC & giám sát nhật ký vận hành"
                },
                "totalActiveCourses", courseRepository.count(),
                "totalSemesters", semesterRepository.count()
        ));
    }
}
