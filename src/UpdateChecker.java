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

/**
 * 自动更新检查器。
 *
 * <p>从远程仓库（GitHub raw 或国内镜像）拉取 {@code update/version.json}，
 * 与本地 {@code version.txt} 比对版本；有新版时下载清单中列出的文件覆盖本地源码，
 * 并尝试用 javac 重新编译到 {@code out/production/ScrollSched}（IDEA 输出布局）。
 * 更新后需重启程序生效。</p>
 *
 * <p>镜像配置：项目根 {@code mirror.txt} 存放 raw 根地址（UTF-8 单行）——
 * 为空时使用默认 GitHub raw；可填 gitee raw 根（如
 * {@code https://gitee.com/xxx/ScrollSched/raw/main}）或代理前缀拼接默认地址
 * （如 {@code https://ghproxy.net/https://raw.githubusercontent.com/xxx/ScrollSched/main}）。
 * 使用默认地址失败时自动依次尝试常见镜像前缀。</p>
 */
public class UpdateChecker {

    /** 仓库所有者与仓库名（用于默认更新源）。 */
    public static final String REPO_OWNER = "RS6048";
    public static final String REPO_NAME = "Schedule-On-Top";

    /** 默认 raw 根（main 分支）。 */
    public static final String DEFAULT_BASE =
            "https://raw.githubusercontent.com/" + REPO_OWNER + "/" + REPO_NAME + "/main";

    /** 直连失败时自动尝试的镜像前缀（拼接完整 URL，按序尝试）。 */
    private static final String[] FALLBACK_PREFIXES = {
            "https://ghproxy.net/",
            "https://mirror.ghproxy.com/"
    };

    private static final File MIRROR_FILE = new File("./mirror.txt");
    private static final File VERSION_FILE = new File("./version.txt");

    /** 当前程序版本（与推送的 version.json 一致）。 */
    public static final String LOCAL_VERSION = "1.0.0";

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
     * 读取镜像 raw 根；mirror.txt 不存在/为空/等于默认值时返回默认直连地址。
     *
     * @return 更新源 base URL（不含末尾斜杠）
     */
    public static String mirrorBase() {
        String cfg = readTrimmed(MIRROR_FILE);
        if (cfg == null || cfg.isEmpty() || cfg.equals(DEFAULT_BASE)) {
            return DEFAULT_BASE;
        }
        while (cfg.endsWith("/")) {
            cfg = cfg.substring(0, cfg.length() - 1);
        }
        return cfg;
    }

    /**
     * 保存镜像 raw 根到 mirror.txt。空串或默认值会清空文件（恢复直连）。
     *
     * @param base 用户填写的 base URL
     * @throws IOException 写入失败时抛出
     */
    public static void saveMirror(String base) throws IOException {
        String v = base == null ? "" : base.trim();
        if (v.isEmpty() || v.equals(DEFAULT_BASE)) {
            Files.write(MIRROR_FILE.toPath(), new byte[0]);
        } else {
            Files.write(MIRROR_FILE.toPath(), v.getBytes(StandardCharsets.UTF_8));
        }
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
        if (base.equals(DEFAULT_BASE)) {
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
     * 自动更新主流程（供后台线程调用）：检查 → 有新版则下载 → 重新编译 → 更新本地版本。
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
        try {
            downloadUpdate(info);
            String compile = recompile();
            Files.write(VERSION_FILE.toPath(), info.version.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            sb.append("已更新到 v").append(info.version).append("（").append(compile).append("）。");
            sb.append("请重启程序生效。");
            if (!info.note.isEmpty()) {
                sb.append("\n更新说明：").append(info.note);
            }
            return sb.toString();
        } catch (Exception e) {
            return "更新失败: " + e.getMessage();
        }
    }

    // ===== 工具 =====

    private static String readTrimmed(File f) {
        try {
            if (f.exists()) {
                return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim();
            }
        } catch (IOException ignored) {
            // 读取失败按未配置处理
        }
        return null;
    }

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
