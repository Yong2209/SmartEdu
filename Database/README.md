# SmartEdu - Cài đặt cơ sở dữ liệu

## 1. Yêu cầu

- MySQL Server **8.4.x**.
- MySQL Workbench hoặc MySQL Client.
- Backend SmartEdu hiện được cấu hình mặc định kết nối:
  - Host: `127.0.0.1`
  - Port: `3307`
  - Database: `smartedu_demo`
  - Username: `root`

Nếu MySQL của bạn chạy port khác, hãy cập nhật cấu hình trong
`Be/demo/src/main/resources/application.properties`.

## 2. Cài đặt bằng MySQL Workbench

1. Khởi động MySQL Server.
2. Mở MySQL Workbench và kết nối tới server của nhóm.
3. Mở file `schema.sql` trong thư mục này.
4. Chạy toàn bộ file một lần bằng nút **Execute**.
5. Mở file `seed.sql` và chạy toàn bộ file một lần.
6. Chạy câu lệnh kiểm tra:

```sql
USE smartedu_demo;

SELECT COUNT(*) AS total_tables
FROM information_schema.tables
WHERE table_schema = 'smartedu_demo'
  AND table_type = 'BASE TABLE';

SELECT id, username, email, account_status
FROM users
ORDER BY id;

SELECT COUNT(*) AS question_count FROM questions;
SELECT COUNT(*) AS quiz_count FROM quiz_tests;
```

`total_tables` phải có giá trị **44**.

## 3. Cài đặt bằng dòng lệnh

Trên Windows, mở PowerShell tại thư mục gốc repository và chạy:

```powershell
mysql -h 127.0.0.1 -P 3307 -u root -p < .\Database\schema.sql
mysql -h 127.0.0.1 -P 3307 -u root -p < .\Database\seed.sql
```

Nhập mật khẩu MySQL khi được yêu cầu. Chỉ chạy `seed.sql` sau khi `schema.sql`
đã chạy thành công.

## 4. Tài khoản demo

Các tài khoản demo dùng cùng mật khẩu dành cho môi trường phát triển:
`Demo@12345`.

| Username | Email | Vai trò |
|---|---|---|
| `student.demo` | `student1@smartedu.test` | STUDENT |
| `student.demo2` | `student2@smartedu.test` | STUDENT |
| `admin.demo` | `admin@smartedu.test` | ADMIN |

Đây là dữ liệu giả lập, không sử dụng cho production.

## 5. Lưu ý

- Chỉ chạy các script trên database trống hoặc database dùng riêng cho
  development.
- Không chạy lại `schema.sql` trên database đang có dữ liệu.
- `seed.sql` dùng các ID demo cố định từ `9001` đến `13403`; chỉ chạy một lần.
- Không đưa mật khẩu database, dữ liệu sinh viên thật hoặc file backup riêng tư
  vào repository.
- Backend đang dùng `spring.jpa.hibernate.ddl-auto=none`, vì vậy phải import
  schema trước khi chạy ứng dụng.
