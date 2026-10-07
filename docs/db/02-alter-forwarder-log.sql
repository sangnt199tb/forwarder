-- =====================================================================
-- Forwarder: nâng bảng forwarder_log bản cũ (id, transaction_key, api_id, created_at) lên bản mới.
-- Chỉ chạy MỘT lần, trên DB đã có forwarder_log cũ (cài mới thì 01-tables.sql đã đủ cột).
-- Các dòng cũ giữ nguyên, partner_id để NULL. Khoá duy nhất cho phép nhiều dòng NULL nên không bị trùng.
-- =====================================================================

USE forwarder_db;

ALTER TABLE forwarder_log
    ADD COLUMN partner_id   VARCHAR(50) NULL AFTER id,
    ADD COLUMN request_id   VARCHAR(64) NULL COMMENT 'X-Request-Id, tra log cùng gateway và service' AFTER api_id,
    ADD COLUMN status       VARCHAR(20) NULL COMMENT 'PROCESSING, SUCCESS, FAILED' AFTER request_id,
    ADD COLUMN http_status  INT         NULL AFTER status,
    ADD COLUMN error_code   VARCHAR(20) NULL COMMENT 'HYD-40-xxx của Forwarder hoặc mã lỗi của eBank' AFTER http_status,
    ADD COLUMN duration_ms  BIGINT      NULL AFTER error_code,
    ADD COLUMN updated_at   TIMESTAMP   NULL AFTER created_at,
    ADD UNIQUE KEY uk_forwarder_log_partner_txn (partner_id, transaction_key),
    ADD KEY idx_forwarder_log_request_id (request_id),
    ADD KEY idx_forwarder_log_created_at (created_at);
