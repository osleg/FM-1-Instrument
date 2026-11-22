# SPDX-License-Identifier: GPL-3.0-only
param(
    [string]$CacheRoot = (Join-Path $PSScriptRoot '..\..\..\.cache'),
    [string]$ToolchainRoot = (Join-Path $PSScriptRoot '..\..\..\.toolchains'),
    [string]$NdkRoot = $env:ANDROID_NDK_HOME
)
$ErrorActionPreference = 'Stop'
$checkout = & (Join-Path $PSScriptRoot 'prepare-emulator.ps1') -CacheRoot $CacheRoot
$stagedRustup = Join-Path $ToolchainRoot 'rustup'
$rustBin = Join-Path $stagedRustup 'toolchains\1.93.1-x86_64-pc-windows-msvc\bin'
$cargo = Join-Path $rustBin 'cargo.exe'
$cargoArgs = @()
if (Test-Path -LiteralPath $cargo) {
    $env:RUSTUP_HOME = (Resolve-Path -LiteralPath $stagedRustup).Path
    $env:CARGO_HOME = Join-Path (Resolve-Path -LiteralPath $ToolchainRoot).Path 'cargo'
    $rustBin = (Resolve-Path -LiteralPath $rustBin).Path
    $env:PATH = "$rustBin;$env:PATH"
} else {
    $cargo = (Get-Command cargo.exe -ErrorAction Stop).Source
    $cargoArgs = @('+1.93.1')
}
if (-not $NdkRoot) {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
    if (-not $sdk) { throw 'Set ANDROID_HOME or pass -NdkRoot for Android NDK 27.2.12479018' }
    $NdkRoot = Join-Path $sdk 'ndk\27.2.12479018'
}
$llvm = Join-Path $NdkRoot 'toolchains\llvm\prebuilt\windows-x86_64\bin'
if (-not (Test-Path -LiteralPath $llvm)) { throw "Android NDK is missing: $llvm" }
$targets = @{
    'aarch64-linux-android' = 'aarch64-linux-android26-clang.cmd'
    'x86_64-linux-android' = 'x86_64-linux-android26-clang.cmd'
}
foreach ($target in $targets.Keys) {
    $linker = Join-Path $llvm $targets[$target]
    if (-not (Test-Path -LiteralPath $linker)) { throw "Android linker is missing: $linker" }
    $linkerKey = 'CARGO_TARGET_' + $target.ToUpperInvariant().Replace('-', '_') + '_LINKER'
    [Environment]::SetEnvironmentVariable($linkerKey, $linker, 'Process')
    & $cargo @cargoArgs rustc --locked --manifest-path (Join-Path $checkout 'Cargo.toml') -p fm1-web --lib --release --target $target -- --crate-type staticlib
    if ($LASTEXITCODE -ne 0) { throw "fm1-web build failed for $target" }
    $library = Join-Path $checkout "target\$target\release\libfm1_web.a"
    $built = Join-Path $checkout "target\$target\release\deps\libfm1_web.a"
    if (Test-Path -LiteralPath $built) { Copy-Item -LiteralPath $built -Destination $library -Force }
    if (-not (Test-Path -LiteralPath $library)) { throw "Expected library missing: $library" }
    Write-Output "$target $library"
}
