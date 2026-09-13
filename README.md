# ScrollSched —— 桌面悬浮课表

一个纯本地运行的 Java Swing 桌面悬浮课表：课条常驻屏幕顶部，实时显示当前课程、倒计时、日期与值日生，并支持命令脚本、格式化文本、延迟课表、教师节祝福与**自动更新**（支持国内镜像）。

## 功能特性

- **悬浮课条**：吸顶半透明悬浮条，显示日期、课表、当前课倒计时（带主题色进度条）、值日生
- **课程追踪**：已上过的课灰色、未上过的课加粗；当前课显示全称 + 倒计时（精确到秒）
- **格式化文本**：Minecraft 风格格式码（`#cRRGGBB` 字体色 / `#gRRGGBB` 背景色 / `#i` 斜体 / `#b` 粗体 / `#u` 下划线 / `#d` 删除线 / `#r` 重置），重复格式码取消（toggle）
- **命令脚本**：`commands/load.txt`（每次 reload 加载）、`commands/tick.txt`（每秒执行）；支持变量、if/while、函数、键鼠控制、splashscreen 幕布、通知弹窗等
- **宏**：`$date$` `$time$`（精确到秒）`$hour$` `$minute$` `$second$` `$week$` `$course$` `$nextCourse$` `$monitor$` 等
- **隐藏字符**：`full_name.txt` 中用 `$---$` 分割线标记隐藏课程（上方隐藏 → course 类宏返回 null）
- **延迟课表**：`delay/` 下按日期放 `.delay` 文件（时间行 + `课表 : 值日生`）即可当天整体替换课表；周日课表也由当天 delay 文件承载
- **晚自习**：`night_study.txt` 独立存放时间段，周一~周五且本地开启时自动并入
- **双模式窗口**：【课表】点选两格换课（预览不落盘）；【设置】下拉框直改课表、时段/周六/晚自习编辑、主题色、周次与时间偏移微调
- **自动更新**：启动时后台检查新版本，自动下载源码并重新编译，提示重启生效；支持国内镜像

## 运行

需要 JDK 17+（开发环境 JDK 21）。

```bat
javac -encoding UTF-8 -d out\production\ScrollSched src\*.java
java -Dfile.encoding=UTF-8 -cp out\production\ScrollSched Main
```

也可用 `BuildJar.bat` 打包为 `sc.jar` 运行。

## 数据文件（位于程序运行目录）

| 文件 | 说明 |
|---|---|
| `schedule.txt` | 周一~周五课表（第 1 行时间成对 HH:mm，第 2-6 行课程） |
| `saturday.txt` | 周六课表（时间行 + 周次轮转行） |
| `self_study.txt` | 自习（第 0 行时间，轮次行前 6 个 token 为周一~周六自习科目） |
| `night_study.txt` | 晚自习时间段（周一~周五且本地开启时生效） |
| `monitor.txt` | 每周值日生 |
| `full_name.txt` | 课程简称→全称映射；`$---$` 上方为隐藏字符 |
| `.local` | 本地配置（周次/晚自习/愚人节/主题色） |
| `delay/*.delay` | 延迟课表（时间行 + `课表 : 值日生`） |
| `commands/*.txt` | 命令脚本（load/tick/其他） |
| `mirror.txt` | 自动更新镜像（可选） |
| `version.txt` | 本地版本号（自动维护） |

## 自动更新与镜像

程序启动约 4 秒后静默检查更新（也可在【设置】→ 自动更新中手动检查）：

- 默认更新源：`https://raw.githubusercontent.com/RS6048/Schedule-On-Top/main`
- 直连失败时自动尝试 `ghproxy.net` / `mirror.ghproxy.com` 前缀
- **配置国内镜像**：在【设置】→ 自动更新 → 更新源（镜像）输入框填入镜像 raw 根后点「保存镜像」。示例：
  - gitee 镜像：`https://gitee.com/你的用户名/Schedule-On-Top/raw/main`
  - ghproxy 加速：`https://ghproxy.net/https://raw.githubusercontent.com/RS6048/Schedule-On-Top/main`
- 留空保存即恢复默认直连

有新版时程序自动下载更新清单中的文件（源码与构建脚本）、用 javac 重新编译到 `out/production/ScrollSched`，然后弹窗提示**重启生效**。

> 本仓库仅包含程序代码，不含个人课表数据（schedule.txt、full_name.txt 等不入库）。
