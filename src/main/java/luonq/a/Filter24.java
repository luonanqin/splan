package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 第一天涨停，第二天十字星，之后七天上不超过十字星，下不超过涨停开盘价（的一半），并且小阴小阳波动不大。
 * 等待一根放量阳线收盘前突破十字星的高点可进场，之后如果跌破了这根阳线的最低点才止损，否则持续拿着等待上涨
 * 例如：暂时没找到例子
 */
public class Filter24 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Filter24 filter24 = new Filter24();
        //        filter24.cal();
        filter24.test(30);
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

            StockKLine _1Kline = stockKLines.get(stockKLines.size() - 1 - temp);
            StockKLine _2Kline = stockKLines.get(stockKLines.size() - 2 - temp);
            StockKLine _3Kline = stockKLines.get(stockKLines.size() - 3 - temp);
            StockKLine _4Kline = stockKLines.get(stockKLines.size() - 4 - temp);
            StockKLine _5Kline = stockKLines.get(stockKLines.size() - 5 - temp);
            StockKLine _6Kline = stockKLines.get(stockKLines.size() - 6 - temp);
            StockKLine _7Kline = stockKLines.get(stockKLines.size() - 7 - temp);
            StockKLine _8Kline = stockKLines.get(stockKLines.size() - 8 - temp);
            StockKLine _9Kline = stockKLines.get(stockKLines.size() - 9 - temp);
            StockKLine _10Kline = stockKLines.get(stockKLines.size() - 10 - temp);

            double _9diffRatio = (_9Kline.getClose() / _10Kline.getLastClose() - 1) * 100;

            boolean upTop = _9diffRatio > 9.9; // 第一天涨停
            double _8High = _8Kline.getHigh();
            // 第二天十字星
            boolean star = _8Kline.getClose() < _8High && _8Kline.getClose() > _8Kline.getLow() && _8Kline.getOpen() < _8High && _8Kline.getOpen() > _8Kline.getLow();

            double _9HalfOpen = _9Kline.getOpen() + (_9Kline.getHigh() - _9Kline.getOpen()) / 2;
            boolean _1 = _1Kline.getHigh() < _8High && _1Kline.getLow() > _9HalfOpen;
            boolean _2 = _2Kline.getHigh() < _8High && _2Kline.getLow() > _9HalfOpen;
            boolean _3 = _3Kline.getHigh() < _8High && _3Kline.getLow() > _9HalfOpen;
            boolean _4 = _4Kline.getHigh() < _8High && _4Kline.getLow() > _9HalfOpen;
            boolean _5 = _5Kline.getHigh() < _8High && _5Kline.getLow() > _9HalfOpen;
            boolean _6 = _6Kline.getHigh() < _8High && _6Kline.getLow() > _9HalfOpen;
            boolean _7 = _7Kline.getHigh() < _8High && _7Kline.getLow() > _9HalfOpen;

            if (upTop && star && _1 && _2 && _3 && _4 && _5 && _6 && _7) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
