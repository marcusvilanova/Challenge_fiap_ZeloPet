<#
.SYNOPSIS
    Gera o .zip de entrega da Sprint 4: LEIA-ME.txt (com os links) + o projeto, sem pastas pesadas ou segredos.
.EXAMPLE
    .\scripts\build-zip.ps1 -Video "https://youtu.be/XXXXXXXX" -Repo "https://github.com/grupo/zelo"
.NOTES
    Sem -Video / -Repo o LEIA-ME sai com um aviso no lugar dos links: preencha antes de enviar.
    O .zip é gravado na pasta acima do projeto (entrega-sprint4-zelo.zip), salvo se -Out for informado.
#>
param(
    [string]$Video = "",
    [string]$Repo = "",
    [string]$Out = ""
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path -Parent $PSScriptRoot
if (-not $Out) { $Out = Join-Path (Split-Path -Parent $root) "entrega-sprint4-zelo.zip" }
$projectName = Split-Path -Leaf $root

$excludedDirs = @(".git", ".venv", "venv", "target", "__pycache__", ".pytest_cache", ".idea", ".vscode", "node_modules")
$excludedPrefixes = @("backend/data/", "ia/vision/training/cache/")
$textExtensions = @(".md", ".py", ".java", ".yml", ".yaml", ".json", ".html", ".js", ".css", ".ps1", ".txt", ".xml", ".sql", ".csv", ".example")
$secretPattern = "sk-ant-[A-Za-z0-9_\-]{20,}"
$requiredFiles = @("README.md", "docs/sprint4-ia.md", "docs/arquitetura.md", "docs/validacao-e-testes.md",
    "docs/governanca-e-limitacoes.md", "docs/api.md", "docs/roteiro-video-pitch.md", "docs/checklist-entrega.md",
    "backend/pom.xml", "ia/vision/service/model/skin_classifier.onnx", ".env.example")

function Test-Excluded([string]$relative) {
    $unix = $relative.Replace("\", "/")
    foreach ($prefix in $excludedPrefixes) { if ($unix.StartsWith($prefix)) { return $true } }
    $name = $unix.Split("/")[-1]
    if ($name -eq ".env" -or ($name -like ".env.*" -and $name -ne ".env.example")) { return $true }
    return ($name -like "*.log" -or $name -like "*.class" -or $name -eq ".DS_Store" -or $name -eq "Thumbs.db")
}

function Get-ProjectFiles([string]$directory) {
    foreach ($item in Get-ChildItem -LiteralPath $directory -Force) {
        if ($item.PSIsContainer) {
            if ($excludedDirs -notcontains $item.Name) { Get-ProjectFiles $item.FullName }
        } else {
            $item
        }
    }
}

$files = @(Get-ProjectFiles $root | Where-Object {
        $relative = $_.FullName.Substring($root.Length + 1)
        -not (Test-Excluded $relative)
    })

# Nada de chave de API no pacote.
foreach ($file in $files) {
    if ($textExtensions -contains $file.Extension.ToLower() -and $file.Length -lt 2MB) {
        if ((Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8) -match $secretPattern) {
            throw "Possível chave de API em $($file.FullName). Remova antes de gerar o .zip."
        }
    }
}

$missing = @($requiredFiles | Where-Object { -not (Test-Path (Join-Path $root $_)) })
if ($missing.Count -gt 0) { throw "Arquivos obrigatórios ausentes: $($missing -join ', ')" }

$placeholder = "(PREENCHER ANTES DE ENVIAR)"
$videoLine = if ($Video) { $Video } else { $placeholder }
$repoLine = if ($Repo) { $Repo } else { $placeholder }
$readme = @"
ZELO - Sprint 4 | Disruptive Architectures: IoT, IoB & Generative IA
FIAP Challenge 2026 - Clyvo Vet

Video pitch (YouTube, nao listado): $videoLine
Repositorio (GitHub):               $repoLine

Integrantes
  Bruno Ferreira                       RM 563489
  Gabriel Robertoni Padilha            RM 566293
  Hebert Lopes do Santos               RM 563192
  Marcus Vinicius Vila Nova da Silva   RM 558771
  Nicolas Monteiro Ramiro              RM 562380

O que e: chatbot de triagem preliminar para pets (HTML + Java/Spring Boot + IA hibrida: regras de
seguranca + LLM opcional + classificador de imagens), com painel da clinica e revisao humana.

Conteudo deste .zip
  $projectName/README.md               uso, tecnologias, resultados parciais
  $projectName/docs/                   arquitetura, validacao, governanca, API, roteiro do video, evidencias (telas)
  $projectName/backend/                Java (Spring Boot) + front-end estatico
  $projectName/ia/                     catalogo de sintomas, pipeline do D2, treino e servico de visao (D3)
  $projectName/tests/, scripts/        testes Python, execucao local, teste de fumaca

Como rodar (detalhes no README): Windows + JDK 21+ + Python 3.10+
  1) python -m venv ia\.venv ; ia\.venv\Scripts\python -m pip install -r ia\requirements-service.txt
  2) .\scripts\run-local.ps1      -> http://localhost:8080  (contas de demonstracao no README)

Observacoes honestas: regras clinicas ainda nao revisadas por veterinario; IoT/IoB nao implementado nesta
sprint; o caminho com o LLM real nao foi executado contra a API (so servidor simulado). Ver docs/governanca-e-limitacoes.md.
"@

if (Test-Path -LiteralPath $Out) { Remove-Item -LiteralPath $Out -Force }
$zip = [System.IO.Compression.ZipFile]::Open($Out, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $entry = $zip.CreateEntry("LEIA-ME.txt", [System.IO.Compression.CompressionLevel]::Optimal)
    $writer = New-Object System.IO.StreamWriter($entry.Open(), (New-Object System.Text.UTF8Encoding($true)))
    $writer.Write($readme)
    $writer.Dispose()
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($root.Length + 1).Replace("\", "/")
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $file.FullName, "$projectName/$relative", [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    $zip.Dispose()
}

$size = [math]::Round((Get-Item -LiteralPath $Out).Length / 1MB, 1)
Write-Host "Gerado: $Out ($($files.Count + 1) arquivos, $size MB)"
if (-not $Video -or -not $Repo) {
    Write-Warning "Links do video e/ou do repositorio ficaram como '$placeholder' no LEIA-ME.txt. Gere de novo com -Video e -Repo."
}
