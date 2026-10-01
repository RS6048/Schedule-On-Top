# 悬浮课表项目 — 代码优化变更说明

## v1.4.0 — 编辑课表界面重设计（内嵌编辑表格）

### 1. 设置-编辑模式改为纵向三区块
- 编辑模式右侧区域重构为三个纵向区块：**周中（周一~周五）/ 周六 / 晚自习**，各区块自带标题与表头，取代原先「仅周中 6 列 + 左侧弹窗」的布局
- 周中表头：`时间 | 周一 周二 周三 周四 周五`；每行时间单元格 = 一个时间段
- 周六 / 晚自习区块：行 = 时间段，列 = 周次轮次行；表头为 `时间 | [第1周 −] [第2周 −] … | [＋]`

### 2. 时间单元格可调 / 删除 / 插入（[开始~结束 − ＋]）
- **点击时间文本** → 弹出输入框修改开始/结束时间（HH:mm，校验格式），写回时间行
- **− 按钮** → 删除该时间段：时间行删一对时间点，**各课程行同步删除对应课程列**（含格式前缀保留）
- **＋ 按钮** → 在下方插入新时间段：预填「上一段结束 ~ 推 45 分钟」，确认后时间行插一对、**各课程行同步插入空位 `\`**
- 三区块共用同一套时间编辑逻辑（周中 schedule.txt / 周六 saturday.txt / 晚自习 self_study.txt）

### 3. 周次轮次行增删（周六 / 晚自习）
- 表头每个 `[第N周 −]`：删除该轮次行（至少保留一行）
- 表头末尾 `[＋]`：追加新的轮次行（周六行首为「六」，晚自习行全 `\` 占位，与文件模板一致）

### 4. 课间 / 隐藏字符可见可编辑
- 编辑模式下拉框现在包含**课间**（`" "`=课间）与 **$---$ 上方的隐藏课程**（如 `~`=晚自习），隐藏字符在课表中显示为下拉并可更换
- 移除编辑模式对 `~` token 的跳过逻辑（原为在弹窗编辑，现已并入网格）
- 编辑模式写回新增轮次区块位置格式 `sec@weekRow@timeIdx`（周六/晚自习下拉直接写入对应轮次行）

### 5. 移除旧弹窗入口
- 删除设置面板中「更改时段」「周六时段与轮次」「晚自习轮次」三个按钮及其弹窗（showTimeEditor / showSaturdayEditor / showNightEditor），其全部功能由编辑模式网格覆盖
- 清理随之无引用的 isValidTimeLine / alignTokens 死代码

---

## v1.3.0 — 设置滚动条 / 镜像域名化 / 晚自习换课显示 / 周次日期联动

### 1. 设置界面滚动条过长修复
- 设置面板内容高度压缩：移除「更新源（镜像）」提示行、删除「保存镜像」按钮，减小多处垂直留白（struts 10→6、12→8 等）
- 窗口默认高度 480 → 520，默认尺寸下设置面板内容完整放下，不再出现近似占满视口的难拖动滚动条（仍保留滚动兜底）

### 2. 镜像配置改为只写域名（保存到 locals）
- **镜像输入框只填域名**（如 `ghproxy.net`、`gitee.com`）——仓库信息 `RS6048/Schedule-On-Top/main` 在下载时由程序自动追加，无需再填完整 raw 根或代理拼接 URL
- **镜像值一旦改变即保存**：输入框挂 DocumentListener 即时写入 `data/.local` 第 6 行（扩展 .local 格式：前 5 行整数 + 第 6 行镜像域名文本），**删除「保存镜像」按钮**
- 留空 = 默认 GitHub raw 直连；默认地址失败时仍自动依次尝试常见加速前缀
- 容错输入：自动剥离 `http(s)://` 协议前缀与尾部斜杠；废弃 `data/mirror.txt`

### 3. 晚自习加入换课显示
- 课表模式下，周中时间列追加 `night_study.txt` 中的晚自习时间段（仅本地开启晚自习时，与主课条生效条件一致）
- 周一~五课程列末尾显示晚自习课程块（`~` token → 全称「晚自习」），**可参与换课定位**：预览/换课数据均含晚自习 token，与主课条行为一致
- 编辑模式仍不显示晚自习（在左侧弹窗中编辑），仅显示在课表/换课模式

### 4. 换课模式周次与日期联动
- **换课界面周次直接显示当前周次**：修复 `previewWeekTurn` 初始值 bug（原初始 1 导致打开即显示第 1 周，现在首次进入自动取真实周次）
- **左右切换周次时下方日期对应变化**：日期范围 = 当前自然周周一 + （查看周次 − 真实周次）周偏移，编辑/换课模式共用同一公式
- [上一周] [下一周] 按钮放置于同一行

