param([Parameter(Mandatory=$true)][string]$LoginName)
$ErrorActionPreference='Stop'
$taskProject=Split-Path -Parent $PSScriptRoot
$taskEnv=Join-Path $taskProject '.env'
if(-not(Test-Path -LiteralPath $taskEnv)){throw 'Configure .env first.'}
foreach($taskLine in [IO.File]::ReadAllLines($taskEnv)){
    if($taskLine -match '^([A-Z_]+)=(.*)$'){[Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')}
}
$taskSecure=Read-Host 'New password (at least 8 characters)' -AsSecureString
$taskPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecure)
try{
    $env:ADMIN_RESET_USER=$LoginName
    $env:ADMIN_RESET_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer)
    $env:APP_ROLE='admin'
    $env:SPRING_MAIN_WEB_APPLICATION_TYPE='none'
    Push-Location (Join-Path $taskProject 'server')
    try { & java -jar target/family-learning-server-0.5.0.jar; if($LASTEXITCODE -ne 0){throw 'Password reset failed.'} }
    finally {Pop-Location}
}finally{
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer)
    $env:ADMIN_RESET_PASSWORD=$null
    $env:ADMIN_RESET_USER=$null
    $env:APP_ROLE=$null
    $env:SPRING_MAIN_WEB_APPLICATION_TYPE=$null
}
