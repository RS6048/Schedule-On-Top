import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 轻量级 INI / 键值对配置文件读取器。
 *
 * <p>文件格式约定：</p>
 * <ul>
 *   <li>每行一个键值对，以 {@code =} 分隔；</li>
 *   <li>以 {@code #} 开头的行为注释，自动跳过；</li>
 *   <li>空行自动跳过；</li>
 *   <li>不含 {@code =} 的行自动跳过并打印警告；</li>
 *   <li>{@code $---$} 为隐藏/显示分割线：分割线上方的键为<b>隐藏字符</b>，
 *       下方的键为普通显示字符（见 {@link #isHidden}）。</li>
 * </ul>
 *
 * <p>本类用于读取课程简称与全称的映射表（如 {@code 语=语文}）。</p>
 */
public class IniFileReader {

    /** 键值对存储。 */
    private final Map<String, String> keyValue = new HashMap<>();

    /** 隐藏键集合（{@code $---$} 分割线上方的键值对为隐藏字符）。 */
    private final Set<String> hiddenKeys = new HashSet<>();

    /**
     * 构造读取器并立即从输入流加载配置。
     *
     * @param is 配置文件输入流，调用方负责关闭
     * @throws IOException 读取失败时抛出
     */
    public IniFileReader(InputStream is) throws IOException {
        load(is);
    }

    /**
     * 从输入流加载（或重新加载）配置。重复调用会清空已有数据。
     *
     * @param is 配置文件输入流
     * @throws IOException 读取失败时抛出
     */
    public void load(InputStream is) throws IOException {
        keyValue.clear();
        hiddenKeys.clear();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            boolean inHidden = true;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (line.trim().equals("$---$")) {
                    // 隐藏/显示分割线：上方的键为隐藏字符，下方的键为普通字符
                    inHidden = false;
                    continue;
                }
                // 注意：不做 trim，保持与原行为一致（full_name.txt 中存在空格开头的 key）
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator < 0) {
                    System.err.println("[IniFileReader] 第 " + lineNo + " 行缺少 '='，已跳过：" + line);
                    continue;
                }
                String key = line.substring(0, separator);
                keyValue.put(key, line.substring(separator + 1));
                if (inHidden) {
                    hiddenKeys.add(key);
                }
            }
        }
    }

    /**
     * 根据键获取值。
     *
     * @param key 键
     * @return 对应的值；不存在时返回 {@code null}
     */
    public String get(String key) {
        return keyValue.get(key);
    }

    /**
     * 判断键是否为隐藏字符（{@code $---$} 分割线上方的键）。
     *
     * @param key 键
     * @return 隐藏返回 true
     */
    public boolean isHidden(String key) {
        return hiddenKeys.contains(key);
    }

    /**
     * 获取所有隐藏键的只读视图。
     *
     * @return 隐藏键集合
     */
    public Set<String> getHiddenKeys() {
        return Collections.unmodifiableSet(new HashSet<>(hiddenKeys));
    }

    /**
     * 获取所有键的只读视图。
     *
     * @return 键集合
     */
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(keyValue.keySet());
    }

    /**
     * 获取内部键值对的只读副本。
     *
     * @return 不可修改的 Map
     */
    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(new HashMap<>(keyValue));
    }
}
