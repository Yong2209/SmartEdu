-- =============================================================
-- SmartEdu - FULL DEMO SEED DATA for MySQL 8.4
-- Target database: smartedu_demo
-- Requires: the corrected 44-table schema has already been imported.
-- Run this script ONCE on an empty SmartEdu database.
-- It inserts fictional development data into all 44 tables.
-- =============================================================

USE smartedu_demo;
SET NAMES utf8mb4;

-- The schema normally inserts these two roles. INSERT IGNORE makes
-- this seed also work if the role rows are missing.
INSERT IGNORE INTO roles (code, name, description) VALUES
('STUDENT', 'Sinh viên', 'Tài khoản người học'),
('ADMIN', 'Quản trị viên', 'Tài khoản quản trị hệ thống');

SET @ROLE_STUDENT = (SELECT id FROM roles WHERE code = 'STUDENT' LIMIT 1);
SET @ROLE_ADMIN   = (SELECT id FROM roles WHERE code = 'ADMIN' LIMIT 1);

START TRANSACTION;

-- -------------------------------------------------------------
-- 1. USERS (fictional demo accounts)
-- Demo password for all 3 accounts: Demo@12345
-- BCrypt hash; never use these demo credentials in production.
-- -------------------------------------------------------------
INSERT INTO users (
  id, role_id, student_code, username, full_name, email, phone,
  address, gender, password_hash, avatar_url, email_verified_at,
  account_status, last_login_at
) VALUES
(
  9001, @ROLE_STUDENT, 'DEMO2026001', 'student.demo', 'Nguyễn Minh An',
  'student1@smartedu.test', '0900000001', 'Đà Nẵng, Việt Nam', 'MALE',
  '$2a$10$qty6JmHBRLmAPAu7hKjQk.xZKy4c.MGQCG8RUg5pah3dWwbU9JPSa',
  '/assets/avatars/student-demo-1.png', NOW(6), 'ACTIVE', NOW(6)
),
(
  9002, @ROLE_STUDENT, 'DEMO2026002', 'student.demo2', 'Trần Gia Linh',
  'student2@smartedu.test', '0900000002', 'Đà Nẵng, Việt Nam', 'FEMALE',
  '$2a$10$qty6JmHBRLmAPAu7hKjQk.xZKy4c.MGQCG8RUg5pah3dWwbU9JPSa',
  '/assets/avatars/student-demo-2.png', NOW(6), 'ACTIVE', NOW(6)
),
(
  9003, @ROLE_ADMIN, NULL, 'admin.demo', 'Quản trị viên mẫu',
  'admin@smartedu.test', '0900000003', 'Đà Nẵng, Việt Nam', 'OTHER',
  '$2a$10$qty6JmHBRLmAPAu7hKjQk.xZKy4c.MGQCG8RUg5pah3dWwbU9JPSa',
  '/assets/avatars/admin-demo.png', NOW(6), 'ACTIVE', NOW(6)
);

-- -------------------------------------------------------------
-- 2. AUTHENTICATION / SECURITY SAMPLE RECORDS
-- Only hashes are stored. These are illustrative records, not live tokens.
-- -------------------------------------------------------------
INSERT INTO email_otps (
  id, user_id, email, otp_hash, expires_at, used_at, attempt_count
) VALUES
(9001, 9001, 'student1@smartedu.test', SHA2('DEMO-OTP-STUDENT1', 256), DATE_ADD(NOW(6), INTERVAL 1 DAY), NULL, 0),
(9002, 9002, 'student2@smartedu.test', SHA2('DEMO-OTP-STUDENT2', 256), DATE_ADD(NOW(6), INTERVAL 1 DAY), NULL, 0);

INSERT INTO password_reset_tokens (
  id, user_id, token_hash, expires_at, used_at
) VALUES
(9001, 9001, SHA2('DEMO-RESET-TOKEN-STUDENT1', 256), DATE_ADD(NOW(6), INTERVAL 7 DAY), NULL);

INSERT INTO user_sessions (
  id, user_id, token_hash, ip_address, user_agent,
  last_activity_at, expires_at, revoked_at
) VALUES
(9001, 9001, SHA2('DEMO-SESSION-STUDENT1', 256), '127.0.0.1',
 'SmartEdu Demo Browser', NOW(6), DATE_ADD(NOW(6), INTERVAL 30 DAY), NULL),
(9002, 9003, SHA2('DEMO-SESSION-ADMIN', 256), '127.0.0.1',
 'SmartEdu Admin Demo Browser', NOW(6), DATE_ADD(NOW(6), INTERVAL 30 DAY), NULL);

-- -------------------------------------------------------------
-- 3. LEARNING MATERIALS AND EXTRACTED CONTENT
-- -------------------------------------------------------------
INSERT INTO learning_materials (
  id, user_id, title, original_file_name, storage_path,
  file_extension, mime_type, file_size_bytes, processing_status,
  uploaded_at, created_at, updated_at
) VALUES
(9101, 9001, 'Tài liệu nhập môn Cơ sở dữ liệu', 'co-so-du-lieu-demo.pdf',
 'uploads/demo/co-so-du-lieu-demo.pdf', 'pdf', 'application/pdf', 524288,
 'COMPLETED', NOW(6), NOW(6), NOW(6)),
