# Einmalige Vorbereitung für Releases: legt den Signaturschlüssel an und hinterlegt ihn als GitHub-Secret.
# Das Passwort wird hier eingegeben, nirgends gespeichert und nie als Befehlszeilenargument übergeben.
#
#   powershell -File tools\setup-signing.ps1
#
# Existiert der Schlüssel schon, wird er nicht überschrieben, sondern nur erneut hochgeladen.
param(
    [string]$Repo,
    [string]$Keystore = (Join-Path $HOME '.clipfang\clipfang-release.keystore')
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.Encoding]::ASCII

function Find-Keytool {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin\keytool.exe' }
    $candidates += 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
    $candidates += @(Get-ChildItem 'C:\Program Files\Android\openjdk' -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'bin\keytool.exe' })
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw 'keytool nicht gefunden. Bitte Android Studio oder ein JDK installieren.'
}

function Read-Password([string]$Prompt) {
    [System.Net.NetworkCredential]::new('', (Read-Host $Prompt -AsSecureString)).Password
}

function Set-RepoSecret([string]$Name, [string]$Value) {
    $Value | & gh secret set $Name --repo $Repo
    if ($LASTEXITCODE -ne 0) { throw "Das Secret $Name konnte nicht gesetzt werden." }
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'GitHub CLI (gh) fehlt: https://cli.github.com' }
if (-not $Repo) {
    Push-Location $PSScriptRoot
    try { $Repo = & gh repo view --json nameWithOwner --jq .nameWithOwner } finally { Pop-Location }
    if (-not $Repo) { throw 'Repo nicht erkannt. Bitte mit -Repo besitzer/name angeben.' }
}
$keytool = Find-Keytool
$exists = Test-Path $Keystore

Write-Host "Repo:      $Repo"
Write-Host "Schlüssel: $Keystore$(if ($exists) { ' (vorhanden, wird wiederverwendet)' })"
Write-Host ''

try {
    if ($exists) {
        $env:CLIPFANG_SETUP_PW = Read-Password 'Passwort des vorhandenen Schlüssels'
    } else {
        $first = Read-Password 'Neues Passwort (mindestens 12 Zeichen, ohne Umlaute)'
        if ($first -cnotmatch '^[\x21-\x7e]{12,}$') {
            throw 'Das Passwort braucht mindestens 12 Zeichen und darf keine Leerzeichen, Umlaute oder Emojis enthalten.'
        }
        if ($first -cne (Read-Password 'Passwort wiederholen')) { throw 'Die Passwörter stimmen nicht überein.' }
        $env:CLIPFANG_SETUP_PW = $first
        New-Item -ItemType Directory -Force (Split-Path $Keystore) | Out-Null
        & $keytool -genkeypair -keystore $Keystore -storetype PKCS12 -alias clipfang -keyalg RSA -keysize 4096 `
            -validity 10000 -dname 'CN=Clipfang' -storepass:env CLIPFANG_SETUP_PW
        if ($LASTEXITCODE -ne 0) { throw 'Der Schlüssel konnte nicht angelegt werden.' }
    }

    & $keytool -list -keystore $Keystore -alias clipfang -storepass:env CLIPFANG_SETUP_PW
    if ($LASTEXITCODE -ne 0) { throw 'Falsches Passwort oder kein Schlüssel namens "clipfang" in der Datei.' }

    Set-RepoSecret 'KEYSTORE_BASE64' ([Convert]::ToBase64String([System.IO.File]::ReadAllBytes($Keystore)))
    Set-RepoSecret 'KEYSTORE_PASSWORD' $env:CLIPFANG_SETUP_PW
} finally {
    Remove-Item Env:\CLIPFANG_SETUP_PW -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host 'Fertig. Jetzt unbedingt sichern:' -ForegroundColor Green
Write-Host "  1. die Datei $Keystore (z. B. auf einen USB-Stick)"
Write-Host '  2. das Passwort in deinem Passwortmanager'
Write-Host 'Ohne beides kannst du keine Updates mehr veröffentlichen. GitHub gibt Secrets nicht wieder heraus.'
