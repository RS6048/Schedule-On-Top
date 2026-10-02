# Schedule-On-Top —— 桌面悬浮课表

一个纯本地运行的 Java Swing 桌面悬浮课表：课条常驻屏幕顶部，实时显示当前课程、倒计时、日期与值日生。支持命令脚本（SScript）、Minecraft 风格格式化文本、延迟课表、换课数据文件、教师节祝福与**自动更新**（支持国内镜像）。

## 功能特性

- **悬浮课条**：吸顶半透明悬浮条，显示日期、课表、当前课倒计时（主题色进度条）、值日生；可拖拽，单击隐藏并弹出通知，连续点击超过 3 次提示前往系统托盘恢复
- **课程追踪**：已上过的课显示灰色、尚未上过的课加粗；当前课显示**完整名称 + 倒计时**（精确到秒），课间/自习/晚自习时段同样计时
- **格式化文本**：Minecraft 风格格式码 —— `#cRRGGBB` 字体颜色、`#gRRGGBB` 背景色、`#i` 斜体、`#b` 粗体、`#u` 下划线、`#d` 删除线、`#r` 重置；**重复格式码取消**（toggle，符合 Minecraft）；解析时删除格式 token，不影响课程计数，只在最后渲染时应用
- **命令脚本（SScript）**：`data/commands/load.txt`（每次 reload 执行）、`data/commands/tick.txt`（每秒执行）；支持变量、四则运算、if/while、函数、课表操作、键鼠控制、管理员命令、幕布、通知、圆圈动画等，详见下文命令参考
- **宏**：`$date$`、`$time$`（精确到秒）、`$hour$`、`$minute$`、`$second$`、`$week$`、`$course$`（当前课全称）、`$nextCourse$`（下一节课）、`$monitor$`（值日生）
- **隐藏字符**：`data/full_name.txt` 中用 `$---$` 分割线标记隐藏课程（分割线上方隐藏）；课程类宏遇到隐藏字符返回 `null`
- **延迟课表**：`data/delay/` 下按日期放 `.txt`（时间行 + `课表 : 值日生`）即可当天整体替换课表；周日课表也由当天 delay 文件承载；无文件时程序自动创建带 `AUTO` 标记的快照；reload 时自动清理过期的 delay 文件
- **晚自习**：`data/night_study.txt` 独立存放时间段；周一~周五且本地开启时自动并入时间数组，课表末尾追加 `[~]` 标记；换课窗口同样显示晚自习时段与课程块
- **换课**：课表模式点选两格换课（写入 `data/swap.txt` 数据文件，预览不落盘）；周次直接显示当前周次，[上一周][下一周] 切换时下方日期联动
- **设置-编辑课表**：纵向三区块表格（周中 / 周六 / 晚自习）。时间单元格 `[开始~结束 − ＋]` 可点击编辑、删除时段、插入新时段（各天课程列同步增删）；周六/晚自习表头 `[第N周 −][＋]` 可增删周次轮次行；课程块为下拉框（课间与隐藏字符可见可编辑）；另有主题色、周次与时间偏移（"我的时间慢了 x 秒"，同步影响课表时间判断与 `$time$` 宏）微调
- **单测命令**：设置中可打开一个窗口输入临时命令直接运行（等同于普通命令，如 addblock/mouse 都会真实生效）
- **通知联动**：通知窗口（NoticeWindow）显示期间自动隐藏主课条，关闭后恢复；「请前往系统托盘」警告不联动课表
- **愚人节彩蛋**：4 月 1 日课间将课表**竖直镜像**显示
- **教师节祝福**：`data/commands/teachersDay.txt`（由 tick.txt 每 1s 调用），9 月 10 日上课时按当前科目显示「祝[姓]老师教师节快乐！」
- **自动更新**：启动约 4 秒后后台自动检测新版本，**弹窗由用户手动确认**后才下载源码、重新编译、自动打包 `sc.jar` 并**自动删除 src/ 与 out/**，提示重启生效；支持国内镜像

## 运行

需要 JDK 17+（开发环境 JDK 21）。

双击 `run.bat` 即可（**优先从 `sc.jar` 启动**；自动更新打包后会删除 src/out，jar 方式仍可正常启动）。也可手动：

```bat
javac -encoding UTF-8 -d out\production\ScrollSched src\*.java
java -Dfile.encoding=UTF-8 -cp out\production\ScrollSched Main
```

`BuildJar.bat` 可手动打包为 `sc.jar`。

## 数据文件（统一位于 `data/` 目录，与代码隔离）

| 文件 | 说明 |
|---|---|
| `data/schedule.txt` | 周一~周五课表（第 1 行时间成对 HH:mm，第 2-6 行课程，`单-双` 表示单双周轮换） |
| `data/saturday.txt` | 周六课表（时间行 + 周次轮转行） |
| `data/self_study.txt` | 自习（第 1 行时间，第 2 行起为轮次行，前 6 个 token 为周一~周六自习科目，`\` 表示无课） |
| `data/night_study.txt` | 晚自习时间段（成对 HH:mm；周一~周五且本地开启时生效） |
| `data/monitor.txt` | 每周值日生（一行一人，按星期取） |
| `data/full_name.txt` | 课程简称→全称映射（`语=语文`）；`$---$` 分割线上方为隐藏字符（课程宏返回 null），下方正常显示 |
| `data/.local` | 本地配置：周次 / 周次更新标记 / 晚自习开关 / 愚人节 / 主题色 / 镜像地址（第 6 行） |
| `data/swap.txt` | 换课数据文件（两行一组，`yyyy@mm@dd@名`） |
| `data/delay/*.txt` | 延迟课表（时间行 + `课表 : 值日生`，可带 `--- load ---` / `--- tick ---` 脚本块） |
| `data/commands/*.txt` | 命令脚本（load.txt / tick.txt / 其他，`run` 调用） |
| `data/version.txt` | 本地版本号（自动维护） |
| `data/mirror.txt` | **已废弃**（旧版本遗留，镜像现存于 `data/.local` 第 6 行） |

> 仓库仅包含程序代码，不含个人课表数据（schedule.txt、full_name.txt 等不入库）。

## 延迟课表（data/delay/）

文件名以日期开头（`YYYY-MM-DD*.txt`），格式（类周日模板，两行 + 可选脚本块）：

```
17:00 18:00
课表 : 值日生
--- load ---
# 每次 reload() 执行（与 load.txt 合并，仅当天生效）
addblock 备注 今天考试
--- tick ---
# 每次更新循环执行（与 tick.txt 合并，仅当天生效）
```

- 第 1 行：时间段（空格分隔的 HH:mm 成对，可含自习/晚自习）
- 第 2 行：`课表 : 值日生`（冒号分隔，两侧各自 trim）
- `--- load ---` / `--- tick ---` 块：仅当天生效；块内可写 SScript 代码，或 `run 文件名` 引用 commands 目录下的文件
- **自动快照**：今天没有手动创建的 delay 文件时，程序自动创建含 `AUTO` 标识行的 `_auto.txt` 快照（reload 时覆盖 AUTO、保留手动文件；tick 时不写盘）
- **自动清理**：reload 时删除文件名日期早于今天的 delay 文件（含手动与 AUTO）

## 换课数据文件（data/swap.txt）

两行一组构成一次换课，每行格式 `yyyy@mm@dd@名`：

```
# 9月30日第2节（数学）与 10月1日第3节（语文）互换
2026@09@30@数学
2026@10@01@语文
```

- `名` 为课程**全称**（如 `语文`/`数学`/`恰饭`）或**简称**（如 `语`/`数`）
- 按课表 token 定位（课间 `|`、空位 `\`、晚自习 `~` 等全部 token 均计入位置）
- 在 reload() 时于延迟课表之后、load.txt 之前应用；追加新的一组即可再次换课/换回

## 命令脚本（SScript）

脚本文件位于 `data/commands/`，后缀 `.txt`，UTF-8 编码。仅 `load.txt`（每次 reload）与 `tick.txt`（每秒）自动执行，其余文件通过 `run <文件名>` 显式调用。`#` 开头为注释。命令大小写不敏感，参数支持变量与宏展开。

### 变量与运算

| 命令 | 说明 |
|---|---|
| `set $var 值` | 赋值（值可为数字/文本/宏） |
| `add / sub / mul / div / mod $var 数` | 四则运算（结果写回 $var） |

### 流程控制

| 命令 | 说明 |
|---|---|
| `if 条件` ... `else` ... `endif` | 条件判断（如 `if %day% == 5`；比较符 `==`、`!=`、`<`、`>`、`<=`、`>=`） |
| `while 条件` ... `endwhile` | 循环 |
| `func 名` ... `endfunc` / `call 名` / `return` | 函数（支持递归，共享上下文） |
| `exit` | 立即结束当前脚本 |

### 课表与显示

| 命令 | 说明 |
|---|---|
| `getcourse index $var` | 取第 index 节课的科目存入变量 |
| `setcourse index name` | 设置第 index 个课表 token（index 从 0 起，**课间/空位/晚自习全部计入**；name 传全称自动反解析为简称） |
| `replace old new` | 将课表中所有 old 替换为 new |
| `addblock 名称 内容` | 追加一个显示块（内容支持内联变量与时间宏；`date`/`monitor` 为内置块不可添加） |
| `delblock 名称` | 删除显示块；`delblock date` / `delblock monitor` 隐藏内置日期/值日生块 |
| `removeblock 名称` | 同 delblock（兼容旧名） |
| `print 文本` | 输出到控制台 |

### 系统操作

| 命令 | 说明 |
|---|---|
| `cmd 命令` | 以当前权限异步执行一条 Windows 命令（`cmd notepad`、`cmd echo hi`） |
| `admcmd 命令` | **以管理员权限执行**（弹 UAC）：写入临时 .bat 后由 `powershell Start-Process -Verb RunAs` 提权运行，命令结束后窗口停留便于查看输出；仅适合偶发管理员命令 |
| `mouse x y` | 移动鼠标到屏幕坐标 |
| `click` / `clickright` | 左键 / 右键单击 |
| `key 键名` | 按下并释放按键（KeyEvent 常量名：ENTER / ESC / SPACE / TAB / F1~F24 / A~Z / 0~9 等） |
| `type 文本` | 逐字符键入（支持字母/数字/常用标点；中文无法键入） |
| `delay 毫秒` | 暂停两行命令之间的执行（幕布显示期间点击可取消） |
| `run 文件名` | 执行 commands/ 下另一个脚本文件（自动补 .txt 后缀，共享上下文） |
| `splashscreen on [文本]` | 显示半透明黑色幕布（可新增一行文本，多次 on 累积）；`splashscreen off` 关闭 |
| `shownotify 毫秒 文本` | 弹出顶部滑入式通知窗口（显示时长毫秒，支持内联变量） |
| `popcircle x y r 颜色` | 在屏幕坐标处显示圆圈扩散动画（颜色支持 `#FF0000` / `FF0000` / `red` 等） |

### 宏

| 宏 | 说明 |
|---|---|
| `$date$` | 日期 MM.dd（内联变量） |
| `$time$` / `$hour$` / `$minute$` / `$second$` | 当前时间（受"我的时间慢了 x 秒"偏移影响，精确到秒） |
| `$week$` | 当前周次 |
| `$course$` | 当前时段课程的**完整名称**（full_name.txt 映射，`\|`→恰饭、`~`→晚自习）；隐藏字符返回 `null`，时段外返回空串 |
| `$nextCourse$` | 下一时段课程完整名称（课间→下一节，上课中→下课后下一节）；隐藏字符返回 `null`，无后续返回空串 |
| `$monitor$` | 当前值日生 |
| `%date% %time% %day% %dayname% %month% %dayofmonth% %year% %hour% %minute% %second% %week%` | 时间宏（判断类，用于 if 条件；`%time%` 等始终为"现在"） |
| `$变量$` / `$变量` | 脚本变量（`set` 定义） |

### 格式化文本

Minecraft 风格，可出现在课表、值日生、addblock 内容等所有显示文本中（`#` 起始）：

| 格式码 | 作用 |
|---|---|
| `#cRRGGBB` | 字体颜色（16 进制，如 `#cFF0000`） |
| `#gRRGGBB` | 背景色（16 进制） |
| `#i` / `#b` / `#u` / `#d` | 斜体 / 粗体 / 下划线 / 删除线（重复出现取消，toggle） |
| `#r` | 重置全部格式 |

格式 token 在解析课表时删除（不参与课程计数），只在最终渲染时应用。

## 自动更新与镜像

启动约 4 秒后静默检测更新（也可在【设置】→ 自动更新中手动检查）：

- 默认更新源：`https://raw.githubusercontent.com/RS6048/Schedule-On-Top/main`
- 直连失败时自动尝试 `https://ghproxy.net/` / `https://mirror.ghproxy.com/` 前缀
- **配置国内镜像（保留 `https://` 头）**：在【设置】→ 自动更新 → 镜像输入框填入**完整地址前缀**（如 `https://ghproxy.net/`），仓库信息 `RS6048/Schedule-On-Top/main` 会在下载时自动追加；留空 = 默认 GitHub 直连
- **镜像值一旦改变即自动保存**（写入 `data/.local` 第 6 行），无需保存按钮

有新版时程序先弹窗提示版本与说明（更新内容置于滚动区域内），**用户点"是"确认后**才下载更新清单中的文件（源码与构建脚本）、用 javac 重新编译到 `out/production/ScrollSched`、自动打包 `sc.jar`，并**自动删除 `src/` 与 `out/`**，然后提示**重启生效**（下次更新会重新下载源码并重建编译目录）。
