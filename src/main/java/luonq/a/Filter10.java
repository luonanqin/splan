package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import util.LoadData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 找出中阳线之后连续两天下跌，最低价低于中阳线的开盘价，但是没有跌破最低价的股票
 * 中阳线那天成交量至少是50天均量线的两倍，后面两天成交量逐渐下跌，都低于第一天
 * 例如：600156 2025-03-21至2025-03-26
 */
public class Filter10 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        cal();
    }

    public static List<String> cal() {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        for (String code : kLineMap.keySet()) {
            if (!code.equals("600156")) {
                //                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            int temp = 0; // 用于测试历史数据做日期调整
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

            StockKLine _1Kline = stockKLines.get(stockKLines.size() - 1 - temp);
            StockKLine _2Kline = stockKLines.get(stockKLines.size() - 2 - temp);
            StockKLine _3Kline = stockKLines.get(stockKLines.size() - 3 - temp);
            double _1diffRatio = (_1Kline.getClose() / _1Kline.getLastClose() - 1) * 100;
            double _2diffRatio = (_2Kline.getClose() / _2Kline.getLastClose() - 1) * 100;
            double _3diffRatio = (_3Kline.getClose() / _3Kline.getLastClose() - 1) * 100;

            boolean lowThanOpen = _1Kline.getClose() < _3Kline.getOpen();
            boolean highThanLow = _1Kline.getClose() > _3Kline.getLow();
            BigDecimal avgVol = avgVolMap.get(_3Kline.getDate());
            boolean greatAvgVol = _3Kline.getVolume().compareTo(avgVol.multiply(BigDecimal.valueOf(2))) > 0;
            boolean lessThan3 = _2Kline.getVolume().compareTo(_3Kline.getVolume()) < 0;
            boolean lessThan2 = _1Kline.getVolume().compareTo(_2Kline.getVolume()) < 0;
            boolean lessThanOpen = _1Kline.getVolume().multiply(BigDecimal.valueOf(2)).compareTo(_3Kline.getVolume()) < 0;

            if (_3diffRatio > 6 && _1diffRatio < 0 && _2diffRatio < 0 && lowThanOpen && highThanLow && greatAvgVol && lessThan2 && lessThan3 && lessThanOpen) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }
}
