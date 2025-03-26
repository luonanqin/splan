package luonq.a;

import bean.StockKLine;
import util.LoadData;

import java.util.List;
import java.util.Map;

/**
 * 从前10天到前128天之间，找出这段时间里的两个高点（当日最高价），即高点和次高点，然后选出这两个高点之间差值比例小于10%，即最高点/次高点-1<0.1
 */
public class Filter1 {

    public static void main(String[] args) {
        LoadData.init();

        Map<String, List<StockKLine>> kLineMap = LoadData.kLineMap;

        for (String code : kLineMap.keySet()) {
            List<StockKLine> stockKLines = kLineMap.get(code);

            int highIndex1 = 0;
            double high1 = stockKLines.get(highIndex1).getHigh();
            for (int i = 0; i < stockKLines.size(); i++) {
                double compareHigh = stockKLines.get(i).getHigh();
                if (high1 < compareHigh) {
                    high1 = compareHigh;
                    highIndex1 = i;
                }
            }

            int highIndex2 = highIndex1 == 0 ? 1 : 0;
            double high2 = stockKLines.get(highIndex2).getHigh();
            for (int i = 0; i < stockKLines.size(); i++) {
                if (highIndex1 == i) {
                    continue;
                }
                double compareHigh = stockKLines.get(i).getHigh();
                if (high2 < compareHigh) {
                    high2 = compareHigh;
                    highIndex2 = i;
                }
            }
            System.out.println(code + " " + stockKLines.get(highIndex1).getDate() + " " + stockKLines.get(highIndex2).getDate());
        }
    }
}
