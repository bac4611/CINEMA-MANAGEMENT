# URL routing migration

Inventory captured before changing controllers, templates or security. Each row lists every old alias, the canonical application URL and compatibility policy. Java names and Thymeleaf template paths stay unchanged.

| Method | Previous URLs | Canonical URL | Compatibility |
| --- | --- | --- | --- |
| POST | `/api/account/register` | `/api/account/register` | Unchanged external contract |
| POST | `/login`, `/api/account/login` | `/login` | Unchanged external contract |
| GET | `/api/account/me` | `/api/account/me` | Unchanged external contract |
| GET | `/Account/Login`, `/Account/login`, `/account/Login`, `/account/login` | `/account/login` | Legacy aliases: GET-only 308 redirect |
| POST | `/Account/Login`, `/Account/login`, `/account/Login`, `/account/login` | `/account/login` | Direct aliases retained; no redirect |
| POST | `/Account/Logout`, `/Account/logout`, `/account/Logout`, `/account/logout` | `/account/logout` | Direct aliases retained; no redirect |
| GET | `/Account/AccessDenied`, `/Account/access-denied`, `/account/AccessDenied`, `/account/access-denied` | `/account/access-denied` | Legacy aliases: GET-only 308 redirect |
| GET | `/Account/Register`, `/Account/register`, `/account/Register`, `/account/register` | `/account/register` | Legacy aliases: GET-only 308 redirect |
| POST | `/Account/Register`, `/Account/register`, `/account/Register`, `/account/register` | `/account/register` | Direct aliases retained; no redirect |
| GET | `/Account/EmailVerificationSent`, `/Account/email-verification-sent`, `/account/EmailVerificationSent`, `/account/email-verification-sent` | `/account/email-verification-sent` | Legacy aliases: GET-only 308 redirect |
| GET | `/Account/ForgotPassword`, `/Account/forgot-password`, `/account/ForgotPassword`, `/account/forgot-password` | `/account/forgot-password` | Legacy aliases: GET-only 308 redirect |
| POST | `/Account/ForgotPassword`, `/Account/forgot-password`, `/account/ForgotPassword`, `/account/forgot-password` | `/account/forgot-password` | Direct aliases retained; no redirect |
| GET | `/Account/ForgotPasswordConfirmation`, `/Account/forgot-password-confirmation`, `/account/ForgotPasswordConfirmation`, `/account/forgot-password-confirmation` | `/account/forgot-password-confirmation` | Legacy aliases: GET-only 308 redirect |
| GET | `/Account/ResetPassword`, `/Account/reset-password`, `/account/ResetPassword`, `/account/reset-password` | `/account/reset-password` | Legacy aliases: GET-only 308 redirect |
| POST | `/Account/ResetPassword`, `/Account/reset-password`, `/account/ResetPassword`, `/account/reset-password` | `/account/reset-password` | Direct aliases retained; no redirect |
| GET | `/Account/ResetPasswordConfirmation`, `/Account/reset-password-confirmation`, `/account/ResetPasswordConfirmation`, `/account/reset-password-confirmation` | `/account/reset-password-confirmation` | Legacy aliases: GET-only 308 redirect |
| GET | `/Account/ConfirmEmail`, `/Account/confirm-email`, `/account/ConfirmEmail`, `/account/confirm-email` | `/account/confirm-email` | Legacy aliases: GET-only 308 redirect |
| POST | `/Account/ResendVerificationEmail`, `/Account/resend-verification-email`, `/account/ResendVerificationEmail`, `/account/resend-verification-email` | `/account/resend-verification-email` | Direct aliases retained; no redirect |
| GET | `/Account/Profile`, `/Account/profile`, `/account/Profile`, `/account/profile` | `/account/profile` | Legacy aliases: GET-only 308 redirect |
| POST | `/Account/Profile`, `/Account/profile`, `/account/Profile`, `/account/profile` | `/account/profile` | Direct aliases retained; no redirect |
| POST | `/GoogleAuth/Login`, `/GoogleAuth/login`, `/google-auth/Login`, `/google-auth/login` | `/google-auth/login` | Direct aliases retained; no redirect |
| GET | `/GoogleAuth/Callback`, `/GoogleAuth/callback`, `/google-auth/Callback`, `/google-auth/callback` | `/google-auth/callback` | Direct aliases retained; no redirect |
| GET | `/Admin`, `/Admin/`, `/Admin/Index`, `/Admin/index`, `/admin`, `/admin/`, `/admin/Index`, `/admin/index` | `/admin` | Legacy aliases: GET-only 308 redirect |
| GET | `/Admin/Analytics`, `/Admin/analytics`, `/admin/Analytics`, `/admin/analytics` | `/admin/analytics` | Legacy aliases: GET-only 308 redirect |
| GET | `/Admin/Staff`, `/Admin/staff`, `/admin/Staff`, `/admin/staff` | `/admin/staff` | Legacy aliases: GET-only 308 redirect |
| GET | `/Admin/Settings`, `/Admin/settings`, `/admin/Settings`, `/admin/settings` | `/admin/settings` | Legacy aliases: GET-only 308 redirect |
| POST | `/Admin/UpdateSettings`, `/Admin/update-settings`, `/admin/UpdateSettings`, `/admin/update-settings` | `/admin/update-settings` | Canonical only; no POST redirect |
| POST | `/Admin/CreateStaff`, `/Admin/create-staff`, `/admin/CreateStaff`, `/admin/create-staff` | `/admin/create-staff` | Canonical only; no POST redirect |
| POST | `/Admin/UpdateStaff`, `/Admin/update-staff`, `/admin/UpdateStaff`, `/admin/update-staff` | `/admin/update-staff` | Canonical only; no POST redirect |
| GET | `/Booking`, `/Booking/`, `/Booking/Index`, `/Booking/index`, `/booking`, `/booking/`, `/booking/Index`, `/booking/index` | `/booking` | Legacy aliases: GET-only 308 redirect |
| GET | `/Booking/SelectShowtime`, `/Booking/select-showtime`, `/booking/SelectShowtime`, `/booking/select-showtime` | `/booking/select-showtime` | Legacy aliases: GET-only 308 redirect |
| POST | `/Booking/SelectShowtime`, `/Booking/select-showtime`, `/booking/SelectShowtime`, `/booking/select-showtime` | `/booking/select-showtime` | Direct aliases retained; no redirect |
| POST | `/Booking/SelectShowtimeFromSchedule`, `/Booking/select-showtime-from-schedule`, `/booking/SelectShowtimeFromSchedule`, `/booking/select-showtime-from-schedule` | `/booking/select-showtime-from-schedule` | Direct aliases retained; no redirect |
| GET | `/Booking/SelectShowtimeFromSchedule`, `/Booking/select-showtime-from-schedule`, `/booking/SelectShowtimeFromSchedule`, `/booking/select-showtime-from-schedule` | `/booking/select-showtime-from-schedule` | Legacy aliases: GET-only 308 redirect |
| GET | `/Booking/SelectSeats`, `/Booking/select-seats`, `/booking/SelectSeats`, `/booking/select-seats` | `/booking/select-seats` | Legacy aliases: GET-only 308 redirect |
| POST | `/Booking/SelectSeats`, `/Booking/select-seats`, `/booking/SelectSeats`, `/booking/select-seats` | `/booking/select-seats` | Direct aliases retained; no redirect |
| POST | `/Booking/HoldSeat`, `/Booking/hold-seat`, `/booking/HoldSeat`, `/booking/hold-seat` | `/booking/hold-seat` | Direct aliases retained; no redirect |
| POST | `/Booking/UpdateComboHold`, `/Booking/update-combo-hold`, `/booking/UpdateComboHold`, `/booking/update-combo-hold` | `/booking/update-combo-hold` | Direct aliases retained; no redirect |
| POST | `/Booking/ClearComboHolds`, `/Booking/clear-combo-holds`, `/booking/ClearComboHolds`, `/booking/clear-combo-holds` | `/booking/clear-combo-holds` | Direct aliases retained; no redirect |
| GET | `/Booking/SelectConcessions`, `/Booking/select-concessions`, `/booking/SelectConcessions`, `/booking/select-concessions` | `/booking/select-concessions` | Legacy aliases: GET-only 308 redirect |
| POST | `/Booking/SelectConcessions`, `/Booking/select-concessions`, `/booking/SelectConcessions`, `/booking/select-concessions` | `/booking/select-concessions` | Direct aliases retained; no redirect |
| GET | `/Booking/Checkout`, `/Booking/checkout`, `/booking/Checkout`, `/booking/checkout` | `/booking/checkout` | Legacy aliases: GET-only 308 redirect |
| POST | `/Booking/StartPayment`, `/Booking/start-payment`, `/booking/StartPayment`, `/booking/start-payment` | `/booking/start-payment` | Direct aliases retained; no redirect |
| GET | `/Booking/Payment`, `/Booking/payment`, `/booking/Payment`, `/booking/payment` | `/booking/payment` | Direct aliases retained; no redirect |
| POST | `/Booking/CompleteDevPayment`, `/Booking/complete-dev-payment`, `/booking/CompleteDevPayment`, `/booking/complete-dev-payment` | `/booking/complete-dev-payment` | Direct aliases retained; no redirect |
| GET | `/Booking/PaymentStatus`, `/Booking/payment-status`, `/booking/PaymentStatus`, `/booking/payment-status` | `/booking/payment-status` | Direct aliases retained; no redirect |
| GET | `/Booking/BookingExpired`, `/Booking/booking-expired`, `/booking/BookingExpired`, `/booking/booking-expired` | `/booking/booking-expired` | Legacy aliases: GET-only 308 redirect |
| POST | `/Booking/CancelBooking`, `/Booking/cancel-booking`, `/booking/CancelBooking`, `/booking/cancel-booking` | `/booking/cancel-booking` | Direct aliases retained; no redirect |
| GET | `/Booking/PaymentSuccess`, `/Booking/payment-success`, `/booking/PaymentSuccess`, `/booking/payment-success` | `/booking/payment-success` | Direct aliases retained; no redirect |
| POST | `/webhooks/sepay` | `/webhooks/sepay` | Unchanged external contract |
| POST | `/api/chatbot/recommend` | `/api/chatbot/recommend` | Unchanged external contract |
| GET | `/Home`, `/Home/`, `/Home/Index`, `/Home/index`, `/home`, `/home/`, `/home/Index`, `/home/index` | `/home` | Legacy aliases: GET-only 308 redirect |
| GET | `/Home/Movie`, `/Home/movie`, `/home/Movie`, `/home/movie` | `/movies` | Legacy aliases: GET-only 308 redirect |
| GET | `/Home/Details/{id}`, `/Home/details/{id}`, `/home/Details/{id}`, `/home/details/{id}` | `/movies/details/{id}` | Legacy aliases: GET-only 308 redirect |
| GET | `/Home/Details`, `/Home/details`, `/home/Details`, `/home/details` | `/movies/details` | Legacy aliases: GET-only 308 redirect |
| POST | `/Home/AddReview`, `/Home/add-review`, `/home/AddReview`, `/home/add-review` | `/movies/add-review` | Direct aliases retained; no redirect |
| GET | `/Home/TicketPricing`, `/Home/ticket-pricing`, `/home/TicketPricing`, `/home/ticket-pricing` | `/ticket-pricing` | Legacy aliases: GET-only 308 redirect |
| GET | `/Staff`, `/Staff/`, `/Staff/Index`, `/Staff/index`, `/staff`, `/staff/`, `/staff/Index`, `/staff/index` | `/staff` | Legacy aliases: GET-only 308 redirect |
| GET | `/Staff/GetSchedule`, `/Staff/schedule`, `/staff/GetSchedule`, `/staff/schedule` | `/staff/schedule` | Legacy aliases: GET-only 308 redirect |
| GET | `/Staff/GetScreeningRoomState`, `/Staff/screening-room-state`, `/staff/GetScreeningRoomState`, `/staff/screening-room-state` | `/staff/screening-room-state` | Legacy aliases: GET-only 308 redirect |
| POST | `/Staff/SaveSchedule`, `/Staff/save-schedule`, `/staff/SaveSchedule`, `/staff/save-schedule` | `/staff/save-schedule` | Canonical only; no POST redirect |
| GET | `/Staff/Halls`, `/Staff/halls`, `/staff/Halls`, `/staff/halls` | `/staff/halls` | Legacy aliases: GET-only 308 redirect |
| GET | `/Staff/Concessions`, `/Staff/concessions`, `/staff/Concessions`, `/staff/concessions` | `/staff/concessions` | Legacy aliases: GET-only 308 redirect |
| GET | `/Staff/CreateConcession`, `/Staff/create-concession`, `/staff/CreateConcession`, `/staff/create-concession` | `/staff/create-concession` | Legacy aliases: GET-only 308 redirect |
| POST | `/Staff/CreateConcession`, `/Staff/create-concession`, `/staff/CreateConcession`, `/staff/create-concession` | `/staff/create-concession` | Canonical only; no POST redirect |
| GET | `/Staff/EditConcession`, `/Staff/edit-concession`, `/staff/EditConcession`, `/staff/edit-concession` | `/staff/edit-concession` | Legacy aliases: GET-only 308 redirect |
| POST | `/Staff/EditConcession`, `/Staff/edit-concession`, `/staff/EditConcession`, `/staff/edit-concession` | `/staff/edit-concession` | Canonical only; no POST redirect |
| POST | `/Staff/CreateMovie`, `/Staff/create-movie`, `/staff/CreateMovie`, `/staff/create-movie` | `/staff/create-movie` | Canonical only; no POST redirect |
| POST | `/Staff/EditMovie`, `/Staff/edit-movie`, `/staff/EditMovie`, `/staff/edit-movie` | `/staff/edit-movie` | Canonical only; no POST redirect |
| POST | `/Staff/DeactivateMovie`, `/Staff/deactivate-movie`, `/staff/DeactivateMovie`, `/staff/deactivate-movie` | `/staff/deactivate-movie` | Canonical only; no POST redirect |

