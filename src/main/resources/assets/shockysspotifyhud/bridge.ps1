# Shocky's Spotify HUD - media bridge.
# Talks to the Windows media controls (the same thing the volume flyout uses).
# No Spotify API key or login is needed. Keep this file ASCII-only.
param(
    [int]$ParentPid = 0,
    [string]$CmdDir = ""
)

$ErrorActionPreference = 'SilentlyContinue'

# Debug log: %TEMP%\shockys_spotify_debug.log
$logFile = Join-Path $env:TEMP 'shockys_spotify_debug.log'
Set-Content -LiteralPath $logFile -Value ("Started " + (Get-Date) + " PS " + $PSVersionTable.PSVersion) -ErrorAction SilentlyContinue
$lastLog = ''
function Log([string]$m) {
    if ($m -eq $script:lastLog) { return }
    $script:lastLog = $m
    try { Add-Content -LiteralPath $logFile -Value ((Get-Date -Format 'HH:mm:ss') + ' ' + $m) } catch {}
}

Add-Type -AssemblyName System.Runtime.WindowsRuntime
$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$propsType   = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]

Add-Type -Namespace Shocky -Name Keys -MemberDefinition '[DllImport("user32.dll")] public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);'

$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, [Type]$type) {
    try {
        $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
        if ($task.Wait(5000)) { return $task.Result }
        Log 'Await timed out'
    } catch { Log ('Await failed: ' + $_) }
    return $null
}

function B64([string]$s) {
    if ($null -eq $s) { $s = '' }
    return [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($s))
}

function PressKey([byte]$vk, [int]$times) {
    for ($i = 0; $i -lt $times; $i++) {
        [Shocky.Keys]::keybd_event($vk, 0, 0, [UIntPtr]::Zero)
        [Shocky.Keys]::keybd_event($vk, 0, 2, [UIntPtr]::Zero)
    }
}

Log ('asTask found: ' + ($null -ne $asTask))
$manager = Await ($managerType::RequestAsync()) $managerType
Log ('manager created: ' + ($null -ne $manager))

function Get-Spotify {
    try {
        $ids = @()
        $found = $null
        foreach ($s in $manager.GetSessions()) {
            $ids += $s.SourceAppUserModelId
            if ($null -eq $found -and $s.SourceAppUserModelId -like '*Spotify*') { $found = $s }
        }
        Log ('sessions: ' + ($ids -join ', '))
        return $found
    } catch { Log ('GetSessions failed: ' + $_) }
    return $null
}

function Run-Command([string]$line) {
    $line = $line.Trim()
    $s = Get-Spotify
    switch -Regex ($line) {
        '^playpause$' { if ($s) { [void](Await ($s.TryTogglePlayPauseAsync()) ([bool])) } }
        '^next$'      { if ($s) { [void](Await ($s.TrySkipNextAsync()) ([bool])) } }
        '^prev$'      { if ($s) { [void](Await ($s.TrySkipPreviousAsync()) ([bool])) } }
        '^vol:up$'    { PressKey 175 5 }
        '^vol:down$'  { PressKey 174 5 }
        '^vol:mute$'  { PressKey 173 1 }
        '^search:([A-Za-z0-9%._~-]{1,300})$' { Start-Process ("spotify:search:" + $Matches[1]) }
    }
}

function Emit-State {
    $s = Get-Spotify
    if ($null -eq $s) { [Console]::Out.WriteLine('STATE|NONE'); return }
    try {
        $status = $s.GetPlaybackInfo().PlaybackStatus.ToString()
        $props = Await ($s.TryGetMediaPropertiesAsync()) $propsType
        $tl = $s.GetTimelineProperties()
        $dur = [int64]($tl.EndTime.TotalMilliseconds - $tl.StartTime.TotalMilliseconds)
        $pos = [int64]$tl.Position.TotalMilliseconds
        if ($status -eq 'Playing') {
            $age = [int64]([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalMilliseconds
            if ($age -gt 0 -and $age -lt 600000) { $pos += $age }
        }
        if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
        if ($pos -lt 0) { $pos = 0 }
        [Console]::Out.WriteLine("STATE|$status|$(B64 $props.Title)|$(B64 $props.Artist)|$pos|$dur")
    } catch {
        Log ('Emit-State failed: ' + $_)
        [Console]::Out.WriteLine('STATE|NONE')
    }
}

[Console]::Out.WriteLine('READY')

$tick = 0
while ($true) {
    if ($CmdDir -ne '' -and (Test-Path -LiteralPath $CmdDir)) {
        $files = Get-ChildItem -LiteralPath $CmdDir -Filter '*.cmd' | Sort-Object Name
        foreach ($f in $files) {
            try {
                $text = Get-Content -LiteralPath $f.FullName -Raw
                Remove-Item -LiteralPath $f.FullName -Force
                if ($text) { Run-Command $text }
            } catch {}
        }
    }
    if ($tick % 4 -eq 0) { Emit-State }
    if ($tick % 40 -eq 0 -and $ParentPid -gt 0) {
        if (-not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { break }
    }
    $tick++
    Start-Sleep -Milliseconds 125
}
