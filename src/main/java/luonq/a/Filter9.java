package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 找出近10天，成交量有大于50天均量线两倍至少2次，并且出现过一次连续下跌3天，并且10天前的40天最多出现1次成交量大于50天均量线两倍，并且近10天没有出现过一字板
 * 例如：001258 2025-03-19至2025-04-01
 */
public class Filter9 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        new Filter9().cal();
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
            StockKLine latest = stockKLines.get(stockKLines.size() - 1 - temp);
            double curClose = latest.getClose();
            if (curClose > 10 || curClose < 4) {
                continue;
            }
            if (stockKLines.size() < 128) {
                //                System.out.println("x " + code);
                continue;
            }

            Map<String/* date */, BigDecimal> avgVolMap = cal50volMa(stockKLines);
            int last40UpAvgVolCount = 0;
            for (int i = stockKLines.size() - 50 - temp; i < stockKLines.size() - 10 - temp; i++) {
                StockKLine kLine = stockKLines.get(i);
                if (kLine.getVolume().compareTo(avgVolMap.get(kLine.getDate()).multiply(BigDecimal.valueOf(2))) > 0) {
                    last40UpAvgVolCount++;
                }
            }

            int last10UpAvgVolCount = 0;
            boolean oneTop = false;
            for (int i = stockKLines.size() - 10 - temp; i < stockKLines.size() - temp; i++) {
                StockKLine kLine = stockKLines.get(i);
                if (kLine.getVolume().compareTo(avgVolMap.get(kLine.getDate()).multiply(BigDecimal.valueOf(2))) > 0) {
                    last10UpAvgVolCount++;
                }
                if (kLine.getClose() == kLine.getOpen()) {
                    oneTop = true;
                }
            }

            StockKLine _1Kline = stockKLines.get(stockKLines.size() - 1 - temp);
            StockKLine _2Kline = stockKLines.get(stockKLines.size() - 2 - temp);
            StockKLine _3Kline = stockKLines.get(stockKLines.size() - 3 - temp);
            double _1diffRatio = (_1Kline.getClose() / _1Kline.getLastClose() - 1) * 100;
            double _2diffRatio = (_2Kline.getClose() / _2Kline.getLastClose() - 1) * 100;
            double _3diffRatio = (_3Kline.getClose() / _3Kline.getLastClose() - 1) * 100;

            if (last40UpAvgVolCount <= 1 && last10UpAvgVolCount >= 2 && _1diffRatio < 0 && _2diffRatio < 0 && _3diffRatio < 0 && !oneTop) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }
}
