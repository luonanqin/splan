package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import util.LoadData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 近40天只有一个涨停，并且涨停之后没有出现收盘价低于涨停开盘价的
 * 例如：002688 2025-01-17至2025-03-27
 */
public class Filter11 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        cal();
    }

    public static List<String> cal() {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        for (String code : kLineMap.keySet()) {
            if (!code.equals("002688")) {
                //                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            int temp = fixTemp(stockKLines, 0); // 用于测试历史数据做日期调整
            int index = stockKLines.size() - 1 - temp;
            if (index < 0) {
                continue;
            }
            StockKLine latest = stockKLines.get(index);
            double curClose = latest.getClose();
            double curLastClose = latest.getLastClose();
            double curRatio = (curClose / curLastClose - 1) * 100;
            if (curClose > 15d) {
                continue;
            }
            if (stockKLines.size() < 128) {
                //                System.out.println("x " + code);
                continue;
            }

            Map<String/* date */, BigDecimal> avgVolMap = cal50volMa(stockKLines);
            int topCount = 0;
            int topIndex = 0;
            double topClose = 0;
            for (int i = stockKLines.size() - 40 - temp; i < stockKLines.size() - 10 - temp; i++) {
                StockKLine kLine = stockKLines.get(i);
                double ratio = 100 * (kLine.getClose() / kLine.getLastClose() - 1);
                if (ratio > 9.90) {
                    topCount++;
                    topIndex = i;
                    topClose = kLine.getClose();
                }
            }
            if (topCount != 1 || topIndex + 30 > stockKLines.size() - 1 - temp) {
                continue;
            }

            boolean lowThanTopOpen = false;
            StockKLine topKline = stockKLines.get(topIndex);
            double highestClose = Double.MIN_VALUE;
            for (int i = topIndex + 1; i < stockKLines.size() - 3 - temp; i++) {
                StockKLine kLine = stockKLines.get(i);
                if (topKline.getOpen() > kLine.getClose()) {
                    lowThanTopOpen = true;
                    break;
                }
                if (highestClose < kLine.getClose()) {
                    highestClose = kLine.getClose();
                }
            }
            double highestRatio = (highestClose / topClose - 1) * 100;

            if (!lowThanTopOpen && highestRatio < 8) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }
}
