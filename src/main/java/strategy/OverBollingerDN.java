package strategy;

import bean.BOLL;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import stock.FilterStock;
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
public class OverBollingerDN {

    public static final String TEST_STOCK = "";

    @Data
    public static class Bean implements Serializable {
        String date;
        private double open;
        private double close;
        private double high;
        private double low;
        private double dn;
        private double changePnt;
        private double lowDnDiffPnt;
        private double highCloseDiffPnt;
        private double openDnDiffPnt;
        private double closeUpDiffPnt;
        private int closeLessOpen; // true=1 false=0

        public String toString() {
            return String.format("%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f", date, open, close, high, low, dn, lowDnDiffPnt, highCloseDiffPnt);
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
            double dn = bean.getDn();
            double low = bean.getLow();
            double open = bean.getOpen();
            if (!(low < dn && open < dn)) {
                return;
            }

            double openDnDiffPnt = bean.getOpenDnDiffPnt();
            int openDnDiffRange = (int) openDnDiffPnt;
            if (openDnDiffRange < 0) {
                return;
            }
            if (openDnDiffRange > 6) {
                if (!ratioMap.containsKey(6)) {
                    ratioMap.put(6, new RatioBean());
                }
                ratioMap.get(6).add(bean);
            } else if (ratioMap.containsKey(openDnDiffRange)) {
                ratioMap.get(openDnDiffRange).add(bean);
            } else {
                RatioBean ratioBean = new RatioBean();
                ratioBean.add(bean);
                ratioMap.put(openDnDiffRange, ratioBean);
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
        double exchange = 6.94;
        double init = 5000;
        int beforeYear = 2022, afterYear = 2020, afterYear2 = 2021, historyBeforeYear = 2021;
        double capital = init;
        Map<String, StockRatio> originRatioMap = computeHistoricalOverBollingerRatio(historyBeforeYear);

        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.STD_DAILY_PATH);

        // 构建2022年各股票k线
        Map<String, Map<String, StockKLine>> dateToStockLineMap = Maps.newHashMap();
        for (String stock : originRatioMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, beforeYear, afterYear);

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
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.INDICATOR_BOLL_PATH + "daily/" + stock, beforeYear, afterYear2);

            for (BOLL boll : bolls) {
                String date = boll.getDate();
                if (!dateToStockBollMap.containsKey(date)) {
                    dateToStockBollMap.put(date, Maps.newHashMap());
                }
                dateToStockBollMap.get(date).put(stock, boll);
            }
        }

