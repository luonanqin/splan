package test;

import bean.StockKLine;
import com.google.common.collect.Lists;
import util.BaseUtils;
import util.Constants;

import java.util.List;
import java.util.Map;

/**
 * Created by Luonanqin on 2023/4/25.
 */
public class Merge2023 {

    public static void main(String[] args) throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> hasMergeMap = BaseUtils.getFileMap(mergePath);

        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.STD_DAILY_PATH);
        List<String> stockList = Lists.newArrayList(dailyFileMap.keySet());
        //        stockList.clear();
        //        stockList.add("A");

        Map<String, String> _2023Map = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2023daily");
        for (String stock : stockList) {
            //            if (hasMergeMap.containsKey(stock)) {
            //                continue;
            //            }

            if (!_2023Map.containsKey(stock)) {
                continue;
            }

            String _2023File = _2023Map.get(stock);
            List<StockKLine> _2023KLines = BaseUtils.loadDataToKline(_2023File, 2023);

            if (_2023KLines.size() < 40) {
                continue;
            }

            String dailyPath = dailyFileMap.get(stock);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(dailyPath);
            _2023KLines.addAll(stockKLines);

            BaseUtils.writeStockKLine(mergePath + stock, _2023KLines);
            System.out.println("finish " + stock);
        }
    }
}
