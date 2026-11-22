import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.zip.GZIPOutputStream;

/**
 * 运行日志。
 *
 * <p>日志目录为程序（jar）根目录下的 {@code log/}，按天分文件 {@code yyyy-MM-dd.log}；
 * 启动时自动压缩今天之前的日志为 {@code yyyy-MM-dd.log.gz}（压缩成功后删除原文件），
 * 避免日志目录无限膨胀。</p>
 *
 * <p>是否记录由 {@code data/.local} 第 8 行（"开启log"，默认 0=关闭）控制，设置面板可勾选；
 * 开启时把 {@code System.out}/{@code System.err} 同时写到控制台与日志文件，
 * 因此现有的 {@code System.out.println} 无需改动即可入日志。</p>
 */
public final class Log {

    /** 日志目录（jar 所在根目录下的 log/，与 data/ 同级）。 */
    public static final File DIR = new File(resolveRoot(), "log");

    /** 解析程序根目录：sc.jar（或 classes 目录）所在位置；失败回退当前工作目录。 */
    private static File resolveRoot() {
        try {
            File loc = new File(Log.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
            return loc.isFile() ? loc.getParentFile() : loc;
        } catch (Exception e) {
            return new File(".");
        }
    }

    /** 日志文件日期格式。 */
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 是否开启日志（默认关闭）。 */
    private static volatile boolean enabled = false;

    /** 是否已安装控制台分流（System.out/err 只能安装一次）。 */
    private static boolean installed = false;

    /** 当前打开的日志文件日期。 */
    private static LocalDate openDate;

    /** 当前日志输出流（追加模式）。 */
    private static OutputStream out;

    private Log() {
    }

    /**
     * 初始化日志：创建目录、压缩过时日志，并按配置安装控制台分流。
     *
     * @param on 是否开启日志
     */
    public static synchronized void init(boolean on) {
        if (!DIR.exists()) {
            DIR.mkdirs();
        }
        compressOldLogs();
        enabled = on;
        if (on) {
            install();
        }
    }

    /**
     * 运行时切换日志开关（设置面板勾选/取消时调用）。
     *
     * @param on 是否开启
     */
    public static synchronized void setEnabled(boolean on) {
        if (on && !DIR.exists()) {
            DIR.mkdirs();
        }
        enabled = on;
        if (on) {
            install();
        } else {
            closeFile();
        }
    }

    /**
     * 当前日志是否开启。
     *
     * @return true 表示开启
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 记录一行日志（仅在开启时输出，走 System.out 因此同时进入日志文件）。
     *
     * @param tag 模块标签
     * @param msg 内容
     */
    public static void d(String tag, String msg) {
        if (enabled) {
            System.out.println("[" + tag + "] " + msg);
        }
    }

    /**
     * 把 System.out / System.err 替换为“控制台 + 日志文件”的 tee 流。
     * 只安装一次；关闭日志时不替换，保持零开销。
     */
    private static void install() {
        if (installed) {
            return;
        }
        final PrintStream consoleOut = System.out;
        final PrintStream consoleErr = System.err;
        System.setOut(tee(consoleOut));
        System.setErr(tee(consoleErr));
        installed = true;
    }

    /**
     * 构造同时写控制台与日志文件的 PrintStream。
     *
     * @param console 原控制台流
     * @return tee 流
     */
    private static PrintStream tee(final PrintStream console) {
        OutputStream tee = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                console.write(b);
                appendBytes(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                console.write(b, off, len);
                appendBytes(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                console.flush();
            }
        };
        return new PrintStream(tee, true, StandardCharsets.UTF_8);
    }

    /**
     * 追加字节到当天的日志文件（跨天自动切换文件）。
     *
     * @param b   字节
     * @param off 起始偏移
     * @param len 长度
     */
    private static synchronized void appendBytes(byte[] b, int off, int len) {
        if (!enabled) {
            return;
        }
        try {
            LocalDate now = LocalDate.now();
            if (out == null || openDate == null || !openDate.equals(now)) {
                openFile(now);
            }
            if (out != null) {
                out.write(b, off, len);
                out.flush();
            }
        } catch (IOException ignored) {
            // 日志写入失败不影响主流程
        }
    }

    /**
     * 打开（追加）指定日期的日志文件。
     *
     * @param date 日期
     */
    private static void openFile(LocalDate date) {
        closeFile();
        try {
            File f = new File(DIR, date.format(DAY) + ".log");
            out = new FileOutputStream(f, true);
            openDate = date;
        } catch (IOException e) {
            out = null;
            openDate = null;
        }
    }

    /** 关闭当前日志文件。 */
    private static void closeFile() {
        if (out != null) {
            try {
                out.flush();
                out.close();
            } catch (IOException ignored) {
                // 忽略关闭失败
            }
        }
        out = null;
        openDate = null;
    }

    /**
     * 压缩过时日志：今天之前的所有 {@code yyyy-MM-dd.log} 压成 {@code .log.gz} 并删除原文件。
     * 文件名不是合法日期的日志跳过不动。
     */
    private static void compressOldLogs() {
        LocalDate today = LocalDate.now();
        File[] files = DIR.listFiles((d, name) -> name.endsWith(".log"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            String name = f.getName();
            String day = name.substring(0, name.length() - 4);
            LocalDate date;
            try {
                date = LocalDate.parse(day, DAY);
            } catch (DateTimeParseException e) {
                continue;
            }
            if (!date.isBefore(today)) {
                continue;
            }
            File gz = new File(DIR, name + ".gz");
            try {
                if (!gz.exists() || gz.length() < f.length()) {
                    try (GZIPOutputStream os = new GZIPOutputStream(new FileOutputStream(gz))) {
                        Files.copy(f.toPath(), os);
                    }
                }
                if (!f.delete()) {
                    System.err.println("[log] 旧日志删除失败: " + name);
                }
            } catch (IOException e) {
                System.err.println("[log] 压缩旧日志失败: " + name + " (" + e.getMessage() + ")");
            }
        }
    }
}
