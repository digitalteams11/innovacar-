# Local Development Setup

## Backend Port 8082

The backend is configured to run on:

```properties
server.port=8082
server.address=0.0.0.0
```

If Spring Boot fails with:

```text
Web server failed to start. Port 8082 was already in use.
```

then an old backend process is still running. Stop it manually before starting a new backend.

### Stop The Old Backend Process

Run this in PowerShell:

```powershell
$portProcess = Get-NetTCPConnection -LocalPort 8082 -ErrorAction SilentlyContinue | Select-Object -First 1
if ($portProcess) {
    Stop-Process -Id $portProcess.OwningProcess -Force
}
```

### Start Backend Manually

Run this manually from the backend project directory:

```powershell
$env:SPRING_DATASOURCE_PASSWORD="<your-local-postgres-password>"
mvn spring-boot:run
```

Do not start backend servers in hidden background processes. Avoid:

```text
start /min
start /b
Start-Process
java -jar target/*.jar
mvn spring-boot:run
```

inside automation scripts unless you are intentionally managing that process and know how to stop it.

## Database Safety

Local development should use:

```properties
spring.jpa.hibernate.ddl-auto=update
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
```

Production should use:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Never use `ddl-auto=create` or `ddl-auto=create-drop` with real data.
