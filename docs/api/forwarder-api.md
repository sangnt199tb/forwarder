# Đặc tả API Forwarder cho hệ thống bên ngoài — v1

Hệ thống bên ngoài (FCC, AI...) gọi vào eBank qua **một endpoint duy nhất** của Forwarder. Forwarder kiểm tra chữ ký,
quyền gọi API, chống gửi lại, rồi chuyển vào eBank qua API Gateway.

```
Đối tác ──HMAC──▶ Forwarder (DMZ, :8089) ──token RS256──▶ API Gateway (:8081, route /*-fwd) ──▶ service eBank
```

## 1. Endpoint

`POST /forwarder-service/api/v1/forward` (local: `http://localhost:8089`)

### Header

| Header | Bắt buộc | Mô tả |
|---|---|---|
| `Content-Type` | Có | `application/json` |
| `X-Partner-Id` | Có | Mã đối tác do eBank cấp, ví dụ `FCC` (chữ hoa, số, `_`) |
| `X-Timestamp` | Có | Thời điểm gửi, **epoch giây** (UTC). Lệch quá **300 giây** so với eBank: `REQUEST_EXPIRED` |
| `X-Signature` | Có | Chữ ký, xem mục 2 |
| `X-Request-Id` | Không | Mã để tra log, `[A-Za-z0-9._-]{1,64}`. Không gửi thì Forwarder tự sinh |

### Body

```json
{
  "apiId": "CUSTOMER_DETAIL",
  "transactionKey": "FCC-20261007-000001",
  "body": { "cif": "CIF0000000001" }
}
```

| Trường | Mô tả |
|---|---|
| `apiId` | API muốn gọi (mục 4). Chữ hoa, số, `_` |
| `transactionKey` | Mã giao dịch do đối tác sinh, `[A-Za-z0-9._-]{1,100}`. **Mỗi mã chỉ dùng một lần**, gửi lại: `DUPLICATE_TRANSACTION` |
| `body` | Dữ liệu của API |

## 2. Chữ ký (HMAC-SHA256)

```
bodyHash   = hex(SHA-256(body gốc))                       body gốc = đúng các byte gửi đi
chuoiKy    = X-Partner-Id + "\n" + X-Timestamp + "\n" + bodyHash
X-Signature = hex(HMAC-SHA256(secret, chuoiKy))            hex chữ thường
```

- `secret` do eBank cấp riêng cho từng đối tác, trao qua kênh riêng, **không gửi trong request**.
- Ký trên **body gốc**: sau khi ký không được format lại JSON (thêm khoảng trắng, đổi thứ tự trường) trước khi gửi.
- Body nằm trong chữ ký nên `apiId`, `transactionKey`, dữ liệu đều không sửa được; `X-Timestamp` nằm trong chữ ký nên
  không sửa được để gửi lại request cũ.

### Ví dụ chữ ký

```
secret      = test-secret
X-Partner-Id = FCC
X-Timestamp = 1791400000
body        = {"apiId":"CUSTOMER_DETAIL","transactionKey":"FCC-0001","body":{"cif":"CIF0000000001"}}
bodyHash    = 509630b3aa765008c587bf9b19e8a01f60ca26b073b323a68d3c2b2d5622e911
X-Signature = 19e957a984103efae793b9bfb611e855982938263759146dfbd5a84f4b075613
```

### Gọi bằng curl (Git Bash)

```bash
SECRET='<secret của FCC>'; PARTNER=FCC; TS=$(date +%s)
BODY='{"apiId":"CUSTOMER_DETAIL","transactionKey":"FCC-'$TS'","body":{"cif":"CIF0000000001"}}'
HASH=$(printf '%s' "$BODY" | openssl dgst -sha256 -r | cut -d' ' -f1)
SIG=$(printf '%s\n%s\n%s' "$PARTNER" "$TS" "$HASH" | openssl dgst -sha256 -hmac "$SECRET" -r | cut -d' ' -f1)
curl -i -X POST http://localhost:8089/forwarder-service/api/v1/forward \
  -H "Content-Type: application/json" -H "X-Partner-Id: $PARTNER" -H "X-Timestamp: $TS" -H "X-Signature: $SIG" \
  -d "$BODY"
```

### Gọi bằng Postman

1. Environment: `forwarderUrl` = `http://localhost:8089`, `partnerId` = `FCC`, `partnerSecret` = secret của FCC.
2. Import → Raw text, dán lệnh curl:

```bash
curl -X POST '{{forwarderUrl}}/forwarder-service/api/v1/forward' \
  -H 'Content-Type: application/json' \
  -H 'X-Partner-Id: {{partnerId}}' \
  -H 'X-Timestamp: {{xTimestamp}}' \
  -H 'X-Signature: {{xSignature}}' \
  -d '{"apiId":"CUSTOMER_DETAIL","transactionKey":"{{txn}}","body":{"cif":"CIF0000000001"}}'
```

