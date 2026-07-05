# Compile tous les diagrammes Mermaid en PNG pour le rapport LaTeX
# Usage : .\compile-diagrams.ps1

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

$Config = Join-Path $ScriptDir "diagrams\mermaid-config.json"
$DiagramDir = Join-Path $ScriptDir "diagrams"

Write-Host "Compilation des diagrammes Mermaid..." -ForegroundColor Cyan

Get-ChildItem -Path $DiagramDir -Filter "*.mmd" | ForEach-Object {
    $input  = $_.FullName
    $output = $input -replace '\.mmd$', '.png'
    $name   = $_.BaseName
    Write-Host "  -> $name.png" -ForegroundColor Green
    npx --yes @mermaid-js/mermaid-cli `
        -c $Config `
        -p (Join-Path $DiagramDir "puppeteer-config.json") `
        -i $input `
        -o $output `
        -b white `
        -w 1400 `
        -s 2
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Echec compilation: $name"
        exit 1
    }
}

Write-Host "`nTermine. PNG generes dans diagrams/" -ForegroundColor Cyan
