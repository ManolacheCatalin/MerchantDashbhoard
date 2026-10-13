# Creates the local PostgreSQL databases used by the project ("awin" for the app, "awin_test" for the tests).
# Safe to re-run: databases that already exist are left untouched. Tables are NOT created here;
# Liquibase creates the schema when the app or the tests start.
#
# Usage (from the project root):
#   .\scripts\windows\create-databases.ps1
#   .\scripts\windows\create-databases.ps1 -Username postgres -Port 5432
#   .\scripts\windows\create-databases.ps1 -Databases awin            # only one of them
#
# Password: taken from $env:PGPASSWORD if set; otherwise you are prompted (input is not echoed).

param(
    [string[]]$Databases = @('awin', 'awin_test'),
    [string]$Username = 'postgres',
    [string]$DbHost = 'localhost',
    [int]$Port = 5432,
    [string]$PsqlPath = 'C:\Program Files\PostgreSQL\17\bin\psql.exe'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $PsqlPath)) {
    $onPath = Get-Command psql -ErrorAction SilentlyContinue
    if ($onPath) { $PsqlPath = $onPath.Source }
    else { throw "psql not found at '$PsqlPath' or on PATH. Install PostgreSQL 17 (with Command Line Tools) or pass -PsqlPath." }
}

$passwordWasSet = -not [string]::IsNullOrEmpty($env:PGPASSWORD)
if (-not $passwordWasSet) {
    $secure = Read-Host -Prompt "Password for PostgreSQL user '$Username'" -AsSecureString
    $env:PGPASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password
}

function Invoke-Psql([string]$Sql) {
    # Connects to the built-in "postgres" maintenance database; CREATE DATABASE cannot target the database itself.
    $output = & $PsqlPath -h $DbHost -p $Port -U $Username -d postgres -v ON_ERROR_STOP=1 -tA -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "psql failed: $output" }
    return ($output | Out-String).Trim()
}

try {
    foreach ($name in $Databases) {
        # Names are interpolated into SQL, so allow only plain lower-case identifiers.
        if ($name -cnotmatch '^[a-z_][a-z0-9_]*$') {
            throw "Invalid database name '$name' (use lower-case letters, digits and underscores)."
        }
        if ((Invoke-Psql "SELECT 1 FROM pg_database WHERE datname = '$name';") -eq '1') {
            Write-Host "Database '$name' already exists - skipped."
        }
        else {
            Invoke-Psql "CREATE DATABASE $name;" | Out-Null
            Write-Host "Database '$name' created."
        }
    }
}
finally {
    if (-not $passwordWasSet) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
}
