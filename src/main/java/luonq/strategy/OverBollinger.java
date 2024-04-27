package luonq.strategy;

import bean.BOLL;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import luonq.stock.FilterStock;
import util.BaseUtils;
import util.Constants;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ROUND_HALF_UP;

/**
 * Created by Luonanqin on 2023/3/21.
 */
public class OverBollinger {

    public static final String TEST_STOCK = "";

    @Data
    public static class Bean implements Serializable {
        String date;
        private double open;
        private double close;
        private double high;
        private double low;
        private double up;
        private double changePnt;
        private double highUpDiffPnt;
        private double highCloseDiffPnt;
        private double openUpDiffPnt;
        private double closeUpDiffPnt;
        private int closeLessOpen; // true=1 false=0

        public String toString() {
            return String.format("%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f", date, open, close, high, low, up, highUpDiffPnt, highCloseDiffPnt);
        }
    }

    @Data
    public static class RatioBean implements Serializable {
        List<Bean> beanList = Lists.newArrayList();
        double ratio;

        public void add(Bean bean) {
            beanList.add(bean);
            long trueCount = beanList.stream().filter(c -> c.getCloseLessOpen() == 1).count();
            int count = beanList.size();
            ratio = (double) trueCount / count;
        }
    }

    @Data
    public static class StockRatio implements Serializable {
        Map<Integer, RatioBean> ratioMap = Maps.newHashMap();

        public void addBean(Bean bean) {
            double up = bean.getUp();
            double high = bean.getHigh();
            double open = bean.getOpen();
            if (!(up < high && up < open)) {
                return;
            }

            double openUpDiffPnt = bean.getOpenUpDiffPnt();
            int openUpDiffRange = (int) openUpDiffPnt;
            if (openUpDiffRange < 0) {
                return;
            }
            if (openUpDiffRange > 6) {
                if (!ratioMap.containsKey(6)) {
                    ratioMap.put(6, new RatioBean());
                }
                ratioMap.get(6).add(bean);
            } else if (ratioMap.containsKey(openUpDiffRange)) {
                ratioMap.get(openUpDiffRange).add(bean);
            } else {
                RatioBean ratioBean = new RatioBean();
                ratioBean.add(bean);
                ratioMap.put(openUpDiffRange, ratioBean);
            }
        }

        public String toString() {
            List<String> s = Lists.newArrayList();
            for (Integer ratio : ratioMap.keySet()) {
                s.add(String.format("%d=%.3f", ratio, ratioMap.get(ratio).getRatio()));
            }
            return StringUtils.join(s, ",");
        }
    }


    public static void main(String[] args) throws Exception {
        double capital = 10000;
        Map<String, StockRatio> originRatioMap = computeHistoricalOverBollingerRatio();

        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.STD_DAILY_PATH);

        // 构建2022年各股票k线
        Map<String, Map<String, StockKLine>> dateToStockLineMap = Maps.newHashMap();
        for (String stock : originRatioMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, 2022, 2020);

