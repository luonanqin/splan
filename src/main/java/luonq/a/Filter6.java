package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import util.LoadData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 前一天涨停然后连续三天下跌，其中第三天最低点超过涨停那天最低点，整体量没有大变化
 * 例如：002358 2025-03-06之后
 */
public class Filter6 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        cal();
    }

    public static List<String> cal() {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        for (String code : kLineMap.keySet()) {
            if (!code.equals("605588")) {
                //                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            int temp = fixTemp(stockKLines, 0); // 用于测试历史数据做日期调整
            StockKLine latest = stockKLines.get(stockKLines.size() - 1 - temp);
            if (latest.getClose() > 10) {
                continue;
            }
            if (stockKLines.size() < temp + 20) {
                //                System.out.println("x " + code);
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
            boolean lowTolow = _4Kline.getLow() > _1Kline.getLow();

            BigDecimal upDayAvgVol = avgVolMap.get(_4Kline.getDate());
            if (upDayAvgVol == null) {
                continue;
            }
            BigDecimal multi3AvgVol = upDayAvgVol.multiply(BigDecimal.valueOf(3));
            boolean great3AvgVol = _4Kline.getVolume().compareTo(multi3AvgVol) < 0;

            boolean greater = true;
            if (
              _1diffRatio < 0 && _2diffRatio < 0 && _3diffRatio < 0 &&
                lowTolow && _4diffRatio > 9.95 && great3AvgVol
                && _1to2VolCompare && _2to3VolCompare
            ) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }

}
