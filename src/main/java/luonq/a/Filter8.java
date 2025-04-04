package luonq.a;

import bean.StockKLine;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 寻找连续跌停后继续持续下跌不反弹的
 * 横盘超过10天，但是最高收盘和最低开盘相差不超过10%，且收盘均价超过前面10天的收盘均价。横盘价离前高不远，且前高没有明显的放量出货走势
 * 例如：600539 2025-03-14之后 狮头股份
 */
public class Filter8 extends BaseFilter {

    public static void main(String[] args) {
        LoadData.init();

        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        for (String code : kLineMap.keySet()) {
            if (!code.equals("002905")) {
//                continue;
            }
            List<StockKLine> stockKLines = kLineMap.get(code);
            int temp = 0;

            if (stockKLines.size() < temp + 22) {
                //                System.out.println("x " + code);
                continue;
            }
            //            StockKLine latest = stockKLines.get(stockKLines.size() - 1 - temp);
            //            if (latest.getClose() > 15d) {
            //                continue;
            //            }

            //            Map<String/* date */, BigDecimal> avgVolMap = cal50volMa(stockKLines);

            for (int i = 0; i < stockKLines.size()-7; i++) {
                StockKLine _1kLine = stockKLines.get(i); // 涨停
                StockKLine _2kLine = stockKLines.get(i + 1); // 第一个跌停
                StockKLine _3kLine = stockKLines.get(i + 2); // 第二个跌停
                double _1ratio = 100 * (_1kLine.getClose() / _1kLine.getLastClose() - 1);
                double _2ratio = 100 * (_2kLine.getClose() / _2kLine.getLastClose() - 1);
                double _3ratio = 100 * (_3kLine.getClose() / _3kLine.getLastClose() - 1);
                if (!(_1ratio > 9.9) || !(_2ratio < -9.9) || !(_3ratio < -9.9)) {
                    continue;
                }

                int downCount = 0;
                for (int j = i + 3; j < stockKLines.size(); j++) {
                    StockKLine kLine = stockKLines.get(j);
                    double ratio = 100 * (kLine.getClose() / kLine.getLastClose() - 1);
                    if (ratio > 0) {
                        break;
                    } else {
                        downCount++;
                    }
                }
                StockKLine kLine = stockKLines.get(i + 7);
                double close = kLine.getClose();
                if (downCount >= 4 && close <= 15) {
                    System.out.println(kLine.getDate() + " " + code);
                }
            }
        }
    }

}
