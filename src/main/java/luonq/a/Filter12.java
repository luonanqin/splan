package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 中阳线之后连续三天下跌，且连续缩量
 * 中阳线那天成交量至少是50天均量线的两倍，后面三天成交量逐渐下跌，都低于第一天，且第四天的成交量低于第一天的一半
 * 例如：600081 2025-03-20至2025-03-26
 */
public class Filter12 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        new Filter12().cal();
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

            Map<String/* date */, BigDecimal> avgVolMap = cal50volMa(stockKLines);

            StockKLine _1Kline = stockKLines.get(stockKLines.size() - 1 - temp);
            StockKLine _2Kline = stockKLines.get(stockKLines.size() - 2 - temp);
            StockKLine _3Kline = stockKLines.get(stockKLines.size() - 3 - temp);
            StockKLine _4Kline = stockKLines.get(stockKLines.size() - 4 - temp);
            double _1diffRatio = (_1Kline.getClose() / _1Kline.getLastClose() - 1) * 100;
            double _2diffRatio = (_2Kline.getClose() / _2Kline.getLastClose() - 1) * 100;
            double _3diffRatio = (_3Kline.getClose() / _3Kline.getLastClose() - 1) * 100;
            double _4diffRatio = (_4Kline.getClose() / _4Kline.getLastClose() - 1) * 100;

            //            boolean lowThanOpen = _1Kline.getClose() < _3Kline.getOpen();
            //            boolean highThanLow = _1Kline.getClose() > _3Kline.getLow();
            BigDecimal _4AvgVol = avgVolMap.get(_4Kline.getDate());
            //            BigDecimal _1AvgVol = avgVolMap.get(_1Kline.getDate());
            boolean greatAvgVol = _4Kline.getVolume().compareTo(_4AvgVol.multiply(BigDecimal.valueOf(2))) > 0;
            boolean lessThan4 = _3Kline.getVolume().compareTo(_4Kline.getVolume()) < 0;
            boolean lessThan3 = _2Kline.getVolume().compareTo(_3Kline.getVolume()) < 0;
            boolean lessThan2 = _1Kline.getVolume().compareTo(_2Kline.getVolume()) < 0;
            boolean lessThan1 = _1Kline.getVolume().multiply(BigDecimal.valueOf(2)).compareTo(_4Kline.getVolume()) < 0;
            boolean closeGreatOpen = _4Kline.getClose() > _4Kline.getOpen();

            if (_4diffRatio > 6 && closeGreatOpen && _1diffRatio < 0 && _2diffRatio < 0 && _3diffRatio < 0 && greatAvgVol && lessThan1 && lessThan2 && lessThan3 && lessThan4) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
