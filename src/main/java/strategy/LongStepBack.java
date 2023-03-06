package strategy;

import bean.MA;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2023/3/3.
 */
public class LongStepBack {

    private static List<StockKLine> kLineList;

    public static void init(String stock, String period) throws Exception {
        String kLingPath = Constants.STD_BASE_PATH + period + "/" + stock;
        List<String> kLines = BaseUtils.readFile(kLingPath);
        kLineList = BaseUtils.convertToKLine(kLines);
    }

    public static void main(String[] args) throws Exception {
        String kLingPath = Constants.STD_DAILY_PATH;
        Map<String, String> fileMap = BaseUtils.getFileMap(kLingPath);

        for (String stock : fileMap.keySet()) {
            if (!stock.equals("AAPL")) {
                continue;
            }
            int longCount = 5;
            String period = "daily";
            init(stock, period);

            // 强多头ma 持续五天以上
            Map<String, MA> strongLong = longPermute(stock, period, longCount, 60);

            // 弱多头ma 持续五天以上
            Map<String, MA> weakLong = longPermute(stock, period, longCount, 30);

            // 第二天开盘大于前一天收盘
            Map<String, StockKLine> openGreatPrevClose = openGreatPrevClose(stock, period);

            // 日最低低于ma5
//            Map<String, StockKLine> lowLessMA5 = lowLessMA5(stock, period);

            // 日最低低于ma10
            Map<String, StockKLine> lowLessMA10 = lowLessMA10(stock, period);

            // 日最低高于ma20
            Map<String, StockKLine> lowGreatMA20 = lowGreatMA20(stock, period);

            // 回踩差值占比
            Map<String, Double> stepBackRateMap = stepBackRate();

            List<String> dateList = Lists.newArrayList();
            for (String date : weakLong.keySet()) {
                if (openGreatPrevClose.containsKey(date) && lowLessMA10.containsKey(date) && lowGreatMA20.containsKey(date)) {
                    dateList.add(date);
                }
            }

            Collection<String> intersection = CollectionUtils.intersection(weakLong.keySet(), lowLessMA10.keySet());
            intersection = CollectionUtils.intersection(intersection, lowGreatMA20.keySet());
            List<String> insecList = Lists.newArrayList(intersection);
            Collections.sort(insecList, Comparator.comparingInt(BaseUtils::dateToInt).reversed());
            int denominator = intersection.size();

            List<String> diff = CollectionUtils.disjunction(intersection, dateList).stream().collect(Collectors.toList());
            Collections.sort(diff, Comparator.comparingInt(BaseUtils::dateToInt).reversed());

            //            double rate = (double) dateList.size() / (double) weekLongAndlowLessMA5Size * 100;
            //            System.out.println(stock
            //              + " weakLong: " + weakLong.size()
            //              + " openGreatPrevClose: " + openGreatPrevClose.size()
            //              + " lowLessMA5: " + lowLessMA5.size()
            //              + " weekLongAndlowLessMA5: " + weekLongAndlowLessMA5Size
            //              + " result: " + dateList.size()
            //              + " rate: " + rate);

            for (String d : insecList) {
                System.out.println(d + " " + stepBackRateMap.get(d) + " " + dateList.contains(d));
            }
            //            System.out.println(diff);
        }
    }