            for (StockKLine kLine : kLines) {
                String date = kLine.getDate();
                if (!dateToStockLineMap.containsKey(date)) {
                    dateToStockLineMap.put(date, Maps.newHashMap());
                }
                dateToStockLineMap.get(date).put(stock, kLine);
            }
        }

        // 构建2022年各股票bolling线
        Map<String, Map<String, BOLL>> dateToStockBollMap = Maps.newHashMap();
        for (String stock : originRatioMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.INDICATOR_BOLL_PATH + "daily/" + stock, 2022, 2021);

            for (BOLL boll : bolls) {
                String date = boll.getDate();
                if (!dateToStockBollMap.containsKey(date)) {
                    dateToStockBollMap.put(date, Maps.newHashMap());
                }
                dateToStockBollMap.get(date).put(stock, boll);
            }
        }

        // 准备当天和20天前的日期映射，用于实时计算布林值
        List<StockKLine> kLines = BaseUtils.loadDataToKline(dailyFileMap.get("AAPL"), 2022, 2020);
        Map<String, List<String>> dateToBefore20dayMap = Maps.newHashMap();
        List<String> dateList = Lists.newArrayList();
        for (int i = 0; i < kLines.size(); i++) {
            if (i + 20 > kLines.size() - 1) {
                break;
            }
            String date = kLines.get(i).getDate();
            List<String> list = Lists.newArrayList();
            for (int j = 1; j < 20; j++) {
                list.add(kLines.get(i + j).getDate());
            }
            dateToBefore20dayMap.put(date, list);

            String year = date.substring(date.lastIndexOf("/") + 1);
            if (year.equals("2022")) {
                dateList.add(date);
            }
        }
        Collections.reverse(dateList);

        // 根据open实时计算出超过up比例最高的前十股票，然后再遍历计算收益
        Map<String, List<String>> dateToStocksMap = Maps.newHashMap();
        for (String date : dateToStockBollMap.keySet()) {
            Map<String, StockKLine> stockToKlineMap = dateToStockLineMap.get(date);
            Map<String, BOLL> stockToBollMap = dateToStockBollMap.get(date);
            Map<String, Double> stockToRatioMap = Maps.newHashMap();
            for (String stock : stockToKlineMap.keySet()) {
                StockKLine kline = stockToKlineMap.get(stock);
                BOLL boll = stockToBollMap.get(stock);
                if (boll != null) {
                    double up = boll.getUp();
                    double open = kline.getOpen();
                    double ratio = (open - up) / up;
                    stockToRatioMap.put(stock, ratio);
                }
            }
            List<String> stocks = stockToRatioMap.entrySet().stream().sorted((o1, o2) -> {
                if (o1.getValue() < o2.getValue()) {
                    return 1;
                }
                return -1;
            }).map(o -> o.getKey()).collect(Collectors.toList());
            dateToStocksMap.put(date, stocks);
        }

        List<Double> hitRatio = Lists.newArrayList(0.8d, 0.9d, 1d);
        List<Double> lossRatioRange = Lists.newArrayList(0.04d, 0.05d, 0.06d, 0.07d, 0.08d, 0.09d, 0.1d, 0.12d, 0.15d);
        for (Double lossRange : lossRatioRange) {
            for (int i = 0; i < hitRatio.size(); i++) {
                double hit = hitRatio.get(i);

                Double nextHit = 2d;
                if (i + 1 < hitRatio.size()) {
                    nextHit = hitRatio.get(i + 1);
                }
                if (hit != 0.8d || lossRange != 0.15d) {
                    continue;
                }
                Map<String, StockRatio> ratioMap = SerializationUtils.clone((HashMap<String, StockRatio>) originRatioMap);

                int gainCount = 0, lossCount = 0;
                for (String date : dateList) {
                    Map<String, StockKLine> stockKLineMap = dateToStockLineMap.get(date);
                    Map<String, BOLL> stockBollMap = dateToStockBollMap.get(date);
                    List<String> stocks = dateToStocksMap.get(date);

                    boolean hasCompute = false;
                    for (String stock : stocks) {
                        StockKLine kLine = stockKLineMap.get(stock);
                        BOLL boll = stockBollMap.get(stock);

                        double open = kLine.getOpen();
                        double close = kLine.getClose();
                        double high = kLine.getHigh();
                        double low = kLine.getLow();
                        double currMb = boll.getMb();

                        if (open < currMb) {
                            continue;
                        }

                        // 根据开盘价实时算布林上轨
                        BigDecimal m20close = BigDecimal.valueOf(open);
                        List<String> _20day = dateToBefore20dayMap.get(date);
                        List<StockKLine> _20Kline = Lists.newArrayList(kLine);
                        boolean failed = false;
                        for (String day : _20day) {
                            StockKLine temp = dateToStockLineMap.get(day).get(stock);
                            if (temp == null) {
                                failed = true;
                                break;
                            }
                            _20Kline.add(temp);
                            m20close = m20close.add(BigDecimal.valueOf(temp.getClose()));
                        }
                        if (failed) {
                            continue;
                        }

                        double mb = m20close.divide(BigDecimal.valueOf(20), 2, ROUND_HALF_UP).doubleValue();
                        BigDecimal avgDiffSum = BigDecimal.ZERO;
                        for (StockKLine temp : _20Kline) {
                            double c = temp.getClose();
                            avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(c - mb).pow(2));
                        }

                        double md = Math.sqrt(avgDiffSum.doubleValue() / 20);
                        BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
                        double up = BigDecimal.valueOf(mb).add(mdPow2).setScale(3, ROUND_DOWN).doubleValue();

                        if (open < up) {
                            continue;
                        }
                        // 根据开盘价算openUpDiffRatio
                        double openUpDiffPnt = (open - up) / up;
                        int openUpDiffInt = (int) openUpDiffPnt;
                        BigDecimal volume = kLine.getVolume();

                        StockRatio stockRatio = ratioMap.get(stock);
                        Map<Integer, RatioBean> ratioDetail = stockRatio.getRatioMap();
                        if (MapUtils.isEmpty(ratioDetail)) {
                            stockRatio.addBean(buildBean(kLine, boll));
                            continue;
                        }

                        RatioBean ratioBean = ratioDetail.get(openUpDiffInt);
                        if (ratioBean == null || ratioBean.getRatio() < hit) {
                            stockRatio.addBean(buildBean(kLine, boll));
                            continue;
                        }

                        if (hasCompute) {
                            stockRatio.addBean(buildBean(kLine, boll));
                            continue;
                        }

                        int count = (int) (capital / open);
                        double lossRatio = (high - open) / open;
                        double v = lossRange;
                        int openLowDiff = (int) ((open - low) / open * 100);
                        if (lossRatio > v) {
                            double loss = count * (open - open * (1 + v));
                            capital += loss;
                            System.out.println("date=" + date + ", stock=" + stock + ", volumn=" + volume + ", loss = " + (int) loss);
                            //                                                        System.out.println(String.format("loss lossRatio=%d", (int)(lossRatio*100)));
                            //                            stockRatio.addBean(buildBean(kLine, boll));
                            lossCount++;
                            //                            break;
                        } else {
                            double gain = count * (open - close);
                            capital += gain;
                            System.out.println("date=" + date + ", stock=" + stock + ", volumn=" + volume + ", gain = " + (int) gain);
                            //                            stockRatio.addBean(buildBean(kLine, boll));

                            if (gain >= 0) {
                                //                                System.out.println(String.format("gain openLowDiff=%d", openLowDiff));
                                gainCount++;
                            } else {
                                lossCount++;
                                //                                System.out.println(String.format("loss openLowDiff=%d, closeOpenDiff=%d", openLowDiff, (int) ((close - open) / open * 100)));
                            }
                            //                            break;
                        }
                        stockRatio.addBean(buildBean(kLine, boll));
                        hasCompute = true;
                    }
                }
                double successRatio = (double) gainCount / (gainCount + lossCount);
                System.out.println("hit=" + hit + ", loss=" + lossRange + ", sum=" + (int) capital + ", gainCount=" + gainCount + ", lossCount=" + lossCount + ", successRatio=" + successRatio);
                capital = 10000;
            }
            System.out.println();
        }
    }

    public Map<String, StockRatio> loadRatio() throws Exception {
        Map<String, StockRatio> stockRatioMap = Maps.newHashMap();

        List<String> linesList = BaseUtils.readFile(Constants.TEST_PATH);
        for (String line : linesList) {
            String[] split = line.split(":");
            String stock = split[0];

            String ratioStr = split[1].trim();
        }

        return stockRatioMap;
    }

    public static Map<String, StockRatio> computeHistoricalOverBollingerRatio() throws Exception {
        // 最高超过布林线上轨
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.STD_DAILY_PATH);
        List<String> filterStock = FilterStock.tradeFlat(Constants.STD_DAILY_PATH);
        List<String> canShortStock = BaseUtils.readFile(Constants.TEST_PATH + "canShort");

        Map<String, Integer> dateToCountMap = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::dateToInt).reversed());
        Map<String, StockRatio> stockRatioMap = Maps.newHashMap();
        for (String stock : dailyFileMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            if (!filterStock.contains(stock)) {
                //                continue;
            }
            if (!canShortStock.contains(stock)) {
                continue;
            }

            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, 2021);
            Map<String, StockKLine> dateToKLineMap = kLines.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k, (k1, k2) -> k1));

            String bollingPath = Constants.INDICATOR_BOLL_PATH + "daily/" + stock;
            List<String> lineList = BaseUtils.readFile(bollingPath);
            if (CollectionUtils.isEmpty(lineList)) {
                continue;
            }
            Map<String, BOLL> dateToBollMap = lineList.stream().map(BOLL::convert).collect(Collectors.toMap(BOLL::getDate, b -> b, (b1, b2) -> b1));

            // 第二天收盘小于前一天收盘
            //            Map<String, StockKLine> nextCloseLessCurrClose = nextCloseLessCurrClose(kLines);

            List<Bean> result = strategy1(dateToKLineMap, dateToBollMap);

            int begin = 1, end = 6;
            boolean first = true;
            Map<Integer, List<Bean>> tempMap = Maps.newHashMap();

            StockRatio stockRatio = new StockRatio();
            result.stream().forEach(r -> stockRatio.addBean(r));
            stockRatioMap.put(stock, stockRatio);
            //            while (true) {
            //                int i = begin;
            //                List<Bean> collect;
            //                if (begin < end) {
            //                    collect = result.stream().filter(r -> r.getOpenUpDiffPnt() > i && r.getOpenUpDiffPnt() < i + 1).collect(Collectors.toList());
            //                } else {
            //                    collect = result.stream().filter(r -> r.getOpenUpDiffPnt() > i).collect(Collectors.toList());
            //                }
            //                tempMap.put(i, collect);
            //                long trueCount = collect.stream().filter(c -> c.getCloseLessOpen() == 1).count();
            //                long falseCount = collect.stream().filter(c -> c.getCloseLessOpen() == 0).count();
            //
            //                int count = collect.size();
            //                double ratio = (double) trueCount / count;
            //                if (ratio > 0.9) {
            //                    if (first) {
            //                        //                        System.out.println(stock);
            //                        first = false;
            //                    }
            //                    collect.forEach(c -> {
            //                        String date = c.getDate();
            //                        if (!dateToCountMap.containsKey(date)) {
            //                            dateToCountMap.put(date, 0);
            //                        }
            //                        dateToCountMap.put(date, dateToCountMap.get(date) + 1);
            //                    });
            //                    //                    System.out.println("openUpDiff great " + begin + ": count=" + count + ", true=" + trueCount + ", false=" + falseCount + ", rate=" + ratio);
            //                }

            //                if (begin == end) {
            //                    break;
            //                } else {
            //                    begin++;
            //                }
            //            }
            //
            //            List<Bean>[] mergeArray = new ArrayList[7];
            //            Map<Integer, List<Bean>> hitMap = Maps.newHashMap();
            //            for (int i = 1; i < 7; i++) {
            //                List<Bean> beans = tempMap.get(i);
            //                long trueCount = beans.stream().filter(c -> c.getCloseLessOpen() == 1).count();
            //                if ((double) trueCount / beans.size() < 0.8) {
            //                    continue;
            //                }
            //                boolean hasMerge = false;
            //                if (trueCount == beans.size()) {
            //                    for (int j = i - 1; j >= 0; j--) {
            //                        List<Bean> merge = mergeArray[j];
            //
            //                        if (merge != null) {
            //                            long mergeTrueCount = merge.stream().filter(c -> c.getCloseLessOpen() == 1).count();
            //                            if (mergeTrueCount != merge.size()) {
            //                                break;
            //                            }
            //                            merge.addAll(beans);
            //                            mergeArray[j] = merge;
            //                            hasMerge = true;
            //                        } else {
            //                            j--;
            //                        }
            //                    }
            //                    if (!hasMerge) {
            //                        mergeArray[i] = beans;
            //                    }
            //                } else if ((double) trueCount / beans.size() > 0.8) {
            //                    mergeArray[i] = beans;
            //                }
            //            }
            //
            //            for (int i = 0; i < 7; i++) {
            //                List<Bean> beans = mergeArray[i];
            //                if (beans != null) {
            //                    long trueCount = beans.stream().filter(c -> c.getCloseLessOpen() == 1).count();
            //                    long falseCount = beans.stream().filter(c -> c.getCloseLessOpen() == 0).count();
            //                    int count = beans.size();
            //                    double ratio = (double) trueCount / count;
            //
            //                    System.out.println("openUpDiff great " + i + ": count=" + count + ", true=" + trueCount + ", false=" + falseCount + ", rate=" + ratio);
            //                }
            //            }
            //            for (Bean bean : result) {
            //                System.out.println(nextCloseLessCurrClose.containsKey(bean.getDate()) + "\t" + bean);
            //                System.out.println(bean);
            //            }
        }

        for (String stock : stockRatioMap.keySet()) {
            StockRatio ratio = stockRatioMap.get(stock);
            if (MapUtils.isEmpty(ratio.getRatioMap())) {
                continue;
            }
            System.out.println(stock + ": " + ratio);
        }

        //        for (String date : dateToCountMap.keySet()) {
        //            System.out.println(date + ": " + dateToCountMap.get(date));
        //        }

        return stockRatioMap;
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
            if (up < high && up < open) {
                result.add(buildBean(kLine, boll));
            }
        }
        Collections.sort(result, ((Comparator<Bean>) (o1, o2) -> BaseUtils.dateToInt(o1.getDate()) - BaseUtils.dateToInt(o2.getDate())).reversed());
        return result;
    }

    private static Bean buildBean(StockKLine kLine, BOLL boll) {
        double up = boll.getUp();
        String date = kLine.getDate();
        double high = kLine.getHigh();
        double close = kLine.getClose();
        double open = kLine.getOpen();
        double low = kLine.getLow();

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

        double closeUpDiffPnt = BigDecimal.valueOf((close - up) / up).setScale(4, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setCloseUpDiffPnt(closeUpDiffPnt);

        double openUpDiffPnt = BigDecimal.valueOf((open - up) / up).setScale(4, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setOpenUpDiffPnt(openUpDiffPnt);

        bean.setCloseLessOpen(close < open ? 1 : 0);
        return bean;
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
