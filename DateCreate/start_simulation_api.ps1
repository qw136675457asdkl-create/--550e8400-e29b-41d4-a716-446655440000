$ErrorActionPreference = "Stop"

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$venvPython = Join-Path $projectDir ".venv\Scripts\python.exe"
$fallbackPython = "D:\python3.8.5\python.exe"
$apiScript = Join-Path $projectDir "simulation_api.py"

$pythonExe = if (Test-Path $venvPython) { $venvPython } else { $fallbackPython }

if (-not (Test-Path $pythonExe)) {
    throw "Python executable not found: $pythonExe"
}

if (-not (Test-Path $apiScript)) {
    throw "simulation_api.py not found: $apiScript"
}

Set-Location $projectDir
& $pythonExe $apiScript
