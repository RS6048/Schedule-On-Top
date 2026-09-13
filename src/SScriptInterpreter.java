import java.awt.AWTException;
import java.awt.Color;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;

/**
 * 命令脚本解释器（Schedule Script）。
 *
 * <p>类 Minecraft 命令式行式脚本，支持：</p>
 * <ul>
 *   <li>变量（{@code $var}）与四则运算；</li>
 *   <li>条件判断 {@code if/else/endif}；</li>
 *   <li>循环 {@code while/endwhile}；</li>
 *   <li>函数 {@code func/endfunc} 与 {@code call}（支持递归）、{@code return}；</li>
 *   <li>时间宏 {@code %date% %time% %day% ...}；</li>
 *   <li>课表操作：{@code getcourse/setcourse/replace}；</li>
 *   <li>显示块操作：{@code addblock/removeblock}（addblock 支持 {@code $var$} 内联变量）；</li>
 *   <li>系统操作：{@code cmd}（执行 Windows 命令）、{@code mouse/click/clickright}（控制鼠标）、
 *       {@code key/type}（控制键盘）。</li>
 * </ul>
 *
 * <p>脚本文件放置于 {@code ./commands/} 目录，后缀 {@code .txt}。
 * 仅 {@code load.txt}（每次 reload）与 {@code tick.txt}（每次更新）自动执行，
 * 其他文件通过 {@code run <文件名>} 命令显式调用。</p>
 *
 * <h3>语法示例</h3>
 * <pre>{@code
 * # 将前3节都设为数学
 * set $i 0
 * while $i < 3
 *   setcourse $i 数学
 *   add $i 1
 * endwhile
 *
 * # 周五添加考试通知块（$date$ 内联变量）
 * if %day% == 5
 *   addblock 考试 下午期中考试 $date$
 * endif
 *
 * # 打开记事本并输入文字
 * cmd notepad
 * type hello world
 * }</pre>
 */
public class SScriptInterpreter {

    /**
     * 脚本执行上下文，持有课表数据与显示块。
     */
    public static class Context {
        /** 课程列表（按空格分割后的科目简称，可被脚本修改）。 */
        public List<String> courses;
        /** 显示块列表：[0]=日期, [1]=课表字符串, [2]=值日生, [3+]=额外块。 */
        public List<String> blocks;
        /** 脚本变量表。 */
        public final Map<String, String> vars = new HashMap<>();
        /** 当前日期。 */
        public LocalDate date;
        /** 当前时间。 */
        public LocalTime time;
        /** 当前周次。 */
        public int weekTurn;
        /** 时间段（HH:mm 成对 [s1,e1,s2,e2,...]，与 courses 一一对应），供 {@code $course$} 宏定位当前课程。 */
        public List<String> times = new ArrayList<>();
        /** 预览模式：系统命令（cmd/键鼠/delay/splashscreen）跳过，仅执行纯逻辑命令。 */
        public boolean dryRun = false;

        /**
         * 从 Main 的显示内容构建上下文。
         *
         * @param dateText 日期文本（如 "周一08.29"）
         * @param schedule 课表文本（空格分隔）
         * @param duty     值日生文本
         * @param date     当前日期
         * @param time     当前时间
         * @param weekTurn 当前周次
         */
        public static Context of(String dateText, String schedule, String duty,
                                 LocalDate date, LocalTime time, int weekTurn) {
            return of(dateText, schedule, duty, date, time, weekTurn, null);
        }

        /**
         * 从 Main 的显示内容构建上下文（带时间段，供 {@code $course$} 宏使用）。
         *
         * @param dateText  日期文本（如 "周一08.29"）
         * @param schedule  课表文本（空格分隔）
         * @param duty      值日生文本
         * @param date      当前日期
         * @param time      当前时间
         * @param weekTurn  当前周次
         * @param timeArr   时间段数组（HH:mm 成对），可为 null
         */
        public static Context of(String dateText, String schedule, String duty,
                                 LocalDate date, LocalTime time, int weekTurn, String[] timeArr) {
            Context ctx = new Context();
            // 解析课表时删除所有格式化 token（#+格式码），避免课程计数错误；
            // 显示字符串 blocks[1] 保留原始（含 token），由渲染层最后应用格式
            ctx.courses = new ArrayList<>(Arrays.asList(stripFormat(schedule).split(" ")));
            ctx.blocks = new ArrayList<>();
            ctx.blocks.add(dateText);
            ctx.blocks.add(schedule);
            ctx.blocks.add(duty);
            ctx.date = date;
            ctx.time = time;
            ctx.weekTurn = weekTurn;
            if (timeArr != null) {
                ctx.times = new ArrayList<>(Arrays.asList(timeArr));
            }
            return ctx;
        }

        /**
         * 将课程列表重新拼接为课表字符串，同步到 blocks[1]。
         */
        public void flushSchedule() {
            if (blocks.size() > 1) {
                blocks.set(1, String.join(" ", courses));
            }
        }

        /**
         * 浅拷贝上下文（tick 脚本每次更新独立执行，不污染 reload 的基础状态）。
         */
        public Context copy() {
            Context c = new Context();
            c.courses = new ArrayList<>(this.courses);
            c.blocks = new ArrayList<>(this.blocks);
            c.vars.putAll(this.vars);
            c.date = this.date;
            c.time = this.time;
            c.weekTurn = this.weekTurn;
            c.times = new ArrayList<>(this.times);
            c.dryRun = this.dryRun;
            return c;
        }
    }

