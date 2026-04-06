package luonq.indicator;

import bean.MA;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 简单移动平均线（SMA）：按日期<strong>升序</strong>滑动窗口，第 {@code j} 根 K 的均线对应 {@code dates[j]}（窗口为含该日的最近 N 日收盘）。
 * 价位为收盘价；小数位数由 {@code scale} 指定（落库 5 位、写文件 2 位）。
 */
public final class MovingAverageSma {

    /** 与 {@link MoveAverage} 写指标文件时一致 */
    public static final int SCALE_FILE = 2;
    /** 与 {@code ma} 表 decimal(20,5) 一致 */
    public static final int SCALE_DB = 5;

    private static final int[] PERIODS = {5, 10, 20, 30, 60};
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;

    private MovingAverageSma() {
    }

    /**
     * @param closesOldestFirst 按日期升序的收盘价
     * @param datesOldestFirst  与 closes 一一对应的日期字符串
     * @param scale             {@link #SCALE_DB} 或 {@link #SCALE_FILE}
     */
    public static List<MA> computeSeries(List<Double> closesOldestFirst, List<String> datesOldestFirst, int scale) {
        if (closesOldestFirst.size() != datesOldestFirst.size()) {
            throw new IllegalArgumentException("closes and dates size mismatch");
        }
        int n = closesOldestFirst.size();
        if (n == 0) {
            return Collections.emptyList();
        }

        List<MA> out = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            double ma5 = 0;
            double ma10 = 0;
            double ma20 = 0;
            double ma30 = 0;
            double ma60 = 0;
            if (j >= 4) {
                ma5 = windowMean(closesOldestFirst, j - 4, j, scale);
            }
            if (j >= 9) {
                ma10 = windowMean(closesOldestFirst, j - 9, j, scale);
            }
            if (j >= 19) {
                ma20 = windowMean(closesOldestFirst, j - 19, j, scale);
            }
            if (j >= 29) {
                ma30 = windowMean(closesOldestFirst, j - 29, j, scale);
            }
            if (j >= 59) {
                ma60 = windowMean(closesOldestFirst, j - 59, j, scale);
            }
            out.add(MA.builder()
                    .date(datesOldestFirst.get(j))
                    .ma5(ma5)
                    .ma10(ma10)
                    .ma20(ma20)
                    .ma30(ma30)
                    .ma60(ma60)
                    .build());
        }
        return out;
    }

    private static double windowMean(List<Double> closes, int from, int to, int scale) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int k = from; k <= to; k++) {
            sum = sum.add(BigDecimal.valueOf(closes.get(k)));
        }
        int len = to - from + 1;
        return sum.divide(BigDecimal.valueOf(len), scale, ROUND).doubleValue();
    }
}
