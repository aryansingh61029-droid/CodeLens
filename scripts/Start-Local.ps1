param([int]$Port = 8082)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$envFile = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Copy .env.example to .env and configure your MySQL connection first.'
}
# Read values as data, never execute the contents of an environment file.
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^(DB_URL|DB_USERNAME|DB_PASSWORD)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$mysqlConfig = Join-Path $workspaceRoot 'work/mysql.ini'
$mysqlExe = Join-Path $workspaceRoot 'work/tools/mysql-8.4.11-winx64/bin/mysqld.exe'
if ($env:DB_URL -like 'jdbc:mysql://127.0.0.1:3306/codelens*' -and (Test-Path -LiteralPath $mysqlExe)) {
    $listener = Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue
    if (-not $listener) {
        Start-Process -FilePath $mysqlExe -ArgumentList ('--defaults-file="{0}"' -f $mysqlConfig) -WindowStyle Hidden
        Write-Host 'Starting local MySQL...'
    }
}
$javaExe = Join-Path $workspaceRoot 'work/tools/jdk-21.0.12.1+1/bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) { $javaExe = 'java' }
$jar = Join-Path $projectRoot 'target-mysql/app.jar'
if (-not (Test-Path -LiteralPath $jar)) { $jar = Join-Path $projectRoot 'target/app.jar' }
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the app first with mvn clean verify.' }
$env:PORT = "$Port"
$env:SPRING_PROFILES_ACTIVE = 'prod'
Write-Host "Open http://localhost:$Port/ once the server starts. Keep this window open."
# Use a real workspace path for Java's Windows selector sockets (avoids MSIX temp remapping).
$socketDir = Join-Path $workspaceRoot 'work/sockets'
New-Item -ItemType Directory -Path $socketDir -Force | Out-Null
& $javaExe "-Djdk.net.unixdomain.tmpdir=$socketDir" -jar $jar
