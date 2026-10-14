# push-to-github.ps1
# Create the GitHub repository (only if missing) and push this project to it.
#
# Usage (token is NOT written into .git/config):
#     $env:GITHUB_TOKEN = 'ghp_xxxxxxxx'
#     .\tools\push-to-github.ps1 -Repo scicalc-android
#     .\tools\push-to-github.ps1 -Repo scicalc-android -Private
#
# The token needs the "repo" scope (classic PAT) or Contents+Administration
# read/write (fine-grained PAT). If GITHUB_TOKEN is absent the script prompts
# for it with a masked input box.
#
# NOTE: keep this file pure ASCII. PowerShell 5.1 decodes BOM-less .ps1 as ANSI.

param(
    [Parameter(Mandatory = $true)][string]$Repo,
    [string]$Owner = '',
    [switch]$Private,
    [string]$Branch = 'main'
)

$ErrorActionPreference = 'Continue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# ---------- 1. get the token ----------
if (-not $env:GITHUB_TOKEN) {
    $sec = Read-Host -Prompt 'GitHub Personal Access Token' -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
    $env:GITHUB_TOKEN = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}
if (-not $env:GITHUB_TOKEN) { throw 'No token provided.' }

$headers = @{
    Authorization          = "token $($env:GITHUB_TOKEN)"
    'User-Agent'           = 'push-to-github'
    Accept                 = 'application/vnd.github+json'
}

# ---------- 2. identify the account ----------
$me = (Invoke-RestMethod -Uri 'https://api.github.com/user' -Headers $headers -Method Get).login
if (-not $Owner) { $Owner = $me }
Write-Host "Account : $me"
Write-Host "Repo    : $Owner/$Repo"

# ---------- 3. create the repo if it does not exist ----------
$api = "https://api.github.com/repos/$Owner/$Repo"
$exists = $false
try {
    Invoke-RestMethod -Uri $api -Headers $headers -Method Get | Out-Null
    $exists = $true
} catch {
    $exists = $false
}

if ($exists) {
    Write-Host "Status  : repository already exists, will just push"
} else {
    $body = @{
        name        = $Repo
        private     = [bool]$Private
        description = 'Science calculator for Android, built with a hand-written toolchain (no Gradle).'
        has_issues  = $true
        has_wiki    = $false
    } | ConvertTo-Json
    Invoke-RestMethod -Uri 'https://api.github.com/user/repos' -Headers $headers `
        -Method Post -Body $body -ContentType 'application/json' | Out-Null
    Write-Host "Status  : repository created"
}

# ---------- 4. push with an ephemeral credential helper ----------
# The helper echoes the token to git on demand, so the secret never lands in
# .git/config and never appears in the remote URL.
$url = "https://github.com/$Owner/$Repo.git"
$hasOrigin = (git remote 2>$null) -contains 'origin'
if ($hasOrigin) { git remote set-url origin $url } else { git remote add origin $url }
Write-Host "Remote  : origin -> $url"

$helper = '!f() { echo username=x-access-token; echo password=$GITHUB_TOKEN; }; f'
git -c credential.helper= -c "credential.helper=$helper" push -u origin $Branch
if ($LASTEXITCODE -ne 0) { throw "git push failed (exit code $LASTEXITCODE)" }

Write-Host ''
Write-Host "DONE -> $url"
