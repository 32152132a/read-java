param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8080,
    [string]$EnvironmentFile = '',
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
if (-not $EnvironmentFile) {
    $EnvironmentFile = Join-Path $projectDirectory '.local\mysql.env'
}
if (-not (Test-Path -LiteralPath $EnvironmentFile)) {
    throw "Missing private MySQL configuration: $EnvironmentFile"
}

$settings = @{}
foreach ($entry in Get-Content -LiteralPath $EnvironmentFile) {
    $line = $entry.Trim()
    if (-not $line -or $line.StartsWith('#')) { continue }
    $separator = $line.IndexOf('=')
    if ($separator -le 0) { throw 'Invalid entry in private MySQL configuration.' }
    $settings[$line.Substring(0, $separator).Trim()] = $line.Substring($separator + 1)
}
foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_ACCESS_SECRET')) {
    if (-not $settings[$name]) { throw "Missing configuration key: $name" }
}
if ($settings['DB_URL'] -notmatch '^jdbc:mysql://(?:127\.0\.0\.1|localhost):3307/') {
    throw 'Local integration requires a MySQL SSH tunnel at 127.0.0.1:3307.'
}

Push-Location -LiteralPath $projectDirectory
try {
    # The wrapper reports the actual Java used by Maven, including JAVA_HOME.
    $mavenVersion = & .\mvnw.cmd -v 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Maven cannot start. Check JAVA_HOME and the installed JDK.' }
    $versionText = $mavenVersion -join "`n"
    if ($versionText -notmatch 'Java version: 21(?:\.|,)') {
        throw 'This project requires JDK 21. Set JAVA_HOME to JDK 21 in this terminal.'
    }
    Write-Host '[OK] Maven uses JDK 21. Node.js is not required by the Java backend.'

    $databaseClient = [Net.Sockets.TcpClient]::new()
    try {
        if (-not $databaseClient.ConnectAsync('127.0.0.1', 3307).Wait(2000)) {
            throw 'Connection timed out.'
        }
        $stream = $databaseClient.GetStream()
        $stream.ReadTimeout = 2000
        $greeting = [byte[]]::new(5)
        $received = 0
        while ($received -lt $greeting.Length) {
            $count = $stream.Read($greeting, $received, $greeting.Length - $received)
            if ($count -eq 0) { throw 'MySQL closed the connection before sending its greeting.' }
            $received += $count
        }
        if ($greeting[4] -ne 10) { throw 'The tunnel did not return a MySQL greeting.' }
    } catch {
        throw 'MySQL is unavailable through 127.0.0.1:3307. Establish the SSH tunnel first, then retry.'
    } finally {
        $databaseClient.Dispose()
    }
    Write-Host '[OK] MySQL responds through 127.0.0.1:3307.'

    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    if ($listeners | Where-Object { $_.Port -eq $Port }) {
        throw "Port $Port is already in use. Use the existing backend or select another -Port and match the frontend -ApiBaseUrl."
    }
    Write-Host "[OK] HTTP port $Port is available."
    if ($CheckOnly) { return }

    foreach ($name in $settings.Keys) { Set-Item -LiteralPath "Env:$name" -Value $settings[$name] }
    # Explicit local settings override inherited values from the old batch launcher.
    $env:SPRING_PROFILES_ACTIVE = 'mysql'
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SERVER_PORT = [string]$Port
    $mavenRepository = Join-Path $projectDirectory '.local\maven-repository'
    Write-Host "Starting backend: http://127.0.0.1:$Port"
    Write-Host "Frontend API: http://127.0.0.1:$Port/api/v1"
    & .\mvnw.cmd "-Dmaven.repo.local=$mavenRepository" spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE." }
} finally {
    Pop-Location
}