    private static Map<String, MA> longPermute(String stock, String period, int longCount, int maDayCount) throws Exception {
        String maPath = Constants.INDICATOR_MA_PATH + period + "/" + stock;
        List<String> lineList = BaseUtils.readFile(maPath);
        List<MA> maList = lineList.stream().map(MA::convert).collect(Collectors.toList());

        Map<String, MA> map = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::dateToInt).reversed());
        int temp = longCount;
        for (MA ma : maList) {
            String date = ma.getDate();
            double ma5 = ma.getMa5();
            double ma10 = ma.getMa10();
            double ma20 = ma.getMa20();
            double ma30 = ma.getMa30();
            double ma60 = ma.getMa60();

            boolean ma30Greatma60 = true;
            if (maDayCount == 60) {
                ma30Greatma60 = ma30 > ma60;
            }

            boolean ma20Greatma30 = true;
            if (maDayCount == 30) {
                ma20Greatma30 = ma20 > ma30;
            }

            if (ma5 > ma10 && ma10 > ma20 && ma20Greatma30 && ma30Greatma60) {
                temp--;
                if (temp == 0) {
                    map.put(date, ma);
                    temp++;
                }
            } else {
                temp = longCount;
            }
        }

        return map;
    }

    private static Map<String, StockKLine> openGreatPrevClose(String stock, String period) throws Exception {
        //        String kLingPath = Constants.STD_BASE_PATH + period + "/" + stock;
        //        List<String> lineList = BaseUtils.readFile(kLingPath);
        //        List<StockKLine> kLineList = BaseUtils.convertToKLine(lineList);

        Map<String, StockKLine> map = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::dateToInt).reversed());
        for (int i = 0; i < kLineList.size() - 1; i++) {
            StockKLine current = kLineList.get(i);
            StockKLine prev = kLineList.get(i + 1);
            String date = prev.getDate();

            if (current.getOpen() > prev.getClose()) {
                map.put(date, prev);
            }
        }

        return map;
    }

    private static Map<String, StockKLine> lowLessMA5(String stock, String period) throws Exception {
        return lowLessMA(stock, period, 5);
    }

    private static Map<String, StockKLine> lowLessMA10(String stock, String period) throws Exception {
        return lowLessMA(stock, period, 10);
    }

    private static Map<String, StockKLine> lowLessMA(String stock, String period, int man) throws Exception {
        Map<String, StockKLine> dateToKlineMap = kLineList.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k));

        String maPath = Constants.INDICATOR_MA_PATH + period + "/" + stock;
        List<String> mas = BaseUtils.readFile(maPath);
        Map<String, MA> dateToMaMap = mas.stream().map(MA::convert).collect(Collectors.toMap(MA::getDate, m -> m));

        Map<String, StockKLine> map = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::dateToInt).reversed());
        for (String date : dateToMaMap.keySet()) {
            if (dateToKlineMap.containsKey(date)) {
                MA ma = dateToMaMap.get(date);
                StockKLine kLine = dateToKlineMap.get(date);
                double low = kLine.getLow();
                if (man == 5 && low < ma.getMa5()) {
                    map.put(date, kLine);
                } else if (man == 10 && low < ma.getMa10()) {
                    map.put(date, kLine);
                } else if (man == 20 && low < ma.getMa20()) {
                    map.put(date, kLine);
                } else if (man == 30 && low < ma.getMa30()) {
                    map.put(date, kLine);
                } else if (man == 60 && low < ma.getMa60()) {
                    map.put(date, kLine);
                }
            }
        }

        return map;
    }

    private static Map<String, StockKLine> lowGreatMA20(String stock, String period) throws Exception {
        return lowGreatMA(stock, period, 20);
    }

    private static Map<String, StockKLine> lowGreatMA(String stock, String period, int man) throws Exception {
        Map<String, StockKLine> dateToKlineMap = kLineList.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k));

        String maPath = Constants.INDICATOR_MA_PATH + period + "/" + stock;
        List<String> mas = BaseUtils.readFile(maPath);
        Map<String, MA> dateToMaMap = mas.stream().map(MA::convert).collect(Collectors.toMap(MA::getDate, m -> m));

        Map<String, StockKLine> map = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::dateToInt).reversed());
        for (String date : dateToMaMap.keySet()) {
            if (dateToKlineMap.containsKey(date)) {
                MA ma = dateToMaMap.get(date);
                StockKLine kLine = dateToKlineMap.get(date);
                double low = kLine.getLow();
                if (man == 5 && low > ma.getMa5()) {
                    map.put(date, kLine);
                } else if (man == 10 && low > ma.getMa10()) {
                    map.put(date, kLine);
                } else if (man == 20 && low > ma.getMa20()) {
                    map.put(date, kLine);
                } else if (man == 30 && low > ma.getMa30()) {
                    map.put(date, kLine);
                } else if (man == 60 && low > ma.getMa60()) {
                    map.put(date, kLine);
                }
            }
        }

        return map;
    }

    // min(开盘与最低之差,收盘与最低之差)/绝对值(最高与最低之差)
    private static Map<String, Double> stepBackRate() {
        Map<String, Double> map = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::dateToInt).reversed());

        for (int i = 0; i < kLineList.size(); i++) {
            StockKLine kLine = kLineList.get(i);
            String date = kLine.getDate();
            double open = kLine.getOpen();
            double close = kLine.getClose();
            double low = kLine.getLow();
            double high = kLine.getHigh();

            double ocDiff = Math.abs(open - low);
            double lcDiff = Math.abs(close - low);
            double hlDiff = high - low;
            if (hlDiff == 0) {
                continue;
            }
            if (date.equals("08/03/2022")) {
                System.out.println();
            }
            double rate = Math.min(ocDiff, lcDiff) / Math.abs(hlDiff);
            map.put(date, BigDecimal.valueOf(rate).setScale(4, BigDecimal.ROUND_DOWN).doubleValue());
        }

        return map;
    }
}
