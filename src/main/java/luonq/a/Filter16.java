package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 前一天涨停，然后接着两天跌破涨停前日收盘，并且涨停前十天量都多于再前面的十天
 * 涨停前面整体要微放量缓慢向上（简单说就是先有控盘，然后大波动洗盘）
 * 例如：002406 2025-02-05 ~ 2025-03-12
 */
public class Filter16 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Filter16 filter16 = new Filter16();
        //        filter16.cal();
        filter16.test(90);
        //                filter16.testOne(94, "002406");
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
            // 第一天要涨停且不是一字板
            if (_3Kline.getDiffRatio() < 9.9 || _3Kline.getOpen() == _3Kline.getClose()) {
                continue;
            }
            // 涨停前一天不涨停
            if (_4Kline.getDiffRatio() > 9.9) {
                continue;
            }
            // 第二天开盘要高开
            if (_2Kline.getOpen() < _2Kline.getLastClose()) {
                continue;
            }
            // 第三天收盘要低于涨停前一天收盘
            if (_1Kline.getClose() > _3Kline.getLastClose()) {
                continue;
            }

            StockKLine _13Kline = stockKLines.get(stockKLines.size() - 13 - temp);
            double ratio = (_3Kline.getLastClose() / _13Kline.getClose() - 1) * 100;
            // 涨停前十天涨幅不高于10%，即排除连板
            if (ratio > 10 || ratio < 0) {
                continue;
            }

            System.out.println(code);
        }
        return res;
    }


}
