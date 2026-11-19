/* =============================================================
   SmartEdu - SQL Server (T-SQL) Database Setup
   Converted from MariaDB 10.4 schema.
   Target: SQL Server 2016 SP1+ (SSMS), NVARCHAR for Unicode, ISJSON support.
   Includes 44 tables, STUDENT/ADMIN roles, and instructor display names.
   SQL Server multiple-cascade-path conflicts have been resolved.

   IMPORTANT:
   - This script creates a new database named SmartEdu_Connect_Retry.
   - Run it once. If a prior attempt created partial objects, use a new database name or drop only the failed empty database before retrying.
   - BIGINT is used for MySQL BIGINT UNSIGNED identifiers. SQL Server BIGINT
     is signed, but its practical range is sufficient for application IDs.
   - DATETIME(6) is mapped to DATETIME2(6).
   - MySQL ON UPDATE CURRENT_TIMESTAMP is implemented with UPDATE triggers.
   ============================================================= */

IF DB_ID(N'SmartEdu_Connect_Retry') IS NULL
BEGIN
    CREATE DATABASE [SmartEdu_Connect_Retry];
END;
GO

USE [SmartEdu_Connect_Retry];
GO
SET NOCOUNT ON;
GO

-- This script targets a newly created database. Run it only once.
GO

-- Table: roles
CREATE TABLE dbo.roles (
    id BIGINT IDENTITY(1,1) NOT NULL,
    code NVARCHAR(50) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    description NVARCHAR(500) NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code),
    CONSTRAINT uq_roles_name UNIQUE (name)
);
GO

-- Table: users
CREATE TABLE dbo.users (
    id BIGINT IDENTITY(1,1) NOT NULL,
    role_id BIGINT NOT NULL,
    student_code NVARCHAR(30) NULL,
    username NVARCHAR(100) NOT NULL,
    full_name NVARCHAR(150) NOT NULL,
    email NVARCHAR(255) NOT NULL,
    phone NVARCHAR(20) NULL,
    address NVARCHAR(500) NULL,
    gender NVARCHAR(20) NULL,
    password_hash NVARCHAR(255) NOT NULL,
    avatar_url NVARCHAR(1000) NULL,
    email_verified_at DATETIME2(6) NULL,
    account_status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',
    locked_at DATETIME2(6) NULL,
    last_login_at DATETIME2(6) NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES dbo.roles(id) ON DELETE NO ACTION
);
GO

