import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * 课表查看与编辑窗口（重写版）。
 *
 * <p>窗口分两个模式（左侧选项卡切换）：</p>
 * <ul>
 *   <li><b>【课表】</b>：真实更改周次（写入 .local）、选中两个课程单元格 → 应用换课
 *       （写入 commands/swap.txt，命令系统生效）；</li>
 *   <li><b>【设置】编辑模式</b>：每个课程块为<b>下拉选择框</b>，直接选择即永久写入
 *       课表文件；另有主题色调节、Local 设置（晚自习、愚人节）、课程管理、更改时段、
 *       周六时段与轮次、晚自习轮次、命令测试。</li>
 * </ul>
 *
 * <p>网格 8 列：时间 / 周一~周五 / <b>周六时间（独立一列）</b> / 周六课程。
 * 临时换课与命令系统共用 commands/swap.txt 作为唯一事实源。</p>
 */
public class MainFrame extends JFrame implements ActionListener {

    /** 窗口图标（PNG 字节数据）。 */
    public static final byte[] logo = {-119,80,78,71,13,10,26,10,0,0,0,13,73,
            72,68,82,0,0,0,32,0,0,0,32,8,2,0,0,0,-4,24,-19,-93,0,0,0,1,115,82,
            71,66,0,-82,-50,28,-23,0,0,0,4,103,65,77,65,0,0,-79,-113,11,-4,97,
            5,0,0,0,9,112,72,89,115,0,0,14,-61,0,0,14,-61,1,-57,111,-88,100,0,
            0,0,24,116,69,88,116,83,111,102,116,119,97,114,101,0,80,97,105,110,
            116,46,78,69,84,32,53,46,49,46,57,108,110,-38,62,0,0,0,-74,101,88,
            73,102,73,73,42,0,8,0,0,0,5,0,26,1,5,0,1,0,0,0,74,0,0,0,27,1,5,0,1,
            0,0,0,82,0,0,0,40,1,3,0,1,0,0,0,2,0,0,0,49,1,2,0,16,0,0,0,90,0,0,0,
            105,-121,4,0,1,0,0,0,106,0,0,0,0,0,0,0,96,0,0,0,1,0,0,0,96,0,0,0,1,
            0,0,0,80,97,105,110,116,46,78,69,84,32,53,46,49,46,57,0,3,0,0,-112,
            7,0,4,0,0,0,48,50,51,48,1,-96,3,0,1,0,0,0,1,0,0,0,5,-96,4,0,1,0,0,0,
            -108,0,0,0,0,0,0,0,2,0,1,0,2,0,4,0,0,0,82,57,56,0,2,0,7,0,4,0,0,0,48,
            49,48,48,0,0,0,0,76,-49,-64,72,37,-113,-41,-96,0,0,3,29,73,68,65,84,
            72,75,-19,86,-47,74,27,65,20,-99,-103,93,-37,98,-48,108,32,38,82,124,
            49,72,11,6,-76,-8,96,31,124,44,40,20,-3,1,125,-110,-4,64,-65,-91,63,
            18,124,-80,-113,-107,18,76,72,-96,32,-115,-59,22,106,82,82,-44,36,40,
            52,-58,38,98,-77,-69,61,-39,59,-101,-52,78,-106,-12,-91,62,-76,-12,48,
            -60,59,-25,-36,-103,59,115,-25,68,-62,95,-67,126,-53,-18,19,66,-2,-67,
            55,-4,47,-16,91,-4,3,5,122,14,27,12,-37,101,-116,115,-105,113,-107,-20,
            -13,-34,39,120,-50,5,-59,42,79,3,42,-29,114,55,-107,-25,-39,55,-17,-88,
            20,116,-37,117,115,-7,-9,-113,103,-29,79,-97,-92,28,-37,38,-66,15,-105,
            -103,-90,-7,-27,107,-19,-29,-25,-22,-53,23,107,-100,75,122,0,33,-116,
            -93,-14,-89,31,-99,-18,-38,-13,103,-114,-125,99,98,72,-120,68,-44,31,
            -106,72,78,115,-42,-82,68,120,107,-42,50,-122,60,73,49,99,-38,104,-1,
            108,126,72,-86,-68,63,102,45,-15,-80,-41,48,58,-33,-110,-106,-95,37,8,
            -57,97,-61,-127,-62,-62,-60,101,3,-92,63,92,60,-104,-15,-56,118,92,
            -115,-57,64,79,24,55,-104,48,40,86,37,97,4,33,124,-56,-71,2,-113,-26,
            -24,-107,-100,7,-31,-87,-76,42,-80,-42,60,57,57,-95,102,-31,5,29,
            -57,-71,-72,-72,-64,22,-109,-109,-109,-120,93,119,-40,74,-112,-43,
            106,-75,-39,108,34,31,-103,-86,4,96,-93,90,-83,118,115,115,3,85,91,
            -56,51,-103,-116,-116,-68,101,-27,114,57,30,-113,-49,-51,-51,105,
            121,-40,-94,94,-81,-93,-58,-22,-22,-22,104,1,-100,-67,82,-87,-36,
            -34,-34,46,46,46,66,82,85,126,112,112,32,35,-17,6,-39,108,118,97,
            97,97,101,101,-91,-41,-21,17,79,-104,-104,-104,56,62,62,62,60,60,
            -36,-35,-35,69,-90,100,125,-96,124,46,-105,107,-75,90,-101,-101,
            -101,-38,-55,-60,-125,32,-48,10,-20,37,39,65,-128,-57,70,114,50,
            2,44,4,16,104,-53,-31,-94,0,80,28,-97,-74,109,-45,84,3,-87,-95,
            -24,-9,37,76,-43,-35,-126,-21,-5,102,8,-63,120,41,84,21,-33,-125,
            -24,118,-69,48,3,-70,41,-25,10,-38,-19,54,-44,80,9,-64,-86,78,
            -89,-125,64,75,-32,120,22,-7,28,-8,-126,-69,-18,-23,-23,-87,101,
            89,51,51,51,-120,37,-21,1,-89,-69,-68,-68,-124,-119,-45,-23,-76,
            -92,20,-32,-34,103,103,103,119,119,119,-13,-13,-13,-38,66,-66,-73,
            -73,39,35,-50,-47,-6,-3,-3,-3,84,42,-75,-76,-76,-124,-104,120,2,
            30,16,30,47,-107,74,59,59,59,-110,82,-128,86,-28,-13,121,92,113,
            125,125,29,125,-105,-84,7,19,-82,-89,-120,108,58,53,53,69,55,24,
            -75,41,-66,7,-8,2,14,-14,85,-96,64,52,26,69,-128,-123,-40,68,-67,
            68,-72,-117,66,1,9,24,99,48,0,-127,-106,64,-113,63,4,-71,40,20,
            -112,-128,-66,49,-62,64,-86,-100,40,48,-81,-82,-82,-24,46,-112,
            81,-112,44,-124,-9,84,-33,0,71,67,-117,96,9,-8,4,18,50,-91,-32,
            3,85,-81,-81,-81,-79,22,42,54,-111,-84,7,-66,-75,-75,37,35,-81,
            0,-71,40,-111,72,104,121,56,11,-114,114,126,126,62,-34,69,48,8,
            -11,74,10,-112,84,23,97,83,-72,8,86,91,94,94,30,125,100,-72,-88,
            88,44,110,111,111,-121,-34,-96,80,40,-32,18,27,27,27,-40,68,45,
            96,-30,-80,20,81,1,-72,40,22,-117,-63,12,90,-117,-16,95,-91,-47,
            104,68,34,17,-28,-121,22,32,23,65,-43,-4,45,112,82,21,-88,-127,
            12,64,-50,61,-48,20,-97,80,-119,25,5,-92,80,-11,-49,-1,108,-47,
            -18,-9,-73,-1,-16,98,-20,23,19,-80,49,-28,-109,41,-45,-69,0,0,
            0,0,73,69,78,68,-82,66,96,-126};

    /** 课程单元格字体。 */
    private static final Font CELL_FONT = new Font("Microsoft Yahei", Font.BOLD, 14);
    /** 时间列字体。 */
    private static final Font TIME_FONT = new Font("Microsoft YaHei", Font.PLAIN, 10);

    /** 当前打开的实例（单例）。 */
    public static MainFrame mf;

    /** 主题色相滑块（主窗口同步引用）。 */
    public static JSlider hueSelector = new JSlider(0, 100, 0);

    // ---- 左侧控制面板 ----
    /** 模式切换：【课表】/【设置】。 */
    private final JTabbedPane modeTabs = new JTabbedPane();
    private final JButton prevWeekBtn = new JButton("◀ 上一周");
    private final JButton nextWeekBtn = new JButton("下一周 ▶");
    private final JButton applyBtn = new JButton("应用换课");
    private final JLabel weekLabel = new JLabel("第 - 周", SwingConstants.CENTER);
    private final JCheckBox nightStudyCheck = new JCheckBox("晚自习");
    private final JCheckBox aprilFoolCheck = new JCheckBox("愚人节彩蛋");
    private final JLabel dateRangeLabel = new JLabel("", SwingConstants.CENTER);
    /** 换课模式预览周次（只预览不写入 .local）。 */
    private int previewWeekTurn = 1;
    /** 设置模式周次微调器（真实更改 WeekTurn）。 */
    private JSpinner weekSpinner;
    /** 换课模式时间偏移微调器（秒，"我的时间慢了x秒"）。 */
    private JSpinner timeOffsetSpinner;