Root `/` redirects to `/movies` (302), so entering only the domain opens the movie catalogue. Legacy GET redirects use 308 and preserve the raw query string (including email tokens and movie/booking filters). Canonical routes do not redirect back to legacy paths.

## Protected integrations

- Spring Security endpoints `/oauth2/authorization/google` and `/login/oauth2/code/google` are unchanged. GoogleAuth callback aliases and configured success/failure URLs are retained. The UI uses `/google-auth/login`.
- `POST /webhooks/sepay`, all `/api/account/**`, `POST /login` and `POST /api/chatbot/recommend` keep their methods, paths and contracts.
- Existing payment page/status/success GET aliases stay on the original handler, without redirects. New internal links use canonical URLs.
- Account, booking and movie-review POST aliases stay on the same handlers for cached forms/in-flight sessions, with CSRF and authorization unchanged. No POST compatibility redirects are registered. Staff/admin forms use canonical endpoints.
- Old email confirmation/reset links remain valid via query-preserving GET redirects; newly generated email links use kebab-case.

## Scope and limitations

No database schema/data, Docker, CI, VPS, commit, push or deployment changes. Live Google OAuth, SMTP delivery and SePay transactions require production credentials and are not triggered by local tests.

The unused `frontend/static/js/offers.js` already calls `/Home/ValidateOfferCode`, which has no controller in this repository. No template loads that script. This is recorded as a pre-existing orphan integration, not silently renamed or implemented as part of routing.

