# Sobe o serviço de visão (porta 8001) e o backend com o front-end (porta 8080).
# Uso:  .\scripts\run-local.ps1            (ctrl+c encerra os dois)
#       .\scripts\run-local.ps1 -SkipVision   (só o backend; o chat funciona, a análise de foto avisa que está indisponível)
param([switch]$SkipVision)

$ErrorActionPreference = "Stop"
# Dados fictícios de demonstração (desligados por padrão no backend). Só para rodar no seu computador.
if (-not $env:ZELO_SEED_DEMO) { $env:ZELO_SEED_DEMO = "true" }
$root = Split-Path -Parent $PSScriptRoot
$python = Join-Path $root "ia\.venv\Scripts\python.exe"
$vision = $null

if (-not $SkipVision) {
    if (-not (Test-Path $python)) {
        throw "Ambiente Python não encontrado. Rode: python -m venv ia\.venv ; ia\.venv\Scripts\python -m pip install -r ia\requirements-service.txt"
    }
    $vision = Start-Process -FilePath $python `
        -ArgumentList "-m", "uvicorn", "app:app", "--host", "127.0.0.1", "--port", "8001" `
        -WorkingDirectory (Join-Path $root "ia\vision\service") -PassThru -WindowStyle Hidden
    Write-Host "Serviço de visão iniciado (pid $($vision.Id)) em http://127.0.0.1:8001"
}

try {
    Set-Location (Join-Path $root "backend")
    Write-Host "Backend em http://localhost:8080  (Swagger: /swagger-ui.html)"
    & .\mvnw.cmd spring-boot:run
}
finally {
    if ($vision -and -not $vision.HasExited) {
        Stop-Process -Id $vision.Id -Force
        Write-Host "Serviço de visão encerrado."
    }
}
