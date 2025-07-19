package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 第一天涨停（之前不是涨停，并且阳线实体要大），第二天真阳线（小阳线且涨幅小于5），第三天真阳线（小阳线且涨幅小于5），第二三天要缩量但不明显，并且前期有上涨的趋势
 * 预期第四天大涨
 * 例如：601208 2025-07-14 ~ 2025-07-17
 */
public class Filter20 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Filter20 filter20 = new Filter20();
        //        filter20.cal();
        filter20.test(20);
        //        filter20.testOne(3, "601208");
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

            double _4diffRatio = _4Kline.getDiffRatio();
            double _3diffRatio = _3Kline.getDiffRatio();
            double _2diffRatio = _2Kline.getDiffRatio();
            double _1diffRatio = _1Kline.getDiffRatio();

            boolean _4up = _4diffRatio < 9.9;
            boolean _3up = _3diffRatio > 9.92 && _3Kline.getOpen() != _3Kline.getClose();
            boolean _2up = _2diffRatio < 5 && _2diffRatio > 0 && _2diffRatio < _3diffRatio && _2Kline.getClose() > _2Kline.getOpen();
            boolean _1up = _1diffRatio < 5 && _1diffRatio > 0 && _1Kline.getClose() > _1Kline.getOpen();

            if (
              _4up
                && _3up
                && _2up
                && _1up
            ) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
