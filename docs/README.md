# Cinema Spring

Giới thiệu dự án, website demo và tài liệu nghiệp vụ dành cho nhà tuyển dụng:
[COSMOS Cinema — README chính](../README.md).

## Modules

- `backend`: Spring Boot Java source and tests.
- `frontend`: Thymeleaf templates and static assets.
- `database`: SQL schema, seed, and test scripts.
- `Dockerfile` and `compose.prod.yml`: application image and production runtime.
- `docker`: reserved directories for container infrastructure.

## GitHub Actions

The workflow lives at `.github/workflows/deploy.yml`. It runs on pull requests
to `main`, pushes to `main`, or manually through GitHub Actions. It sets up
Java 21, runs Maven tests, and packages the backend with the frontend resources.
Tests use the existing H2 test configuration; the application still uses SQL Server.
The JAR and Windows authentication DLL are retained as an artifact for seven days.
After CI succeeds it builds the application image and can publish it to Docker
Hub and deploy to an Ubuntu VPS. Production CD is disabled until its enable
variable, secrets and VPS configuration are supplied. See
[DEPLOYMENT.md](../DEPLOYMENT.md) for the complete setup and rollback procedure.

Frontend files live directly under `frontend`, without a Java source tree:

```text
backend/src/main/
  java/
  resources/
    application.properties
    application-dev.properties
frontend/
  templates/
  static/
```

Maven copies `frontend/templates` and `frontend/static` into the backend
classpath during the build. Application configuration lives in
`backend/src/main/resources`.

Run the project from the repository root:

```bash
./mvnw spring-boot:run -pl backend
```

## Local SQL Server Express

The default connection targets:

- Server: `localhost:1433` (the local `SQLEXPRESS` instance)
- Database: `MovieTicketDB`
- Authentication: Windows Authentication
- Encryption: enabled with `trustServerCertificate=true` for local development

Create the database by running `database/schema/00-create-database.sql` in
SQL Server Management Studio or Azure Data Studio. The Microsoft JDBC native
authentication DLL (`mssql-jdbc_auth-*.x64.dll`) must be available on
`PATH` or `java.library.path` for Windows Authentication.

Because the Microsoft JDBC driver uses TCP, enable `TCP/IP` for
`SQL Server Network Configuration > Protocols for SQLEXPRESS`, set
`IPAll > TCP Port` to `1433`, clear `TCP Dynamic Ports`, and restart
`SQL Server (SQLEXPRESS)`. A fixed port avoids requiring the disabled
`SQL Server Browser` service.

Maven downloads the matching x64 DLL and copies it to
`backend/target/native`. Both `spring-boot:run` and the checked-in VS Code
launch configuration pass that directory through `java.library.path`.

The development connection can be overridden without editing source:

```powershell
$env:DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=MovieTicketDB;integratedSecurity=true;authenticationScheme=NativeAuthentication;encrypt=true;trustServerCertificate=true"
.\mvnw.cmd -pl backend spring-boot:run
```

Linux production containers use SQL authentication through
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD`, with `SPRING_PROFILES_ACTIVE=production`.
