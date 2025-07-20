package luonq.a;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 一根大阳线涨停后，四天内跌回涨停开盘，但是不低于涨停日的最低。目的是缓慢拉高但是强势洗盘
 * 涨停前最好没有涨停，并且之前最好有横盘洗盘
 * 例如：002688 2025-03-27 ~ 2025-03-31
 */
public class Filter22 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();
        Filter22 filter22 = new Filter22();
        filter22.cal();
        //        filter22.test(71);
        //        filter22.testOne(70, "002688");
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

            StockKLine topKLine = stockKLines.get(index - 4);
            // 第一天涨停
            if (topKLine.getDiffRatio() < 9.9) {
                continue;
            }
            // 涨停必须是大实体
            double openDiff = (topKLine.getOpen() / topKLine.getLastClose() - 1) * 100;
            if (openDiff > 2) {
                continue;
            }
            //            StockKLine _1kLine = stockKLines.get(index - 3);
            //            if (_1kLine.get) {
            //
            //            }

            // 四天内打到预期位置
            for (int i = index - 3; i < index; i++) {
                StockKLine kLine = stockKLines.get(i);
                double close = kLine.getClose();
                double open = kLine.getOpen();
                // 必须阴线，无论真假
                if (close > open) {
                    break;
                }
                // 收盘低于涨停开盘高于涨停最低
                if (close < topKLine.getOpen() && close >= topKLine.getLow()) {
                    StockKLine benchmark = stockKLines.get(i - 10);
                    double diffRatio = 100 * (close / benchmark.getClose() - 1);
                    // 打到位的那天之前十天不能有大涨幅
                    if (diffRatio > 20) {
                        continue;
                    }
                    System.out.println(code);
                    res.add(code);
                    break;
                }
            }
        }
        return res;
    }


}
