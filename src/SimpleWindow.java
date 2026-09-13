import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Objects;

/**
 * 可拖拽、可塑形的无边框窗口基类。
 *
 * <p>提供以下能力：</p>
 * <ul>
 *   <li>鼠标拖拽移动窗口，可限定拖拽热区；</li>
 *   <li>屏幕边缘自动吸附（{@link #snapDistance}）；</li>
 *   <li>通过 {@link #setShape(Shape)} 设置窗口形状并自动开启透明背景。</li>
 * </ul>
 *
 * <p>子类可直接继承并在构造中调用 {@link #setDraggable(boolean)} 启用拖拽。</p>
 */
public class SimpleWindow extends JWindow {

    /** 是否允许拖拽。 */
    public boolean draggable = false;

    /** 拖拽热区（窗口内坐标）。为 {@code null} 时表示整个窗口可拖。 */
    public Shape draggableArea;

    /** 距屏幕边缘多少像素内触发吸附，0 表示不吸附。 */
    public int snapDistance = 0;

    /** 当前是否处于吸附状态。 */
    public boolean snapped = false;

    /** 窗口形状，为 {@code null} 时使用默认矩形。 */
    private Shape windowShape;

    /** 拖拽起始点（窗口内坐标），(-1,-1) 表示未在拖拽。子类可通过 {@link #cancelDrag()} 重置。 */
    private final Point dragPoint = new Point(-1, -1);

    /**
     * 取消当前拖拽操作。子类可在鼠标按下时检测到特殊热区（如关闭按钮）后调用，
     * 使本次按下不触发窗口移动。
     */
    protected void cancelDrag() {
        dragPoint.x = -1;
        dragPoint.y = -1;
    }

    /** 鼠标监听器（启用拖拽时挂载，禁用时卸载）。 */
    private final MouseListener mouseListener;

    /** 鼠标移动监听器。 */
    private final MouseMotionListener mouseMotionListener;

    /**
     * 构造一个无边框窗口，并初始化拖拽监听器（默认未启用）。
     */
    public SimpleWindow() {
        super();
        mouseListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragPoint.x = e.getX();
                dragPoint.y = e.getY();
                // 若指定了热区且点击点不在热区内，则忽略本次拖拽
                if (draggableArea != null && !draggableArea.contains(dragPoint)) {
                    dragPoint.x = -1;
                    dragPoint.y = -1;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragPoint.x = -1;
                dragPoint.y = -1;
            }
        };
        mouseMotionListener = new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragPoint.x == -1 || dragPoint.y == -1) {
                    return;
                }
                int locX = getX() + e.getX() - dragPoint.x;
                int locY = getY() + e.getY() - dragPoint.y;
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

                // 上下左右边缘吸附
                if (locY < snapDistance) {
                    locY = 0;
                    snapped = true;
                } else if (locY > screen.height - getHeight() - snapDistance) {
                    locY = screen.height - getHeight();
                    snapped = true;
                } else {
                    snapped = false;
                }
                if (locX < snapDistance) {
                    locX = 0;
                } else if (locX > screen.width - getWidth() - snapDistance) {
                    locX = screen.width - getWidth();
                }
                setLocation(locX, locY);
            }
        };
    }

    /**
     * 启用或禁用窗口拖拽。
     *
     * @param draggable {@code true} 启用，{@code false} 禁用
     */
    public void setDraggable(boolean draggable) {
        this.draggable = draggable;
        if (draggable) {
            if (draggableArea == null) {
                draggableArea = new Rectangle(0, 0, getWidth(), getHeight());
            }
            addMouseListener(mouseListener);
            addMouseMotionListener(mouseMotionListener);
        } else {
            removeMouseListener(mouseListener);
            removeMouseMotionListener(mouseMotionListener);
        }
    }

    /**
     * 设置拖拽热区。
     *
     * @param area 热区形状，{@code null} 表示整个窗口
     */
    public void setDraggableArea(Shape area) {
        this.draggableArea = area;
    }

    /**
     * 设置窗口形状，并自动配置透明背景与前景色。
     *
     * @param shape 窗口形状，{@code null} 恢复默认矩形
     */
    @Override
    public void setShape(Shape shape) {
        this.windowShape = shape;
        if (shape != null) {
            setForeground(Color.WHITE);
            setBackground(new Color(0, 0, 0, 0));
            // 尝试调用平台级窗口塑形（部分 JDK / 平台支持）
            try {
                super.setShape(shape);
            } catch (UnsupportedOperationException | SecurityException ex) {
                System.err.println("[SimpleWindow] 平台不支持 setShape，回退到自绘模式：" + ex.getMessage());
            }
        }
        repaint();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (windowShape != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(getForeground());
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.fill(windowShape);
            g2.dispose();
        }
    }
}
