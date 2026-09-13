import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

/**
 * 主悬浮课条窗口。
 *
 * <p>始终位于屏幕顶部的无边框透明窗口，显示：</p>
 * <ul>
 *   <li>当前星期与日期；</li>
 *   <li>当日课程表（当前进行中的课程高亮并显示倒计时）；</li>
 *   <li>值日生信息。</li>
 * </ul>
 *
 * <p>交互特性：</p>
 * <ul>
 *   <li>鼠标拖拽移动窗口，靠近顶部自动吸附；</li>
 *   <li>单击隐藏并弹出通知面板，连续点击超过 3 次触发保护提示；</li>
 *   <li>从屏幕下方释放时召唤屏幕便签（最多 6 个）；</li>
 *   <li>支持主题色调节与愚人节彩蛋。</li>
 * </ul>
 */
public class MainWindow extends JWindow {

    /** 主显示字体。 */
    public static final Font f = new Font("宋体", Font.BOLD, 20);

    /** 全局共享的通知面板实例。 */
    public static NoticeWindow cd = new NoticeWindow("", 10000);

    /** 窗口高度（固定）。 */
    private static final int WINDOW_HEIGHT = 30;

    /** 滑入 / 滑出动画每帧移动像素。 */
    private static final int SLIDE_STEP = 3;

    /** 吸附动画每帧移动像素。 */
    private static final int SNAP_STEP = 5;

    /** 连续点击重置间隔（毫秒）。 */
    private static final int CLICK_RESET_DELAY = 60_000;

    /** 召唤便签的 Y 坐标阈值。 */
    private static final int COMMENT_SUMMON_Y = 500;

    /** 最多同时存在的便签数量。 */
    private static final int MAX_COMMENTS = 6;

    // ---- 当前课程状态 ----
    /** 当前课程在课表中的索引，-1 表示无。 */
    public int onClassNumber = -1;
    /** 当前课程进度 0~1。 */
    public float onClassProgress = 0.0f;
    /** 当前课程倒计时文本。 */
    public String onClassCountdown = "";

    // ---- 显示内容 ----
    /** 显示文本：[0]=日期, [1]=课表, [2]=值日。 */
    public String[] textContent;

    // ---- 主题 ----
    /** 主题色 RGB 整数值。 */
    public int themeColor;
    /** 主题色对象。 */
    public Color theme;
    /** 愚人节动态色相。 */
    private int aprilFoolHue;

    // ---- 交互状态 ----
    /** 连续点击计数。 */
    public static int clickTimes = 0;
    /** 全局便签计数。 */
    public static short commentNumber = 0;

    // ---- 布局计算缓存 ----
    private int grayX, grayLength, lastLength;
    private final Point dragPoint = new Point();
    private boolean snapped = true;

    // ---- 动画与定时器 ----
    private Timer slideTimer;
    private Timer snapTimer;
    private Timer clickResetTimer;
    private boolean commentSummoned = false;

    /**
     * 格式化文本段：strip 后的文本 + 颜色 + 背景色 + 样式标志。
     * background 为 null 表示无背景色。
     */
    public record Seg(String text, Color color, Color background, boolean bold, boolean italic,
                      boolean underline, boolean strike) {
    }

    static {
        cd.setInvokeOnClose(e -> {
            if (Main.mw != null) {
                Main.mw.setVisible(true);
                FrameTray.setLabel();
            }
        });
    }

    /**
     * 构造主窗口，初始化位置、透明背景与鼠标交互。
     */
    public MainWindow() {
        System.out.println("Main window was created.");
        setBounds(200, -WINDOW_HEIGHT, 400, WINDOW_HEIGHT);
        setBackground(new Color(0, 0, 0, 0));
        lastLength = getWidth();

        setupClickResetTimer();
        setupMouseInteractions();
        setupSlideTimer();

        // 初始定位到屏幕水平中央
        setLocation(
                (Main.tk.getScreenSize().width / 2) - (getWidth() / 2),
                getY()
        );

        // 修复：启动时立即初始化主题色。
        // 原实现只在主题色相滑块 changeListener 中调用 updateTheme()，
        // 而 hueSelector.setValue(相同值) 不会触发 change 事件，
        // 导致 theme 一直为 null，主题色高亮/进度条不显示，直到手动拖动滑块才刷新。
        updateTheme();
    }

