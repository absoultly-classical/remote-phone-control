# Android 项目重新构建脚本
# 用于当 Android Studio 无法正常同步/重构项目时，手动执行 Gradle 命令
# 使用方法: 在 PowerShell 中运行 .\rebuild.ps1 [选项]
# 选项:
#   clean    - 仅清理项目
#   sync     - 刷新依赖并同步
#   debug    - 构建 Debug APK
#   release  - 构建 Release APK
#   full     - 完整重构（clean + 构建）
#   无参数   - 默认执行完整重构

param(
    [Parameter(Position=0)]
    [ValidateSet("clean", "sync", "debug", "release", "full", "")]
    [string]$Action = "full"
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "===========================================" -ForegroundColor Cyan
    Write-Host " $Message" -ForegroundColor Cyan
    Write-Host "===========================================" -ForegroundColor Cyan
}

function Check-GradleWrapper {
    if (-not (Test-Path ".\gradlew.bat")) {
        Write-Host "错误: 未找到 gradlew.bat，请确保在 android-app 目录下运行此脚本。" -ForegroundColor Red
        exit 1
    }
}

function Run-Gradle {
    param([string[]]$Tasks)
    $taskString = $Tasks -join " "
    Write-Host "执行: .\gradlew $taskString" -ForegroundColor Yellow
    & .\gradlew $Tasks
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Gradle 任务失败，退出码: $LASTEXITCODE" -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

# 切换到脚本所在目录
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

Check-GradleWrapper

switch ($Action) {
    "clean" {
        Write-Step "正在清理项目..."
        Run-Gradle @("clean")
        Write-Host "清理完成！" -ForegroundColor Green
    }
    
    "sync" {
        Write-Step "正在刷新依赖并同步项目..."
        # 刷新依赖
        Run-Gradle @("--refresh-dependencies", "dependencies")
        Write-Host "依赖同步完成！" -ForegroundColor Green
    }
    
    "debug" {
        Write-Step "正在构建 Debug APK..."
        Run-Gradle @("assembleDebug")
        $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
        if (Test-Path $apkPath) {
            Write-Host "Debug APK 构建成功！" -ForegroundColor Green
            Write-Host "APK 路径: $apkPath" -ForegroundColor Yellow
        }
    }
    
    "release" {
        Write-Step "正在构建 Release APK..."
        Run-Gradle @("assembleRelease")
        $apkPath = "app\build\outputs\apk\release\app-release.apk"
        if (Test-Path $apkPath) {
            Write-Host "Release APK 构建成功！" -ForegroundColor Green
            Write-Host "APK 路径: $apkPath" -ForegroundColor Yellow
        } else {
            Write-Host "注意: 如果未配置签名，可能生成 app-release-unsigned.apk" -ForegroundColor Yellow
        }
    }
    
    "full" {
        Write-Step "步骤 1/4: 停止 Gradle Daemon..."
        & .\gradlew --stop 2>$null
        
        Write-Step "步骤 2/4: 清理构建缓存..."
        # 删除构建目录
        if (Test-Path ".\build") { Remove-Item -Recurse -Force ".\build" }
        if (Test-Path ".\app\build") { Remove-Item -Recurse -Force ".\app\build" }
        if (Test-Path ".\.gradle") { Remove-Item -Recurse -Force ".\.gradle" }
        Write-Host "已删除 build 和 .gradle 目录" -ForegroundColor Yellow
        
        Write-Step "步骤 3/4: 重新下载依赖..."
        Run-Gradle @("--refresh-dependencies", "clean")
        
        Write-Step "步骤 4/4: 构建 Debug APK..."
        Run-Gradle @("assembleDebug")
        
        $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
        Write-Host ""
        Write-Host "===========================================" -ForegroundColor Green
        Write-Host " 完整重构成功！" -ForegroundColor Green
        Write-Host "===========================================" -ForegroundColor Green
        if (Test-Path $apkPath) {
            Write-Host "APK 路径: $apkPath" -ForegroundColor Yellow
        }
    }
    
    default {
        Write-Step "正在执行完整重构..."
        # 默认等同于 full
        & $MyInvocation.MyCommand.Path -Action "full"
    }
}

Write-Host ""
Write-Host "脚本执行完毕。" -ForegroundColor Cyan
