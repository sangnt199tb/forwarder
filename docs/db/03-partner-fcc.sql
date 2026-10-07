-- =====================================================================
-- Forwarder: đối tác FCC và API CUSTOMER_DETAIL (tra cứu khách hàng theo CIF). Chạy bằng root.
-- Chạy lại được: secret, URL được cập nhật theo giá trị mới.
--
-- TRƯỚC KHI CHẠY: thay <SECRET_FCC> (MỘT chỗ duy nhất, trong câu INSERT đầu tiên) bằng giá trị FCC trong
-- %USERPROFILE%\.ebank\forwarder-partner-secrets.yaml. Không commit file đã điền secret.
-- Mỗi câu tự chứa (không dùng biến session) nên chạy cả file hay bôi đen chạy từng câu đều được.
-- Quên thay <SECRET_FCC>: FCC được tạo ở trạng thái INACTIVE, không gọi được (không ai ký bằng chuỗi mẫu được).
-- =====================================================================

USE forwarder_db;

INSERT INTO forwarder_db.partner (partner_id, partner_name, secret_key, status, created_at, updated_at)
SELECT 'FCC', 'FCC - Core banking', s.secret, IF(s.secret LIKE '<%', 'INACTIVE', 'ACTIVE'), NOW(), NOW()
FROM (SELECT '<SECRET_FCC>' AS secret) AS s
ON DUPLICATE KEY UPDATE secret_key = s.secret,
                        status     = IF(s.secret LIKE '<%', 'INACTIVE', 'ACTIVE'),
                        updated_at = NOW();

-- Gọi qua API Gateway (cổng 8081), không gọi thẳng customer-service
INSERT INTO forwarder_db.gateway_route_config (api_id, target_url, http_method, is_active)
VALUES ('CUSTOMER_DETAIL', 'http://localhost:8081/customer-fwd/v1/customers/{cif}', 'GET', 1) AS new_route
ON DUPLICATE KEY UPDATE target_url = new_route.target_url, http_method = new_route.http_method, is_active = 1;

INSERT IGNORE INTO forwarder_db.partner_api_permission (partner_id, api_id, created_at)
VALUES ('FCC', 'CUSTOMER_DETAIL', NOW());

-- Hai route thử nghiệm cũ trỏ thẳng vào service (bỏ qua gateway), path không còn tồn tại: tắt đi
UPDATE forwarder_db.gateway_route_config SET is_active = 0 WHERE api_id IN ('TEST_EBANK_CALL', 'TEST_EBANK_CALL_1');

-- Kiểm tra: FCC phải là ACTIVE và secret_length = 64
SELECT partner_id, status, CHAR_LENGTH(secret_key) AS secret_length FROM forwarder_db.partner;
SELECT api_id, target_url, http_method, is_active FROM forwarder_db.gateway_route_config;
