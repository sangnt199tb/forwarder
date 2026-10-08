# Hướng dẫn: mở một API của eBank cho hệ thống bên ngoài qua Forwarder

Dùng khi cần cho FCC, AI hay đối tác khác gọi thêm một API của eBank. Ví dụ mẫu đã chạy thật (07/10/2026):
`CUSTOMER_DETAIL` → `GET /customer-fwd/v1/customers/{cif}` của customer-service.

```
Đối tác ──HMAC──▶ Forwarder :8089 ──token RS256──▶ API Gateway :8081 /<module>-fwd/** ──X-Gateway-Token──▶ service
```

Có 4 nơi phải làm: **spec → service → gateway → DB của Forwarder**. Code của Forwarder **không cần sửa**.

## Khi nào phải sửa code Forwarder?

Forwarder là phần **dùng chung, không chứa nghiệp vụ**. Với nó, mỗi API chỉ là 2 dòng dữ liệu:
`gateway_route_config` (apiId → URL trên gateway + method) và `partner_api_permission` (đối tác nào được gọi).
Thêm dòng là dùng được ngay, không cần khởi động lại. Code chỉ phải viết ở phía eBank (spec, controller, route gateway).

**Chỉ cấu hình DB là đủ** khi API:
- là GET/DELETE với tham số trong path hoặc query (`.../customers/{cif}`, `...?from={from}`), giá trị lấy từ trường
  cùng tên trong `body` của đối tác;
- là POST/PUT/PATCH nhận một JSON: Forwarder gửi nguyên object `body`;
- trả JSON (kể cả lỗi nghiệp vụ, Forwarder trả nguyên văn cho đối tác);
- chạy xong trong 130 giây.

**Phải sửa code Forwarder** khi:

| Trường hợp | Vì sao | Hướng làm |
|---|---|---|
| Upload/download file (multipart, PDF, ảnh) | Forwarder chỉ gửi, nhận JSON; response không phải JSON bị coi là `SERVER_ERROR` | Thêm kiểu nội dung vào `gateway_route_config`, xử lý riêng trong `EbankGatewayClient` |
| Đổi cấu trúc dữ liệu: đổi tên trường, gộp nhiều API, thêm header riêng | Forwarder chỉ thay biến vào URL và chuyển nguyên body | Làm ở service eBank (thêm API fwd đúng dạng đối tác cần) thay vì nhét nghiệp vụ vào Forwarder |
| API chạy quá 130 giây | Timeout đang chung cho mọi API (`forwarder.http.response-timeout`) | Thêm cột timeout vào `gateway_route_config` |
| Hạn mức riêng theo đối tác và API (ví dụ 1000 lần/ngày) | Hiện chỉ có rate limit chung theo đối tác ở gateway | Thêm bảng hạn mức, đếm bằng Redis |
| eBank gọi ngược ra đối tác (callback, thông báo) | Chiều ngược lại, Forwarder chưa có | Module gọi ra riêng, ký request bằng secret của đối tác |
| Đổi cách xác thực đối tác (mTLS, OAuth2) | Xác thực nằm trong code (`PartnerSignature`, `ForwardServiceImpl`) | Thêm cách xác thực mới, chọn theo cấu hình của đối tác |

**Câu trả lời ngắn khi bảo vệ:** thêm một API cho đối tác không phải sửa hay triển khai lại Forwarder, chỉ thêm cấu hình
(route + quyền). Forwarder chỉ lo phần chung: xác thực đối tác, chống gửi lại, phân quyền, ghi log, chuyển tiếp. Nghiệp vụ
nằm ở service eBank. Cách tách này giống API Gateway hay ESB trong ngân hàng: thêm đối tác hay API là việc vận hành, không
phải việc phát triển.

---

## Bước 1. Spec (`spec/<module>-spec`)

1. Tạo `src/main/resources/openapi/<module>-forwarder-api.yml`:
   - Path bắt đầu bằng `/<module>-fwd/v1/...` (ví dụ `/onboard-fwd/v1/...`). Gateway chỉ bảo vệ bằng token của Forwarder
     những path khớp `/*-fwd/**`.
   - Tag riêng, ví dụ `<Module>Forwarder`, để sinh ra một interface riêng.
   - Header bắt buộc `X-Partner-Id` (gateway gắn, service dùng để ghi log, có thể kiểm tra thêm).
   - Khai đủ ràng buộc cho tham số (`pattern`, `maxLength`): giá trị đến từ hệ thống bên ngoài.
   - Request, response viết thành file `.json` riêng trong `schemas/<nhóm>/request|response/` (quy ước từ 06/10).
     Dùng lại schema có sẵn bằng `$ref` thay vì viết lại.
