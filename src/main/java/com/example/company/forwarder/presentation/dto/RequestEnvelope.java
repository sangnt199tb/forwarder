package com.example.company.forwarder.presentation.dto;

import tools.jackson.databind.JsonNode;

/**
 * Body request của đối tác. Xác thực nằm ở header (X-Partner-Id, X-Timestamp, X-Signature), chữ ký tính trên đúng các
 * byte của body này nên mọi trường bên dưới đều được bảo vệ.
 *
 * @param apiId          API muốn gọi, khai trong gateway_route_config, ví dụ CUSTOMER_DETAIL
 * @param transactionKey mã giao dịch do đối tác sinh, không được dùng lại (chống gửi lại)
 * @param body           dữ liệu nghiệp vụ. Trường cùng tên với biến {ten} trong target_url được đưa vào URL; với API
 *                       POST/PUT/PATCH, cả object được gửi làm body
 */
public record RequestEnvelope(String apiId, String transactionKey, JsonNode body) {
}
