package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 连续阳线（包括假阳线）超过4根但没有一根涨停，且成交量波动不大
 * 需要观察假阳线如果缩量属于正常
 * 例如：002153 2025-07-08 ~ 2025-07-11
 */
public class Filter19 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        new Filter19().cal();
        //        test(10);
        //                                testOne(1, null);
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

            //            Map<String, BigDecimal> avgVolMap = cal50volMa(stockKLines);

            StockKLine _1Kline = stockKLines.get(stockKLines.size() - 1 - temp);
            StockKLine _2Kline = stockKLines.get(stockKLines.size() - 2 - temp);
            StockKLine _3Kline = stockKLines.get(stockKLines.size() - 3 - temp);
            StockKLine _4Kline = stockKLines.get(stockKLines.size() - 4 - temp);

            double _4diffRatio = (_4Kline.getClose() / _4Kline.getOpen() - 1) * 100;
            double _3diffRatio = (_3Kline.getClose() / _3Kline.getOpen() - 1) * 100;
            double _2diffRatio = (_2Kline.getClose() / _2Kline.getOpen() - 1) * 100;
            double _1diffRatio = (_1Kline.getClose() / _1Kline.getOpen() - 1) * 100;

            boolean _4up = _4diffRatio > 0 && _4diffRatio < 9.9;
            boolean _3up = _3diffRatio > 0 && _3diffRatio < 9.9;
            boolean _2up = _2diffRatio > 0 && _2diffRatio < 9.9;
            boolean _1up = _1diffRatio > 0 && _1diffRatio < 9.9;
            boolean _4volDown = _4Kline.getVolume().compareTo(_3Kline.getVolume()) > 0;
            boolean _3volDown = _3Kline.getVolume().compareTo(_2Kline.getVolume()) > 0;
            boolean _2volDown = _2Kline.getVolume().compareTo(_1Kline.getVolume()) > 0;

            if (
              _4up
                && _3up
                && _2up
                && _1up
                && !(_4volDown
                && _3volDown
                && _2volDown)
            ) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