-- Table: email_otps
CREATE TABLE dbo.email_otps (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    email NVARCHAR(255) NOT NULL,
    otp_hash NVARCHAR(255) NOT NULL,
    expires_at DATETIME2(6) NOT NULL,
    used_at DATETIME2(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_email_otps_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE,
    CONSTRAINT chk_email_otps_attempt_count CHECK (attempt_count >= 0)
);
GO

-- Table: password_reset_tokens
CREATE TABLE dbo.password_reset_tokens (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash NVARCHAR(255) NOT NULL,
    expires_at DATETIME2(6) NOT NULL,
    used_at DATETIME2(6) NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE
);
GO

-- Table: user_sessions
CREATE TABLE dbo.user_sessions (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash NVARCHAR(255) NOT NULL,
    ip_address NVARCHAR(45) NULL,
    user_agent NVARCHAR(512) NULL,
    last_activity_at DATETIME2(6) NULL,
    expires_at DATETIME2(6) NOT NULL,
    revoked_at DATETIME2(6) NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_user_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE
);
GO

-- Table: learning_materials
CREATE TABLE dbo.learning_materials (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    title NVARCHAR(255) NOT NULL,
    original_file_name NVARCHAR(255) NOT NULL,
    storage_path NVARCHAR(1024) NOT NULL,
    file_extension NVARCHAR(20) NOT NULL,
    mime_type NVARCHAR(150) NULL,
    file_size_bytes BIGINT NOT NULL,
    processing_status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',
    uploaded_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    deleted_at DATETIME2(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_learning_materials_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT chk_learning_materials_file_size CHECK (file_size_bytes >= 0)
);
GO

-- Table: material_contents
CREATE TABLE dbo.material_contents (
    id BIGINT IDENTITY(1,1) NOT NULL,
    learning_material_id BIGINT NOT NULL,
    extracted_text NVARCHAR(MAX) NULL,
    page_count BIGINT NULL,
    extraction_status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',
    extraction_version NVARCHAR(50) NULL,
    extracted_at DATETIME2(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_material_contents_material UNIQUE (learning_material_id),
    CONSTRAINT fk_material_contents_material FOREIGN KEY (learning_material_id) REFERENCES dbo.learning_materials(id) ON DELETE CASCADE,
    CONSTRAINT chk_material_contents_page_count CHECK (page_count IS NULL OR page_count >= 0)
);
GO

-- Table: question_generation_requests
CREATE TABLE dbo.question_generation_requests (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    learning_material_id BIGINT NOT NULL,
    content_scope NVARCHAR(30) NOT NULL,
    scope_description NVARCHAR(MAX) NULL,
    question_type NVARCHAR(30) NOT NULL,
    question_count INT NOT NULL,
    difficulty NVARCHAR(20) NOT NULL,
    status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',
    requested_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    completed_at DATETIME2(6) NULL,
    error_message NVARCHAR(MAX) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_qgr_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT fk_qgr_material FOREIGN KEY (learning_material_id) REFERENCES dbo.learning_materials(id) ON DELETE NO ACTION,
    CONSTRAINT chk_qgr_question_count CHECK (question_count > 0)
);
GO

-- Table: question_sets
CREATE TABLE dbo.question_sets (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    generation_request_id BIGINT NULL,
    source_material_id BIGINT NULL,
    title NVARCHAR(255) NOT NULL,
    description NVARCHAR(MAX) NULL,
    set_status NVARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    saved_at DATETIME2(6) NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_question_sets_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT fk_question_sets_generation_request FOREIGN KEY (generation_request_id) REFERENCES dbo.question_generation_requests(id) ON DELETE SET NULL,
    CONSTRAINT fk_question_sets_source_material FOREIGN KEY (source_material_id) REFERENCES dbo.learning_materials(id) ON DELETE SET NULL
);
GO

-- Table: questions
CREATE TABLE dbo.questions (
    id BIGINT IDENTITY(1,1) NOT NULL,
    question_set_id BIGINT NOT NULL,
    source_material_id BIGINT NULL,
    replaces_question_id BIGINT NULL,
    question_type NVARCHAR(30) NOT NULL,
    content NVARCHAR(MAX) NOT NULL,
    explanation NVARCHAR(MAX) NULL,
    difficulty NVARCHAR(20) NULL,
    reference_excerpt NVARCHAR(MAX) NULL,
    reference_page BIGINT NULL,
    display_order INT NOT NULL,
    question_status NVARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_questions_set_order UNIQUE (question_set_id, display_order),
    CONSTRAINT fk_questions_question_set FOREIGN KEY (question_set_id) REFERENCES dbo.question_sets(id) ON DELETE CASCADE,
    CONSTRAINT fk_questions_source_material FOREIGN KEY (source_material_id) REFERENCES dbo.learning_materials(id) ON DELETE SET NULL,
    CONSTRAINT fk_questions_replaces FOREIGN KEY (replaces_question_id) REFERENCES dbo.questions(id) ON DELETE NO ACTION
);
GO

-- Table: question_options
CREATE TABLE dbo.question_options (
    id BIGINT IDENTITY(1,1) NOT NULL,
    question_id BIGINT NOT NULL,
    option_label NVARCHAR(10) NOT NULL,
    content NVARCHAR(MAX) NOT NULL,
    is_correct BIT NOT NULL DEFAULT 0,
    display_order INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_question_options_label UNIQUE (question_id, option_label),
    CONSTRAINT uq_question_options_order UNIQUE (question_id, display_order),
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES dbo.questions(id) ON DELETE CASCADE,
    CONSTRAINT chk_question_options_is_correct CHECK (is_correct IN (0,1))
);
GO

-- Table: quiz_tests
CREATE TABLE dbo.quiz_tests (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    question_set_id BIGINT NOT NULL,
    title NVARCHAR(255) NOT NULL,
    duration_minutes INT NOT NULL,
    total_score DECIMAL(8,2) NOT NULL DEFAULT 100.00,
    test_status NVARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_tests_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT fk_quiz_tests_question_set FOREIGN KEY (question_set_id) REFERENCES dbo.question_sets(id) ON DELETE NO ACTION,
    CONSTRAINT chk_quiz_tests_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_quiz_tests_total_score CHECK (total_score >= 0)
);
GO

-- Table: quiz_test_questions
CREATE TABLE dbo.quiz_test_questions (
    id BIGINT IDENTITY(1,1) NOT NULL,
    quiz_test_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    max_score DECIMAL(8,2) NOT NULL DEFAULT 1.00,
    PRIMARY KEY (id),
    CONSTRAINT uq_quiz_test_questions_question UNIQUE (quiz_test_id, question_id),
    CONSTRAINT uq_quiz_test_questions_order UNIQUE (quiz_test_id, display_order),
    CONSTRAINT fk_quiz_test_questions_test FOREIGN KEY (quiz_test_id) REFERENCES dbo.quiz_tests(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_test_questions_question FOREIGN KEY (question_id) REFERENCES dbo.questions(id) ON DELETE NO ACTION,
    CONSTRAINT chk_quiz_test_questions_max_score CHECK (max_score >= 0)
);
GO

-- Table: quiz_attempts
CREATE TABLE dbo.quiz_attempts (
    id BIGINT IDENTITY(1,1) NOT NULL,
    quiz_test_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    started_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    submitted_at DATETIME2(6) NULL,
    duration_seconds BIGINT NULL,
    attempt_status NVARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    total_score DECIMAL(8,2) NULL,
    correct_count INT NOT NULL DEFAULT 0,
    incorrect_count INT NOT NULL DEFAULT 0,
    unanswered_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_attempts_test FOREIGN KEY (quiz_test_id) REFERENCES dbo.quiz_tests(id) ON DELETE NO ACTION,
    CONSTRAINT fk_quiz_attempts_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT chk_quiz_attempts_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
);
GO

-- Table: quiz_attempt_answers
CREATE TABLE dbo.quiz_attempt_answers (
    id BIGINT IDENTITY(1,1) NOT NULL,
    quiz_attempt_id BIGINT NOT NULL,
    quiz_test_question_id BIGINT NOT NULL,
    selected_option_id BIGINT NULL,
    answer_text NVARCHAR(MAX) NULL,
    is_correct BIT NULL,
    awarded_score DECIMAL(8,2) NOT NULL DEFAULT 0.00,
    question_content_snapshot NVARCHAR(MAX) NULL,
    correct_answer_snapshot NVARCHAR(MAX) NULL,
    explanation_snapshot NVARCHAR(MAX) NULL,
    answered_at DATETIME2(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_quiz_attempt_answer_question UNIQUE (quiz_attempt_id, quiz_test_question_id),
    CONSTRAINT fk_quiz_attempt_answers_attempt FOREIGN KEY (quiz_attempt_id) REFERENCES dbo.quiz_attempts(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_attempt_answers_test_question FOREIGN KEY (quiz_test_question_id) REFERENCES dbo.quiz_test_questions(id) ON DELETE NO ACTION,
    CONSTRAINT fk_quiz_attempt_answers_selected_option FOREIGN KEY (selected_option_id) REFERENCES dbo.question_options(id) ON DELETE SET NULL,
    CONSTRAINT chk_quiz_attempt_answers_is_correct CHECK (is_correct IS NULL OR is_correct IN (0,1)),
    CONSTRAINT chk_quiz_attempt_answers_score CHECK (awarded_score >= 0)
);
GO

-- Table: review_sessions
CREATE TABLE dbo.review_sessions (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    source_quiz_attempt_id BIGINT NULL,
    source_question_set_id BIGINT NULL,
    session_status NVARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    ended_at DATETIME2(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_sessions_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT fk_review_sessions_source_attempt FOREIGN KEY (source_quiz_attempt_id) REFERENCES dbo.quiz_attempts(id) ON DELETE SET NULL,
    CONSTRAINT fk_review_sessions_source_set FOREIGN KEY (source_question_set_id) REFERENCES dbo.question_sets(id) ON DELETE SET NULL
);
GO

-- Table: review_session_items
CREATE TABLE dbo.review_session_items (
    id BIGINT IDENTITY(1,1) NOT NULL,
    review_session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    source_attempt_answer_id BIGINT NULL,
    review_status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',
    last_is_correct BIT NULL,
    last_reviewed_at DATETIME2(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_review_session_items_question UNIQUE (review_session_id, question_id),
    CONSTRAINT fk_review_items_session FOREIGN KEY (review_session_id) REFERENCES dbo.review_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_items_question FOREIGN KEY (question_id) REFERENCES dbo.questions(id) ON DELETE NO ACTION,
    CONSTRAINT fk_review_items_source_answer FOREIGN KEY (source_attempt_answer_id) REFERENCES dbo.quiz_attempt_answers(id) ON DELETE SET NULL,
    CONSTRAINT chk_review_items_last_correct CHECK (last_is_correct IS NULL OR last_is_correct IN (0,1))
);
GO

-- Table: review_attempts
CREATE TABLE dbo.review_attempts (
    id BIGINT IDENTITY(1,1) NOT NULL,
    review_session_item_id BIGINT NOT NULL,
    selected_option_id BIGINT NULL,
    answer_text NVARCHAR(MAX) NULL,
    is_correct BIT NOT NULL,
    attempted_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_review_attempts_item FOREIGN KEY (review_session_item_id) REFERENCES dbo.review_session_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_attempts_option FOREIGN KEY (selected_option_id) REFERENCES dbo.question_options(id) ON DELETE SET NULL,
    CONSTRAINT chk_review_attempts_is_correct CHECK (is_correct IN (0,1))
);
GO

-- Table: semesters
CREATE TABLE dbo.semesters (
    id BIGINT IDENTITY(1,1) NOT NULL,
    semester_code NVARCHAR(30) NOT NULL,
    semester_name NVARCHAR(100) NOT NULL,
    academic_year NVARCHAR(20) NOT NULL,
    term_no TINYINT NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    registration_start_at DATETIME2(6) NULL,
    registration_end_at DATETIME2(6) NULL,
    semester_status NVARCHAR(30) NOT NULL DEFAULT 'PLANNING',
    PRIMARY KEY (id),
    CONSTRAINT uq_semesters_code UNIQUE (semester_code),
    CONSTRAINT chk_semesters_term_no CHECK (term_no > 0),
    CONSTRAINT chk_semesters_dates CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date)
);
GO

-- Table: courses
CREATE TABLE dbo.courses (
    id BIGINT IDENTITY(1,1) NOT NULL,
    course_code NVARCHAR(30) NOT NULL,
    course_name NVARCHAR(255) NOT NULL,
    credits DECIMAL(4,1) NOT NULL,
    description NVARCHAR(MAX) NULL,
    is_active BIT NOT NULL DEFAULT 1,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_courses_code UNIQUE (course_code),
    CONSTRAINT chk_courses_credits CHECK (credits > 0),
    CONSTRAINT chk_courses_is_active CHECK (is_active IN (0,1))
);
GO

-- Table: campuses
CREATE TABLE dbo.campuses (
    id BIGINT IDENTITY(1,1) NOT NULL,
    campus_code NVARCHAR(30) NOT NULL,
    campus_name NVARCHAR(150) NOT NULL,
    address NVARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_campuses_code UNIQUE (campus_code),
    CONSTRAINT uq_campuses_name UNIQUE (campus_name)
);
GO

-- Table: rooms
CREATE TABLE dbo.rooms (
    id BIGINT IDENTITY(1,1) NOT NULL,
    campus_id BIGINT NOT NULL,
    room_code NVARCHAR(30) NOT NULL,
    room_name NVARCHAR(100) NULL,
    capacity INT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_rooms_campus_code UNIQUE (campus_id, room_code),
    CONSTRAINT fk_rooms_campus FOREIGN KEY (campus_id) REFERENCES dbo.campuses(id) ON DELETE NO ACTION,
    CONSTRAINT chk_rooms_capacity CHECK (capacity IS NULL OR capacity > 0)
);
GO

-- Table: registration_imports
CREATE TABLE dbo.registration_imports (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    import_type NVARCHAR(30) NOT NULL,
    file_name NVARCHAR(255) NOT NULL,
    storage_path NVARCHAR(1024) NOT NULL,
    file_format NVARCHAR(20) NOT NULL,
    import_status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_rows BIGINT NOT NULL DEFAULT 0,
    valid_rows BIGINT NOT NULL DEFAULT 0,
    error_rows BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    processed_at DATETIME2(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_registration_imports_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT fk_registration_imports_semester FOREIGN KEY (semester_id) REFERENCES dbo.semesters(id) ON DELETE NO ACTION
);
GO

-- Table: course_catalog_imports
CREATE TABLE dbo.course_catalog_imports (
    id BIGINT IDENTITY(1,1) NOT NULL,
    admin_user_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    file_name NVARCHAR(255) NOT NULL,
    storage_path NVARCHAR(1024) NOT NULL,
    import_status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_rows BIGINT NOT NULL DEFAULT 0,
    success_rows BIGINT NOT NULL DEFAULT 0,
    error_rows BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    completed_at DATETIME2(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_course_catalog_imports_admin FOREIGN KEY (admin_user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT fk_course_catalog_imports_semester FOREIGN KEY (semester_id) REFERENCES dbo.semesters(id) ON DELETE NO ACTION
);
GO

-- Table: student_course_records
CREATE TABLE dbo.student_course_records (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    semester_id BIGINT NULL,
    source_import_id BIGINT NULL,
    grade DECIMAL(5,2) NULL,
    result_status NVARCHAR(30) NOT NULL,
    completed_at DATETIME2(6) NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_student_course_records_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT fk_student_course_records_course FOREIGN KEY (course_id) REFERENCES dbo.courses(id) ON DELETE NO ACTION,
    CONSTRAINT fk_student_course_records_semester FOREIGN KEY (semester_id) REFERENCES dbo.semesters(id) ON DELETE SET NULL,
    CONSTRAINT fk_student_course_records_import FOREIGN KEY (source_import_id) REFERENCES dbo.registration_imports(id) ON DELETE SET NULL
);
GO

-- Table: course_sections
CREATE TABLE dbo.course_sections (
    id BIGINT IDENTITY(1,1) NOT NULL,
    semester_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    section_code NVARCHAR(50) NOT NULL,
    instructor_name NVARCHAR(150) NULL,
    max_capacity INT NOT NULL,
    current_enrollment INT NOT NULL DEFAULT 0,
    section_status NVARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_course_sections_semester_code UNIQUE (semester_id, section_code),
    CONSTRAINT fk_course_sections_semester FOREIGN KEY (semester_id) REFERENCES dbo.semesters(id) ON DELETE NO ACTION,
    CONSTRAINT fk_course_sections_course FOREIGN KEY (course_id) REFERENCES dbo.courses(id) ON DELETE NO ACTION,
    CONSTRAINT chk_course_sections_capacity CHECK (max_capacity > 0 AND current_enrollment <= max_capacity)
);
GO

-- Table: section_meetings
CREATE TABLE dbo.section_meetings (
    id BIGINT IDENTITY(1,1) NOT NULL,
    course_section_id BIGINT NOT NULL,
    weekday TINYINT NOT NULL,
    start_period TINYINT NOT NULL,
    end_period TINYINT NOT NULL,
    start_time TIME NULL,
    end_time TIME NULL,
    room_id BIGINT NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_section_meetings_section FOREIGN KEY (course_section_id) REFERENCES dbo.course_sections(id) ON DELETE CASCADE,
    CONSTRAINT fk_section_meetings_room FOREIGN KEY (room_id) REFERENCES dbo.rooms(id) ON DELETE SET NULL,
    CONSTRAINT chk_section_meetings_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT chk_section_meetings_periods CHECK (start_period > 0 AND end_period >= start_period),
    CONSTRAINT chk_section_meetings_time CHECK (start_time IS NULL OR end_time IS NULL OR start_time < end_time)
);
GO

-- Table: course_prerequisites
CREATE TABLE dbo.course_prerequisites (
    id BIGINT IDENTITY(1,1) NOT NULL,
    course_id BIGINT NOT NULL,
    prerequisite_course_id BIGINT NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_course_prerequisites_pair UNIQUE (course_id, prerequisite_course_id),
    CONSTRAINT fk_course_prerequisites_course FOREIGN KEY (course_id) REFERENCES dbo.courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_prerequisites_prerequisite FOREIGN KEY (prerequisite_course_id) REFERENCES dbo.courses(id) ON DELETE NO ACTION,
    CONSTRAINT fk_course_prerequisites_created_by FOREIGN KEY (created_by) REFERENCES dbo.users(id) ON DELETE SET NULL,
    CONSTRAINT chk_course_prerequisites_not_self CHECK (course_id <> prerequisite_course_id)
);
GO

-- Table: registration_import_errors
CREATE TABLE dbo.registration_import_errors (
    id BIGINT IDENTITY(1,1) NOT NULL,
    registration_import_id BIGINT NOT NULL,
    row_number BIGINT NOT NULL,
    field_name NVARCHAR(100) NULL,
    error_code NVARCHAR(50) NULL,
    error_message NVARCHAR(MAX) NOT NULL,
    row_data NVARCHAR(MAX) NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_registration_import_errors_import FOREIGN KEY (registration_import_id) REFERENCES dbo.registration_imports(id) ON DELETE CASCADE,
    CONSTRAINT chk_registration_import_errors_row_data_json CHECK (row_data IS NULL OR ISJSON(row_data) = 1)
);
GO

-- Table: registration_plans
CREATE TABLE dbo.registration_plans (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    source_import_id BIGINT NULL,
    plan_name NVARCHAR(255) NOT NULL,
    plan_status NVARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_registration_plans_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT fk_registration_plans_semester FOREIGN KEY (semester_id) REFERENCES dbo.semesters(id) ON DELETE NO ACTION,
    CONSTRAINT fk_registration_plans_source_import FOREIGN KEY (source_import_id) REFERENCES dbo.registration_imports(id) ON DELETE SET NULL
);
GO

-- Table: registration_plan_courses
CREATE TABLE dbo.registration_plan_courses (
    id BIGINT IDENTITY(1,1) NOT NULL,
    registration_plan_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    selection_source NVARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_registration_plan_courses_pair UNIQUE (registration_plan_id, course_id),
    CONSTRAINT fk_registration_plan_courses_plan FOREIGN KEY (registration_plan_id) REFERENCES dbo.registration_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_registration_plan_courses_course FOREIGN KEY (course_id) REFERENCES dbo.courses(id) ON DELETE NO ACTION
);
GO

-- Table: registration_preferences
CREATE TABLE dbo.registration_preferences (
    id BIGINT IDENTITY(1,1) NOT NULL,
    registration_plan_id BIGINT NOT NULL,
    min_credits DECIMAL(5,1) NOT NULL,
    max_credits DECIMAL(5,1) NOT NULL,
    preferred_shift NVARCHAR(20) NULL,
    avoid_saturday BIT NOT NULL DEFAULT 0,
    compact_days BIT NOT NULL DEFAULT 0,
    minimize_gaps BIT NOT NULL DEFAULT 0,
    preferred_free_days NVARCHAR(MAX) NULL,
    max_gap_periods TINYINT NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_registration_preferences_plan UNIQUE (registration_plan_id),
    CONSTRAINT fk_registration_preferences_plan FOREIGN KEY (registration_plan_id) REFERENCES dbo.registration_plans(id) ON DELETE CASCADE,
    CONSTRAINT chk_registration_preferences_credit_range CHECK (min_credits >= 0 AND max_credits >= min_credits),
    CONSTRAINT chk_registration_preferences_boolean CHECK (avoid_saturday IN (0,1) AND compact_days IN (0,1) AND minimize_gaps IN (0,1)),
    CONSTRAINT chk_registration_preferences_preferred_free_days_json CHECK (preferred_free_days IS NULL OR ISJSON(preferred_free_days) = 1)
);
GO

-- Table: schedule_generations
CREATE TABLE dbo.schedule_generations (
    id BIGINT IDENTITY(1,1) NOT NULL,
    registration_plan_id BIGINT NOT NULL,
    generation_no INT NOT NULL,
    algorithm_version NVARCHAR(50) NULL,
    generation_status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_candidates BIGINT NOT NULL DEFAULT 0,
    feasible_candidates BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME2(6) NULL,
    completed_at DATETIME2(6) NULL,
    error_message NVARCHAR(MAX) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_schedule_generations_plan_no UNIQUE (registration_plan_id, generation_no),
    CONSTRAINT fk_schedule_generations_plan FOREIGN KEY (registration_plan_id) REFERENCES dbo.registration_plans(id) ON DELETE CASCADE,
    CONSTRAINT chk_schedule_generations_counts CHECK (feasible_candidates <= total_candidates)
);
GO

-- Table: schedule_options
CREATE TABLE dbo.schedule_options (
    id BIGINT IDENTITY(1,1) NOT NULL,
    schedule_generation_id BIGINT NOT NULL,
    parent_option_id BIGINT NULL,
    option_name NVARCHAR(255) NULL,
    option_rank INT NULL,
    total_credits DECIMAL(5,1) NOT NULL DEFAULT 0.0,
    total_score DECIMAL(10,4) NULL,
    is_feasible BIT NOT NULL DEFAULT 1,
    option_type NVARCHAR(30) NOT NULL DEFAULT 'GENERATED',
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_options_generation FOREIGN KEY (schedule_generation_id) REFERENCES dbo.schedule_generations(id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_options_parent FOREIGN KEY (parent_option_id) REFERENCES dbo.schedule_options(id) ON DELETE NO ACTION,
    CONSTRAINT chk_schedule_options_feasible CHECK (is_feasible IN (0,1)),
    CONSTRAINT chk_schedule_options_credits CHECK (total_credits >= 0)
);
GO

-- Table: schedule_option_sections
CREATE TABLE dbo.schedule_option_sections (
    id BIGINT IDENTITY(1,1) NOT NULL,
    schedule_option_id BIGINT NOT NULL,
    course_section_id BIGINT NOT NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_schedule_option_sections_pair UNIQUE (schedule_option_id, course_section_id),
    CONSTRAINT fk_schedule_option_sections_option FOREIGN KEY (schedule_option_id) REFERENCES dbo.schedule_options(id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_option_sections_section FOREIGN KEY (course_section_id) REFERENCES dbo.course_sections(id) ON DELETE NO ACTION
);
GO

-- Table: schedule_option_evaluations
CREATE TABLE dbo.schedule_option_evaluations (
    id BIGINT IDENTITY(1,1) NOT NULL,
    schedule_option_id BIGINT NOT NULL,
    criterion_code NVARCHAR(50) NOT NULL,
    criterion_name NVARCHAR(150) NOT NULL,
    score_value DECIMAL(10,4) NOT NULL DEFAULT 0,
    weight_value DECIMAL(10,4) NOT NULL DEFAULT 0,
    explanation NVARCHAR(MAX) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_schedule_option_evaluations_criterion UNIQUE (schedule_option_id, criterion_code),
    CONSTRAINT fk_schedule_option_evaluations_option FOREIGN KEY (schedule_option_id) REFERENCES dbo.schedule_options(id) ON DELETE CASCADE
);
GO

-- Table: schedule_option_warnings
CREATE TABLE dbo.schedule_option_warnings (
    id BIGINT IDENTITY(1,1) NOT NULL,
    schedule_option_id BIGINT NOT NULL,
    warning_type NVARCHAR(50) NOT NULL,
    severity NVARCHAR(20) NOT NULL DEFAULT 'INFO',
    warning_message NVARCHAR(MAX) NOT NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_option_warnings_option FOREIGN KEY (schedule_option_id) REFERENCES dbo.schedule_options(id) ON DELETE CASCADE
);
GO

-- Table: saved_schedules
CREATE TABLE dbo.saved_schedules (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    schedule_option_id BIGINT NOT NULL,
    display_name NVARCHAR(255) NOT NULL,
    saved_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    updated_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT uq_saved_schedules_user_option UNIQUE (user_id, schedule_option_id),
    CONSTRAINT fk_saved_schedules_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT fk_saved_schedules_option FOREIGN KEY (schedule_option_id) REFERENCES dbo.schedule_options(id) ON DELETE NO ACTION
);
GO

-- Table: schedule_exports
CREATE TABLE dbo.schedule_exports (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    schedule_option_id BIGINT NOT NULL,
    export_format NVARCHAR(10) NOT NULL,
    file_path NVARCHAR(1024) NULL,
    export_status NVARCHAR(30) NOT NULL DEFAULT 'PENDING',
    file_size_bytes BIGINT NULL,
    exported_at DATETIME2(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_exports_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT fk_schedule_exports_option FOREIGN KEY (schedule_option_id) REFERENCES dbo.schedule_options(id) ON DELETE NO ACTION,
    CONSTRAINT chk_schedule_exports_format CHECK (export_format IN ('PNG','PDF'))
);
GO

-- Table: course_catalog_import_errors
CREATE TABLE dbo.course_catalog_import_errors (
    id BIGINT IDENTITY(1,1) NOT NULL,
    course_catalog_import_id BIGINT NOT NULL,
    row_number BIGINT NOT NULL,
    field_name NVARCHAR(100) NULL,
    error_code NVARCHAR(50) NULL,
    error_message NVARCHAR(MAX) NOT NULL,
    row_data NVARCHAR(MAX) NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_course_catalog_import_errors_import FOREIGN KEY (course_catalog_import_id) REFERENCES dbo.course_catalog_imports(id) ON DELETE CASCADE,
    CONSTRAINT chk_course_catalog_import_errors_row_data_json CHECK (row_data IS NULL OR ISJSON(row_data) = 1)
);
GO

-- Table: email_logs
CREATE TABLE dbo.email_logs (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NULL,
    recipient_email NVARCHAR(255) NOT NULL,
    email_type NVARCHAR(50) NOT NULL,
    provider NVARCHAR(100) NULL,
    provider_message_id NVARCHAR(255) NULL,
    send_status NVARCHAR(30) NOT NULL,
    error_message NVARCHAR(MAX) NULL,
    sent_at DATETIME2(6) NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_email_logs_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE SET NULL
);
GO

-- Table: ai_call_logs
CREATE TABLE dbo.ai_call_logs (
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NULL,
    question_generation_request_id BIGINT NULL,
    operation_type NVARCHAR(50) NOT NULL,
    provider NVARCHAR(100) NULL,
    model_name NVARCHAR(150) NULL,
    response_time_ms BIGINT NULL,
    prompt_tokens BIGINT NULL,
    completion_tokens BIGINT NULL,
    total_tokens BIGINT NULL,
    call_status NVARCHAR(30) NOT NULL,
    error_code NVARCHAR(100) NULL,
    error_message NVARCHAR(MAX) NULL,
    called_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_call_logs_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_ai_call_logs_request FOREIGN KEY (question_generation_request_id) REFERENCES dbo.question_generation_requests(id) ON DELETE SET NULL
);
GO

-- Table: file_processing_logs
CREATE TABLE dbo.file_processing_logs (
    id BIGINT IDENTITY(1,1) NOT NULL,
    learning_material_id BIGINT NULL,
    registration_import_id BIGINT NULL,
    course_catalog_import_id BIGINT NULL,
    processing_type NVARCHAR(50) NOT NULL,
    processing_status NVARCHAR(30) NOT NULL,
    duration_ms BIGINT NULL,
    error_message NVARCHAR(MAX) NULL,
    processed_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_file_processing_logs_material FOREIGN KEY (learning_material_id) REFERENCES dbo.learning_materials(id) ON DELETE SET NULL,
    CONSTRAINT fk_file_processing_logs_registration_import FOREIGN KEY (registration_import_id) REFERENCES dbo.registration_imports(id) ON DELETE SET NULL,
    CONSTRAINT fk_file_processing_logs_catalog_import FOREIGN KEY (course_catalog_import_id) REFERENCES dbo.course_catalog_imports(id) ON DELETE SET NULL,
    CONSTRAINT chk_file_processing_logs_one_source CHECK (
                (CASE WHEN learning_material_id IS NOT NULL THEN 1 ELSE 0 END) +
                (CASE WHEN registration_import_id IS NOT NULL THEN 1 ELSE 0 END) +
                (CASE WHEN course_catalog_import_id IS NOT NULL THEN 1 ELSE 0 END) <= 1
            )
);
GO

-- Table: activity_logs
CREATE TABLE dbo.activity_logs (
    id BIGINT IDENTITY(1,1) NOT NULL,
    actor_user_id BIGINT NULL,
    target_user_id BIGINT NULL,
    action_code NVARCHAR(100) NOT NULL,
    entity_type NVARCHAR(100) NULL,
    entity_id BIGINT NULL,
    detail NVARCHAR(MAX) NULL,
    ip_address NVARCHAR(45) NULL,
    created_at DATETIME2(6) NOT NULL DEFAULT (SYSDATETIME()),
    PRIMARY KEY (id),
    CONSTRAINT fk_activity_logs_actor FOREIGN KEY (actor_user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT fk_activity_logs_target FOREIGN KEY (target_user_id) REFERENCES dbo.users(id) ON DELETE NO ACTION,
    CONSTRAINT chk_activity_logs_detail_json CHECK (detail IS NULL OR ISJSON(detail) = 1)
);
GO

-- Seed the two supported login roles.
IF NOT EXISTS (SELECT 1 FROM dbo.roles WHERE code = N'STUDENT')
    INSERT INTO dbo.roles (code, name, description)
    VALUES (N'STUDENT', N'Sinh viên', N'Tài khoản người học');

IF NOT EXISTS (SELECT 1 FROM dbo.roles WHERE code = N'ADMIN')
    INSERT INTO dbo.roles (code, name, description)
    VALUES (N'ADMIN', N'Quản trị viên', N'Tài khoản quản trị hệ thống');
GO

-- Indexes converted from MySQL KEY definitions.
-- Filtered unique indexes preserve MySQL's behavior for nullable UNIQUE columns.
CREATE UNIQUE NONCLUSTERED INDEX uq_users_student_code
    ON dbo.users (student_code)
    WHERE student_code IS NOT NULL;

CREATE UNIQUE NONCLUSTERED INDEX uq_schedule_options_generation_rank
    ON dbo.schedule_options (schedule_generation_id, option_rank)
    WHERE option_rank IS NOT NULL;


CREATE NONCLUSTERED INDEX idx_users_full_name ON dbo.users (full_name);
CREATE NONCLUSTERED INDEX idx_users_account_status ON dbo.users (account_status);
CREATE NONCLUSTERED INDEX idx_users_role_id ON dbo.users (role_id);
CREATE NONCLUSTERED INDEX idx_email_otps_user_created ON dbo.email_otps (user_id, created_at);
CREATE NONCLUSTERED INDEX idx_email_otps_expires_at ON dbo.email_otps (expires_at);
CREATE NONCLUSTERED INDEX idx_password_reset_user ON dbo.password_reset_tokens (user_id);
CREATE NONCLUSTERED INDEX idx_password_reset_expires ON dbo.password_reset_tokens (expires_at);
CREATE NONCLUSTERED INDEX idx_user_sessions_user_expiry ON dbo.user_sessions (user_id, expires_at);
CREATE NONCLUSTERED INDEX idx_learning_materials_user_status ON dbo.learning_materials (user_id, processing_status);
CREATE NONCLUSTERED INDEX idx_learning_materials_uploaded_at ON dbo.learning_materials (uploaded_at);
CREATE NONCLUSTERED INDEX idx_qgr_user_requested ON dbo.question_generation_requests (user_id, requested_at);
CREATE NONCLUSTERED INDEX idx_qgr_material ON dbo.question_generation_requests (learning_material_id);
CREATE NONCLUSTERED INDEX idx_qgr_status ON dbo.question_generation_requests (status);
CREATE NONCLUSTERED INDEX idx_question_sets_user_saved ON dbo.question_sets (user_id, saved_at);
CREATE NONCLUSTERED INDEX idx_question_sets_generation_request ON dbo.question_sets (generation_request_id);
CREATE NONCLUSTERED INDEX idx_question_sets_source_material ON dbo.question_sets (source_material_id);
CREATE NONCLUSTERED INDEX idx_questions_source_material ON dbo.questions (source_material_id);
CREATE NONCLUSTERED INDEX idx_questions_replaces ON dbo.questions (replaces_question_id);
CREATE NONCLUSTERED INDEX idx_question_options_question ON dbo.question_options (question_id);
CREATE NONCLUSTERED INDEX idx_quiz_tests_user_created ON dbo.quiz_tests (user_id, created_at);
CREATE NONCLUSTERED INDEX idx_quiz_tests_question_set ON dbo.quiz_tests (question_set_id);
CREATE NONCLUSTERED INDEX idx_quiz_test_questions_question ON dbo.quiz_test_questions (question_id);
CREATE NONCLUSTERED INDEX idx_quiz_attempts_user_started ON dbo.quiz_attempts (user_id, started_at);
CREATE NONCLUSTERED INDEX idx_quiz_attempts_test ON dbo.quiz_attempts (quiz_test_id);
CREATE NONCLUSTERED INDEX idx_quiz_attempt_answers_option ON dbo.quiz_attempt_answers (selected_option_id);
CREATE NONCLUSTERED INDEX idx_review_sessions_user_started ON dbo.review_sessions (user_id, started_at);
CREATE NONCLUSTERED INDEX idx_review_sessions_source_attempt ON dbo.review_sessions (source_quiz_attempt_id);
CREATE NONCLUSTERED INDEX idx_review_sessions_source_set ON dbo.review_sessions (source_question_set_id);
CREATE NONCLUSTERED INDEX idx_review_items_question ON dbo.review_session_items (question_id);
CREATE NONCLUSTERED INDEX idx_review_items_source_answer ON dbo.review_session_items (source_attempt_answer_id);
CREATE NONCLUSTERED INDEX idx_review_attempts_item_time ON dbo.review_attempts (review_session_item_id, attempted_at);
CREATE NONCLUSTERED INDEX idx_review_attempts_selected_option ON dbo.review_attempts (selected_option_id);
CREATE NONCLUSTERED INDEX idx_semesters_academic_year_term ON dbo.semesters (academic_year, term_no);
CREATE NONCLUSTERED INDEX idx_courses_name ON dbo.courses (course_name);
CREATE NONCLUSTERED INDEX idx_registration_imports_user_created ON dbo.registration_imports (user_id, created_at);
CREATE NONCLUSTERED INDEX idx_registration_imports_semester ON dbo.registration_imports (semester_id);
CREATE NONCLUSTERED INDEX idx_course_catalog_imports_admin_created ON dbo.course_catalog_imports (admin_user_id, created_at);
CREATE NONCLUSTERED INDEX idx_course_catalog_imports_semester ON dbo.course_catalog_imports (semester_id);
CREATE NONCLUSTERED INDEX idx_student_course_records_user_status ON dbo.student_course_records (user_id, result_status);
CREATE NONCLUSTERED INDEX idx_student_course_records_course ON dbo.student_course_records (course_id);
CREATE NONCLUSTERED INDEX idx_student_course_records_semester ON dbo.student_course_records (semester_id);
CREATE NONCLUSTERED INDEX idx_student_course_records_import ON dbo.student_course_records (source_import_id);
CREATE NONCLUSTERED INDEX idx_course_sections_course ON dbo.course_sections (course_id);
CREATE NONCLUSTERED INDEX idx_section_meetings_section ON dbo.section_meetings (course_section_id);
CREATE NONCLUSTERED INDEX idx_section_meetings_room_time ON dbo.section_meetings (room_id, weekday, start_period, end_period);
CREATE NONCLUSTERED INDEX idx_course_prerequisites_prerequisite ON dbo.course_prerequisites (prerequisite_course_id);
CREATE NONCLUSTERED INDEX idx_course_prerequisites_created_by ON dbo.course_prerequisites (created_by);
CREATE NONCLUSTERED INDEX idx_registration_import_errors_import_row ON dbo.registration_import_errors (registration_import_id, row_number);
CREATE NONCLUSTERED INDEX idx_registration_plans_user_semester ON dbo.registration_plans (user_id, semester_id);
CREATE NONCLUSTERED INDEX idx_registration_plans_source_import ON dbo.registration_plans (source_import_id);
CREATE NONCLUSTERED INDEX idx_registration_plan_courses_course ON dbo.registration_plan_courses (course_id);
CREATE NONCLUSTERED INDEX idx_schedule_options_parent ON dbo.schedule_options (parent_option_id);
CREATE NONCLUSTERED INDEX idx_schedule_option_sections_section ON dbo.schedule_option_sections (course_section_id);
CREATE NONCLUSTERED INDEX idx_schedule_option_warnings_option ON dbo.schedule_option_warnings (schedule_option_id);
CREATE NONCLUSTERED INDEX idx_saved_schedules_user_saved ON dbo.saved_schedules (user_id, saved_at);
CREATE NONCLUSTERED INDEX idx_schedule_exports_user_exported ON dbo.schedule_exports (user_id, exported_at);
CREATE NONCLUSTERED INDEX idx_schedule_exports_option ON dbo.schedule_exports (schedule_option_id);
CREATE NONCLUSTERED INDEX idx_course_catalog_import_errors_import_row ON dbo.course_catalog_import_errors (course_catalog_import_id, row_number);
CREATE NONCLUSTERED INDEX idx_email_logs_user_created ON dbo.email_logs (user_id, created_at);
CREATE NONCLUSTERED INDEX idx_email_logs_status_created ON dbo.email_logs (send_status, created_at);
CREATE NONCLUSTERED INDEX idx_ai_call_logs_user_called ON dbo.ai_call_logs (user_id, called_at);
CREATE NONCLUSTERED INDEX idx_ai_call_logs_request ON dbo.ai_call_logs (question_generation_request_id);
CREATE NONCLUSTERED INDEX idx_ai_call_logs_status_called ON dbo.ai_call_logs (call_status, called_at);
CREATE NONCLUSTERED INDEX idx_file_processing_logs_material ON dbo.file_processing_logs (learning_material_id);
CREATE NONCLUSTERED INDEX idx_file_processing_logs_registration_import ON dbo.file_processing_logs (registration_import_id);
CREATE NONCLUSTERED INDEX idx_file_processing_logs_catalog_import ON dbo.file_processing_logs (course_catalog_import_id);
CREATE NONCLUSTERED INDEX idx_file_processing_logs_status_date ON dbo.file_processing_logs (processing_status, processed_at);
CREATE NONCLUSTERED INDEX idx_activity_logs_actor_created ON dbo.activity_logs (actor_user_id, created_at);
CREATE NONCLUSTERED INDEX idx_activity_logs_target_created ON dbo.activity_logs (target_user_id, created_at);
CREATE NONCLUSTERED INDEX idx_activity_logs_entity ON dbo.activity_logs (entity_type, entity_id);
CREATE NONCLUSTERED INDEX idx_activity_logs_action_created ON dbo.activity_logs (action_code, created_at);
GO

/*
   Triggers to emulate MySQL's ON UPDATE CURRENT_TIMESTAMP(6).
   DATETIME2(6) columns are refreshed whenever a row is updated.
*/

CREATE OR ALTER TRIGGER dbo.trg_roles_updated_at
ON dbo.roles
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.roles AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_users_updated_at
ON dbo.users
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.users AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_learning_materials_updated_at
ON dbo.learning_materials
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.learning_materials AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_question_sets_updated_at
ON dbo.question_sets
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.question_sets AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_questions_updated_at
ON dbo.questions
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.questions AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_courses_updated_at
ON dbo.courses
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.courses AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_student_course_records_updated_at
ON dbo.student_course_records
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.student_course_records AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_course_sections_updated_at
ON dbo.course_sections
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.course_sections AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_registration_plans_updated_at
ON dbo.registration_plans
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.registration_plans AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_registration_preferences_updated_at
ON dbo.registration_preferences
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.registration_preferences AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_schedule_options_updated_at
ON dbo.schedule_options
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.schedule_options AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

CREATE OR ALTER TRIGGER dbo.trg_saved_schedules_updated_at
ON dbo.saved_schedules
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE target
       SET updated_at = SYSDATETIME()
    FROM dbo.saved_schedules AS target
    INNER JOIN inserted AS i ON i.id = target.id;
END;
GO

-- Final validation: expected 44 tables, 75 foreign keys, and two login roles.
SELECT COUNT(*) AS total_tables
FROM sys.tables
WHERE is_ms_shipped = 0;

SELECT COUNT(*) AS total_foreign_keys
FROM sys.foreign_keys;

SELECT code, name
FROM dbo.roles
ORDER BY code;
GO

/* =============================================================
   End of SmartEdu SQL Server schema.
   Notes:
   1. Passwords, OTPs, sessions and reset tokens must be hashed before storage.
   2. File contents remain in file/object storage; storage_path stores the location.
   3. Business rules such as timetable conflict checks, prerequisite cycle prevention,
      and checking that a user has STUDENT or ADMIN role must be validated by services.
   4. Instructor is not a login role. course_sections.instructor_name is display text.
   5. Self-referencing links (question replacement / parent schedule option) use NO ACTION.
      Clear dependent links in application code before deleting referenced rows.
   6. activity_logs user references use NO ACTION to preserve audit integrity;
      clear actor_user_id and target_user_id before hard-deleting a referenced user.
   7. JSON columns use NVARCHAR(MAX) with ISJSON checks (SQL Server 2016+).
   8. This script creates a fresh database named SmartEdu_Connect_Retry.
   ============================================================= */
