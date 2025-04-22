package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import util.LoadData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 第一天上涨后面三天连续缩量，且第四天量不到第一天的一半，但是这四天量都比50天均量线两倍多
 * 例如：600644 2025-03-25至2025-03-31
 */
public class Filter15 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        cal();
    }

    public static List<String> cal() {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        for (String code : kLineMap.keySet()) {
            if (!code.equals("600644")) {
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
            if (curClose > 10) {
                continue;
            }
            if (stockKLines.size() < 128) {
                //                System.out.println("x " + code);
                continue;
            }

            Map<String, BigDecimal> avgVolMap = cal50volMa(stockKLines);

            StockKLine _1Kline = stockKLines.get(stockKLines.size() - 1 - temp);
            StockKLine _2Kline = stockKLines.get(stockKLines.size() - 2 - temp);
            StockKLine _3Kline = stockKLines.get(stockKLines.size() - 3 - temp);

            StockKLine _4Kline = stockKLines.get(stockKLines.size() - 4 - temp);

            double _1diffRatio = (_1Kline.getClose() / _1Kline.getLastClose() - 1) * 100;
            double _2diffRatio = (_2Kline.getClose() / _2Kline.getLastClose() - 1) * 100;
            double _3diffRatio = (_3Kline.getClose() / _3Kline.getLastClose() - 1) * 100;
            double _4diffRatio = (_4Kline.getClose() / _4Kline.getLastClose() - 1) * 100;

            boolean _1down = _1diffRatio < 0 && _1Kline.getClose() < _1Kline.getOpen();
            boolean _2down = _2diffRatio < 0 && _2Kline.getClose() < _2Kline.getOpen();
            boolean _3down = _3diffRatio < 0 && _3Kline.getClose() < _3Kline.getOpen();
            boolean _4up = _4diffRatio > 0 && _4Kline.getClose() > _4Kline.getOpen();

            boolean _1less2 = _1Kline.getVolume().compareTo(_2Kline.getVolume()) < 0;
            boolean _2less3 = _2Kline.getVolume().compareTo(_3Kline.getVolume()) < 0;
            boolean _3less4 = _3Kline.getVolume().compareTo(_4Kline.getVolume()) < 0;
            boolean _1less4 = _1Kline.getVolume().multiply(BigDecimal.valueOf(2)).compareTo(_4Kline.getVolume()) < 0;

            boolean _1greatAvgVol = _1Kline.getVolume().compareTo(avgVolMap.get(_1Kline.getDate())) > 0;
            boolean _2greatAvgVol = _2Kline.getVolume().compareTo(avgVolMap.get(_2Kline.getDate())) > 0;
            boolean _3greatAvgVol = _3Kline.getVolume().compareTo(avgVolMap.get(_3Kline.getDate())) > 0;
            boolean _4greatAvgVol = _4Kline.getVolume().compareTo(avgVolMap.get(_4Kline.getDate())) > 0;

            if (_1down &&
              _2down &&
              _3down &&
              _4up &&
              _1less2 &&
              _2less3 &&
              _3less4 &&
              _1less4 &&
              _1greatAvgVol &&
              _2greatAvgVol &&
              _3greatAvgVol &&
              _4greatAvgVol
            ) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
