package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 第一天涨停，第二天阴线并且放倍量，之后横盘几天收盘都高于第一天的涨停价
 * 如果后续中阳站上5日线，说明短期情绪转好
 * 如果后续小阴小阳且缩量，说明在洗盘
 * 例如：002639 2025-05-06 ~ 2025-05-23
 */
public class Filter17 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Filter17 filter17 = new Filter17();
        //        filter17.cal();
        filter17.test(50);
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
            StockKLine _5Kline = stockKLines.get(stockKLines.size() - 5 - temp);

            double _4diffRatio = _4Kline.getDiffRatio();
            double _3diffRatio = _3Kline.getDiffRatio();

            boolean upTop = _4diffRatio > 9.9; // 第一天涨停
            boolean volTimes = _3Kline.getVolume().compareTo(_4Kline.getVolume().multiply(BigDecimal.valueOf(2))) > 0; // 第二天阴线倍量
            boolean _3fakeDown = _3Kline.getOpen() > _4Kline.getClose() && _3Kline.getClose() < _3Kline.getOpen() && _3Kline.getClose() > _4Kline.getClose(); // 第二天假阴线
            boolean _3trueDown = _3Kline.getOpen() > _4Kline.getClose() && _3Kline.getClose() < _3Kline.getOpen() && _3Kline.getClose() < _4Kline.getClose(); // 第二天真阴线
            boolean _3down = _3Kline.getOpen() > _4Kline.getClose() && _3Kline.getClose() < _3Kline.getOpen();
            boolean _2greater_4 = _2Kline.getClose() > _4Kline.getClose();
            boolean _1greater_4 = _1Kline.getClose() > _4Kline.getClose();
            boolean _2lower_3 = _2Kline.getClose() < _3Kline.getHigh();
            boolean _1lower_3 = _1Kline.getClose() < _3Kline.getHigh();

            if (upTop
              && volTimes
              //              && _3fakeDown
              && _3down
              && _2greater_4
              && _1greater_4
              && _2lower_3
              && _1lower_3
            ) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
