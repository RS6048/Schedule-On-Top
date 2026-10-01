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
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * 自动更新检查器。
 *
 * <p>从远程仓库（GitHub raw 或国内镜像）拉取 {@code update/version.json}，
 * 与本地 {@code version.txt} 比对版本；有新版时下载清单中列出的文件覆盖本地源码，
 * 并尝试用 javac 重新编译到 {@code out/production/ScrollSched}（IDEA 输出布局）。
 * 更新后需重启程序生效。</p>
 *
 * <p>镜像配置：镜像<b>只写域名</b>（存于 {@code data/.local} 第 6 行，如
 * {@code ghproxy.net}、{@code gitee.com}）——仓库信息
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

    private static final File VERSION_FILE = new File("./data/version.txt");

    /** 当前程序版本（与推送的 version.json 一致）。 */
    public static final String LOCAL_VERSION = "1.3.0";

    /** 最近一次成功请求使用的 base（下载更新文件时复用）。 */
    private static String lastWorkingBase = null;

    /** 更新信息：远程版本、说明与待更新文件清单。 */
    public static class UpdateInfo {
        public final String version;
        public final String note;
        public final List<String> files;

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
     * 当前镜像域名（.local 第 6 行；空 = 默认 GitHub raw 直连）。
     *
     * <p>容错输入：自动剥离 {@code http(s)://} 协议前缀与尾部斜杠。</p>
     *
     * @return 镜像域名（不含协议与路径，如 {@code raw.githubusercontent.com}）
     */
    public static String mirrorDomain() {
        String d = Main.mirrorDomain == null ? "" : Main.mirrorDomain.trim();
        int scheme = d.indexOf("://");
        if (scheme >= 0) {
            d = d.substring(scheme + 3);
        }
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d.isEmpty() ? DEFAULT_DOMAIN : d;
    }

    /**
     * 完整更新源 base：镜像域名 + 仓库信息（{@code RS6048/Schedule-On-Top/main}）自动追加。
     *
     * @return 更新源 base URL（不含末尾斜杠）
     */
    public static String mirrorBase() {
        return "https://" + mirrorDomain() + "/" + REPO_OWNER + "/" + REPO_NAME + "/main";
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
     * 读取本地版本号；version.txt 不存在时自动创建并写入当前版本。
     *
     * @return 本地版本号（如 "1.0.0"）
     */
    public static String localVersion() {
        try {
            if (VERSION_FILE.exists()) {
                String v = new String(Files.readAllBytes(VERSION_FILE.toPath()), StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
            Files.write(VERSION_FILE.toPath(), LOCAL_VERSION.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 无法读写时仍返回内置版本
        }
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
     * @param info 更新信息（files 为相对仓库根的路径）
     * @throws IOException 下载或写入失败时抛出
     */
    public static void downloadUpdate(UpdateInfo info) throws IOException {
        if (info == null || info.files.isEmpty()) {
            return;
        }
        String base = lastWorkingBase != null ? lastWorkingBase : mirrorBase();
        for (String path : info.files) {
            if (path == null || path.isEmpty() || path.startsWith("/") || path.contains("..")) {
                throw new IOException("非法更新路径: " + path);
            }
            File target = new File(path);
            if (target.getParentFile() != null) {
                target.getParentFile().mkdirs();
            }
            String content = httpGet(base + "/" + path);
            Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8));
            System.out.println("[update] 已下载: " + path);
        }
    }

    /**
     * 用 javac 重新编译 src 下全部源码到 out/production/ScrollSched。
     *
     * @return 编译结果描述（"编译成功" 或失败原因）
     */
    public static String recompile() {
        File outDir = new File("out/production/ScrollSched");
        File javac = findJavac();
        if (javac == null) {
            return "未找到 javac，请手动重新编译";
        }
        File srcDir = new File("src");
        File[] srcs = srcDir.listFiles((d, n) -> n.endsWith(".java"));
        if (srcs == null || srcs.length == 0) {
            return "src 目录下没有 .java 文件";
        }
        outDir.mkdirs();
        List<String> cmd = new ArrayList<>();
        cmd.add(javac.getAbsolutePath());
        cmd.add("-encoding");
        cmd.add("UTF-8");
        cmd.add("-d");
        cmd.add(outDir.getAbsolutePath());
        for (File f : srcs) {
            cmd.add(f.getAbsolutePath());
        }
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
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
                return "编译成功（out/production/ScrollSched）";
            }
            return "编译失败(exit=" + code + ")：\n" + sb.toString().trim();
        } catch (Exception e) {
            return "编译失败: " + e.getMessage();
        }
    }

    private static File findJavac() {
        String home = System.getProperty("java.home", "");
        File[] candidates = {
                new File(home, "bin/javac.exe"),
                new File(home, "../bin/javac.exe"), // JRE 场景：java.home 指向 jre 时回退到 jdk/bin
        };
        for (File f : candidates) {
            if (f.exists()) {
                return f;
            }
        }
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            File f = new File(javaHome, "bin/javac.exe");
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }

    // ===== 主流程 =====

    /**
     * 自动更新主流程（供后台线程调用）：检查 → 有新版则<b>弹窗由用户手动确认</b> →
     * 确认后下载 → 重新编译 → 自动打包 jar → 更新本地版本。
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
                StringBuilder msg = new StringBuilder();
                msg.append("发现新版本 v").append(detectedVersion);
                if (!detectedNote.isEmpty()) {
                    msg.append("\n").append(detectedNote);
                }
                msg.append("\n\n是否现在下载更新？（更新完成后需重启程序生效）");
                choice[0] = JOptionPane.showConfirmDialog(null, msg.toString(),
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
            String compile = recompile();
            String jarMsg = "";
            if (compile.startsWith("编译成功")) {
                jarMsg = packageJar();
                // 打包成功后自动清理源码与编译产物（运行中的类仍在内存，重启由 sc.jar 启动）
                if (jarMsg.startsWith("已打包")) {
                    cleanupSources();
                }
            }
            Files.write(VERSION_FILE.toPath(), info.version.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            sb.append("已更新到 v").append(info.version).append("（").append(compile);
            if (jarMsg != null && !jarMsg.isEmpty()) {
                sb.append("；").append(jarMsg);
            }
            sb.append("）。请重启程序生效。");
            if (!info.note.isEmpty()) {
                sb.append("\n更新说明：").append(info.note);
            }
            return sb.toString();
        } catch (Exception e) {
            return "更新失败: " + e.getMessage();
        }
    }

    /**
     * 编译成功后自动打包可运行 jar：{@code jar cfe sc.jar Main -C out/production/ScrollSched .}。
     * 打包失败（找不到 jar 工具等）不阻断主流程，仅返回描述。
     *
     * @return 打包结果描述
     */
    public static String packageJar() {
        File outDir = new File("out/production/ScrollSched");
        if (!outDir.exists()) {
            return "未打包 jar：class 目录不存在";
        }
        File jarTool = findJar();
        if (jarTool == null) {
            return "未找到 jar 工具，跳过打包";
        }
        File target = new File("sc.jar");
        List<String> cmd = new ArrayList<>();
        cmd.add(jarTool.getAbsolutePath());
        cmd.add("cfe");
        cmd.add(target.getAbsolutePath());
        cmd.add("Main");
        cmd.add("-C");
        cmd.add(outDir.getAbsolutePath());
        cmd.add(".");
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
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
                return "已打包 sc.jar";
            }
            return "打 jar 失败(exit=" + code + ")：\n" + sb.toString().trim();
        } catch (Exception e) {
            return "打 jar 失败: " + e.getMessage();
        }
    }

    /**
     * 定位 JDK 自带的 jar 工具（与 {@link #findJavac()} 同序查找）。
     *
     * @return jar 可执行文件；找不到返回 null
     */
    private static File findJar() {
        String home = System.getProperty("java.home", "");
        File[] candidates = {
                new File(home, "bin/jar.exe"),
                new File(home, "../bin/jar.exe"),
        };
        for (File f : candidates) {
            if (f.exists()) {
                return f;
            }
        }
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            File f = new File(javaHome, "bin/jar.exe");
            if (f.exists()) {
                return f;
            }
        }
        return null;
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