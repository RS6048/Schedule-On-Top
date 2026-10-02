import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 顶部滑入式通知窗口。
 *
 * <p>功能特性：</p>
 * <ul>
 *   <li>从屏幕顶部平滑滑入，自动居中；</li>
 *   <li>文字超出宽度时水平滚动显示；</li>
 *   <li>底部进度条随倒计时缩减；</li>
 *   <li>到达显示时长后自动滑出并关闭；</li>
 *   <li>鼠标点击可立即关闭；</li>
 *   <li>关闭时触发 {@link #setInvokeOnClose(ActionListener)} 注册的回调。</li>
 * </ul>
 *
 * <p>所有动画均基于 {@link Timer}，不阻塞事件 dispatch 线程。</p>
 */
public class NoticeWindow extends JWindow {

    /** 通知窗口使用的字体（与主窗口一致）。 */
    public static final Font FONT = MainWindow.f;

    /** 窗口固定宽度。 */
    private static final int WIDTH = 500;

    /** 有内容时的窗口高度。 */
    private static final int HEIGHT = 30;

    /** 倒计时进度条高度。 */
    private static final int PROGRESS_HEIGHT = 4;

    /** 滑入 / 滑出动画每帧移动的像素数。 */
    private static final int SLIDE_STEP = 4;

    /** 动画 / 倒计时定时器间隔（毫秒）。 */
    private static final int TICK_INTERVAL = 20;

    /** 文字滚动每帧移动的像素数。 */
    private static final int SCROLL_STEP = 2;

    /** 显示时长（毫秒）。 */
    private final int displayMillis;

    /** 通知文本。 */
    private final String detail;

    /** 关闭回调。 */
    private ActionListener closeCallback;

    /** 倒计时剩余帧数（每帧 TICK_INTERVAL 毫秒）。 */
    private int remainingTicks;

    /** 倒计时总帧数（用于进度条比例计算）。 */
    private final int totalTicks;

    // ---- 主课条联动 ----
    /** 正在显示（滑入中/可见中）且参与课表联动的通知数量。 */
    private static int activeCount = 0;

    /** 是否因通知显示而隐藏了主悬浮课条（仅当通知显示前课条可见时为 true）。 */
    private static boolean mainHiddenByNotice = false;

    /** 本实例是否已计入 activeCount（防止共享实例重复显示时重复计数）。 */
    private boolean counted = false;

    /** 是否不联动主课条（显示时不隐藏课表，关闭时不恢复）。 */
    private boolean noMainLink = false;

    /**
     * 设置本通知<b>不联动</b>主悬浮课条（如"请前往系统托盘"警告：
     * 显示时不隐藏课表，关闭后也不自动恢复课表）。
     *
     * @return 本实例（便于链式调用）
     */
    public NoticeWindow setNoMainLink() {
        this.noMainLink = true;
        return this;
    }

    /**
     * 通知显示/关闭时同步主悬浮课条的显隐：有参与联动的通知正在显示时隐藏课条，
     * 全部关闭后恢复（仅恢复因通知而隐藏的情况，用户主动隐藏不被覆盖）。
     */
    private static void syncMainWindow() {
        if (Main.mw == null) {
            return;
        }
        if (activeCount > 0) {
            if (Main.mw.isVisible() && !mainHiddenByNotice) {
                mainHiddenByNotice = true;
                Main.mw.setVisible(false);
            }
        } else if (mainHiddenByNotice) {
            mainHiddenByNotice = false;
            Main.mw.setVisible(true);
            FrameTray.setLabel();
        }
    }

    /** 文字滚动当前 X 偏移。 */
    private int scrollX;

    /** 文字是否已完全滚出左侧（用于重置到右侧）。 */
    private boolean textScrolledOut = false;

    /** 滑入滑出动画定时器。 */
    private Timer animationTimer;

    /** 倒计时与滚动定时器。 */
    private Timer countdownTimer;

    /** 当前窗口状态。 */
    private enum State { HIDDEN, SLIDING_IN, VISIBLE, SLIDING_OUT }
    private State state = State.HIDDEN;

    /**
     * 构造通知窗口。
     *
     * @param detail 通知文本，为空字符串时窗口仅显示一条细进度条
     * @param millis 显示时长（毫秒）
     */
    public NoticeWindow(String detail, int millis) {
        this.detail = detail == null ? "" : detail;
        this.displayMillis = Math.max(TICK_INTERVAL, millis);
        this.totalTicks = displayMillis / TICK_INTERVAL;
        this.remainingTicks = totalTicks;

        setAlwaysOnTop(true);
        setSize(WIDTH, detail.isEmpty() ? PROGRESS_HEIGHT : HEIGHT);
        setBackground(new Color(0, 0, 0, 0));
        setLayout(null);

        // 点击关闭
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dispose();
            }
        });

        setupTimers();
    }

    /**
     * 初始化动画与倒计时定时器。
     */
    private void setupTimers() {
        // 滑入 / 滑出动画
        animationTimer = new Timer(TICK_INTERVAL, e -> {
            int y = getY();
            if (state == State.SLIDING_IN) {
                y += SLIDE_STEP;
                if (y >= 0) {
                    y = 0;
                    state = State.VISIBLE;
                    animationTimer.stop();
                    countdownTimer.start();
                }
                setLocation(getX(), y);
            } else if (state == State.SLIDING_OUT) {
                y -= SLIDE_STEP;
                if (y <= -getHeight()) {
                    y = -getHeight();
                    state = State.HIDDEN;
                    animationTimer.stop();
                    super.setVisible(false);
                    fireCloseCallback();
                    super.dispose();
                }
                setLocation(getX(), y);
            }
        });

        // 倒计时 + 文字滚动
        countdownTimer = new Timer(TICK_INTERVAL, e -> {
            remainingTicks--;
            // 文字过长时滚动
            if (needScroll()) {
                scrollX -= SCROLL_STEP;
                if (scrollX < -getTextWidth()) {
                    scrollX = WIDTH;
                }
            }
            repaint();
            if (remainingTicks <= 0) {
                dispose();
            }
        });
    }

    /**
     * 注册窗口关闭时的回调。
     *
     * @param invoker 回调监听器
     */
    public void setInvokeOnClose(ActionListener invoker) {
        this.closeCallback = invoker;
    }

    /**
     * 触发关闭回调，并（若本通知参与联动）从活跃计数中移除、恢复主课条。
     */
    private void fireCloseCallback() {
        if (counted) {
            counted = false;
            if (!noMainLink) {
                activeCount--;
            }
            syncMainWindow();
        }
        if (closeCallback != null) {
            closeCallback.actionPerformed(new ActionEvent(this, 0, "closed"));
        }
    }

    /**
     * 显示圆圈扩散动画特效（通知到达反馈）。
     *
     * @param c 圆圈颜色
     */
    public void showNotify(Color c) {
        CircleAnimationWindow caw = new CircleAnimationWindow(
                getLocation().x + WIDTH / 2, 300, 130, c);
        caw.startAnimation();
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            if (state == State.VISIBLE || state == State.SLIDING_IN) {
                return;
            }
            // 定位到屏幕中央水平位置、顶部之外
            int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
            setLocation((screenWidth - WIDTH) / 2, -getHeight());
            remainingTicks = totalTicks;
            scrollX = needScroll() ? WIDTH : (WIDTH - getTextWidth()) / 2;
            super.setVisible(true);
            // 计入活跃通知并同步主课条（仅首次显示；同一实例重复显示不重复计数）
            if (!counted) {
                counted = true;
                if (!noMainLink) {
                    activeCount++;
                }
                syncMainWindow();
            }
            state = State.SLIDING_IN;
            animationTimer.start();
        } else {
            if (state == State.HIDDEN || state == State.SLIDING_OUT) {
                return;
            }
            countdownTimer.stop();
            state = State.SLIDING_OUT;
            animationTimer.start();
        }
    }

    @Override
    public void dispose() {
        if (state == State.HIDDEN) {
            fireCloseCallback();
            super.dispose();
            return;
        }
        countdownTimer.stop();
        if (state == State.VISIBLE) {
            state = State.SLIDING_OUT;
            animationTimer.start();
        }
        // 若正在滑入中，直接切换到滑出
        if (state == State.SLIDING_IN) {
            animationTimer.stop();
            state = State.SLIDING_OUT;
            animationTimer.start();
        }
    }

    /**
     * 判断文本是否需要水平滚动。
     *
     * @return {@code true} 表示文本宽度超过窗口
     */
    private boolean needScroll() {
        return getTextWidth() > WIDTH - 20;
    }

    /**
     * 计算当前文本的像素宽度。
     *
     * @return 文本宽度
     */
    private int getTextWidth() {
        return getFontMetrics(FONT).stringWidth(detail);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 底部倒计时进度条（空内容时也必须绘制，作为唯一可见元素）
        float progress = totalTicks > 0 ? (float) remainingTicks / totalTicks : 0;
        if (Main.mw != null && Main.mw.theme != null) {
            g2.setColor(Main.mw.theme);
        } else {
            g2.setColor(new Color(100, 180, 255));
        }
        g2.fillRect(0, getHeight() - PROGRESS_HEIGHT, (int) (WIDTH * progress), PROGRESS_HEIGHT);

        // 空内容窗口仅显示进度条（倒计时指示器），直接返回
        if (detail.isEmpty()) {
            g2.dispose();
            return;
        }

        // 半透明圆角背景
        g2.setColor(new Color(30, 30, 30, 200));
        g2.fillRoundRect(0, 0, WIDTH, HEIGHT, 8, 8);

        // 顶部主题色装饰线
        if (Main.mw != null && Main.mw.theme != null) {
            g2.setColor(Main.mw.theme);
            g2.fillRoundRect(0, 0, WIDTH, 2, 8, 8);
        }

        // 文本（裁剪到窗口范围内，支持滚动）
        g2.setColor(Color.WHITE);
        g2.setFont(FONT);
        g2.setClip(4, 0, WIDTH - 8, HEIGHT - PROGRESS_HEIGHT);
        int textY = (HEIGHT - PROGRESS_HEIGHT) / 2 + g2.getFontMetrics().getAscent() / 2 - 2;
        g2.drawString(detail, scrollX, textY);
        g2.setClip(null);

        g2.dispose();
    }
}
