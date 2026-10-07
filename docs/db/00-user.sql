-- =====================================================================
-- Forwarder: user MySQL riêng cho forwarder_db
-- MySQL 8.x. Chạy bằng root. Chạy lại nhiều lần không lỗi.
-- Mật khẩu dưới đây chỉ dùng cho máy local, trùng với giá trị mặc định trong application.yml.
-- Môi trường khác truyền DB_PASSWORD.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS forwarder_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'forwarder'@'%' IDENTIFIED BY 'Forwarder@Local2026';

-- Chỉ quyền dữ liệu. Không có CREATE/ALTER/DROP: bảng do người quản trị tạo bằng các script trong thư mục này.
GRANT SELECT, INSERT, UPDATE, DELETE ON forwarder_db.* TO 'forwarder'@'%';

FLUSH PRIVILEGES;