(9102, 9002, 'Tài liệu Java hướng đối tượng', 'java-oop-demo.pdf',
 'uploads/demo/java-oop-demo.pdf', 'pdf', 'application/pdf', 786432,
 'COMPLETED', NOW(6), NOW(6), NOW(6));

INSERT INTO material_contents (
  id, learning_material_id, extracted_text, page_count,
  extraction_status, extraction_version, extracted_at
) VALUES
(9201, 9101,
 'Tài liệu giới thiệu cơ sở dữ liệu quan hệ, bảng, khóa chính, khóa ngoại và câu lệnh SQL cơ bản.',
 18, 'COMPLETED', 'demo-extractor-1.0', NOW(6)),
(9202, 9102,
 'Tài liệu giới thiệu lập trình hướng đối tượng, lớp, đối tượng, đóng gói, kế thừa và đa hình trong Java.',
 24, 'COMPLETED', 'demo-extractor-1.0', NOW(6));

-- -------------------------------------------------------------
-- 4. AI QUESTION GENERATION
-- -------------------------------------------------------------
INSERT INTO question_generation_requests (
  id, user_id, learning_material_id, content_scope, scope_description,
  question_type, question_count, difficulty, status,
  requested_at, completed_at, error_message
) VALUES
(9301, 9001, 9101, 'FULL_DOCUMENT', 'Toàn bộ tài liệu cơ sở dữ liệu',
 'MULTIPLE_CHOICE', 3, 'MIXED', 'COMPLETED',
 DATE_SUB(NOW(6), INTERVAL 2 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY), NULL),
(9302, 9002, 9102, 'FULL_DOCUMENT', 'Toàn bộ tài liệu Java OOP',
 'MULTIPLE_CHOICE', 3, 'MIXED', 'COMPLETED',
 DATE_SUB(NOW(6), INTERVAL 1 DAY), DATE_SUB(NOW(6), INTERVAL 1 DAY), NULL);

INSERT INTO question_sets (
  id, user_id, generation_request_id, source_material_id,
  title, description, set_status, saved_at, created_at, updated_at
) VALUES
(9401, 9001, 9301, 9101, 'Bộ câu hỏi Cơ sở dữ liệu cơ bản',
 'Bộ câu hỏi mẫu được tạo từ tài liệu cơ sở dữ liệu.', 'PUBLISHED', NOW(6), NOW(6), NOW(6)),
(9402, 9002, 9302, 9102, 'Bộ câu hỏi Java OOP',
 'Bộ câu hỏi mẫu về lập trình hướng đối tượng Java.', 'PUBLISHED', NOW(6), NOW(6), NOW(6));

-- -------------------------------------------------------------
-- 5. QUESTIONS (3 questions per question set)
-- -------------------------------------------------------------
INSERT INTO questions (
  id, question_set_id, source_material_id, replaces_question_id,
  question_type, content, explanation, difficulty,
  reference_excerpt, reference_page, display_order, question_status
) VALUES
(9501, 9401, 9101, NULL, 'MULTIPLE_CHOICE',
 'Cơ sở dữ liệu (Database) được hiểu chính xác nhất là gì?',
 'Cơ sở dữ liệu là tập hợp dữ liệu có tổ chức, được lưu trữ và quản lý để dễ dàng truy cập.',
 'EASY', 'Cơ sở dữ liệu là tập hợp dữ liệu có tổ chức.', 1, 1, 'ACTIVE'),
(9502, 9401, 9101, NULL, 'MULTIPLE_CHOICE',
 'Khóa chính (Primary Key) trong một bảng có mục đích gì?',
 'Khóa chính dùng để định danh duy nhất mỗi bản ghi trong bảng.',
 'EASY', 'Mỗi bảng quan hệ thường có một khóa chính.', 3, 2, 'ACTIVE'),
(9503, 9401, 9101, NULL, 'MULTIPLE_CHOICE',
 'Khóa ngoại (Foreign Key) dùng để làm gì?',
 'Khóa ngoại tạo liên kết và đảm bảo tham chiếu giữa các bảng.',
 'MEDIUM', 'Khóa ngoại tham chiếu đến khóa chính hoặc khóa duy nhất của bảng khác.', 5, 3, 'ACTIVE'),
(9504, 9402, 9102, NULL, 'MULTIPLE_CHOICE',
 'Tính đóng gói (Encapsulation) trong OOP có ý nghĩa gì?',
 'Đóng gói che giấu trạng thái bên trong và cung cấp phương thức truy cập phù hợp.',
 'EASY', 'Encapsulation là một trong bốn tính chất cơ bản của OOP.', 4, 1, 'ACTIVE'),
(9505, 9402, 9102, NULL, 'MULTIPLE_CHOICE',
 'Tính kế thừa (Inheritance) cho phép điều gì?',
 'Lớp con có thể kế thừa thuộc tính và phương thức từ lớp cha.',
 'EASY', 'Inheritance hỗ trợ tái sử dụng và mở rộng mã nguồn.', 8, 2, 'ACTIVE'),
