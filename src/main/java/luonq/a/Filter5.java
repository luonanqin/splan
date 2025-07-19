package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 前一天上涨超过3%然后连续三天下跌超过3%，且下跌三天连续缩量，整体量没有大变化
 * 例如：605588 2025-03-19之后
 */
public class Filter5 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        new Filter5().cal();
    }

    public List<String> cal(int prevDays, String testCode) {
        List<String> res = Lists.newArrayList();
        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        Map<String, Integer> map = Maps.newHashMap();
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

            BigDecimal upDayAvgVol = avgVolMap.get(_4Kline.getDate());
            if (upDayAvgVol == null) {
                continue;
            }
            BigDecimal multi3AvgVol = upDayAvgVol.multiply(BigDecimal.valueOf(3));
            boolean great3AvgVol = _4Kline.getVolume().compareTo(multi3AvgVol) < 0;

            boolean greater = true;
            if (_1diffRatio < -3 && _2diffRatio < -3 && _3diffRatio < -3 && _4diffRatio > 3 && _1to2VolCompare && _2to3VolCompare && great3AvgVol) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }
}
