param([string]$DeviceSerial = '4c637b9e')
$ErrorActionPreference = 'Stop'
& adb -s $DeviceSerial reverse tcp:8765 tcp:8765
if ($LASTEXITCODE -ne 0) { throw 'ADB USB forwarding failed. Connect the tablet and enable USB debugging.' }
$listener = Get-NetTCPConnection -LocalPort 8765 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    $owner = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener[0].OwningProcess)"
    if ($owner.CommandLine -notlike '*pc_pad_bridge.py*') { throw 'Port 8765 is already used by another application.' }
    Write-Output 'Pad bridge is already running.'
} else {
    $pythonGui = (Get-Command pythonw.exe).Source
    $bridgeScript = Join-Path $PSScriptRoot 'pc_pad_bridge.py'
    $bridgeLog = Join-Path $PSScriptRoot 'pad_bridge.log'
    $relayArgs = @(('"{0}"' -f $bridgeScript), '--log', ('"{0}"' -f $bridgeLog))
    $relayProcess = Start-Process -FilePath $pythonGui -ArgumentList $relayArgs -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru
    $ready = $false
    for ($attempt = 0; $attempt -lt 15; $attempt++) {
        if (Get-NetTCPConnection -LocalPort 8765 -State Listen -ErrorAction SilentlyContinue) { $ready = $true; break }
        Start-Sleep -Milliseconds 200
    }
    if (-not $ready) { throw "PC relay did not start. See $bridgeLog" }
    Write-Output "Pad bridge started (PID $($relayProcess.Id))."
}
Write-Output 'In Dance Pad on the tablet, turn on PC bridge. Leave both USB cables connected.'