(9506, 9402, 9102, NULL, 'MULTIPLE_CHOICE',
 'Đa hình (Polymorphism) trong Java cho phép điều gì?',
 'Một giao diện hoặc kiểu tham chiếu có thể biểu diễn nhiều hình thái đối tượng.',
 'MEDIUM', 'Polymorphism cho phép cùng một lời gọi phương thức có hành vi khác nhau.', 11, 3, 'ACTIVE');

-- -------------------------------------------------------------
-- 6. QUESTION OPTIONS (4 options per question; one correct answer)
-- -------------------------------------------------------------
INSERT INTO question_options (
  id, question_id, option_label, content, is_correct, display_order
) VALUES
-- Question 9501: correct A
(9601, 9501, 'A', 'Tập hợp dữ liệu có tổ chức và có thể truy cập, quản lý.', 1, 1),
(9602, 9501, 'B', 'Một ngôn ngữ lập trình.', 0, 2),
(9603, 9501, 'C', 'Một thiết bị phần cứng.', 0, 3),
(9604, 9501, 'D', 'Một giao diện thiết kế website.', 0, 4),
-- Question 9502: correct B
(9605, 9502, 'A', 'Lưu hình ảnh của bảng.', 0, 1),
(9606, 9502, 'B', 'Định danh duy nhất mỗi bản ghi trong bảng.', 1, 2),
(9607, 9502, 'C', 'Cho phép mọi giá trị bị trùng lặp.', 0, 3),
(9608, 9502, 'D', 'Mã hóa toàn bộ dữ liệu.', 0, 4),
-- Question 9503: correct C
(9609, 9503, 'A', 'Sắp xếp dữ liệu theo thứ tự bảng chữ cái.', 0, 1),
(9610, 9503, 'B', 'Tự động tạo giao diện người dùng.', 0, 2),
(9611, 9503, 'C', 'Tạo liên kết và đảm bảo tính toàn vẹn tham chiếu giữa các bảng.', 1, 3),
(9612, 9503, 'D', 'Thay thế hoàn toàn khóa chính.', 0, 4),
-- Question 9504: correct A
(9613, 9504, 'A', 'Ẩn chi tiết cài đặt bên trong và kiểm soát truy cập thông qua phương thức.', 1, 1),
(9614, 9504, 'B', 'Cho phép một lớp kế thừa nhiều lớp cha.', 0, 2),
(9615, 9504, 'C', 'Biến mọi thuộc tính thành biến toàn cục.', 0, 3),
(9616, 9504, 'D', 'Chỉ dùng để tăng tốc độ thực thi.', 0, 4),
-- Question 9505: correct D
(9617, 9505, 'A', 'Xóa bỏ quan hệ giữa các lớp.', 0, 1),
(9618, 9505, 'B', 'Chỉ cho phép sao chép dữ liệu thủ công.', 0, 2),
(9619, 9505, 'C', 'Ngăn lớp con sử dụng phương thức của lớp cha.', 0, 3),
(9620, 9505, 'D', 'Cho phép lớp con kế thừa và mở rộng thuộc tính, phương thức của lớp cha.', 1, 4),
-- Question 9506: correct B
(9621, 9506, 'A', 'Một phương thức chỉ có thể chạy một lần.', 0, 1),
(9622, 9506, 'B', 'Cùng một kiểu tham chiếu có thể gọi hành vi khác nhau tùy đối tượng thực tế.', 1, 2),
(9623, 9506, 'C', 'Mọi lớp phải có cùng một constructor.', 0, 3),
(9624, 9506, 'D', 'Không cần sử dụng kế thừa hoặc interface.', 0, 4);

-- -------------------------------------------------------------
-- 7. QUIZZES, ATTEMPTS AND ANSWERS
-- -------------------------------------------------------------
INSERT INTO quiz_tests (
  id, user_id, question_set_id, title, duration_minutes,
  total_score, test_status, created_at
) VALUES
(9701, 9001, 9401, 'Kiểm tra Cơ sở dữ liệu cơ bản', 30, 100.00, 'ACTIVE', NOW(6)),
(9702, 9002, 9402, 'Kiểm tra Java OOP', 30, 100.00, 'ACTIVE', NOW(6));

INSERT INTO quiz_test_questions (
  id, quiz_test_id, question_id, display_order, max_score
) VALUES
(9801, 9701, 9501, 1, 33.34),
(9802, 9701, 9502, 2, 33.33),
(9803, 9701, 9503, 3, 33.33),
(9804, 9702, 9504, 1, 33.34),
(9805, 9702, 9505, 2, 33.33),
(9806, 9702, 9506, 3, 33.33);

INSERT INTO quiz_attempts (
  id, quiz_test_id, user_id, started_at, submitted_at,
  duration_seconds, attempt_status, total_score,
  correct_count, incorrect_count, unanswered_count
) VALUES
(9901, 9701, 9001, DATE_SUB(NOW(6), INTERVAL 3 HOUR), DATE_SUB(NOW(6), INTERVAL 2 HOUR),
  720, 'SUBMITTED', 66.67, 2, 1, 0),