    // ---- 执行状态 ----
    private List<String> lines;
    private int pc = 0;
    private Context ctx;
    /** 函数调用栈（保存返回行号）。 */
    private final Deque<Integer> callStack = new ArrayDeque<>();
    /** 函数名 → 函数体起始行号。 */
    private final Map<String, Integer> funcAddresses = new HashMap<>();
    /** while 循环栈（保存 while 起始行号，用于 endwhile 跳回）。 */
    private final Deque<Integer> whileStack = new ArrayDeque<>();

    /**
     * 执行一段脚本程序。
     *
     * @param program 脚本行列表
     * @param ctx     执行上下文
     */
    public void execute(List<String> program, Context ctx) {
        this.ctx = ctx;
        this.lines = new ArrayList<>(program);
        this.pc = 0;
        this.callStack.clear();
        this.whileStack.clear();
        this.funcAddresses.clear();

        preprocessFunctions();

        // 每次执行开始时重置取消标记，避免上一次的取消状态影响新执行
        ScriptSplash.resetCancelled();

        try {
            while (pc < lines.size()) {
                // 用户单击幕布 → 取消剩余命令
                if (ScriptSplash.isCancelled()) {
                    System.out.println("[SScript] 已由用户取消");
                    break;
                }
                executeLine(lines.get(pc));
                pc++;
            }
        } finally {
            // 脚本执行结束（正常完成或取消）→ 关闭幕布
            ScriptSplash.off();
        }
    }

