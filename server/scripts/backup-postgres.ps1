param(
    [string]$Database = $env:POSTGRES_DB,
    [string]$HostName = $(if ($env:POSTGRES_HOST) { $env:POSTGRES_HOST } else { "localhost" }),
    [int]$Port = $(if ($env:POSTGRES_PORT) { [int]$env:POSTGRES_PORT } else { 5432 }),
    [string]$Username = $env:POSTGRES_USER,
    [string]$OutputDirectory = "storage/backups"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Database) -or [string]::IsNullOrWhiteSpace($Username)) {
    throw "Set POSTGRES_DB and POSTGRES_USER, or pass -Database and -Username."
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$output = Join-Path $OutputDirectory "$Database-$timestamp.dump"

& pg_dump -h $HostName -p $Port -U $Username -d $Database -Fc -f $output
if ($LASTEXITCODE -ne 0) {
    throw "pg_dump failed with exit code $LASTEXITCODE."
}

& pg_restore --list $output | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Backup verification failed for $output."
}

Write-Output "Verified backup: $output"