3. Curl không mang được script, nên dán đoạn sau vào tab **Scripts → Pre-request** của request vừa import:

```javascript
// Mỗi lần gửi: transactionKey mới, timestamp hiện tại, ký trên đúng body sẽ gửi đi
pm.variables.set('txn', 'FCC-' + Date.now());
const body = pm.variables.replaceIn(pm.request.body.raw);
pm.request.body.raw = body;

const partnerId = pm.environment.get('partnerId');
const ts = Math.floor(Date.now() / 1000).toString();
const bodyHash = CryptoJS.SHA256(body).toString(CryptoJS.enc.Hex);
const signature = CryptoJS.HmacSHA256(partnerId + '\n' + ts + '\n' + bodyHash,
        pm.environment.get('partnerSecret')).toString(CryptoJS.enc.Hex);

pm.variables.set('xTimestamp', ts);
pm.variables.set('xSignature', signature);
```

## 3. Response

**HTTP status chỉ có hai giá trị** (thay đổi ngày 08/10/2026):

| HTTP | Khi nào |
|---|---|
| **400** | Forwarder **từ chối request**, request chưa tới eBank: sai xác thực, quá hạn, gửi trùng `transactionKey`, không có quyền, `apiId` không có, request sai định dạng (các mã `HYD-40-001…006`) |
| **200** | Mọi trường hợp còn lại: eBank xử lý thành công, **eBank báo lỗi nghiệp vụ** (ví dụ không có dữ liệu), hoặc lỗi hệ thống (`HYD-40-007…010`) |

HTTP 200 **không có nghĩa là thành công**: đối tác xem `status` và `responseBody.code`. Body luôn có dạng:

```json
{
  "status": "SUCCESS",
  "transactionKey": "FCC-20261007-000001",
  "requestId": "7f3c1e2a-5b6d-4e8f-9a0b-1c2d3e4f5a6b",
  "responseBody": { }
}
```

| Trường | Mô tả |
|---|---|
| `status` | `SUCCESS` khi eBank xử lý thành công, còn lại `FAILED`. Đây là trường duy nhất cho biết thành công hay không |
| `responseBody` | Dữ liệu eBank trả về; hoặc body lỗi `{code, errorCode, message{vi,en}, requestId}` |

**Chữ ký của response:** khi đối tác đã qua kiểm tra chữ ký, response có `X-Timestamp`, `X-Signature` tính **cùng
công thức** trên body response (dùng `X-Partner-Id` của đối tác). Đối tác nên kiểm tra để chắc response đến từ eBank và
không bị sửa. Response lỗi `UNAUTHORIZED` không có chữ ký (Forwarder chưa xác định được đối tác).

**Cách đối tác xử lý kết quả:**

1. HTTP 400: request bị từ chối, sửa theo `responseBody.code` (mục 5) rồi gửi lại với `transactionKey` mới.
2. HTTP 200 và `status = SUCCESS`: dữ liệu nằm trong `responseBody`.
3. HTTP 200 và `status = FAILED`: đọc `responseBody.code` (và `errorCode` để báo eBank khi cần). Mã `HYD-40-xxx` là lỗi của Forwarder (mục 5), các mã khác (ví dụ `HYD-37-xxx`) là lỗi nghiệp vụ của eBank (mục 4 của từng API).
4. Không nhận được body JSON đúng dạng trên (mất kết nối, timeout phía đối tác): coi như chưa biết kết quả, tra lại bằng `transactionKey` trước khi gửi giao dịch mới.

## 4. Danh sách API

### CUSTOMER_DETAIL — thông tin đầy đủ của khách hàng theo CIF

| | |
|---|---|
| `body` | `{"cif": "CIF0000000001"}` — `cif`: chữ và số, tối đa 20 ký tự |
| Gọi tới | `GET /customer-fwd/v1/customers/{cif}` của customer-service (qua API Gateway) |
| `responseBody` khi thành công | `customer` (hồ sơ, giấy tờ, liên hệ, địa chỉ, KYC), `management` (chi nhánh, phân loại, rủi ro, kênh mở, mã nhân viên, mã giới thiệu), `branch`, `accounts[]` (số tài khoản, loại, tiền tệ, số dư, trạng thái, ngày mở). Ngày dạng `dd/MM/yyyy`; trường không có dữ liệu thì không trả. Chi tiết: `docs/api/customer-api.md` mục 2 của eBank |
| Lỗi nghiệp vụ | HTTP 200, `status = FAILED`, `responseBody.code = CUSTOMER_NOT_FOUND` (`HYD-37-005`): không có khách hàng với CIF này. `VALIDATION_ERROR` (`HYD-37-001`): CIF sai định dạng |

Ví dụ `responseBody`:

