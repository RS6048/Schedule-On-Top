import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * 悬浮课表程序入口与全局状态管理。
 *
 * <p>负责：</p>
 * <ul>
 *   <li>初始化配置文件（不存在时生成默认模板）；</li>
 *   <li>加载课表、自习、晚自习、值日生等数据；</li>
 *   <li>维护本地配置（周次、主题色、晚自习开关等）；</li>
 *   <li>启动定时器，实时更新当前课程进度与倒计时；</li>
 *   <li>创建主窗口、系统托盘等 UI 组件。</li>
 * </ul>
 *
 * <p>配置文件约定（均位于程序运行目录）：</p>
 * <ul>
 *   <li>{@code schedule.txt} — 周一至周五课表（时间行 + 周一~周五课程行）</li>
 *   <li>{@code saturday.txt} — 周六课表（时间行 + 周次轮转行）</li>
 *   <li>{@code self_study.txt} — 自习（时间行 + 周次轮转行）</li>
 *   <li>{@code night_study.txt} — 晚自习时间段（周一~周五且本地开启时生效）</li>
 *   <li>{@code delay/*.delay} — 延迟课表（统一格式：时间行 + "课表 : 值日生"；周日课表也由当天延迟课表承载）</li>
 *   <li>{@code monitor.txt} — 值日生表</li>
 *   <li>{@code full_name.txt} — 课程简称→全称映射（{@code $---$} 分割线上方为隐藏字符）</li>
 *   <li>{@code .local} — 本地配置</li>
 * </ul>
 */
public class Main {

    // ===== 全局 UI 与工具实例 =====
    /** 主悬浮课条窗口。 */
    public static MainWindow mw;

    /** 课程简称映射读取器。 */
    public static IniFileReader iifr;

    /** 系统托盘实例。 */
    public static FrameTray tray;

    /** 全局 Toolkit（获取屏幕尺寸等）。 */
    public static final Toolkit tk = Toolkit.getDefaultToolkit();

    // ===== 配置文件 =====
    public static final File schedule = new File("./schedule.txt");
    public static final File saturday = new File("./saturday.txt");
    public static final File sstudy = new File("./self_study.txt");
    public static final File nstudy = new File("./night_study.txt");
    public static final File monitor = new File("./monitor.txt");
    public static final File fullName = new File("./full_name.txt");
    public static final File local = new File("./.local");

    /** 命令脚本目录（.txt，程序化修改课表与系统操作）。 */
    public static final File commandsDir = new File("./commands");

    /** .delay 延迟课表目录（指定日期覆盖课表）。 */
    public static final File delayDir = new File("./delay");

    // ===== 运行时数据 =====
    /** 本地配置键值（周次、主题色等）。 */
    public static final Map<String, Integer> locals = new HashMap<>();

    /** 今日星期数字（1=周一 ... 7=周日）。 */
    static int dayNum;

    /** 今日星期中文名。 */
    static String dayName;

    /** 显示内容：[0]=日期, [1]=课表, [2]=值日。 */
    static String[] cont;

    /** 全部时间点（上午 + 自习 + 晚自习）。 */
    public static String[] scheds;

    /** 上午时间点。 */
    static String[] times;

    /** 自习时间点。 */
    static String[] selfs;

    /** 晚自习时间点。 */
    static String[] night;

    /** 当前正在读取的文件名（用于错误提示）。 */
    private static String errorFile = "";

    /** 今日日期。 */
    static final LocalDate today = LocalDate.now();

    /** 最近一次 reload 的脚本上下文（tick.txt 每次更新基于其拷贝执行）。 */
    static volatile SScriptInterpreter.Context lastCtx;

    /** 时间偏移（秒）："我的时间慢了x秒"，课表时间判断与 $time$ 宏同步偏移。 */
    static volatile int timeOffsetSeconds = 0;

    /** tick 脚本是否正在执行（防重入）。 */
    private static volatile boolean tickRunning = false;

    /**
     * 程序入口。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        try {
            init();
            startClassMonitor();
            startAutoUpdate();
        } catch (Exception e) {
            fatalError(e);
        }
    }

    /**
     * 启动自动更新检查（后台线程，不阻塞启动）。
     *
     * <p>延迟数秒等主窗口就绪后静默检查；有新版时自动下载并重新编译，
     * 完成后弹窗提示重启生效。更新源与镜像见 {@link UpdateChecker}。</p>
     */
    private static void startAutoUpdate() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ignored) {
                return;
            }
            String result = UpdateChecker.runUpdate();
            if (result != null && result.startsWith("已更新")) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(mw, result, "自动更新",
                                JOptionPane.INFORMATION_MESSAGE));
            } else {
                System.out.println("[update] " + result);
            }
        }, "auto-update");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 启动课程进度监控定时器，每 1s 检查一次当前时间并执行 tick.txt。
     */
    private static void startClassMonitor() {
        TimeChecker tc = new TimeChecker();
        Timer t = new Timer(1000, e -> {
            if (scheds == null || scheds.length < 2) {
                return;
            }
            LocalTime now = LocalTime.now().plusSeconds(timeOffsetSeconds);
            boolean found = false;
            for (int i = 0; i < scheds.length - 1; i++) {
                // 跳过空时间点或格式异常的条目
                if (scheds[i] == null || scheds[i + 1] == null
                        || scheds[i].isEmpty() || scheds[i + 1].isEmpty()
                        || scheds[i].startsWith(":") || scheds[i + 1].startsWith(":")) {
                    continue;
                }
                try {
                    tc.load(scheds[i], scheds[i + 1]);
                } catch (Exception ex) {
                    continue;
                }
                if (tc.isInRange(now)) {
                    mw.setOnClassNumber(i);
                    mw.setOnClassProgress(tc.getProgress(now));
                    mw.setOnClassCountdown(tc.getShownCountdown(now));
                    found = true;
                    break;
                }
            }
            if (!found) {
                mw.setOnClassNumber(-1);
            }
            // 每次更新循环执行 tick.txt（后台线程，防重入）
            executeTickScript();
        });
        t.start();
    }

    /**
     * 运行时异常处理：弹出错误对话框并打印堆栈，<b>不退出程序</b>。
     * 用于 reload、脚本执行等可恢复的场景。
     *
     * @param e 捕获到的异常
     */
    public static void outputException(Exception e) {
        String message = "";
        if (e instanceof NoSuchElementException) {
            message = "\n看起来" + errorFile + "里没有今天";
        } else if (e instanceof IndexOutOfBoundsException) {
            message = "\n好像是周次或者课表有问题。";
        }
        JOptionPane.showMessageDialog(null, e + message, "错误", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
    }

    /**
     * 致命错误处理：初始化等不可恢复的场景，显示对话框后退出程序。
     *
     * @param e 捕获到的异常
     */
    public static void fatalError(Exception e) {
        outputException(e);
        System.exit(1);
    }

    /**
     * 静默记录错误（不弹对话框），用于非关键操作失败。
     *
     * @param context 错误发生的上下文描述
     * @param e       捕获到的异常
     */
    public static void logError(String context, Exception e) {
        System.err.println("[ERROR] " + context + ": " + e.getMessage());
    }

    /**
     * 初始化：确保配置文件存在、加载数据、创建主窗口与托盘。
     *
     * @throws Exception 初始化失败时抛出
     */
    public static void init() throws Exception {
        System.out.println("Preparing for initialize...");
        getLocals();
        ensureConfigFiles();

        mw = new MainWindow();
        reload();
        mw.setVisible(true);
        tray = new FrameTray();
    }

    /**
     * 确保所有配置文件存在，不存在时生成默认模板。
     *
     * @throws IOException 文件创建或写入失败时抛出
     */
    private static void ensureConfigFiles() throws IOException {
        // 创建脚本与延迟课表目录
        if (!commandsDir.exists()) commandsDir.mkdirs();
        if (!delayDir.exists()) delayDir.mkdirs();

        // load.txt：每次 reload() 自动执行（不存在则自动创建，保证换课等脚本机制可用）
        File load = new File(commandsDir, "load.txt");
        if (!load.exists()) {
            writeFile(load, """
                    # load.txt —— 每次 reload() 自动加载执行
                    # 在这里显式调用其他命令文件（不调用即忽略）。
                    run swap.txt
                    """);
        }
        // tick.txt：每次更新循环（1s）自动执行（不存在则自动创建，包含教师节祝福等持续判断）
        File tick = new File(commandsDir, "tick.txt");
        if (!tick.exists()) {
            writeFile(tick, """
                    # tick.txt —— 每次更新循环（1s）自动执行
                    # 基于 reload 基准上下文拷贝执行，addblock 不跨 tick 累积

                    # === 教师节祝福（9月10日）===
                    # 上课时根据当前科目显示对应老师的姓；非上课（$course$ 为空）不显示
                    # 预设初始均为空字符，按需在 set $surname 后填写老师姓氏
                    if %month% == 9
                      if %dayofmonth% == 10
                        if $course$ != ""
                          if $course$ == 语
                            set $surname
                          endif
                          if $course$ == 数
                            set $surname
                          endif
                          if $course$ == 英
                            set $surname
                          endif
                          if $course$ == 物
                            set $surname
                          endif
                          if $course$ == 化
                            set $surname
                          endif
                          if $course$ == 生
                            set $surname
                          endif
                          if $course$ == 政
                            set $surname
                          endif
                          if $course$ == 史
                            set $surname
                          endif
                          if $course$ == 地
                            set $surname
                          endif
                          addblock 教师节祝福 祝$surname$老师教师节快乐！
                        endif
                      endif
                    endif
                    """);
        }
        // swap.txt：换课记录（用 if 日期判断 + setcourse 调整；不存在则自动创建模板）
        File swap = new File(commandsDir, "swap.txt");
        if (!swap.exists()) {
            writeFile(swap, """
                    # 换课记录：if %date% == MM.dd + setcourse 索引 课程
                    # 索引从 0 开始：0=第一节（模板中行首的"一~六"是第一节课，不是表头）
                    #
                    # 示例：9月7日第2节课换为语文
                    # if %date% == 09.07
                    #   setcourse 2 语
                    # endif
                    #
                    """);
        }

        if (!schedule.exists()) {
            schedule.createNewFile();
            generateExample(schedule);
        }
        if (!monitor.exists()) {
            monitor.createNewFile();
            writeFile(monitor,
                    "值一生\n值二生\n值三生\n值四生\n值五生\n值六生\n");
        }
        if (!sstudy.exists()) {
            sstudy.createNewFile();
            writeFile(sstudy,
                    """
                            17:00 18:00
                            一 信 数 信 数 \\
                            二 数 英 物 化 \\
                            三 信 语 信 数 \\
                            """);
        }
        // night_study.txt：晚自习时间段（成对 HH:mm）。仅当本地开启晚自习且为周一~周五时生效
        if (!nstudy.exists()) {
            nstudy.createNewFile();
            writeFile(nstudy,
                    """
                            19:00 20:00
                            """);
        }
        if (!fullName.exists()) {
            fullName.createNewFile();
            writeFile(fullName,
                    """
                            ~=晚自习
                            $---$
                            |=恰饭
                             =课间
                            班=班会
                            旗=升旗
                            语=语文
                            数=数学
                            英=英语
                            物=物理
                            化=化学
                            生=生物
                            政=政治
                            史=历史
                            地=地理
                            信=信息
                            音=音乐
                            体=体育
                            美=美术
                            心=心理
                            习=自习
                            社=社团
                            团=社团
                            """);
        }
    }

    /**
     * 以 UTF-8 编码向文件写入文本（覆盖模式）。
     *
     * @param file    目标文件
     * @param content 文本内容
     * @throws IOException 写入失败时抛出
     */
    private static void writeFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    /**
     * 重新加载全部配置并刷新主窗口显示。
     *
     * @throws Exception 加载失败时抛出
     */
    public static void reload() throws Exception {
        boolean wasVisible = mw.isVisible();
        mw.setVisible(false);
        try {
            iifr = new IniFileReader(new FileInputStream(fullName));
            getLocals();

            dayNum = today.getDayOfWeek().getValue();
            switch (dayNum) {
                case 1:
                    dayName = "周一";
                    break;
                case 2:
                    dayName = "周二";
                    break;
                case 3:
                    dayName = "周三";
                    break;
                case 4:
                    dayName = "周四";
                    break;
                case 5:
                    dayName = "周五";
                    break;
                case 6:
                    dayName = "周六";
                    break;
                case 7:
                    dayName = "周日";
                    break;
                default:
                    throw new IllegalStateException("Unexpected day number: " + dayNum);
            }
            System.out.println("Today is " + dayNum + " : " + dayName);

            updateWeekTurn();

            // 加载基础课表（从文件 + 换课应用）
            int weekTurn = getLocal("WeekTurn", 1);
            String baseSchedule = getSchedule(today, true, weekTurn);
            String duty;
            if (dayNum != 7) {
                errorFile = "monitor.txt";
                try (Scanner sc = new Scanner(monitor, StandardCharsets.UTF_8)) {
                    for (int i = 1; i < dayNum; i++) {
                        if (sc.hasNextLine()) sc.nextLine();
                    }
                    duty = sc.hasNextLine() ? sc.nextLine() : "";
                }
            } else {
                // 周日课表格式："课表 : 值日生"，按 : 分割后 trim 首尾空格
                String[] parts = baseSchedule.split(":", 2);
                duty = parts.length > 1 ? parts[1].trim() : "";
                baseSchedule = parts[0].trim();
            }
            cont = new String[]{baseSchedule, duty};

            // 延迟课表覆盖（统一格式：时间行 + "课表 : 值日生"）。
            // 在 scheds 合并之前应用：手动 .delay 文件可同时替换课表、值日生与时间段
            DelayData delay = null;
            try {
                delay = loadDelaySchedule(today);
            } catch (IOException e) {
                System.err.println("[delay] 加载延迟课表失败: " + e.getMessage());
            }
            if (delay != null) {
                if (delay.times != null && delay.times.length > 0) {
                    times = delay.times;
                }
                baseSchedule = delay.schedule;
                if (delay.duty != null && !delay.duty.isEmpty()) {
                    duty = delay.duty;
                }
                cont = new String[]{baseSchedule, duty};
            }

            // 合并全部时间点；晚自习仅当本地开启且为周一~周五时并入课表时间数组。
            // 周日的课表由当天 delay 文件承载（时间行自包含），不再自动并入自习时段，
            // 避免快照时间行反复叠加自习时间导致无限增长
            String[] nightArr = (getLocal("HasNightStudy", 0) == 1 && dayNum >= 1 && dayNum <= 5)
                    ? night : new String[0];
            String[] selfArr = dayNum == 7 ? new String[0] : selfs;
            scheds = new String[times.length + selfArr.length + nightArr.length];
            System.arraycopy(times, 0, scheds, 0, times.length);
            System.arraycopy(selfArr, 0, scheds, times.length, selfArr.length);
            System.arraycopy(nightArr, 0, scheds, times.length + selfArr.length, nightArr.length);
            for (int i = 0; i < scheds.length; i++) {
                scheds[i] = scheds[i].replaceAll("[^0-9:]", "");
            }
            System.out.println("The schedule time is :" + Arrays.toString(scheds));

            // 自动快照今天课表：无手动创建的今天延迟课表时自动创建/覆盖（含 AUTO 标识，不参与覆盖）
            try {
                ensureAutoDelaySchedule(today, baseSchedule, duty, scheds);
            } catch (IOException e) {
                System.err.println("[delay] 自动创建延迟课表失败: " + e.getMessage());
            }

            // 执行命令脚本：load.txt 在后台线程执行（避免 delay/cmd 阻塞 EDT），
            // 先应用基础显示，脚本完成后异步刷新带脚本结果的显示
            String dateText = dayName + today.format(DateTimeFormatter.ofPattern("MM.dd"));
            SScriptInterpreter.Context ctx = SScriptInterpreter.Context.of(
                    dateText, baseSchedule, duty, today, LocalTime.now(), weekTurn, times);
            lastCtx = ctx;
            mw.setTextContent(ctx.blocks.toArray(new String[0]));
            executeLoadScriptAsync(ctx);
        } finally {
            mw.setVisible(wasVisible);
        }
    }

    /**
     * 周次自动递增：每周一将周次 +1，并在本周内标记已更新，避免重复递增。
     *
     * @throws IOException 保存配置失败时抛出
     */
    private static void updateWeekTurn() throws IOException {
        if (dayNum == 1 && getLocal("IsWeekTurnUpdated", 0) != 1) {
            int newWeek = getLocal("WeekTurn", 1) + 1;
            System.out.println("Updating week turn to " + newWeek);
            locals.put("WeekTurn", newWeek);
            locals.put("IsWeekTurnUpdated", 1);
            saveLocals();
        }
        if (dayNum != 1 && getLocal("IsWeekTurnUpdated", 0) == 1) {
            locals.put("IsWeekTurnUpdated", 0);
            saveLocals();
        }
    }

    /**
     * 获取指定日期的课表字符串。
     *
     * @param date       目标日期
     * @param exposeTime 是否同时加载时间点到 {@link #times} 等字段
     * @param weekTurn   当前周次（用于单双周课程）
     * @return 课表字符串（空格分隔；换课由命令脚本在 reload 时应用）
     * @throws Exception 读取课表文件失败时抛出
     */
    public static String getSchedule(LocalDate date, boolean exposeTime, int weekTurn) throws Exception {
        int dayOfWeek = date.getDayOfWeek().getValue();
        String sched;

        // 加载自习时间与晚自习时间（所有分支共用，确保 reload() 中不为 null）
        // self_study.txt 格式：
        //   第0行：自习时间（成对 HH:mm）
        //   第1~N行：轮次行（按周次轮转）—— 前 6 个 token 为周一~周六自习科目
        // night_study.txt 格式：
        //   第0行：晚自习时间段（成对 HH:mm，不随周次轮转）
        // 晚自习生效条件：本地 HasNightStudy=1 且当天为周一~周五
        selfs = new String[0];
        night = new String[0];
        errorFile = "self_study.txt";
        try {
            List<String> sLines = new ArrayList<>();
            try (Scanner sc = new Scanner(sstudy, StandardCharsets.UTF_8)) {
                while (sc.hasNextLine()) sLines.add(sc.nextLine());
            }
            selfs = sLines.isEmpty() ? new String[0] : sLines.get(0).split(" ");
        } catch (Exception e) {
            selfs = new String[0];
        }
        errorFile = "night_study.txt";
        try (Scanner sc = new Scanner(nstudy, StandardCharsets.UTF_8)) {
            night = sc.hasNextLine() ? sc.nextLine().split(" ") : new String[0];
        } catch (Exception e) {
            night = new String[0];
        }

        if (dayOfWeek == 7) {
            // 周日：由当天的延迟课表提供（统一格式：时间行 + "课表 : 值日生"）。
            // 无独立 sunday.txt；delay 目录尚无今天的文件时使用兜底模板（reload 会自动创建）
            errorFile = "delay/当天.delay";
            File delayFile = findDelayFile(date, true);
            if (delayFile != null) {
                try (Scanner sc = new Scanner(delayFile, StandardCharsets.UTF_8)) {
                    if (exposeTime) times = sc.nextLine().split(" ");
                    else sc.nextLine();
                    sched = sc.nextLine();
                }
            } else {
                if (exposeTime) times = "01:00 02:00 03:00 04:00 05:00 06:00".split(" ");
                sched = "请 指 定 今 天 | 的 课 表 : 值日生";
            }
        } else if (dayOfWeek == 6) {
            // 周六
            errorFile = "saturday.txt";
            if (!saturday.exists()) {
                saturday.createNewFile();
                generateExample(saturday);
            }
            try (Scanner sc = new Scanner(saturday, StandardCharsets.UTF_8)) {
                if (exposeTime) times = sc.nextLine().split(" ");
                else sc.nextLine();
                try {
                    sched = getLine(weekTurn, saturday, 1);
                } catch (IOException ex) {
                    // 轮次行被删光（仅剩时间行）时显示空课表，不崩溃
                    sched = "";
                }
            }
        } else {
            // 周一至周五
            errorFile = "schedule.txt";
            try (Scanner sc = new Scanner(schedule, StandardCharsets.UTF_8)) {
                if (exposeTime) times = sc.nextLine().split(" ");
                else sc.nextLine();
                for (int i = 1; i < dayOfWeek; i++) {
                    sc.nextLine();
                }
                sched = sc.nextLine();
            }
        }

        // 取当天的自习科目（周日无自习，不追加分隔符）
        String selfStudy = "";
        if (dayOfWeek != 7) {
            String[] weekSelfStudy = getLine(weekTurn, sstudy, 1).split(" ");
            if (dayOfWeek - 1 < weekSelfStudy.length) {
                selfStudy = weekSelfStudy[dayOfWeek - 1];
            }
            if (!selfStudy.equals("\\")) {
                selfStudy = " | " + selfStudy;
            } else {
                selfStudy = " " + selfStudy;
            }
        }

        // 单双周课程解析（如 "单-双"）
        if (sched.contains("-")) {
            String[] parseSched = sched.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String s : parseSched) {
                if (s.contains("-")) {
                    String[] sp = s.split("-");
                    sb.append(sp[(weekTurn - 1) % sp.length]).append(" ");
                } else {
                    sb.append(s).append(" ");
                }
            }
            sched = sb.toString().trim();
        }

        sched += selfStudy;
        if (dayOfWeek != 6 && dayOfWeek != 7 && getLocal("HasNightStudy", 0) == 1 && night.length >= 2) {
            sched += " ~";
        }

        // 换课由命令脚本（swap.txt，经 load.txt 调用）在 reload 时应用
        return sched;
    }

    /**
     * 延迟课表数据：包含时间段、课表内容与可选的值日生覆盖。
     */
    private record DelayData(String[] times, String schedule, String duty) {
    }

    /**
     * 查找目标日期的延迟课表文件。
     *
     * <p>按<b>文件名前缀</b>匹配（{@code YYYY-MM-DD*}）。排除自动快照
     * （{@code _auto} 后缀或含 {@code AUTO} 标记行）后即为手动文件。
     * {@code includeAuto} 为 true 时，无手动文件则回退到自动快照（周日课表来源）。</p>
     *
     * @param date        目标日期
     * @param includeAuto 是否允许回退到自动快照文件
     * @return 匹配的 .delay 文件；无匹配返回 null
     */
    private static File findDelayFile(LocalDate date, boolean includeAuto) {
        if (!delayDir.exists() || !delayDir.isDirectory()) {
            return null;
        }
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        File[] files = delayDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".delay"));
        if (files == null || files.length == 0) {
            return null;
        }
        File manual = null;
        long manualMod = -1;
        File auto = null;
        for (File f : files) {
            String name = f.getName();
            if (!name.startsWith(dateStr)) {
                continue;
            }
            if (name.endsWith("_auto.delay") || isAutoDelayFile(f)) {
                if (auto == null || f.lastModified() > auto.lastModified()) {
                    auto = f;
                }
            } else if (manual == null || f.lastModified() > manualMod) {
                manual = f;
                manualMod = f.lastModified();
            }
        }
        if (manual != null) {
            return manual;
        }
        return includeAuto ? auto : null;
    }

    /**
     * 加载指定日期的延迟课表（仅手动文件）。
     *
     * <p>统一格式（类周日模板，两行）：</p>
     * <ol>
     *   <li>时间段（空格分隔的 HH:mm 成对）</li>
     *   <li>课表内容 : 值日生（冒号分隔，两侧各自 trim）</li>
     * </ol>
     * <p>自动快照文件（{@code _auto.delay} / 含 {@code AUTO} 行）不参与覆盖。</p>
     *
     * @param date 目标日期
     * @return 延迟课表数据；无匹配时返回 null
     * @throws IOException 读取文件失败时抛出
     */
    private static DelayData loadDelaySchedule(LocalDate date) throws IOException {
        File f = findDelayFile(date, false);
        if (f == null) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String timesLine = br.readLine();
            String scheduleLine = br.readLine();
            if (scheduleLine == null || scheduleLine.trim().isEmpty()) {
                return null;
            }
            String[] parts = scheduleLine.split(":", 2);
            String schedule = parts[0].trim();
            String duty = parts.length > 1 ? parts[1].trim() : "";
            String[] timesArr = (timesLine != null && !timesLine.trim().isEmpty())
                    ? timesLine.trim().split("\\s+") : new String[0];
            return new DelayData(timesArr, schedule, duty);
        }
    }

    /**
     * 判断 .delay 文件是否为自动创建的快照（内含 {@code AUTO} 标记行）。
     */
    private static boolean isAutoDelayFile(File f) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if ("AUTO".equals(line.trim())) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            // 读取失败按手动文件处理（不影响主流程）
        }
        return false;
    }

    /**
     * 确保今天的延迟课表存在：若 ./delay/ 下<b>没有手动创建</b>的今天延迟课表，
     * 则自动创建（已存在自动快照时覆盖）今天的延迟课表。
     * 自动快照文件含 {@code AUTO} 标识行，不参与延迟课表覆盖逻辑。
     * 格式（类周日模板）：第 1 行时间段（含自习/晚自习），第 2 行 "课表 : 值日生"。
     *
     * @param date     目标日期
     * @param schedule 当前课表字符串
     * @param duty     当前值日生（可为 null）
     * @param timesArr 课表时间段数组（含自习/晚自习，可为 null）
     * @throws IOException 写入失败时抛出
     */
    private static void ensureAutoDelaySchedule(LocalDate date, String schedule, String duty,
                                                String[] timesArr) throws IOException {
        if (!delayDir.exists()) {
            delayDir.mkdirs();
        }
        String todayStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        File[] files = delayDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".delay"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                // 手动文件：文件名以今天日期开头，且非自动快照（_auto 后缀或含 AUTO 行）
                if (name.startsWith(todayStr) && !name.endsWith("_auto.delay")
                        && !isAutoDelayFile(f)) {
                    // 存在手动创建的今天延迟课表 → 不自动创建/覆盖
                    return;
                }
            }
        }
        // 统一格式（类周日模板）：第 1 行时间段，第 2 行 "课表 : 值日生"，第 3 行 AUTO 标识
        StringBuilder sb = new StringBuilder();
        if (timesArr != null && timesArr.length > 0) {
            sb.append(String.join(" ", timesArr));
        }
        sb.append('\n')
                .append(schedule == null ? "" : schedule)
                .append(" : ").append(duty == null ? "" : duty).append('\n')
                .append("AUTO").append('\n');
        File auto = new File(delayDir, todayStr + "_auto.delay");
        writeFile(auto, sb.toString());
        System.out.println("[delay] 已自动创建今天的延迟课表: " + auto.getName());
    }

    /**
     * 执行 ./commands/load.txt（每次 {@link #reload()} 时加载）。
     *
     * <p>其余命令文件<b>不会自动执行</b>，需在脚本中用 {@code run <文件名>}
     * 命令显式调用；tick.txt 由 {@link #executeTickScript()} 在每次更新循环中执行。</p>
     *
     * @param ctx 脚本执行上下文（含课程列表、显示块、日期时间等）
     * @throws IOException 读取脚本失败时抛出
     */
    private static void executeLoadScript(SScriptInterpreter.Context ctx) throws IOException {
        File load = new File(commandsDir, "load.txt");
        if (!load.exists() || !load.isFile()) {
            return;
        }
        new SScriptInterpreter().execute(readScriptLines(load), ctx);
    }

    /**
     * 在后台线程异步执行 load.txt，完成后在 EDT 上刷新主课条与换课窗口。
     * 后台执行避免 delay / cmd / 键鼠操作阻塞 EDT（幕布提示期间界面保持响应）。
     *
     * @param ctx reload 时创建的脚本上下文
     */
    private static void executeLoadScriptAsync(SScriptInterpreter.Context ctx) {
        Thread t = new Thread(() -> {
            try {
                executeLoadScript(ctx);
            } catch (Exception e) {
                System.err.println("[脚本] load 执行失败: " + e.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (mw != null) {
                            mw.setTextContent(ctx.blocks.toArray(new String[0]));
                        }
                        if (MainFrame.mf != null) {
                            MainFrame.mf.reload();
                        }
                    } catch (Exception ex) {
                        System.err.println("[脚本] load 结果刷新失败: " + ex.getMessage());
                    }
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * 执行 ./commands/tick.txt（每次更新循环加载）。
     *
     * <p>基于 reload 的基础上下文拷贝独立执行（每次从基准状态开始，
     * addblock 等结果仅作用于当次显示、不会跨 tick 累积），
     * 完成后在 EDT 上刷新主课条。防重入：上一次未跑完时跳过本次。</p>
     */
    private static void executeTickScript() {
        if (tickRunning) {
            return;
        }
        File tickFile = new File(commandsDir, "tick.txt");
        if (!tickFile.exists() || !tickFile.isFile()) {
            return;
        }
        SScriptInterpreter.Context base = lastCtx;
        if (base == null) {
            return;
        }
        tickRunning = true;
        Thread t = new Thread(() -> {
            try {
                SScriptInterpreter.Context tickCtx = base.copy();
                new SScriptInterpreter().execute(readScriptLines(tickFile), tickCtx);
                SwingUtilities.invokeLater(() -> {
                    if (mw != null) {
                        mw.setTextContent(tickCtx.blocks.toArray(new String[0]));
                    }
                });
            } catch (Exception e) {
                System.err.println("[脚本] tick 执行失败: " + e.getMessage());
            } finally {
                tickRunning = false;
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * 以 UTF-8 读取脚本文件的全部行。
     *
     * @param f 脚本文件
     * @return 行列表
     * @throws IOException 读取失败时抛出
     */
    private static List<String> readScriptLines(File f) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * 从文件中按周次取一行（跳过前 skip 行后循环取第 lineNumber 行）。
     *
     * @param lineNumber 行号（从 1 开始，超出行数时取模循环）
     * @param target     目标文件
     * @param skip       跳过的行数（如时间行）
     * @return 对应行的内容
     * @throws IOException 文件为空或读取失败时抛出
     */
    public static String getLine(int lineNumber, File target, int skip) throws IOException {
        List<String> lines = new ArrayList<>();
        try (Scanner scanner = new Scanner(target, StandardCharsets.UTF_8)) {
            for (int i = 0; i < skip; i++) {
                if (scanner.hasNextLine()) scanner.nextLine();
            }
            while (scanner.hasNextLine()) {
                lines.add(scanner.nextLine());
            }
        }
        if (lines.isEmpty()) {
            throw new IOException("File is empty: " + target);
        }
        return lines.get((lineNumber - 1) % lines.size());
    }

    /**
     * 从 .local 文件读取本地配置。
     *
     * @throws IOException 读取失败时抛出
     */
    public static void getLocals() throws IOException {
        locals.clear();
        if (!local.exists()) {
            local.createNewFile();
            writeFile(local, "1\n1\n1\n1\n47\n");
        }
        try (Scanner sc = new Scanner(local, StandardCharsets.UTF_8)) {
            locals.put("WeekTurn", sc.nextInt());
            locals.put("IsWeekTurnUpdated", sc.nextInt());
            locals.put("HasNightStudy", sc.nextInt());
            locals.put("AprilFool", sc.nextInt());
            locals.put("Theme", sc.nextInt());
        }
    }

    /**
     * 将本地配置保存到 .local 文件。
     *
     * @throws IOException 写入失败时抛出
     */
    public static void saveLocals() throws IOException {
        try (FileWriter fw = new FileWriter(local, StandardCharsets.UTF_8)) {
            fw.write(getLocal("WeekTurn", 1) + "\n");
            fw.write(getLocal("IsWeekTurnUpdated", 0) + "\n");
            fw.write(getLocal("HasNightStudy", 0) + "\n");
            fw.write(getLocal("AprilFool", 0) + "\n");
            fw.write(getLocal("Theme", 0) + "\n");
        }
    }

    /**
     * 安全读取本地配置，避免拆箱 NPE。
     *
     * @param key          配置键
     * @param defaultValue 默认值（键不存在时返回）
     * @return 配置值
     */
    public static int getLocal(String key, int defaultValue) {
        Integer value = locals.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 生成示例课表文件（周一至周五，含单双周示例）。
     *
     * @param target 目标文件
     * @throws IOException 写入失败时抛出
     */
    public static void generateExample(File target) throws IOException {
        writeFile(target,
                """
                        01:30 02:00 03:30 04:50 05:00 06:25 07:00 08:10 09:25 10:00 10:30 11:00 12:00 13:00
                        一 信 数 信 | 数 信
                        二 数 英 物 | 化 生
                        三 信 单-双 信 | 数 信
                        四 英 物 化 | 社 团
                        五 信 数 信 | 数 信
                        """);
    }
}
