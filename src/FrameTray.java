import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 系统托盘管理器。
 *
 * <p>在系统托盘中添加一个图标，提供右键菜单：</p>
 * <ul>
 *   <li><b>Hide / Show</b>：切换主悬浮课条的显示状态；</li>
 *   <li><b>Reload</b>：重新从文件加载课表配置；</li>
 *   <li><b>Exit</b>：退出程序。</li>
 * </ul>
 *
 * <p>左键单击托盘图标打开换课编辑窗口。</p>
 */
public class FrameTray {

    /** 系统托盘实例。 */
    private final SystemTray tray;

    /** 托盘图标。 */
    private final TrayIcon trayIcon;

    /** 右键弹出菜单。 */
    private final PopupMenu popupMenu = new PopupMenu();

    /** 显示 / 隐藏菜单项（文本随状态切换）。 */
    private static final MenuItem showItem = new MenuItem("Hide");

    /** 当前打开的换课窗口引用。 */
    private MainFrame myFrame;

    /**
     * 构造并注册系统托盘图标。若平台不支持系统托盘则弹出警告。
     */
    public FrameTray() {
        if (!SystemTray.isSupported()) {
            JOptionPane.showMessageDialog(
                    null,
                    "当前平台不支持系统托盘，部分功能（换课、隐藏/显示）将不可用。",
                    "警告",
                    JOptionPane.WARNING_MESSAGE
            );
            tray = null;
            trayIcon = null;
            return;
        }

        tray = SystemTray.getSystemTray();
        trayIcon = new TrayIcon(
                new ImageIcon(MainFrame.logo).getImage(),
                "悬浮课表",
                popupMenu
        );
        trayIcon.setImageAutoSize(true);

        buildPopupMenu();
        setupTrayIconMouseListener();

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            JOptionPane.showMessageDialog(
                    null,
                    e + "\n未能创建系统托盘组件\n这可能导致无法临时更改课表等问题",
                    "警告",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * 构建右键弹出菜单。
     */
    private void buildPopupMenu() {
        // 显示 / 隐藏
        showItem.addActionListener(e -> {
            if (Main.mw != null) {
                Main.mw.setVisible(!Main.mw.isVisible());
            }
            MainWindow.clickTimes = 0;
        });
        popupMenu.add(showItem);

        popupMenu.addSeparator();

        // 重新加载
        MenuItem reloadItem = new MenuItem("Reload");
        reloadItem.addActionListener(e -> {
            try {
                Main.reload();
                if (myFrame != null && myFrame.isVisible()) {
                    myFrame.reload();
                }
            } catch (Exception ex) {
                Main.outputException(ex);
            }
        });
        popupMenu.add(reloadItem);

        popupMenu.addSeparator();

        // 退出
        MenuItem quitItem = new MenuItem("Exit");
        quitItem.addActionListener(e -> {
            MainWindow.cd.setVisible(false);
            MainWindow.cd.dispose();
            if (tray != null && trayIcon != null) {
                tray.remove(trayIcon);
            }
            System.exit(0);
        });
        popupMenu.add(quitItem);
    }

    /**
     * 设置托盘图标鼠标监听：左键打开换课窗口。
     */
    private void setupTrayIconMouseListener() {
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    try {
                        myFrame = new MainFrame();
                        myFrame.setVisible(true);
                        myFrame.setExtendedState(Frame.NORMAL);
                    } catch (Exception ex) {
                        Main.outputException(ex);
                    }
                }
            }
        });
    }

    /**
     * 根据主窗口可见状态更新 "Hide / Show" 菜单项文本。
     */
    public static void setLabel() {
        if (Main.mw != null) {
            showItem.setLabel(Main.mw.isVisible() ? "Hide" : "Show");
        }
    }
}
