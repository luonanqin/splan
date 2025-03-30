package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Maps;
import util.LoadData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 前一天上涨超过3%然后连续三天下跌超过3%，且下跌三天连续缩量，整体量没有大变化
 * 例如：605588 2025-03-19之后
 */
public class Filter5 extends BaseFilter{

    public static void main(String[] args) {
        LoadData.init();

        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        Map<String, Integer> map = Maps.newHashMap();
        for (String code : kLineMap.keySet()) {
            if (!code.equals("605588")) {
//                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            int temp = 0;
            StockKLine latest = stockKLines.get(stockKLines.size() - 1 - temp);
            if (latest.getClose() > 15d) {
                //                continue;
            }
            if (stockKLines.size() < temp + 20) {
                System.out.println("x " + code);
                continue;
            }

            Map<String/* date */, BigDecimal> avgVolMap = cal50volMa(stockKLines);

            StockKLine _1Kline = stockKLines.get(stockKLines.size() - 1 - temp);
            StockKLine _2Kline = stockKLines.get(stockKLines.size() - 2 - temp);
            StockKLine _3Kline = stockKLines.get(stockKLines.size() - 3 - temp);
            StockKLine _4Kline = stockKLines.get(stockKLines.size() - 4 - temp);
            double _1diffRatio = (_1Kline.getClose() / _1Kline.getLastClose() - 1) * 100;
            double _2diffRatio = (_2Kline.getClose() / _2Kline.getLastClose() - 1) * 100;
            double _3diffRatio = (_3Kline.getClose() / _3Kline.getLastClose() - 1) * 100;
            double _4diffRatio = (_4Kline.getClose() / _4Kline.getLastClose() - 1) * 100;
            boolean _1to2VolCompare = _1Kline.getVolume().compareTo(_2Kline.getVolume()) < 0;
            boolean _2to3VolCompare = _2Kline.getVolume().compareTo(_3Kline.getVolume()) < 0;

            BigDecimal upDayAvgVol = avgVolMap.get(_4Kline.getDate());
            BigDecimal multi3AvgVol = upDayAvgVol.multiply(BigDecimal.valueOf(3));
            boolean great3AvgVol = _4Kline.getVolume().compareTo(multi3AvgVol) < 0;

            boolean greater = true;
            if (_1diffRatio < -3 && _2diffRatio < -3 && _3diffRatio < -3 && _4diffRatio > 3 && _1to2VolCompare && _2to3VolCompare && great3AvgVol) {
                System.out.println(code);
            }
        }
    }
}
