# 悬浮课表项目 — 代码优化变更说明

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