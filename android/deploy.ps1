<#
.SYNOPSIS
    Собирает APK PeerFlow и ставит его на подключённый телефон.

.DESCRIPTION
    Один шаг вместо трёх: сборка под нужный сервер, установка и запуск.

    Адрес сервера «вшивается» в APK на сборке (BuildConfig.BASE_URL), поэтому
    по умолчанию подставляется тестовый сервер. Без явного адреса debug-сборка
    смотрела бы на http://10.0.2.2:8080/ — это localhost компьютера с точки
    зрения эмулятора, и на реальном телефоне приложение не нашло бы сервер.

    Если телефон не подключён, скрипт всё равно собирает APK и показывает, где
    он лежит: файл можно перекинуть на устройство вручную.

.PARAMETER BaseUrl
    Адрес сервера. По умолчанию https://demiapp.ru:8443/.

.PARAMETER Serial
    Серийный номер устройства из `adb devices`. Нужен, только если подключено
    несколько телефонов.

.PARAMETER Release
    Собрать release вместо debug. Требует настроенной подписи (`signingConfig`
    в app/build.gradle.kts): без неё APK не установится.

.PARAMETER NoLaunch
    Не запускать приложение после установки.

.PARAMETER SkipTests
    Не прогонять модульные тесты перед сборкой.

.EXAMPLE
    .\deploy.ps1

.EXAMPLE
    .\deploy.ps1 -BaseUrl http://192.168.1.10:8080/

.EXAMPLE
    .\deploy.ps1 -Serial emulator-5554
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'https://demiapp.ru:8443/',
    [string]$Serial,
    [switch]$Release,
    [switch]$NoLaunch,
    [switch]$SkipTests
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Step([string]$Text) {
    Write-Host ''
    Write-Host "=== $Text ===" -ForegroundColor Cyan
}

function Write-Ok([string]$Text) {
    Write-Host $Text -ForegroundColor Green
}

function Write-Warn([string]$Text) {
    Write-Host $Text -ForegroundColor Yellow
}

$AndroidDir = $PSScriptRoot
$RepoDir = Split-Path -Parent $AndroidDir
$Gradlew = Join-Path $AndroidDir 'gradlew.bat'

if (-not (Test-Path $Gradlew)) {
    throw "Не найден gradlew.bat в $AndroidDir. Запускайте скрипт из каталога android/."
}

# Адрес без завершающего слэша дал бы «https://host:8443api/auth/login»:
# Retrofit склеивает baseUrl и путь напрямую.
if (-not $BaseUrl.EndsWith('/')) {
    $BaseUrl = "$BaseUrl/"
}

$BuildType = if ($Release) { 'release' } else { 'debug' }
$BaseUrlProperty = if ($Release) { 'peerflowReleaseBaseUrl' } else { 'peerflowDebugBaseUrl' }

# ---------------------------------------------------------------------------
# 1. Тесты
# ---------------------------------------------------------------------------
# Ставим на телефон только зелёную сборку: отлаживать на устройстве то, что
# уже упало в тестах, дороже, чем прогнать их сейчас.
if (-not $SkipTests) {
    Write-Step 'Модульные тесты'
    Push-Location $AndroidDir
    try {
        & $Gradlew ':app:testDebugUnitTest' '--console=plain'
        if ($LASTEXITCODE -ne 0) {
            throw "Тесты не прошли (код $LASTEXITCODE). Сборка остановлена: ключ -SkipTests, если нужен APK вопреки падению."
        }
    }
    finally {
        Pop-Location
    }
    Write-Ok 'Тесты зелёные.'
}

