import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 全屏半透明幕布（splashscreen），由脚本命令 {@code splashscreen on/off} 控制。
 *
 * <ul>
 *   <li>{@code splashscreen on <text>}：显示幕布并在其上<b>新增一行</b>文本（多次调用累积多行）；</li>
 *   <li>{@code splashscreen off}：关闭幕布并清空新增行；</li>
 *   <li>幕布显示期间，解释器每执行一行命令都会更新"目前命令"文本；</li>
 *   <li><b>单击幕布</b>取消当前脚本执行；</li>
 *   <li><b>click 指令穿透</b>：click 执行前自动 {@link #suspend()}（隐式 off），
 *       执行完成后 {@link #resume()} 自动恢复，无需手动开关幕布。</li>
 * </ul>
 */
public final class ScriptSplash {

    private static JWindow window;
    private static final JLabel cmdLabel = new JLabel("", SwingConstants.CENTER);
    private static final List<String> extraLines = new ArrayList<>();
    private static volatile boolean visible = false;
    private static volatile boolean cancelled = false;
    /** suspend 时保存的额外文本行，用于 resume 恢复。 */
    private static List<String> suspendedLines = null;

    private ScriptSplash() {
    }

    /**
     * 显示幕布并新增一行文本（幕布已显示时仅追加该行）。线程安全。
     *
     * @param line 新增的文本行
     */
    public static void on(String line) {
        cancelled = false;
        runOnEdt(() -> {
            if (window == null) {
                createWindow();
            }
            if (line != null && !line.isEmpty()) {
                extraLines.add(line);
            }
            refreshLabel();
            window.setVisible(true);
            visible = true;
            window.repaint();
        });
    }

    /**
     * 关闭幕布并清空新增行。
     */
    public static void off() {
        runOnEdt(() -> {
            visible = false;
            suspendedLines = null;
            extraLines.clear();
            if (window != null) {
                window.setVisible(false);
            }
        });
    }

    /**
     * 临时隐藏幕布（click 指令执行前调用），保存当前状态供 {@link #resume()} 恢复。
     * 与 {@link #off()} 的区别：不清空额外文本行，resume 后完全恢复。
     */
    public static void suspend() {
        if (!visible || window == null) {
            return;
        }
        runOnEdt(() -> {
            if (!visible || window == null) {
                return;
            }
            suspendedLines = new ArrayList<>(extraLines);
            visible = false;
            window.setVisible(false);
        });
    }

    /**
     * 恢复 {@link #suspend()} 之前的幕布状态（click 指令执行完成后调用）。
     */
    public static void resume() {
        if (suspendedLines == null || window == null) {
            return;
        }
        runOnEdt(() -> {
            if (suspendedLines == null || window == null) {
                return;
            }
            extraLines.clear();
            extraLines.addAll(suspendedLines);
            suspendedLines = null;
            visible = true;
            refreshLabel();
            window.setVisible(true);
            window.repaint();
        });
    }

    /**
     * 更新幕布上的"目前命令"文本（幕布未显示时无操作）。
     *
     * @param command 当前正在解释的命令行
     */
    public static void update(String command) {
        if (!visible) {
            return;
        }
        runOnEdt(() -> {
            if (!visible || window == null) {
                return;
            }
            cmdLabel.setText(buildText(command));
            // 强制同步重绘：命令在 EDT 执行时 repaint() 异步排队会被阻塞，
            // 必须 paintImmediately 才能让当前命令实时显示在幕布上
            cmdLabel.revalidate();
            ((JComponent) window.getContentPane()).paintImmediately(
                    0, 0, window.getWidth(), window.getHeight());
            Toolkit.getDefaultToolkit().sync();
        });
    }

    /**
     * 幕布当前是否显示。
     */
    public static boolean isVisible() {
        return visible;
    }

    /**
     * 是否已被用户单击取消。
     */
    public static boolean isCancelled() {
        return cancelled;
    }

    /**
     * 重置取消标记（每次脚本执行开始时调用，确保上一次的取消状态不影响新执行）。
     */
    public static void resetCancelled() {
        cancelled = false;
    }

    // ==================================================================

    private static String buildText(String command) {
        StringBuilder sb = new StringBuilder("<html><div style='text-align:center;'>");
        synchronized (extraLines) {
            for (String l : extraLines) {
                sb.append(escapeHtml(l)).append("<br>");
            }
        }
        sb.append("正在执行命令...<br>单击幕布取消</div></html>");
        return sb.toString();
    }

    private static void refreshLabel() {
        cmdLabel.setText(buildText(""));
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(r);
            } catch (Exception e) {
                // EDT 繁忙时等待失败，降级为异步执行
                SwingUtilities.invokeLater(r);
            }
        }
    }

    private static void createWindow() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle bounds = gd.getDefaultConfiguration().getBounds();

        window = new JWindow();
        window.setAlwaysOnTop(true);
        window.setBackground(new Color(0, 0, 0, 100));
        window.setBounds(bounds);

        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // 全屏半透明黑幕布
                g2.setColor(new Color(0, 0, 0, 100));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(false);

        cmdLabel.setFont(new Font("微软雅黑", Font.BOLD, 22));
        cmdLabel.setForeground(Color.WHITE);
        panel.add(cmdLabel, BorderLayout.CENTER);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 单击幕布取消当前脚本执行
                cancelled = true;
            }
        });

        window.setContentPane(panel);
    }
}