2. `pom.xml`: thêm execution `generate-forwarder-api`, chép từ customer-spec:
   - `apiPackage` = `com.ebank.<module>.api.fwdapi`;
   - `modelPackage` = **cùng package model với API cho app** (model dùng chung được sinh lại y hệt, không thành hai class);
   - `useTags`, `fluentMethods`, `openApiNullable=false`, `interfaceOnly`, `skipDefaultInterface`.
3. `mvn clean install`.

Mẫu: `spec/customer-spec/src/main/resources/openapi/customer-forwarder-api.yml`.

## Bước 2. Service (`dbs/<service>`)

1. Controller trong package **`integration/forwarder`**, implement interface vừa sinh. Mẫu:
   `customers_service/.../integration/forwarder/CustomerForwarderController.java`.
2. Logic trong `presentation/service/XxxService` + `impl/XxxServiceImpl`. Tách phần dùng chung với API cho app (mapper)
   thay vì chép lại.
3. Mọi method public: `LogUtils.start/end/error` trong try/catch. Ghi `partnerId` vào log.
4. Lỗi: thêm controller mới vào `basePackageClasses` của exception handler cho API client
   (customer: `ClientExceptionHandler`). Lỗi nghiệp vụ dùng mã `HYD-<module>-xxx` sẵn có; Forwarder trả nguyên cho đối tác.
5. Model mới cần bỏ trường null: thêm mix-in vào `ClientJsonConfig` (hoặc file tương đương của service).
6. **Kiểm tra service có `GatewaySecurityFilter`**. transfer-service hiện **chưa có**: phải thêm trước khi mở API fwd.
7. Test cho service mới, gồm cả test **không lộ** cột nội bộ (id, mật khẩu, khoá nội bộ).
8. Reload Maven (spec đổi), khởi động lại service.

## Bước 3. API Gateway (`dbs/api-gateway/src/main/resources/application.yaml`)

Thêm route, đặt cạnh `customer-forwarder-api-route`:

```yaml
        - id: <module>-forwarder-api-route
          uri: lb://<SERVICE-NAME-TRÊN-EUREKA>
          predicates:
            - Path=/<module>-fwd/v1/**
          filters:
            - name: CircuitBreaker
              args:
                name: <module>ServiceCircuitBreaker
                fallbackUri: forward:/fallback/<module>
```

- **Không** cần cấu hình xác thực: `AuthenticationFilter` tự bắt buộc token của Forwarder cho mọi `/*-fwd/**`.
- **Không** thêm path fwd vào `gateway.security.public-paths` (có thêm cũng không mở được, nhưng gây hiểu nhầm).
- API chậm (OCR, so khớp khuôn mặt): dùng circuit breaker có `timelimiter` dài như onboard (125 giây). Mặc định chỉ 3 giây.
- Khởi động lại gateway.

## Bước 4. DB của Forwarder (`forwarder_db`, chạy bằng root)

Viết thành script mới trong `d:\code\forwarder\docs\db\` (ví dụ `04-api-<ten>.sql`). Mỗi câu tự chứa, không dùng biến session:

```sql
-- API: target_url trỏ tới GATEWAY (:8081), không trỏ thẳng service.
-- {ten} được thay bằng trường cùng tên trong "body" của đối tác (mã hoá chặt, "/" thành %2F)
INSERT INTO forwarder_db.gateway_route_config (api_id, target_url, http_method, is_active)
VALUES ('<API_ID>', 'http://localhost:8081/<module>-fwd/v1/<path>/{ten}', 'GET', 1) AS r
ON DUPLICATE KEY UPDATE target_url = r.target_url, http_method = r.http_method, is_active = 1;