## Verification results

- Command: `./mvnw.cmd --offline --batch-mode --no-transfer-progress -pl backend -am verify`.
- Result: **BUILD SUCCESS; 392 tests, 0 failures, 0 errors, 0 skipped**. Includes the Maven test phase and Spring Boot executable JAR packaging.
- The original 70 tests remain, with URL assertions updated. Added 322 test cases: 320 routing/security/compatibility cases and 2 email-link generation cases.
- `routing-contract.csv` freezes 282 pre-migration method/path combinations. Tests verify each canonical route resolves to a controller, all preserved aliases use the same Java handler, and 119 legacy GET aliases redirect to canonical URLs while preserving encoded query parameters. Dynamic movie IDs and context paths are covered.
- Rendered real Thymeleaf public home/movie/pricing/account/booking pages and authorized management pages against isolated H2. Tested sessionless booking recovery, profile/management protection, role checks, CSRF, and removed staff POST routes returning 404 rather than redirecting.
- Existing Google authorization tests confirm the registered `/login/oauth2/code/google` redirect URI; Google account callback/service tests pass. Existing SePay signature/delegation tests pass; added HTTP-level webhook authentication/CSRF-exemption coverage. Forgot-password and web/API registration tests verify newly generated email links.
- `node --check frontend/static/js/staff.js`, `node --check frontend/static/js/staff-schedule.js`, and `git diff --check` passed.
- Static scan of application URL references in HTML/JS finds no unmatched routes except the pre-existing unused offers integration above.
- No live SMTP email, Google account consent, SePay bank transfer or production database action was performed. These integration round trips still need smoke testing with the deployed configuration. This routing change does not diagnose or guarantee a fix for the previously observed production HTTP 500/database health issue.

