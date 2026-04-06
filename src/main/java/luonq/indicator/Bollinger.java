package luonq.indicator;

import bean.BOLL;
import bean.StockKLine;
import lombok.extern.slf4j.Slf4j;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.RoundingMode.HALF_UP;

/**
 * 布林带：20 周期收盘价均线为中轨，标准差 ×2 为上下轨（中轨 2 位 HALF_UP，上下轨 3 位 DOWN）。
 * 输入按日期升序；第 {@code j} 根 K 的 BOLL 对应 {@code dates[j]}（窗口为含该日的最近 20 日收盘）。
 */
@Slf4j
public class Bollinger {

    public static void main(String[] args) throws Exception {
        calculate("daily");
    }

    /**
     * 与 {@link MovingAverageSma#computeSeries} 对齐：输出长度与输入相同，前 19 根无完整窗口时 md/mb/up/dn 为 0。
     */
    public static List<BOLL> computeSeries(List<Double> closesOldestFirst, List<String> datesOldestFirst) {
        if (closesOldestFirst.size() != datesOldestFirst.size()) {
            throw new IllegalArgumentException("closes and dates size mismatch");
        }
        int n = closesOldestFirst.size();
        if (n == 0) {
            return Collections.emptyList();
        }

        List<BOLL> out = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            double md = 0;
            double mb = 0;
            double up = 0;
            double dn = 0;
            if (j >= 19) {
                double sum = 0;
                for (int k = 0; k < 20; k++) {
                    sum += closesOldestFirst.get(j - 19 + k);
                }
                BigDecimal mbBd = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(20), 2, HALF_UP);
                double mbD = mbBd.doubleValue();
                double varSum = 0;
                for (int k = 0; k < 20; k++) {
                    double c = closesOldestFirst.get(j - 19 + k);
                    double d = c - mbD;
                    varSum += d * d;
                }
                md = Math.sqrt(varSum / 20.0);
                mb = mbD;
                BigDecimal md2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
                up = mbBd.add(md2).setScale(3, RoundingMode.DOWN).doubleValue();
                dn = mbBd.subtract(md2).setScale(3, RoundingMode.DOWN).doubleValue();
            }
            out.add(BOLL.builder()
                    .date(datesOldestFirst.get(j))
                    .md(md)
                    .mb(mb)
                    .up(up)
                    .dn(dn)
                    .build());
        }
        return out;
    }

    public static void calculate(String period) throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> stockToKLineMap = BaseUtils.getFileMap(mergePath);
        Map<String, String> hasCalcMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "mergeBoll");
        Set<String> hasCalcStock = hasCalcMap.keySet();

        for (String stock : stockToKLineMap.keySet()) {
            if (!stock.equals("WINV")) {
//                continue;
            }
            if (hasCalcStock.contains(stock)) {
                                continue;
            }
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(stockToKLineMap.get(stock), 2023);

            List<Double> closes = stockKLines.stream().map(StockKLine::getClose).collect(Collectors.toList());
            List<String> dates = stockKLines.stream().map(StockKLine::getDate).collect(Collectors.toList());
            List<BOLL> series = computeSeries(closes, dates);
            List<String> maList = series.stream().map(BOLL::toString).collect(Collectors.toList());

            BaseUtils.writeFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, maList);
            log.info("finish " + period + " " + stock);
        }
    }
}
