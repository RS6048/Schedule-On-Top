@echo off
cd /d %~dp0
rem 优先使用打包好的 sc.jar（自动更新打包后会删除 src/out）
if exist "sc.jar" (
    start "ScrollSched" java -Dfile.encoding=UTF-8 -jar "sc.jar"
    exit /b 0
)
rem 回退：编译产物启动（未打包或手动编译场景）
if exist "out\production\ScrollSched\Main.class" (
    start "ScrollSched" java -Dfile.encoding=UTF-8 -cp "out\production\ScrollSched" Main
    exit /b 0
)
echo [错误] 未找到 sc.jar 或编译产物 out\production\ScrollSched\Main.class
echo 请先手动编译，或在程序中触发一次自动更新后重试。
pause
exit /b 1