        // 准备当天和20天前的日期映射，用于实时计算布林值
        List<StockKLine> kLines = BaseUtils.loadDataToKline(dailyFileMap.get("AAPL"), beforeYear, afterYear);
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
            if (year.equals(String.valueOf(beforeYear))) {
                dateList.add(date);
            }
        }
        Collections.reverse(dateList);

        // 根据open实时计算出低于dn比例最高的前十股票，然后再遍历计算收益
        Map<String, List<String>> dateToStocksMap = Maps.newHashMap();
        for (String date : dateToStockBollMap.keySet()) {
            Map<String, StockKLine> stockToKlineMap = dateToStockLineMap.get(date);
            Map<String, BOLL> stockToBollMap = dateToStockBollMap.get(date);
            Map<String, Double> stockToRatioMap = Maps.newHashMap();
            for (String stock : stockToKlineMap.keySet()) {
                StockKLine kline = stockToKlineMap.get(stock);
                BOLL boll = stockToBollMap.get(stock);
                if (boll != null && boll.getDn() > 0) {
                    double dn = boll.getDn();
                    double open = kline.getOpen();
                    double ratio = (dn - open) / dn;
                    if (ratio < 0) {
                        continue;
                    }
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

        List<Double> hitRatio = Lists.newArrayList(0.5d, 0.6d, 0.7d, 0.8d, 0.9d, 1d);
        hitRatio.clear();
        hitRatio.add(0.5d);
        List<Double> lossRatioRange = Lists.newArrayList(0.15d, 0.2d, 0.3d, 0.4d);
        List<Integer> openRange = Lists.newArrayList(5, 6, 7);
        for (Integer openR : openRange) {
            for (Double lossRange : lossRatioRange) {
                for (int i = 0; i < hitRatio.size(); i++) {
                    double hit = hitRatio.get(i);

                    //                Double nextHit = 2d;
                    //                if (i + 1 < hitRatio.size()) {
                    //                    nextHit = hitRatio.get(i + 1);
                    //                }
                    if (openR != 5 || hit != 0.5d || lossRange != 0.15d) {
                                                continue;
                    }
                    Map<String, StockRatio> ratioMap = SerializationUtils.clone((HashMap<String, StockRatio>) originRatioMap);

                    int gainCount = 0, lossCount = 0;
                    for (String date : dateList) {
                        Map<String, StockKLine> stockKLineMap = dateToStockLineMap.get(date);
                        Map<String, BOLL> stockBollMap = dateToStockBollMap.get(date);
                        List<String> stocks = dateToStocksMap.get(date);

                        boolean hasCompute = false;
                        double income = 0;
                        double sum = capital;
                        for (String stock : stocks) {
                            StockKLine kLine = stockKLineMap.get(stock);
                            BOLL boll = stockBollMap.get(stock);

                            double open = kLine.getOpen();
                            double close = kLine.getClose();
                            double low = kLine.getLow();
                            double currMb = boll.getMb();

                            if (open < openR) {
                                continue;
                            }

                            if (open > currMb) {
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
                            double dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();

                            if (open > dn) {
                                continue;
                            }
                            // 根据开盘价算openDnDiffRatio
                            double openDnDiffPnt = (dn - open) / dn;
                            int openDnDiffInt = (int) openDnDiffPnt;
                            BigDecimal volume = kLine.getVolume();
                            int avgVolume = (int) volume.doubleValue() / 360;

                            StockRatio stockRatio = ratioMap.get(stock);
                            Map<Integer, RatioBean> ratioDetail = stockRatio.getRatioMap();
                            if (MapUtils.isEmpty(ratioDetail)) {
                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            RatioBean ratioBean = ratioDetail.get(openDnDiffInt);
                            if (ratioBean == null || ratioBean.getRatio() < hit) {
                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            if (hasCompute) {
                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            int count = (int) (sum / open);
                            if (count == 0) {
                                hasCompute = true;
                                break;
                            }
                            double lossRatio = (open - low) / open;
                            double v = lossRange;
                            if (avgVolume < count) {
                                count = avgVolume;
                            }
                            sum -= count * open;
                            if (lossRatio > v) {
                                double loss = -count * open * v;
                                income += loss;
                                                                                            System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", loss = " + (int) loss);
                                //                                                        System.out.println(String.format("loss lossRatio=%d", (int)(lossRatio*100)));
                                //                            stockRatio.addBean(buildBean(kLine, boll));
                                lossCount++;
                                //                            break;
                            } else {
                                double gain = count * (close - open);
                                income += gain;
                                                                                            System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", gain = " + (int) gain);
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
                        }
                        capital += income;
                                                                    System.out.println("date=" + date + ", income=" + income + ", capital=" + capital);
                    }
                    double successRatio = (double) gainCount / (gainCount + lossCount);
                    System.out.println("openRange=" + openR + ", hit=" + hit + ", loss=" + lossRange + ", sum=" + (int) (capital) + ", gainCount=" + gainCount + ", lossCount=" + lossCount + ", successRatio=" + successRatio);
                    capital = init;
                }
                System.out.println();
            }
        }
    }

    public static Map<String, StockRatio> computeHistoricalOverBollingerRatio(int beforeYear) throws Exception {
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.STD_DAILY_PATH);
        List<String> filterStock = FilterStock.tradeFlat(Constants.STD_DAILY_PATH);

        Map<String, StockRatio> stockRatioMap = Maps.newHashMap();
        for (String stock : dailyFileMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            if (!filterStock.contains(stock)) {
                //                continue;
            }

            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, beforeYear);
            Map<String, StockKLine> dateToKLineMap = kLines.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k, (k1, k2) -> k1));

            String bollingPath = Constants.INDICATOR_BOLL_PATH + "daily/" + stock;
            List<String> lineList = BaseUtils.readFile(bollingPath);
            if (CollectionUtils.isEmpty(lineList)) {
                continue;
            }
            Map<String, BOLL> dateToBollMap = lineList.stream().map(BOLL::convert).collect(Collectors.toMap(BOLL::getDate, b -> b, (b1, b2) -> b1));

            List<Bean> result = strategy1(dateToKLineMap, dateToBollMap);

            StockRatio stockRatio = new StockRatio();
            result.stream().forEach(r -> stockRatio.addBean(r));
            stockRatioMap.put(stock, stockRatio);
        }

        //        for (String stock : stockRatioMap.keySet()) {
        //            StockRatio ratio = stockRatioMap.get(stock);
        //            if (MapUtils.isEmpty(ratio.getRatioMap())) {
        //                continue;
        //            }
        //            System.out.println(stock + ": " + ratio);
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
            double dn = boll.getDn();
            if (dn == 0) {
                continue;
            }

            StockKLine kLine = dateToKLineMap.get(date);
            double open = kLine.getOpen();
            double low = kLine.getLow();
            if (low < dn && open < dn) {
                result.add(buildBean(kLine, boll));
            }
        }
        Collections.sort(result, ((Comparator<Bean>) (o1, o2) -> BaseUtils.dateToInt(o1.getDate()) - BaseUtils.dateToInt(o2.getDate())).reversed());
        return result;
    }

    private static Bean buildBean(StockKLine kLine, BOLL boll) {
        double dn = boll.getDn();
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
        bean.setDn(dn);

        double lowDnDiffPnt = BigDecimal.valueOf((dn - low) / dn).setScale(4, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setLowDnDiffPnt(lowDnDiffPnt);

        double highCloseDiffPnt = BigDecimal.valueOf((high - close) / close).setScale(4, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setHighCloseDiffPnt(highCloseDiffPnt);

        double closeUpDiffPnt = BigDecimal.valueOf((close - dn) / dn).setScale(4, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setCloseUpDiffPnt(closeUpDiffPnt);

        double openDnDiffPnt = BigDecimal.valueOf((dn - open) / dn).setScale(4, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setOpenDnDiffPnt(openDnDiffPnt);

        bean.setCloseLessOpen(close > open ? 1 : 0);
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
