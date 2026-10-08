package com.example.company.forwarder.common.error;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi do chính Forwarder trả cho đối tác (request chưa tới eBank, hoặc eBank không trả lời được).
 * errorCode có dạng HYD-40-xxx, module 40 dành cho Forwarder. Message song ngữ trong i18n/errors_vi.properties,
 * i18n/errors_en.properties với key "error.&lt;tên mã&gt;". Lỗi nghiệp vụ của eBank (ví dụ HYD-37-005
 * CUSTOMER_NOT_FOUND) được trả nguyên văn trong responseBody, không đổi sang mã ở đây.
 * <p>
 * Mọi lỗi 5xx có {@code code = SERVER_ERROR} như API Gateway, phân biệt nhau bằng errorCode.
 * <p>
 * Mã HTTP đối tác nhận ({@link #partnerStatus()}):
 * <ul>
 *   <li><b>400</b>: request bị Forwarder từ chối, chưa tới eBank (mã 4xx ở đây: xác thực, phân quyền, request sai,
 *       apiId không có).</li>
 *   <li><b>200</b>: lỗi hệ thống (mã 5xx ở đây). Kết quả do eBank trả (thành công hoặc lỗi nghiệp vụ) cũng là 200,
 *       xử lý ở ForwardServiceImpl.</li>
 * </ul>
 * {@link #status()} là mã HTTP chi tiết của lỗi, chỉ ghi vào forwarder_log.http_status và log của Forwarder.
 * Không đổi số của mã đã có và không dùng lại số của mã đã xoá.
 */
public enum ForwarderErrorCode {

    /** Body không phải JSON, thiếu apiId/transactionKey, sai định dạng, thiếu dữ liệu để dựng URL. */
    VALIDATION_ERROR(1, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR"),
    /** Thiếu header xác thực, đối tác không tồn tại hoặc bị khoá, sai chữ ký. Cố ý không nói rõ lý do nào. */
    UNAUTHORIZED(2, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"),
    /** X-Timestamp lệch quá forwarder.partner-auth.max-clock-skew: request cũ bị gửi lại, hoặc đồng hồ đối tác sai. */
    REQUEST_EXPIRED(3, HttpStatus.UNAUTHORIZED, "REQUEST_EXPIRED"),
    /** transactionKey đã dùng: gửi lại request cũ. */
    DUPLICATE_TRANSACTION(4, HttpStatus.BAD_REQUEST, "DUPLICATE_TRANSACTION"),
    /** apiId không có hoặc đã tắt trong gateway_route_config. */
    API_NOT_FOUND(5, HttpStatus.BAD_REQUEST, "API_NOT_FOUND"),
    /** Đối tác không được cấp quyền gọi apiId này (partner_api_permission). */
    API_NOT_ALLOWED(6, HttpStatus.FORBIDDEN, "API_NOT_ALLOWED"),
    SERVER_ERROR(7, HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR"),
    /** Không kết nối được API Gateway. */
    SERVICE_UNAVAILABLE(8, HttpStatus.SERVICE_UNAVAILABLE, "SERVER_ERROR"),
    /** API Gateway không trả lời trong forwarder.http.response-timeout. */
    GATEWAY_TIMEOUT(9, HttpStatus.GATEWAY_TIMEOUT, "SERVER_ERROR"),
    /**
     * API Gateway từ chối chính Forwarder (401, 403) hoặc không có route (404, 405): cấu hình sai ở Forwarder
     * (khoá, target_url), không phải lỗi của đối tác. Không trả nguyên lỗi của gateway để đối tác khỏi hiểu nhầm.
     */
    DOWNSTREAM_REJECTED(10, HttpStatus.BAD_GATEWAY, "SERVER_ERROR");

    private static final String MODULE_PREFIX = "HYD-40-";

    private final int number;
    private final HttpStatus status;
    private final String code;

    ForwarderErrorCode(int number, HttpStatus status, String code) {
        this.number = number;
        this.status = status;
        this.code = code;
    }

    /** Mã HTTP chi tiết của lỗi, chỉ dùng để ghi log (đối tác nhận {@link #partnerStatus()}). */
    public HttpStatus status() {
        return status;
    }

    /** Mã HTTP đối tác nhận: 400 khi request bị từ chối (lỗi 4xx), 200 khi lỗi hệ thống. */
    public HttpStatus partnerStatus() {
        return status.is4xxClientError() ? HttpStatus.BAD_REQUEST : HttpStatus.OK;
    }

    /** Giá trị trường "code" trong body lỗi. */
    public String code() {
        return code;
    }

    /** Giá trị trường "errorCode" trong body lỗi, ví dụ HYD-40-002. */
    public String errorCode() {
        return MODULE_PREFIX + String.format("%03d", number);
    }
}
