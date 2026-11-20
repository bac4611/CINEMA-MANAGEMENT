# Bật đăng nhập Google

Ứng dụng đã có nút Google trên trang đăng nhập và đăng ký, kiểm tra email Google
đã xác minh và tạo tài khoản khách hàng nếu chưa tồn tại. Profile `google` cung cấp
cấu hình OAuth để bật luồng này. Không dùng Client ID hoặc Client Secret giả.

## Google Cloud

1. Mở https://console.cloud.google.com/ và chọn project của bạn.
2. Trong Google Auth Platform, cấu hình Branding, Audience và email hỗ trợ.
3. Nếu ứng dụng External đang ở chế độ Testing, thêm email dùng thử vào Test users.
4. Trong Clients, tạo OAuth client loại Web application.
5. Thêm Authorized redirect URI chính xác:
   `http://localhost:8080/login/oauth2/code/google`.
6. Lấy Client ID và Client Secret. Không gửi secret lên Git hoặc dán vào chat.

`/google-auth/callback` là bước xử lý nội bộ sau OAuth, không phải redirect URI
đăng ký trên Google. Khi đổi domain hoặc port, phải đăng ký URI tương ứng.

## Chạy bằng VS Code

1. Copy `.env.example` thành `.env` tại thư mục gốc.
2. Điền `GOOGLE_CLIENT_ID` và `GOOGLE_CLIENT_SECRET` trong `.env`.
3. Dừng backend cũ nếu đang chiếm port 8080.
4. Chọn Run and Debug > Spring Boot-CinemaApplication (Google Login), rồi F5.
5. Vào http://localhost:8080/account/login và bấm Đăng nhập với Google.

File `.env` được Git bỏ qua. Cấu hình chạy cũ vẫn dùng profile `dev`.

## Chạy bằng PowerShell

Từ thư mục gốc project, nhập từng lệnh:

```powershell
$env:GOOGLE_CLIENT_ID = Read-Host 'Google Client ID'
$googleSecret = Read-Host 'Google Client Secret' -AsSecureString
$env:GOOGLE_CLIENT_SECRET = [System.Net.NetworkCredential]::new('', $googleSecret).Password
Remove-Variable googleSecret
.\mvnw.cmd -pl backend spring-boot:run '-Dspring-boot.run.profiles=dev,google'
```

Maven không tự đọc `.env`; các lệnh trên truyền credential qua biến môi trường.
Nếu chưa điền đủ hai credential, ứng dụng vẫn chạy và ẩn nút Google.
Khi điền đủ credential, khởi động lại backend để bật OAuth.
SQL Server vẫn dùng cấu hình Windows Authentication hiện tại.

## Luồng đăng nhập

`/account/login` → POST `/google-auth/login` (có CSRF) →
`/oauth2/authorization/google` → Google → `/login/oauth2/code/google` →
`/google-auth/callback` → trang tương ứng với role.

## Lỗi thường gặp

- Không thấy nút Google: kiểm tra profile `google` và hai biến môi trường.
- `redirect_uri_mismatch`: kiểm tra URI, bao gồm http/https, hostname, port và path.
- `invalid_client`: kiểm tra credential của đúng OAuth Web client.
- `access_denied`: kiểm tra Test users và quyền đồng ý trên màn hình Google.

Tham khảo: https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html
và https://developers.google.com/identity/protocols/oauth2/web-server.