    // ---- 课程数据（编辑模式下拉框与课程管理共用）----
    /** 课程全称列表（full_name.txt，用于下拉框显示）。 */
    private final List<String> fullNames = new ArrayList<>();
    /** 课程全称 → 简称 的映射（用于写回课表文件）。 */
    private final Map<String, String> fullToAbbr = new HashMap<>();

    // ---- 设置面板按钮 ----
    /** 课程管理：添加/移除/编辑 full_name.txt 中的课程。 */
    private final JButton courseManageBtn = new JButton("添加/移除/编辑课程");
    /** 更改时段：编辑周一~周五课表的时间行。 */
    private final JButton timeEditBtn = new JButton("更改时段");
    /** 周六时段与轮次：编辑 saturday.txt 的时间行与周次轮转行。 */
    private final JButton saturdayEditBtn = new JButton("周六时段与轮次");
    /** 晚自习轮次：编辑 self_study.txt 中轮次行末位的晚自习时间。 */
    private final JButton nightEditBtn = new JButton("晚自习轮次");
    /** 命令测试：打开单测命令窗口。 */
    private final JButton testCmdBtn = new JButton("命令测试");

    // ---- 自动更新 ----
    /** 镜像/更新源 raw 根输入框（mirror.txt，空 = 默认 GitHub 直连）。 */
    private JTextField mirrorField;
    /** 更新状态标签（当前版本 / 检查结果）。 */
    private JLabel updateStatusLabel;
    /** 保存镜像配置。 */
    private final JButton saveMirrorBtn = new JButton("保存镜像");
    /** 立即检查更新。 */
    private final JButton checkUpdateBtn = new JButton("检查更新");

    // ---- 课表区域 ----
    /** 表头行（GridLayout 1×8，与网格列严格对齐：时间/周一~五/周六时间/周六）。 */
    private final JPanel headerPanel = new JPanel(new GridLayout(1, 8, 6, 0));
    /** 课程网格（GridLayout timeRows×8，行列严格对齐）。 */
    private final JPanel gridPanel = new JPanel();

    /** 当前选中的两个课程单元格（【课表】模式换课用）。 */
    private final UnitPane[] swap = new UnitPane[2];

    /** 当前是否处于【设置】编辑模式（课程块渲染为下拉选择框）。 */
    private boolean editMode = false;

    /**
     * 构造换课窗口。
     *
     * @throws Exception 读取课表数据失败时抛出
     */
    public MainFrame() throws Exception {
        super("悬浮课表选项");
        setIconImage(new ImageIcon(logo).getImage());

        if (mf != null) {
            mf.setVisible(false);
            mf.dispose();
        }
        mf = this;

        setSize(720, 480);
        setMinimumSize(new Dimension(640, 400));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        setupLayout();
        setupControls();
        reload();
    }