(9902, 9702, 9002, DATE_SUB(NOW(6), INTERVAL 5 HOUR), DATE_SUB(NOW(6), INTERVAL 4 HOUR),
  680, 'SUBMITTED', 100.00, 3, 0, 0);

INSERT INTO quiz_attempt_answers (
  id, quiz_attempt_id, quiz_test_question_id, selected_option_id,
  answer_text, is_correct, awarded_score, question_content_snapshot,
  correct_answer_snapshot, explanation_snapshot, answered_at
) VALUES
(10001, 9901, 9801, 9601, NULL, 1, 33.34,
 'Cơ sở dữ liệu (Database) được hiểu chính xác nhất là gì?',
 'A. Tập hợp dữ liệu có tổ chức và có thể truy cập, quản lý.',
 'Cơ sở dữ liệu là tập hợp dữ liệu có tổ chức.', DATE_SUB(NOW(6), INTERVAL 2 HOUR)),
(10002, 9901, 9802, 9606, NULL, 1, 33.33,
 'Khóa chính (Primary Key) trong một bảng có mục đích gì?',
 'B. Định danh duy nhất mỗi bản ghi trong bảng.',
 'Khóa chính dùng để định danh duy nhất mỗi bản ghi.', DATE_SUB(NOW(6), INTERVAL 2 HOUR)),
(10003, 9901, 9803, 9609, NULL, 0, 0.00,
 'Khóa ngoại (Foreign Key) dùng để làm gì?',
 'C. Tạo liên kết và đảm bảo tính toàn vẹn tham chiếu giữa các bảng.',
 'Khóa ngoại tạo liên kết và đảm bảo tham chiếu giữa các bảng.', DATE_SUB(NOW(6), INTERVAL 2 HOUR)),
(10004, 9902, 9804, 9613, NULL, 1, 33.34,
 'Tính đóng gói (Encapsulation) trong OOP có ý nghĩa gì?',
 'A. Ẩn chi tiết cài đặt bên trong và kiểm soát truy cập thông qua phương thức.',
 'Đóng gói che giấu trạng thái bên trong.', DATE_SUB(NOW(6), INTERVAL 4 HOUR)),
(10005, 9902, 9805, 9620, NULL, 1, 33.33,
 'Tính kế thừa (Inheritance) cho phép điều gì?',
 'D. Cho phép lớp con kế thừa và mở rộng thuộc tính, phương thức của lớp cha.',
 'Lớp con có thể kế thừa thuộc tính và phương thức từ lớp cha.', DATE_SUB(NOW(6), INTERVAL 4 HOUR)),
(10006, 9902, 9806, 9622, NULL, 1, 33.33,
 'Đa hình (Polymorphism) trong Java cho phép điều gì?',
 'B. Cùng một kiểu tham chiếu có thể gọi hành vi khác nhau tùy đối tượng thực tế.',
 'Một giao diện hoặc kiểu tham chiếu có thể biểu diễn nhiều hình thái đối tượng.', DATE_SUB(NOW(6), INTERVAL 4 HOUR));

-- -------------------------------------------------------------
-- 8. REVIEW SESSIONS
-- -------------------------------------------------------------
INSERT INTO review_sessions (
  id, user_id, source_quiz_attempt_id, source_question_set_id,
  session_status, started_at, ended_at
) VALUES
(10101, 9001, 9901, 9401, 'COMPLETED', DATE_SUB(NOW(6), INTERVAL 1 HOUR), NOW(6)),
(10102, 9002, 9902, 9402, 'COMPLETED', DATE_SUB(NOW(6), INTERVAL 2 HOUR), NOW(6));

INSERT INTO review_session_items (
  id, review_session_id, question_id, source_attempt_answer_id,
  review_status, last_is_correct, last_reviewed_at
) VALUES
(10201, 10101, 9503, 10003, 'MASTERED', 1, NOW(6)),
(10202, 10101, 9501, 10001, 'MASTERED', 1, NOW(6)),
(10203, 10102, 9505, 10005, 'MASTERED', 1, NOW(6));

INSERT INTO review_attempts (
  id, review_session_item_id, selected_option_id,
  answer_text, is_correct, attempted_at
) VALUES
(10301, 10201, 9611, NULL, 1, NOW(6)),
(10302, 10202, 9601, NULL, 1, NOW(6)),
(10303, 10203, 9620, NULL, 1, NOW(6));

-- -------------------------------------------------------------
-- 9. ACADEMIC CATALOGUE
-- -------------------------------------------------------------
INSERT INTO semesters (
  id, semester_code, semester_name, academic_year, term_no,
  start_date, end_date, registration_start_at,
  registration_end_at, semester_status
) VALUES
(11001, 'DEMO-HK1-2026', 'Học kỳ 1 năm học 2026-2027', '2026-2027', 1,
 '2026-08-24', '2026-12-31', '2026-07-20 08:00:00',
 '2026-08-20 23:59:59', 'ACTIVE'),
(11002, 'DEMO-HK2-2026', 'Học kỳ 2 năm học 2026-2027', '2026-2027', 2,
 '2027-01-11', '2027-05-31', '2026-12-01 08:00:00',
 '2027-01-08 23:59:59', 'PLANNING');

