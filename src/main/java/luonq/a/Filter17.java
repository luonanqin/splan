package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 第一天涨停，第二天阴线并且放倍量，之后横盘几天收盘都高于第一天的涨停价
 * 如果后续中阳站上5日线，说明短期情绪转好
 * 如果后续小阴小阳且缩量，说明在洗盘
 * 例如：暂时没找到例子
 */
public class Filter17 extends BaseFilter {

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
            //            double curRatio = (curClose / curLastClose - 1) * 100;
            if (curClose > 10) {
                //                continue;
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

            double _4diffRatio = (_4Kline.getClose() / _5Kline.getLastClose() - 1) * 100;
            double _3diffRatio = (_3Kline.getClose() / _4Kline.getClose() - 1) * 100;

            boolean upTop = _4diffRatio > 9.9; // 第一天涨停
            boolean _3fakeDown = _3Kline.getOpen() > _4Kline.getClose() && _3Kline.getClose() < _3Kline.getOpen() && _3Kline.getClose() > _4Kline.getClose(); // 第二天假阴线
            boolean _3trueDown = _3Kline.getOpen() > _4Kline.getClose() && _3Kline.getClose() < _3Kline.getOpen() && _3Kline.getClose() < _4Kline.getClose(); // 第二天真阴线
            boolean _2greater_4 = _2Kline.getClose() > _4Kline.getClose();
            boolean _1greater_4 = _1Kline.getClose() > _4Kline.getClose();

            if (upTop
              && _3fakeDown
              && _2greater_4
              && _1greater_4
            ) {
                System.out.println(code);
                res.add(code);
            }
        }
        return res;
    }


}