# ---------------------------------------------------------------------------
# 2. Сборка
# ---------------------------------------------------------------------------
Write-Step "Сборка $BuildType под $BaseUrl"
Push-Location $AndroidDir
try {
    & $Gradlew ":app:assemble$(if ($Release) { 'Release' } else { 'Debug' })" `
        "-P$BaseUrlProperty=$BaseUrl" '--console=plain'
    if ($LASTEXITCODE -ne 0) {
        throw "Сборка не удалась (код $LASTEXITCODE)."
    }
}
finally {
    Pop-Location
}

$Apk = Join-Path $AndroidDir "app\build\outputs\apk\$BuildType\app-$BuildType.apk"
if (-not (Test-Path $Apk)) {
    # Release без настроенной подписи кладёт рядом «unsigned»-файл: подскажем это,
    # а не просто «файл не найден».
    $Unsigned = Join-Path $AndroidDir "app\build\outputs\apk\$BuildType\app-$BuildType-unsigned.apk"
    if (Test-Path $Unsigned) {
        Write-Warn "APK собран, но не подписан: $Unsigned"
        Write-Warn 'Телефон такой файл не установит — нужен signingConfig в app/build.gradle.kts.'
        exit 1
    }
    throw "Сборка прошла, но APK не найден: $Apk"
}

$SizeMb = [math]::Round((Get-Item $Apk).Length / 1MB, 1)
Write-Ok "APK готов: $Apk ($SizeMb МБ)"

# ---------------------------------------------------------------------------
# 3. Поиск adb
# ---------------------------------------------------------------------------
function Resolve-Adb {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    # Android Studio прописывает SDK в local.properties — берём adb оттуда,
    # чтобы не требовать настроенного PATH.
    $localProperties = Join-Path $AndroidDir 'local.properties'
    if (Test-Path $localProperties) {
        $line = Get-Content $localProperties |
            Where-Object { $_ -match '^\s*sdk\.dir\s*=' } |
            Select-Object -First 1
        if ($line) {
            $sdkDir = ($line -split '=', 2)[1].Trim()
            # В local.properties двоеточия и обратные слэши экранированы.
            $sdkDir = $sdkDir -replace '\\:', ':' -replace '\\\\', '\'
            $candidate = Join-Path $sdkDir 'platform-tools\adb.exe'
            if (Test-Path $candidate) { return $candidate }
        }
    }
    return $null
}

# ---------------------------------------------------------------------------
# 4. Установка
# ---------------------------------------------------------------------------
$Adb = Resolve-Adb
if (-not $Adb) {
    Write-Warn 'adb не найден: ни в PATH, ни в SDK из local.properties.'
    Write-Warn 'APK собран — установите его вручную или поставьте Android Platform Tools.'
    exit 0
}

Write-Step 'Подключённые устройства'
# `adb devices` печатает заголовок, затем строки «серийник<TAB>состояние».
# Берём только state=device: unauthorized и offline ставить APK не дадут.
$DeviceLines = & $Adb devices |
    Select-Object -Skip 1 |
    Where-Object { $_ -match '\S' } |
    ForEach-Object { $_.Trim() -split '\s+' } |
    Where-Object { $_ }

$Devices = @()
for ($i = 0; $i -lt $DeviceLines.Count - 1; $i += 2) {
    if ($DeviceLines[$i + 1] -eq 'device') {
        $Devices += $DeviceLines[$i]
    }
    elseif ($DeviceLines[$i + 1] -in @('unauthorized', 'offline')) {
        Write-Warn "Устройство $($DeviceLines[$i]) в состоянии $($DeviceLines[$i + 1]): разрешите отладку на телефоне."
    }
}

if ($Devices.Count -eq 0) {
    Write-Warn 'Телефон не подключён. APK собран — установите его вручную:'
    Write-Warn "  $Apk"
    Write-Warn 'Включите «Отладку по USB», подключите кабель и запустите скрипт снова.'
    exit 0
}

Write-Ok "Найдено: $($Devices -join ', ')"

$Target = $Serial
if ($Target) {
    if ($Devices -notcontains $Target) {
        throw "Устройство '$Target' не найдено среди подключённых: $($Devices -join ', ')"
    }
}
elseif ($Devices.Count -gt 1) {
    # Молча ставить на первое попавшееся — верный способ обновить не тот
    # телефон (например, эмулятор вместо устройства).
    Write-Warn "Подключено несколько устройств: $($Devices -join ', ')"
    throw 'Укажите нужное: .\deploy.ps1 -Serial <серийный номер>'
}
else {
    $Target = $Devices[0]
}

Write-Step "Установка на $Target"
# -r обновляет приложение, сохраняя данные и вход; иначе Android потребовал бы
# сначала удалить предыдущую версию.
& $Adb -s $Target install -r $Apk
if ($LASTEXITCODE -ne 0) {
    throw "Установка не удалась (код $LASTEXITCODE)."
}
Write-Ok 'Установлено.'

if (-not $NoLaunch) {
    Write-Step 'Запуск'
    # Явный компонент, а не monkey: так видно ошибку, если activity переименуют.
    & $Adb -s $Target shell am start -n 'ru.peerflow.demo/.MainActivity'
    if ($LASTEXITCODE -ne 0) {
        Write-Warn 'Установлено, но запустить не удалось — откройте приложение вручную.'
    }
}

Write-Step 'Готово'
Write-Ok "Версия $BuildType на $Target смотрит на $BaseUrl"