INSERT INTO courses (
  id, course_code, course_name, credits, description, is_active
) VALUES
(11101, 'DEMO-CS101', 'Nhập môn Công nghệ thông tin', 3.0,
 'Kiến thức nền tảng về máy tính, phần mềm và công nghệ thông tin.', 1),
(11102, 'DEMO-CS102', 'Lập trình căn bản', 3.0,
 'Tư duy thuật toán, biến, kiểu dữ liệu, cấu trúc điều khiển và hàm.', 1),
(11103, 'DEMO-CS201', 'Cơ sở dữ liệu', 3.0,
 'Mô hình quan hệ, thiết kế cơ sở dữ liệu và ngôn ngữ SQL.', 1),
(11104, 'DEMO-CS202', 'Lập trình hướng đối tượng', 3.0,
 'Lớp, đối tượng, đóng gói, kế thừa, đa hình và Java.', 1);

INSERT INTO campuses (id, campus_code, campus_name, address)
VALUES (11201, 'DEMO-MAIN', 'Cơ sở SmartEdu mẫu', 'Đà Nẵng, Việt Nam');

INSERT INTO rooms (id, campus_id, room_code, room_name, capacity)
VALUES
(11301, 11201, 'DEMO-A101', 'Phòng học A101', 50),
(11302, 11201, 'DEMO-LAB02', 'Phòng máy LAB02', 35);

-- -------------------------------------------------------------
-- 10. IMPORTS AND STUDENT ACADEMIC RECORDS
-- -------------------------------------------------------------
INSERT INTO registration_imports (
  id, user_id, semester_id, import_type, file_name,
  storage_path, file_format, import_status,
  total_rows, valid_rows, error_rows, processed_at
) VALUES
(11401, 9001, 11001, 'STUDENT_REGISTRATION', 'demo-registration-student1.xlsx',
 'uploads/demo/imports/demo-registration-student1.xlsx', 'XLSX', 'COMPLETED',
 5, 4, 1, NOW(6)),
(11402, 9002, 11001, 'STUDENT_REGISTRATION', 'demo-registration-student2.xlsx',
 'uploads/demo/imports/demo-registration-student2.xlsx', 'XLSX', 'COMPLETED',
 3, 3, 0, NOW(6));

INSERT INTO course_catalog_imports (
  id, admin_user_id, semester_id, file_name, storage_path,
  import_status, total_rows, success_rows, error_rows, completed_at
) VALUES
(11403, 9003, 11001, 'demo-course-catalog.xlsx',
 'uploads/demo/imports/demo-course-catalog.xlsx', 'COMPLETED',
 4, 4, 0, NOW(6));

INSERT INTO student_course_records (
  id, user_id, course_id, semester_id, source_import_id,
  grade, result_status, completed_at
) VALUES
(11501, 9001, 11101, 11001, 11401, 8.50, 'PASSED', NOW(6)),
(11502, 9001, 11102, 11001, 11401, 7.75, 'PASSED', NOW(6)),
(11503, 9002, 11101, 11001, 11402, 9.00, 'PASSED', NOW(6));

-- -------------------------------------------------------------
-- 11. COURSE SECTIONS AND MEETINGS
-- -------------------------------------------------------------
INSERT INTO course_sections (
  id, semester_id, course_id, section_code, instructor_name,
  max_capacity, current_enrollment, section_status
) VALUES
(11601, 11001, 11101, 'DEMO-CS101-01', 'Giảng viên mẫu Nguyễn An', 50, 25, 'OPEN'),
(11602, 11001, 11103, 'DEMO-CS201-01', 'Giảng viên mẫu Trần Bình', 40, 18, 'OPEN'),
(11603, 11001, 11104, 'DEMO-CS202-01', 'Giảng viên mẫu Lê Cường', 35, 20, 'OPEN');

INSERT INTO section_meetings (
  id, course_section_id, weekday, start_period, end_period,
  start_time, end_time, room_id
) VALUES
(11701, 11601, 2, 1, 3, '07:00:00', '09:30:00', 11301),
(11702, 11602, 4, 4, 6, '13:00:00', '15:30:00', 11302),
(11703, 11603, 6, 1, 3, '07:00:00', '09:30:00', 11301);

INSERT INTO course_prerequisites (
  id, course_id, prerequisite_course_id, created_by
) VALUES
(11801, 11103, 11102, 9003),
(11802, 11104, 11102, 9003);

-- -------------------------------------------------------------
-- 12. IMPORT ERROR DETAILS
-- `row_number` is quoted because it is a MySQL keyword/function name.
-- -------------------------------------------------------------
INSERT INTO registration_import_errors (
  id, registration_import_id, `row_number`, field_name,
  error_code, error_message, row_data
) VALUES
(11901, 11401, 5, 'course_code', 'COURSE_NOT_FOUND',
 'Mã học phần trong tệp mẫu không tồn tại trong danh mục học phần.',
 JSON_OBJECT('student_code', 'DEMO2026001', 'course_code', 'DEMO-UNKNOWN'));

