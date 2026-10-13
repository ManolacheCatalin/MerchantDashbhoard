# Creates (or replaces) the demo API key for one merchant in the local "awin" database.
#
# - Generates a random 256-bit key (URL-safe Base64, no padding).
# - Stores ONLY its lower-case hex SHA-256 hash in merchant_api_keys, hashed exactly as the app does
#   (SHA-256 over the UTF-8 bytes). The plain key is never written to disk or the database.
# - Each merchant has one key: an existing key for the merchant is replaced and re-activated.
# - The plain key is returned as the script's output, once. It cannot be recovered from the hash;
#   if you lose it, run the script again to replace it.
#
# Run after the app has started at least once (Liquibase must have created the tables and demo merchants).
#
# Usage (from the project root):
#   $apiKey = .\scripts\provision-api-key.ps1 -MerchantId 1     # keep the key in $apiKey for requests
#   .\scripts\provision-api-key.ps1 -MerchantId 2               # just print it
#
# Password: taken from $env:PGPASSWORD if set; otherwise you are prompted (input is not echoed).

param(
    [Parameter(Mandatory = $true)]
    [long]$MerchantId,
    [string]$Database = 'awin',
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
if ($Database -cnotmatch '^[a-z_][a-z0-9_]*$') {
    throw "Invalid database name '$Database' (use lower-case letters, digits and underscores)."
}

$passwordWasSet = -not [string]::IsNullOrEmpty($env:PGPASSWORD)
if (-not $passwordWasSet) {
    $secure = Read-Host -Prompt "Password for PostgreSQL user '$Username'" -AsSecureString
    $env:PGPASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password
}

function Invoke-Psql([string]$Sql) {
    $output = & $PsqlPath -h $DbHost -p $Port -U $Username -d $Database -v ON_ERROR_STOP=1 -tA -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "psql failed: $output" }
    return ($output | Out-String).Trim()
}

try {
    $merchantName = Invoke-Psql "SELECT name FROM merchants WHERE id = $MerchantId;"
    if ([string]::IsNullOrEmpty($merchantName)) {
        throw "Merchant $MerchantId does not exist in database '$Database'. Start the app once so the demo data is loaded, or pick another id."
    }

    # 1. Random 256-bit key, URL-safe Base64 without padding
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $apiKey = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')

    # 2. Hash exactly as the app does: SHA-256 over the UTF-8 bytes, lower-case hex
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try { $digest = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($apiKey)) } finally { $sha256.Dispose() }
    $keyHash = -join ($digest | ForEach-Object { $_.ToString('x2') })

    # 3. Store only the hash (insert, or replace and re-activate the merchant's existing key)
    Invoke-Psql @"
INSERT INTO merchant_api_keys (merchant_id, key_hash) VALUES ($MerchantId, '$keyHash')
ON CONFLICT (merchant_id) DO UPDATE SET key_hash = EXCLUDED.key_hash, active = TRUE, created_at = now();
"@ | Out-Null

    Write-Host "API key stored for merchant $MerchantId ($merchantName) in database '$Database'. Keep it now; it is shown only once:"
    # 4. Return the plain key as the script's output
    $apiKey
}
finally {
    if (-not $passwordWasSet) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
}