    /**
     * 搭建整体布局：左侧控制面板（双模式）+ 右侧滚动课表。
     */
    private void setupLayout() {
        setLayout(new BorderLayout(8, 8));

        // ===== 左侧控制面板 =====
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        controlPanel.setPreferredSize(new Dimension(200, 0));
        controlPanel.setBackground(new Color(240, 240, 240));

        // ===== 【课表】模式面板 =====
        JPanel tabSchedule = new JPanel();
        tabSchedule.setLayout(new BoxLayout(tabSchedule, BoxLayout.Y_AXIS));
        tabSchedule.setBorder(new EmptyBorder(10, 8, 8, 8));

        // 周次切换
        JLabel weekTitle = new JLabel("周次切换", SwingConstants.CENTER);
        weekTitle.setFont(new Font("微软雅黑", Font.BOLD, 13));
        weekTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        weekLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        weekLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        weekLabel.setForeground(new Color(0, 100, 180));

        dateRangeLabel.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        dateRangeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        dateRangeLabel.setForeground(new Color(100, 100, 100));

        JPanel weekBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        weekBtnPanel.setOpaque(false);
        prevWeekBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        nextWeekBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        prevWeekBtn.addActionListener(this);
        nextWeekBtn.addActionListener(this);
        weekBtnPanel.add(prevWeekBtn);
        weekBtnPanel.add(nextWeekBtn);

        // 临时换课按钮：选中两个课程单元格后互换（写入 commands/swap.txt，命令系统生效）
        applyBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        applyBtn.setMaximumSize(new Dimension(170, 28));
        applyBtn.setFont(new Font("微软雅黑", Font.BOLD, 11));
        applyBtn.addActionListener(this);

        // 时间偏移："我的时间慢了x秒"，微调课表时间判断与 $time$ 宏
        JLabel timeOffsetTitle = new JLabel("时间偏移（秒）", SwingConstants.CENTER);
        timeOffsetTitle.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        timeOffsetTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        timeOffsetSpinner = new JSpinner(new SpinnerNumberModel(0, -3600, 3600, 1));
        timeOffsetSpinner.setMaximumSize(new Dimension(120, 28));
        timeOffsetSpinner.setAlignmentX(Component.CENTER_ALIGNMENT);
        timeOffsetSpinner.addChangeListener(e -> {
            Main.timeOffsetSeconds = (int) timeOffsetSpinner.getValue();
        });

        tabSchedule.add(weekTitle);
        tabSchedule.add(Box.createVerticalStrut(4));
        tabSchedule.add(weekLabel);
        tabSchedule.add(dateRangeLabel);
        tabSchedule.add(Box.createVerticalStrut(4));
        tabSchedule.add(weekBtnPanel);
        tabSchedule.add(Box.createVerticalStrut(12));
        tabSchedule.add(applyBtn);
        tabSchedule.add(Box.createVerticalStrut(12));
        tabSchedule.add(timeOffsetTitle);
        tabSchedule.add(Box.createVerticalStrut(2));
        tabSchedule.add(timeOffsetSpinner);
        tabSchedule.add(Box.createVerticalGlue());

        // ===== 【设置】模式面板 =====
        JPanel tabSettings = new JPanel();
        tabSettings.setLayout(new BoxLayout(tabSettings, BoxLayout.Y_AXIS));
        tabSettings.setBorder(new EmptyBorder(10, 8, 8, 8));

        // 主题色
        JLabel hueTitle = new JLabel("主题色相", SwingConstants.CENTER);
        hueTitle.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        hueTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        hueSelector.setAlignmentX(Component.CENTER_ALIGNMENT);
        hueSelector.setMaximumSize(new Dimension(170, 30));

        // Local 设置
        JLabel localTitle = new JLabel("本地设置", SwingConstants.CENTER);
        localTitle.setFont(new Font("微软雅黑", Font.BOLD, 13));
        localTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 周次微调器（真实更改 WeekTurn 写入 .local）
        JLabel weekSpinnerTitle = new JLabel("当前周次", SwingConstants.CENTER);
        weekSpinnerTitle.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        weekSpinnerTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        weekSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
        weekSpinner.setMaximumSize(new Dimension(120, 28));
        weekSpinner.setAlignmentX(Component.CENTER_ALIGNMENT);
        weekSpinner.addChangeListener(e -> {
            int next = (int) weekSpinner.getValue();
            int current = Main.getLocal("WeekTurn", 1);
            if (next != current) {
                Main.locals.put("WeekTurn", next);
                try {
                    Main.saveLocals();
                    Main.reload();
                    reload();
                } catch (Exception ex) {
                    Main.outputException(ex);
                }
            }
        });

        nightStudyCheck.setAlignmentX(Component.CENTER_ALIGNMENT);
        nightStudyCheck.setOpaque(false);
        nightStudyCheck.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        nightStudyCheck.addActionListener(e -> saveLocalSettings());

        aprilFoolCheck.setAlignmentX(Component.CENTER_ALIGNMENT);
        aprilFoolCheck.setOpaque(false);
        aprilFoolCheck.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        aprilFoolCheck.addActionListener(e -> saveLocalSettings());

        // 课程管理 / 时段 / 周六 / 晚自习 / 命令测试按钮
        JButton[] settingBtns = {courseManageBtn, timeEditBtn, saturdayEditBtn, nightEditBtn, testCmdBtn};
        for (JButton b : settingBtns) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(170, 28));
            b.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            b.addActionListener(this);
        }

        tabSettings.add(hueTitle);
        tabSettings.add(Box.createVerticalStrut(2));
        tabSettings.add(hueSelector);
        tabSettings.add(Box.createVerticalStrut(10));
        tabSettings.add(localTitle);
        tabSettings.add(Box.createVerticalStrut(4));
        tabSettings.add(weekSpinnerTitle);
        tabSettings.add(Box.createVerticalStrut(2));
        tabSettings.add(weekSpinner);
        tabSettings.add(Box.createVerticalStrut(8));
        tabSettings.add(nightStudyCheck);
        tabSettings.add(aprilFoolCheck);
        tabSettings.add(Box.createVerticalStrut(10));
        tabSettings.add(courseManageBtn);
        tabSettings.add(Box.createVerticalStrut(4));
        tabSettings.add(timeEditBtn);
        tabSettings.add(Box.createVerticalStrut(4));
        tabSettings.add(saturdayEditBtn);
        tabSettings.add(Box.createVerticalStrut(4));
        tabSettings.add(nightEditBtn);
        tabSettings.add(Box.createVerticalStrut(4));
        tabSettings.add(testCmdBtn);
        tabSettings.add(Box.createVerticalStrut(12));

        // ---- 自动更新 ----
        JLabel updateTitle = new JLabel("自动更新", SwingConstants.CENTER);
        updateTitle.setFont(new Font("微软雅黑", Font.BOLD, 13));
        updateTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        mirrorField = new JTextField(UpdateChecker.mirrorBase());
        mirrorField.setMaximumSize(new Dimension(180, 24));
        mirrorField.setAlignmentX(Component.CENTER_ALIGNMENT);
        mirrorField.setToolTipText("更新源 raw 根地址；留空或恢复默认值使用 GitHub 直连，"
                + "可填 gitee raw 根或 ghproxy 前缀拼接的地址");
        updateStatusLabel = new JLabel("本地版本 v" + UpdateChecker.localVersion(), SwingConstants.CENTER);
        updateStatusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        updateStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel mirrorHint = new JLabel("更新源（镜像）", SwingConstants.CENTER);
        mirrorHint.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        mirrorHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        saveMirrorBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        saveMirrorBtn.setMaximumSize(new Dimension(120, 26));
        saveMirrorBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        saveMirrorBtn.addActionListener(this);
        checkUpdateBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        checkUpdateBtn.setMaximumSize(new Dimension(120, 26));
        checkUpdateBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        checkUpdateBtn.addActionListener(this);

        tabSettings.add(updateTitle);
        tabSettings.add(Box.createVerticalStrut(4));
        tabSettings.add(mirrorHint);
        tabSettings.add(Box.createVerticalStrut(2));
        tabSettings.add(mirrorField);
        tabSettings.add(Box.createVerticalStrut(4));
        tabSettings.add(saveMirrorBtn);
        tabSettings.add(Box.createVerticalStrut(4));
        tabSettings.add(checkUpdateBtn);
        tabSettings.add(Box.createVerticalStrut(4));
        tabSettings.add(updateStatusLabel);
        tabSettings.add(Box.createVerticalGlue());

        modeTabs.addTab("课表", tabSchedule);

        // 设置面板内容较多（含自动更新区块），包一层滚动面板，防止超出窗口高度显示不全
        JScrollPane settingsScroll = new JScrollPane(tabSettings);
        settingsScroll.setBorder(null);
        settingsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        settingsScroll.getVerticalScrollBar().setUnitIncrement(16);
        settingsScroll.getViewport().setOpaque(false);
        modeTabs.addTab("设置", settingsScroll);

        modeTabs.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        controlPanel.add(modeTabs, BorderLayout.CENTER);

        add(controlPanel, BorderLayout.WEST);

        // ===== 右侧课表滚动区 =====
        // wrapper 包含表头 + 网格，二者均用 GridLayout 7 列，保证表头与课程列严格对齐
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setBackground(new Color(250, 250, 250));
        wrapperPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        headerPanel.setBackground(new Color(250, 250, 250));
        gridPanel.setBackground(new Color(250, 250, 250));

        wrapperPanel.add(headerPanel, BorderLayout.NORTH);
        wrapperPanel.add(gridPanel, BorderLayout.CENTER);

        JScrollPane scrollPane = new JScrollPane(wrapperPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * 配置控件初始状态与监听器。
     */
    private void setupControls() {
        nightStudyCheck.setSelected(Main.getLocal("HasNightStudy", 0) == 1);
        aprilFoolCheck.setSelected(Main.getLocal("AprilFool", 0) == 1);
        hueSelector.setValue(Main.getLocal("Theme", 0));

        refreshCourseCombo();

        // 模式切换：【设置】模式下课程块渲染为下拉选择框（编辑模式）
        modeTabs.addChangeListener(e -> {
            boolean nowEdit = modeTabs.getSelectedIndex() == 1;
            if (nowEdit != editMode) {
                editMode = nowEdit;
                try {
                    reload();
                } catch (Exception ex) {
                    Main.outputException(ex);
                }
            }
        });

        hueSelector.addChangeListener(e -> {
            Main.locals.put("Theme", hueSelector.getValue());
            if (Main.mw != null) {
                Main.mw.updateTheme();
                Main.mw.repaint();
            }
            for (UnitPane up : swap) {
                if (up != null) up.repaint();
            }
            gridPanel.repaint();
            try {
                Main.saveLocals();
            } catch (IOException ex) {
                Main.logError("保存主题色", ex);
            }
        });
    }

    /**
     * 刷新课程下拉列表数据：读取 full_name.txt 的全部课程（全称显示，简称写回）。
     */
    private void refreshCourseCombo() {
        fullNames.clear();
        fullToAbbr.clear();
        if (Main.iifr != null) {
            for (Map.Entry<String, String> e : Main.iifr.asMap().entrySet()) {
                String key = e.getKey();
                String val = e.getValue();
                // 跳过空键/空值；空格键（课间）保留，使编辑模式下可见可编辑
                if (key == null || val == null || val.isEmpty()) {
                    continue;
                }
                if (!val.equals(" ") && key.trim().isEmpty()) {
                    continue;
                }
                fullToAbbr.put(val, key);
                fullNames.add(val);
            }
            // 去重后按名称排序，保证下拉顺序稳定
            fullNames.removeIf(n -> fullNames.indexOf(n) != fullNames.lastIndexOf(n));
            fullNames.sort(String::compareTo);
        }
    }

    /**
     * 保存本地设置（晚自习、愚人节开关）。
     */
    private void saveLocalSettings() {
        Main.locals.put("HasNightStudy", nightStudyCheck.isSelected() ? 1 : 0);
        Main.locals.put("AprilFool", aprilFoolCheck.isSelected() ? 1 : 0);
        try {
            Main.saveLocals();
            System.out.println("[MainFrame] 本地设置已保存: 晚自习=" + nightStudyCheck.isSelected()
                    + ", 愚人节=" + aprilFoolCheck.isSelected());
        } catch (IOException ex) {
            Main.outputException(ex);
        }
    }

    /**
     * 读取时间文件第 0 行，将时间点两两配对为时间段列表。
     *
     * <p>时间行全部时间点成对配对：课程行第 0 个 token 也是真实的第一节课
     * （模板中设为星期前缀仅方便 debug，不是表头），因此时间列从第 0 对开始。</p>
     *
     * @param f 时间文件（schedule.txt 或 saturday.txt）
     * @return 时间段列表（空文件返回空列表）
     */
    private static List<String[]> readTimePairs(File f) {
        List<String[]> pairs = new ArrayList<>();
        try (Scanner sc = new Scanner(f, StandardCharsets.UTF_8)) {
            if (sc.hasNextLine()) {
                String[] t = sc.nextLine().split(" ");
                for (int i = 0; i + 1 < t.length; i += 2) {
                    String s = stripLeadingZero(t[i]);
                    String e = stripLeadingZero(t[i + 1]);
                    if (!Objects.equals(s, e)) {
                        pairs.add(new String[]{s, e});
                    }
                }
            }
        } catch (IOException ignored) {
            // 文件不存在/读取失败 → 空时间段
        }
        return pairs;
    }

    /**
     * 重新加载课表数据并重建网格。
     *
     * <p><b>【课表】模式</b>（8 列）：表头 [时间、周一~周五、周六时间、周六]。
     * 周一至周五共用 schedule.txt 的时间列，周六使用 saturday.txt 自己的时间行
     * 单独成一列（示例中两者相同不代表一定相同）。课程块为 UnitPane
     * （点击选中两格 → 应用换课），并应用命令脚本（load.txt，dryRun 预览）
     * 的换课效果，与主课条显示一致。</p>
     *
     * <p><b>【设置】编辑模式</b>（6 列）：表头 [时间、周一~周五]，
     * 不显示周六与晚自习相关内容（它们在左侧弹窗中编辑）；
     * 课程块为下拉选择框（选择即永久写入课表文件），显示文件原始内容（不应用脚本）。</p>
     *
     * @throws Exception 读取课表文件失败时抛出
     */
    public void reload() throws Exception {
        headerPanel.removeAll();
        gridPanel.removeAll();
        swap[0] = swap[1] = null;

        // 周次：设置模式用真实 WeekTurn（写入 .local），换课模式用预览周次（不写入）
        int realWeek = Main.getLocal("WeekTurn", 1);
        if (previewWeekTurn < 1) previewWeekTurn = realWeek;
        int weekTurn = editMode ? realWeek : previewWeekTurn;
        weekLabel.setText("第 " + weekTurn + " 周");
        if (weekSpinner != null && !weekSpinner.getValue().equals(realWeek)) {
            weekSpinner.setValue(realWeek);
        }
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate saturday = monday.plusDays(5);
        dateRangeLabel.setText(monday.format(DateTimeFormatter.ofPattern("MM.dd"))
                + " ~ " + saturday.format(DateTimeFormatter.ofPattern("MM.dd")));

        // ===== 时间段：周中与周六各自独立读取 =====
        List<String[]> weekTimePairs = readTimePairs(Main.schedule);
        List<String[]> satTimePairs = readTimePairs(Main.saturday);
        int rows = Math.max(weekTimePairs.size(), satTimePairs.size());

        // ===== 构建周一~周五单元格（课表模式应用脚本换课预览，编辑模式显示文件原样）=====
        boolean applyPreview = !editMode;
        List<List<JComponent>> dayColumns = new ArrayList<>(5);
        LocalDate date = monday;
        for (int dayIdx = 0; dayIdx < 5; dayIdx++) {
            List<JComponent> cells = buildDayCells(date, weekTurn, applyPreview);
            // 与周中时间列严格对齐：不足补空位，超出截断（时段增减后由对齐逻辑保证一致）
            while (cells.size() < weekTimePairs.size()) {
                cells.add(emptyCell());
            }
            if (cells.size() > weekTimePairs.size()) {
                cells = new ArrayList<>(cells.subList(0, weekTimePairs.size()));
            }
            dayColumns.add(cells);
            date = date.plusDays(1);
        }

        int colCount;
        if (editMode) {
            // ===== 编辑模式：仅时间列 + 周一~周五（周六/晚自习在弹窗编辑）=====
            colCount = 6;
            String[] dayNames = {"时间", "周一", "周二", "周三", "周四", "周五"};
            for (String name : dayNames) {
                headerPanel.add(makeHeaderLabel(name));
            }
            gridPanel.setLayout(new GridLayout(rows, colCount, 6, 6));
            for (int row = 0; row < rows; row++) {
                gridPanel.add(row < weekTimePairs.size()
                        ? new TimePane(weekTimePairs.get(row)[0], weekTimePairs.get(row)[1])
                        : new TimePane("", ""));
                for (int d = 0; d < 5; d++) {
                    gridPanel.add(row < dayColumns.get(d).size() ? dayColumns.get(d).get(row) : emptyCell());
                }
            }
        } else {
            // ===== 课表模式：时间 / 周一~五 / 周六时间 / 周六（8 列）=====
            colCount = 8;
            List<JComponent> satCells = buildDayCells(saturday, weekTurn, false);
            while (satCells.size() < satTimePairs.size()) {
                satCells.add(emptyCell());
            }
            if (satCells.size() > satTimePairs.size()) {
                satCells = new ArrayList<>(satCells.subList(0, satTimePairs.size()));
            }
            String[] dayNames = {"时间", "周一", "周二", "周三", "周四", "周五", "周六时间", "周六"};
            for (String name : dayNames) {
                headerPanel.add(makeHeaderLabel(name));
            }
            gridPanel.setLayout(new GridLayout(rows, colCount, 6, 6));
            for (int row = 0; row < rows; row++) {
                // 列0：周中时间
                gridPanel.add(row < weekTimePairs.size()
                        ? new TimePane(weekTimePairs.get(row)[0], weekTimePairs.get(row)[1])
                        : new TimePane("", ""));
                // 列1~5：周一~周五
                for (int d = 0; d < 5; d++) {
                    gridPanel.add(row < dayColumns.get(d).size() ? dayColumns.get(d).get(row) : emptyCell());
                }
                // 列6：周六时间（独立）
                gridPanel.add(row < satTimePairs.size()
                        ? new TimePane(satTimePairs.get(row)[0], satTimePairs.get(row)[1])
                        : new TimePane("", ""));
                // 列7：周六课程
                gridPanel.add(row < satCells.size() ? satCells.get(row) : emptyCell());
            }
        }

        headerPanel.revalidate();
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    /**
     * 创建表头标签（统一样式）。
     */
    private static JLabel makeHeaderLabel(String name) {
        JLabel label = new JLabel(name, SwingConstants.CENTER);
        label.setFont(new Font("微软雅黑", Font.BOLD, 12));
        label.setForeground(new Color(70, 70, 70));
        label.setOpaque(true);
        label.setBackground(new Color(246, 246, 246));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(7, 0, 7, 0)));
        return label;
    }

    /**
     * 空位单元格（灰色块，用于补齐行列对齐）。
     */
    private JComponent emptyCell() {
        if (editMode) {
            JPanel p = new JPanel();
            p.setPreferredSize(new Dimension(70, 50));
            p.setOpaque(true);
            p.setBackground(new Color(246, 246, 246));
            return p;
        }
        return new UnitPane("", "");
    }

    /**
     * 构建指定日期的课程单元格列表（完整显示全部课程，不含自习/晚自习追加内容）。
     *
     * <p>【课表】模式返回 UnitPane（点击选中），并在单双周解析后以 dryRun 方式
     * 执行 load.txt（含 run swap.txt 的 if+setcourse 换课），显示与主课条一致的效果；
     * 【设置】编辑模式返回 ComboCell（下拉选择框，选择即永久更改课表文件），
     * 显示文件原始内容，不应用脚本。</p>
     *
     * @param date         目标日期
     * @param weekTurn     当前周次（单双周解析与周六行轮转用）
     * @param applyPreview 是否应用脚本换课预览（仅课表模式为 true）
     * @return 该天的课程单元格列表
     * @throws Exception 读取课表文件失败时抛出
     */
    private List<JComponent> buildDayCells(LocalDate date, int weekTurn, boolean applyPreview) throws Exception {
        List<JComponent> cells = new ArrayList<>();
        String base = getBaseRow(date, weekTurn);

        // 单双周课程解析（与 Main.getSchedule 保持一致，按当前周次取值）
        if (base.contains("-")) {
            String[] sp = base.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String s : sp) {
                if (s.contains("-")) {
                    String[] sub = s.split("-");
                    sb.append(sub[(weekTurn - 1) % sub.length]).append(" ");
                } else {
                    sb.append(s).append(" ");
                }
            }
            base = sb.toString().trim();
        }

        // 课表模式：dryRun 执行 load.txt（if+setcourse 换课生效，系统命令跳过），
        // 使窗口显示与主课条（真实执行脚本）一致
        if (applyPreview) {
            File load = new File(Main.commandsDir, "load.txt");
            if (load.exists()) {
                List<String> scriptLines = readAllLines(load);
                if (!scriptLines.isEmpty()) {
                    SScriptInterpreter.Context previewCtx = SScriptInterpreter.Context.of(
                            "预览", base, "", date, java.time.LocalTime.now(), weekTurn);
                    previewCtx.dryRun = true;
                    new SScriptInterpreter().execute(scriptLines, previewCtx);
                    base = String.join(" ", previewCtx.courses);
                }
            }
        }

        // 展示全部课程：每行第 0 个 token 也是真实课程（模板中设为星期前缀方便 debug，不是表头）
        // 解析时删除格式化 token（#+格式码），避免被当作课程
        base = SScriptInterpreter.stripFormat(base);
        String[] arr = base.trim().isEmpty() ? new String[0] : base.split(" ");
        for (int j = 0; j < arr.length; j++) {
            if (Objects.equals(arr[j], "~")) {
                continue;
            }
            String position = date.getYear() + "@" + date.getMonthValue() + "@" + date.getDayOfMonth() + "@" + j;
            String abbr = arr[j];
            if (editMode) {
                // 编辑模式：课间（空字符串→空格）/午饭（|）均显示，空位（\）仍为空
                if (Objects.equals(abbr, "\\")) {
                    cells.add(emptyCell());
                } else {
                    String cellAbbr = abbr.isEmpty() ? " " : abbr;
                    cells.add(new ComboCell(position, cellAbbr));
                }
            } else {
                String display;
                switch (abbr) {
                    case "\\":
                        display = "";
                        break;
                    case "|":
                        display = Main.iifr != null ? Main.iifr.get("|") : "|";
                        break;
                    default:
                        display = abbr;
                        break;
                }
                UnitPane up = new UnitPane(position, display);
                up.addActionListener(this);
                up.setDoubleClickListener(() -> showCoursePicker(up));
                cells.add(up);
            }
        }
        return cells;
    }

    /**
     * 读取指定日期的基础课表行（不含自习 / 晚自习等追加内容）。
     *
     * <p>周一至周五从 {@code schedule.txt} 按星期取行；周六从 {@code saturday.txt}
     * 按周次轮转取行（与 {@link Main#getSchedule} 的周六逻辑一致）。
     * 文件不存在时返回空串（网格按空位补齐）。</p>
     *
     * @param date     目标日期
     * @param weekTurn 当前查看周次（周六行轮转用）
     * @return 该行的课表文本（含全部课程 token，调用方完整展示）
     * @throws Exception 读取失败时抛出
     */
    private String getBaseRow(LocalDate date, int weekTurn) throws Exception {
        int dow = date.getDayOfWeek().getValue();
        if (dow == 6) {
            if (!Main.saturday.exists()) return "";
            return Main.getLine(weekTurn, Main.saturday, 1);
        }
        if (dow == 7) {
            return "";
        }
        // 周一~周五：schedule.txt
        List<String> lines = new ArrayList<>();
        try (Scanner sc = new Scanner(Main.schedule, StandardCharsets.UTF_8)) {
            while (sc.hasNextLine()) {
                lines.add(sc.nextLine());
            }
        }
        if (dow < lines.size()) {
            // 第 0 行是时间行，第 dow 行是对应当天的课表行
            return lines.get(dow);
        }
        return "";
    }

    /**
     * 去掉时间字符串开头的 "0"（如 "03:30" → "3:30"）。
     *
     * @param t 时间字符串
     * @return 去掉前导零后的时间
     */
    private static String stripLeadingZero(String t) {
        if (t != null && t.startsWith("0")) {
            return t.substring(1);
        }
        return t;
    }

    /**
     * 将颜色混入白色生成柔和的浅色版本（降低饱和度，避免选中块过于刺眼）。
     *
     * @param c      原始颜色
     * @param amount 混入白色的比例（0~1，1 为纯白）
     * @return 柔化后的颜色
     */
    private static Color lighten(Color c, float amount) {
        int r = (int) (c.getRed() + (255 - c.getRed()) * amount);
        int g = (int) (c.getGreen() + (255 - c.getGreen()) * amount);
        int b = (int) (c.getBlue() + (255 - c.getBlue()) * amount);
        return new Color(r, g, b);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == prevWeekBtn) {
            // 换课模式：只预览，不写入 .local
            if (previewWeekTurn > 1) {
                previewWeekTurn--;
                try { reload(); } catch (Exception e) { Main.outputException(e); }
            }
            return;
        }
        if (ae.getSource() == nextWeekBtn) {
            // 换课模式：只预览，不写入 .local
            previewWeekTurn++;
            try { reload(); } catch (Exception e) { Main.outputException(e); }
            return;
        }
        if (ae.getSource() == applyBtn) {
            applySwap();
            return;
        }
        if (ae.getSource() == courseManageBtn) {
            showCourseManager();
            return;
        }
        if (ae.getSource() == timeEditBtn) {
            showTimeEditor();
            return;
        }
        if (ae.getSource() == saturdayEditBtn) {
            showSaturdayEditor();
            return;
        }
        if (ae.getSource() == nightEditBtn) {
            showNightEditor();
            return;
        }
        if (ae.getSource() == testCmdBtn) {
            showCommandTester();
            return;
        }
        if (ae.getSource() == saveMirrorBtn) {
            try {
                UpdateChecker.saveMirror(mirrorField.getText());
                mirrorField.setText(UpdateChecker.mirrorBase());
                updateStatusLabel.setText("镜像已保存：" + UpdateChecker.mirrorBase());
                System.out.println("[update] 镜像已保存: " + UpdateChecker.mirrorBase());
            } catch (Exception ex) {
                Main.outputException(ex);
            }
            return;
        }
        if (ae.getSource() == checkUpdateBtn) {
            updateStatusLabel.setText("检查中…");
            new Thread(() -> {
                String result = UpdateChecker.runUpdate();
                SwingUtilities.invokeLater(() ->
                        updateStatusLabel.setText("<html>" + escapeHtml(result) + "</html>"));
            }, "manual-update").start();
            return;
        }
        if (!(ae.getSource() instanceof UnitPane)) return;
        handleUnitPaneClick((UnitPane) ae.getSource());
    }

    /**
     * 真实更改当前周次（写入 .local 的 WeekTurn），并刷新窗口与主课条。
     *
     * @param delta 周次增量（-1 上一周 / +1 下一周）
     */
    private void changeWeekTurn(int delta) {
        int current = Main.getLocal("WeekTurn", 1);
        int next = current + delta;
        if (next < 1) {
            JOptionPane.showMessageDialog(this, "周次不能小于 1", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Main.locals.put("WeekTurn", next);
        try {
            Main.saveLocals();
            Main.reload();
            reload();
        } catch (Exception e) {
            Main.outputException(e);
        }
        System.out.println("[MainFrame] 周次已更改: " + current + " → " + next);
    }

    /**
     * 将指定单元格的课程直接写入对应课表文件（永久更改），并刷新窗口与主课条。
     *
     * <p>写入位置由单元格 position（year@month@day@index）换算：
     * 周一至周五写入 schedule.txt 对应星期行，周六写入 saturday.txt 按周次轮转的行。
     * 编辑模式下拉框与双击更换共用此方法。</p>
     *
     * @param position 单元格位置标识（year@month@day@index）
     * @param newAbbr  课程简称（写入课表文件的 token）
     */
    private void applyChangeToCell(String position, String newAbbr) {
        // 解析 position：year@month@day@index
        String[] parts = position.split("@");
        int year, month, day, index;
        try {
            year = Integer.parseInt(parts[0]);
            month = Integer.parseInt(parts[1]);
            day = Integer.parseInt(parts[2]);
            index = Integer.parseInt(parts[3]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            JOptionPane.showMessageDialog(this, "单元格位置无效：" + position, "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        LocalDate date;
        try {
            date = LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            JOptionPane.showMessageDialog(this, "单元格日期无效", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int dow = date.getDayOfWeek().getValue();
        if (dow == 7) {
            JOptionPane.showMessageDialog(this, "周日课表暂不支持在窗口中永久更改", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 确定目标文件
        File targetFile = (dow == 6) ? Main.saturday : Main.schedule;

        // 读取文件 → 计算行号（0 起）→ 修改目标行对应列 → 写回
        List<String> lines = new ArrayList<>();
        try (Scanner sc = new Scanner(targetFile, StandardCharsets.UTF_8)) {
            while (sc.hasNextLine()) {
                lines.add(sc.nextLine());
            }
        } catch (IOException e) {
            Main.outputException(e);
            return;
        }
        int weekTurn = Main.getLocal("WeekTurn", 1);
        int lineIndex;
        if (dow == 6) {
            // 周六按周次轮转，轮次行数为 文件行数-1（时间行之后），动态适配增删
            int rowCount = Math.max(1, lines.size() - 1);
            lineIndex = 1 + (weekTurn - 1) % rowCount;
        } else {
            lineIndex = dow; // 周一=1 ... 周五=5（第 0 行是时间行）
        }
        if (lineIndex >= lines.size()) {
            JOptionPane.showMessageDialog(this, "课表文件行数不足，无法写入", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        // 定位目标列：先删除格式化 token 再数课程（与界面显示索引一致），写回时保留原 token 前缀
        String rawRow = lines.get(lineIndex);
        String[] rawTokens = rawRow.split(" ");
        int realIdx = -1;
        boolean found = false;
        for (int k = 0; k < rawTokens.length; k++) {
            String st = SScriptInterpreter.stripFormat(rawTokens[k]);
            if (!st.isEmpty()) {
                realIdx++;
                if (realIdx == index) {
                    // 保留该 token 的格式前缀（如 #c语 → #c + 新课程），未改列不受影响
                    String prefix = rawTokens[k].substring(0, rawTokens[k].length() - st.length());
                    rawTokens[k] = prefix + newAbbr;
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            JOptionPane.showMessageDialog(this, "课表列数不足，无法写入", "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        lines.set(lineIndex, String.join(" ", rawTokens));

        try (FileWriter fw = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
            for (String l : lines) {
                fw.write(l);
                fw.write("\n");
            }
        } catch (IOException e) {
            Main.outputException(e);
            return;
        }

        // 清除选择并刷新
        if (swap[0] != null) swap[0].setSelected(false);
        if (swap[1] != null) swap[1].setSelected(false);
        swap[0] = swap[1] = null;
        try {
            Main.reload();
            reload();
        } catch (Exception e) {
            Main.outputException(e);
        }
        System.out.println("[MainFrame] 永久更改课程: " + date + " 第" + index + "列 → " + newAbbr);
    }

    /**
     * 双击更换课程：弹出课程选择框，选择后直接写入课表文件（永久更改）。
     *
     * @param up 被双击的课程单元格
     */
    private void showCoursePicker(UnitPane up) {
        if (up.course.isEmpty()) return;
        // 先单击选中该格，保持视觉反馈一致
        handleUnitPaneClick(up);

        JComboBox<String> picker = new JComboBox<>();
        for (String n : fullNames) {
            picker.addItem(n);
        }
        // 尽量定位到当前课程的全称
        String currentFull = fullToAbbr.entrySet().stream()
                .filter(e -> e.getValue().equals(up.course))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        if (currentFull != null) {
            for (int i = 0; i < picker.getItemCount(); i++) {
                if (picker.getItemAt(i).equals(currentFull)) {
                    picker.setSelectedIndex(i);
                    break;
                }
            }
        }
        picker.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JLabel("更换为："), BorderLayout.WEST);
        panel.add(picker, BorderLayout.CENTER);
        panel.setBorder(new EmptyBorder(8, 4, 4, 4));

        int r = JOptionPane.showOptionDialog(this, panel, "更换课程 - " + up.course,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, new Object[]{"更换", "取消"}, "更换");
        if (r == 0 && picker.getSelectedItem() != null) {
            String abbr = fullToAbbr.get(picker.getSelectedItem().toString());
            if (abbr == null || abbr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "未找到该课程对应的简称，无法写入", "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            applyChangeToCell(up.position, abbr);
        }
    }

    // ==================================================================
    // 设置对话框：命令测试 / 课程管理 / 时段 / 周六 / 晚自习
    // ==================================================================

    /**
     * 命令测试窗口：输入临时 command 逐行运行，等同于普通命令真实执行——
     * 直接作用于当前显示上下文（Main.lastCtx），addblock/setcourse 等真实修改课表并刷新主课条，
     * cmd/键鼠/splashscreen 等系统命令真实生效；不显示输出。
     */
    private void showCommandTester() {
        JDialog dlg = new JDialog(this, "命令测试", false);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.setSize(520, 320);
        dlg.setLocationRelativeTo(this);

        JTextArea inputArea = new JTextArea(10, 40);
        inputArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        inputArea.setBorder(new EmptyBorder(6, 6, 6, 6));
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder("输入命令（每行一条，真实执行）"));

        JButton runBtn = new JButton("运行");
        JButton closeBtn = new JButton("关闭");
        runBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        closeBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        btnPanel.add(runBtn);
        btnPanel.add(closeBtn);

        dlg.add(inputScroll, BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);

        runBtn.addActionListener(e -> {
            List<String> lines = new ArrayList<>();
            for (String l : inputArea.getText().split("\\R")) {
                if (!l.trim().isEmpty()) lines.add(l);
            }
            if (lines.isEmpty()) return;

            // 真实执行：直接作用于当前显示上下文（等同 load.txt 里的命令），系统命令不跳过
            SScriptInterpreter.Context ctx = Main.lastCtx;
            if (ctx == null) {
                JOptionPane.showMessageDialog(dlg, "当前无课表上下文，无法执行", "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            // 后台线程执行：delay/splashscreen 等阻塞命令不占用 EDT，避免单测窗口卡死
            new Thread(() -> {
                try {
                    new SScriptInterpreter().execute(lines, ctx);
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(dlg, "命令执行出错：\n" + ex, "错误",
                                    JOptionPane.ERROR_MESSAGE));
                    return;
                }
                // 真实修改立即反映到主课条（EDT 中操作 Swing 组件）
                SwingUtilities.invokeLater(() -> {
                    if (Main.mw != null) {
                        Main.mw.setTextContent(ctx.blocks.toArray(new String[0]));
                    }
                });
            }, "单测命令执行").start();
        });
        closeBtn.addActionListener(e -> dlg.dispose());

        dlg.setVisible(true);
    }

    /**
     * 课程管理对话框：添加、移除、编辑 full_name.txt 中的课程（简称 = 全称）。
     */
    private void showCourseManager() {
        // 读取现有条目（保持文件顺序）
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        for (String l : readAllLines(Main.fullName)) {
            if (l.trim().isEmpty() || l.startsWith("#")) continue;
            int eq = l.indexOf('=');
            if (eq < 0) continue;
            entries.put(l.substring(0, eq), l.substring(eq + 1));
        }

        JDialog dlg = new JDialog(this, "课程管理", true);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.setSize(340, 420);
        dlg.setLocationRelativeTo(this);

        DefaultListModel<String> model = new DefaultListModel<>();
        entries.forEach((k, v) -> model.addElement(k + " = " + v));
        JList<String> list = new JList<>(model);
        list.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(new EmptyBorder(6, 6, 6, 6));

        // 输入区
        JPanel inputPanel = new JPanel(new GridLayout(2, 2, 6, 6));
        inputPanel.setBorder(new EmptyBorder(0, 8, 0, 8));
        JTextField abbrField = new JTextField();
        JTextField fullField = new JTextField();
        abbrField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        fullField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        inputPanel.add(new JLabel("简称："));
        inputPanel.add(abbrField);
        inputPanel.add(new JLabel("全称："));
        inputPanel.add(fullField);

        // 按钮区
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        JButton addBtn = new JButton("添加");
        JButton updBtn = new JButton("更新");
        JButton delBtn = new JButton("删除");
        JButton closeBtn = new JButton("关闭");
        for (JButton b : new JButton[]{addBtn, updBtn, delBtn, closeBtn}) {
            b.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            btnPanel.add(b);
        }

        Runnable refreshList = () -> {
            model.clear();
            entries.forEach((k, v) -> model.addElement(k + " = " + v));
        };
        Runnable saveEntries = () -> {
            try (FileWriter fw = new FileWriter(Main.fullName, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> e : entries.entrySet()) {
                    fw.write(e.getKey() + "=" + e.getValue() + "\n");
                }
            } catch (IOException ex) {
                Main.outputException(ex);
                return;
            }
            // 刷新 iifr 与下拉框
            if (Main.iifr != null) {
                try {
                    Main.iifr.load(new FileInputStream(Main.fullName));
                } catch (IOException ex) {
                    Main.outputException(ex);
                }
            }
            refreshCourseCombo();
        };

        list.addListSelectionListener(e -> {
            String sel = list.getSelectedValue();
            if (sel == null) return;
            int eq = sel.indexOf(" = ");
            if (eq > 0) {
                abbrField.setText(sel.substring(0, eq));
                fullField.setText(sel.substring(eq + 3));
            }
        });

        addBtn.addActionListener(e -> {
            String k = abbrField.getText();
            String v = fullField.getText();
            if (k.isEmpty() || v.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "简称与全称都不能为空", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (entries.containsKey(k)) {
                entries.put(k, v);
            } else {
                entries.put(k, v);
            }
            saveEntries.run();
            refreshList.run();
        });
        updBtn.addActionListener(e -> {
            String k = abbrField.getText();
            String v = fullField.getText();
            if (k.isEmpty() || v.isEmpty() || !entries.containsKey(k)) {
                JOptionPane.showMessageDialog(dlg, "请选择或输入一个已存在的简称", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            entries.put(k, v);
            saveEntries.run();
            refreshList.run();
        });
        delBtn.addActionListener(e -> {
            String k = abbrField.getText();
            if (k.isEmpty() || !entries.containsKey(k)) {
                JOptionPane.showMessageDialog(dlg, "请选择要删除的课程", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            entries.remove(k);
            saveEntries.run();
            refreshList.run();
            abbrField.setText("");
            fullField.setText("");
        });
        closeBtn.addActionListener(e -> dlg.dispose());

        dlg.add(listScroll, BorderLayout.CENTER);
        dlg.add(inputPanel, BorderLayout.NORTH);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    /**
     * 更改时段：编辑 schedule.txt 的时间行（周一~周五共用）。
     * 保存时自动将各课程行的课程数对齐到新的时段数（类比 Excel 增删列：
     * 加时段自动补空课位，减时段自动截断多余课程）。
     */
    private void showTimeEditor() {
        List<String> lines = readAllLines(Main.schedule);
        String timeLine = lines.isEmpty() ? "" : lines.get(0);

        JDialog dlg = new JDialog(this, "更改时段", true);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.setSize(360, 220);
        dlg.setLocationRelativeTo(this);

        JLabel hint = new JLabel("<html>周一到周五时间行（空格分隔，HH:mm 成对出现）。<br>保存后各天课程行自动按新时段数增删（多退少补空位）。</html>");
        hint.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        hint.setBorder(new EmptyBorder(8, 8, 0, 8));

        JTextArea area = new JTextArea(timeLine, 4, 32);
        area.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane areaScroll = new JScrollPane(area);
        areaScroll.setBorder(new EmptyBorder(4, 8, 4, 8));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        JButton saveBtn = new JButton("保存");
        JButton cancelBtn = new JButton("取消");
        saveBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        cancelBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);

        saveBtn.addActionListener(e -> {
            String text = area.getText().trim();
            if (!isValidTimeLine(text)) {
                JOptionPane.showMessageDialog(dlg, "时间格式不正确：需要成对的 HH:mm，用空格分隔", "错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            int pairs = text.trim().split("\\s+").length / 2;
            lines.set(0, text);
            // 对齐各课程行（周一~周五）：token 数 = 时段数，多退少补（"\"=无课）
            for (int i = 1; i < lines.size(); i++) {
                String row = lines.get(i);
                if (row.trim().isEmpty()) continue;
                lines.set(i, alignTokens(row, pairs));
            }
            writeAllLines(Main.schedule, lines);
            dlg.dispose();
            refreshAfterSettingsSaved();
        });
        cancelBtn.addActionListener(e -> dlg.dispose());

        dlg.add(hint, BorderLayout.NORTH);
        dlg.add(areaScroll, BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    /**
     * 周六时段与轮次：编辑 saturday.txt 的时间行与周次轮转行（行数可自由增删）。
     * 文件不存在时自动创建（时间行沿用周中时间，轮次行为占位）。
     */
    private void showSaturdayEditor() {
        List<String> lines = readAllLines(Main.saturday);
        if (lines.isEmpty()) {
            // 创建默认模板：时间行取 schedule.txt 的时间行
            List<String> schedLines = readAllLines(Main.schedule);
            lines.add(schedLines.isEmpty() ? "01:30 02:00 03:30 04:50 05:00 06:25 07:00 08:10 09:25 10:00 10:30 11:00 12:00 13:00" : schedLines.get(0));
            int pairs = lines.get(0).trim().split("\\s+").length / 2;
            StringBuilder row = new StringBuilder("六");
            for (int i = 1; i < pairs; i++) row.append(" \\");
            for (int i = 0; i < 5; i++) {
                lines.add(row.toString());
            }
        }

        JDialog dlg = new JDialog(this, "周六时段与轮次", true);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.setSize(440, 460);
        dlg.setLocationRelativeTo(this);

        JLabel hint = new JLabel("<html>第 1 行：周六时间行（成对 HH:mm）。其余行：周次轮转课表（每行一个轮次，行首“六”是第一节课）。</html>");
        hint.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        hint.setBorder(new EmptyBorder(8, 8, 0, 8));

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

        JTextArea timeArea = new JTextArea(lines.get(0), 2, 36);
        timeArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane timeScroll = new JScrollPane(timeArea);
        timeScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        timeScroll.setPreferredSize(new Dimension(400, 55));
        centerPanel.add(new JLabel("时间行（" + lines.get(0).split("\\s+").length + " 个时间点）："));
        centerPanel.add(timeScroll);
        centerPanel.add(Box.createVerticalStrut(8));

        JTextArea rowsArea = new JTextArea(8, 36);
        rowsArea.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < lines.size(); i++) {
            sb.append(lines.get(i)).append("\n");
        }
        rowsArea.setText(sb.toString());
        JScrollPane rowsScroll = new JScrollPane(rowsArea);
        rowsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        rowsScroll.setPreferredSize(new Dimension(400, 220));
        centerPanel.add(new JLabel("轮次课表（每行一个轮次，可增删）："));
        centerPanel.add(rowsScroll);

        JPanel rowBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton addRowBtn = new JButton("+ 添加轮次");
        JButton delRowBtn = new JButton("- 删除最后一行");
        addRowBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        delRowBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        rowBtns.add(addRowBtn);
        rowBtns.add(delRowBtn);
        rowBtns.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(rowBtns);

        addRowBtn.addActionListener(e -> {
            String t = rowsArea.getText();
            String row = "六";
            String[] tt = timeArea.getText().trim().split("\\s+");
            int pairs = tt.length >= 2 && tt.length % 2 == 0 ? tt.length / 2 : 7;
            for (int i = 1; i < pairs; i++) row += " \\";
            rowsArea.setText(t + (t.isEmpty() ? "" : "\n") + row);
        });
        delRowBtn.addActionListener(e -> {
            String[] ls = rowsArea.getText().split("\n", -1);
            if (ls.length <= 1) {
                rowsArea.setText("");
                return;
            }
            StringBuilder sb2 = new StringBuilder();
            for (int i = 0; i < ls.length - 1; i++) {
                if (ls[i].trim().isEmpty()) continue;
                sb2.append(ls[i]).append("\n");
            }
            rowsArea.setText(sb2.toString());
        });

        JScrollPane centerScroll = new JScrollPane(centerPanel);
        centerScroll.setBorder(null);
        centerScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        JButton saveBtn = new JButton("保存");
        JButton cancelBtn = new JButton("取消");
        saveBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        cancelBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);

        saveBtn.addActionListener(e -> {
            // 用户输入直接通过（不做正则/时间格式校验）
            String timeText = timeArea.getText().trim();
            int pairs = timeText.split("\\s+").length / 2;
            List<String> out = new ArrayList<>();
            out.add(timeText);
            for (String line : rowsArea.getText().split("\n")) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                out.add(alignTokens(t, pairs));
            }
            if (out.size() == 1) {
                // 轮次被删光：自动补一行占位课表
                StringBuilder row = new StringBuilder("六");
                for (int i = 1; i < pairs; i++) row.append(" \\");
                out.add(row.toString());
            }
            writeAllLines(Main.saturday, out);
            dlg.dispose();
            refreshAfterSettingsSaved();
        });
        cancelBtn.addActionListener(e -> dlg.dispose());

        dlg.add(hint, BorderLayout.NORTH);
        dlg.add(centerScroll, BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    /**
     * 晚自习轮次：编辑 self_study.txt —— 第 0 行为自习时间行，
     * 其余行为周次轮转行（前 6 个 token 为周一~周六自习科目）。
     * 行数可自由增删。晚自习时间段独立存放于 night_study.txt。
     */
    private void showNightEditor() {
        List<String> lines = readAllLines(Main.sstudy);

        JDialog dlg = new JDialog(this, "晚自习轮次", true);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.setSize(460, 480);
        dlg.setLocationRelativeTo(this);

        JLabel hint = new JLabel("<html>文件 self_study.txt。第 1 行：自习时间（成对 HH:mm）。<br>其余行：周次轮转行（前 6 个 token 为周一~周六自习科目）。<br>晚自习时间段在 night_study.txt（周一~周五且本地开启时生效）。</html>");
        hint.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        hint.setBorder(new EmptyBorder(8, 8, 0, 8));

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

        JTextArea timeArea = new JTextArea(lines.isEmpty() ? "17:00 18:00" : lines.get(0), 2, 36);
        timeArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane timeScroll = new JScrollPane(timeArea);
        timeScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        timeScroll.setPreferredSize(new Dimension(420, 55));
        centerPanel.add(new JLabel("自习时间行："));
        centerPanel.add(timeScroll);
        centerPanel.add(Box.createVerticalStrut(8));

        JTextArea rowsArea = new JTextArea(7, 36);
        rowsArea.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < lines.size(); i++) {
            sb.append(lines.get(i)).append("\n");
        }
        rowsArea.setText(sb.toString());
        JScrollPane rowsScroll = new JScrollPane(rowsArea);
        rowsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        rowsScroll.setPreferredSize(new Dimension(420, 200));
        centerPanel.add(new JLabel("轮次行（每行一个轮次，可增删）："));
        centerPanel.add(rowsScroll);

        JPanel rowBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton addRowBtn = new JButton("+ 添加轮次");
        JButton delRowBtn = new JButton("- 删除最后一行");
        addRowBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        delRowBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        rowBtns.add(addRowBtn);
        rowBtns.add(delRowBtn);
        rowBtns.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(rowBtns);

        addRowBtn.addActionListener(e -> {
            String t = rowsArea.getText();
            String row = "\\ \\ \\ \\ \\ \\";
            rowsArea.setText(t + (t.isEmpty() ? "" : "\n") + row);
        });
        delRowBtn.addActionListener(e -> {
            String[] ls = rowsArea.getText().split("\n", -1);
            if (ls.length <= 1) {
                rowsArea.setText("");
                return;
            }
            StringBuilder sb2 = new StringBuilder();
            for (int i = 0; i < ls.length - 1; i++) {
                if (ls[i].trim().isEmpty()) continue;
                sb2.append(ls[i]).append("\n");
            }
            rowsArea.setText(sb2.toString());
        });

        JScrollPane centerScroll = new JScrollPane(centerPanel);
        centerScroll.setBorder(null);
        centerScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        JButton saveBtn = new JButton("保存");
        JButton cancelBtn = new JButton("取消");
        saveBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        cancelBtn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);

        saveBtn.addActionListener(e -> {
            // 用户输入直接通过（不做正则/时间格式校验）
            String timeText = timeArea.getText().trim();
            List<String> out = new ArrayList<>();
            out.add(timeText);
            for (String line : rowsArea.getText().split("\n")) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                out.add(t);
            }
            if (out.size() == 1) {
                // 轮次被删光：补一行默认自习行
                out.add("\\ \\ \\ \\ \\ \\");
            }
            writeAllLines(Main.sstudy, out);
            dlg.dispose();
            refreshAfterSettingsSaved();
        });
        cancelBtn.addActionListener(e -> dlg.dispose());

        dlg.add(hint, BorderLayout.NORTH);
        dlg.add(centerScroll, BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    /**
     * 设置保存后统一刷新：主课条 + 本窗口网格。
     */
    private void refreshAfterSettingsSaved() {
        try {
            Main.reload();
            reload();
        } catch (Exception ex) {
            Main.outputException(ex);
        }
    }

    // ==================================================================
    // 文件辅助
    // ==================================================================

    /** HTML 转义（用于更新状态标签的多行文本显示）。 */
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** HH:mm 时间点校验。 */
    private static final Pattern TIME_TOKEN = Pattern.compile("^\\d{1,2}:\\d{2}$");

    /**
     * 校验时间行：非空、token 数为偶数、每个 token 形如 HH:mm。
     */
    private static boolean isValidTimeLine(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length < 2 || tokens.length % 2 != 0) return false;
        for (String t : tokens) {
            if (!TIME_TOKEN.matcher(t).matches()) return false;
        }
        return true;
    }

    /**
     * 对齐课程行到指定 token 数（类比 Excel 增删列）：
     * token 多余则截断，不足则补 "\"（无课）。
     *
     * @param line  课程行文本
     * @param count 目标 token 数（= 时间对数）
     * @return 对齐后的课程行
     */
    private static String alignTokens(String line, int count) {
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length > count) {
            return String.join(" ", Arrays.copyOf(tokens, count));
        }
        List<String> list = new ArrayList<>(Arrays.asList(tokens));
        while (list.size() < count) {
            list.add("\\");
        }
        return String.join(" ", list);
    }

    /**
     * 读取文件全部行（UTF-8），文件不存在或读取失败时返回空列表。
     */
    private static List<String> readAllLines(File f) {
        List<String> lines = new ArrayList<>();
        if (f == null || !f.exists()) return lines;
        try (Scanner sc = new Scanner(f, StandardCharsets.UTF_8)) {
            while (sc.hasNextLine()) {
                lines.add(sc.nextLine());
            }
        } catch (IOException e) {
            Main.outputException(e);
        }
        return lines;
    }

    /**
     * 将全部行写回文件（UTF-8）。
     */
    private static void writeAllLines(File f, List<String> lines) {
        try (FileWriter fw = new FileWriter(f, StandardCharsets.UTF_8)) {
            for (String l : lines) {
                fw.write(l);
                fw.write("\n");
            }
        } catch (IOException e) {
            Main.outputException(e);
        }
    }

    /**
     * 执行临时换课：将两个选中单元格的课程互换。
     *
     * <p>换课以命令脚本形式追加到 {@code commands/swap.txt}（由 load.txt 每次 reload
     * 自动调用）：生成 {@code if %date% == MM.dd + setcourse} 块，目标日期当天生效。
     * 追加而非覆盖——脚本顺序执行，同一位置重复换课时后面的记录覆盖前面的，
     * 因此再次点击同一对可换回。</p>
     */
    private void applySwap() {
        if (swap[1] == null) {
            JOptionPane.showMessageDialog(this, "请先选择两个课程单元格", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        UnitPane a = swap[0];
        UnitPane b = swap[1];

        // 排除课间与空位（第一节课是真实课程，允许参与换课）
        String breakName = Main.iifr != null ? Main.iifr.get("|") : "|";
        if (a.course.isEmpty() || a.course.equals(breakName)
                || b.course.isEmpty() || b.course.equals(breakName)) {
            JOptionPane.showMessageDialog(this, "课间/空位不可参与换课，请重新选择两个课程", "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 解析 position → 日期 + 索引
        int[] pa = parsePosition(a.position);
        int[] pb = parsePosition(b.position);
        if (pa == null || pb == null) {
            JOptionPane.showMessageDialog(this, "单元格位置无效", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        StringBuilder sb = new StringBuilder();
        String dateA = String.format("%02d.%02d", pa[0], pa[1]);
        String dateB = String.format("%02d.%02d", pb[0], pb[1]);
        if (dateA.equals(dateB)) {
            // 同一天：一个 if 块内两条 setcourse（顺序执行 = 互换）
            sb.append("# 窗口换课 ").append(dateA).append(" 第").append(pa[2])
                    .append("节 ⇄ 第").append(pb[2]).append("节\n");
            sb.append("if %date% == ").append(dateA).append('\n');
            sb.append("  setcourse ").append(pa[2]).append(' ').append(b.course).append('\n');
            sb.append("  setcourse ").append(pb[2]).append(' ').append(a.course).append('\n');
            sb.append("endif\n");
        } else {
            sb.append("# 窗口换课 ").append(dateA).append(" 第").append(pa[2]).append("节 ⇄ ")
                    .append(dateB).append(" 第").append(pb[2]).append("节\n");
            sb.append("if %date% == ").append(dateA).append('\n');
            sb.append("  setcourse ").append(pa[2]).append(' ').append(b.course).append('\n');
            sb.append("endif\n");
            sb.append("if %date% == ").append(dateB).append('\n');
            sb.append("  setcourse ").append(pb[2]).append(' ').append(a.course).append('\n');
            sb.append("endif\n");
        }

        if (!Main.commandsDir.exists()) {
            Main.commandsDir.mkdirs();
        }
        File swapFile = new File(Main.commandsDir, "swap.txt");
        if (!swapFile.exists()) {
            try {
                swapFile.createNewFile();
            } catch (IOException e) {
                Main.outputException(e);
                return;
            }
        }
        try (FileWriter fw = new FileWriter(swapFile, StandardCharsets.UTF_8, true)) {
            fw.write(sb.toString());
        } catch (IOException e) {
            Main.outputException(e);
            return;
        }

        // 清除选择并刷新
        a.setSelected(false);
        b.setSelected(false);
        swap[0] = swap[1] = null;
        try {
            Main.reload();
            reload();
        } catch (Exception e) {
            Main.outputException(e);
        }
        System.out.println("[MainFrame] 临时换课: " + a.position + " ⇄ " + b.position);
    }

    /**
     * 解析 position（year@month@day@index）→ [month, day, index]；无效返回 null。
     */
    private static int[] parsePosition(String position) {
        try {
            String[] p = position.split("@");
            if (p.length != 4) return null;
            return new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 处理课程单元格点击，维护两个选中槽位（用于【课表】模式换课与双击更换）。
     */
    private void handleUnitPaneClick(UnitPane up) {
        if (up.course.isEmpty()) return;
        if (swap[0] != null && !swap[0].equals(up)) {
            if (swap[1] == null) {
                swap[1] = up;
                swap[1].setSelected(true);
            } else if (swap[1].equals(up)) {
                swap[1].setSelected(false);
                swap[1] = null;
            }
        } else if (swap[0] == null) {
            swap[0] = up;
            swap[0].setSelected(true);
        } else {
            swap[0].setSelected(false);
            swap[0] = swap[1];
            swap[1] = null;
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        try {
            Main.reload();
        } catch (Exception e) {
            Main.outputException(e);
        }
    }

    // ==================================================================
    // 内部类：编辑模式下拉选择框 / 课程单元格
    // ==================================================================

    /**
     * 编辑模式课程单元格：内嵌下拉选择框，选择课程即永久写入课表文件。
     */
    private final class ComboCell extends JPanel {
        /** 位置标识，格式 "year@month@day@index"。 */
        private final String position;
        /** 下拉选择框（显示课程全称）。 */
        private final JComboBox<String> combo = new JComboBox<>();

        ComboCell(String position, String abbr) {
            this.position = position;
            setLayout(new BorderLayout());
            setOpaque(false);
            setPreferredSize(new Dimension(70, 50));

            // 当前课程的显示名（优先全称，无映射时退化为简称）
            String currentFull = fullToAbbr.entrySet().stream()
                    .filter(e -> e.getValue().equals(abbr))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse(abbr);

            combo.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            combo.setBackground(Color.WHITE);
            combo.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 200)),
                    BorderFactory.createEmptyBorder(1, 3, 1, 3)));
            for (String n : fullNames) {
                combo.addItem(n);
            }
            combo.setSelectedItem(currentFull);

            // 选择变化 → 永久写入课表文件（初始化 setSelectedItem 在监听器添加前，不触发）
            combo.addActionListener(e -> {
                Object sel = combo.getSelectedItem();
                if (sel == null) return;
                String newAbbr = fullToAbbr.get(sel.toString());
                if (newAbbr == null || newAbbr.isEmpty() || newAbbr.equals(abbr)) return;
                applyChangeToCell(position, newAbbr);
            });

            add(combo, BorderLayout.CENTER);
        }
    }

    /**
     * 课程单元格：显示一门课的简称，支持选中高亮与点击事件。
     */
    private static final class UnitPane extends JPanel {
        /** 位置标识，格式 "year@month@day@index"。 */
        public final String position;
        /** 课程显示名称。 */
        public final String course;
        /** 是否被选中。 */
        private boolean selected = false;
        /** 点击回调。 */
        private ActionListener actionListener;
        /** 双击回调（用于直接更换课程）。 */
        private Runnable doubleClickListener;

        UnitPane(String position, String course) {
            this.position = position;
            this.course = course == null ? "" : course;
            setPreferredSize(new Dimension(70, 50));
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (course.isEmpty()) return;
                    if (e.getClickCount() == 2) {
                        // 双击：直接更换课程（先触发单击选中逻辑由调用方处理）
                        if (doubleClickListener != null) {
                            doubleClickListener.run();
                        }
                        return;
                    }
                    if (actionListener == null) return;
                    int idx;
                    try {
                        String[] parts = position.split("@");
                        idx = Integer.parseInt(parts[parts.length - 1]);
                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
                        idx = 0;
                    }
                    actionListener.actionPerformed(
                            new ActionEvent(UnitPane.this, idx, UnitPane.this.toString()));
                }
            });
        }

        void setSelected(boolean selected) {
            if (course.isEmpty()) return;
            this.selected = selected;
            repaint();
        }

        void addActionListener(ActionListener listener) {
            this.actionListener = listener;
        }

        void setDoubleClickListener(Runnable listener) {
            this.doubleClickListener = listener;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 10;

            if (selected) {
                // 选中：柔和的主题色背景（混入 62% 白色降低饱和度）+ 主题色描边
                Color theme = Main.mw != null && Main.mw.theme != null ? Main.mw.theme : new Color(100, 180, 255);
                g2.setColor(lighten(theme, 0.62f));
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(theme);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            } else if (course.isEmpty()) {
                // 空位：浅灰底
                g2.setColor(new Color(246, 246, 246));
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(new Color(228, 228, 228));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            } else {
                // 普通课程：白底 + 浅灰描边
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(new Color(215, 215, 215));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }

            // 文字：始终深色，水平垂直居中（保证亮色主题下文字可读，不会被白色覆盖）
            if (!course.isEmpty()) {
                g2.setColor(new Color(45, 45, 45));
                g2.setFont(CELL_FONT);
                FontMetrics fm = g2.getFontMetrics();
                int textX = (w - fm.stringWidth(course)) / 2;
                int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(course, textX, textY);
            }
        }

        @Override
        public String toString() {
            return position + "@" + course;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof UnitPane)) return false;
            return Objects.equals(position, ((UnitPane) obj).position);
        }

        @Override
        public int hashCode() {
            return Objects.hash(position);
        }
    }

    // ==================================================================
    // 内部类：时间段显示
    // ==================================================================

    /**
     * 时间段单元格：上下两行分别显示开始与结束时间。
     */
    private static final class TimePane extends JPanel {
        private final String start;
        private final String end;

        TimePane(String start, String end) {
            this.start = start;
            this.end = end;
            setPreferredSize(new Dimension(70, 50));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(TIME_FONT);
            g2.setColor(new Color(130, 130, 130));
            FontMetrics fm = g2.getFontMetrics();
            int w = getWidth();
            int h = getHeight();
            int midY = h / 2;
            // 开始时间（上半行居中）
            int sX = (w - fm.stringWidth(start)) / 2;
            g2.drawString(start, sX, midY - 2);
            // 结束时间（下半行居中）
            int eX = (w - fm.stringWidth(end)) / 2;
            g2.drawString(end, eX, midY + fm.getAscent() + 2);
        }
    }
}