    /**
     * 设置连续点击重置定时器：60 秒无新点击则清零计数。
     */
    private void setupClickResetTimer() {
        clickResetTimer = new Timer(CLICK_RESET_DELAY, e -> {
            clickTimes = 0;
            System.out.println("60 sec remains unclicked. Resetting the clickTimes");
        });
        clickResetTimer.setRepeats(false);
    }

    /**
     * 设置鼠标拖拽与点击交互。
     */
    private void setupMouseInteractions() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                dragPoint.x = e.getX();
                dragPoint.y = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragPoint.x = -1;
                dragPoint.y = -1;
                animateSnapToTop();
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragPoint.x == -1 || dragPoint.y == -1) {
                    return;
                }
                int locX = getX() + e.getX() - dragPoint.x;
                int locY = getY() + e.getY() - dragPoint.y;
                int screenWidth = Main.tk.getScreenSize().width;

                // 水平边界
                if (locX < 0) locX = 0;
                if (locX > screenWidth - getWidth()) locX = screenWidth - getWidth();

                // 垂直：靠近顶部则吸附
                if (locY < WINDOW_HEIGHT) {
                    setLocation(locX, 0);
                    snapped = true;
                } else {
                    setLocation(locX, locY);
                    snapped = false;
                }
                repaint();
            }
        });
    }

    /**
     * 处理窗口点击：隐藏主窗口、显示通知面板，连续点击过多时提示。
     */
    private void handleClick() {
        clickResetTimer.restart();
        clickTimes++;
        System.out.println("Window clicked " + clickTimes);

        setVisible(false);
        if (clickTimes > 2) {
            clickResetTimer.stop();
            System.out.println("Clicked " + clickTimes + ", hiding with warning...");
            new NoticeWindow("主窗口已隐藏，请前往系统托盘使其显示", 10000).setVisible(true);
            return;
        }
        cd.setVisible(true);
        FrameTray.setLabel();
    }

    /**
     * 设置滑入 / 滑出动画定时器。
     */
    private void setupSlideTimer() {
        slideTimer = new Timer(5, e -> {
            int y = getY();
            // 方向由当前位置判断：y<0 且目标为可见则向下滑入；目标为隐藏则向上滑出
            // 这里用一个标志字段区分方向
            if (slideDirection > 0) {
                y += SLIDE_STEP;
                if (y >= 0) {
                    y = 0;
                    slideTimer.stop();
                }
                setLocation(getX(), y);
            } else {
                y -= SLIDE_STEP;
                if (y <= -getHeight()) {
                    y = -getHeight();
                    slideTimer.stop();
                    super.setVisible(false);
                    FrameTray.setLabel();
                }
                setLocation(getX(), y);
            }
        });
    }

    /** 滑入方向：>0 向下滑入，<0 向上滑出。 */
    private int slideDirection = 0;

    /**
     * 用 Timer 动画将窗口吸附到顶部，过程中检测便签召唤。
     */
    private void animateSnapToTop() {
        if (snapTimer != null && snapTimer.isRunning()) {
            snapTimer.stop();
        }
        commentSummoned = false;
        snapTimer = new Timer(5, e -> {
            int y = getY();
            if (y <= 0) {
                setLocation(getX(), 0);
                snapped = true;
                commentSummoned = false;
                snapTimer.stop();
                return;
            }
            // 从屏幕下方经过时召唤便签（仅一次）
            if (y > COMMENT_SUMMON_Y && !commentSummoned) {
                commentSummoned = true;
                summonComment();
            }
            setLocation(getX(), y - SNAP_STEP);
        });
        snapTimer.start();
    }

    /**
     * 召唤一个屏幕便签，超过上限时提示。
     */
    private void summonComment() {
        if (commentNumber < MAX_COMMENTS) {
            CommentWindow cw = new CommentWindow();
            cw.setSize(320, 180);
            cw.setLocation(getX(), getY() - 180);
            cw.setAlwaysOnTop(true);
            cw.setVisible(true);
            cw.toFront();
            commentNumber++;
            System.out.println("Comments summoned, total: " + commentNumber);
        } else {
            new NoticeWindow("最多支持6个屏幕评论", 10000).setVisible(true);
        }
    }

    /**
     * 设置显示文本内容。
     *
     * @param textContent [日期, 课表, 值日]
     */
    public void setTextContent(String... textContent) {
        this.textContent = textContent;
        repaint();
    }

    /**
     * 设置当前课程索引。
     *
     * @param onClassNumber 课程索引，-1 表示无
     */
    public void setOnClassNumber(int onClassNumber) {
        this.onClassNumber = onClassNumber;
        repaint();
    }

    /**
     * 设置当前课程进度。
     *
     * @param onClassProgress 0~1
     */
    public void setOnClassProgress(float onClassProgress) {
        this.onClassProgress = onClassProgress;
        repaint();
    }

    /**
     * 设置当前课程倒计时文本。
     *
     * @param onClassCountdown 倒计时文本
     */
    public void setOnClassCountdown(String onClassCountdown) {
        this.onClassCountdown = onClassCountdown;
        repaint();
    }

    @Override
    public void setVisible(boolean visible) {
        setAlwaysOnTop(visible);
        if (slideTimer.isRunning()) {
            slideTimer.stop();
        }
        if (!visible) {
            if (!super.isVisible()) {
                return;
            }
            slideDirection = -1;
            slideTimer.start();
        } else {
            if (super.isVisible()) {
                return;
            }
            aprilFoolHue = getLocalTheme();
            MainFrame.hueSelector.setValue(aprilFoolHue);
            updateTheme();
            super.setVisible(true);
            setLocation(getX(), -getHeight());
            slideDirection = 1;
            slideTimer.start();
            FrameTray.setLabel();
        }
    }

    /**
     * 安全读取主题色配置，避免 NPE。
     *
     * @return 主题色值（0~100）
     */
    private int getLocalTheme() {
        Integer value = Main.locals.get("Theme");
        return value != null ? value : 0;
    }

    /**
     * 安全读取愚人节开关。
     *
     * @return 1 表示启用
     */
    private int getAprilFoolFlag() {
        Integer value = Main.locals.get("AprilFool");
        return value != null ? value : 0;
    }

    /**
     * 更新主题色。愚人节玩笑已改为课间竖直镜像课表显示（见 paint()），此处正常读取主题色。
     */
    public void updateTheme() {
        aprilFoolHue = getLocalTheme();
        themeColor = Color.HSBtoRGB(aprilFoolHue / 100.0f, 1.0f, 1.0f);
        theme = new Color(themeColor);
    }

    @Override
    public void paint(Graphics g) {
        if (textContent == null || textContent.length < 3 || textContent[1] == null) {
            super.paint(g);
            return;
        }

        // 课表中 "\" 表示无课空位，渲染时去除；" ~"（晚自习标记）压缩为空格（保持原显示行为）
        String schedule = textContent[1].replace("\\", "").replace(" ~", " ");
        // 解析格式化 token 为段（token 不参与字符计数，课程索引与无格式课表一一对应）
        List<Seg> schedSegs = parseSegments(schedule);

        // 愚人节玩笑：仅在课间（onClassNumber == -1）竖直镜像课表显示
        boolean isAprilFools = getAprilFoolFlag() == 1
                && textContent[0] != null
                && textContent[0].endsWith("04.01")
                && onClassNumber == -1;

        // 在当前课程位置插入全称 + 倒计时（定位在 strip 后文本上，与课程索引对齐）
        // 已上过的课（字符索引 < onClassNumber）强制灰色显示；
        // 尚未上过的课（字符索引 > onClassNumber，含正在上的当前课）追加加粗（等同 #b）
        boolean cdAvailable = false;
        int highlightStart = 0;
        int highlightWidth = 0;
        List<Seg> displaySegs = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean curPast = false;   // 当前累积段是否包含已上课字符
        boolean curFuture = false; // 当前累积段是否包含未上课字符
        int i = 0;
        for (Seg seg : schedSegs) {
            for (int k = 0; k < seg.text.length(); k++) {
                if (i == onClassNumber) {
                    if (cur.length() > 0) {
                        Color c = curPast ? Color.GRAY : seg.color;
                        displaySegs.add(new Seg(cur.toString(), c, seg.background,
                                seg.bold || curFuture, seg.italic, seg.underline, seg.strike));
                        cur.setLength(0);
                        curPast = false;
                        curFuture = false;
                    }
                    String onClassName = Main.iifr != null
                            ? Main.iifr.get(String.valueOf(seg.text.charAt(k))) : null;
                    if (onClassName == null) {
                        onClassName = String.valueOf(seg.text.charAt(k));
                    }
                    String insert = " " + onClassName + " " + onClassCountdown + " ";
                    highlightStart = widthOf(displaySegs) + textWidth(cur.toString(), seg);
                    // 当前课程（正在上）加粗
                    displaySegs.add(new Seg(insert, Color.BLACK, null, true, false, false, false));
                    highlightWidth = widthOf(displaySegs) - highlightStart;
                    cdAvailable = true;
                } else {
                    cur.append(seg.text.charAt(k));
                    if (i < onClassNumber) {
                        curPast = true;
                    } else {
                        curFuture = true;
                    }
                }
                i++;
            }
            if (cur.length() > 0) {
                Color c = curPast ? Color.GRAY : seg.color;
                displaySegs.add(new Seg(cur.toString(), c, seg.background,
                        seg.bold || curFuture, seg.italic, seg.underline, seg.strike));
                cur.setLength(0);
                curPast = false;
                curFuture = false;
            }
        }

        // 倒计时索引超出课表字符范围（课表文本较短）时，在末尾追加当前时段倒计时，
        // 确保自习/晚自习等时段计时不消失。排字组（Main.getSchedule）已负责在课表末尾
        // 自动追加 [~] 等标记，此处不再按位置区分自习/晚自习/课程，直接提取课表末尾
        // token 的全称（key-value，无映射时回退到 token 本身）
        if (onClassNumber != -1 && i <= onClassNumber) {
            String onClassName = lastScheduleFullName();
            String insert = " " + onClassName + " " + onClassCountdown + " ";
            highlightStart = widthOf(displaySegs);
            // 当前时段（自习/晚自习）加粗
            displaySegs.add(new Seg(insert, Color.BLACK, null, true, false, false, false));
            highlightWidth = widthOf(displaySegs) - highlightStart;
            cdAvailable = true;
        }

        super.paint(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 愚人节竖直镜像：保存原始变换，沿水平轴翻转（上下颠倒）
        AffineTransform oldAt = null;
        if (isAprilFools) {
            oldAt = g2.getTransform();
            g2.translate(0, getHeight());
            g2.scale(1, -1);
        }

        int x = 0;
        try {
            // 依次渲染：日期 | 课表 | 值日
            int[] dateSize = render(g2, textContent[0], x, snapped);
            x += dateSize[0] + 10;

            int[] schedSize = renderSegments(g2, displaySegs, x, snapped, true);

            // 当前课程高亮背景 + 进度条
            if (cdAvailable && theme != null) {
                int highlightX = x + highlightStart + 4;
                g2.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 80));
                g2.fillRect(highlightX, 0, highlightWidth - 4, schedSize[1]);
                // 进度条（更亮的同色覆盖层）
                g2.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 160));
                g2.fillRect(highlightX, 0, (int) ((highlightWidth - 4) * onClassProgress), schedSize[1]);
                // 进度条之上重新绘制课表文字（保留格式颜色，不重画背景以免覆盖进度条）
                renderSegments(g2, displaySegs, x, snapped, false);
            }

            x += schedSize[0] + 10;

            // 其余显示块：值日生（index 2）+ 脚本添加的额外块（index 3+）
            for (int j = 2; j < textContent.length; j++) {
                int[] blockSize = render(g2, textContent[j], x, snapped);
                x += blockSize[0] + 10;
            }
        } finally {
            if (oldAt != null) {
                g2.setTransform(oldAt);
            }
        }

        // 根据内容总宽度调整窗口大小，并保持水平中心不变
        int newWidth = x - 10;
        if (newWidth != getWidth()) {
            int centerX = getX() + lastLength / 2;
            setSize(newWidth, WINDOW_HEIGHT);
            setLocation(centerX - newWidth / 2, getY());
            lastLength = newWidth;
        }
    }

    /**
     * 提取课表文本末尾 token 的全称（key-value 映射）。
     *
     * <p>排字组（{@code Main.getSchedule}）负责在课表末尾自动追加 {@code ~}（晚自习）、
     * 自习科目等标记，末尾 token 即当前计时时段对应的课程；按 full_name.txt 的
     * 简称→全称映射取值，无映射时回退到 token 本身。</p>
     *
     * @return 末尾 token 的全称；课表为空时返回 ""
     */
    private String lastScheduleFullName() {
        String sched = textContent != null && textContent.length > 1 ? textContent[1] : null;
        if (sched == null) return "";
        String stripped = SScriptInterpreter.stripFormat(sched).trim();
        if (stripped.isEmpty()) return "";
        int sp = stripped.lastIndexOf(' ');
        String token = sp >= 0 ? stripped.substring(sp + 1) : stripped;
        String full = Main.iifr != null ? Main.iifr.get(token) : null;
        return full != null ? full : token;
    }

    /**
     * 渲染一段带圆角背景的文本（支持 Minecraft 风格格式化 token）。
     *
     * @param g       图形上下文
     * @param text    文本（可含 {@code #} 格式码）
     * @param x       起始 X
     * @param snapped 是否吸附在顶部（吸附时背景向上延伸隐藏上边框）
     * @return [文本宽度, 文本高度]
     */
    public int[] render(Graphics2D g, String text, int x, boolean snapped) {
        return renderSegments(g, parseSegments(text), x, snapped, true);
    }

    /**
     * 按格式化段渲染一段带圆角背景的文本。
     *
     * @param g       图形上下文
     * @param segs    格式化段列表
     * @param x       起始 X
     * @param snapped 是否吸附在顶部
     * @param drawBg  是否绘制背景（圆角面板背景与段格式背景）；高亮/进度条之上重绘文字时传 false
     * @return [文本宽度, 文本高度]
     */
    public int[] renderSegments(Graphics2D g, List<Seg> segs, int x, boolean snapped, boolean drawBg) {
        if (segs == null || segs.isEmpty()) {
            return new int[]{0, 0};
        }
        FontMetrics fm = g.getFontMetrics(f);
        int w = widthOf(segs) + 10;
        int h = fm.getHeight();

        if (drawBg) {
            // 背景色：吸附在顶部时用纯白，脱离顶部时用浅灰（视觉区分）
            Color bg = getY() < COMMENT_SUMMON_Y ? Color.WHITE : new Color(208, 208, 208);
            g.setColor(bg);
            int bgY = snapped ? -10 : 0;
            int bgH = h + (snapped ? 10 : 0);
            g.fillRoundRect(x, bgY, w, bgH, 10, 10);
        }

        // 按段绘制：先画背景色（如有），再以各自颜色与字体样式绘制文字，累加 X 偏移
        int cx = x + 4;
        for (Seg s : segs) {
            if (s.text.isEmpty()) {
                continue;
            }
            Font sf = f.deriveFont(styleOf(s));
            FontMetrics sfm = g.getFontMetrics(sf);
            int tw = sfm.stringWidth(s.text);
            if (drawBg && s.background != null) {
                // 用基础字体 metrics 定位背景（派生粗/斜体 metrics 对中文字体 ascent 偏小，会向下偏移）
                g.setColor(s.background);
                g.fillRect(cx, 20 - fm.getAscent(), tw, fm.getHeight());
            }
            g.setColor(s.color);
            g.setFont(sf);
            g.drawString(s.text, cx, 20);
            if (s.underline) {
                g.drawLine(cx, 22, cx + tw, 22);
            }
            if (s.strike) {
                int mid = 20 - fm.getAscent() / 3;
                g.drawLine(cx, mid, cx + tw, mid);
            }
            cx += tw;
        }
        g.setFont(f);
        return new int[]{w, h};
    }

    /**
     * 将文本解析为格式化段：{@code #cRRGGBB} 字体颜色、{@code #gRRGGBB} 背景色（16 进制色码），
     * {@code #i} 斜体、{@code #b} 粗体、{@code #u} 下划线、{@code #d} 删除线、{@code #r} 重置。
     * 格式 token 不进入段文本；不满足格式规则（如 {@code #c} 后不足 6 位色码）按普通文本。
     */
    public static List<Seg> parseSegments(String text) {
        List<Seg> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        Color color = Color.BLACK;
        Color background = null;
        boolean bold = false, italic = false, underline = false, strike = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '#' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                // #c / #g + 6 位 16 进制色码 = 字体颜色 / 背景色
                if ((code == 'c' || code == 'g') && i + 8 <= text.length()
                        && isHex6(text.substring(i + 2, i + 8))) {
                    flushSeg(out, sb, color, background, bold, italic, underline, strike);
                    Color col = hexColor(text.substring(i + 2, i + 8));
                    if (code == 'c') {
                        color = col;
                    } else {
                        background = col;
                    }
                    i += 7; // 跳过 # + c/g + 6 位色码
                    continue;
                }
                // 单字符样式码（toggle：重复出现则取消对应格式，符合 Minecraft 行为）
                if (code == 'i' || code == 'b' || code == 'u' || code == 'd' || code == 'r') {
                    flushSeg(out, sb, color, background, bold, italic, underline, strike);
                    switch (code) {
                        case 'i': italic = !italic; break;
                        case 'b': bold = !bold; break;
                        case 'u': underline = !underline; break;
                        case 'd': strike = !strike; break;
                        case 'r':
                            color = Color.BLACK;
                            background = null;
                            bold = italic = underline = strike = false;
                            break;
                    }
                    i++; // 跳过样式码字符
                    continue;
                }
            }
            sb.append(c);
        }
        flushSeg(out, sb, color, background, bold, italic, underline, strike);
        return out;
    }

    /** 是否为 6 位 16 进制色码。 */
    private static boolean isHex6(String s) {
        if (s.length() != 6) {
            return false;
        }
        for (int k = 0; k < 6; k++) {
            char ch = s.charAt(k);
            boolean hex = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    /** 解析 6 位 16 进制色码；非法返回 null。 */
    private static Color hexColor(String hex) {
        try {
            return new Color(Integer.parseInt(hex, 16));
        } catch (Exception e) {
            return null;
        }
    }

    /** 将累积文本按当前状态收尾为一段。 */
    private static void flushSeg(List<Seg> out, StringBuilder sb, Color color, Color background,
                                 boolean bold, boolean italic, boolean underline, boolean strike) {
        if (sb.length() > 0) {
            out.add(new Seg(sb.toString(), color, background, bold, italic, underline, strike));
            sb.setLength(0);
        }
    }

    /** 段的字体样式（基础字体已是粗体，l 仅保持粗体、o 追加斜体）。 */
    private static int styleOf(Seg s) {
        int style = 0;
        if (s.bold) {
            style |= Font.BOLD;
        }
        if (s.italic) {
            style |= Font.ITALIC;
        }
        return style;
    }

    /** 段的渲染宽度（按其字体样式计算）。 */
    private int segWidth(Seg s) {
        return getFontMetrics(f.deriveFont(styleOf(s))).stringWidth(s.text);
    }

    /** 段列表的总渲染宽度。 */
    private int widthOf(List<Seg> segs) {
        int w = 0;
        for (Seg s : segs) {
            w += segWidth(s);
        }
        return w;
    }

    /** 指定文本在给定段样式下的渲染宽度。 */
    private int textWidth(String t, Seg styleRef) {
        return getFontMetrics(f.deriveFont(styleOf(styleRef))).stringWidth(t);
    }
}
