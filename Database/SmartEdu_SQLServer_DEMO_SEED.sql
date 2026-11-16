/* =============================================================
   SmartEdu - SQL Server DEMO SEED DATA
   Target database: SmartEdu_Connect_Retry
   Requires: SmartEdu_SQLServer_SETUP_RETRY.sql đã chạy thành công.
   Inserts fictional sample data into the 44-table schema.
   Chỉ chạy MỘT LẦN trên database chưa có dữ liệu demo.
   Tài khoản demo dùng chung mật khẩu: Demo@12345
   Không dùng tài khoản/mật khẩu này trên môi trường thật.
   ============================================================= */

USE [SmartEdu_Connect_Retry];
SET NOCOUNT ON;
SET XACT_ABORT ON;

-- Roles are inserted by SmartEdu_SQLServer_SETUP_RETRY.sql.
DECLARE @ROLE_STUDENT BIGINT;
DECLARE @ROLE_ADMIN BIGINT;
SELECT @ROLE_STUDENT = id FROM dbo.roles WHERE code = N'STUDENT';
SELECT @ROLE_ADMIN = id FROM dbo.roles WHERE code = N'ADMIN';

IF @ROLE_STUDENT IS NULL OR @ROLE_ADMIN IS NULL
BEGIN
    RAISERROR(N'Không tìm thấy vai trò STUDENT hoặc ADMIN trong dbo.roles. Hãy chạy schema trước.', 16, 1);
    RETURN;
END;

-- Prevent accidental duplicate insertion when the seed is run again.
IF EXISTS (SELECT 1 FROM dbo.users WHERE id IN (9001, 9002, 9003)
           OR username IN (N'student.demo', N'student.demo2', N'admin.demo'))
BEGIN
    RAISERROR(N'Dữ liệu mẫu SmartEdu đã tồn tại. Không chạy seed lần thứ hai trên cùng database.', 16, 1);
    RETURN;
END;

BEGIN TRY
    BEGIN TRANSACTION;

-- -------------------------------------------------------------
-- 1. USERS (fictional demo accounts)
-- Demo password for all 3 accounts: Demo@12345
-- BCrypt hash; never use these demo credentials in production.
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.users ON;
INSERT INTO dbo.users (
  id, role_id, student_code, username, full_name, email, phone,
  address, gender, password_hash, avatar_url, email_verified_at,
  account_status, last_login_at
) VALUES
(
  9001, @ROLE_STUDENT, N'DEMO2026001', N'student.demo', N'Nguyễn Minh An',
  N'student1@smartedu.test', N'0900000001', N'Đà Nẵng, Việt Nam', N'MALE',
  N'$2a$10$qty6JmHBRLmAPAu7hKjQk.xZKy4c.MGQCG8RUg5pah3dWwbU9JPSa',
  N'/assets/avatars/student-demo-1.png', SYSDATETIME(), N'ACTIVE', SYSDATETIME()
),
(
  9002, @ROLE_STUDENT, N'DEMO2026002', N'student.demo2', N'Trần Gia Linh',
  N'student2@smartedu.test', N'0900000002', N'Đà Nẵng, Việt Nam', N'FEMALE',
  N'$2a$10$qty6JmHBRLmAPAu7hKjQk.xZKy4c.MGQCG8RUg5pah3dWwbU9JPSa',
  N'/assets/avatars/student-demo-2.png', SYSDATETIME(), N'ACTIVE', SYSDATETIME()
),
(
  9003, @ROLE_ADMIN, NULL, N'admin.demo', N'Quản trị viên mẫu',
  N'admin@smartedu.test', N'0900000003', N'Đà Nẵng, Việt Nam', N'OTHER',
  N'$2a$10$qty6JmHBRLmAPAu7hKjQk.xZKy4c.MGQCG8RUg5pah3dWwbU9JPSa',
  N'/assets/avatars/admin-demo.png', SYSDATETIME(), N'ACTIVE', SYSDATETIME()
);
SET IDENTITY_INSERT dbo.users OFF;