-- -------------------------------------------------------------
-- 13. REGISTRATION PLANS AND PREFERENCES
-- -------------------------------------------------------------
INSERT INTO registration_plans (
  id, user_id, semester_id, source_import_id,
  plan_name, plan_status
) VALUES
(12001, 9001, 11001, 11401, 'Kế hoạch học kỳ 1 - Minh An', 'DRAFT'),
(12002, 9002, 11001, 11402, 'Kế hoạch học kỳ 1 - Gia Linh', 'DRAFT');

INSERT INTO registration_plan_courses (
  id, registration_plan_id, course_id, selection_source
) VALUES
(12101, 12001, 11103, 'MANUAL'),
(12102, 12001, 11104, 'AI_RECOMMENDED'),
(12103, 12002, 11101, 'MANUAL'),
(12104, 12002, 11102, 'MANUAL');

INSERT INTO registration_preferences (
  id, registration_plan_id, min_credits, max_credits,
  preferred_shift, avoid_saturday, compact_days, minimize_gaps,
  preferred_free_days, max_gap_periods
) VALUES
(12201, 12001, 6.0, 18.0, 'MORNING', 1, 1, 1, JSON_ARRAY(1, 3, 5, 7), 2),
(12202, 12002, 6.0, 18.0, 'AFTERNOON', 0, 1, 1, JSON_ARRAY(1, 2, 7), 3);

-- -------------------------------------------------------------
-- 14. TIMETABLE GENERATION AND OPTIONS
-- -------------------------------------------------------------
INSERT INTO schedule_generations (
  id, registration_plan_id, generation_no, algorithm_version,
  generation_status, total_candidates, feasible_candidates,
  started_at, completed_at, error_message
) VALUES
(12301, 12001, 1, 'smartedu-scheduler-demo-1.0', 'COMPLETED', 12, 9,
 DATE_SUB(NOW(6), INTERVAL 30 MINUTE), DATE_SUB(NOW(6), INTERVAL 29 MINUTE), NULL),
(12302, 12002, 1, 'smartedu-scheduler-demo-1.0', 'COMPLETED', 8, 5,
 DATE_SUB(NOW(6), INTERVAL 20 MINUTE), DATE_SUB(NOW(6), INTERVAL 19 MINUTE), NULL);

-- Insert parent options before the child option because parent_option_id
-- is a self-referencing foreign key.
INSERT INTO schedule_options (
  id, schedule_generation_id, parent_option_id, option_name,
  option_rank, total_credits, total_score, is_feasible, option_type
) VALUES
(12401, 12301, NULL, 'Thời khóa biểu cân bằng - phương án 1', 1, 6.0, 92.5000, 1, 'GENERATED'),
(12403, 12302, NULL, 'Thời khóa biểu Gia Linh - phương án 1', 1, 6.0, 90.0000, 1, 'GENERATED');

INSERT INTO schedule_options (
  id, schedule_generation_id, parent_option_id, option_name,
  option_rank, total_credits, total_score, is_feasible, option_type
) VALUES
(12402, 12301, 12401, 'Thời khóa biểu linh hoạt - phương án 2', 2, 6.0, 86.2500, 1, 'ALTERNATIVE');

INSERT INTO schedule_option_sections (
  id, schedule_option_id, course_section_id
) VALUES
(12501, 12401, 11601),
(12502, 12401, 11602),
(12503, 12402, 11601),
(12504, 12402, 11603),
(12505, 12403, 11602),
(12506, 12403, 11603);

INSERT INTO schedule_option_evaluations (
  id, schedule_option_id, criterion_code, criterion_name,
  score_value, weight_value, explanation
) VALUES
(12601, 12401, 'COMPACT_DAYS', 'Mức độ tập trung lịch học', 95.0000, 0.3500,
 'Các buổi học được phân bố tương đối tập trung trong tuần.'),
(12602, 12401, 'PREFERENCE_MATCH', 'Mức độ phù hợp nguyện vọng', 90.0000, 0.4000,
 'Phù hợp với ca học và ngày nghỉ ưu tiên.'),
(12603, 12402, 'GAP_MINIMIZATION', 'Tối thiểu hóa thời gian trống', 82.0000, 0.2500,
 'Có một khoảng trống giữa các buổi học.'),
(12604, 12403, 'PREFERENCE_MATCH', 'Mức độ phù hợp nguyện vọng', 90.0000, 0.4000,
 'Phương án phù hợp với các học phần đã chọn.');

INSERT INTO schedule_option_warnings (
  id, schedule_option_id, warning_type, severity, warning_message
) VALUES
(12701, 12402, 'LONG_GAP', 'INFO',
 'Phương án có khoảng trống tương đối dài giữa hai buổi học.');

INSERT INTO saved_schedules (
  id, user_id, schedule_option_id, display_name
) VALUES
(12801, 9001, 12401, 'Thời khóa biểu học kỳ 2 của Minh An'),
(12802, 9002, 12403, 'Thời khóa biểu học kỳ 2 của Gia Linh');

