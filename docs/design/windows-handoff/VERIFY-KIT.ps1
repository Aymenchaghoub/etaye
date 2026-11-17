$ErrorActionPreference = 'Stop'
$kitRoot = [IO.Path]::GetFullPath($PSScriptRoot) + [IO.Path]::DirectorySeparatorChar
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'MANIFEST.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$verified = 0
foreach ($entry in $manifest.files.PSObject.Properties) {
    $path = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot $entry.Name))
    if (-not $path.StartsWith($kitRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw ('Unsafe manifest path: ' + $entry.Name)
    }
    $file = Get-Item -LiteralPath $path
    if ($file.PSIsContainer -or ($file.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw ('Expected an ordinary file: ' + $entry.Name)
    }
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($file.Length -ne $entry.Value.bytes -or $hash -ne $entry.Value.sha256) {
        throw ('Integrity mismatch: ' + $entry.Name)
    }
    $verified += 1
}
Write-Output ('Verified ' + $verified + ' payload files; states: ' + $manifest.states + '; packets: ' + $manifest.packets)
