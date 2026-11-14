# Quy ước view Thymeleaf

Hai file Razor cấp `Views` không được sao chép thành HTML vì Spring Boot/Thymeleaf
không đọc `_ViewImports.cshtml` hoặc `_ViewStart.cshtml`.

## Thay cho `_ViewImports.cshtml`

- Controller đưa model vào view bằng `Model#addAttribute`.
- Template truy cập trực tiếp thuộc tính Java, ví dụ `${movie.title}`.
- ASP.NET Tag Helpers được thay bằng thuộc tính Thymeleaf:
  - `asp-controller`, `asp-action`, `asp-route-*` → `th:href` hoặc `th:action`.
  - `asp-for` → `th:field`.
  - `asp-validation-for` → `th:errors`.
- Mỗi template khai báo `xmlns:th="http://www.thymeleaf.org"`.

## Thay cho `_ViewStart.cshtml`

Layout được khai báo rõ ràng tại phần tử `<html>` của từng trang:

- `templates/staff/**` → `layouts/staff`.
- `templates/admin/**` → `layouts/admin`.
- Các bước thanh toán cần giao diện đặt vé → `layouts/booking`.
- Account, Home và các trang công khai → `layouts/main`.

Ví dụ:

```html
<html lang="vi" xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layouts/staff :: layout(~{::title}, 'halls', ~{::main}, ~{::scripts})}">
```

Các fragment độc lập như `fragments/chatbot.html`, `fragments/validation.html` và
`booking/_booking-timer.html` không tự chọn layout.
