import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * 圆圈扩散淡出动画窗口。
 *
 * <p>在屏幕指定位置显示一个从中心向外扩散、同时逐渐淡出的圆圈，
 * 常用于通知到达时的视觉反馈。动画结束后窗口自动关闭。</p>
 *
 * <p>动画分两阶段：</p>
 * <ol>
 *   <li><b>放大阶段</b>：圆圈半径从 0 线性增长到 maxRadius；</li>
 *   <li><b>淡出阶段</b>：半径继续缓慢增长，同时透明度线性衰减至 0。</li>
 * </ol>
 */
public class CircleAnimationWindow extends JWindow {

    /** 圆圈在屏幕上的中心 X 坐标。 */
    private final int centerX;

    /** 圆圈在屏幕上的中心 Y 坐标。 */
    private final int centerY;

    /** 圆圈最大半径（像素）。 */
    private final int maxRadius;

    /** 圆圈颜色。 */
    private final Color circleColor;

    /** 当前半径。 */
    private float currentRadius;

    /** 放大阶段的半径增长速度（像素/帧）。 */
    private float expansionSpeed = 5.0f;

    /** 淡出阶段的透明度衰减速度（0~1/帧）。 */
    private static final float FADE_SPEED = 0.05f;

    /** 当前透明度（0~1）。 */
    private float currentAlpha = 1.0f;

    /** {@code true} 表示处于放大阶段，{@code false} 表示处于淡出阶段。 */
    private boolean isExpanding;

    /** 动画定时器（约 60 FPS）。 */
    private Timer animationTimer;

    /**
     * 构造动画窗口。
     *
     * @param centerX   屏幕中心 X
     * @param centerY   屏幕中心 Y
     * @param maxRadius 最大半径（像素）
     * @param color     圆圈颜色
     */
    public CircleAnimationWindow(int centerX, int centerY, int maxRadius, Color color) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.maxRadius = maxRadius;
        this.circleColor = color;
        this.currentRadius = 0;
        this.isExpanding = true;
        initializeWindow();
        setupAnimation();
    }

    /**
     * 初始化窗口位置、大小、透明背景与绘制面板。
     */
    private void initializeWindow() {
        // 窗口比最大半径稍大，避免圆圈边缘被裁剪
        int windowSize = maxRadius * 2 + 100;
        setLocation(centerX - windowSize / 2, centerY - windowSize / 2);
        setSize(windowSize, windowSize);
        setBackground(new Color(0, 0, 0, 0));

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawCircle(g);
            }
        };
        panel.setOpaque(false);
        setContentPane(panel);
    }

    /**
     * 设置动画定时器（约 60 FPS）。
     */
    private void setupAnimation() {
        animationTimer = new Timer(16, e -> {
            updateAnimation();
            repaint();
        });
    }

    /**
     * 每帧更新动画状态。
     */
    private void updateAnimation() {
        if (isExpanding) {
            currentRadius += expansionSpeed;
            if (currentRadius >= maxRadius) {
                currentRadius = maxRadius;
                isExpanding = false;
                expansionSpeed *= 0.3f; // 进入淡出阶段后减速
            }
        } else {
            currentRadius += expansionSpeed;
            currentAlpha -= FADE_SPEED;
            if (currentAlpha <= 0) {
                animationTimer.stop();
                setVisible(false);
                dispose();
            }
        }
    }

    /**
     * 在面板中心绘制当前帧的圆圈。
     *
     * @param g 图形上下文
     */
    private void drawCircle(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setStroke(new BasicStroke(3.0f));

        int alpha = Math.max(0, Math.min(255, (int) (currentAlpha * 255)));
        g2d.setColor(new Color(circleColor.getRed(), circleColor.getGreen(), circleColor.getBlue(), alpha));

        int windowCenterX = getWidth() / 2;
        int windowCenterY = getHeight() / 2;
        float x = windowCenterX - currentRadius;
        float y = windowCenterY - currentRadius;
        g2d.draw(new Ellipse2D.Float(x, y, currentRadius * 2, currentRadius * 2));
        g2d.dispose();
    }

    /**
     * 显示窗口并启动动画。
     */
    public void startAnimation() {
        setVisible(true);
        animationTimer.start();
    }
}