INSERT INTO schedule_exports (
  id, user_id, schedule_option_id, export_format,
  file_path, export_status, file_size_bytes, exported_at
) VALUES
(12901, 9001, 12401, 'PDF', 'exports/demo/minh-an-timetable.pdf', 'COMPLETED', 153600, NOW(6)),
(12902, 9002, 12403, 'PNG', 'exports/demo/gia-linh-timetable.png', 'COMPLETED', 98304, NOW(6));

-- -------------------------------------------------------------
-- 15. CATALOG IMPORT ERRORS AND SYSTEM LOGS
-- -------------------------------------------------------------
INSERT INTO course_catalog_import_errors (
  id, course_catalog_import_id, `row_number`, field_name,
  error_code, error_message, row_data
) VALUES
(13001, 11403, 4, 'credits', 'INVALID_CREDITS',
 'Số tín chỉ trong dòng mẫu phải lớn hơn 0.',
 JSON_OBJECT('course_code', 'DEMO-CS999', 'credits', 0));

INSERT INTO email_logs (
  id, user_id, recipient_email, email_type, provider,
  provider_message_id, send_status, error_message, sent_at
) VALUES
(13101, 9001, 'student1@smartedu.test', 'EMAIL_VERIFICATION', 'DEMO_PROVIDER',
 'demo-message-9001', 'SENT', NULL, NOW(6)),
(13102, 9002, 'student2@smartedu.test', 'WELCOME', 'DEMO_PROVIDER',
 'demo-message-9002', 'SENT', NULL, NOW(6));

INSERT INTO ai_call_logs (
  id, user_id, question_generation_request_id, operation_type,
  provider, model_name, response_time_ms, prompt_tokens,
  completion_tokens, total_tokens, call_status, error_code,
  error_message, called_at
) VALUES
(13201, 9001, 9301, 'QUESTION_GENERATION', 'DEMO_PROVIDER', 'demo-llm',
 1850, 1200, 450, 1650, 'SUCCESS', NULL, NULL, NOW(6)),
(13202, 9002, 9302, 'QUESTION_GENERATION', 'DEMO_PROVIDER', 'demo-llm',
 2100, 1350, 510, 1860, 'SUCCESS', NULL, NULL, NOW(6));

INSERT INTO file_processing_logs (
  id, learning_material_id, registration_import_id,
  course_catalog_import_id, processing_type, processing_status,
  duration_ms, error_message, processed_at
) VALUES
(13301, 9101, NULL, NULL, 'MATERIAL_EXTRACTION', 'COMPLETED', 1250, NULL, NOW(6)),
(13302, NULL, 11401, NULL, 'REGISTRATION_IMPORT', 'COMPLETED', 980, NULL, NOW(6)),
(13303, NULL, NULL, 11403, 'COURSE_CATALOG_IMPORT', 'COMPLETED', 1120, NULL, NOW(6));

INSERT INTO activity_logs (
  id, actor_user_id, target_user_id, action_code,
  entity_type, entity_id, detail, ip_address
) VALUES
(13401, 9003, 9001, 'DEMO_USER_REVIEWED', 'users', 9001,
 JSON_OBJECT('source', 'seed', 'description', 'Demo admin activity'), '127.0.0.1'),
(13402, 9001, NULL, 'DEMO_QUIZ_SUBMITTED', 'quiz_attempts', 9901,
 JSON_OBJECT('score', 66.67, 'source', 'seed'), '127.0.0.1'),
(13403, 9002, NULL, 'DEMO_SCHEDULE_SAVED', 'saved_schedules', 12802,
 JSON_OBJECT('optionId', 12403, 'source', 'seed'), '127.0.0.1');

COMMIT;

