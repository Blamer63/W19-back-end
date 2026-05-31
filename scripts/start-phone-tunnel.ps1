param(
  [switch]$Build,
  [switch]$Stop
)

$ErrorActionPreference = "Stop"
if (Get-Variable PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
  $PSNativeCommandUseErrorActionPreference = $false
}

$tunnelName = "locale-cloudflare-tunnel"

function Stop-Tunnel {
  $existing = docker ps -a --filter "name=^/$tunnelName$" --format "{{.Names}}"
  if ($existing) {
    docker rm -f $tunnelName | Out-Null
    Write-Host "Stopped Cloudflare tunnel container: $tunnelName"
  } else {
    Write-Host "No Cloudflare tunnel container is running."
  }
}

if ($Stop) {
  Stop-Tunnel
  exit 0
}

docker version | Out-Null

if ($Build) {
  docker compose up -d --build
} else {
  docker compose up -d
}

$frontendContainer = docker compose ps -q frontend
if (-not $frontendContainer) {
  throw "Frontend container is not running. Try: docker compose up -d --build"
}

$network = docker inspect `
  --format "{{range `$name, `$settings := .NetworkSettings.Networks}}{{println `$name}}{{end}}" `
  $frontendContainer |
  Select-Object -First 1

if (-not $network) {
  throw "Could not detect the Docker network for the frontend container."
}

Stop-Tunnel

docker run -d `
  --name $tunnelName `
  --network $network `
  cloudflare/cloudflared:latest `
  tunnel --no-autoupdate --url http://frontend:80 |
  Out-Null

Write-Host "Starting Cloudflare Quick Tunnel..."
Start-Sleep -Seconds 5

$logs = cmd /c "docker logs $tunnelName 2>&1"
$match = [regex]::Match(($logs -join "`n"), "https://[a-zA-Z0-9-]+\.trycloudflare\.com")

if (-not $match.Success) {
  Write-Host "Tunnel started, but the public URL was not ready yet. Recent logs:"
  cmd /c "docker logs --tail 40 $tunnelName 2>&1"
  Write-Host ""
  Write-Host "Run this after a few seconds:"
  Write-Host "docker logs $tunnelName"
  exit 0
}

$url = $match.Value

try {
  Set-Clipboard -Value $url
  $clipboardMessage = "Copied to clipboard."
} catch {
  $clipboardMessage = "Copy the URL below."
}

Write-Host ""
Write-Host "Phone HTTPS URL:"
Write-Host $url
Write-Host $clipboardMessage
Write-Host ""
Write-Host "Keep Docker Desktop running while testing from your phone."
Write-Host "Stop the tunnel with:"
Write-Host ".\scripts\start-phone-tunnel.ps1 -Stop"
