# SmartEdu - Nền tảng Hỗ trợ Học tập AI & Tối ưu Thời khóa biểu Đại học

## 1. Cấu trúc thư mục
- `Be/demo/`: Backend Spring Boot 3.4 (Java 17), Spring Security + JWT, Flyway Migration (44 bảng SQL Server), Apache PDFBox / POI, tích hợp Gemini AI & thuật toán Backtracking xếp lịch.
- `Fe/`: Frontend React 18 + Vite + Tailwind CSS kết nối đầy đủ API.

## 2. Hướng dẫn khởi chạy Backend (`Be/demo`)
1. Tạo database `SmartEduDB` trên SQL Server (hoặc cấu hình biến môi trường `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` trong `src/main/resources/application.yml`).
2. Chạy lệnh:
   ```bash
   cd Be/demo
   mvn spring-boot:run
   ```
3. Truy cập Swagger UI tại: `http://localhost:8080/swagger-ui.html`
4. Tài khoản khởi tạo tự động:
   - **Sinh viên:** `sinhvien@smartedu.edu.vn` / `SmartEdu@123`
   - **Quản trị viên:** `admin@smartedu.edu.vn` / `SmartEdu@123`

## 3. Hướng dẫn khởi chạy Frontend (`Fe`)
```bash
cd Fe
npm install
npm run dev
```
Truy cập giao diện tại `http://localhost:5173`.