-- -------------------------------------------------------------
-- 16. VERIFICATION: every one of the 44 tables is populated
-- -------------------------------------------------------------
SELECT 'roles' AS table_name, COUNT(*) AS sample_rows FROM roles;
SELECT 'users' AS table_name, COUNT(*) AS sample_rows FROM users WHERE id IN (9001,9002,9003);
SELECT 'email_otps' AS table_name, COUNT(*) AS sample_rows FROM email_otps WHERE id IN (9001,9002);
SELECT 'password_reset_tokens' AS table_name, COUNT(*) AS sample_rows FROM password_reset_tokens WHERE id = 9001;
SELECT 'user_sessions' AS table_name, COUNT(*) AS sample_rows FROM user_sessions WHERE id IN (9001,9002);
SELECT 'learning_materials' AS table_name, COUNT(*) AS sample_rows FROM learning_materials WHERE id IN (9101,9102);
SELECT 'material_contents' AS table_name, COUNT(*) AS sample_rows FROM material_contents WHERE id IN (9201,9202);
SELECT 'question_generation_requests' AS table_name, COUNT(*) AS sample_rows FROM question_generation_requests WHERE id IN (9301,9302);
SELECT 'question_sets' AS table_name, COUNT(*) AS sample_rows FROM question_sets WHERE id IN (9401,9402);
SELECT 'questions' AS table_name, COUNT(*) AS sample_rows FROM questions WHERE id BETWEEN 9501 AND 9506;
SELECT 'question_options' AS table_name, COUNT(*) AS sample_rows FROM question_options WHERE id BETWEEN 9601 AND 9624;
SELECT 'quiz_tests' AS table_name, COUNT(*) AS sample_rows FROM quiz_tests WHERE id IN (9701,9702);
SELECT 'quiz_test_questions' AS table_name, COUNT(*) AS sample_rows FROM quiz_test_questions WHERE id BETWEEN 9801 AND 9806;
SELECT 'quiz_attempts' AS table_name, COUNT(*) AS sample_rows FROM quiz_attempts WHERE id IN (9901,9902);
SELECT 'quiz_attempt_answers' AS table_name, COUNT(*) AS sample_rows FROM quiz_attempt_answers WHERE id BETWEEN 10001 AND 10006;
SELECT 'review_sessions' AS table_name, COUNT(*) AS sample_rows FROM review_sessions WHERE id IN (10101,10102);
SELECT 'review_session_items' AS table_name, COUNT(*) AS sample_rows FROM review_session_items WHERE id BETWEEN 10201 AND 10203;
SELECT 'review_attempts' AS table_name, COUNT(*) AS sample_rows FROM review_attempts WHERE id BETWEEN 10301 AND 10303;
SELECT 'semesters' AS table_name, COUNT(*) AS sample_rows FROM semesters WHERE id IN (11001,11002);
SELECT 'courses' AS table_name, COUNT(*) AS sample_rows FROM courses WHERE id BETWEEN 11101 AND 11104;
SELECT 'campuses' AS table_name, COUNT(*) AS sample_rows FROM campuses WHERE id = 11201;
SELECT 'rooms' AS table_name, COUNT(*) AS sample_rows FROM rooms WHERE id IN (11301,11302);
SELECT 'registration_imports' AS table_name, COUNT(*) AS sample_rows FROM registration_imports WHERE id IN (11401,11402);
SELECT 'course_catalog_imports' AS table_name, COUNT(*) AS sample_rows FROM course_catalog_imports WHERE id = 11403;
SELECT 'student_course_records' AS table_name, COUNT(*) AS sample_rows FROM student_course_records WHERE id BETWEEN 11501 AND 11503;
SELECT 'course_sections' AS table_name, COUNT(*) AS sample_rows FROM course_sections WHERE id BETWEEN 11601 AND 11603;
SELECT 'section_meetings' AS table_name, COUNT(*) AS sample_rows FROM section_meetings WHERE id BETWEEN 11701 AND 11703;
SELECT 'course_prerequisites' AS table_name, COUNT(*) AS sample_rows FROM course_prerequisites WHERE id IN (11801,11802);
SELECT 'registration_import_errors' AS table_name, COUNT(*) AS sample_rows FROM registration_import_errors WHERE id = 11901;
SELECT 'registration_plans' AS table_name, COUNT(*) AS sample_rows FROM registration_plans WHERE id IN (12001,12002);
SELECT 'registration_plan_courses' AS table_name, COUNT(*) AS sample_rows FROM registration_plan_courses WHERE id BETWEEN 12101 AND 12104;
SELECT 'registration_preferences' AS table_name, COUNT(*) AS sample_rows FROM registration_preferences WHERE id IN (12201,12202);
SELECT 'schedule_generations' AS table_name, COUNT(*) AS sample_rows FROM schedule_generations WHERE id IN (12301,12302);
SELECT 'schedule_options' AS table_name, COUNT(*) AS sample_rows FROM schedule_options WHERE id BETWEEN 12401 AND 12403;
SELECT 'schedule_option_sections' AS table_name, COUNT(*) AS sample_rows FROM schedule_option_sections WHERE id BETWEEN 12501 AND 12506;
SELECT 'schedule_option_evaluations' AS table_name, COUNT(*) AS sample_rows FROM schedule_option_evaluations WHERE id BETWEEN 12601 AND 12604;
SELECT 'schedule_option_warnings' AS table_name, COUNT(*) AS sample_rows FROM schedule_option_warnings WHERE id = 12701;
SELECT 'saved_schedules' AS table_name, COUNT(*) AS sample_rows FROM saved_schedules WHERE id IN (12801,12802);
SELECT 'schedule_exports' AS table_name, COUNT(*) AS sample_rows FROM schedule_exports WHERE id IN (12901,12902);
SELECT 'course_catalog_import_errors' AS table_name, COUNT(*) AS sample_rows FROM course_catalog_import_errors WHERE id = 13001;
SELECT 'email_logs' AS table_name, COUNT(*) AS sample_rows FROM email_logs WHERE id IN (13101,13102);
SELECT 'ai_call_logs' AS table_name, COUNT(*) AS sample_rows FROM ai_call_logs WHERE id IN (13201,13202);
SELECT 'file_processing_logs' AS table_name, COUNT(*) AS sample_rows FROM file_processing_logs WHERE id BETWEEN 13301 AND 13303;
SELECT 'activity_logs' AS table_name, COUNT(*) AS sample_rows FROM activity_logs WHERE id BETWEEN 13401 AND 13403;

-- Sample accounts (same demo password for all): Demo@12345
SELECT id, username, full_name, email, role_id, account_status
FROM users WHERE id IN (9001,9002,9003) ORDER BY id;
