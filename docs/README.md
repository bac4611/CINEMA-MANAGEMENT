# Cinema Spring

## Modules

- `backend`: Spring Boot Java source and tests.
- `frontend`: Thymeleaf templates and static assets.
- `database`: SQL schema, seed, and test scripts.
- `docker`: Backend and SQL Server container definitions.

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

The connection can be overridden without editing source. An override must
continue to use Windows Authentication:

```powershell
$env:DB_URL = "jdbc:sqlserver://localhost:1433;databaseName=MovieTicketDB;integratedSecurity=true;authenticationScheme=NativeAuthentication;encrypt=true;trustServerCertificate=true"
.\mvnw.cmd -pl backend spring-boot:run
```
