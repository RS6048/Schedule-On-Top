@echo off
cd /d %~dp0
if not exist "out\production\ScrollSched\Main.class" (
    echo [错误] 未找到编译产物 out\production\ScrollSched\Main.class
    echo 请先手动编译，或在程序中触发一次自动更新后重试。
    pause
    exit /b 1
)
start "ScrollSched" java -Dfile.encoding=UTF-8 -cp "out\production\ScrollSched" Main