-- Quyền: một dòng cho mỗi đối tác được gọi
INSERT IGNORE INTO forwarder_db.partner_api_permission (partner_id, api_id, created_at)
VALUES ('FCC', '<API_ID>', NOW());
```

- `api_id`: chữ hoa, số, `_` (ví dụ `ONBOARD_STATUS`).
- `http_method` GET/DELETE: chỉ dùng biến trong URL. POST/PUT/PATCH: cả object `body` được gửi làm body JSON.
- Biến trong query string (`?from={from}`) cũng được, nhưng token của Forwarder chỉ gắn với path, không gắn query.
- **Không cần khởi động lại Forwarder**: mỗi request đều đọc DB.

### Thêm đối tác mới (nếu cần)

1. Sinh secret: `openssl rand -hex 32`. Lưu vào `%USERPROFILE%\.ebank\forwarder-partner-secrets.yaml` (dòng `<PARTNER_ID>: ...`).
2. Thêm vào DB bằng một câu tự chứa, chép câu INSERT đầu tiên của `03-partner-fcc.sql` rồi đổi mã và tên đối tác.
3. Kiểm tra: `SELECT partner_id, status, CHAR_LENGTH(secret_key) FROM forwarder_db.partner;` phải `ACTIVE | 64`.
   Thấy `INACTIVE | 12` là chưa thay placeholder.
4. Trao secret cho đối tác qua kênh riêng, không gửi qua email thường hay chat nhóm.

## Bước 5. Tài liệu và kiểm tra

1. Tài liệu:
   - `d:\code\forwarder\docs\api\forwarder-api.md` mục 4: thêm API (`apiId`, `body`, `responseBody`, lỗi nghiệp vụ);
   - `docs/api/<module>-api.md` của eBank: thêm mục API fwd (xem mục 2 của `customer-api.md`).
2. Postman: dùng lại request `FWD`, đổi `apiId` và `body`. Script Pre-request tự ký lại.
3. Kiểm tra kết quả:
   - `SELECT * FROM forwarder_db.forwarder_log ORDER BY id DESC LIMIT 5;` → `SUCCESS | 200` (cột `http_status` là mã HTTP thật của eBank);
   - log theo `requestId`: Forwarder `D:\code\java\log-ebank\log-forwarder\current.log`, gateway và service trong
     `D:\app\log-ebank-system\` (hoặc OpenSearch Dashboards).

## Bảng tra lỗi khi cấu hình

Đối tác nhận HTTP 400 khi Forwarder từ chối request (HYD-40-001…006), HTTP 200 khi eBank đã xử lý hoặc lỗi hệ thống; cột đầu là HTTP và `responseBody.code` / `errorCode`. Mã HTTP chi tiết nằm ở `forwarder_log.http_status` và log của Forwarder.

| Đối tác nhận | Log / dấu hiệu | Nguyên nhân thường gặp |
|---|---|---|
| 400 `UNAUTHORIZED` (HYD-40-002), log `partner <invalid>` | Header thiếu hoặc chưa thay biến | Postman chưa chọn Environment, sai tên biến |
| 400 `UNAUTHORIZED` (HYD-40-002), log `partner FCC: unknown or inactive partner, or wrong signature` | | Đối tác `INACTIVE` / chưa có trong DB; secret trong DB khác secret đối tác dùng |
| 400 `REQUEST_EXPIRED` (HYD-40-003) | | Đồng hồ máy đối tác lệch quá 5 phút |
| 400 `API_NOT_ALLOWED` (HYD-40-006) | | Thiếu dòng `partner_api_permission` |
| 400 `API_NOT_FOUND` (HYD-40-005) | | Thiếu `gateway_route_config` hoặc `is_active = 0` |
| 400 `VALIDATION_ERROR` (HYD-40-001) | `cannot build URL ... Map has no value for 'x'` | `body` thiếu trường trùng tên với biến `{x}` trong `target_url` |
| 200 `SERVER_ERROR` (HYD-40-010) | `API Gateway returned 404` | Gateway chưa có route `/<module>-fwd/**`, hoặc chưa restart gateway, hoặc `target_url` sai path |
| 200 `SERVER_ERROR` (HYD-40-010) | `API Gateway returned 401` | Khoá của Forwarder không khớp `gateway.forwarder.public-key`; xem log gateway `Rejected forwarder token ... <lý do>` |
| 200 `SERVER_ERROR` (HYD-40-010) | `API Gateway returned 403` | Service chặn vì thiếu/sai `X-Gateway-Token` (secret dùng chung không khớp, cần restart) |
| 200 `SERVER_ERROR` (HYD-40-008) | `cannot reach API Gateway` | Gateway chưa chạy, sai cổng trong `target_url` |
| 200 `SERVER_ERROR` (`HYD-00-005`, gateway trả) | Circuit breaker mở | Service chưa đăng ký Eureka / chưa chạy |
| 200 `SERVER_ERROR` (`HYD-00-006` từ gateway, hoặc HYD-40-009) | | API chậm hơn timelimiter của route (mặc định 3 giây) |
