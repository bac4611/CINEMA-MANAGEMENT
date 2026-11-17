# GitHub Actions CI

The CI-only workflow lives in `.github/workflows/ci.yml`. It runs on branch
pushes, pull requests, and manual dispatches. The existing **Cinema build**
workflow in `.github/workflows/deploy.yml` also tests/packages the application,
builds its Docker image, and supports gated publishing and VPS deployment.
See [DEPLOYMENT.md](../DEPLOYMENT.md) for secrets, setup, activation and rollback.

## Build and tests

The Ubuntu runner installs Temurin Java 21, caches Maven dependencies, and runs
the repository's Maven Wrapper from the root:

```bash
bash ./mvnw --batch-mode --no-transfer-progress clean verify
```

On Windows, run the equivalent command:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

The existing test configuration in `backend/src/test/resources` uses H2 in
SQL Server compatibility mode. CI does not need database credentials, a running
SQL Server, or GitHub secrets. These tests do not replace integration testing
against a real SQL Server instance.

Maven includes `frontend/templates` and `frontend/static` in the backend JAR.
The workflow does not start the application or apply database migrations.

## View results

Commit and push the workflow to GitHub, then open **Actions > Cinema CI**.
Select a run to inspect the build logs and download its artifacts:

- `test-reports-*`: available when Maven produces reports, including failed
  test runs; retained for 7 days.
- `cinema-jar-*`: application JAR from a successful build; retained for 14 days.

Manual runs are available through **Run workflow** once the workflow exists on
the repository's default branch. GitHub Actions must be enabled for the repo.

To require CI before merging, configure a branch protection rule or ruleset
and require the **Build and test (Java 21)** status check after its first run.

Action references follow the official repositories:
[checkout](https://github.com/actions/checkout),
[setup-java](https://github.com/actions/setup-java), and
[upload-artifact](https://github.com/actions/upload-artifact).