-- -------------------------------------------------------------
-- 2. AUTHENTICATION / SECURITY SAMPLE RECORDS
-- Only hashes are stored. These are illustrative records, not live tokens.
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.email_otps ON;
INSERT INTO dbo.email_otps (
  id, user_id, email, otp_hash, expires_at, used_at, attempt_count
) VALUES
(9001, 9001, N'student1@smartedu.test', CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', CONVERT(VARCHAR(128), N'DEMO-OTP-STUDENT1')), 2), DATEADD(DAY, 1, SYSDATETIME()), NULL, 0),
(9002, 9002, N'student2@smartedu.test', CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', CONVERT(VARCHAR(128), N'DEMO-OTP-STUDENT2')), 2), DATEADD(DAY, 1, SYSDATETIME()), NULL, 0);
SET IDENTITY_INSERT dbo.email_otps OFF;

SET IDENTITY_INSERT dbo.password_reset_tokens ON;
INSERT INTO dbo.password_reset_tokens (
  id, user_id, token_hash, expires_at, used_at
) VALUES
(9001, 9001, CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', CONVERT(VARCHAR(128), N'DEMO-RESET-TOKEN-STUDENT1')), 2), DATEADD(DAY, 7, SYSDATETIME()), NULL);
SET IDENTITY_INSERT dbo.password_reset_tokens OFF;

SET IDENTITY_INSERT dbo.user_sessions ON;
INSERT INTO dbo.user_sessions (
  id, user_id, token_hash, ip_address, user_agent,
  last_activity_at, expires_at, revoked_at
) VALUES
(9001, 9001, CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', CONVERT(VARCHAR(128), N'DEMO-SESSION-STUDENT1')), 2), N'127.0.0.1',
 N'SmartEdu Demo Browser', SYSDATETIME(), DATEADD(DAY, 30, SYSDATETIME()), NULL),
(9002, 9003, CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', CONVERT(VARCHAR(128), N'DEMO-SESSION-ADMIN')), 2), N'127.0.0.1',
 N'SmartEdu Admin Demo Browser', SYSDATETIME(), DATEADD(DAY, 30, SYSDATETIME()), NULL);
SET IDENTITY_INSERT dbo.user_sessions OFF;

-- -------------------------------------------------------------
-- 3. LEARNING MATERIALS AND EXTRACTED CONTENT
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.learning_materials ON;
INSERT INTO dbo.learning_materials (
  id, user_id, title, original_file_name, storage_path,
  file_extension, mime_type, file_size_bytes, processing_status,
  uploaded_at, created_at, updated_at
) VALUES
(9101, 9001, N'Tài liệu nhập môn Cơ sở dữ liệu', N'co-so-du-lieu-demo.pdf',
 N'uploads/demo/co-so-du-lieu-demo.pdf', N'pdf', N'application/pdf', 524288,
 N'COMPLETED', SYSDATETIME(), SYSDATETIME(), SYSDATETIME()),
(9102, 9002, N'Tài liệu Java hướng đối tượng', N'java-oop-demo.pdf',
 N'uploads/demo/java-oop-demo.pdf', N'pdf', N'application/pdf', 786432,
 N'COMPLETED', SYSDATETIME(), SYSDATETIME(), SYSDATETIME());
SET IDENTITY_INSERT dbo.learning_materials OFF;

SET IDENTITY_INSERT dbo.material_contents ON;
INSERT INTO dbo.material_contents (
  id, learning_material_id, extracted_text, page_count,
  extraction_status, extraction_version, extracted_at
) VALUES
(9201, 9101,
 N'Tài liệu giới thiệu cơ sở dữ liệu quan hệ, bảng, khóa chính, khóa ngoại và câu lệnh SQL cơ bản.',
 18, N'COMPLETED', N'demo-extractor-1.0', SYSDATETIME()),
(9202, 9102,
 N'Tài liệu giới thiệu lập trình hướng đối tượng, lớp, đối tượng, đóng gói, kế thừa và đa hình trong Java.',
 24, N'COMPLETED', N'demo-extractor-1.0', SYSDATETIME());
SET IDENTITY_INSERT dbo.material_contents OFF;

-- -------------------------------------------------------------
-- 4. AI QUESTION GENERATION
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.question_generation_requests ON;
INSERT INTO dbo.question_generation_requests (
  id, user_id, learning_material_id, content_scope, scope_description,
  question_type, question_count, difficulty, status,
  requested_at, completed_at, error_message
) VALUES
(9301, 9001, 9101, N'FULL_DOCUMENT', N'Toàn bộ tài liệu cơ sở dữ liệu',
 N'MULTIPLE_CHOICE', 3, N'MIXED', N'COMPLETED',
 DATEADD(DAY, -2, SYSDATETIME()), DATEADD(DAY, -2, SYSDATETIME()), NULL),
(9302, 9002, 9102, N'FULL_DOCUMENT', N'Toàn bộ tài liệu Java OOP',
 N'MULTIPLE_CHOICE', 3, N'MIXED', N'COMPLETED',
 DATEADD(DAY, -1, SYSDATETIME()), DATEADD(DAY, -1, SYSDATETIME()), NULL);
SET IDENTITY_INSERT dbo.question_generation_requests OFF;

SET IDENTITY_INSERT dbo.question_sets ON;
INSERT INTO dbo.question_sets (
  id, user_id, generation_request_id, source_material_id,
  title, description, set_status, saved_at, created_at, updated_at
) VALUES
(9401, 9001, 9301, 9101, N'Bộ câu hỏi Cơ sở dữ liệu cơ bản',
 N'Bộ câu hỏi mẫu được tạo từ tài liệu cơ sở dữ liệu.', N'PUBLISHED', SYSDATETIME(), SYSDATETIME(), SYSDATETIME()),
(9402, 9002, 9302, 9102, N'Bộ câu hỏi Java OOP',
 N'Bộ câu hỏi mẫu về lập trình hướng đối tượng Java.', N'PUBLISHED', SYSDATETIME(), SYSDATETIME(), SYSDATETIME());
SET IDENTITY_INSERT dbo.question_sets OFF;

-- -------------------------------------------------------------
-- 5. QUESTIONS (3 questions per question set)
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.questions ON;
INSERT INTO dbo.questions (
  id, question_set_id, source_material_id, replaces_question_id,
  question_type, content, explanation, difficulty,
  reference_excerpt, reference_page, display_order, question_status
) VALUES
(9501, 9401, 9101, NULL, N'MULTIPLE_CHOICE',
 N'Cơ sở dữ liệu (Database) được hiểu chính xác nhất là gì?',
 N'Cơ sở dữ liệu là tập hợp dữ liệu có tổ chức, được lưu trữ và quản lý để dễ dàng truy cập.',
 N'EASY', N'Cơ sở dữ liệu là tập hợp dữ liệu có tổ chức.', 1, 1, N'ACTIVE'),
(9502, 9401, 9101, NULL, N'MULTIPLE_CHOICE',
 N'Khóa chính (Primary Key) trong một bảng có mục đích gì?',
 N'Khóa chính dùng để định danh duy nhất mỗi bản ghi trong bảng.',
 N'EASY', N'Mỗi bảng quan hệ thường có một khóa chính.', 3, 2, N'ACTIVE'),
(9503, 9401, 9101, NULL, N'MULTIPLE_CHOICE',
 N'Khóa ngoại (Foreign Key) dùng để làm gì?',
 N'Khóa ngoại tạo liên kết và đảm bảo tham chiếu giữa các bảng.',
 N'MEDIUM', N'Khóa ngoại tham chiếu đến khóa chính hoặc khóa duy nhất của bảng khác.', 5, 3, N'ACTIVE'),
(9504, 9402, 9102, NULL, N'MULTIPLE_CHOICE',
 N'Tính đóng gói (Encapsulation) trong OOP có ý nghĩa gì?',
 N'Đóng gói che giấu trạng thái bên trong và cung cấp phương thức truy cập phù hợp.',
 N'EASY', N'Encapsulation là một trong bốn tính chất cơ bản của OOP.', 4, 1, N'ACTIVE'),
(9505, 9402, 9102, NULL, N'MULTIPLE_CHOICE',
 N'Tính kế thừa (Inheritance) cho phép điều gì?',
 N'Lớp con có thể kế thừa thuộc tính và phương thức từ lớp cha.',
 N'EASY', N'Inheritance hỗ trợ tái sử dụng và mở rộng mã nguồn.', 8, 2, N'ACTIVE'),
(9506, 9402, 9102, NULL, N'MULTIPLE_CHOICE',
 N'Đa hình (Polymorphism) trong Java cho phép điều gì?',
 N'Một giao diện hoặc kiểu tham chiếu có thể biểu diễn nhiều hình thái đối tượng.',
 N'MEDIUM', N'Polymorphism cho phép cùng một lời gọi phương thức có hành vi khác nhau.', 11, 3, N'ACTIVE');
SET IDENTITY_INSERT dbo.questions OFF;

-- -------------------------------------------------------------
-- 6. QUESTION OPTIONS (4 options per question; one correct answer)
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.question_options ON;
INSERT INTO dbo.question_options (
  id, question_id, option_label, content, is_correct, display_order
) VALUES
-- Question 9501: correct A
(9601, 9501, N'A', N'Tập hợp dữ liệu có tổ chức và có thể truy cập, quản lý.', 1, 1),
(9602, 9501, N'B', N'Một ngôn ngữ lập trình.', 0, 2),
(9603, 9501, N'C', N'Một thiết bị phần cứng.', 0, 3),
(9604, 9501, N'D', N'Một giao diện thiết kế website.', 0, 4),
-- Question 9502: correct B
(9605, 9502, N'A', N'Lưu hình ảnh của bảng.', 0, 1),
(9606, 9502, N'B', N'Định danh duy nhất mỗi bản ghi trong bảng.', 1, 2),
(9607, 9502, N'C', N'Cho phép mọi giá trị bị trùng lặp.', 0, 3),
(9608, 9502, N'D', N'Mã hóa toàn bộ dữ liệu.', 0, 4),
-- Question 9503: correct C
(9609, 9503, N'A', N'Sắp xếp dữ liệu theo thứ tự bảng chữ cái.', 0, 1),
(9610, 9503, N'B', N'Tự động tạo giao diện người dùng.', 0, 2),
(9611, 9503, N'C', N'Tạo liên kết và đảm bảo tính toàn vẹn tham chiếu giữa các bảng.', 1, 3),
(9612, 9503, N'D', N'Thay thế hoàn toàn khóa chính.', 0, 4),
-- Question 9504: correct A
(9613, 9504, N'A', N'Ẩn chi tiết cài đặt bên trong và kiểm soát truy cập thông qua phương thức.', 1, 1),
(9614, 9504, N'B', N'Cho phép một lớp kế thừa nhiều lớp cha.', 0, 2),
(9615, 9504, N'C', N'Biến mọi thuộc tính thành biến toàn cục.', 0, 3),
(9616, 9504, N'D', N'Chỉ dùng để tăng tốc độ thực thi.', 0, 4),
-- Question 9505: correct D
(9617, 9505, N'A', N'Xóa bỏ quan hệ giữa các lớp.', 0, 1),
(9618, 9505, N'B', N'Chỉ cho phép sao chép dữ liệu thủ công.', 0, 2),
(9619, 9505, N'C', N'Ngăn lớp con sử dụng phương thức của lớp cha.', 0, 3),
(9620, 9505, N'D', N'Cho phép lớp con kế thừa và mở rộng thuộc tính, phương thức của lớp cha.', 1, 4),
-- Question 9506: correct B
(9621, 9506, N'A', N'Một phương thức chỉ có thể chạy một lần.', 0, 1),
(9622, 9506, N'B', N'Cùng một kiểu tham chiếu có thể gọi hành vi khác nhau tùy đối tượng thực tế.', 1, 2),
(9623, 9506, N'C', N'Mọi lớp phải có cùng một constructor.', 0, 3),
(9624, 9506, N'D', N'Không cần sử dụng kế thừa hoặc interface.', 0, 4);
SET IDENTITY_INSERT dbo.question_options OFF;

-- -------------------------------------------------------------
-- 7. QUIZZES, ATTEMPTS AND ANSWERS
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.quiz_tests ON;
INSERT INTO dbo.quiz_tests (
  id, user_id, question_set_id, title, duration_minutes,
  total_score, test_status, created_at
) VALUES
(9701, 9001, 9401, N'Kiểm tra Cơ sở dữ liệu cơ bản', 30, 100.00, N'ACTIVE', SYSDATETIME()),
(9702, 9002, 9402, N'Kiểm tra Java OOP', 30, 100.00, N'ACTIVE', SYSDATETIME());
SET IDENTITY_INSERT dbo.quiz_tests OFF;

SET IDENTITY_INSERT dbo.quiz_test_questions ON;
INSERT INTO dbo.quiz_test_questions (
  id, quiz_test_id, question_id, display_order, max_score
) VALUES
(9801, 9701, 9501, 1, 33.34),
(9802, 9701, 9502, 2, 33.33),
(9803, 9701, 9503, 3, 33.33),
(9804, 9702, 9504, 1, 33.34),
(9805, 9702, 9505, 2, 33.33),
(9806, 9702, 9506, 3, 33.33);
SET IDENTITY_INSERT dbo.quiz_test_questions OFF;

SET IDENTITY_INSERT dbo.quiz_attempts ON;
INSERT INTO dbo.quiz_attempts (
  id, quiz_test_id, user_id, started_at, submitted_at,
  duration_seconds, attempt_status, total_score,
  correct_count, incorrect_count, unanswered_count
) VALUES
(9901, 9701, 9001, DATEADD(HOUR, -3, SYSDATETIME()), DATEADD(HOUR, -2, SYSDATETIME()),
  720, N'SUBMITTED', 66.67, 2, 1, 0),
(9902, 9702, 9002, DATEADD(HOUR, -5, SYSDATETIME()), DATEADD(HOUR, -4, SYSDATETIME()),
  680, N'SUBMITTED', 100.00, 3, 0, 0);
SET IDENTITY_INSERT dbo.quiz_attempts OFF;

SET IDENTITY_INSERT dbo.quiz_attempt_answers ON;
INSERT INTO dbo.quiz_attempt_answers (
  id, quiz_attempt_id, quiz_test_question_id, selected_option_id,
  answer_text, is_correct, awarded_score, question_content_snapshot,
  correct_answer_snapshot, explanation_snapshot, answered_at
) VALUES
(10001, 9901, 9801, 9601, NULL, 1, 33.34,
 N'Cơ sở dữ liệu (Database) được hiểu chính xác nhất là gì?',
 N'A. Tập hợp dữ liệu có tổ chức và có thể truy cập, quản lý.',
 N'Cơ sở dữ liệu là tập hợp dữ liệu có tổ chức.', DATEADD(HOUR, -2, SYSDATETIME())),
(10002, 9901, 9802, 9606, NULL, 1, 33.33,
 N'Khóa chính (Primary Key) trong một bảng có mục đích gì?',
 N'B. Định danh duy nhất mỗi bản ghi trong bảng.',
 N'Khóa chính dùng để định danh duy nhất mỗi bản ghi.', DATEADD(HOUR, -2, SYSDATETIME())),
(10003, 9901, 9803, 9609, NULL, 0, 0.00,
 N'Khóa ngoại (Foreign Key) dùng để làm gì?',
 N'C. Tạo liên kết và đảm bảo tính toàn vẹn tham chiếu giữa các bảng.',
 N'Khóa ngoại tạo liên kết và đảm bảo tham chiếu giữa các bảng.', DATEADD(HOUR, -2, SYSDATETIME())),
(10004, 9902, 9804, 9613, NULL, 1, 33.34,
 N'Tính đóng gói (Encapsulation) trong OOP có ý nghĩa gì?',
 N'A. Ẩn chi tiết cài đặt bên trong và kiểm soát truy cập thông qua phương thức.',
 N'Đóng gói che giấu trạng thái bên trong.', DATEADD(HOUR, -4, SYSDATETIME())),
(10005, 9902, 9805, 9620, NULL, 1, 33.33,
 N'Tính kế thừa (Inheritance) cho phép điều gì?',
 N'D. Cho phép lớp con kế thừa và mở rộng thuộc tính, phương thức của lớp cha.',
 N'Lớp con có thể kế thừa thuộc tính và phương thức từ lớp cha.', DATEADD(HOUR, -4, SYSDATETIME())),
(10006, 9902, 9806, 9622, NULL, 1, 33.33,
 N'Đa hình (Polymorphism) trong Java cho phép điều gì?',
 N'B. Cùng một kiểu tham chiếu có thể gọi hành vi khác nhau tùy đối tượng thực tế.',
 N'Một giao diện hoặc kiểu tham chiếu có thể biểu diễn nhiều hình thái đối tượng.', DATEADD(HOUR, -4, SYSDATETIME()));
SET IDENTITY_INSERT dbo.quiz_attempt_answers OFF;

-- -------------------------------------------------------------
-- 8. REVIEW SESSIONS
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.review_sessions ON;
INSERT INTO dbo.review_sessions (
  id, user_id, source_quiz_attempt_id, source_question_set_id,
  session_status, started_at, ended_at
) VALUES
(10101, 9001, 9901, 9401, N'COMPLETED', DATEADD(HOUR, -1, SYSDATETIME()), SYSDATETIME()),
(10102, 9002, 9902, 9402, N'COMPLETED', DATEADD(HOUR, -2, SYSDATETIME()), SYSDATETIME());
SET IDENTITY_INSERT dbo.review_sessions OFF;

SET IDENTITY_INSERT dbo.review_session_items ON;
INSERT INTO dbo.review_session_items (
  id, review_session_id, question_id, source_attempt_answer_id,
  review_status, last_is_correct, last_reviewed_at
) VALUES
(10201, 10101, 9503, 10003, N'MASTERED', 1, SYSDATETIME()),
(10202, 10101, 9501, 10001, N'MASTERED', 1, SYSDATETIME()),
(10203, 10102, 9505, 10005, N'MASTERED', 1, SYSDATETIME());
SET IDENTITY_INSERT dbo.review_session_items OFF;

SET IDENTITY_INSERT dbo.review_attempts ON;
INSERT INTO dbo.review_attempts (
  id, review_session_item_id, selected_option_id,
  answer_text, is_correct, attempted_at
) VALUES
(10301, 10201, 9611, NULL, 1, SYSDATETIME()),
(10302, 10202, 9601, NULL, 1, SYSDATETIME()),
(10303, 10203, 9620, NULL, 1, SYSDATETIME());
SET IDENTITY_INSERT dbo.review_attempts OFF;

-- -------------------------------------------------------------
-- 9. ACADEMIC CATALOGUE
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.semesters ON;
INSERT INTO dbo.semesters (
  id, semester_code, semester_name, academic_year, term_no,
  start_date, end_date, registration_start_at,
  registration_end_at, semester_status
) VALUES
(11001, N'DEMO-HK1-2026', N'Học kỳ 1 năm học 2026-2027', N'2026-2027', 1,
 N'2026-08-24', N'2026-12-31', N'2026-07-20 08:00:00',
 N'2026-08-20 23:59:59', N'ACTIVE'),
(11002, N'DEMO-HK2-2026', N'Học kỳ 2 năm học 2026-2027', N'2026-2027', 2,
 N'2027-01-11', N'2027-05-31', N'2026-12-01 08:00:00',
 N'2027-01-08 23:59:59', N'PLANNING');
SET IDENTITY_INSERT dbo.semesters OFF;

SET IDENTITY_INSERT dbo.courses ON;
INSERT INTO dbo.courses (
  id, course_code, course_name, credits, description, is_active
) VALUES
(11101, N'DEMO-CS101', N'Nhập môn Công nghệ thông tin', 3.0,
 N'Kiến thức nền tảng về máy tính, phần mềm và công nghệ thông tin.', 1),
(11102, N'DEMO-CS102', N'Lập trình căn bản', 3.0,
 N'Tư duy thuật toán, biến, kiểu dữ liệu, cấu trúc điều khiển và hàm.', 1),
(11103, N'DEMO-CS201', N'Cơ sở dữ liệu', 3.0,
 N'Mô hình quan hệ, thiết kế cơ sở dữ liệu và ngôn ngữ SQL.', 1),
(11104, N'DEMO-CS202', N'Lập trình hướng đối tượng', 3.0,
 N'Lớp, đối tượng, đóng gói, kế thừa, đa hình và Java.', 1);
SET IDENTITY_INSERT dbo.courses OFF;

SET IDENTITY_INSERT dbo.campuses ON;
INSERT INTO dbo.campuses (id, campus_code, campus_name, address) VALUES (11201, N'DEMO-MAIN', N'Cơ sở SmartEdu mẫu', N'Đà Nẵng, Việt Nam');
SET IDENTITY_INSERT dbo.campuses OFF;

SET IDENTITY_INSERT dbo.rooms ON;
INSERT INTO dbo.rooms (id, campus_id, room_code, room_name, capacity) VALUES
(11301, 11201, N'DEMO-A101', N'Phòng học A101', 50),
(11302, 11201, N'DEMO-LAB02', N'Phòng máy LAB02', 35);
SET IDENTITY_INSERT dbo.rooms OFF;

-- -------------------------------------------------------------
-- 10. IMPORTS AND STUDENT ACADEMIC RECORDS
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.registration_imports ON;
INSERT INTO dbo.registration_imports (
  id, user_id, semester_id, import_type, file_name,
  storage_path, file_format, import_status,
  total_rows, valid_rows, error_rows, processed_at
) VALUES
(11401, 9001, 11001, N'STUDENT_REGISTRATION', N'demo-registration-student1.xlsx',
 N'uploads/demo/imports/demo-registration-student1.xlsx', N'XLSX', N'COMPLETED',
 5, 4, 1, SYSDATETIME()),
(11402, 9002, 11001, N'STUDENT_REGISTRATION', N'demo-registration-student2.xlsx',
 N'uploads/demo/imports/demo-registration-student2.xlsx', N'XLSX', N'COMPLETED',
 3, 3, 0, SYSDATETIME());
SET IDENTITY_INSERT dbo.registration_imports OFF;

SET IDENTITY_INSERT dbo.course_catalog_imports ON;
INSERT INTO dbo.course_catalog_imports (
  id, admin_user_id, semester_id, file_name, storage_path,
  import_status, total_rows, success_rows, error_rows, completed_at
) VALUES
(11403, 9003, 11001, N'demo-course-catalog.xlsx',
 N'uploads/demo/imports/demo-course-catalog.xlsx', N'COMPLETED',
 4, 4, 0, SYSDATETIME());
SET IDENTITY_INSERT dbo.course_catalog_imports OFF;

SET IDENTITY_INSERT dbo.student_course_records ON;
INSERT INTO dbo.student_course_records (
  id, user_id, course_id, semester_id, source_import_id,
  grade, result_status, completed_at
) VALUES
(11501, 9001, 11101, 11001, 11401, 8.50, N'PASSED', SYSDATETIME()),
(11502, 9001, 11102, 11001, 11401, 7.75, N'PASSED', SYSDATETIME()),
(11503, 9002, 11101, 11001, 11402, 9.00, N'PASSED', SYSDATETIME());
SET IDENTITY_INSERT dbo.student_course_records OFF;

-- -------------------------------------------------------------
-- 11. COURSE SECTIONS AND MEETINGS
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.course_sections ON;
INSERT INTO dbo.course_sections (
  id, semester_id, course_id, section_code, instructor_name,
  max_capacity, current_enrollment, section_status
) VALUES
(11601, 11001, 11101, N'DEMO-CS101-01', N'Giảng viên mẫu Nguyễn An', 50, 25, N'OPEN'),
(11602, 11001, 11103, N'DEMO-CS201-01', N'Giảng viên mẫu Trần Bình', 40, 18, N'OPEN'),
(11603, 11001, 11104, N'DEMO-CS202-01', N'Giảng viên mẫu Lê Cường', 35, 20, N'OPEN');
SET IDENTITY_INSERT dbo.course_sections OFF;

SET IDENTITY_INSERT dbo.section_meetings ON;
INSERT INTO dbo.section_meetings (
  id, course_section_id, weekday, start_period, end_period,
  start_time, end_time, room_id
) VALUES
(11701, 11601, 2, 1, 3, N'07:00:00', N'09:30:00', 11301),
(11702, 11602, 4, 4, 6, N'13:00:00', N'15:30:00', 11302),
(11703, 11603, 6, 1, 3, N'07:00:00', N'09:30:00', 11301);
SET IDENTITY_INSERT dbo.section_meetings OFF;

SET IDENTITY_INSERT dbo.course_prerequisites ON;
INSERT INTO dbo.course_prerequisites (
  id, course_id, prerequisite_course_id, created_by
) VALUES
(11801, 11103, 11102, 9003),
(11802, 11104, 11102, 9003);
SET IDENTITY_INSERT dbo.course_prerequisites OFF;

-- -------------------------------------------------------------
-- 12. IMPORT ERROR DETAILS
-- [row_number] is bracketed for SQL Server compatibility.
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.registration_import_errors ON;
INSERT INTO dbo.registration_import_errors (
  id, registration_import_id, [row_number], field_name,
  error_code, error_message, row_data
) VALUES
(11901, 11401, 5, N'course_code', N'COURSE_NOT_FOUND',
 N'Mã học phần trong tệp mẫu không tồn tại trong danh mục học phần.',
 N'{"student_code":"DEMO2026001","course_code":"DEMO-UNKNOWN"}');
SET IDENTITY_INSERT dbo.registration_import_errors OFF;

-- -------------------------------------------------------------
-- 13. REGISTRATION PLANS AND PREFERENCES
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.registration_plans ON;
INSERT INTO dbo.registration_plans (
  id, user_id, semester_id, source_import_id,
  plan_name, plan_status
) VALUES
(12001, 9001, 11001, 11401, N'Kế hoạch học kỳ 1 - Minh An', N'DRAFT'),
(12002, 9002, 11001, 11402, N'Kế hoạch học kỳ 1 - Gia Linh', N'DRAFT');
SET IDENTITY_INSERT dbo.registration_plans OFF;

SET IDENTITY_INSERT dbo.registration_plan_courses ON;
INSERT INTO dbo.registration_plan_courses (
  id, registration_plan_id, course_id, selection_source
) VALUES
(12101, 12001, 11103, N'MANUAL'),
(12102, 12001, 11104, N'AI_RECOMMENDED'),
(12103, 12002, 11101, N'MANUAL'),
(12104, 12002, 11102, N'MANUAL');
SET IDENTITY_INSERT dbo.registration_plan_courses OFF;

SET IDENTITY_INSERT dbo.registration_preferences ON;
INSERT INTO dbo.registration_preferences (
  id, registration_plan_id, min_credits, max_credits,
  preferred_shift, avoid_saturday, compact_days, minimize_gaps,
  preferred_free_days, max_gap_periods
) VALUES
(12201, 12001, 6.0, 18.0, N'MORNING', 1, 1, 1, N'[1,3,5,7]', 2),
(12202, 12002, 6.0, 18.0, N'AFTERNOON', 0, 1, 1, N'[1,2,7]', 3);
SET IDENTITY_INSERT dbo.registration_preferences OFF;

-- -------------------------------------------------------------
-- 14. TIMETABLE GENERATION AND OPTIONS
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.schedule_generations ON;
INSERT INTO dbo.schedule_generations (
  id, registration_plan_id, generation_no, algorithm_version,
  generation_status, total_candidates, feasible_candidates,
  started_at, completed_at, error_message
) VALUES
(12301, 12001, 1, N'smartedu-scheduler-demo-1.0', N'COMPLETED', 12, 9,
 DATEADD(MINUTE, -30, SYSDATETIME()), DATEADD(MINUTE, -29, SYSDATETIME()), NULL),
(12302, 12002, 1, N'smartedu-scheduler-demo-1.0', N'COMPLETED', 8, 5,
 DATEADD(MINUTE, -20, SYSDATETIME()), DATEADD(MINUTE, -19, SYSDATETIME()), NULL);
SET IDENTITY_INSERT dbo.schedule_generations OFF;

-- Insert parent options before the child option because parent_option_id
-- is a self-referencing foreign key.
SET IDENTITY_INSERT dbo.schedule_options ON;
INSERT INTO dbo.schedule_options (
  id, schedule_generation_id, parent_option_id, option_name,
  option_rank, total_credits, total_score, is_feasible, option_type
) VALUES
(12401, 12301, NULL, N'Thời khóa biểu cân bằng - phương án 1', 1, 6.0, 92.5000, 1, N'GENERATED'),
(12403, 12302, NULL, N'Thời khóa biểu Gia Linh - phương án 1', 1, 6.0, 90.0000, 1, N'GENERATED');
SET IDENTITY_INSERT dbo.schedule_options OFF;

SET IDENTITY_INSERT dbo.schedule_options ON;
INSERT INTO dbo.schedule_options (
  id, schedule_generation_id, parent_option_id, option_name,
  option_rank, total_credits, total_score, is_feasible, option_type
) VALUES
(12402, 12301, 12401, N'Thời khóa biểu linh hoạt - phương án 2', 2, 6.0, 86.2500, 1, N'ALTERNATIVE');
SET IDENTITY_INSERT dbo.schedule_options OFF;

SET IDENTITY_INSERT dbo.schedule_option_sections ON;
INSERT INTO dbo.schedule_option_sections (
  id, schedule_option_id, course_section_id
) VALUES
(12501, 12401, 11601),
(12502, 12401, 11602),
(12503, 12402, 11601),
(12504, 12402, 11603),
(12505, 12403, 11602),
(12506, 12403, 11603);
SET IDENTITY_INSERT dbo.schedule_option_sections OFF;

SET IDENTITY_INSERT dbo.schedule_option_evaluations ON;
INSERT INTO dbo.schedule_option_evaluations (
  id, schedule_option_id, criterion_code, criterion_name,
  score_value, weight_value, explanation
) VALUES
(12601, 12401, N'COMPACT_DAYS', N'Mức độ tập trung lịch học', 95.0000, 0.3500,
 N'Các buổi học được phân bố tương đối tập trung trong tuần.'),
(12602, 12401, N'PREFERENCE_MATCH', N'Mức độ phù hợp nguyện vọng', 90.0000, 0.4000,
 N'Phù hợp với ca học và ngày nghỉ ưu tiên.'),
(12603, 12402, N'GAP_MINIMIZATION', N'Tối thiểu hóa thời gian trống', 82.0000, 0.2500,
 N'Có một khoảng trống giữa các buổi học.'),
(12604, 12403, N'PREFERENCE_MATCH', N'Mức độ phù hợp nguyện vọng', 90.0000, 0.4000,
 N'Phương án phù hợp với các học phần đã chọn.');
SET IDENTITY_INSERT dbo.schedule_option_evaluations OFF;

SET IDENTITY_INSERT dbo.schedule_option_warnings ON;
INSERT INTO dbo.schedule_option_warnings (
  id, schedule_option_id, warning_type, severity, warning_message
) VALUES
(12701, 12402, N'LONG_GAP', N'INFO',
 N'Phương án có khoảng trống tương đối dài giữa hai buổi học.');
SET IDENTITY_INSERT dbo.schedule_option_warnings OFF;

SET IDENTITY_INSERT dbo.saved_schedules ON;
INSERT INTO dbo.saved_schedules (
  id, user_id, schedule_option_id, display_name
) VALUES
(12801, 9001, 12401, N'Thời khóa biểu học kỳ 2 của Minh An'),
(12802, 9002, 12403, N'Thời khóa biểu học kỳ 2 của Gia Linh');
SET IDENTITY_INSERT dbo.saved_schedules OFF;

SET IDENTITY_INSERT dbo.schedule_exports ON;
INSERT INTO dbo.schedule_exports (
  id, user_id, schedule_option_id, export_format,
  file_path, export_status, file_size_bytes, exported_at
) VALUES
(12901, 9001, 12401, N'PDF', N'exports/demo/minh-an-timetable.pdf', N'COMPLETED', 153600, SYSDATETIME()),
(12902, 9002, 12403, N'PNG', N'exports/demo/gia-linh-timetable.png', N'COMPLETED', 98304, SYSDATETIME());
SET IDENTITY_INSERT dbo.schedule_exports OFF;

-- -------------------------------------------------------------
-- 15. CATALOG IMPORT ERRORS AND SYSTEM LOGS
-- -------------------------------------------------------------
SET IDENTITY_INSERT dbo.course_catalog_import_errors ON;
INSERT INTO dbo.course_catalog_import_errors (
  id, course_catalog_import_id, [row_number], field_name,
  error_code, error_message, row_data
) VALUES
(13001, 11403, 4, N'credits', N'INVALID_CREDITS',
 N'Số tín chỉ trong dòng mẫu phải lớn hơn 0.',
 N'{"course_code":"DEMO-CS999","credits":0}');
SET IDENTITY_INSERT dbo.course_catalog_import_errors OFF;

SET IDENTITY_INSERT dbo.email_logs ON;
INSERT INTO dbo.email_logs (
  id, user_id, recipient_email, email_type, provider,
  provider_message_id, send_status, error_message, sent_at
) VALUES
(13101, 9001, N'student1@smartedu.test', N'EMAIL_VERIFICATION', N'DEMO_PROVIDER',
 N'demo-message-9001', N'SENT', NULL, SYSDATETIME()),
(13102, 9002, N'student2@smartedu.test', N'WELCOME', N'DEMO_PROVIDER',
 N'demo-message-9002', N'SENT', NULL, SYSDATETIME());
SET IDENTITY_INSERT dbo.email_logs OFF;

SET IDENTITY_INSERT dbo.ai_call_logs ON;
INSERT INTO dbo.ai_call_logs (
  id, user_id, question_generation_request_id, operation_type,
  provider, model_name, response_time_ms, prompt_tokens,
  completion_tokens, total_tokens, call_status, error_code,
  error_message, called_at
) VALUES
(13201, 9001, 9301, N'QUESTION_GENERATION', N'DEMO_PROVIDER', N'demo-llm',
 1850, 1200, 450, 1650, N'SUCCESS', NULL, NULL, SYSDATETIME()),
(13202, 9002, 9302, N'QUESTION_GENERATION', N'DEMO_PROVIDER', N'demo-llm',
 2100, 1350, 510, 1860, N'SUCCESS', NULL, NULL, SYSDATETIME());
SET IDENTITY_INSERT dbo.ai_call_logs OFF;

SET IDENTITY_INSERT dbo.file_processing_logs ON;
INSERT INTO dbo.file_processing_logs (
  id, learning_material_id, registration_import_id,
  course_catalog_import_id, processing_type, processing_status,
  duration_ms, error_message, processed_at
) VALUES
(13301, 9101, NULL, NULL, N'MATERIAL_EXTRACTION', N'COMPLETED', 1250, NULL, SYSDATETIME()),
(13302, NULL, 11401, NULL, N'REGISTRATION_IMPORT', N'COMPLETED', 980, NULL, SYSDATETIME()),
(13303, NULL, NULL, 11403, N'COURSE_CATALOG_IMPORT', N'COMPLETED', 1120, NULL, SYSDATETIME());
SET IDENTITY_INSERT dbo.file_processing_logs OFF;

SET IDENTITY_INSERT dbo.activity_logs ON;
INSERT INTO dbo.activity_logs (
  id, actor_user_id, target_user_id, action_code,
  entity_type, entity_id, detail, ip_address
) VALUES
(13401, 9003, 9001, N'DEMO_USER_REVIEWED', N'users', 9001,
 N'{"source":"seed","description":"Demo admin activity"}', N'127.0.0.1'),
(13402, 9001, NULL, N'DEMO_QUIZ_SUBMITTED', N'quiz_attempts', 9901,
 N'{"score":66.67,"source":"seed"}', N'127.0.0.1'),
(13403, 9002, NULL, N'DEMO_SCHEDULE_SAVED', N'saved_schedules', 12802,
 N'{"optionId":12403,"source":"seed"}', N'127.0.0.1');
SET IDENTITY_INSERT dbo.activity_logs OFF;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    DECLARE @SeedError NVARCHAR(4000) = ERROR_MESSAGE();
    RAISERROR(N'Không thể nạp dữ liệu mẫu SmartEdu: %s', 16, 1, @SeedError);
    RETURN;
END CATCH;

-- -------------------------------------------------------------
-- 16. VERIFICATION: all 44 tables have sample data (roles are seeded by the schema script)
-- -------------------------------------------------------------
SELECT N'roles' AS table_name, COUNT(*) AS sample_rows FROM dbo.roles;
SELECT N'users' AS table_name, COUNT(*) AS sample_rows FROM dbo.users WHERE id IN (9001,9002,9003);
SELECT N'email_otps' AS table_name, COUNT(*) AS sample_rows FROM dbo.email_otps WHERE id IN (9001,9002);
SELECT N'password_reset_tokens' AS table_name, COUNT(*) AS sample_rows FROM dbo.password_reset_tokens WHERE id = 9001;
SELECT N'user_sessions' AS table_name, COUNT(*) AS sample_rows FROM dbo.user_sessions WHERE id IN (9001,9002);
SELECT N'learning_materials' AS table_name, COUNT(*) AS sample_rows FROM dbo.learning_materials WHERE id IN (9101,9102);
SELECT N'material_contents' AS table_name, COUNT(*) AS sample_rows FROM dbo.material_contents WHERE id IN (9201,9202);
SELECT N'question_generation_requests' AS table_name, COUNT(*) AS sample_rows FROM dbo.question_generation_requests WHERE id IN (9301,9302);
SELECT N'question_sets' AS table_name, COUNT(*) AS sample_rows FROM dbo.question_sets WHERE id IN (9401,9402);
SELECT N'questions' AS table_name, COUNT(*) AS sample_rows FROM dbo.questions WHERE id BETWEEN 9501 AND 9506;
SELECT N'question_options' AS table_name, COUNT(*) AS sample_rows FROM dbo.question_options WHERE id BETWEEN 9601 AND 9624;
SELECT N'quiz_tests' AS table_name, COUNT(*) AS sample_rows FROM dbo.quiz_tests WHERE id IN (9701,9702);
SELECT N'quiz_test_questions' AS table_name, COUNT(*) AS sample_rows FROM dbo.quiz_test_questions WHERE id BETWEEN 9801 AND 9806;
SELECT N'quiz_attempts' AS table_name, COUNT(*) AS sample_rows FROM dbo.quiz_attempts WHERE id IN (9901,9902);
SELECT N'quiz_attempt_answers' AS table_name, COUNT(*) AS sample_rows FROM dbo.quiz_attempt_answers WHERE id BETWEEN 10001 AND 10006;
SELECT N'review_sessions' AS table_name, COUNT(*) AS sample_rows FROM dbo.review_sessions WHERE id IN (10101,10102);
SELECT N'review_session_items' AS table_name, COUNT(*) AS sample_rows FROM dbo.review_session_items WHERE id BETWEEN 10201 AND 10203;
SELECT N'review_attempts' AS table_name, COUNT(*) AS sample_rows FROM dbo.review_attempts WHERE id BETWEEN 10301 AND 10303;
SELECT N'semesters' AS table_name, COUNT(*) AS sample_rows FROM dbo.semesters WHERE id IN (11001,11002);
SELECT N'courses' AS table_name, COUNT(*) AS sample_rows FROM dbo.courses WHERE id BETWEEN 11101 AND 11104;
SELECT N'campuses' AS table_name, COUNT(*) AS sample_rows FROM dbo.campuses WHERE id = 11201;
SELECT N'rooms' AS table_name, COUNT(*) AS sample_rows FROM dbo.rooms WHERE id IN (11301,11302);
SELECT N'registration_imports' AS table_name, COUNT(*) AS sample_rows FROM dbo.registration_imports WHERE id IN (11401,11402);
SELECT N'course_catalog_imports' AS table_name, COUNT(*) AS sample_rows FROM dbo.course_catalog_imports WHERE id = 11403;
SELECT N'student_course_records' AS table_name, COUNT(*) AS sample_rows FROM dbo.student_course_records WHERE id BETWEEN 11501 AND 11503;
SELECT N'course_sections' AS table_name, COUNT(*) AS sample_rows FROM dbo.course_sections WHERE id BETWEEN 11601 AND 11603;
SELECT N'section_meetings' AS table_name, COUNT(*) AS sample_rows FROM dbo.section_meetings WHERE id BETWEEN 11701 AND 11703;
SELECT N'course_prerequisites' AS table_name, COUNT(*) AS sample_rows FROM dbo.course_prerequisites WHERE id IN (11801,11802);
SELECT N'registration_import_errors' AS table_name, COUNT(*) AS sample_rows FROM dbo.registration_import_errors WHERE id = 11901;
SELECT N'registration_plans' AS table_name, COUNT(*) AS sample_rows FROM dbo.registration_plans WHERE id IN (12001,12002);
SELECT N'registration_plan_courses' AS table_name, COUNT(*) AS sample_rows FROM dbo.registration_plan_courses WHERE id BETWEEN 12101 AND 12104;
SELECT N'registration_preferences' AS table_name, COUNT(*) AS sample_rows FROM dbo.registration_preferences WHERE id IN (12201,12202);
SELECT N'schedule_generations' AS table_name, COUNT(*) AS sample_rows FROM dbo.schedule_generations WHERE id IN (12301,12302);
SELECT N'schedule_options' AS table_name, COUNT(*) AS sample_rows FROM dbo.schedule_options WHERE id BETWEEN 12401 AND 12403;
SELECT N'schedule_option_sections' AS table_name, COUNT(*) AS sample_rows FROM dbo.schedule_option_sections WHERE id BETWEEN 12501 AND 12506;
SELECT N'schedule_option_evaluations' AS table_name, COUNT(*) AS sample_rows FROM dbo.schedule_option_evaluations WHERE id BETWEEN 12601 AND 12604;
SELECT N'schedule_option_warnings' AS table_name, COUNT(*) AS sample_rows FROM dbo.schedule_option_warnings WHERE id = 12701;
SELECT N'saved_schedules' AS table_name, COUNT(*) AS sample_rows FROM dbo.saved_schedules WHERE id IN (12801,12802);
SELECT N'schedule_exports' AS table_name, COUNT(*) AS sample_rows FROM dbo.schedule_exports WHERE id IN (12901,12902);
SELECT N'course_catalog_import_errors' AS table_name, COUNT(*) AS sample_rows FROM dbo.course_catalog_import_errors WHERE id = 13001;
SELECT N'email_logs' AS table_name, COUNT(*) AS sample_rows FROM dbo.email_logs WHERE id IN (13101,13102);
SELECT N'ai_call_logs' AS table_name, COUNT(*) AS sample_rows FROM dbo.ai_call_logs WHERE id IN (13201,13202);
SELECT N'file_processing_logs' AS table_name, COUNT(*) AS sample_rows FROM dbo.file_processing_logs WHERE id BETWEEN 13301 AND 13303;
SELECT N'activity_logs' AS table_name, COUNT(*) AS sample_rows FROM dbo.activity_logs WHERE id BETWEEN 13401 AND 13403;

-- Sample accounts (same demo password for all): Demo@12345
SELECT id, username, full_name, email, role_id, account_status
FROM dbo.users WHERE id IN (9001,9002,9003) ORDER BY id;
