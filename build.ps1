# =====================================================================
#  Science Calculator - manual APK build script (no Gradle)
#  Chain: aapt2 compile -> aapt2 link -> javac -> d8 -> pack dex
#         -> zipalign -> keytool -> apksigner sign -> apksigner verify
#  NOTE: keep this file ASCII-only (PS 5.1 reads .ps1 as ANSI w/o BOM).
# =====================================================================
$ErrorActionPreference = "Continue"
# NOTE: PowerShell 5.1 turns native stderr output (keytool/apksigner notices)
# into NativeCommandError records; with "Stop" that aborts a healthy build.
# So use "Continue" and verify each native call via $LASTEXITCODE + throw.

$proj  = "D:\android_1"
$bt    = "C:\android-build\android-14"          # build-tools 34.0.0
$plat  = "C:\android-build\android-34\android.jar"
$pkg   = "com\scicalc\calculator"
$pkgDot= "com.scicalc.calculator"
$build = Join-Path $proj "build"
$ks    = Join-Path $proj "release.keystore"
$ver   = "1.1"
$apk   = Join-Path $build ("ScienceCalculator-v" + $ver + ".apk")

foreach ($tool in @(
    (Join-Path $bt "aapt2.exe"),
    (Join-Path $bt "d8.bat"),
    (Join-Path $bt "zipalign.exe"),
    (Join-Path $bt "apksigner.bat"),
    $plat)) {
    if (-not (Test-Path $tool)) { throw "missing tool: $tool" }
}

Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $build | Out-Null
New-Item -ItemType Directory -Force (Join-Path $build "classes") | Out-Null

# --------------------------------------------------------------- 1. aapt2 compile
Write-Host "== 1/9 aapt2 compile =="
& (Join-Path $bt "aapt2.exe") compile --dir (Join-Path $proj "res") -o (Join-Path $build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

# --------------------------------------------------------------- 2. aapt2 link
Write-Host "== 2/9 aapt2 link =="
& (Join-Path $bt "aapt2.exe") link `
    -o (Join-Path $build "base.apk") `
    -I $plat `
    --manifest (Join-Path $proj "AndroidManifest.xml") `
    --java (Join-Path $build "gen") `
    --min-sdk-version 21 `
    --target-sdk-version 34 `
    --auto-add-overlay `
    (Join-Path $build "res.zip")
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# --------------------------------------------------------------- 3. javac
Write-Host "== 3/9 javac (source/target 1.8) =="
$srcs = @(Get-ChildItem (Join-Path $proj "java\$pkg\*.java") | ForEach-Object { $_.FullName })
$rjava = Join-Path $build "gen\$pkg\R.java"
if (Test-Path $rjava) { $srcs += $rjava }
& javac -source 1.8 -target 1.8 -Xlint:-options -encoding UTF-8 `
    -classpath $plat -d (Join-Path $build "classes") @srcs
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# --------------------------------------------------------------- 4. d8
Write-Host "== 4/9 d8 -> classes.dex =="
$classes = @(Get-ChildItem (Join-Path $build "classes\$pkg\*.class") | ForEach-Object { $_.FullName })
if ($classes.Count -eq 0) { throw "no .class produced" }
& (Join-Path $bt "d8.bat") --release --lib $plat --min-api 21 --output $build @classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }
$dex = Join-Path $build "classes.dex"
if (-not (Test-Path $dex)) { throw "classes.dex not produced" }

# --------------------------------------------------------------- 5. pack dex into apk
Write-Host "== 5/9 pack classes.dex into base.apk =="
$py = "import zipfile,sys;z=zipfile.ZipFile(r'" + (Join-Path $build "base.apk") + "','a',zipfile.ZIP_DEFLATED);z.write(r'" + $dex + "','classes.dex');z.close();print('dex packed')"
& python -c $py
if ($LASTEXITCODE -ne 0) { throw "dex packing failed" }

# --------------------------------------------------------------- 6. zipalign
Write-Host "== 6/9 zipalign =="
& (Join-Path $bt "zipalign.exe") -f 4 (Join-Path $build "base.apk") (Join-Path $build "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# --------------------------------------------------------------- 7. keystore
Write-Host "== 7/9 keystore =="
if (-not (Test-Path $ks)) {
    & keytool -genkeypair -keystore $ks -alias scicalc `
        -keyalg RSA -keysize 2048 -validity 10950 `
        -storepass scicalc2026 -keypass scicalc2026 `
        -dname "CN=Science Calculator,O=Local,C=CN"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
} else {
    Write-Host "   keystore exists, reuse"
}

# --------------------------------------------------------------- 8. sign
Write-Host "== 8/9 apksigner sign =="
& (Join-Path $bt "apksigner.bat") sign `
    --ks $ks --ks-key-alias scicalc `
    --ks-pass pass:scicalc2026 --key-pass pass:scicalc2026 `
    --v1-signing-enabled true --v2-signing-enabled true `
    --out $apk (Join-Path $build "aligned.apk")
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

# --------------------------------------------------------------- 9. verify
Write-Host "== 9/9 apksigner verify =="
& (Join-Path $bt "apksigner.bat") verify --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }

Write-Host ""
Write-Host "BUILD-OK -> $apk"
Get-Item $apk | Select-Object FullName, Length, LastWriteTime | Format-List
