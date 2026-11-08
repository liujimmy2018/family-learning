param([string]$DatabaseUrl,[string]$DatabaseUser='family_test',[string]$LegacyDatabaseUrl,[string]$MigrationDatabaseUrl)
$ErrorActionPreference='Stop'
if($DatabaseUrl){$env:TEST_DB_URL=$DatabaseUrl;$env:TEST_DB_USER=$DatabaseUser}
if($LegacyDatabaseUrl){$env:TEST_LEGACY_DB_URL=$LegacyDatabaseUrl}
if($MigrationDatabaseUrl){$env:TEST_MIGRATION_DB_URL=$MigrationDatabaseUrl}
if([bool]$env:TEST_LEGACY_DB_URL -ne [bool]$env:TEST_MIGRATION_DB_URL){throw 'Set both TEST_LEGACY_DB_URL and TEST_MIGRATION_DB_URL for UUID copy tests.'}
if($env:TEST_DB_URL -and -not $env:TEST_DB_PASSWORD){throw 'Set TEST_DB_PASSWORD in the local environment before testing. Use a dedicated test database.'}
if($env:TEST_DB_PASSWORD){$env:DB_PASSWORD=$env:TEST_DB_PASSWORD}
$taskProject=Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $taskProject 'server')
try { & mvn -B -ntp test; if($LASTEXITCODE -ne 0){throw 'Server tests failed.'} }
finally { Pop-Location }
