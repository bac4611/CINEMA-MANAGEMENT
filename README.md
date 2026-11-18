# COSMOS Cinema — Cinema Management System

Ứng dụng quản lý rạp chiếu phim và đặt vé trực tuyến, xây dựng bằng **Java 21,
Spring Boot, Spring Security, Thymeleaf và Microsoft SQL Server**. Dự án kết nối
quy trình của khách hàng với hoạt động xếp lịch, quản lý phim, bắp nước và theo dõi
doanh thu của rạp.

**Website demo:** [https://bacdz.shop](https://bacdz.shop/)

**Mã nguồn:** [bac4611/CINEMA-MANAGEMENT](https://github.com/bac4611/CINEMA-MANAGEMENT)

## Giới thiệu dành cho nhà tuyển dụng

Dự án triển khai một luồng nghiệp vụ xuyên suốt: khách chọn phim và suất chiếu,
giữ ghế có thời hạn, chọn combo, thanh toán chuyển khoản và nhận vé khi backend
xác nhận giao dịch. Nhân viên vận hành danh mục phim và lịch chiếu; quản trị viên
quản lý nhân sự và xem thống kê kinh doanh.

Các bài toán kỹ thuật được xử lý trong mã nguồn gồm:

- Phân quyền theo vai trò, kết hợp đăng nhập web bằng session và API bằng JWT.
- Quản lý thời hạn giữ chỗ tại server và xử lý tranh chấp ghế giữa các phiên đặt vé.
- Tách yêu cầu thanh toán khỏi đơn đặt vé đã xác nhận; kiểm tra giao dịch webhook trùng.
- Kiểm tra lịch chiếu chồng lấn trong cùng phòng, kể cả suất chiếu qua nửa đêm.
- Dùng transaction, khóa dữ liệu và kiểm tra tồn kho khi hoàn tất đặt vé.
- Đóng gói ứng dụng bằng Docker và triển khai lên VPS thông qua GitHub Actions.

## Trải nghiệm bản demo

| Trang | Đường dẫn |
| --- | --- |
| Trang chủ | [bacdz.shop/Home](https://bacdz.shop/Home) |
| Danh sách phim | [bacdz.shop/Home/Movie](https://bacdz.shop/Home/Movie) |
| Lịch chiếu và đặt vé | [bacdz.shop/Booking/Index](https://bacdz.shop/Booking/Index) |
| Bảng giá vé | [bacdz.shop/Home/TicketPricing](https://bacdz.shop/Home/TicketPricing) |
| Đăng nhập | [bacdz.shop/Account/Login](https://bacdz.shop/Account/Login) |
| Đăng ký | [bacdz.shop/Account/Register](https://bacdz.shop/Account/Register) |

Có thể bắt đầu bằng việc duyệt phim, xem chi tiết, chọn suất chiếu và trải nghiệm
sơ đồ ghế. Các bước đặt vé yêu cầu tài khoản theo luồng của ứng dụng. Khu vực
Staff và Admin được bảo vệ bằng phân quyền, không công khai tài khoản quản trị.

Google OAuth, gửi email và thanh toán tự động phụ thuộc cấu hình tích hợp trên
server. Danh mục phim, lịch chiếu và kết quả gợi ý phụ thuộc dữ liệu của bản demo.

## Vai trò và phạm vi nghiệp vụ

| Vai trò | Chức năng chính |
| --- | --- |
| Khách truy cập | Xem phim, thông tin chi tiết, lịch chiếu, bảng giá; sử dụng công cụ gợi ý phim; đăng ký tài khoản. |
| Khách hàng — `KhachHang` | Đăng nhập, quản lý hồ sơ, đặt vé, chọn ghế và combo, thanh toán, xem lịch sử đặt vé và đánh giá phim. |
| Nhân viên — `Staff` | Thêm/sửa/xóa phim theo ràng buộc dữ liệu; lập lịch chiếu; xem trạng thái phòng và ghế; quản lý danh mục, giá và tồn kho combo. |
| Quản trị viên — `Admin` | Xem thống kê doanh thu/vé, quản lý tài khoản nhân viên, cập nhật thông tin quản trị và truy cập nghiệp vụ Staff. |

## Nghiệp vụ khách hàng

### 1. Khám phá phim

- Xem phim đang chiếu và sắp chiếu.
- Xem poster, nội dung, thời lượng, phân loại độ tuổi, thể loại, ngôn ngữ,
  quốc gia, diễn viên và đạo diễn theo dữ liệu đã nhập.
- Xem lịch chiếu theo ngày và phim; chọn suất chiếu để bắt đầu đặt vé.
- Xem bảng giá và gửi đánh giá phim từ tài khoản khách hàng đang hoạt động.

### 2. Tài khoản và xác thực

- Đăng ký bằng email, kiểm tra dữ liệu nhập và email đã tồn tại.
- Xác minh email và gửi lại email xác minh có thời gian chờ.
- Đăng nhập bằng email/mật khẩu; kiểm tra trạng thái tài khoản và xác minh email.
- Đăng nhập Google khi OAuth được cấu hình: kiểm tra email đã xác minh,
  liên kết tài khoản hoặc tạo khách hàng mới.
- Quên mật khẩu và đặt lại bằng token có thời hạn; token được lưu dưới dạng hash.
- Cập nhật hồ sơ cá nhân và xem thông tin các booking của mình.

Web sử dụng session/cookie. API đăng nhập trả JWT Bearer ký bằng HS256, thời hạn
mặc định 15 phút; phía xác thực kiểm tra chữ ký, thời hạn, issuer, audience và role.
Mật khẩu được hash bằng BCrypt.

### 3. Luồng đặt vé

```text
Chọn phim / lịch chiếu
        ↓
Chọn suất chiếu và định dạng
        ↓
Chọn ghế → giữ chỗ trên server
        ↓
Chọn bắp nước / combo (tùy chọn)
        ↓
Kiểm tra đơn hàng → tạo yêu cầu thanh toán
        ↓
QR chuyển khoản → webhook xác nhận
        ↓
Booking được xác nhận → tạo vé → trang thanh toán thành công
```

Các quy tắc chính:

- Thời hạn giữ chỗ là **10 phút**, do server cấp. Đồng hồ trình duyệt hiển thị
  thời gian còn lại từ hạn đó, không tự gia hạn khi tải lại trang.
- Mỗi phiên chọn tối đa **5 ghế**; sơ đồ phân biệt ghế thường, VIP và ghế đôi.
- Kiểm tra ghế đã bán hoặc bị phiên khác giữ trước khi chấp nhận lựa chọn.
- Combo được giữ theo tồn kho khả dụng, tối đa 10 đơn vị cho mỗi loại trong phiên.
- **Tổng thanh toán = tổng tiền vé + tổng tiền combo**, không cộng thuế hoặc phí tiện ích.
- Yêu cầu thanh toán đang còn hiệu lực được tái sử dụng để hạn chế tạo trùng.
- Hủy hoặc hết hạn phiên sẽ giải phóng tài nguyên giữ chỗ theo xử lý của server.
- Trước khi hoàn tất, backend kiểm tra lại thời hạn, trạng thái giữ chỗ, giá,
  ghế đã bán và tồn kho combo.
- Trang thành công hiển thị mã đơn, phim, lịch chiếu, ghế, mã vé và số tiền.

### 4. Thanh toán SePay

1. Backend tạo `PaymentIntent` chứa mã tham chiếu `COSMOS…`, số tiền và thời hạn.
2. Trang thanh toán tạo QR theo ngân hàng, số tài khoản, số tiền và nội dung chuyển khoản.
3. SePay gửi giao dịch đến `POST /webhooks/sepay`.
4. Backend xử lý tiền vào, tìm yêu cầu thanh toán và đối chiếu số tiền.
5. Kiểm tra transaction ID để hạn chế xử lý lặp cùng một giao dịch.
6. Khi hợp lệ, tạo booking, vé, bản ghi thanh toán và cập nhật tồn kho trong transaction.
7. Trang thanh toán truy vấn trạng thái để chuyển sang kết quả tương ứng.

Webhook hỗ trợ header `Authorization: Apikey <key>`. Khóa webhook và thông tin
ngân hàng cần được cấu hình riêng trên VPS. Chức năng hoàn tất thanh toán giả lập
chỉ phục vụ môi trường phát triển, không thay thế webhook trên production.

## Nghiệp vụ nhân viên

### Danh mục phim

- Thêm và sửa thông tin phim, poster, trailer, thể loại, định dạng và thông tin liên quan.
- Kiểm tra trùng tên phim, có chuẩn hóa khoảng trắng và chữ hoa/thường.
- Kiểm tra dữ liệu liên quan trước khi xóa phim để bảo toàn các tham chiếu nghiệp vụ.
- Hạn chế thay đổi thời lượng phim đã có lịch chiếu để tránh làm sai lịch đang vận hành.

### Lịch chiếu

- Giao diện timeline theo ngày và phòng chiếu; danh sách phim có tìm kiếm và cuộn.
- Tạo, chỉnh sửa và xóa các suất chiếu trong bản lịch, sau đó lưu về server.
- Cho phép bắt đầu ở bất kỳ giờ/phút nào trong ngày; thời gian kết thúc được tính
  theo thời lượng phim và có thể sang ngày tiếp theo.
- Kiểm tra chồng lấn trong cùng phòng, bao gồm các suất thuộc ngày liền kề.
- Kiểm tra vé đã bán và các tham chiếu đặt chỗ/thanh toán trước khi sửa hoặc xóa lịch.
- Trả kết quả lưu và số lượng suất được tạo, cập nhật hoặc xóa.

### Phòng chiếu và bắp nước

- Xem phòng chiếu, sơ đồ và cơ cấu loại ghế.
- Xem trạng thái ghế theo phòng, ngày và suất chiếu.
- Thêm/sửa combo, giá bán và số lượng tồn kho.
- Lượng combo khách đang giữ được tính khi xác định số lượng còn có thể đặt.

## Nghiệp vụ quản trị viên

- Theo dõi tổng doanh thu, số vé và booking được xác nhận theo dữ liệu thanh toán.
- Lọc thống kê theo tháng và phim; xem dữ liệu doanh thu/vé theo thời gian và phim.
- Doanh thu được tổng hợp từ các thanh toán thành công của booking đã xác nhận.
- Xem danh sách nhân viên có phân trang; tạo và cập nhật tài khoản nhân viên.
- Cập nhật hồ sơ quản trị và mật khẩu theo điều kiện xác thực mật khẩu hiện tại.

## Gợi ý phim theo tâm trạng

Widget **COSMOS AI SUPPORT** nhận bốn tiêu chí: tâm trạng, người đi cùng, nhịp phim
và độ tuổi. Bộ dự đoán sử dụng dữ liệu mẫu trong Java và so sánh đặc trưng để chọn
thể loại; sau đó lọc phim có thể loại tương ứng và chấm điểm theo các tiêu chí,
nội dung phim cùng dữ liệu suất chiếu, trả tối đa 5 kết quả.

Đây là bộ gợi ý dựa trên dữ liệu mẫu và quy tắc chấm điểm, **không tích hợp LLM
hoặc API ChatGPT**. Nếu thể loại dự đoán chưa được gán phim trong database,
hệ thống trả thông báo chưa có phim phù hợp. Hiện chưa có cơ chế tự chọn thể loại
thay thế; dữ liệu `Movies`, `Genres` và `MovieGenres` cần nhất quán giữa các môi trường.

## Công nghệ và kiến trúc

| Thành phần | Công nghệ / cách triển khai |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.2, Spring MVC |
| Dữ liệu | Spring Data JPA, Hibernate, Microsoft SQL Server |
| Xác thực | Spring Security, BCrypt, JWT HS256, Google OAuth2/OIDC |
| Frontend | Thymeleaf, HTML, CSS, JavaScript, Bootstrap |
| Email | Spring Mail: xác minh email và khôi phục mật khẩu |
| Thanh toán | QR chuyển khoản và webhook SePay |
| Build & test | Maven Wrapper, JUnit, Mockito, MockMvc; H2 cho test |
| Triển khai | Docker, Docker Compose, Docker Hub, Ubuntu VPS, GitHub Actions |

Ứng dụng tổ chức theo tầng **Controller → Service → Repository → SQL Server**.
DTO và ViewModel phục vụ dữ liệu đầu vào/đầu ra; Entity ánh xạ dữ liệu lưu trữ.
Frontend có thư mục riêng nhưng được Maven đóng gói vào cùng JAR để Spring Boot
render trang Thymeleaf và phục vụ tài nguyên tĩnh.

### Cấu trúc repository

```text
CINEMA-java/
├── .github/workflows/     # Build, test, Docker và deploy
├── backend/
│   ├── src/main/java/     # Controller, Service, Repository, Entity, DTO, Config
│   ├── src/main/resources/ # Cấu hình theo môi trường
│   └── src/test/          # Kiểm thử backend
├── frontend/
│   ├── templates/        # Trang Thymeleaf theo nhóm nghiệp vụ
│   └── static/           # CSS, JavaScript, hình ảnh và thư viện
├── database/             # SQL schema và script bổ sung
├── scripts/              # Deploy SSH, health check, rollback và kiểm tra script
├── docs/                 # Tài liệu kỹ thuật
├── Dockerfile
├── compose.prod.yml
├── DEPLOYMENT.md
├── .env.example
└── pom.xml               # Maven parent, module backend
```

### Các nhóm dữ liệu chính

| Nhóm | Bảng tiêu biểu |
| --- | --- |
| Người dùng | `Users` |
| Phim và nội dung | `Movies`, `Genres`, `MovieGenres`, `Persons`, `MovieCasts`, `MovieDirectors`, `Reviews` |
| Rạp và lịch chiếu | `Rooms`, `Seats`, `Showtimes`, `SeatTypePricing` |
| Giữ chỗ | `BookingHolds`, `BookingSeatHolds`, `BookingComboHolds` |
| Đặt vé và combo | `Bookings`, `Tickets`, `Combos`, `BookingCombos` |
| Thanh toán | `PaymentIntents`, `Payments`, `PaymentMethods` |

## Một số endpoint tiêu biểu

| Phương thức | Endpoint | Mục đích |
| --- | --- | --- |
| GET | `/Home`, `/Home/Movie`, `/Home/Details?id=…` | Khám phá phim |
| POST | `/api/account/register` | Đăng ký tài khoản |
| POST | `/api/account/login` | Đăng nhập API, nhận JWT |
| GET | `/api/account/me` | Thông tin từ JWT; yêu cầu Bearer token hợp lệ |
| POST | `/GoogleAuth/Login` | Bắt đầu đăng nhập Google từ web |
| GET | `/login/oauth2/code/google` | Nhận kết quả OAuth từ Google |
| GET | `/Booking/Index` | Lịch chiếu và bắt đầu đặt vé |
| POST | `/Booking/HoldSeat` | Chọn/bỏ ghế trong phiên giữ chỗ |
| POST | `/Booking/UpdateComboHold` | Thay đổi số lượng combo |
| POST | `/Booking/StartPayment` | Tạo hoặc lấy yêu cầu thanh toán đang hiệu lực |
| GET | `/Booking/PaymentStatus` | Tra cứu trạng thái thanh toán theo quyền sở hữu |
| POST | `/webhooks/sepay` | Nhận thông báo giao dịch ngân hàng |
| POST | `/api/chatbot/recommend` | Gợi ý phim theo bốn tiêu chí |
| POST | `/Staff/SaveSchedule` | Lưu lịch chiếu; yêu cầu Staff/Admin |
| GET | `/Admin/Analytics` | Thống kê kinh doanh; yêu cầu Admin |

Các thao tác web POST được bảo vệ CSRF theo cấu hình Spring Security. API JWT
và webhook dùng chuỗi xác thực riêng; endpoint công khai không đồng nghĩa với
quyền truy cập khu vực Staff/Admin.

## Chạy local

Yêu cầu: JDK 21, SQL Server có database `MovieTicketDB`, dữ liệu/schema phù hợp
với các Entity và cổng TCP SQL Server đã cấu hình. Maven Wrapper có sẵn trong repo.

Trên Windows, cấu hình mặc định kết nối SQL Server local qua Windows Authentication.
Maven tải DLL xác thực tương ứng và đặt vào `backend/target/native`.

Từ thư mục gốc, chạy:

```powershell
.\mvnw.cmd -pl backend spring-boot:run
```

Mở [http://localhost:8080/Home](http://localhost:8080/Home).
Để dùng API phát hành JWT, cấp `JWT_KEY` đủ ít nhất 32 byte qua biến môi trường.
Schema không được Hibernate tự tạo hoặc chỉnh sửa ở cấu hình chạy mặc định;
xem script SQL trong `database/` trước khi chuẩn bị database mới.

Profile `dev` chỉ dành cho phát triển: có seeder tài khoản thử và cơ chế đăng nhập
đặc biệt cho các tài khoản được cho phép. **Không bật profile này trên production.**
Google đăng nhập cần profile `google` và credential riêng; xem
[hướng dẫn Google Login](docs/google-login.md).

## Kiểm thử và CI/CD

Các test hiện có bao phủ nhiều nhóm nghiệp vụ: tài khoản, JWT/Google, phân quyền
controller, giữ ghế, thanh toán, lịch chiếu, quản trị và gợi ý phim. H2 chỉ được dùng
trong test; kiểm tra trên H2 không thay thế kiểm tra tích hợp với SQL Server thật.

Chạy test:

```powershell
.\mvnw.cmd -pl backend -am test
```

Pipeline trên `main` thực hiện build/test, build image gắn commit SHA, đẩy Docker
Hub và deploy VPS khi đủ cấu hình. Bước deploy dùng SSH có xác minh host key, thử
lại kết nối ban đầu và truyền file qua kết nối dùng chung. Script kiểm tra health
của ứng dụng/database; khi bản mới không healthy, khôi phục bản trước nếu có.
Pipeline không tự thay đổi schema database.

Xem [DEPLOYMENT.md](DEPLOYMENT.md) để cấu hình VPS, secrets, biến môi trường và rollback.

## Phạm vi hiện tại và hướng phát triển

- Hoàn thiện dữ liệu phim/thể loại giữa local và production; thêm phương án gợi ý
  thay thế khi thể loại dự đoán không có phim.
- Tối ưu truy vấn thống kê và gợi ý bằng phân trang, tổng hợp tại database,
  kiểm soát số lượng truy vấn và đo độ trễ với dữ liệu lớn.
- Củng cố webhook: bắt buộc khóa trên production, đối chiếu tài khoản nhận tiền
  và bổ sung xử lý đối soát các giao dịch đến sau khi hết hạn.
- Mở rộng kiểm thử đồng thời trên SQL Server, đo tải và bổ sung quan sát hệ thống.

Đây là các hướng cải tiến, không phải cam kết chức năng đã hoàn thành.
Các cấu hình tích hợp và secret được quản lý qua môi trường triển khai; không
đưa giá trị `.env`, khóa JWT, Google Client Secret hoặc webhook key vào README.

## Tài liệu liên quan

- [Hướng dẫn cấu hình và chạy dự án](docs/README.md)
- [Triển khai Docker/VPS và rollback](DEPLOYMENT.md)
- [Cấu hình đăng nhập Google](docs/google-login.md)
- [Tài liệu CI](docs/CI.md)
- [SQL của hệ thống](database/database.sql)
