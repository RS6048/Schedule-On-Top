import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间段检查器：判断当前时刻是否落在某节课的时间范围内，
 * 并计算课程进度与剩余倒计时。
 *
 * <p>使用方式：先调用 {@link #load(String, String)} 载入起止时间，
 * 再调用 {@link #isInRange(LocalTime)} / {@link #getProgress(LocalTime)} /
 * {@link #getShownCountdown(LocalTime)} 获取信息。</p>
 */
public class TimeChecker {

    /** 课表时间文件使用的时间格式（如 "8:05"、"13:00"）。 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    /** 当前载入的课程开始时间。 */
    private LocalTime start;

    /** 当前载入的课程结束时间。 */
    private LocalTime end;

    /**
     * 构造一个空的检查器。使用前必须调用 {@link #load(String, String)}。
     */
    public TimeChecker() {
    }

    /**
     * 载入一节课的起止时间。
     *
     * @param start 开始时间，格式 "H:mm"
     * @param end   结束时间，格式 "H:mm"
     * @throws java.time.format.DateTimeParseException 时间格式不合法时抛出
     */
    public void load(String start, String end) {
        this.start = LocalTime.parse(start, FORMATTER);
        this.end = LocalTime.parse(end, FORMATTER);
    }

    /**
     * 判断指定时刻是否在 [start, end] 闭区间内。
     *
     * @param lt 待判断的时刻
     * @return {@code true} 表示在课程进行中
     */
    public boolean isInRange(LocalTime lt) {
        if (start == null || end == null) {
            return false;
        }
        return !lt.isBefore(start) && !lt.isAfter(end);
    }

    /**
     * 计算课程已进行的进度比例。
     *
     * @param lt 当前时刻
     * @return 0.0 ~ 1.0 之间的进度值；不在课程范围内返回 0
     */
    public float getProgress(LocalTime lt) {
        if (!isInRange(lt)) {
            return 0.0f;
        }
        long total = Duration.between(start, end).toMillis();
        if (total <= 0) {
            return 0.0f;
        }
        long past = Duration.between(start, lt).toMillis();
        return (float) past / total;
    }

    /**
     * 获取面向用户显示的倒计时字符串，格式 "m:ss"。
     *
     * @param lt 当前时刻
     * @return 倒计时文本；不在课程范围内返回 "0:00"
     */
    public String getShownCountdown(LocalTime lt) {
        if (!isInRange(lt)) {
            return "0:00";
        }
        Duration remaining = Duration.between(lt, end);
        if (remaining.isNegative()) {
            return "0:00";
        }
        long minutes = remaining.toMinutes();
        long seconds = remaining.getSeconds() % 60;
        return minutes + ":" + String.format("%02d", seconds);
    }
}
