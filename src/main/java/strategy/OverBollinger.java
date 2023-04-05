package strategy;

import bean.BOLL;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2023/3/21.
 */
public class OverBollinger {

    @Data
    public static class Bean {
        String date;
        private double open;
        private double close;
        private double high;
        private double low;
        private double up;
        private double changePnt;
        private double highUpDiffPnt;
        private double highCloseDiffPnt;

        public String toString() {
            return String.format("%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f", date, open, close, high, low, up, highUpDiffPnt, highCloseDiffPnt);
        }
    }

    public static void main(String[] args) throws Exception {
        // 最高超过布林线上轨
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.STD_DAILY_PATH);
        for (String stock : dailyFileMap.keySet()) {
            if (!stock.equals("TSLA")) {
                continue;
            }

            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath);
            Map<String, StockKLine> dateToKLineMap = kLines.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k, (k1, k2) -> k1));

            String bollingPath = Constants.INDICATOR_BOLL_PATH + "daily/" + stock;
            List<String> lineList = BaseUtils.readFile(bollingPath);
            if (CollectionUtils.isEmpty(lineList)) {
                continue;
            }
            Map<String, BOLL> dateToBollMap = lineList.stream().map(BOLL::convert).collect(Collectors.toMap(BOLL::getDate, b -> b, (b1, b2) -> b1));

            // 第二天收盘小于前一天收盘
            Map<String, StockKLine> nextCloseLessCurrClose = nextCloseLessCurrClose(kLines);

            List<Bean> result = strategy1(dateToKLineMap, dateToBollMap);

            for (Bean bean : result) {
//                System.out.println(nextCloseLessCurrClose.containsKey(bean.getDate()) + "\t" + bean);
                System.out.println(bean);
            }
        }
    }

    private static List<Bean> strategy1(Map<String, StockKLine> dateToKLineMap, Map<String, BOLL> dateToBollMap) {
        List<Bean> result = Lists.newArrayList();
        for (String date : dateToKLineMap.keySet()) {
            if (!dateToBollMap.containsKey(date)) {
                continue;
            }

            BOLL boll = dateToBollMap.get(date);
            double up = boll.getUp();
            if (up == 0) {
                continue;
            }

            StockKLine kLine = dateToKLineMap.get(date);
            double high = kLine.getHigh();
            double close = kLine.getClose();
            double open = kLine.getOpen();
            double low = kLine.getLow();
            if (up < high && up < open && close > open) {
                Bean bean = new Bean();
                bean.setDate(date);
                bean.setOpen(open);
                bean.setClose(close);
                bean.setHigh(high);
                bean.setLow(low);
                bean.setUp(up);

                double highUpDiffPnt = BigDecimal.valueOf((high - up) / up).setScale(4, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
                bean.setHighUpDiffPnt(highUpDiffPnt);

                double highCloseDiffPnt = BigDecimal.valueOf((high - close) / close).setScale(4, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
                bean.setHighCloseDiffPnt(highCloseDiffPnt);

                result.add(bean);
            }
        }
        Collections.sort(result, ((Comparator<Bean>) (o1, o2) -> BaseUtils.dateToInt(o1.getDate()) - BaseUtils.dateToInt(o2.getDate())).reversed());
        return result;
    }

    private static Map<String, StockKLine> nextCloseLessCurrClose(List<StockKLine> kLineList) throws Exception {
        Map<String, StockKLine> map = Maps.newHashMap();
        for (int i = 0; i < kLineList.size() - 1; i++) {
            StockKLine next = kLineList.get(i);
            StockKLine current = kLineList.get(i + 1);
            String date = current.getDate();

            if (next.getClose() < current.getClose()) {
                map.put(date, current);
            }
        }

        return map;
    }
}
