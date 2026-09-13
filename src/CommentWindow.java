import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 屏幕便签 / 评论窗口。
 *
 * <p>一个半透明圆角的无边框小窗口，支持：</p>
 * <ul>
 *   <li>鼠标拖拽移动（继承自 {@link SimpleWindow}）；</li>
 *   <li>右上角关闭按钮（悬停高亮）；</li>
 *   <li>显示一段文本内容；</li>
 *   <li>关闭时自动递减全局计数 {@link MainWindow#commentNumber}。</li>
 * </ul>
 */
public class CommentWindow extends SimpleWindow {

    /** 关闭按钮边长（像素）。 */
    private static final int CLOSE_BTN_SIZE = 14;

    /** 关闭按钮距右上角的内边距。 */
    private static final int CLOSE_BTN_MARGIN = 8;

    /** 便签文本内容。 */
    private String context = "";

    /** 鼠标是否悬停在关闭按钮上（用于高亮反馈）。 */
    private boolean closeHover = false;

    /**
     * 构造一个便签窗口，默认启用拖拽与透明背景。
     */
    public CommentWindow() {
        super();
        setBackground(new Color(0, 0, 0, 0));
        setDraggable(true);

        // 关闭按钮点击检测 + 悬停反馈
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (getCloseButtonBounds().contains(e.getPoint())) {
                    cancelDrag(); // 点击关闭按钮时不触发拖拽
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (getCloseButtonBounds().contains(e.getPoint())) {
                    dispose();
                } else if (e.getClickCount() == 2) {
                    // 双击编辑便签内容
                    String input = JOptionPane.showInputDialog(CommentWindow.this,
                            "编辑便签内容：", context);
                    if (input != null) {
                        setCommentContext(input);
                    }
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                updateHover(e.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeHover = false;
                repaint();
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getPoint());
            }
        });

        // 关闭时递减全局便签计数
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (MainWindow.commentNumber > 0) {
                    MainWindow.commentNumber--;
                }
            }
        });
    }

    /**
     * 设置便签显示的文本内容。
     *
     * @param context 文本内容
     */
    public void setCommentContext(String context) {
        this.context = context == null ? "" : context;
        repaint();
    }

    /**
     * 获取关闭按钮在窗口内的边界矩形。
     *
     * @return 关闭按钮区域
     */
    private Rectangle getCloseButtonBounds() {
        return new Rectangle(
                getWidth() - CLOSE_BTN_MARGIN - CLOSE_BTN_SIZE,
                CLOSE_BTN_MARGIN,
                CLOSE_BTN_SIZE,
                CLOSE_BTN_SIZE
        );
    }

    /**
     * 根据鼠标位置更新关闭按钮悬停状态。
     *
     * @param p 鼠标在窗口内的坐标
     */
    private void updateHover(Point p) {
        boolean hover = getCloseButtonBounds().contains(p);
        if (hover != closeHover) {
            closeHover = hover;
            repaint();
        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 半透明圆角背景
        g2d.setColor(new Color(0, 0, 0, 90));
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);

        // 顶部细边框装饰线
        g2d.setColor(new Color(255, 255, 255, 40));
        g2d.drawLine(8, 4, getWidth() - 8, 4);

        // 关闭按钮（悬停时红色高亮）
        Rectangle btn = getCloseButtonBounds();
        if (closeHover) {
            g2d.setColor(new Color(255, 80, 80, 200));
            g2d.fillRoundRect(btn.x - 2, btn.y - 2, btn.width + 4, btn.height + 4, 4, 4);
            g2d.setColor(Color.WHITE);
        } else {
            g2d.setColor(new Color(220, 220, 220));
        }
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawLine(btn.x, btn.y, btn.x + btn.width, btn.y + btn.height);
        g2d.drawLine(btn.x + btn.width, btn.y, btn.x, btn.y + btn.height);

        // 文本内容（支持格式化文本：#cRRGGBB 字体色 / #gRRGGBB 背景色 / #i#b#u#d 样式 / #r 重置）
        if (!context.isEmpty()) {
            Font baseFont = new Font("微软雅黑", Font.PLAIN, 14);
            FontMetrics baseFm = g2d.getFontMetrics(baseFont);
            int textY = CLOSE_BTN_MARGIN + CLOSE_BTN_SIZE + 8 + baseFm.getAscent();
            int textX = 12;
            java.util.List<MainWindow.Seg> segs = MainWindow.parseSegments(context);
            for (MainWindow.Seg s : segs) {
                if (s.text().isEmpty()) continue;
                Font sf = baseFont.deriveFont(
                        (s.bold() ? Font.BOLD : 0) | (s.italic() ? Font.ITALIC : 0));
                FontMetrics sfm = g2d.getFontMetrics(sf);
                int tw = sfm.stringWidth(s.text());
                // 背景色
                if (s.background() != null) {
                    g2d.setColor(s.background());
                    g2d.fillRect(textX, textY - baseFm.getAscent(), tw, baseFm.getHeight());
                }
                // 文字
                g2d.setColor(s.color());
                g2d.setFont(sf);
                g2d.drawString(s.text(), textX, textY);
                // 下划线
                if (s.underline()) {
                    g2d.drawLine(textX, textY + 2, textX + tw, textY + 2);
                }
                // 删除线
                if (s.strike()) {
                    int mid = textY - baseFm.getAscent() / 3;
                    g2d.drawLine(textX, mid, textX + tw, mid);
                }
                textX += tw;
            }
        }

        g2d.dispose();
    }
}
