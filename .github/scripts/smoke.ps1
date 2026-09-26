param(
    [Parameter(Mandatory = $true)][string]$Java,
    [Parameter(Mandatory = $true)][string]$WorkDir,
    [Parameter(Mandatory = $true)][string]$ServerJar,
    [Parameter(Mandatory = $true)][string]$PluginJar,
    [Parameter(Mandatory = $true)][string]$OutLog,
    [string[]]$Commands = @(),
    [string[]]$Assert = @(),
    [string[]]$Forbid = @(
        'Storage initialization failed',
        'Error occurred while enabling NeoBan',
        'Encountered an unexpected exception',
        'Exception in thread "main"',
        '(ERROR|SEVERE).*\[NeoBan\]'
    ),
    [int]$StartTimeoutSec = 180
)

$ErrorActionPreference = 'Stop'
$statusFile = "$OutLog.status"
function Status([string]$m) {
    $line = "{0} {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
    Write-Host $line
}

function Fatal([string]$m, [int]$code) {
    Status "FAIL: $m"
    exit $code
}

if (Test-Path $statusFile) { Remove-Item $statusFile -Force }
if (Test-Path $OutLog) { Remove-Item $OutLog -Force }

New-Item -ItemType Directory -Path (Join-Path $WorkDir 'plugins') -Force | Out-Null
Get-ChildItem (Join-Path $WorkDir 'plugins') -Filter 'NeoBan-*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item $ServerJar (Join-Path $WorkDir 'server.jar') -Force
Copy-Item $PluginJar (Join-Path $WorkDir ("plugins/" + (Split-Path $PluginJar -Leaf))) -Force
Set-Content -Path (Join-Path $WorkDir 'eula.txt') -Value 'eula=true' -Encoding Ascii
@(
    'online-mode=false',
    'level-type=FLAT',
    'view-distance=3',
    'generate-structures=false',
    'motd=neo-ban-ci'
) | Set-Content -Path (Join-Path $WorkDir 'server.properties') -Encoding Ascii

# Seed player IP history so the auto-IP-ban threshold can be exercised in one run.
$neobanDir = Join-Path $WorkDir 'plugins/NeoBan'
New-Item -ItemType Directory -Path $neobanDir -Force | Out-Null
$seed = New-Object System.Collections.Generic.List[string]
$seed.Add('players:')
for ($i = 1; $i -le 8; $i++) {
    $uuid = '11111111-1111-1111-1111-1111111111{0:D2}' -f $i
    $seed.Add("  ${uuid}:")
    $seed.Add("    name: Alt$i")
    $seed.Add('    ip: 198.51.100.77')
    $seed.Add('    last-seen: 1790380000000')
}
[System.IO.File]::WriteAllLines((Join-Path $neobanDir 'player-ips.yml'), $seed, (New-Object System.Text.UTF8Encoding($false)))

Add-Type -TypeDefinition @'
using System;
using System.Collections.Concurrent;
using System.Diagnostics;
public static class CiSmokeReader {
    public static readonly ConcurrentQueue<string> Queue = new ConcurrentQueue<string>();
    public static void Attach(Process p) {
        p.OutputDataReceived += (s, e) => { if (e.Data != null) Queue.Enqueue(e.Data); };
        p.ErrorDataReceived += (s, e) => { if (e.Data != null) Queue.Enqueue(e.Data); };
    }
    public static string[] Snapshot() { return Queue.ToArray(); }
}
'@

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $Java
$psi.Arguments = "-Xms512M -Xmx1024M -jar `"" + (Join-Path $WorkDir 'server.jar') + "`" nogui"
$psi.WorkingDirectory = $WorkDir
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.StandardOutputEncoding = [System.Text.Encoding]::Default

$p = New-Object System.Diagnostics.Process
$p.StartInfo = $psi
[CiSmokeReader]::Attach($p)

try {
    Status "starting $Java in $WorkDir"
    [void]$p.Start()
    Status "started pid=$($p.Id)"
    $p.BeginOutputReadLine()
    $p.BeginErrorReadLine()
} catch {
    Fatal "START FAILED: $($_.Exception.Message)" 2
}

$deadline = (Get-Date).AddSeconds($StartTimeoutSec)
$ready = $false
while ((Get-Date) -lt $deadline) {
    if ($p.HasExited) { Status "process exited early, code=$($p.ExitCode)"; break }
    $lines = [CiSmokeReader]::Snapshot()
    foreach ($l in $lines) {
        if ($l -match 'Done \(') { $ready = $true; break }
    }
    if ($ready) { break }
    Start-Sleep -Milliseconds 400
}
Status "ready=$ready"

if ($ready) {
    foreach ($cmd in $Commands) {
        if ($p.HasExited) { Status "exited before command: $cmd"; break }
        try {
            $p.StandardInput.WriteLine($cmd)
            Status "cmd> $cmd"
        } catch {
            Status "stdin write failed: $($_.Exception.Message)"
            break
        }
        Start-Sleep -Milliseconds 1200
    }
} else {
    Status 'not ready, skipping commands'
}

if (-not $p.HasExited) {
    Start-Sleep -Seconds 2
    try { $p.StandardInput.WriteLine('stop'); Status 'cmd> stop' } catch { Status "stop write failed: $($_.Exception.Message)" }
    if (-not $p.WaitForExit(60000)) {
        Status 'stop timeout, killing'
        try { $p.Kill() } catch {}
        [void]$p.WaitForExit(10000)
    }
}

Start-Sleep -Seconds 2
$all = [CiSmokeReader]::Snapshot()
[System.IO.File]::WriteAllLines($OutLog, $all, (New-Object System.Text.UTF8Encoding($false)))
Status "done, exited=$($p.HasExited) lines=$($all.Length)"

if (-not $ready) { Fatal 'server never became ready' 1 }

$log = $all -join "`n"
$failed = $false
foreach ($a in $Assert) {
    if ($log -notmatch $a) { Status "ASSERT MISSING: $a"; $failed = $true }
}
foreach ($f in $Forbid) {
    if ($log -match $f) { Status "FORBIDDEN PATTERN PRESENT: $f"; $failed = $true }
}
if ($failed) { exit 1 }
Status 'all assertions passed'
exit 0