    /**
     * 第一遍扫描：记录所有函数定义的起始地址。
     */
    private void preprocessFunctions() {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("func ")) {
                String[] parts = line.split("\\s+", 2);
                if (parts.length == 2) {
                    funcAddresses.put(parts[1], i + 1);
                }
            }
        }
    }

    /**
     * 执行单行命令。
     */
    private void executeLine(String rawLine) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        line = expandMacros(line);

        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "set":        handleSet(parts); break;
            case "add":        handleMath(parts, (a, b) -> a + b); break;
            case "sub":        handleMath(parts, (a, b) -> a - b); break;
            case "mul":        handleMath(parts, (a, b) -> a * b); break;
            case "div":        handleMath(parts, (a, b) -> b != 0 ? a / b : 0); break;
            case "mod":        handleMath(parts, (a, b) -> b != 0 ? a % b : 0); break;
            case "if":         handleIf(parts); break;
            case "else":       handleElse(); break;
            case "endif":      break;
            case "while":      handleWhile(parts); break;
            case "endwhile":   handleEndWhile(); break;
            case "func":       skipFunction(); break;
            case "endfunc":    handleReturn(); break;
            case "call":       handleCall(parts); break;
            case "return":     handleReturn(); break;
            case "getcourse":  handleGetCourse(parts); break;
            case "setcourse":  handleSetCourse(parts); break;
            case "replace":    handleReplace(parts); break;
            case "addblock":   handleAddBlock(parts); break;
            case "delblock":    handleDelBlock(parts); break;
            case "removeblock":handleRemoveBlock(parts); break;
            case "cmd":        handleCmd(parts); break;
            case "mouse":      handleMouse(parts); break;
            case "click":      handleClick(parts, false); break;
            case "clickright": handleClick(parts, true); break;
            case "key":        handleKey(parts); break;
            case "type":       handleType(parts); break;
            case "delay":      handleDelay(parts); break;
            case "run":        handleRun(parts); break;
            case "splashscreen": handleSplash(parts); break;
            case "shownotify":   handleShowNotify(parts); break;
            case "popcircle":    handlePopCircle(parts); break;
            case "print":      handlePrint(parts); break;
            case "exit":       pc = lines.size(); break;
            default:
                System.err.println("[SScript] 未知命令: " + cmd + " (行 " + (pc + 1) + ")");
        }
    }

    // ==================================================================
    // 变量与运算
    // ==================================================================

    /**
     * set $var value —— 变量赋值。
     */
    private void handleSet(String[] parts) {
        if (parts.length < 3) return;
        String varName = parts[1];
        String value = resolve(parts[2]);
        // 支持多词字符串值（如 set $msg Hello World）
        if (parts.length > 3) {
            StringBuilder sb = new StringBuilder(value);
            for (int i = 3; i < parts.length; i++) {
                sb.append(" ").append(resolve(parts[i]));
            }
            value = sb.toString();
        }
        ctx.vars.put(varName, value);
    }

    /**
     * 通用数学运算：add/sub/mul/div/mod $var operand。
     */
    private void handleMath(String[] parts, MathOp op) {
        if (parts.length < 3) return;
        String varName = parts[1];
        int current = parseInt(ctx.vars.getOrDefault(varName, "0"));
        int operand = parseInt(resolve(parts[2]));
        int result = op.apply(current, operand);
        ctx.vars.put(varName, String.valueOf(result));
    }

    @FunctionalInterface
    private interface MathOp {
        int apply(int a, int b);
    }

    // ==================================================================
    // 条件与循环
    // ==================================================================

    /**
     * if $var op value —— 条件为假时跳转到 else 或 endif。
     */
    private void handleIf(String[] parts) {
        if (!evaluateCondition(parts)) {
            int elseLine = findMatching("else", "endif", pc + 1);
            int endifLine = findMatching("endif", null, pc + 1);
            // 优先跳转到 else（如果存在且在 endif 之前）
            if (elseLine >= 0 && (endifLine < 0 || elseLine < endifLine)) {
                pc = elseLine; // 停在 else 行，下一轮 pc++ 进入 else 体
            } else if (endifLine >= 0) {
                pc = endifLine;
            } else {
                pc = lines.size();
            }
        }
    }

    /**
     * else —— if 条件为真时，执行完 if 体后跳过 else 体到 endif。
     */
    private void handleElse() {
        int endifLine = findMatching("endif", null, pc + 1);
        if (endifLine >= 0) {
            pc = endifLine;
        }
    }

    /**
     * while $var op value —— 条件为假时跳转到 endwhile。
     */
    private void handleWhile(String[] parts) {
        if (evaluateCondition(parts)) {
            whileStack.push(pc); // 记录 while 起始行，endwhile 时跳回
        } else {
            int endLine = findMatching("endwhile", null, pc + 1);
            if (endLine >= 0) {
                pc = endLine;
            } else {
                pc = lines.size();
            }
        }
    }

    /**
     * endwhile —— 跳回对应的 while 行重新判断条件。
     */
    private void handleEndWhile() {
        if (!whileStack.isEmpty()) {
            int whileLine = whileStack.pop();
            pc = whileLine - 1; // 下一轮 pc++ 回到 while 行
        }
    }

    /**
     * 求值条件表达式：$var op value，op ∈ {==, !=, >, <, >=, <=}。
     * 字符串比较仅支持 == / !=，数字比较支持全部运算符。
     * 两侧均经内联解析（支持 $course$ 等宏与 $var 变量）；
     * 字面 {@code ""} 视为空字符串。
     */
    private boolean evaluateCondition(String[] parts) {
        if (parts.length < 4) return false;
        String left = inlineResolve(parts[1]);
        String op = parts[2];
        String right = inlineResolve(parts[3]);
        if (right.equals("\"\"") || right.equals("''")) {
            right = "";
        }

        // 尝试数字比较
        try {
            int ln = Integer.parseInt(left);
            int rn = Integer.parseInt(right);
            switch (op) {
                case "==": return ln == rn;
                case "!=": return ln != rn;
                case ">":  return ln > rn;
                case "<":  return ln < rn;
                case ">=": return ln >= rn;
                case "<=": return ln <= rn;
            }
        } catch (NumberFormatException ignored) {
            // 非数字，走字符串比较
        }
        switch (op) {
            case "==": return left.equals(right);
            case "!=": return !left.equals(right);
            default:   return false;
        }
    }

    /**
     * 从 start 行开始查找匹配的关键字（考虑嵌套）。
     *
     * @param target    目标关键字
     * @param altTarget 可选的备选关键字（如 if 查找 else/endif），为 null 时只找 target
     * @param start     起始行（不含当前行）
     * @return 匹配行号，未找到返回 -1
     */
    private int findMatching(String target, String altTarget, int start) {
        int depth = 1;
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String cmd = line.split("\\s+")[0].toLowerCase();
            // 进入嵌套
            if (cmd.equals("if") || cmd.equals("while") || cmd.equals("func")) {
                depth++;
            }
            // 退出嵌套
            if (cmd.equals("endif") || cmd.equals("endwhile") || cmd.equals("endfunc")) {
                depth--;
                if (depth == 0) {
                    if (cmd.equals(target) || (altTarget != null && cmd.equals(altTarget))) {
                        return i;
                    }
                    // 找到了 endif 但目标是 else → 说明没有 else
                    if (altTarget != null && cmd.equals(target)) {
                        return i;
                    }
                }
            }
            // 在同一层级遇到 else（仅 if 嵌套中）
            if (depth == 1 && altTarget != null && cmd.equals(altTarget)) {
                return i;
            }
            if (depth == 1 && cmd.equals(target)) {
                return i;
            }
        }
        return -1;
    }

    // ==================================================================
    // 函数（支持递归）
    // ==================================================================

    /**
     * func name —— 函数定义，非调用时跳过函数体。
     */
    private void skipFunction() {
        int endLine = findMatching("endfunc", null, pc + 1);
        if (endLine >= 0) {
            pc = endLine;
        } else {
            pc = lines.size();
        }
    }

    /**
     * call name —— 调用函数，将返回地址压栈并跳转到函数体。
     */
    private void handleCall(String[] parts) {
        if (parts.length < 2) return;
        String funcName = parts[1];
        Integer addr = funcAddresses.get(funcName);
        if (addr == null) {
            System.err.println("[SScript] 未定义的函数: " + funcName);
            return;
        }
        callStack.push(pc); // 保存 call 下一行的地址（pc++ 后自然进入函数）
        pc = addr - 1; // 下一轮 pc++ 进入函数体第一行
    }

    /**
     * return / endfunc —— 从函数返回，弹栈恢复 pc。
     */
    private void handleReturn() {
        if (!callStack.isEmpty()) {
            pc = callStack.pop();
        }
    }

    // ==================================================================
    // 课表操作
    // ==================================================================

    /**
     * getcourse index $var —— 将第 index 节课的科目存入变量。
     */
    private void handleGetCourse(String[] parts) {
        if (parts.length < 3) return;
        int index = parseInt(resolve(parts[1]));
        String varName = parts[2];
        if (index >= 0 && index < ctx.courses.size()) {
            ctx.vars.put(varName, ctx.courses.get(index));
        } else {
            ctx.vars.put(varName, "");
        }
    }

    /**
     * setcourse index name —— 设置第 index 节课的科目。
     */
    private void handleSetCourse(String[] parts) {
        if (parts.length < 3) return;
        int index = parseInt(resolve(parts[1]));
        String name = resolve(parts[2]);
        if (parts.length > 3) {
            StringBuilder sb = new StringBuilder(name);
            for (int i = 3; i < parts.length; i++) {
                sb.append(" ").append(resolve(parts[i]));
            }
            name = sb.toString();
        }
        if (index >= 0 && index < ctx.courses.size()) {
            ctx.courses.set(index, name);
            ctx.flushSchedule();
        } else if (index >= ctx.courses.size()) {
            // 自动扩展课程列表
            while (ctx.courses.size() <= index) {
                ctx.courses.add("");
            }
            ctx.courses.set(index, name);
            ctx.flushSchedule();
        }
    }

    /**
     * replace old new —— 将课表中所有 old 科目替换为 new。
     */
    private void handleReplace(String[] parts) {
        if (parts.length < 3) return;
        String oldName = resolve(parts[1]);
        String newName = resolve(parts[2]);
        if (parts.length > 3) {
            StringBuilder sb = new StringBuilder(newName);
            for (int i = 3; i < parts.length; i++) {
                sb.append(" ").append(resolve(parts[i]));
            }
            newName = sb.toString();
        }
        for (int i = 0; i < ctx.courses.size(); i++) {
            if (ctx.courses.get(i).equals(oldName)) {
                ctx.courses.set(i, newName);
            }
        }
        ctx.flushSchedule();
    }

    // ==================================================================
    // 显示块操作
    // ==================================================================

    /**
     * addblock name content —— 添加一个额外显示块。
     * name 仅作标识，实际显示 content。
     * content 支持内联变量（{@code $var$} 或 {@code $var}）与时间宏（如 {@code %date%}）。
     * 示例：{@code addblock 1 today is $date$}。
     * <b>内置块 date（blocks[0]）与 monitor（blocks[2]）不可通过 addblock 添加</b>，需用 delblock 隐藏。
     */
    private void handleAddBlock(String[] parts) {
        if (parts.length < 3) return;
        // parts[1] 是块名称（标识用，不参与显示）
        String name = parts[1];
        if ("date".equalsIgnoreCase(name) || "monitor".equalsIgnoreCase(name)) {
            return; // 内置块不允许通过 addblock 添加
        }
        StringBuilder content = new StringBuilder(inlineResolve(parts[2]));
        for (int i = 3; i < parts.length; i++) {
            content.append(" ").append(inlineResolve(parts[i]));
        }
        ctx.blocks.add(content.toString());
    }

    /**
     * delblock date|monitor —— 隐藏内置显示块。
     * <ul>
     *   <li>{@code delblock date}：隐藏日期块（blocks[0]）</li>
     *   <li>{@code delblock monitor}：隐藏值日生块（blocks[2]）</li>
     * </ul>
     * 实现为置空字符串（不从 List 移除，避免打乱 blocks 索引约定）。
     */
    private void handleDelBlock(String[] parts) {
        if (parts.length < 2) return;
        String name = parts[1];
        if ("date".equalsIgnoreCase(name)) {
            if (ctx.blocks.size() > 0) {
                ctx.blocks.set(0, "");
            }
        } else if ("monitor".equalsIgnoreCase(name)) {
            if (ctx.blocks.size() > 2) {
                ctx.blocks.set(2, "");
            }
        }
    }

    // ==================================================================
    // 系统操作：cmd / 鼠标 / 键盘
    // ==================================================================

    /** 键鼠控制 Robot 实例（懒加载）。 */
    private Robot robot;

    /**
     * cmd command —— 在 Windows 上异步执行一条 cmd 命令。
     * 参数中的变量与宏会先展开。示例：{@code cmd echo hello}、{@code cmd notepad}。
     */
    private void handleCmd(String[] parts) {
        if (ctx.dryRun) return; // 预览模式跳过系统命令
        if (parts.length < 2) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            sb.append(inlineResolve(parts[i])).append(" ");
        }
        String command = sb.toString().trim();
        if (command.isEmpty()) return;
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // 异步读取输出，避免阻塞脚本执行
            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.out.println("[sscm-cmd] " + line);
                    }
                } catch (IOException ignored) {
                }
            }).start();
        } catch (IOException e) {
            System.err.println("[SScript] cmd 执行失败: " + e.getMessage());
        }
    }

    /**
     * mouse x y —— 移动鼠标到屏幕坐标 (x, y)。
     */
    private void handleMouse(String[] parts) {
        if (ctx.dryRun) return; // 预览模式跳过系统命令
        if (parts.length < 3) return;
        Robot r = getRobot();
        if (r == null) return;
        int x = parseInt(resolve(parts[1]));
        int y = parseInt(resolve(parts[2]));
        r.mouseMove(x, y);
    }

    /**
     * click [x y] —— 左键单击当前鼠标位置（或先移动到 x,y）。
     * clickright [x y] —— 右键单击。
     * click 执行前自动 suspend 幕布（隐式 off），执行完成后自动 resume 恢复，
     * 确保 Robot 点击不会被幕布拦截。
     */
    private void handleClick(String[] parts, boolean right) {
        Robot r = getRobot();
        if (r == null) return;
        if (parts.length >= 3) {
            int x = parseInt(resolve(parts[1]));
            int y = parseInt(resolve(parts[2]));
            r.mouseMove(x, y);
        }
        int mask = right ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
        // click 前隐式关闭幕布，点击穿透到下层窗口
        ScriptSplash.suspend();
        try {
            r.mousePress(mask);
            r.mouseRelease(mask);
        } finally {
            // 执行完成后自动恢复幕布
            ScriptSplash.resume();
        }
    }

    /**
     * key keyname —— 按下并释放一个按键。
     * keyname 支持 KeyEvent 常量名（ENTER / ESC / SPACE / TAB / F1~F24 / A~Z / 0~9 等）。
     */
    private void handleKey(String[] parts) {
        if (ctx.dryRun) return; // 预览模式跳过系统命令
        if (parts.length < 2) return;
        Robot r = getRobot();
        if (r == null) return;
        int code = keyCodeFor(resolve(parts[1]));
        if (code > 0) {
            r.keyPress(code);
            r.keyRelease(code);
        } else {
            System.err.println("[SScript] 未知按键: " + parts[1]);
        }
    }

    /**
     * type text —— 逐字符键入文本（支持大小写字母、数字、常用标点；中文无法键入）。
     */
    private void handleType(String[] parts) {
        if (ctx.dryRun) return; // 预览模式跳过系统命令
        if (parts.length < 2) return;
        Robot r = getRobot();
        if (r == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            sb.append(inlineResolve(parts[i])).append(" ");
        }
        String text = sb.toString().trim();
        for (char c : text.toCharArray()) {
            TypeKey tk = typeKeyFor(c);
            if (tk == null) {
                System.err.println("[SScript] 无法键入字符: " + c);
                continue;
            }
            if (tk.shift) r.keyPress(KeyEvent.VK_SHIFT);
            r.keyPress(tk.code);
            r.keyRelease(tk.code);
            if (tk.shift) r.keyRelease(KeyEvent.VK_SHIFT);
        }
    }

    /**
     * delay milliseconds —— 暂停两行命令之间的执行。
     * 暂停期间用户单击幕布可立即取消（剩余命令跳过）。
     */
    private void handleDelay(String[] parts) {
        if (ctx.dryRun) return; // 预览模式跳过系统命令
        if (parts.length < 2) return;
        int ms = parseInt(resolve(parts[1]));
        if (ms <= 0) return;
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (ScriptSplash.isCancelled()) {
                pc = lines.size(); // 用户取消 → 跳出整个脚本
                return;
            }
            long remain = end - System.currentTimeMillis();
            try {
                Thread.sleep(Math.min(50, remain));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * run filename —— 调用 ./commands/ 下的另一个命令文件执行（共享同一 Context）。
     * 未自动执行的普通命令文件需通过本命令显式调用。
     * run 内可再 run（递归由用户自控）。示例：{@code run 02_morning.txt}。
     */
    private void handleRun(String[] parts) {
        if (parts.length < 2) return;
        String name = resolve(parts[1]);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            name += ".txt";
        }
        File f = new File(Main.commandsDir, name);
        if (!f.exists() || !f.isFile()) {
            System.err.println("[SScript] 找不到命令文件: " + name);
            return;
        }
        List<String> subLines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                subLines.add(line);
            }
        } catch (IOException e) {
            System.err.println("[SScript] run 读取失败: " + name + " - " + e.getMessage());
            return;
        }
        // 子解释器共享 ctx（变量/课程/显示块互通），独立函数表
        new SScriptInterpreter().execute(subLines, ctx);
    }

    /**
     * splashscreen on [text...] / splashscreen off —— 命令控制全屏幕布。
     * {@code on} 显示幕布并在其上新增一行文本；多次 on 累积多行。
     * {@code off} 关闭幕布并清空新增行。
     * 幕布显示期间，每解释一行命令都会更新"目前命令"文本。
     */
    private void handleSplash(String[] parts) {
        if (ctx.dryRun) return; // 预览模式跳过系统命令
        if (parts.length < 2) return;
        String action = parts[1].toLowerCase(Locale.ROOT);
        if ("off".equals(action)) {
            ScriptSplash.off();
            return;
        }
        if ("on".equals(action)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                sb.append(inlineResolve(parts[i])).append(" ");
            }
            ScriptSplash.on(sb.toString().trim());
            return;
        }
        System.err.println("[SScript] splashscreen 未知操作: " + action + "（应为 on / off）");
    }

    /**
     * shownotify [delay] [text] —— 弹出顶部滑入式通知窗口。
     * delay 为显示时长（毫秒），text 为通知文本（支持多词与内联变量）。
     */
    private void handleShowNotify(String[] parts) {
        if (ctx.dryRun) return; // 预览模式跳过
        if (parts.length < 3) return;
        int millis = parseInt(resolve(parts[1]));
        StringBuilder sb = new StringBuilder(inlineResolve(parts[2]));
        for (int i = 3; i < parts.length; i++) {
            sb.append(" ").append(inlineResolve(parts[i]));
        }
        String text = sb.toString();
        SwingUtilities.invokeLater(() -> {
            NoticeWindow nw = new NoticeWindow(text, millis);
            nw.setVisible(true);
        });
    }

    /**
     * popcircle [x] [y] [r] [color] —— 在屏幕指定位置显示圆圈扩散淡出动画。
     * x/y 为屏幕中心坐标，r 为最大半径（像素），color 支持 16 进制（#FF0000 / FF0000）或颜色名（red/blue 等）。
     */
    private void handlePopCircle(String[] parts) {
        if (ctx.dryRun) return; // 预览模式跳过
        if (parts.length < 5) return;
        int x = parseInt(resolve(parts[1]));
        int y = parseInt(resolve(parts[2]));
        int r = parseInt(resolve(parts[3]));
        Color color = parseColor(parts[4]);
        if (color == null) {
            System.err.println("[SScript] popcircle 无法解析颜色: " + parts[4]);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            CircleAnimationWindow caw = new CircleAnimationWindow(x, y, r, color);
            caw.startAnimation();
        });
    }

    /**
     * 解析颜色字符串：支持 #RRGGBB / 0xRRGGBB / RRGGBB，以及 Color 静态字段名（red/blue/green 等）。
     */
    private static Color parseColor(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Color.decode(s.startsWith("#") || s.startsWith("0x") ? s : "#" + s);
        } catch (NumberFormatException e) {
            try {
                java.lang.reflect.Field f = Color.class.getField(s.toLowerCase(Locale.ROOT));
                return (Color) f.get(null);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * 懒加载 Robot 实例；headless 环境返回 null。
     */
    private Robot getRobot() {
        if (robot == null) {
            try {
                robot = new Robot();
            } catch (AWTException e) {
                System.err.println("[SScript] 无法初始化键鼠控制: " + e.getMessage());
            }
        }
        return robot;
    }

    /** 按键映射结果。 */
    private record TypeKey(int code, boolean shift) {
    }

    /**
     * 将字符映射为键盘键码（含 shift 需求）。
     *
     * @return 映射结果；不支持的字符返回 null
     */
    private TypeKey typeKeyFor(char c) {
        if (c >= 'a' && c <= 'z') {
            return new TypeKey(KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(c)), false);
        }
        if (c >= 'A' && c <= 'Z') {
            return new TypeKey(KeyEvent.getExtendedKeyCodeForChar(c), true);
        }
        if (c >= '0' && c <= '9') {
            return new TypeKey(KeyEvent.getExtendedKeyCodeForChar(c), false);
        }
        switch (c) {
            case ' ': return new TypeKey(KeyEvent.VK_SPACE, false);
            case ',': return new TypeKey(KeyEvent.VK_COMMA, false);
            case '.': return new TypeKey(KeyEvent.VK_PERIOD, false);
            case '/': return new TypeKey(KeyEvent.VK_SLASH, false);
            case ';': return new TypeKey(KeyEvent.VK_SEMICOLON, false);
            case '\'': return new TypeKey(KeyEvent.VK_QUOTE, false);
            case '[': return new TypeKey(KeyEvent.VK_OPEN_BRACKET, false);
            case ']': return new TypeKey(KeyEvent.VK_CLOSE_BRACKET, false);
            case '\\': return new TypeKey(KeyEvent.VK_BACK_SLASH, false);
            case '-': return new TypeKey(KeyEvent.VK_MINUS, false);
            case '=': return new TypeKey(KeyEvent.VK_EQUALS, false);
            case '`': return new TypeKey(KeyEvent.VK_BACK_QUOTE, false);
            // 需要 Shift 的符号
            case '~': return new TypeKey(KeyEvent.VK_BACK_QUOTE, true);
            case '!': return new TypeKey(KeyEvent.VK_1, true);
            case '@': return new TypeKey(KeyEvent.VK_2, true);
            case '#': return new TypeKey(KeyEvent.VK_3, true);
            case '$': return new TypeKey(KeyEvent.VK_4, true);
            case '%': return new TypeKey(KeyEvent.VK_5, true);
            case '^': return new TypeKey(KeyEvent.VK_6, true);
            case '&': return new TypeKey(KeyEvent.VK_7, true);
            case '*': return new TypeKey(KeyEvent.VK_8, true);
            case '(': return new TypeKey(KeyEvent.VK_9, true);
            case ')': return new TypeKey(KeyEvent.VK_0, true);
            case '_': return new TypeKey(KeyEvent.VK_MINUS, true);
            case '+': return new TypeKey(KeyEvent.VK_EQUALS, true);
            case '{': return new TypeKey(KeyEvent.VK_OPEN_BRACKET, true);
            case '}': return new TypeKey(KeyEvent.VK_CLOSE_BRACKET, true);
            case '|': return new TypeKey(KeyEvent.VK_BACK_SLASH, true);
            case ':': return new TypeKey(KeyEvent.VK_SEMICOLON, true);
            case '"': return new TypeKey(KeyEvent.VK_QUOTE, true);
            case '<': return new TypeKey(KeyEvent.VK_COMMA, true);
            case '>': return new TypeKey(KeyEvent.VK_PERIOD, true);
            case '?': return new TypeKey(KeyEvent.VK_SLASH, true);
            default: return null;
        }
    }

    /**
     * 将按键名称解析为键码。
     * 支持 KeyEvent 常量名、单字符、F1~F24。
     *
     * @return 键码；未知名称返回 -1
     */
    private int keyCodeFor(String name) {
        if (name == null) return -1;
        String up = name.toUpperCase(Locale.ROOT);
        switch (up) {
            case "ENTER": return KeyEvent.VK_ENTER;
            case "ESC": case "ESCAPE": return KeyEvent.VK_ESCAPE;
            case "SPACE": return KeyEvent.VK_SPACE;
            case "TAB": return KeyEvent.VK_TAB;
            case "BACKSPACE": return KeyEvent.VK_BACK_SPACE;
            case "DELETE": return KeyEvent.VK_DELETE;
            case "SHIFT": return KeyEvent.VK_SHIFT;
            case "CTRL": case "CONTROL": return KeyEvent.VK_CONTROL;
            case "ALT": return KeyEvent.VK_ALT;
            case "CAPSLOCK": return KeyEvent.VK_CAPS_LOCK;
            case "UP": return KeyEvent.VK_UP;
            case "DOWN": return KeyEvent.VK_DOWN;
            case "LEFT": return KeyEvent.VK_LEFT;
            case "RIGHT": return KeyEvent.VK_RIGHT;
            case "HOME": return KeyEvent.VK_HOME;
            case "END": return KeyEvent.VK_END;
            case "PAGEUP": return KeyEvent.VK_PAGE_UP;
            case "PAGEDOWN": return KeyEvent.VK_PAGE_DOWN;
            default:
                if (up.length() == 1) {
                    char ch = up.charAt(0);
                    if (Character.isLetterOrDigit(ch)) {
                        return KeyEvent.getExtendedKeyCodeForChar(ch);
                    }
                }
                if (up.startsWith("F") && up.length() > 1 && up.length() <= 3) {
                    try {
                        int n = Integer.parseInt(up.substring(1));
                        if (n >= 1 && n <= 24) {
                            return KeyEvent.VK_F1 + n - 1;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                return -1;
        }
    }

    /**
     * removeblock index —— 移除第 index 个额外显示块（index 从 0 开始，对应 blocks[3+index]）。
     */
    private void handleRemoveBlock(String[] parts) {
        if (parts.length < 2) return;
        int index = parseInt(resolve(parts[1]));
        int blockIndex = 3 + index;
        if (blockIndex >= 3 && blockIndex < ctx.blocks.size()) {
            ctx.blocks.remove(blockIndex);
        }
    }

    // ==================================================================
    // 工具方法
    // ==================================================================

    /**
     * print message —— 控制台输出（调试用）。
     */
    private void handlePrint(String[] parts) {
        StringBuilder sb = new StringBuilder("[SScript] ");
        for (int i = 1; i < parts.length; i++) {
            sb.append(resolve(parts[i])).append(" ");
        }
        System.out.println(sb.toString().trim());
    }

    /**
     * 解析 token：以 $ 开头则取变量值，否则返回字面量。
     */
    private String resolve(String token) {
        if (token.startsWith("$")) {
            return ctx.vars.getOrDefault(token, "");
        }
        return token;
    }

    /**
     * 内联变量解析：将文本中的 {@code $name$} / {@code $name} 替换为对应值。
     *
     * <p>优先级：宏名（date/time/day/dayname/month/dayofmonth/hour/minute/second/week/year）
     * 优先取宏值，否则取脚本变量（{@code set $name value}）。</p>
     *
     * <p>示例：{@code today is $date$} → {@code today is 09.05}。</p>
     */
    private String inlineResolve(String text) {
        if (text == null || text.indexOf('$') < 0) {
            return text;
        }
        Matcher m = Pattern.compile("\\$(\\w+)\\$?").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            String macroVal = macroValue(name);
            String val = macroVal != null ? macroVal : ctx.vars.getOrDefault("$" + name, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 取宏名对应的宏值；非宏名返回 null。
     */
    private String macroValue(String name) {
        if (ctx.date != null) {
            switch (name) {
                case "date": return ctx.date.format(DateTimeFormatter.ofPattern("MM.dd"));
                case "day": return String.valueOf(ctx.date.getDayOfWeek().getValue());
                case "dayname": return dayName(ctx.date.getDayOfWeek().getValue());
                case "month": return String.valueOf(ctx.date.getMonthValue());
                case "dayofmonth": return String.valueOf(ctx.date.getDayOfMonth());
                case "year": return String.valueOf(ctx.date.getYear());
                default: break;
            }
        }
        // 时间宏：直接取当前偏移时间（不依赖 ctx.time 快照，确保 tick.txt 中也准确）
        LocalTime now = LocalTime.now().plusSeconds(Main.timeOffsetSeconds);
        switch (name) {
            case "time": return now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            case "hour": return String.valueOf(now.getHour());
            case "minute": return String.valueOf(now.getMinute());
            case "second": return String.valueOf(now.getSecond());
            default: break;
        }
        if ("week".equals(name)) {
            return String.valueOf(ctx.weekTurn);
        }
        if ("course".equals(name)) {
            // 当前时段对应的课表 token 本身（含 | \ ~ 等）；仅隐藏字符（full_name.txt
            // 中 $---$ 分割线上方）返回 null，时段外返回空串
            return currentCourse();
        }
        if ("nextcourse".equals(name)) {
            // 下一时段的课表 token 本身（课间→下一节，上课中→下课后下一节）；
            // 仅隐藏字符返回 null，无后续时段返回空串
            return nextCourse();
        }
        if ("monitor".equals(name)) {
            // 值日生（blocks[2]）；不存在时返回空串
            return ctx.blocks != null && ctx.blocks.size() > 2 && ctx.blocks.get(2) != null
                    ? ctx.blocks.get(2) : "";
        }
        return null;
    }

    /**
     * 计算当前时间对应的课表 token。
     *
     * <p>按 {@link Context#times}（HH:mm 成对）匹配当前时间所在时段，返回
     * {@link Context#courses} 对应位置的 token<b>本身</b>（包括 {@code |} 恰饭、
     * {@code \} 无课、{@code ~} 晚自习等）；仅当该 token 为隐藏字符
     * （full_name.txt 中 {@code $---$} 分割线上方）时返回 null；时段外返回空串。</p>
     *
     * @return 当前时段 token；时段外返回 ""；隐藏字符返回 null
     */
    private String currentCourse() {
        if (ctx.courses == null || ctx.courses.isEmpty()
                || ctx.times == null || ctx.times.size() < 2) {
            return "";
        }
        // 用当前偏移时间（不依赖 ctx.time 快照，确保 tick.txt 中也准确）
        LocalTime now = LocalTime.now().plusSeconds(Main.timeOffsetSeconds);
        for (int i = 0; i + 1 < ctx.times.size(); i += 2) {
            LocalTime s = parseTime(ctx.times.get(i));
            LocalTime e = parseTime(ctx.times.get(i + 1));
            if (s == null || e == null) continue;
            if (!now.isBefore(s) && !now.isAfter(e)) {
                int idx = i / 2;
                if (idx >= 0 && idx < ctx.courses.size()) {
                    String c = ctx.courses.get(idx);
                    if (c.isEmpty()) {
                        return "";
                    }
                    if (isHiddenCourse(c)) {
                        return null;
                    }
                    return c;
                }
                return "";
            }
        }
        return "";
    }

    /**
     * 计算下一时段的课表 token。
     *
     * <p>以当前偏移时间为基准：处于课间时返回下一时段的 token；正在上课时返回
     * 下课后（下一时段）的 token。与 {@link #currentCourse()} 一致，返回 token
     * <b>本身</b>（含 {@code |}、{@code \}、{@code ~} 等）；仅隐藏字符返回 null；
     * 无后续时段返回空串。</p>
     *
     * @return 下一时段 token；无后续时段返回 ""；隐藏字符返回 null
     */
    private String nextCourse() {
        if (ctx.courses == null || ctx.courses.isEmpty()
                || ctx.times == null || ctx.times.size() < 2) {
            return "";
        }
        LocalTime now = LocalTime.now().plusSeconds(Main.timeOffsetSeconds);
        int slot = -1; // 当前所在时段索引（时段前/课间时停留在上一时段；第一节课前为 -1）
        for (int i = 0; i + 1 < ctx.times.size(); i += 2) {
            LocalTime s = parseTime(ctx.times.get(i));
            LocalTime e = parseTime(ctx.times.get(i + 1));
            if (s == null || e == null) continue;
            if (now.isBefore(s)) break; // 处于该时段前的课间
            slot = i / 2;
            if (!now.isAfter(e)) break; // 处于该时段内
        }
        int next = slot + 1;
        if (next < 0 || next >= ctx.courses.size()) {
            return "";
        }
        String c = ctx.courses.get(next);
        if (c.isEmpty()) {
            return "";
        }
        if (isHiddenCourse(c)) {
            return null;
        }
        return c;
    }

    /** 判断课程简称是否为隐藏字符（full_name.txt 中 {@code $---$} 分割线上方的键）。 */
    private boolean isHiddenCourse(String c) {
        return Main.iifr != null && Main.iifr.isHidden(c);
    }

    /**
     * 解析 HH:mm 时间；格式非法返回 null。
     */
    private static LocalTime parseTime(String t) {
        try {
            return LocalTime.parse(t);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 删除文本中所有格式化 token。
     *
     * <p>格式码：{@code #cRRGGBB} 字体颜色、{@code #gRRGGBB} 背景色（16 进制色码），
     * {@code #i} 斜体、{@code #b} 粗体、{@code #u} 下划线、{@code #d} 删除线、
     * {@code #r} 重置。解析课表时调用，保证课程计数/索引不受格式 token 影响；
     * 格式只在渲染时应用。</p>
     *
     * @param s 含格式 token 的文本
     * @return 删除全部格式 token 后的文本
     */
    public static String stripFormat(String s) {
        if (s == null || s.indexOf('#') < 0) {
            return s == null ? null : s.trim();
        }
        // 删除全部格式 token，并压缩删除后残留的多余空格（独立 token 如 "语 #cFF0000 数" → "语 数"）
        return FORMAT_TOKEN.matcher(s).replaceAll("").replaceAll("  +", " ").trim();
    }

    /** 格式化 token：样式单字符（ibudr）或 #c/#g + 6 位 16 进制色码。 */
    private static final java.util.regex.Pattern FORMAT_TOKEN =
            java.util.regex.Pattern.compile("#(?i)(?:[ibudr]|[cg][0-9a-f]{6})");

    /**
     * 安全解析整数，失败返回 0。
     */
    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 展开时间宏。
     *
     * <p>支持的宏：</p>
     * <ul>
     *   <li>{@code %date%} — MM.dd</li>
     *   <li>{@code %time%} — HH:mm</li>
     *   <li>{@code %day%} — 星期几数字（1=周一）</li>
     *   <li>{@code %dayname%} — 星期几中文</li>
     *   <li>{@code %month%} — 月份</li>
     *   <li>{@code %dayofmonth%} — 日</li>
     *   <li>{@code %hour%} — 小时</li>
     *   <li>{@code %minute%} — 分钟</li>
     *   <li>{@code %second%} — 秒</li>
     *   <li>{@code %week%} — 周次</li>
     *   <li>{@code %year%} — 年份</li>
     * </ul>
     */
    private String expandMacros(String line) {
        if (ctx.date != null) {
            line = line.replace("%date%", ctx.date.format(DateTimeFormatter.ofPattern("MM.dd")));
            line = line.replace("%day%", String.valueOf(ctx.date.getDayOfWeek().getValue()));
            line = line.replace("%dayname%", dayName(ctx.date.getDayOfWeek().getValue()));
            line = line.replace("%month%", String.valueOf(ctx.date.getMonthValue()));
            line = line.replace("%dayofmonth%", String.valueOf(ctx.date.getDayOfMonth()));
            line = line.replace("%year%", String.valueOf(ctx.date.getYear()));
        }
        if (ctx.time != null) {
            line = line.replace("%time%", ctx.time.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            line = line.replace("%hour%", String.valueOf(ctx.time.getHour()));
            line = line.replace("%minute%", String.valueOf(ctx.time.getMinute()));
            line = line.replace("%second%", String.valueOf(ctx.time.getSecond()));
        }
        line = line.replace("%week%", String.valueOf(ctx.weekTurn));
        return line;
    }

    /**
     * 星期数字转中文名。
     */
    private String dayName(int dayNum) {
        switch (dayNum) {
            case 1: return "周一";
            case 2: return "周二";
            case 3: return "周三";
            case 4: return "周四";
            case 5: return "周五";
            case 6: return "周六";
            case 7: return "周日";
            default: return "";
        }
    }
}