```json
{
  "customer": {
    "cif": "CIF0000000001", "fullName": "NGUYỄN VĂN AN", "dateOfBirth": "15/03/1995", "gender": "MALE",
    "idNumber": "001095012345", "idType": "CCCD", "idIssueDate": "10/08/2021", "idExpiryDate": "15/03/2035",
    "phoneNumber": "0901234567", "email": "nguyenvanan@example.com", "kycLevel": 2, "status": "ACTIVE",
    "customerSince": "05/10/2026"
  },
  "management": {
    "branchCode": "DIGI", "authenticationMethod": "EKYC_NFC", "sourceApp": "SANGLANGTHANGBANK", "updatedAt": "05/10/2026"
  },
  "branch": { "code": "DIGI", "name": "Trung tâm Ngân hàng số" },
  "accounts": [
    { "accountNumber": "19030000000001", "accountType": "PAYMENT", "currency": "VND", "balance": 0,
      "availableBalance": 0, "status": "ACTIVE", "openDate": "05/10/2026" }
  ]
}
```

## 5. Mã lỗi của Forwarder (HYD-40-xxx)

Lỗi nghiệp vụ của eBank (ví dụ `HYD-37-005`) được trả nguyên văn trong `responseBody`. Bảng dưới là lỗi do Forwarder trả.

Mọi lỗi có `status = FAILED`, mã lỗi nằm trong `responseBody`.

| HTTP | code | errorCode | Khi nào | Đối tác nên làm gì |
|---|---|---|---|---|
| 400 | `VALIDATION_ERROR` | HYD-40-001 | Body không phải JSON, thiếu hoặc sai `apiId`, `transactionKey`, thiếu dữ liệu của API | Sửa request |
| 400 | `UNAUTHORIZED` | HYD-40-002 | Thiếu header, đối tác không tồn tại hoặc bị khoá, sai chữ ký (cố ý không nói rõ lý do) | Kiểm tra secret, cách ký |
| 400 | `REQUEST_EXPIRED` | HYD-40-003 | `X-Timestamp` lệch quá 300 giây | Đồng bộ đồng hồ (NTP), gửi lại với timestamp và transactionKey mới |
| 400 | `DUPLICATE_TRANSACTION` | HYD-40-004 | `transactionKey` đã dùng | Không gửi lại; tra kết quả lần trước theo transactionKey |
| 400 | `API_NOT_FOUND` | HYD-40-005 | `apiId` không có hoặc đã tắt | Kiểm tra apiId |
| 400 | `API_NOT_ALLOWED` | HYD-40-006 | Đối tác chưa được cấp quyền gọi API này | Liên hệ eBank |
| 200 | `SERVER_ERROR` | HYD-40-007 | Lỗi không lường trước | Thử lại với transactionKey mới |
| 200 | `SERVER_ERROR` | HYD-40-008 | Forwarder không kết nối được eBank | Thử lại sau |
| 200 | `SERVER_ERROR` | HYD-40-009 | eBank không trả lời trong 130 giây | Thử lại sau |
| 200 | `SERVER_ERROR` | HYD-40-010 | eBank từ chối Forwarder (cấu hình phía eBank sai) | Báo eBank kèm `requestId` |

## 6. Bảo mật — tóm tắt cho báo cáo

| Mối đe doạ | Cách chặn |
|---|---|
| Giả mạo đối tác | HMAC-SHA256 với secret riêng từng đối tác; so chữ ký bằng phép so thời gian hằng (`MessageDigest.isEqual`) |
| Sửa dữ liệu trên đường truyền | Chữ ký tính trên toàn bộ body gốc; response cũng được ký |
| Gửi lại request cũ (replay) | `X-Timestamp` trong chữ ký, lệch tối đa 300 giây; `transactionKey` duy nhất theo đối tác (khoá duy nhất trong `forwarder_log`) |
| Đối tác gọi API không được phép | Bảng `partner_api_permission` |
| Forwarder bị chiếm quyền | Forwarder không giữ `X-Gateway-Token`; chỉ có khoá ký token cho route `*-fwd`. Token sống 60 giây, gắn với đúng method + path (claim `htm`, `htu`), không dùng được cho API khác hay khách hàng khác |
| Chèn path qua dữ liệu (`../`) | Giá trị trong URL được mã hoá chặt (`/` thành `%2F`); gateway còn chặn path chưa chuẩn hoá |
| Một đối tác gọi quá nhiều | Gateway giới hạn tần suất theo `X-Partner-Id` |

**Giới hạn (ghi vào báo cáo):** secret của đối tác lưu nguyên văn trong DB (thực tế mã hoá bằng khoá trong HSM/KMS);
chưa có mTLS và danh sách IP được phép giữa đối tác và Forwarder; request sai chữ ký chỉ ghi vào file log, không vào DB.
