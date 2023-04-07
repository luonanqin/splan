package strategy;

import bean.BOLL;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import stock.FilterStock;
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
        private double openUpDiffPnt;
        private double closeUpDiffPnt;
        private int closeLessOpen; // true=1 false=0

        public String toString() {
            return String.format("%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f", date, open, close, high, low, up, highUpDiffPnt, highCloseDiffPnt);
        }
    }

    @Data
    public static class RatioBean {
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
    public static class StockRatio {
        Map<Integer, RatioBean> ratioMap = Maps.newHashMap();

        public void addBean(Bean bean) {
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
        // 最高超过布林线上轨
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.STD_DAILY_PATH);
        List<String> filterStock = FilterStock.tradeFlat(Constants.STD_DAILY_PATH);

        Map<String, Integer> dateToCountMap = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::dateToInt).reversed());
        Map<String, StockRatio> stockRatioMap = Maps.newHashMap();
        for (String stock : dailyFileMap.keySet()) {
            if (!stock.equals("TSLA")) {
                continue;
            }
            if (filterStock.contains(stock)) {
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
            System.out.println(stock + ": " + stockRatioMap.get(stock));
        }

        //        for (String date : dateToCountMap.keySet()) {
        //            System.out.println(date + ": " + dateToCountMap.get(date));
        //        }
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
