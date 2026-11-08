param([ValidateSet('api','worker','all')][string]$Role='all')
$ErrorActionPreference='Stop'
$taskProject=Split-Path -Parent $PSScriptRoot
$taskEnv=Join-Path $taskProject '.env'
if(-not(Test-Path -LiteralPath $taskEnv)){throw 'Run scripts/init-env.ps1 first.'}
foreach($taskLine in [IO.File]::ReadAllLines($taskEnv)){
    if($taskLine -match '^([A-Z_]+)=(.*)$'){
        [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')
    }
}
$env:APP_ROLE=$Role
Push-Location (Join-Path $taskProject 'server')
try { & mvn -B -ntp spring-boot:run; if($LASTEXITCODE -ne 0){throw 'Server exited with an error.'} }
finally { Pop-Location }
