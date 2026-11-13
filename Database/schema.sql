-- =============================================================
-- SmartEdu - MySQL 8.4 compatible relational database schema
-- Target: MySQL Server 8.4 | InnoDB | utf8mb4
-- Tables: 44 | Login roles: STUDENT and ADMIN
--
-- IMPORTANT:
-- 1. Run this script against an EMPTY schema named smartedu_demo.
-- 2. This script does NOT DROP an existing database or tables.
-- 3. Do not rerun after partial execution without first checking the schema.
-- 4. Business rules involving course prerequisite self-reference and
--    mutually exclusive file-processing sources must be validated in
--    the application service layer.
-- =============================================================

CREATE DATABASE IF NOT EXISTS smartedu_demo
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE smartedu_demo;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------
-- 1. ACCOUNTS AND AUTHENTICATION
-- -------------------------------------------------------------
CREATE TABLE roles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(50) NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_roles_code (code),
  UNIQUE KEY uq_roles_name (name)
) ENGINE=InnoDB;

INSERT INTO roles (code, name, description) VALUES
  ('STUDENT', 'Sinh viên', 'Tài khoản người học'),
  ('ADMIN', 'Quản trị viên', 'Tài khoản quản trị hệ thống');

CREATE TABLE users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role_id BIGINT UNSIGNED NOT NULL,
  student_code VARCHAR(30) NULL,
  username VARCHAR(100) NOT NULL,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(255) NOT NULL,
  phone VARCHAR(20) NULL,
  address VARCHAR(500) NULL,
  gender VARCHAR(20) NULL,
  password_hash VARCHAR(255) NOT NULL,
  avatar_url VARCHAR(1000) NULL,
  email_verified_at DATETIME(6) NULL,
  account_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  locked_at DATETIME(6) NULL,
  last_login_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_student_code (student_code),
  UNIQUE KEY uq_users_username (username),
  UNIQUE KEY uq_users_email (email),
  KEY idx_users_full_name (full_name),
  KEY idx_users_account_status (account_status),
  KEY idx_users_role_id (role_id),
  CONSTRAINT fk_users_role FOREIGN KEY (role_id)
    REFERENCES roles(id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE email_otps (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  email VARCHAR(255) NOT NULL,
  otp_hash VARCHAR(255) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) NULL,
  attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_email_otps_user_created (user_id, created_at),
  KEY idx_email_otps_expires_at (expires_at),
  CONSTRAINT fk_email_otps_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_email_otps_attempt_count CHECK (attempt_count >= 0)
) ENGINE=InnoDB;

