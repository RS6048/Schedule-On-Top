import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * 自动更新检查器。
 *
 * <p>从远程仓库（GitHub raw 或国内镜像）拉取 {@code update/version.json}，
 * 与内置（硬编码）版本号比对；有新版时<b>按远端 version.json 的 files 清单</b>
 * 下载文件覆盖本地源码（本地文件不参与文件列表的生成），再调用根目录
 * {@code createJar.bat} 编译打包（{@code out/production/ScrollSched} → {@code sc.jar}），
 * 完成后调用 {@code run.bat} 重启。两个 bat 缺失时均自动创建。</p>
 *
 * <p>清单容错：若远端清单中残留了新版本已删除的旧文件（如历史上的
 * {@code src/CommentWindow.java}），下载时该文件返回 404 会被跳过，不影响其余文件
 * 与整体更新流程——不会因单条过期清单条目而更新失败。</p>
 *
 * <p>镜像配置：镜像<b>保留完整地址头</b>（存于 {@code data/.local} 第 6 行，如
 * {@code https://ghproxy.net}、{@code gitee.com}）——仓库信息
 * （{@code RS6048/Schedule-On-Top/main}）在下载时由程序自动追加，
 * 无需在输入框填写完整 URL。镜像值一旦改变立即保存（无需保存按钮）；
 * 留空 = 默认 GitHub raw 直连。使用默认地址失败时自动依次尝试常见镜像前缀。</p>
 */
public class UpdateChecker {

    /** 仓库所有者与仓库名（用于默认更新源与自动追加仓库路径）。 */
    public static final String REPO_OWNER = "RS6048";
    public static final String REPO_NAME = "Schedule-On-Top";

    /** 默认镜像域名（GitHub raw）。 */
    public static final String DEFAULT_DOMAIN = "raw.githubusercontent.com";

    /** 默认 raw 根（main 分支）。 */
    public static final String DEFAULT_BASE =
            "https://" + DEFAULT_DOMAIN + "/" + REPO_OWNER + "/" + REPO_NAME + "/main";

    /** 直连失败时自动尝试的镜像前缀（拼接完整 URL，按序尝试）。 */
    private static final String[] FALLBACK_PREFIXES = {
            "https://ghproxy.net/",
            "https://mirror.ghproxy.com/"
    };

    /** 当前程序版本（硬编码于源码，与推送的 version.json 一致；不使用 version.txt）。 */
    public static final String LOCAL_VERSION = "1.8.3";

    /** 最近一次成功请求使用的 base（下载更新文件时复用）。 */
    private static String lastWorkingBase = null;

    /**
     * 更新信息：远程版本、说明与待更新文件清单。
     */
        public record UpdateInfo(String version, String note, List<String> files) {
            public UpdateInfo(String version, String note, List<String> files) {
                this.version = version;
                this.note = note == null ? "" : note;
                this.files = files == null ? new ArrayList<>() : files;
            }
        }

    private UpdateChecker() {
    }

    // ===== 镜像与版本 =====

    /**
     * 当前镜像地址（.local 第 6 行；空 = 默认 GitHub raw 直连）。
     *
     * <p>保留用户填写的完整地址（含 {@code https://} 等协议头），仅去除尾部斜杠。</p>
     *
     * @return 镜像地址（可含协议头，如 {@code https://ghproxy.net}）
     */
    public static String mirrorDomain() {
        String d = Main.mirrorDomain == null ? "" : Main.mirrorDomain.trim();
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d.isEmpty() ? DEFAULT_DOMAIN : d;
    }

    /**
     * 完整更新源 base：镜像地址（保留其 {@code https://} 头，缺省时自动补）+ 仓库信息
     * （{@code RS6048/Schedule-On-Top/main}）自动追加。
     *
     * @return 更新源 base URL（不含末尾斜杠）
     */
    public static String mirrorBase() {
        String domain = mirrorDomain();
        String base = (domain.startsWith("http://") || domain.startsWith("https://"))
                ? domain : "https://" + domain;
        return base + "/" + REPO_OWNER + "/" + REPO_NAME + "/main";
    }

    /**
     * 保存镜像域名到 {@code data/.local}（空串恢复默认直连）。镜像值一旦改变即调用，
     * 无需额外保存按钮。
     *
     * @param domain 用户填写的镜像域名
     * @throws IOException 写入 .local 失败时抛出
     */
    public static void saveMirror(String domain) throws IOException {
        Main.mirrorDomain = domain == null ? "" : domain.trim();
        Main.saveLocals();
    }

    /**
     * 本地版本号（硬编码）：版本随源码编译进程序，不再依赖 version.txt 文件。
     * 更新后源码中的 {@link #LOCAL_VERSION} 随之升级，重启即生效。
     *
     * @return 本地版本号（如 "1.7.0"）
     */
    public static String localVersion() {
        return LOCAL_VERSION;
    }

    /**
     * 比较两个点分数字版本号：a &gt; b 返回正数，相等返回 0，a &lt; b 返回负数。
     *
     * @param a 版本号 A
     * @param b 版本号 B
     * @return 比较结果
     */
    public static int compareVersion(String a, String b) {
        String[] pa = (a == null ? "" : a).split("\\.");
        String[] pb = (b == null ? "" : b).split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? parseInt(pa[i]) : 0;
            int y = i < pb.length ? parseInt(pb[i]) : 0;
            if (x != y) {
                return x - y;
            }
        }
        return 0;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ===== 网络 =====

    /**
     * 检查远程更新：拉取 version.json 并解析。
     *
     * <p>使用配置的镜像地址；未配置（默认直连）且请求失败时，依次尝试常见镜像前缀。</p>
     *
     * @return 更新信息；无法获取时返回 null
     */
    public static UpdateInfo checkUpdate() {
        String base = mirrorBase();
        List<String> candidates = new ArrayList<>();
        candidates.add(base);
        // 仅未自定义镜像（默认直连）失败时自动尝试常见加速前缀
        if (Main.mirrorDomain == null || Main.mirrorDomain.trim().isEmpty()) {
            for (String prefix : FALLBACK_PREFIXES) {
                candidates.add(prefix + DEFAULT_BASE);
            }
        }
        for (String cand : candidates) {
            try {
                String json = httpGet(cand + "/update/version.json");
                UpdateInfo info = parseVersionJson(json);
                if (info != null) {
                    lastWorkingBase = cand;
                    return info;
                }
            } catch (IOException ignored) {
                // 尝试下一个候选源
            }
        }
        return null;
    }

    /**
     * 下载更新清单中的文件到本地对应路径（覆盖）。
     *
     * <p>文件列表来自 {@link #checkUpdate()} 解析的<b>远端 version.json</b>（新版本
     * 的文件列表），本地文件不参与列表生成。下载时对单条清单容错：若远端返回 404
     * （该文件在新版本中已删除，属清单过期条目），跳过并继续，不中断整体更新；
     * 其他网络错误仍视为失败抛出。</p>
     *
     * @param info 更新信息（files 为相对仓库根的路径）
     * @throws IOException 下载或写入失败时抛出
     */
    public static void downloadUpdate(UpdateInfo info) throws IOException {
        if (info == null || info.files.isEmpty()) {
            return;
        }
        String base = lastWorkingBase != null ? lastWorkingBase : mirrorBase();
        List<String> skipped = new ArrayList<>();
        for (String path : info.files) {
            if (path == null || path.isEmpty() || path.startsWith("/") || path.contains("..")) {
                throw new IOException("非法更新路径: " + path);
            }
            File target = new File(path);
            if (target.getParentFile() != null) {
                target.getParentFile().mkdirs();
            }
            String url = base + "/" + path;
            try {
                String content = httpGet(url);
                Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8));
                System.out.println("[update] 已下载: " + path);
            } catch (IOException e) {
                // 404 = 清单中残留的旧文件在新版本中已删除 → 跳过该条，继续下载其余文件；
                // 其余错误（网络中断/源不可达等）仍按失败处理，避免静默“成功”
                if (e.getMessage() != null && e.getMessage().contains("HTTP 404")) {
                    System.out.println("[update] 跳过（新版本已删除）: " + path);
                    skipped.add(path);
                } else {
                    throw e;
                }
            }
        }
        if (!skipped.isEmpty()) {
            System.out.println("[update] 清单过期条目已跳过: " + String.join(", ", skipped));
        }
    }

    /** 重启脚本（程序根目录，缺失时自动创建）。 */
    public static final String RUN_BAT = "run.bat";

    /** 编译打包脚本（程序根目录，缺失时自动创建）。 */
    public static final String CREATE_JAR_BAT = "createJar.bat";

    /** run.bat 默认内容（缺失时自动创建；纯 ASCII，避免 cmd 代码页解析问题）。 */
    private static final String RUN_BAT_CONTENT = """
            @echo off
            cd /d %~dp0
            rem Prefer the packaged sc.jar (auto update removes src/out after packaging)
            if exist "sc.jar" (
                start "ScrollSched" java -Dfile.encoding=UTF-8 -jar "sc.jar"
                exit /b 0
            )
            rem Fallback: run from compiled classes
            if exist "out\\production\\ScrollSched\\Main.class" (
                start "ScrollSched" java -Dfile.encoding=UTF-8 -cp "out\\production\\ScrollSched" Main
                exit /b 0
            )
            echo [ERROR] sc.jar or out\\production\\ScrollSched\\Main.class not found.
            echo Run createJar.bat to build and package first.
            pause
            exit /b 1
            """;

    /** createJar.bat 默认内容（缺失时自动创建；编译 src/*.java 并打包 sc.jar）。 */
    private static final String CREATE_JAR_BAT_CONTENT = """
            @echo off
            cd /d %~dp0
            rem Called by the program when a build/package is needed (auto update, rebuild).
            rem Compiles src\\*.java into out\\production\\ScrollSched, then packages sc.jar.
            setlocal
            set "JAVAC="
            set "JAR="
            if defined JAVA_HOME if exist "%JAVA_HOME%\\bin\\javac.exe" (
                set "JAVAC=%JAVA_HOME%\\bin\\javac.exe"
                set "JAR=%JAVA_HOME%\\bin\\jar.exe"
            )
            if not defined JAVAC for /d %%D in ("%ProgramFiles%\\Java\\jdk*") do (
                if exist "%%D\\bin\\javac.exe" (
                    set "JAVAC=%%D\\bin\\javac.exe"
                    set "JAR=%%D\\bin\\jar.exe"
                )
            )
            if not defined JAVAC for /d %%D in ("%ProgramFiles%\\Eclipse Adoptium\\jdk*") do (
                if exist "%%D\\bin\\javac.exe" (
                    set "JAVAC=%%D\\bin\\javac.exe"
                    set "JAR=%%D\\bin\\jar.exe"
                )
            )
            if not defined JAVAC (
                javac -version >nul 2>&1
                if not errorlevel 1 (
                    set "JAVAC=javac"
                    set "JAR=jar"
                )
            )
            if not defined JAVAC (
                echo [ERROR] javac not found. Install a JDK or set JAVA_HOME.
                exit /b 2
            )
            if not exist "src" (
                echo [ERROR] src directory not found.
                exit /b 2
            )
            if not exist "out\\production\\ScrollSched" mkdir "out\\production\\ScrollSched"
            "%JAVAC%" -encoding UTF-8 -d "out\\production\\ScrollSched" src\\*.java
            if errorlevel 1 (
                echo [ERROR] javac compilation failed.
                exit /b 1
            )
            "%JAR%" cfe sc.jar Main -C "out\\production\\ScrollSched" .
            if errorlevel 1 (
                echo [ERROR] jar packaging failed.
                exit /b 1
            )
            echo [OK] Compiled and packaged sc.jar
            exit /b 0
            """;

    /**
     * 返回 cmd.exe 路径：优先环境变量 ComSpec（避免 PATH 缺少 System32 时找不到 cmd）。
     *
     * @return cmd.exe 路径或回退名
     */
    private static String cmd() {
        String comspec = System.getenv("ComSpec");
        return (comspec != null && !comspec.isEmpty()) ? comspec : "cmd.exe";
    }

    /**
     * 确保根目录存在 run.bat（重启入口）；缺失时按默认内容自动创建。
     *
     * @return run.bat 文件
     * @throws IOException 写入失败
     */
    public static File ensureRunBat() throws IOException {
        File bat = new File(RUN_BAT);
        if (!bat.exists()) {
            Files.writeString(bat.toPath(), RUN_BAT_CONTENT, StandardCharsets.UTF_8);
        }
        return bat;
    }

    /**
     * 确保根目录存在 createJar.bat（编译打包入口）；缺失时按默认内容自动创建。
     *
     * @return createJar.bat 文件
     * @throws IOException 写入失败
     */
    public static File ensureCreateJarBat() throws IOException {
        File bat = new File(CREATE_JAR_BAT);
        if (!bat.exists()) {
            Files.writeString(bat.toPath(), CREATE_JAR_BAT_CONTENT, StandardCharsets.UTF_8);
        }
        return bat;
    }

    /**
     * 调用 createJar.bat 编译并打包（不存在时先自动创建）。
     *
     * <p>替代原先内置的 javac/jar 调用：编译与打包逻辑统一收敛到脚本，
     * 便于用户自行修改构建方式。</p>
     *
     * @return 构建结果描述（成功以 "编译打包成功" 开头）
     */
    public static String buildWithCreateJar() {
        try {
            File bat = ensureCreateJarBat();
            ProcessBuilder pb = new ProcessBuilder(cmd(), "/c", bat.getAbsolutePath());
            pb.directory(new File("."));
            pb.redirectErrorStream(true);
            if (System.getenv("JAVA_HOME") == null || System.getenv("JAVA_HOME").isEmpty()) {
                // 把当前 JVM 的 java.home 交给 bat，便于定位 javac/jar
                pb.environment().put("JAVA_HOME", System.getProperty("java.home", ""));
            }
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            int code = p.waitFor();
            if (code == 0) {
                return "编译打包成功（createJar.bat → sc.jar）";
            }
            return "编译打包失败(exit=" + code + ")：\n" + sb.toString().trim();
        } catch (Exception e) {
            return "调用 createJar.bat 失败: " + e.getMessage();
        }
    }

    /**
     * 调用 run.bat 重启程序（不存在时先自动创建），随后退出当前进程。
     *
     * @return 启动结果描述
     */
    public static String restart() {
        try {
            File bat = ensureRunBat();
            // 直接执行 bat（bat 内用 start 启动新进程后自身立即返回）
            new ProcessBuilder(cmd(), "/c", bat.getAbsolutePath())
                    .directory(new File("."))
                    .start();
            // 给新进程留出启动时间，再退出当前进程
            new Thread(() -> {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                System.exit(0);
            }, "restart-exit").start();
            return "已调用 run.bat 重启";
        } catch (Exception e) {
            return "调用 run.bat 失败: " + e.getMessage();
        }
    }

    // ===== 主流程 =====

    /**
     * 自动更新主流程（供后台线程调用）：检查 → 有新版则<b>弹窗由用户手动确认</b> →
     * 确认后下载 → 调用 {@code createJar.bat} 编译打包（缺失时自动创建）→
     * 调用 {@code run.bat} 重启（缺失时自动创建）并退出当前进程（版本号硬编码于源码）。
     *
     * <p>即“自动检测，手动确认”：发现新版不会直接下载，先弹确认框；用户选“否”则放弃本次更新。</p>
     *
     * @return 面向用户的结果描述文案
     */
    public static String runUpdate() {
        UpdateInfo info = checkUpdate();
        if (info == null) {
            return "检查更新失败（无法访问更新源，请检查网络或镜像配置）";
        }
        String local = localVersion();
        if (compareVersion(info.version, local) <= 0) {
            return "当前已是最新版本 v" + local;
        }

        // 检测到新版 → 弹确认框，用户手动选择是否下载
        final String detectedVersion = info.version;
        final String detectedNote = info.note;
        final int[] choice = new int[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                // 更新内容放入滚动区域：固定宽度，长说明只在框内纵向滚动，不横向扩展对话框
                StringBuilder msg = new StringBuilder();
                msg.append("发现新版本 v").append(detectedVersion);
                if (!detectedNote.isEmpty()) {
                    msg.append("\n\n").append(detectedNote);
                }
                msg.append("\n\n是否现在下载更新？（更新完成后需重启程序生效）");
                JTextArea ta = new JTextArea(msg.toString());
                ta.setEditable(false);
                ta.setOpaque(false);
                ta.setLineWrap(true);
                ta.setWrapStyleWord(true);
                ta.setFont(new Font("微软雅黑", Font.PLAIN, 12));
                JScrollPane sp = new JScrollPane(ta);
                sp.setPreferredSize(new Dimension(420, 260));
                sp.setBorder(BorderFactory.createEmptyBorder());
                JPanel panel = new JPanel(new BorderLayout());
                panel.add(sp, BorderLayout.CENTER);
                choice[0] = JOptionPane.showConfirmDialog(null, panel,
                        "发现更新", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            });
        } catch (Exception e) {
            return "更新确认弹窗失败: " + e.getMessage();
        }
        if (choice[0] != JOptionPane.YES_OPTION) {
            return "已取消更新（检测到新版 v" + detectedVersion + "，未下载）";
        }

        try {
            downloadUpdate(info);
            String build = buildWithCreateJar();
            boolean built = build.startsWith("编译打包成功");
            if (built) {
                // 打包成功后自动清理源码与编译产物（运行中的类仍在内存，重启由 sc.jar 启动）
                cleanupSources();
            }
            StringBuilder sb = new StringBuilder();
            sb.append("已更新到 v").append(info.version).append("（").append(build).append("）。");
            if (built) {
                sb.append(' ').append(restart()).append("，请等待新窗口启动。");
            } else {
                sb.append(" 请手动运行 createJar.bat 后重启程序生效。");
            }
            if (!info.note.isEmpty()) {
                sb.append("\n更新说明：").append(info.note);
            }
            return sb.toString();
        } catch (Exception e) {
            return "更新失败: " + e.getMessage();
        }
    }

    /**
     * 打包成功后自动删除 {@code src/} 与 {@code out/}（源码与编译产物与配置隔离，
     * 重启由 sc.jar 启动；下次更新会重新下载源码并重建编译目录）。
     *
     * @return 清理结果描述
     */
    private static String cleanupSources() {
        StringBuilder sb = new StringBuilder();
        for (String path : new String[]{"src", "out"}) {
            File f = new File(path);
            if (f.exists()) {
                sb.append(deleteRecursively(f) ? ("已删除 " + path + "/") : ("删除 " + path + "/ 失败"));
                sb.append(' ');
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "无源码/编译产物可清理" : result;
    }

    /** 递归删除文件或目录。 */
    private static boolean deleteRecursively(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    if (!deleteRecursively(c)) {
                        return false;
                    }
                }
            }
        }
        return f.delete();
    }

    // ===== 工具 =====

    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("User-Agent", "ScrollSched-Updater/1.0");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new IOException("HTTP " + code + " for " + urlStr);
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            conn.disconnect();
        }
        return sb.toString();
    }

    /**
     * 解析 version.json：{"version":"1.0.1","note":"...","files":["src/A.java",...]}
     *
     * @param json 原始 JSON 文本
     * @return 更新信息；解析不到 version 时返回 null
     */
    private static UpdateInfo parseVersionJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        String version = match("\"version\"\\s*:\\s*\"([^\"]+)\"", json);
        if (version == null) {
            return null;
        }
        String note = match("\"note\"\\s*:\\s*\"([^\"]*)\"", json);
        List<String> files = new ArrayList<>();
        Matcher fm = Pattern.compile("\"files\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        if (fm.find()) {
            Matcher pm = Pattern.compile("\"([^\"]+)\"").matcher(fm.group(1));
            while (pm.find()) {
                files.add(pm.group(1));
            }
        }
        return new UpdateInfo(version, note, files);
    }

    private static String match(String regex, String text) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
