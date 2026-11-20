# Kiểm tra CI/CD CINEMA-MANAGEMENT

## Bằng chứng về workflow chỉ có build

Đối chiếu ngày 17/11/2026 bằng Git local, `git ls-remote origin refs/heads/main`
và GitHub REST API công khai:

- Remote main: `49d5fce0c3ce6cce98f41c24faa6863d62bd2887`.
- [Cinema build #3](https://github.com/bac4611/CINEMA-MANAGEMENT/actions/runs/44058834888):
  `workflow_dispatch`, branch `main`, đúng SHA trên, kết quả success.
- API jobs của run chỉ có `Test and package Java 21 application`.
- `git show 49d5fce:.github/workflows/deploy.yml` chỉ khai báo `jobs.build`.
  Workflow này được thêm tại `ace6e86`, merge qua PR #8 tại `49d5fce`.
- Bản local có ba job là thay đổi chưa commit. Dockerfile, Compose, deploy script
  và production profile cũng chưa được Git theo dõi tại thời điểm bắt đầu kiểm tra.
- Branch đang làm việc: `googleLogin/CI-pipeline`; local branch `main` còn ở
  `64a415e`, chậm hơn remote main 19 commit. Không dùng local main cũ để chẩn đoán.

Vì vậy #3 chưa có job Docker/Deploy, không phải hai job bị skipped bởi secrets
hay biến bật triển khai. Run này không build/push image và không SSH lên VPS.
Không suy ra trạng thái toàn bộ Docker Hub từ kết quả một run.

Re-run run cũ vẫn dùng SHA/ref của sự kiện cũ theo
[GitHub Docs](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs).
Sau khi merge phải tạo run mới trên main để dùng workflow đã sửa.

## Hành vi pipeline sau sửa

| Sự kiện | Build/test | Docker build | Push SHA tag | Deploy |
| --- | --- | --- | --- | --- |
| PR vào main, kể cả fork | Có | Sau build thành công | Không | Không |
| Push main | Có | Sau build thành công | Bắt buộc | Khi ENABLE_VPS_DEPLOY=true |
| Manual main, deploy=false | Có | Sau build thành công | Bắt buộc | Không |
| Manual main, deploy=true | Có | Sau build thành công | Bắt buộc | Khi ENABLE_VPS_DEPLOY=true |
| Manual branch khác | Có | Sau build thành công | Không | Không |
| Push branch khác | Không trong deploy.yml | Không | Không | Không |

Workflow `ci.yml` riêng vẫn chạy theo trigger của nó. PR cập nhật workflow cũng
cần được review trước khi merge; production environment nên chỉ cho phép main.

Deploy bị skipped nếu build/docker lỗi hoặc bị hủy, chưa xác nhận push thành
công, ref không phải main, biến repository không đúng chuỗi `true`, hoặc manual
không chọn deploy. Thiếu Docker Hub credentials trên trusted main làm Docker job
failed. Thiếu VPS secrets làm deploy failed khi kiểm tra cấu hình, không âm thầm
skipped. Environment protection có thể làm job chờ phê duyệt; đây là trạng thái
khác với skipped.

## Compose, môi trường và rollback

- Image `bacdepzaiz7/cinema:<40-character-sha>`, linux/amd64; Buildx, cache GHA.
- SSH xác minh known_hosts bắt buộc; file tải vào
  `/opt/cinema/releases/<sha>-<run-id>-<attempt>/`.
- Script luôn chạy trong `/opt/cinema`, dùng `--project-name cinema`,
  `--project-directory` và `--env-file` tuyệt đối. Vì vậy Compose lấy `.env` từ
  `/opt/cinema` dù file Compose nằm trong release hoặc thư mục snapshot tạm.
- Tag truyền qua môi trường lệnh Compose ưu tiên hơn tag cũ trong `.env`.
- `.env` trên VPS chứa SQL Server URL/user/password và JWT_KEY; không truyền các
  giá trị này qua GitHub logs. Linux dùng SQL authentication, không dùng Windows
  integratedSecurity. Database/schema phải tồn tại và container phải truy cập được.
- Production không tự tạo schema hay chạy seed/migration; không có database
  service, không publish port 1433, không xóa Docker volumes.
- App bind host `127.0.0.1:8080` (hoặc CINEMA_HTTP_PORT). Cần reverse proxy nếu
  phục vụ Internet; không truy cập trực tiếp bằng public-IP:8080 với cấu hình này.
- Docker kiểm tra HTTP `/actuator/health`; deploy kiểm tra thêm
  `/actuator/health/readiness`, gồm DB. Management chỉ bind container loopback
  port 8081, không publish. Security config hiện tại cho phép truy cập health.
- Khi up/HTTP readiness lỗi, khôi phục image và Compose của release khỏe gần nhất,
  vẫn trả exit khác 0. Lần đầu chưa có baseline thì không thể rollback.
- Rollback không khôi phục `.env` hoặc database. Script không tự đẩy application
  logs vào Actions vì logs có thể chứa thông tin kết nối; đọc trực tiếp trên VPS.

## Cấu hình trước khi chạy

Trong Settings → Secrets and variables → Actions:

| Loại | Tên | Giá trị/yêu cầu |
| --- | --- | --- |
| Repository secret | DOCKERHUB_USERNAME | bacdepzaiz7 |
| Repository secret | DOCKERHUB_TOKEN | Token có quyền push repository cinema |
| Secret | VPS_HOST | DNS hoặc IPv4 VPS |
| Secret | VPS_PORT | Port SSH, mặc định 22 |
| Secret | VPS_USER | deploy |
| Secret | VPS_SSH_KEY | Private key đúng public key của deploy |
| Secret | VPS_KNOWN_HOSTS | Host key xác minh qua kênh tin cậy; port khác 22 dùng [host]:port |
| Repository variable | ENABLE_VPS_DEPLOY | true khi đã chuẩn bị và chấp thuận triển khai |

Không có phiên GitHub xác thực trong môi trường kiểm tra nên chưa xác minh được
sự tồn tại/quyền của secrets và production environment. Không cần cung cấp giá
trị secrets trong hội thoại. Khi chạy mới, workflow kiểm tra thiếu credentials và
Docker login/push xác minh quyền thực tế. Docker Hub private yêu cầu `deploy`
đăng nhập riêng trên VPS bằng token chỉ đọc; đăng nhập trên runner không áp dụng
cho VPS. Xem [DEPLOYMENT.md](../DEPLOYMENT.md) để tạo known_hosts an toàn.

VPS đã có Docker/Compose thì không cần cài lại. Quản trị viên tạo thư mục:

```bash
sudo install -d -m 700 -o deploy -g "$(id -gn deploy)" /opt/cinema
```

Chuyển `.env.example` từ bản đã review lên VPS. Với user deploy:

```bash
cd /opt/cinema
cp .env.example .env
chmod 600 .env
${EDITOR:-vi} .env
docker compose version
docker info >/dev/null
```

Điền database thực và JWT_KEY ngẫu nhiên ít nhất 32 byte, giữ profile production.
Không chạy lệnh copy đè nếu `.env` đã tồn tại. CI sẽ chuyển Compose và script lên
release directory, không yêu cầu cấu hình DB trong repository.

## Commit, push và merge (người vận hành thực hiện)

Do biến đang là true, đặt `ENABLE_VPS_DEPLOY=false` trước merge nếu chưa phê duyệt
production. Push/merge vào main với true sẽ tự kích hoạt deploy.

Working tree đã có nhiều thay đổi trước kiểm tra. Review từng diff; không dùng
`git add .` vì có thay đổi nghiệp vụ/database không thuộc lần kiểm tra này.
Danh sách bên dưới bao gồm cả các file triển khai có sẵn nhưng chưa commit:

```bash
git diff
git add .github/workflows/deploy.yml Dockerfile .dockerignore compose.prod.yml
git add scripts/deploy.sh scripts/tests/deploy-test.sh .env.example
git add backend/src/main/resources/application-production.properties backend/pom.xml
git add .gitattributes .gitignore DEPLOYMENT.md docs/CICD-AUDIT.md
git diff --cached
git commit -m "Complete Docker Hub and VPS deployment pipeline"
git push origin HEAD
```

Tạo PR branch hiện tại vào main, chờ Build/Test và Docker build thành công, rồi
merge sau review. `backend/pom.xml` cần dependency actuator có sẵn trong diff để
health endpoint tồn tại. Chọn riêng các thay đổi khác nếu muốn đưa chúng vào PR.
Không tự merge/push trong phiên kiểm tra này.

Sau khi VPS/secrets sẵn sàng và được chấp thuận, đặt ENABLE_VPS_DEPLOY=true,
Actions → Cinema build → Run workflow → main → deploy=true. Nếu dùng GitHub CLI
đã đăng nhập:

```bash
gh workflow run deploy.yml --ref main -f deploy=true
gh run list --workflow deploy.yml --limit 5
```

Run mới phải có ba job. Docker step phải trả digest của tag đúng SHA; Deploy phải
báo Deployment healthy. Không bấm Re-run #3 để thử workflow mới.

## Xác nhận trên VPS

```bash
cd /opt/cinema
export CINEMA_IMAGE_TAG=$(cat .deployed-tag)
docker compose --project-name cinema --env-file .env -f compose.prod.yml ps
container_id=$(docker compose --project-name cinema --env-file .env -f compose.prod.yml ps -q cinema)
docker inspect --format '{{.Config.Image}} {{.State.Health.Status}}' "$container_id"
docker logs --tail 100 "$container_id"
docker compose --project-name cinema --env-file .env -f compose.prod.yml exec -T cinema \
  wget -q -O - http://127.0.0.1:8081/actuator/health/readiness
curl --fail --silent --show-error -o /dev/null -w '%{http_code}\n' \
  "http://127.0.0.1:${CINEMA_HTTP_PORT:-8080}/account/login"
```

Kỳ vọng: service running/healthy, image đúng SHA, readiness `status=UP`, trang
login HTTP 200. Nếu đổi CINEMA_HTTP_PORT trong .env thì dùng giá trị thực trong
lệnh curl (không source `.env` như shell script). Kiểm tra thêm URL HTTPS qua
reverse proxy nếu đã cấu hình. Muốn rollback thủ công:

```bash
bash releases/<release-id>/deploy.sh "$(cat .previous-tag)" .previous-compose.yml
```

## Kết quả kiểm tra local

- Java 21 Maven Wrapper verify: 70 tests, 0 failures/errors/skipped; JAR package
  thành công, có frontend resources và production profile.
- Bash syntax và deploy-test.sh chạy qua Ubuntu WSL: pass; Docker giả lập, không
  kết nối production hay database thật.
- Docker Compose config --quiet: pass với biến giả trong thư mục tạm, không dùng
  hay in credentials thật.
- GitHub Actions YAML: actionlint v1.7.12 pass cho deploy.yml và ci.yml (không có
  ShellCheck trên Windows; Bash syntax được kiểm tra riêng trong WSL).
- Docker daemon local chưa chạy nên chưa build/run image thật, chưa kiểm tra SQL
  Server thật. Không push Docker Hub, SSH hay deploy VPS trong lần kiểm tra này.
