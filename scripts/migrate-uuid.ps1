param(
    [Parameter(Mandatory=$true)][string]$SourceDatabaseUrl,
    [Parameter(Mandatory=$true)][string]$TargetDatabaseUrl,
    [string]$SourceDatabaseUser='family',
    [string]$TargetDatabaseUser='family'
)
$ErrorActionPreference='Stop'
if(-not $env:MIGRATION_SOURCE_DB_PASSWORD){throw 'Set MIGRATION_SOURCE_DB_PASSWORD in the local environment.'}
if(-not $env:DB_PASSWORD){throw 'Set DB_PASSWORD for the empty target database in the local environment.'}
$taskProject=Split-Path -Parent $PSScriptRoot
$taskJar=Join-Path $taskProject 'server\target\family-learning-server-0.5.0.jar'
if(-not(Test-Path -LiteralPath $taskJar)){throw 'Build the updated server JAR with Maven first.'}
$taskKeys=@('MIGRATION_SOURCE_DB_URL','MIGRATION_SOURCE_DB_USER','DB_URL','DB_USER','APP_ROLE','SPRING_MAIN_WEB_APPLICATION_TYPE','DB_MIGRATIONS_ENABLED')
$taskPrevious=@{}
foreach($taskKey in $taskKeys){$taskPrevious[$taskKey]=[Environment]::GetEnvironmentVariable($taskKey,'Process')}
try {
    $env:MIGRATION_SOURCE_DB_URL=$SourceDatabaseUrl
    $env:MIGRATION_SOURCE_DB_USER=$SourceDatabaseUser
    $env:DB_URL=$TargetDatabaseUrl
    $env:DB_USER=$TargetDatabaseUser
    $env:APP_ROLE='migrate'
    $env:SPRING_MAIN_WEB_APPLICATION_TYPE='none'
    $env:DB_MIGRATIONS_ENABLED='true'
    # Run from a fresh directory so a developer's local Profile cannot override migration endpoints.
    $taskWorking=Join-Path ([IO.Path]::GetTempPath()) ('family-uuid-'+[Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $taskWorking|Out-Null
    Push-Location $taskWorking
    try {
        & java '-Djava.awt.headless=true' -jar $taskJar --spring.profiles.active=uuid-migration --spring.config.location=classpath:/application.yml
        if($LASTEXITCODE -ne 0){throw 'UUID migration failed. The source was not modified; inspect the error before retrying.'}
    } finally {
        Pop-Location
        $taskResolved=[IO.Path]::GetFullPath($taskWorking)
        $taskTempRoot=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')+'\'
        if(-not $taskResolved.StartsWith($taskTempRoot,[StringComparison]::OrdinalIgnoreCase) -or (Split-Path $taskResolved -Leaf) -notmatch '^family-uuid-[0-9a-f]{32}$'){throw 'Refusing to remove an unexpected migration working directory.'}
        Remove-Item -LiteralPath $taskResolved -Recurse -Force
    }
} finally {
    foreach($taskKey in $taskKeys){[Environment]::SetEnvironmentVariable($taskKey,$taskPrevious[$taskKey],'Process')}
}