---

## v1.2.0 — 换课回归数据文件 / setcourse 语义 / 打包清理 / 设置面板修复

### 1. 换课机制回归数据文件（data/swap.txt）
- **移除 `commands/swap.txt` 命令脚本及其支持**：`run swap.txt` 调用与 if+setcourse 模板删除，load.txt 恢复为纯命令入口
- 恢复独立换课文件 `data/swap.txt`：每行 `yyyy@mm@dd@名`，**两行一组**构成一次换课
- 应用时按名称（全称或简称）定位课表 token（**第一节课与课间 `|`、空位 `\` 等全部 token 均计入位置**）互换；仅目标日期当天生效；追加记录、再次写入当前名称可换回
- GUI"应用换课"按钮改为追加 data/swap.txt 记录（写全称，如 `语文`）；窗口预览路径同步应用换课数据文件

### 2. setcourse 语义调整
- **索引计入课间等全部 token**（与课表行 token 一一对应，不再按"第几节课"跳过特殊 token）
- **名称支持全称**：如 `setcourse 2 语文` 自动反向解析为简称 `语` 存储，
  倒计时显示正确的完整名称，同时保持单个 token 参与课程定位

### 3. 自动更新打包后清理
- 打包 sc.jar 成功后自动删除 `src/` 与 `out/`（源码/编译产物与配置隔离，
  重启由 sc.jar 启动）；`run.bat` 改为优先 `java -jar sc.jar`，其次编译产物
- 下次更新会重新下载源码并重建编译目录，流程自洽

### 4. 设置面板水平溢出修复（第二次）
- 设置面板子组件宽度改为 **preferred/maximum/minimum 三约束同步钳制**为视口可用宽度
  （上次仅设 maximum 无效——BoxLayout 按 preferred 宽度布局，仍会溢出居中裁切）
- 首次布局后立即校准一次（视口初次监听 resize 可能不触发），彻底消除水平溢出/偏移出框架

---

## v4 — MainFrame 重写 + 跨年修复 + 异常处理优化

### 1. MainFrame 完全重写
- **新布局**：左侧控制面板（周次切换/主题色/本地设置/应用按钮）+ 右侧滚动课表网格，使用标准 LayoutManager，彻底移除 null 布局和 paint() 中手动画文字。
- **周次切换**：新增「上一周/下一周」按钮，可查看并编辑任意周次课表；顶部显示当前周次和日期范围（MM.dd ~ MM.dd）。
- **本地设置面板**：晚自习开关、愚人节彩蛋开关，修改后即时保存到 .local 文件。
- **渲染优化**：UnitPane 改用 paintComponent 自定义绘制（圆角矩形+抗锯齿），不在绘制中修改组件属性；reload() 前彻底清除旧组件，避免重复堆叠。
- **换课 position 升级**：从 `month@day@index` 升级为 `year@month@day@index`，正确处理跨年换课。

### 2. 跨年换课修复（Main.doSwap）
- **换课记录格式升级**：`.swap` 文件从 `month@day@location=course` 升级为 `year@month@day@location=course`。
- **自动兼容旧格式**：getSwap() 加载时自动将旧格式（3段）升级为新格式（4段），用 inferSwapDate 推断年份后立即保存。
- **精确日期比较**：doSwap() 用 LocalDate.isEqual/isBefore 完整比较，不再只比同月 day，12.31→1月的过期换课能被正确清理。

### 3. 异常处理优化
- **outputException() 不再退出**：运行时异常（reload失败、脚本执行失败等）仅弹对话框+打印堆栈，程序继续运行。
- **新增 fatalError()**：仅用于 main() 初始化失败等不可恢复场景，才调用 System.exit(1)。
- **新增 logError()**：非关键操作失败仅记录到 stderr，不弹对话框。
- **reload() try-finally 保护**：主窗口 setVisible(false) 后即使加载失败，finally 中也会恢复可见性，避免主窗口永久隐藏。

---

## 一、漏洞修复

### 1. 资源泄漏（Critical）
- **问题**：`Main.java` 中静态 `Scanner s` 在 `getSwap()`、`getSchedule()`、`reload()`、`getLocals()` 等多处使用后从未关闭，导致文件句柄泄漏。
- **修复**：全部改为局部变量 + try-with-resources，移除静态 `Scanner s` 字段。

### 2. 空指针异常（High）
- **问题**：`Main.locals.get("HasNightStudy")`、`get("Theme")`、`get("AprilFool")` 等返回 `Integer`，直接拆箱为 `int` 比较，键不存在时抛 NPE。
- **修复**：新增 `Main.getLocal(key, defaultValue)` 安全读取方法，所有调用点替换。

### 3. 数组越界（High）
- **问题**：`cont[0].split(":")[1]` 未检查长度；`doSwap()` 中 `flag[2]` 未检查；`self_study` 取当天索引未检查数组长度。
- **修复**：全部添加长度检查与默认值。

### 4. EDT 线程阻塞（High）
- **问题**：`MainWindow.setVisible()`、`NoticeWindow.setVisible()`、`MainWindow.mouseReleased()` 中使用 `Thread.sleep()` + for 循环做动画，阻塞事件 dispatch 线程，导致界面卡死。
- **修复**：全部改为 `javax.swing.Timer` 驱动的非阻塞动画。

### 5. 愚人节彩蛋逻辑错误（Medium）
- **问题**：`updateTheme()` 中检查 `textContent[1].endsWith("4.01")`，但 `textContent[1]` 是课表字符串，永远不会以日期结尾；且日期格式为 `MM.dd`（如 `04.01`），不是 `4.01`。
- **修复**：改为检查 `textContent[0].endsWith("04.01")`。

### 6. 课程监控时间解析崩溃（Medium）
- **问题**：`main()` 的 Timer 中 `tc.load(scheds[i], scheds[i+1])` 未处理空字符串或格式异常的时间点，遇到 `replaceAll` 后产生的空串会抛 `DateTimeParseException`。
- **修复**：添加空值 / 空串 / 格式异常检查，异常条目跳过。

### 7. IniFileReader 越界（Medium）
- **问题**：遇到不含 `=` 的行时 `line.substring(0, -1)` 抛 `StringIndexOutOfBoundsException`；未使用的 `ZipInputStream` import；重复 `load()` 不清空旧数据。
- **修复**：添加无等号行跳过与警告；`load()` 前清空 map；移除无用 import。

### 8. MainFrame.reload 组件重复（Medium）
- **问题**：多次调用 `reload()` 不清空旧组件，导致课程单元格越堆越多。
- **修复**：`reload()` 开头移除所有旧组件并重置选中状态。

### 9. SimpleWindow.setShape 无效（Low）
- **问题**：重写 `setShape()` 时未调用 `super.setShape()`，平台级窗口塑形不生效，透明区域仍响应鼠标。
- **修复**：调用 `super.setShape()` 并 catch 不支持的平台异常，自绘作为视觉后备。

### 10. CircleAnimationWindow 面板未挂载（Low）
- **问题**：`initializeWindow()` 中创建了 `JPanel` 但被注释掉未 `setContentPane`，实际绘制走 `paint()` 而非 `paintComponent()`。
- **修复**：正确挂载内容面板，绘制逻辑移入 `paintComponent()`。

### 11. 编码问题
- **问题**：`Main.java`、`MainFrame.java`、`MainWindow.java` 为 GBK 编码，中文在 UTF-8 环境下显示为乱码。
- **修复**：全部转为 UTF-8 编码，文件读写显式指定 `StandardCharsets.UTF_8`。

---

## 二、代码简化

### 1. 消除重复拖拽逻辑
- `CommentWindow` 原自行实现了一套与 `SimpleWindow` 完全相同的拖拽 + 边缘吸附代码。
- 改为继承 `SimpleWindow`，复用 `setDraggable(true)`，新增 `cancelDrag()` 保护机制使关闭按钮区域不触发拖拽。

### 2. 移除测试代码
- `Main.main()` 中移除了硬编码的 `CommentWindow` 测试实例和被注释的 `NoticeWindow` 测试代码。
- `CircleAnimationWindow` 中移除了含 `Thread.sleep` 的 `main()` 测试方法。

### 3. 统一文件写入
- 新增 `Main.writeFile(File, String)` 工具方法，替代散落各处的 `try(FileWriter) { writer.write(...) }` 重复代码。

### 4. 简化换课解析
- `doSwap()` 中 `flag[0].replaceAll("\\D", "")` 多余（flag[0] 已是纯数字），直接 `parseInt`。
- 添加 `flag.length < 3` 和 `NumberFormatException` 防护。

### 5. 字段精简
- `MainWindow` 中 `StringBuilder sb`、`boolean cdAvailable` 从字段改为 `paint()` 局部变量。
- `Main` 中移除静态 `Scanner s`，新增 `tray` 字段持有托盘引用。

---

## 三、文档注释

所有 10 个类均补充了完整的 Javadoc：
- **类级注释**：说明职责、功能特性、使用场景。
- **方法级注释**：`@param`、`@return`、`@throws` 齐全，复杂逻辑附实现说明。
- **字段级注释**：关键常量和状态字段均有中文说明。
- **内部类**：`UnitPane`、`TimePane`、`TextContainer` 等均有注释。

---

## 四、GUI 界面改良

### MainWindow（主悬浮课条）
- 滑入 / 滑出动画从阻塞式 `Thread.sleep` 改为流畅的 `Timer` 动画。
- 吸附回顶部动画同理改为 `Timer`，过程中保留便签召唤逻辑。
- 当前课程高亮背景改为圆角矩形 + 进度条叠加，视觉更清晰。
- 连续点击保护提示文案修正为正确中文。

### MainFrame（换课编辑器）
- 从 `null` 布局 + 手动 `setBounds` 改为 `BorderLayout`：左侧控制面板 + 右侧 `JScrollPane` 课表区。
- 左侧面板含：应用按钮、主题色相滑块（带标签）、选中状态实时显示（HTML 格式化）、周次显示。
- 课程单元格 `UnitPane` 改为圆角矩形，选中时主题色填充 + 白色文字，未选中时浅灰边框。
- 移除 `paint()` 中手动绘制文字的做法，改用 `JLabel` 组件。
- `reload()` 自动清空旧组件，避免重复堆叠。

### NoticeWindow（通知窗口）
- 滑入 / 滑出改为非阻塞 `Timer` 动画。
- 背景改为半透明深色圆角矩形，顶部加主题色装饰线。
- 底部倒计时进度条使用主题色，文字支持裁剪滚动。
- 移除 `paint()` 中 `setLocation()` 的不良实践。

### CommentWindow（便签窗口）
- 继承 `SimpleWindow`，拖拽体验统一。
- 关闭按钮增加悬停高亮（红色圆角背景 + 白色 X）。
- 背景改为半透明圆角，顶部加装饰线，文字使用微软雅黑。
- 关闭按钮区域点击不触发拖拽。

### FrameTray（系统托盘）
- 添加 `SystemTray.isSupported()` 平台检测，不支持时友好提示。
- 右键菜单清理多余分隔符，使用 `addSeparator()` 替代 `new MenuItem("-")`。
- 托盘图标设置 `setImageAutoSize(true)`。

### CircleAnimationWindow
- 正确使用 `JPanel` 内容面板，`paintComponent` 中绘制。
- 透明度计算钳制在 0~255，避免异常。

---

## 五、兼容性

- 所有代码兼容 **Java 11**（已移除文本块 `"""` 和 switch 表达式，降级为传统字符串拼接和 switch 语句）。
- 外部依赖：仅 JDK 标准库（Swing / AWT / java.time），无第三方库。

---

## 七、新增功能（v2）

### 7.1 .sscm 脚本解释器
- 新增 `CommandInterpreter.java`（393行）：类 Minecraft 命令的行式脚本解释器
- 支持变量赋值、加减乘除、条件判断（if/endif，支持嵌套）
- 内置宏：`@time`（时间归一化 0~1）、`@hour`、`@minute`、`@day`、`@week`
- 课表操作：`replace <索引> <课程>` / `schedule set <索引> <课程>`
- 脚本位于 `./commands/*.sscm`，重载时按文件名顺序执行
- 脚本执行失败不崩溃，仅打印错误日志

### 7.2 .delay 延迟课表
- `./delay/*.delay` 文件，三行格式：日期 / 时间戳 / 课表内容
- 当天有匹配文件时，用文件内容完全替换正常课表
- 多文件匹配时取时间戳最新者
- 优先级：延迟课表 → 换课 → .sscm 脚本

### 7.3 示例文件
- `commands/01_afternoon_selfstudy.sscm` — 午休后换自习示例
- `commands/02_friday_classmeeting.sscm` — 周五班会示例
- `delay/2026-09-01_exam_review.delay` — 延迟课表示例
- `FEATURES.md` — 完整功能使用文档

---

## 八、文件清单（更新后）

| 文件 | 行数 | 主要改动 |
|------|------|----------|
| Main.java | 759 | +延迟课表加载、+脚本执行、资源管理、NPE 修复 |
| CommandInterpreter.java | 393 | **新增**：.sscm 脚本解释器 |
| MainWindow.java | 485 | 非阻塞动画、paint 修复、GUI 改良 |
| MainFrame.java | 520 | 布局重构、组件清除、GUI 改良 |
| NoticeWindow.java | 281 | Timer 动画、paint 修复、GUI 改良 |
| CommentWindow.java | 161 | 继承 SimpleWindow、GUI 改良 |
| SimpleWindow.java | 169 | setShape 修复、cancelDrag、注释 |
| FrameTray.java | 146 | 平台检测、菜单清理、注释 |
| CircleAnimationWindow.java | 149 | 面板修复、动画清理、注释 |
| IniFileReader.java | 93 | 越界修复、资源管理、注释 |
| TimeChecker.java | 91 | 边界防护、注释 |
| **合计** | **3247** | |
