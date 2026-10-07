-- =====================================================================
-- Forwarder: bảng của forwarder_db (cài mới). Chạy bằng root. Chạy lại nhiều lần không lỗi.
-- Không dùng FOREIGN KEY: quan hệ giữa các bảng do code đảm bảo, cột liên kết có index.
-- DB đã có forwarder_log, gateway_route_config bản cũ: chạy thêm 02-alter-forwarder-log.sql.
-- =====================================================================

USE forwarder_db;

-- Hệ thống bên ngoài được gọi vào eBank (FCC, AI...).
-- secret_key: secret HMAC-SHA256 dùng chung với đối tác, trao qua kênh riêng. Lưu nguyên văn vì Forwarder phải tính
-- lại chữ ký (giới hạn của đồ án: thực tế mã hoá bằng khoá trong HSM/KMS).
CREATE TABLE IF NOT EXISTS partner (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    partner_id    VARCHAR(50)  NOT NULL COMMENT 'Mã đối tác, header X-Partner-Id',
    partner_name  VARCHAR(100) NOT NULL,
    secret_key    VARCHAR(128) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, INACTIVE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_partner_id (partner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Đối tác nào được gọi API nào. Không có dòng: API_NOT_ALLOWED.
CREATE TABLE IF NOT EXISTS partner_api_permission (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    partner_id  VARCHAR(50)  NOT NULL,
    api_id      VARCHAR(100) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_api (partner_id, api_id),
    KEY idx_partner_api_api_id (api_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- API đối tác gọi được. target_url trỏ tới API Gateway (route *-fwd), biến {ten} lấy từ body của đối tác.
CREATE TABLE IF NOT EXISTS gateway_route_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    api_id       VARCHAR(100) NOT NULL,
    target_url   VARCHAR(255) NOT NULL,
    http_method  VARCHAR(10)  NOT NULL,
    is_active    TINYINT(1)   DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY api_id (api_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Một dòng cho mỗi giao dịch đã qua kiểm tra chữ ký. Khoá duy nhất (partner_id, transaction_key) chống gửi lại.
-- Không lưu body request, response (có dữ liệu cá nhân).
CREATE TABLE IF NOT EXISTS forwarder_log (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    partner_id       VARCHAR(50)  NULL,
    transaction_key  VARCHAR(100) NOT NULL,
    api_id           VARCHAR(100) NOT NULL,
    request_id       VARCHAR(64)  NULL COMMENT 'X-Request-Id, tra log cùng gateway và service',
    status           VARCHAR(20)  NULL COMMENT 'PROCESSING, SUCCESS, FAILED',
    http_status      INT          NULL,
    error_code       VARCHAR(20)  NULL COMMENT 'HYD-40-xxx của Forwarder hoặc mã lỗi của eBank',
    duration_ms      BIGINT       NULL,
    created_at       TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_forwarder_log_partner_txn (partner_id, transaction_key),
    KEY idx_forwarder_log_request_id (request_id),
    KEY idx_forwarder_log_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
