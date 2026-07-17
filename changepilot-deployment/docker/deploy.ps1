param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'down', 'help')]
    [string]$Command,

    [switch]$Build,

    [Alias('h')]
    [switch]$Help
)

function Show-Usage {
    @'
Usage:
  ./deploy.ps1 up
  ./deploy.ps1 up -Build
  ./deploy.ps1 down
  ./deploy.ps1 -Help

Commands:
  up      Start the local layered Compose stack in detached mode.
  down    Stop and remove the local layered Compose stack.
'@
}

if ($Help -or -not $Command -or $Command -eq 'help') {
    Show-Usage
    exit 0
}

if ($Build -and $Command -ne 'up') {
    Write-Error '--build/-Build can only be used with the up command.'
    exit 1
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error 'docker is not installed or not on PATH.'
    exit 1
}

& docker compose version *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Error 'modern docker compose is required.'
    exit $LASTEXITCODE
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ComposeDir = Join-Path $ScriptDir 'compose'
$EnvFile = Join-Path $ComposeDir '.env'

$ComposeArgs = @(
    '-f', (Join-Path $ComposeDir 'docker-compose.base.yaml'),
    '-f', (Join-Path $ComposeDir 'docker-compose.local.yaml')
)

if (Test-Path -LiteralPath $EnvFile) {
    $ComposeArgs += @('--env-file', $EnvFile)
}

switch ($Command) {
    'up' {
        $DockerArgs = @('compose') + $ComposeArgs + @('up', '-d')
        if ($Build) {
            $DockerArgs += '--build'
        }
        & docker @DockerArgs
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
    'down' {
        $DockerArgs = @('compose') + $ComposeArgs + @('down')
        & docker @DockerArgs
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
}
