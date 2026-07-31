param(
    [int]$IntervalSeconds = 2,
    [int]$Width = 320,
    [int]$Height = 180,
    [string]$FfmpegPath,
    [string]$FfprobePath
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$appRoot = Join-Path $repoRoot 'app'
$videoRoot = Join-Path $appRoot 'src\main\assets\fileVideoSample'
$outputRoot = Join-Path $appRoot 'src\main\assets\seekPreview'
$bundledFfmpeg = Join-Path $repoRoot 'tools\ffmpeg\ffmpeg-8.1.2-essentials_build\bin\ffmpeg.exe'
$bundledFfprobe = Join-Path $repoRoot 'tools\ffmpeg\ffmpeg-8.1.2-essentials_build\bin\ffprobe.exe'

if ($IntervalSeconds -lt 1) { throw 'IntervalSeconds must be at least 1.' }

function Resolve-VideoTool([string]$ConfiguredPath, [string]$BundledPath, [string]$CommandName) {
    if ($ConfiguredPath) {
        if (-not (Test-Path -LiteralPath $ConfiguredPath -PathType Leaf)) {
            throw "$CommandName was configured but not found: $ConfiguredPath"
        }
        return (Resolve-Path -LiteralPath $ConfiguredPath).Path
    }
    if (Test-Path -LiteralPath $BundledPath -PathType Leaf) {
        return (Resolve-Path -LiteralPath $BundledPath).Path
    }
    $command = Get-Command $CommandName -CommandType Application -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw "Seek previews require $CommandName. Install it on PATH, add the bundled tool at $BundledPath, or pass -$($CommandName.Substring(0,1).ToUpperInvariant() + $CommandName.Substring(1))Path."
}

$ffmpeg = Resolve-VideoTool $FfmpegPath $bundledFfmpeg 'ffmpeg'
$ffprobe = Resolve-VideoTool $FfprobePath $bundledFfprobe 'ffprobe'

$assetsRoot = [System.IO.Path]::GetFullPath((Join-Path $appRoot 'src\main\assets'))
$resolvedOutput = [System.IO.Path]::GetFullPath($outputRoot)
$assetsRootPrefix = $assetsRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
if (-not $resolvedOutput.StartsWith($assetsRootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Output must stay under shared app assets: $resolvedOutput"
}

function Assert-PathUnderOutput([string]$Path) {
    $candidate = [System.IO.Path]::GetFullPath($Path)
    $outputPrefix = $resolvedOutput.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $candidate.StartsWith($outputPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside the seek-preview output: $candidate"
    }
}

function Read-Index([string]$Path) {
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $values }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $parts = $_ -split '=', 2
        if ($parts.Count -eq 2) { $values[$parts[0]] = $parts[1] }
    }
    return $values
}

New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

$supportedExtensions = @('.mp4', '.m4v', '.webm', '.mkv', '.mov')
$videos = @(
    Get-ChildItem -LiteralPath $videoRoot -File -Recurse |
        Where-Object { $supportedExtensions -contains $_.Extension.ToLowerInvariant() } |
        Sort-Object FullName
)

$duplicateKeys = $videos |
    Group-Object { [System.IO.Path]::GetFileNameWithoutExtension($_.Name).ToLowerInvariant() } |
    Where-Object Count -gt 1
if ($duplicateKeys) {
    $details = $duplicateKeys | ForEach-Object { "'$($_.Name)': $($_.Group.FullName -join ', ')" }
    throw "Seek previews require unique video base names. Duplicates: $($details -join '; ')"
}

$activeKeys = @{}
foreach ($video in $videos) {
    $key = [System.IO.Path]::GetFileNameWithoutExtension($video.Name)
    $activeKeys[$key.ToLowerInvariant()] = $true
    $videoOutput = Join-Path $outputRoot $key
    $temporaryOutput = Join-Path $outputRoot "$key.tmp"
    Assert-PathUnderOutput $videoOutput
    Assert-PathUnderOutput $temporaryOutput

    $sourceSha256 = (Get-FileHash -LiteralPath $video.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $indexPath = Join-Path $videoOutput 'index.txt'
    $index = Read-Index $indexPath
    $existingFrames = if (Test-Path -LiteralPath $videoOutput -PathType Container) {
        @(Get-ChildItem -LiteralPath $videoOutput -File -Filter 'frame_*.jpg').Count
    } else { 0 }
    $isCurrent =
        $index['version'] -eq '1' -and
        $index['sourceSha256'] -eq $sourceSha256 -and
        $index['intervalMs'] -eq [string]($IntervalSeconds * 1000) -and
        $index['width'] -eq [string]$Width -and
        $index['height'] -eq [string]$Height -and
        $existingFrames -gt 0 -and
        $index['frameCount'] -eq [string]$existingFrames
    if ($isCurrent) {
        Write-Host "$($video.Name): unchanged ($existingFrames preview frames)"
        continue
    }

    if (Test-Path -LiteralPath $temporaryOutput) { Remove-Item -LiteralPath $temporaryOutput -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $temporaryOutput | Out-Null

    $filter = "fps=1/$IntervalSeconds,scale=${Width}:${Height}:force_original_aspect_ratio=decrease,pad=${Width}:${Height}:(ow-iw)/2:(oh-ih)/2:black"
    $framePattern = Join-Path $temporaryOutput 'frame_%05d.jpg'
    & $ffmpeg -hide_banner -loglevel error -y -i $video.FullName -vf $filter -q:v 5 -start_number 0 $framePattern
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed for $($video.Name)" }

    $durationSeconds = & $ffprobe -v error -show_entries format=duration -of 'default=noprint_wrappers=1:nokey=1' $video.FullName
    if ($LASTEXITCODE -ne 0) { throw "ffprobe failed for $($video.Name)" }
    $durationMs = [long]([double]::Parse($durationSeconds, [Globalization.CultureInfo]::InvariantCulture) * 1000.0)
    $frameCount = @(Get-ChildItem -LiteralPath $temporaryOutput -File -Filter 'frame_*.jpg').Count
    if ($frameCount -eq 0) { throw "ffmpeg generated no preview frames for $($video.Name)" }

    @(
        'version=1'
        "sourceSha256=$sourceSha256"
        "intervalMs=$($IntervalSeconds * 1000)"
        "durationMs=$durationMs"
        "frameCount=$frameCount"
        "width=$Width"
        "height=$Height"
    ) | Set-Content -LiteralPath (Join-Path $temporaryOutput 'index.txt') -Encoding ascii

    if (Test-Path -LiteralPath $videoOutput) { Remove-Item -LiteralPath $videoOutput -Recurse -Force }
    Move-Item -LiteralPath $temporaryOutput -Destination $videoOutput
    Write-Host "$($video.Name): $frameCount preview frames"
}

Get-ChildItem -LiteralPath $outputRoot -Directory | ForEach-Object {
    $directory = $_
    $isTemporary = $directory.Name.EndsWith('.tmp', [System.StringComparison]::OrdinalIgnoreCase)
    $key = if ($isTemporary) { $directory.Name.Substring(0, $directory.Name.Length - 4) } else { $directory.Name }
    if ($isTemporary -or -not $activeKeys.ContainsKey($key.ToLowerInvariant())) {
        Assert-PathUnderOutput $directory.FullName
        Remove-Item -LiteralPath $directory.FullName -Recurse -Force
        Write-Host "$($directory.Name): removed stale preview data"
    }
}
