package luonq.stock;

import bean.MA;
import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Created by Luonanqin on 2023/2/3.
 */
public class MergeMoveAverage {

    public static void main(String[] args) throws Exception {
        calculate();
    }

    public static void calculate() throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> stockToKLineMap = BaseUtils.getFileMap(mergePath);

        for (String stock : stockToKLineMap.keySet()) {
            if (!stock.equals("AAPL")) {
                //                continue;
            }
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(stockToKLineMap.get(stock), 2023);
            List<String> mas = BaseUtils.readMaFile(Constants.HIS_BASE_PATH + "mergeMA/" + stock, 2023, 0);

            int missCount = 0;
            if (CollectionUtils.isNotEmpty(mas)) {
                String[] split = mas.get(0).split(",");
                String maDate = split[0];
                int maDateInt = BaseUtils.dateToInt(maDate);

                for (int i = 0; i < stockKLines.size(); i++) {
                    StockKLine kline = stockKLines.get(i);
                    String date = kline.getDate();
                    int dateInt = BaseUtils.dateToInt(date);
                    if (dateInt <= maDateInt) {
                        missCount = i;
                        break;
                    }
                }
            }
            if (missCount == 0) {
                System.out.println("has calculate: " + stock);
                continue;
            }

            BigDecimal m5close = BigDecimal.ZERO, m10close = BigDecimal.ZERO, m20close = BigDecimal.ZERO, m30close = BigDecimal.ZERO, m60close = BigDecimal.ZERO;
            int ma5count = 0, ma10count = 0, ma20count = 0, ma30count = 0, ma60count = 0;
            double ma5 = 0, ma10 = 0, ma20 = 0, ma30 = 0, ma60 = 0;
            List<String> maList = Lists.newArrayList();
            int i = 58 + missCount;
            if (i > stockKLines.size()) {
                continue;
            }
            for (; i >= 0; i--) {
                StockKLine kLine = stockKLines.get(i);
                String date = kLine.getDate();

                BigDecimal close = BigDecimal.valueOf(kLine.getClose());
                m5close = m5close.add(close);
                m10close = m10close.add(close);
                m20close = m20close.add(close);
                m30close = m30close.add(close);
                m60close = m60close.add(close);

                ma5count++;
                ma10count++;
                ma20count++;
                ma30count++;
                ma60count++;

                if (ma5count == 5) {
                    ma5 = m5close.divide(BigDecimal.valueOf(5), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                    ma5count--;
                    m5close = m5close.subtract(BigDecimal.valueOf(stockKLines.get(i + 5 - 1).getClose()));
                }
                if (ma10count == 10) {
                    ma10 = m10close.divide(BigDecimal.valueOf(10), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                    ma10count--;
                    m10close = m10close.subtract(BigDecimal.valueOf(stockKLines.get(i + 10 - 1).getClose()));
                }
                if (ma20count == 20) {
                    ma20 = m20close.divide(BigDecimal.valueOf(20), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                    ma20count--;
                    m20close = m20close.subtract(BigDecimal.valueOf(stockKLines.get(i + 20 - 1).getClose()));
                }
                if (ma30count == 30) {
                    ma30 = m30close.divide(BigDecimal.valueOf(30), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                    ma30count--;
                    m30close = m30close.subtract(BigDecimal.valueOf(stockKLines.get(i + 30 - 1).getClose()));
                }
                if (ma60count == 60) {
                    ma60 = m60close.divide(BigDecimal.valueOf(60), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
                    ma60count--;
                    m60close = m60close.subtract(BigDecimal.valueOf(stockKLines.get(i + 60 - 1).getClose()));
                    MA ma = MA.builder().date(date).ma5(ma5).ma10(ma10).ma20(ma20).ma30(ma30).ma60(ma60).build();
                    maList.add(ma.toString());
                    mas.add(0, ma.toString());
                }
            }

            BaseUtils.writeFile(Constants.HIS_BASE_PATH + "mergeMA/" + stock, mas);
            System.out.println("finish " + stock);
        }
    }
}
