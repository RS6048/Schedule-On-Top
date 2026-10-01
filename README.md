# ScrollSched —— 桌面悬浮课表

一个纯本地运行的 Java Swing 桌面悬浮课表：课条常驻屏幕顶部，实时显示当前课程、倒计时、日期与值日生，并支持命令脚本、格式化文本、延迟课表、教师节祝福与**自动更新**（支持国内镜像）。

## 功能特性

- **悬浮课条**：吸顶半透明悬浮条，显示日期、课表、当前课倒计时（带主题色进度条）、值日生
- **课程追踪**：已上过的课灰色、未上过的课加粗；当前课显示全称 + 倒计时（精确到秒）
- **格式化文本**：Minecraft 风格格式码（`#cRRGGBB` 字体色 / `#gRRGGBB` 背景色 / `#i` 斜体 / `#b` 粗体 / `#u` 下划线 / `#d` 删除线 / `#r` 重置），重复格式码取消（toggle）
- **命令脚本**：`data/commands/load.txt`（每次 reload 加载）、`data/commands/tick.txt`（每秒执行）；支持变量、if/while、函数、键鼠控制、splashscreen 幕布、通知弹窗等
- **宏**：`$date$` `$time$`（精确到秒）`$hour$` `$minute$` `$second$` `$week$` `$course$` `$nextCourse$` `$monitor$` 等
- **隐藏字符**：`data/full_name.txt` 中用 `$---$` 分割线标记隐藏课程（上方隐藏 → course 类宏返回 null）
- **延迟课表**：`data/delay/` 下按日期放 `.txt` 文件（时间行 + `课表 : 值日生`）即可当天整体替换课表；周日课表也由当天 delay 文件承载；无文件时程序自动创建带 `AUTO` 标记的快照
- **晚自习**：`data/night_study.txt` 独立存放时间段，周一~周五且本地开启时自动并入；课表/换课窗口同时显示晚自习时间段与课程块（可参与换课）
- **双模式窗口**：【课表】点选两格换课（写入 `data/swap.txt` 数据文件，预览不落盘），周次直接显示当前周次，[上一周][下一周] 切换时下方日期联动；【设置】编辑模式为**纵向三区块表格**（周中 / 周六 / 晚自习）：时间单元格 `[开始~结束 − ＋]` 可点击编辑、删除时段、插入新时段（各天课程列同步增删），周六/晚自习表头 `[第N周 −][＋]` 可增删周次轮次行，课程块为下拉框（课间与隐藏字符可见可编辑），另有主题色、周次与时间偏移微调
- **换课数据文件**：`data/swap.txt`，两行一组（`yyyy@mm@dd@名`，名 = 课程全称），按名称定位课表 token（含课间）互换，追加记录即可换回
- **自动更新**：启动时后台自动检测新版本，**弹窗由用户手动确认**后才下载源码、重新编译、自动打包 `sc.jar` 并**自动删除 src/ 与 out/**，提示重启生效；支持国内镜像

## 运行

需要 JDK 17+（开发环境 JDK 21）。

双击 `run.bat` 即可（**优先从 `sc.jar` 启动**，其次编译产物；自动更新打包后会删除 src/out，jar 方式仍可正常启动）。也可手动：

```bat
javac -encoding UTF-8 -d out\production\ScrollSched src\*.java
java -Dfile.encoding=UTF-8 -cp out\production\ScrollSched Main
```

`BuildJar.bat` 仍可手动打包为 `sc.jar`。

## 数据文件（统一位于 `data/` 目录，与代码隔离）

| 文件 | 说明 |
|---|---|
| `data/schedule.txt` | 周一~周五课表（第 1 行时间成对 HH:mm，第 2-6 行课程） |
| `data/saturday.txt` | 周六课表（时间行 + 周次轮转行） |
| `data/self_study.txt` | 自习（第 0 行时间，轮次行前 6 个 token 为周一~周六自习科目） |
| `data/night_study.txt` | 晚自习时间段（周一~周五且本地开启时生效） |
| `data/monitor.txt` | 每周值日生 |
| `data/full_name.txt` | 课程简称→全称映射；`$---$` 上方为隐藏字符 |
| `data/.local` | 本地配置（周次/晚自习/愚人节/主题色/镜像域名） |
| `data/swap.txt` | 换课数据文件（两行一组，`yyyy@mm@dd@名`） |
| `data/delay/*.txt` | 延迟课表（时间行 + `课表 : 值日生`） |
| `data/commands/*.txt` | 命令脚本（load/tick/其他） |
| `data/version.txt` | 本地版本号（自动维护） |

## 自动更新与镜像

程序启动约 4 秒后静默检查更新（也可在【设置】→ 自动更新中手动检查）：

- 默认更新源：`https://raw.githubusercontent.com/RS6048/Schedule-On-Top/main`
- 直连失败时自动尝试 `ghproxy.net` / `mirror.ghproxy.com` 前缀
- **配置国内镜像（只写域名）**：在【设置】→ 自动更新 → 镜像输入框填入**域名**即可（仓库信息 `RS6048/Schedule-On-Top/main` 会在下载时自动追加）。示例：
  - ghproxy 加速：`ghproxy.net` → 实际拉取 `https://ghproxy.net/RS6048/Schedule-On-Top/main/...`
  - gitee 镜像：`gitee.com` → 实际拉取 `https://gitee.com/RS6048/Schedule-On-Top/main/...`
- **镜像值一旦改变即自动保存**（写入 `data/.local`），无需保存按钮；留空 = 默认 GitHub 直连
- 输入容错：自动剥离 `http(s)://` 前缀与尾部斜杠

有新版时程序先弹窗提示版本与说明，**用户点“是”确认后**才下载更新清单中的文件（源码与构建脚本）、用 javac 重新编译到 `out/production/ScrollSched`、自动打包 `sc.jar`，并**自动删除 `src/` 与 `out/`**，然后提示**重启生效**（下次更新会重新下载源码并重建编译目录）。

> 本仓库仅包含程序代码，不含个人课表数据（schedule.txt、full_name.txt 等不入库）。