CREATE TABLE password_reset_tokens (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_password_reset_token_hash (token_hash),
  KEY idx_password_reset_user (user_id),
  KEY idx_password_reset_expires (expires_at),
  CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE user_sessions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  ip_address VARCHAR(45) NULL,
  user_agent VARCHAR(512) NULL,
  last_activity_at DATETIME(6) NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_sessions_token_hash (token_hash),
  KEY idx_user_sessions_user_expiry (user_id, expires_at),
  CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 2. LEARNING MATERIALS AND AI QUESTION GENERATION
-- -------------------------------------------------------------
CREATE TABLE learning_materials (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(255) NOT NULL,
  original_file_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(1024) NOT NULL,
  file_extension VARCHAR(20) NOT NULL,
  mime_type VARCHAR(150) NULL,
  file_size_bytes BIGINT UNSIGNED NOT NULL,
  processing_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  uploaded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_learning_materials_user_status (user_id, processing_status),
  KEY idx_learning_materials_uploaded_at (uploaded_at),
  CONSTRAINT fk_learning_materials_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_learning_materials_file_size CHECK (file_size_bytes >= 0)
) ENGINE=InnoDB;

CREATE TABLE material_contents (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  learning_material_id BIGINT UNSIGNED NOT NULL,
  extracted_text LONGTEXT NULL,
  page_count INT UNSIGNED NULL,
  extraction_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  extraction_version VARCHAR(50) NULL,
  extracted_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_material_contents_material (learning_material_id),
  CONSTRAINT fk_material_contents_material FOREIGN KEY (learning_material_id)
    REFERENCES learning_materials(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_material_contents_page_count CHECK (page_count IS NULL OR page_count >= 0)
) ENGINE=InnoDB;

CREATE TABLE question_generation_requests (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  learning_material_id BIGINT UNSIGNED NOT NULL,
  content_scope VARCHAR(30) NOT NULL,
  scope_description TEXT NULL,
  question_type VARCHAR(30) NOT NULL,
  question_count SMALLINT UNSIGNED NOT NULL,
  difficulty VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at DATETIME(6) NULL,
  error_message TEXT NULL,
  PRIMARY KEY (id),
  KEY idx_qgr_user_requested (user_id, requested_at),
  KEY idx_qgr_material (learning_material_id),
  KEY idx_qgr_status (status),
  CONSTRAINT fk_qgr_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_qgr_material FOREIGN KEY (learning_material_id)
    REFERENCES learning_materials(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_qgr_question_count CHECK (question_count > 0)
) ENGINE=InnoDB;

CREATE TABLE question_sets (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  generation_request_id BIGINT UNSIGNED NULL,
  source_material_id BIGINT UNSIGNED NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT NULL,
  set_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  saved_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_question_sets_user_saved (user_id, saved_at),
  KEY idx_question_sets_generation_request (generation_request_id),
  KEY idx_question_sets_source_material (source_material_id),
  CONSTRAINT fk_question_sets_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_question_sets_generation_request FOREIGN KEY (generation_request_id)
    REFERENCES question_generation_requests(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_question_sets_source_material FOREIGN KEY (source_material_id)
    REFERENCES learning_materials(id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE questions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  question_set_id BIGINT UNSIGNED NOT NULL,
  source_material_id BIGINT UNSIGNED NULL,
  replaces_question_id BIGINT UNSIGNED NULL,
  question_type VARCHAR(30) NOT NULL,
  content LONGTEXT NOT NULL,
  explanation LONGTEXT NULL,
  difficulty VARCHAR(20) NULL,
  reference_excerpt TEXT NULL,
  reference_page INT UNSIGNED NULL,
  display_order SMALLINT UNSIGNED NOT NULL,
  question_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_questions_set_order (question_set_id, display_order),
  KEY idx_questions_source_material (source_material_id),
  KEY idx_questions_replaces (replaces_question_id),
  CONSTRAINT fk_questions_question_set FOREIGN KEY (question_set_id)
    REFERENCES question_sets(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_questions_source_material FOREIGN KEY (source_material_id)
    REFERENCES learning_materials(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_questions_replaces FOREIGN KEY (replaces_question_id)
    REFERENCES questions(id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE question_options (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  question_id BIGINT UNSIGNED NOT NULL,
  option_label VARCHAR(10) NOT NULL,
  content LONGTEXT NOT NULL,
  is_correct TINYINT NOT NULL DEFAULT 0,
  display_order SMALLINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_question_options_label (question_id, option_label),
  UNIQUE KEY uq_question_options_order (question_id, display_order),
  KEY idx_question_options_question (question_id),
  CONSTRAINT fk_question_options_question FOREIGN KEY (question_id)
    REFERENCES questions(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_question_options_is_correct CHECK (is_correct IN (0,1))
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 3. SELF-PRACTICE TESTS AND REVIEW
-- -------------------------------------------------------------
CREATE TABLE quiz_tests (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  question_set_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(255) NOT NULL,
  duration_minutes SMALLINT UNSIGNED NOT NULL,
  total_score DECIMAL(8,2) NOT NULL DEFAULT 100.00,
  test_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_quiz_tests_user_created (user_id, created_at),
  KEY idx_quiz_tests_question_set (question_set_id),
  CONSTRAINT fk_quiz_tests_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_quiz_tests_question_set FOREIGN KEY (question_set_id)
    REFERENCES question_sets(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_quiz_tests_duration CHECK (duration_minutes > 0),
  CONSTRAINT chk_quiz_tests_total_score CHECK (total_score >= 0)
) ENGINE=InnoDB;

CREATE TABLE quiz_test_questions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  quiz_test_id BIGINT UNSIGNED NOT NULL,
  question_id BIGINT UNSIGNED NOT NULL,
  display_order SMALLINT UNSIGNED NOT NULL,
  max_score DECIMAL(8,2) NOT NULL DEFAULT 1.00,
  PRIMARY KEY (id),
  UNIQUE KEY uq_quiz_test_questions_question (quiz_test_id, question_id),
  UNIQUE KEY uq_quiz_test_questions_order (quiz_test_id, display_order),
  KEY idx_quiz_test_questions_question (question_id),
  CONSTRAINT fk_quiz_test_questions_test FOREIGN KEY (quiz_test_id)
    REFERENCES quiz_tests(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_quiz_test_questions_question FOREIGN KEY (question_id)
    REFERENCES questions(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_quiz_test_questions_max_score CHECK (max_score >= 0)
) ENGINE=InnoDB;

CREATE TABLE quiz_attempts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  quiz_test_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  submitted_at DATETIME(6) NULL,
  duration_seconds INT UNSIGNED NULL,
  attempt_status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
  total_score DECIMAL(8,2) NULL,
  correct_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  incorrect_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  unanswered_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_quiz_attempts_user_started (user_id, started_at),
  KEY idx_quiz_attempts_test (quiz_test_id),
  CONSTRAINT fk_quiz_attempts_test FOREIGN KEY (quiz_test_id)
    REFERENCES quiz_tests(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_quiz_attempts_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_quiz_attempts_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
) ENGINE=InnoDB;

CREATE TABLE quiz_attempt_answers (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  quiz_attempt_id BIGINT UNSIGNED NOT NULL,
  quiz_test_question_id BIGINT UNSIGNED NOT NULL,
  selected_option_id BIGINT UNSIGNED NULL,
  answer_text LONGTEXT NULL,
  is_correct TINYINT NULL,
  awarded_score DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  question_content_snapshot LONGTEXT NULL,
  correct_answer_snapshot LONGTEXT NULL,
  explanation_snapshot LONGTEXT NULL,
  answered_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_quiz_attempt_answer_question (quiz_attempt_id, quiz_test_question_id),
  KEY idx_quiz_attempt_answers_option (selected_option_id),
  CONSTRAINT fk_quiz_attempt_answers_attempt FOREIGN KEY (quiz_attempt_id)
    REFERENCES quiz_attempts(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_quiz_attempt_answers_test_question FOREIGN KEY (quiz_test_question_id)
    REFERENCES quiz_test_questions(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_quiz_attempt_answers_selected_option FOREIGN KEY (selected_option_id)
    REFERENCES question_options(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT chk_quiz_attempt_answers_is_correct CHECK (is_correct IS NULL OR is_correct IN (0,1)),
  CONSTRAINT chk_quiz_attempt_answers_score CHECK (awarded_score >= 0)
) ENGINE=InnoDB;

CREATE TABLE review_sessions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  source_quiz_attempt_id BIGINT UNSIGNED NULL,
  source_question_set_id BIGINT UNSIGNED NULL,
  session_status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
  started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ended_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_review_sessions_user_started (user_id, started_at),
  KEY idx_review_sessions_source_attempt (source_quiz_attempt_id),
  KEY idx_review_sessions_source_set (source_question_set_id),
  CONSTRAINT fk_review_sessions_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_review_sessions_source_attempt FOREIGN KEY (source_quiz_attempt_id)
    REFERENCES quiz_attempts(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_review_sessions_source_set FOREIGN KEY (source_question_set_id)
    REFERENCES question_sets(id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE review_session_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  review_session_id BIGINT UNSIGNED NOT NULL,
  question_id BIGINT UNSIGNED NOT NULL,
  source_attempt_answer_id BIGINT UNSIGNED NULL,
  review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  last_is_correct TINYINT NULL,
  last_reviewed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_review_session_items_question (review_session_id, question_id),
  KEY idx_review_items_question (question_id),
  KEY idx_review_items_source_answer (source_attempt_answer_id),
  CONSTRAINT fk_review_items_session FOREIGN KEY (review_session_id)
    REFERENCES review_sessions(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_review_items_question FOREIGN KEY (question_id)
    REFERENCES questions(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_review_items_source_answer FOREIGN KEY (source_attempt_answer_id)
    REFERENCES quiz_attempt_answers(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT chk_review_items_last_correct CHECK (last_is_correct IS NULL OR last_is_correct IN (0,1))
) ENGINE=InnoDB;

CREATE TABLE review_attempts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  review_session_item_id BIGINT UNSIGNED NOT NULL,
  selected_option_id BIGINT UNSIGNED NULL,
  answer_text LONGTEXT NULL,
  is_correct TINYINT NOT NULL,
  attempted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_review_attempts_item_time (review_session_item_id, attempted_at),
  KEY idx_review_attempts_selected_option (selected_option_id),
  CONSTRAINT fk_review_attempts_item FOREIGN KEY (review_session_item_id)
    REFERENCES review_session_items(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_review_attempts_option FOREIGN KEY (selected_option_id)
    REFERENCES question_options(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT chk_review_attempts_is_correct CHECK (is_correct IN (0,1))
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 4. ACADEMIC CATALOGUE AND STUDY RECORDS
-- -------------------------------------------------------------
CREATE TABLE semesters (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  semester_code VARCHAR(30) NOT NULL,
  semester_name VARCHAR(100) NOT NULL,
  academic_year VARCHAR(20) NOT NULL,
  term_no TINYINT UNSIGNED NOT NULL,
  start_date DATE NULL,
  end_date DATE NULL,
  registration_start_at DATETIME(6) NULL,
  registration_end_at DATETIME(6) NULL,
  semester_status VARCHAR(30) NOT NULL DEFAULT 'PLANNING',
  PRIMARY KEY (id),
  UNIQUE KEY uq_semesters_code (semester_code),
  KEY idx_semesters_academic_year_term (academic_year, term_no),
  CONSTRAINT chk_semesters_term_no CHECK (term_no > 0),
  CONSTRAINT chk_semesters_dates CHECK (start_date IS NULL OR end_date IS NULL OR start_date <= end_date)
) ENGINE=InnoDB;

CREATE TABLE courses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  course_code VARCHAR(30) NOT NULL,
  course_name VARCHAR(255) NOT NULL,
  credits DECIMAL(4,1) NOT NULL,
  description TEXT NULL,
  is_active TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_courses_code (course_code),
  KEY idx_courses_name (course_name),
  CONSTRAINT chk_courses_credits CHECK (credits > 0),
  CONSTRAINT chk_courses_is_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB;

CREATE TABLE campuses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  campus_code VARCHAR(30) NOT NULL,
  campus_name VARCHAR(150) NOT NULL,
  address VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_campuses_code (campus_code),
  UNIQUE KEY uq_campuses_name (campus_name)
) ENGINE=InnoDB;

CREATE TABLE rooms (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  campus_id BIGINT UNSIGNED NOT NULL,
  room_code VARCHAR(30) NOT NULL,
  room_name VARCHAR(100) NULL,
  capacity SMALLINT UNSIGNED NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_rooms_campus_code (campus_id, room_code),
  CONSTRAINT fk_rooms_campus FOREIGN KEY (campus_id)
    REFERENCES campuses(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_rooms_capacity CHECK (capacity IS NULL OR capacity > 0)
) ENGINE=InnoDB;

CREATE TABLE registration_imports (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  semester_id BIGINT UNSIGNED NOT NULL,
  import_type VARCHAR(30) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(1024) NOT NULL,
  file_format VARCHAR(20) NOT NULL,
  import_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  total_rows INT UNSIGNED NOT NULL DEFAULT 0,
  valid_rows INT UNSIGNED NOT NULL DEFAULT 0,
  error_rows INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  processed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_registration_imports_user_created (user_id, created_at),
  KEY idx_registration_imports_semester (semester_id),
  CONSTRAINT fk_registration_imports_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_registration_imports_semester FOREIGN KEY (semester_id)
    REFERENCES semesters(id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE course_catalog_imports (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  admin_user_id BIGINT UNSIGNED NOT NULL,
  semester_id BIGINT UNSIGNED NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(1024) NOT NULL,
  import_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  total_rows INT UNSIGNED NOT NULL DEFAULT 0,
  success_rows INT UNSIGNED NOT NULL DEFAULT 0,
  error_rows INT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_course_catalog_imports_admin_created (admin_user_id, created_at),
  KEY idx_course_catalog_imports_semester (semester_id),
  CONSTRAINT fk_course_catalog_imports_admin FOREIGN KEY (admin_user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_course_catalog_imports_semester FOREIGN KEY (semester_id)
    REFERENCES semesters(id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE student_course_records (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  course_id BIGINT UNSIGNED NOT NULL,
  semester_id BIGINT UNSIGNED NULL,
  source_import_id BIGINT UNSIGNED NULL,
  grade DECIMAL(5,2) NULL,
  result_status VARCHAR(30) NOT NULL,
  completed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_student_course_records_user_status (user_id, result_status),
  KEY idx_student_course_records_course (course_id),
  KEY idx_student_course_records_semester (semester_id),
  KEY idx_student_course_records_import (source_import_id),
  CONSTRAINT fk_student_course_records_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_student_course_records_course FOREIGN KEY (course_id)
    REFERENCES courses(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_student_course_records_semester FOREIGN KEY (semester_id)
    REFERENCES semesters(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_student_course_records_import FOREIGN KEY (source_import_id)
    REFERENCES registration_imports(id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE course_sections (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  semester_id BIGINT UNSIGNED NOT NULL,
  course_id BIGINT UNSIGNED NOT NULL,
  section_code VARCHAR(50) NOT NULL,
  instructor_name VARCHAR(150) NULL,
  max_capacity SMALLINT UNSIGNED NOT NULL,
  current_enrollment SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  section_status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_course_sections_semester_code (semester_id, section_code),
  KEY idx_course_sections_course (course_id),
  CONSTRAINT fk_course_sections_semester FOREIGN KEY (semester_id)
    REFERENCES semesters(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_course_sections_course FOREIGN KEY (course_id)
    REFERENCES courses(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_course_sections_capacity CHECK (max_capacity > 0 AND current_enrollment <= max_capacity)
) ENGINE=InnoDB;

CREATE TABLE section_meetings (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  course_section_id BIGINT UNSIGNED NOT NULL,
  weekday TINYINT UNSIGNED NOT NULL,
  start_period TINYINT UNSIGNED NOT NULL,
  end_period TINYINT UNSIGNED NOT NULL,
  start_time TIME NULL,
  end_time TIME NULL,
  room_id BIGINT UNSIGNED NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_section_meetings_section (course_section_id),
  KEY idx_section_meetings_room_time (room_id, weekday, start_period, end_period),
  CONSTRAINT fk_section_meetings_section FOREIGN KEY (course_section_id)
    REFERENCES course_sections(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_section_meetings_room FOREIGN KEY (room_id)
    REFERENCES rooms(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT chk_section_meetings_weekday CHECK (weekday BETWEEN 1 AND 7),
  CONSTRAINT chk_section_meetings_periods CHECK (start_period > 0 AND end_period >= start_period),
  CONSTRAINT chk_section_meetings_time CHECK (start_time IS NULL OR end_time IS NULL OR start_time < end_time)
) ENGINE=InnoDB;

CREATE TABLE course_prerequisites (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  course_id BIGINT UNSIGNED NOT NULL,
  prerequisite_course_id BIGINT UNSIGNED NOT NULL,
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_course_prerequisites_pair (course_id, prerequisite_course_id),
  KEY idx_course_prerequisites_prerequisite (prerequisite_course_id),
  KEY idx_course_prerequisites_created_by (created_by),
  CONSTRAINT fk_course_prerequisites_course FOREIGN KEY (course_id)
    REFERENCES courses(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_course_prerequisites_prerequisite FOREIGN KEY (prerequisite_course_id)
    REFERENCES courses(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_course_prerequisites_created_by FOREIGN KEY (created_by)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL
  -- The rule course_id <> prerequisite_course_id is validated in the service layer.
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 5. REGISTRATION PLANNING AND TIMETABLE OPTIMIZATION
-- -------------------------------------------------------------
CREATE TABLE registration_import_errors (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  registration_import_id BIGINT UNSIGNED NOT NULL,
  `row_number` INT UNSIGNED NOT NULL,
  field_name VARCHAR(100) NULL,
  error_code VARCHAR(50) NULL,
  error_message TEXT NOT NULL,
  row_data JSON NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_registration_import_errors_import_row (registration_import_id, `row_number`),
  CONSTRAINT fk_registration_import_errors_import FOREIGN KEY (registration_import_id)
    REFERENCES registration_imports(id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE registration_plans (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  semester_id BIGINT UNSIGNED NOT NULL,
  source_import_id BIGINT UNSIGNED NULL,
  plan_name VARCHAR(255) NOT NULL,
  plan_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_registration_plans_user_semester (user_id, semester_id),
  KEY idx_registration_plans_source_import (source_import_id),
  CONSTRAINT fk_registration_plans_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_registration_plans_semester FOREIGN KEY (semester_id)
    REFERENCES semesters(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_registration_plans_source_import FOREIGN KEY (source_import_id)
    REFERENCES registration_imports(id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE registration_plan_courses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  registration_plan_id BIGINT UNSIGNED NOT NULL,
  course_id BIGINT UNSIGNED NOT NULL,
  selection_source VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_registration_plan_courses_pair (registration_plan_id, course_id),
  KEY idx_registration_plan_courses_course (course_id),
  CONSTRAINT fk_registration_plan_courses_plan FOREIGN KEY (registration_plan_id)
    REFERENCES registration_plans(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_registration_plan_courses_course FOREIGN KEY (course_id)
    REFERENCES courses(id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE registration_preferences (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  registration_plan_id BIGINT UNSIGNED NOT NULL,
  min_credits DECIMAL(5,1) NOT NULL,
  max_credits DECIMAL(5,1) NOT NULL,
  preferred_shift VARCHAR(20) NULL,
  avoid_saturday TINYINT NOT NULL DEFAULT 0,
  compact_days TINYINT NOT NULL DEFAULT 0,
  minimize_gaps TINYINT NOT NULL DEFAULT 0,
  preferred_free_days JSON NULL,
  max_gap_periods TINYINT UNSIGNED NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_registration_preferences_plan (registration_plan_id),
  CONSTRAINT fk_registration_preferences_plan FOREIGN KEY (registration_plan_id)
    REFERENCES registration_plans(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_registration_preferences_credit_range CHECK (min_credits >= 0 AND max_credits >= min_credits),
  CONSTRAINT chk_registration_preferences_boolean CHECK (avoid_saturday IN (0,1) AND compact_days IN (0,1) AND minimize_gaps IN (0,1))
) ENGINE=InnoDB;

CREATE TABLE schedule_generations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  registration_plan_id BIGINT UNSIGNED NOT NULL,
  generation_no SMALLINT UNSIGNED NOT NULL,
  algorithm_version VARCHAR(50) NULL,
  generation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  total_candidates INT UNSIGNED NOT NULL DEFAULT 0,
  feasible_candidates INT UNSIGNED NOT NULL DEFAULT 0,
  started_at DATETIME(6) NULL,
  completed_at DATETIME(6) NULL,
  error_message TEXT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_schedule_generations_plan_no (registration_plan_id, generation_no),
  CONSTRAINT fk_schedule_generations_plan FOREIGN KEY (registration_plan_id)
    REFERENCES registration_plans(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_schedule_generations_counts CHECK (feasible_candidates <= total_candidates)
) ENGINE=InnoDB;

CREATE TABLE schedule_options (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  schedule_generation_id BIGINT UNSIGNED NOT NULL,
  parent_option_id BIGINT UNSIGNED NULL,
  option_name VARCHAR(255) NULL,
  option_rank SMALLINT UNSIGNED NULL,
  total_credits DECIMAL(5,1) NOT NULL DEFAULT 0.0,
  total_score DECIMAL(10,4) NULL,
  is_feasible TINYINT NOT NULL DEFAULT 1,
  option_type VARCHAR(30) NOT NULL DEFAULT 'GENERATED',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_schedule_options_generation_rank (schedule_generation_id, option_rank),
  KEY idx_schedule_options_parent (parent_option_id),
  CONSTRAINT fk_schedule_options_generation FOREIGN KEY (schedule_generation_id)
    REFERENCES schedule_generations(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_schedule_options_parent FOREIGN KEY (parent_option_id)
    REFERENCES schedule_options(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT chk_schedule_options_feasible CHECK (is_feasible IN (0,1)),
  CONSTRAINT chk_schedule_options_credits CHECK (total_credits >= 0)
) ENGINE=InnoDB;

CREATE TABLE schedule_option_sections (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  schedule_option_id BIGINT UNSIGNED NOT NULL,
  course_section_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_schedule_option_sections_pair (schedule_option_id, course_section_id),
  KEY idx_schedule_option_sections_section (course_section_id),
  CONSTRAINT fk_schedule_option_sections_option FOREIGN KEY (schedule_option_id)
    REFERENCES schedule_options(id) ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_schedule_option_sections_section FOREIGN KEY (course_section_id)
    REFERENCES course_sections(id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE schedule_option_evaluations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  schedule_option_id BIGINT UNSIGNED NOT NULL,
  criterion_code VARCHAR(50) NOT NULL,
  criterion_name VARCHAR(150) NOT NULL,
  score_value DECIMAL(10,4) NOT NULL DEFAULT 0,
  weight_value DECIMAL(10,4) NOT NULL DEFAULT 0,
  explanation TEXT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_schedule_option_evaluations_criterion (schedule_option_id, criterion_code),
  CONSTRAINT fk_schedule_option_evaluations_option FOREIGN KEY (schedule_option_id)
    REFERENCES schedule_options(id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE schedule_option_warnings (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  schedule_option_id BIGINT UNSIGNED NOT NULL,
  warning_type VARCHAR(50) NOT NULL,
  severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
  warning_message TEXT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_schedule_option_warnings_option (schedule_option_id),
  CONSTRAINT fk_schedule_option_warnings_option FOREIGN KEY (schedule_option_id)
    REFERENCES schedule_options(id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE saved_schedules (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  schedule_option_id BIGINT UNSIGNED NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  saved_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uq_saved_schedules_user_option (user_id, schedule_option_id),
  KEY idx_saved_schedules_user_saved (user_id, saved_at),
  CONSTRAINT fk_saved_schedules_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_saved_schedules_option FOREIGN KEY (schedule_option_id)
    REFERENCES schedule_options(id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE schedule_exports (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  schedule_option_id BIGINT UNSIGNED NOT NULL,
  export_format VARCHAR(10) NOT NULL,
  file_path VARCHAR(1024) NULL,
  export_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  file_size_bytes BIGINT UNSIGNED NULL,
  exported_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_schedule_exports_user_exported (user_id, exported_at),
  KEY idx_schedule_exports_option (schedule_option_id),
  CONSTRAINT fk_schedule_exports_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_schedule_exports_option FOREIGN KEY (schedule_option_id)
    REFERENCES schedule_options(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_schedule_exports_format CHECK (export_format IN ('PNG','PDF'))
) ENGINE=InnoDB;

-- -------------------------------------------------------------
-- 6. ADMIN IMPORT ERRORS AND SYSTEM LOGS
-- -------------------------------------------------------------
CREATE TABLE course_catalog_import_errors (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  course_catalog_import_id BIGINT UNSIGNED NOT NULL,
  `row_number` INT UNSIGNED NOT NULL,
  field_name VARCHAR(100) NULL,
  error_code VARCHAR(50) NULL,
  error_message TEXT NOT NULL,
  row_data JSON NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_course_catalog_import_errors_import_row (course_catalog_import_id, `row_number`),
  CONSTRAINT fk_course_catalog_import_errors_import FOREIGN KEY (course_catalog_import_id)
    REFERENCES course_catalog_imports(id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE email_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NULL,
  recipient_email VARCHAR(255) NOT NULL,
  email_type VARCHAR(50) NOT NULL,
  provider VARCHAR(100) NULL,
  provider_message_id VARCHAR(255) NULL,
  send_status VARCHAR(30) NOT NULL,
  error_message TEXT NULL,
  sent_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_email_logs_user_created (user_id, created_at),
  KEY idx_email_logs_status_created (send_status, created_at),
  CONSTRAINT fk_email_logs_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE ai_call_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NULL,
  question_generation_request_id BIGINT UNSIGNED NULL,
  operation_type VARCHAR(50) NOT NULL,
  provider VARCHAR(100) NULL,
  model_name VARCHAR(150) NULL,
  response_time_ms INT UNSIGNED NULL,
  prompt_tokens INT UNSIGNED NULL,
  completion_tokens INT UNSIGNED NULL,
  total_tokens INT UNSIGNED NULL,
  call_status VARCHAR(30) NOT NULL,
  error_code VARCHAR(100) NULL,
  error_message TEXT NULL,
  called_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_ai_call_logs_user_called (user_id, called_at),
  KEY idx_ai_call_logs_request (question_generation_request_id),
  KEY idx_ai_call_logs_status_called (call_status, called_at),
  CONSTRAINT fk_ai_call_logs_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_ai_call_logs_request FOREIGN KEY (question_generation_request_id)
    REFERENCES question_generation_requests(id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE file_processing_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  learning_material_id BIGINT UNSIGNED NULL,
  registration_import_id BIGINT UNSIGNED NULL,
  course_catalog_import_id BIGINT UNSIGNED NULL,
  processing_type VARCHAR(50) NOT NULL,
  processing_status VARCHAR(30) NOT NULL,
  duration_ms INT UNSIGNED NULL,
  error_message TEXT NULL,
  processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_file_processing_logs_material (learning_material_id),
  KEY idx_file_processing_logs_registration_import (registration_import_id),
  KEY idx_file_processing_logs_catalog_import (course_catalog_import_id),
  KEY idx_file_processing_logs_status_date (processing_status, processed_at),
  CONSTRAINT fk_file_processing_logs_material FOREIGN KEY (learning_material_id)
    REFERENCES learning_materials(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_file_processing_logs_registration_import FOREIGN KEY (registration_import_id)
    REFERENCES registration_imports(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_file_processing_logs_catalog_import FOREIGN KEY (course_catalog_import_id)
    REFERENCES course_catalog_imports(id) ON UPDATE CASCADE ON DELETE SET NULL
  -- The rule allowing at most one non-NULL source is validated in the service layer.
) ENGINE=InnoDB;

CREATE TABLE activity_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT UNSIGNED NULL,
  target_user_id BIGINT UNSIGNED NULL,
  action_code VARCHAR(100) NOT NULL,
  entity_type VARCHAR(100) NULL,
  entity_id BIGINT UNSIGNED NULL,
  detail JSON NULL,
  ip_address VARCHAR(45) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_activity_logs_actor_created (actor_user_id, created_at),
  KEY idx_activity_logs_target_created (target_user_id, created_at),
  KEY idx_activity_logs_entity (entity_type, entity_id),
  KEY idx_activity_logs_action_created (action_code, created_at),
  CONSTRAINT fk_activity_logs_actor FOREIGN KEY (actor_user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT fk_activity_logs_target FOREIGN KEY (target_user_id)
    REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- POST-IMPORT CHECKS (run these separately after successful import)
-- =============================================================
-- SELECT VERSION() AS mysql_version, @@port AS server_port, DATABASE() AS database_name;
-- SELECT COUNT(*) AS total_tables
-- FROM information_schema.tables
-- WHERE table_schema = 'smartedu_demo' AND table_type = 'BASE TABLE';
-- SELECT * FROM smartedu_demo.roles;
-- =============================================================
-- NOTES:
-- 1. Passwords, OTPs and session/reset tokens must be hashed before storage.
-- 2. File contents belong in file/object storage; storage_path stores the location.
-- 3. Enforce course_id <> prerequisite_course_id in the service layer.
-- 4. Enforce at most one non-NULL file source in file_processing_logs in the service layer.
-- 5. Validate selected-option ownership, timetable conflicts, prerequisite cycles,
--    and student-only operations in the application service layer.
-- 6. Only STUDENT and ADMIN are login roles. Instructor names are display text in course_sections.
