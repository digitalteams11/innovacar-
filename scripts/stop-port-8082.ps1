$connection = Get-NetTCPConnection -LocalPort 8082 -ErrorAction SilentlyContinue | Select-Object -First 1

if ($connection) {
    Write-Host "Stopping process on port 8082 PID:" $connection.OwningProcess
    Stop-Process -Id $connection.OwningProcess -Force
} else {
    Write-Host "No process found on port 8082"
}