## Deployment review

Build/deploy the resulting code through the existing workflow when authorized; editing Java files alone does not update a running Docker image. Smoke-test `/` → `/movies`, `/home`, account forms, Google Login, email links, booking/payment and management roles after deployment. Old account/booking/review POST URLs and payment GET URLs intentionally remain direct handler aliases; do not replace them with redirects while cached forms, emails or in-flight payments may still use them. Old staff/admin POST aliases are removed: reload stale management pages before submitting a form after deployment.

No database, Dockerfile, Compose, GitHub Actions or VPS configuration changed. DEPLOYMENT.md and the audit document only update their example login probe URL.

## Changed files (58)

- `DEPLOYMENT.md`
- `README.md`
- `backend/src/main/java/com/cinema/management/config/AccountSecurityConfiguration.java`
- `backend/src/main/java/com/cinema/management/config/HomeRoutingConfiguration.java`
- `backend/src/main/java/com/cinema/management/controller/account/AccountApiController.java`
- `backend/src/main/java/com/cinema/management/controller/account/AccountController.java`
- `backend/src/main/java/com/cinema/management/controller/account/GoogleAuthController.java`
- `backend/src/main/java/com/cinema/management/controller/admin/AdminController.java`
- `backend/src/main/java/com/cinema/management/controller/booking/BookingController.java`
- `backend/src/main/java/com/cinema/management/controller/home/HomeController.java`
- `backend/src/main/java/com/cinema/management/controller/home/LegacyRoutingController.java`
- `backend/src/main/java/com/cinema/management/controller/staff/StaffController.java`
- `backend/src/test/java/com/cinema/management/controller/account/AccountControllerTests.java`
- `backend/src/test/java/com/cinema/management/controller/account/GoogleAuthControllerTests.java`
- `backend/src/test/java/com/cinema/management/controller/account/GoogleAuthSecurityTests.java`
- `backend/src/test/java/com/cinema/management/controller/account/GoogleLoginConfigurationTests.java`
- `backend/src/test/java/com/cinema/management/controller/account/GoogleLoginMissingCredentialsTests.java`
- `backend/src/test/java/com/cinema/management/controller/admin/AdminControllerTests.java`
- `backend/src/test/java/com/cinema/management/controller/booking/BookingControllerTests.java`
- `backend/src/test/java/com/cinema/management/controller/home/RoutingIntegrationTests.java`
- `backend/src/test/java/com/cinema/management/controller/staff/StaffControllerTests.java`
- `backend/src/test/resources/routing-contract.csv`
- `docs/CICD-AUDIT.md`
- `docs/URL-ROUTING.md`
- `docs/google-login.md`
- `frontend/static/js/staff-schedule.js`
- `frontend/static/js/staff.js`
- `frontend/templates/account/access-denied.html`
- `frontend/templates/account/email-verification-sent.html`
- `frontend/templates/account/forgot-password-confirmation.html`
- `frontend/templates/account/forgot-password.html`
- `frontend/templates/account/login.html`
- `frontend/templates/account/profile.html`
- `frontend/templates/account/register.html`
- `frontend/templates/account/reset-password-confirmation.html`
- `frontend/templates/account/reset-password.html`
- `frontend/templates/admin/analytics.html`
- `frontend/templates/admin/index.html`
- `frontend/templates/admin/settings.html`
- `frontend/templates/admin/staff.html`
- `frontend/templates/booking/_booking-timer.html`
- `frontend/templates/booking/checkout.html`
- `frontend/templates/booking/index.html`
- `frontend/templates/booking/payment-success.html`
- `frontend/templates/booking/payment.html`
- `frontend/templates/booking/select-concessions.html`
- `frontend/templates/booking/select-seats.html`
- `frontend/templates/booking/select-showtime.html`
- `frontend/templates/error.html`
- `frontend/templates/home/details.html`
- `frontend/templates/home/index.html`
- `frontend/templates/home/movie.html`
- `frontend/templates/layouts/admin.html`
- `frontend/templates/layouts/main.html`
- `frontend/templates/layouts/staff.html`
- `frontend/templates/staff/concessions.html`
- `frontend/templates/staff/halls.html`
- `frontend/templates/staff/index.html`
