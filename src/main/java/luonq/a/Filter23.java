package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 在一段上升趋势中突发大跌，但是成交量并未特别放大
 * 前期有明显的控盘
 * 例如：002104 2025-05-15 ~ 2025-05-23
 */
public class Filter23 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Filter23 filter23 = new Filter23();
        filter23.cal();
//                filter23.test(50);
        //        filter23.testOne(45, "002104");
    }

    public List<String> cal(int prevDays, String testCode) {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        for (String code : kLineMap.keySet()) {
            if (StringUtils.isNotBlank(testCode) && !code.equals(testCode)) {
                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            int temp = fixTemp(stockKLines, prevDays); // 用于测试历史数据做日期调整
            int index = stockKLines.size() - 1 - temp;
            if (index < 0) {
                continue;
            }
            StockKLine latest = stockKLines.get(index);
            double curClose = latest.getClose();
            if (curClose > 10 || curClose < 4) {
                continue;
            }
            if (stockKLines.size() < 128) {
                //                System.out.println("x " + code);
                continue;
            }

            // 第一天大跌
            if (latest.getDiffRatio() > -6) {
                continue;
            }
            // 必须是大实体阴线
            double openDiff = (latest.getOpen() / latest.getLastClose() - 1) * 100;
            if (openDiff < -2) {
                continue;
            }

            // 计算前面十天的平均成交量
            BigDecimal volSum = BigDecimal.ZERO;
            double max = Double.MIN_VALUE, min = Double.MAX_VALUE;
            for (int i = index - 1; i > index - 11; i--) {
                StockKLine kLine = stockKLines.get(i);
                volSum = volSum.add(kLine.getVolume());
                double vol = kLine.getVolume().doubleValue();
                if (max < vol) {
                    max = vol;
                }
                if (min > vol) {
                    min = vol;
                }
            }
            // 前十天极差倍数要小于4倍
            if (max > min * 4) {
                continue;
            }
            double avgVol = volSum.divide(BigDecimal.valueOf(10), 0, RoundingMode.HALF_UP).doubleValue();
            double vol = latest.getVolume().doubleValue();
            double ratio = Math.max(vol, avgVol) / Math.min(vol, avgVol);
            if (ratio < 1.5) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
