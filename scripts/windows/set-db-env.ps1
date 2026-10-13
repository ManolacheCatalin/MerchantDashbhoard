# Sets the database credentials used by the app (database "awin") and the tests (database "awin_test")
# as environment variables for the CURRENT PowerShell session only. Nothing is written to disk.
#
# Usage (note the leading dot, so the variables stay in your session):
#   . .\scripts\windows\set-db-env.ps1
#   . .\scripts\windows\set-db-env.ps1 -Username other_user -TestUsername other_user
#
# Then run from the same session, e.g.:
#   .\gradlew.bat bootRun --args='--spring.profiles.active=dev'
#   .\gradlew.bat test

param(
    [string]$Username = 'postgres',
    [string]$TestUsername = $Username
)

if ($MyInvocation.InvocationName -ne '.') {
    Write-Warning "Run this script dot-sourced ('. .\scripts\windows\set-db-env.ps1'); otherwise the variables are lost when it exits."
}

function Read-Secret([string]$Prompt) {
    $secure = Read-Host -Prompt $Prompt -AsSecureString
    return [System.Net.NetworkCredential]::new('', $secure).Password
}

$env:DB_USERNAME = $Username
$env:DB_PASSWORD = Read-Secret "Password for '$Username' on database 'awin'"

$env:TEST_DB_USERNAME = $TestUsername
$env:TEST_DB_PASSWORD = Read-Secret "Password for '$TestUsername' on database 'awin_test' (Enter to reuse the previous one)"
if ([string]::IsNullOrEmpty($env:TEST_DB_PASSWORD)) {
    $env:TEST_DB_PASSWORD = $env:DB_PASSWORD
}

Write-Host "Set DB_USERNAME=$env:DB_USERNAME and TEST_DB_USERNAME=$env:TEST_DB_USERNAME (passwords set, not shown)."
