$ErrorActionPreference = 'Stop'
$taskProject = Split-Path -Parent $PSScriptRoot
$taskEnvPath = Join-Path $taskProject '.env'
if (Test-Path -LiteralPath $taskEnvPath) { throw '.env already exists; it has not been overwritten.' }
function New-TaskSecret {
    $taskBytes = New-Object byte[] 24
    $taskRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $taskRandom.GetBytes($taskBytes) } finally { $taskRandom.Dispose() }
    return [Convert]::ToBase64String($taskBytes).Replace('+','-').Replace('/','_').TrimEnd('=')
}
$taskLines = @(
    ('DB_PASSWORD=' + (New-TaskSecret)),
    'DB_USER=family',
    'DB_URL=jdbc:mysql://127.0.0.1:3306/family_learning_uuid?useUnicode=true&characterEncoding=UTF-8&useSSL=false&useLegacyDatetimeCode=false&serverTimezone=UTC',
    ('INVITE_CODE=' + (New-TaskSecret)),
    'DASHSCOPE_API_KEY=',
    'AI_BASE_URL=https://dashscope.aliyuncs.com',
    'VISION_MODEL=qwen3-vl-plus',
    'SPEECH_MODEL=qwen3-tts-flash'
)
[IO.File]::WriteAllLines($taskEnvPath,$taskLines,(New-Object Text.UTF8Encoding($false)))
Write-Output 'Created .env with random database passwords and an invite code. Open it locally to configure AI and obtain your invite code.'
