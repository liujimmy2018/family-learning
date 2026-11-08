param(
    [string]$BaseUrl='http://127.0.0.1:8080',
    [Parameter(Mandatory=$true)][string]$LoginName
)
$ErrorActionPreference='Stop'
if(-not $env:SMOKE_PASSWORD){throw 'Set SMOKE_PASSWORD in the local environment for a dedicated test account.'}
$taskRoot=$BaseUrl.TrimEnd('/')+'/api/v1'
$script:taskSmokeToken=$null
function Invoke-TaskApi([string]$Method,[string]$Path,$Body=$null,[string]$RequestKey=''){
    $taskHeaders=@{}
    if($script:taskSmokeToken){$taskHeaders['Authorization']='Bearer '+$script:taskSmokeToken}
    if($RequestKey){$taskHeaders['Idempotency-Key']=$RequestKey}
    $taskArguments=@{Method=$Method;Uri=($taskRoot+$Path);Headers=$taskHeaders;TimeoutSec=30}
    if($null -ne $Body){$taskArguments.ContentType='application/json; charset=utf-8';$taskArguments.Body=[Text.Encoding]::UTF8.GetBytes(($Body|ConvertTo-Json -Depth 20 -Compress))}
    return (Invoke-RestMethod @taskArguments).data
}
function Assert-TaskValue([bool]$Condition,[string]$Message){if(-not $Condition){throw $Message}}
function Assert-TaskUnauthorized{
    try{Invoke-TaskApi 'GET' '/children'|Out-Null;throw 'Request unexpectedly succeeded'}
    catch{if($null -eq $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 401){throw}}
}
$taskChild=$null
try{
    Assert-TaskUnauthorized
    $taskAuth=Invoke-TaskApi 'POST' '/auth/login' @{loginName=$LoginName;password=$env:SMOKE_PASSWORD}
    $script:taskSmokeToken=$taskAuth.accessToken
    $taskChild=Invoke-TaskApi 'POST' '/children' @{nickname='SmokeChild';grade=3;textbook='试用验证';expectedVersion=0}
    $taskChildPath='/children/'+$taskChild.id
    $taskWords=@(@{text='apple';meaning='苹果'},@{text='book';meaning='书'})
    $taskRequestKey=[Guid]::NewGuid().ToString()
    $taskListBody=@{title='Smoke words';words=$taskWords;expectedVersion=0}
    $taskList=Invoke-TaskApi 'POST' ($taskChildPath+'/lists') $taskListBody $taskRequestKey
    $taskRepeated=Invoke-TaskApi 'POST' ($taskChildPath+'/lists') $taskListBody $taskRequestKey
    Assert-TaskValue ($taskRepeated.id -eq $taskList.id) 'Repeated list request created a duplicate'
    $taskSettings=@{mode='ENGLISH';countdownSeconds=0;intervalSeconds=3;repeats=2;repeatGapSeconds=0.5;rounds=2;randomOrder=$true}
    $taskPractice=Invoke-TaskApi 'POST' ($taskChildPath+'/practices') @{listId=$taskList.id;expectedListVersion=1;itemIds=@($taskList.words|ForEach-Object{$_.id});settings=$taskSettings} ([Guid]::NewGuid().ToString())
    $taskNewVersion=Invoke-TaskApi 'POST' ($taskChildPath+'/lists/'+$taskList.id+'/versions') @{title='Changed words';words=@(@{text='pear';meaning='梨'});expectedVersion=1} ([Guid]::NewGuid().ToString())
    $taskSnapshot=Invoke-TaskApi 'GET' ($taskChildPath+'/practices/'+$taskPractice.id)
    Assert-TaskValue ($taskSnapshot.words.Count -eq 2) 'Editing a list changed an existing practice snapshot'
    Assert-TaskValue (($taskSnapshot.words|Where-Object{$_.text -eq 'pear'}).Count -eq 0) 'Snapshot was not immutable'
    $taskEvent=@{eventId=[Guid]::NewGuid().ToString();action='PLAY';round=0;itemIndex=0;occurredAt=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()}
    $taskProgress=Invoke-TaskApi 'POST' ($taskChildPath+'/practices/'+$taskPractice.id+'/events') @{expectedVersion=1;status='PAUSED';round=0;itemIndex=0;events=@($taskEvent)}
    Assert-TaskValue ($taskProgress.status -eq 'PAUSED' -and $taskProgress.version -eq 2) 'Practice progress was not persisted'
    Invoke-TaskApi 'DELETE' $taskChildPath|Out-Null
    $taskChild=$null
    Invoke-TaskApi 'POST' '/auth/logout' @{}|Out-Null
    Assert-TaskUnauthorized
    Write-Output 'PASS: packaged API login, child profile, idempotent word list, immutable practice snapshot, progress and logout.'
}finally{
    if($taskChild -and $script:taskSmokeToken){try{Invoke-TaskApi 'DELETE' ('/children/'+$taskChild.id)|Out-Null}catch{}}
    $script:taskSmokeToken=$null
}
