# SPDX-License-Identifier: GPL-3.0-only
param([string]$CacheRoot = (Join-Path $PSScriptRoot '..\..\..\.cache'))
$ErrorActionPreference = 'Stop'
$source = 'https://github.com/osleg/fm1-emu.git'
$revision = '54ddec2056e028cc9061f981f5d9623065e83a23'
New-Item -ItemType Directory -Force -Path $CacheRoot | Out-Null
$checkout = Join-Path (Resolve-Path -LiteralPath $CacheRoot).Path 'fm1-emu-windjammer'
if (-not (Test-Path -LiteralPath (Join-Path $checkout '.git'))) {
    git clone --no-checkout $source $checkout
    if ($LASTEXITCODE -ne 0) { throw 'Could not clone the FM-1 Instrument emulator fork' }
}
git -C $checkout cat-file -e "$revision^{commit}" 2>$null
if ($LASTEXITCODE -ne 0) {
    git -C $checkout fetch $source $revision
    if ($LASTEXITCODE -ne 0) { throw 'Pinned fm1-emu revision is unavailable' }
}
git -C $checkout checkout --detach --quiet $revision | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not select the pinned fm1-emu revision' }
$status = git -C $checkout status --porcelain --untracked-files=no
if ($LASTEXITCODE -ne 0 -or $status) { throw "Tracked files in the pinned emulator checkout must be clean: $checkout" }
Write-Output $checkout
