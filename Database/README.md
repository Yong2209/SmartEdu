# SmartEdu database package (MySQL 8.4)

This package contains the corrected 44-table MySQL 8.4 schema and a complete fictional seed dataset covering all 44 tables.

## Files

- `schema.sql` — creates `smartedu_demo`, all 44 tables, indexes, foreign keys and supported checks. Run on a fresh/empty database only.
- `seed.sql` — inserts fictional development data into all 44 tables. Run after `schema.sql` succeeds.

## MySQL Workbench setup

1. Start MySQL Server 8.4.
2. Open MySQL Workbench and connect to the team's MySQL instance (for example `127.0.0.1`, port `3307`).
3. Open `schema.sql` with **File > Open SQL Script...** and execute the whole script once.
4. Confirm the schema has 44 tables:

```sql
SELECT COUNT(*) AS total_tables
FROM information_schema.tables
WHERE table_schema = 'smartedu_demo'
  AND table_type = 'BASE TABLE';
```

5. Open `seed.sql` and execute the whole script once.
6. Confirm sample data:

```sql
USE smartedu_demo;
SELECT * FROM roles;
SELECT id, username, email, account_status FROM users ORDER BY id;
SELECT course_code, course_name FROM courses ORDER BY course_code;
SELECT COUNT(*) AS question_count FROM questions;
SELECT COUNT(*) AS quiz_count FROM quiz_tests;
```

## Demo accounts

All demo accounts use the same local-development password: `Demo@12345`.

- `student.demo` / `student1@smartedu.test`
- `student.demo2` / `student2@smartedu.test`
- `admin.demo` / `admin@smartedu.test`

The `users.password_hash` values are BCrypt hashes. OTP, reset-token and session data are hashed placeholders for database/UI demonstration, not live credentials or valid sessions. These fictional accounts must never be used in production.

## Important notes

- `seed.sql` assumes that all 44 tables exist and should be run once on a fresh schema. It uses reserved demo IDs (9001–13403); do not rerun it after the application has begun using those IDs.
- If the team previously inserted data into this database, inspect existing rows before running the seed.
- Do not commit production data, real student information, database passwords, `.env` files or private backups.
- Each teammate configures their own local MySQL port, username and password in environment variables. The SQL package does not contain database server credentials.
