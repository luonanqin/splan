package luonq.indicator;

import bean.MA;
import bean.StockKLine;
import util.BaseUtils;
import util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Luonanqin on 2023/2/3.
 * 从标准 K 线目录读文件写指标文件；均线数值由 {@link MovingAverageSma} 计算。
 */
public class MoveAverage {

    public static void main(String[] args) throws Exception {
        calculate("daily");
        calculate("weekly");
        calculate("monthly");
        calculate("quarterly");
        calculate("yearly");
    }

    public static void calculate(String period) throws Exception {
        Map<String, String> stockToKLineMap = BaseUtils.getFileMap(Constants.STD_BASE_PATH + period + "/");
        Map<String, String> hasCalcMap = BaseUtils.getFileMap(Constants.INDICATOR_MA_PATH + period);
        Set<String> hasCalcStock = hasCalcMap.keySet();

        for (String stock : stockToKLineMap.keySet()) {
            if (!stock.equals("AAPL")) {
                //                continue;
            }
            if (hasCalcStock.contains(stock)) {
                continue;
            }
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(stockToKLineMap.get(stock));

            List<Double> closes = new ArrayList<>(stockKLines.size());
            List<String> dates = new ArrayList<>(stockKLines.size());
            for (StockKLine k : stockKLines) {
                closes.add(k.getClose());
                dates.add(k.getDate());
            }
            List<MA> series = MovingAverageSma.computeSeries(closes, dates, MovingAverageSma.SCALE_FILE);
            List<String> maList = new ArrayList<>(series.size());
            for (MA ma : series) {
                maList.add(ma.toString());
            }

            BaseUtils.writeFile(Constants.INDICATOR_MA_PATH + period + "/" + stock, maList);
            System.out.println("finish " + period + " " + stock);
        }
    }
}
