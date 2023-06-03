package luonq.strategy;

import bean.MA;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.SetUtils;
import luonq.stock.FilterStock;
import util.BaseUtils;
import util.Constants;
import util.StrategyUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 胜率太低50%，暂停
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
        String kLinePath = Constants.STD_DAILY_PATH;
        Map<String, String> fileMap = BaseUtils.getFileMap(kLinePath);
        Map<String, Integer> dayToCountMap = Maps.newHashMap();
        Map<String, List<Double>> dayToChangePntMap = Maps.newHashMap();

        List<String> flatTradeStockList = FilterStock.tradeFlat(kLinePath);
        for (String stock : fileMap.keySet()) {
            if (flatTradeStockList.contains(stock)) {
                continue;
            }
            //            if (BaseUtils.after_2000(kLinePath + stock)) {
            //                continue;
            //            }
            if (!stock.equals("AAPL")) {
                continue;
            }
            int longCount = 5;
            String period = "daily";
            init(stock, period);

            // all
            Map<String, StockKLine> all = all();

            Map<String, String> nextDay = nextDay(stock, period);

            // 强多头ma 持续五天以上
            //            Map<String, MA> strongLong = longPermute(stock, period, longCount, 60);

            // 弱多头ma 持续五天以上
            Map<String, MA> weakLong = longPermute(stock, period, longCount, 30);

            // 第二天开盘大于前一天收盘
            //            Map<String, StockKLine> openGreatPrevClose = openGreatPrevClose();

            // 第二天收盘大于前一天收盘
            Map<String, StockKLine> nextCloseGreatCurrClose = nextCloseGreatCurrClose();

            // 日最低低于ma5
            //            Map<String, StockKLine> lowLessMA5 = lowLessMA5(stock, period);

            // 日最低低于ma10
            Map<String, StockKLine> lowLessMA10 = lowLessMA10(stock, period);

            // 日最低高于ma20
            Map<String, StockKLine> lowGreatMA20 = lowGreatMA20(stock, period);

            // 回踩差值占比
            Map<String, Double> stepBackRateMap = stepBackRate();

            // 收盘大于十日线
            Map<String, StockKLine> closeGreatMA10 = closeGreatMA10(stock, period);

            // 分母
            Set<String> denominator = StrategyUtils.computerIntersection(weakLong.keySet(), lowLessMA10.keySet(), lowGreatMA20.keySet(), closeGreatMA10.keySet());
            // 分子
            Set<String> numerator = SetUtils.intersection(denominator, nextCloseGreatCurrClose.keySet());

            List<String> show = Lists.newArrayList(denominator);
            Collections.sort(show, Comparator.comparingInt(BaseUtils::dateToInt).reversed());
            //            List<String> diff = CollectionUtils.disjunction(denominator, stdList).stream().collect(Collectors.toList());
            //            Collections.sort(diff, Comparator.comparingInt(BaseUtils::dateToInt).reversed());

            //            double rate = (double) stdList.size() / (double) weekLongAndlowLessMA5Size * 100;
            //            System.out.println(stock
            //              + " weakLong: " + weakLong.size()
            //              + " openGreatPrevClose: " + openGreatPrevClose.size()
            //              + " lowLessMA5: " + lowLessMA5.size()
            //              + " weekLongAndlowLessMA5: " + weekLongAndlowLessMA5Size
            //              + " result: " + stdList.size()
            //              + " rate: " + rate);

            double trueCount = 0, sum = 0;
            for (int i = 0; i < show.size(); i++) {
                String day = show.get(i);
                String nextDate = nextDay.get(day);

                StockKLine dayK = all.get(day);
                StockKLine nextDayK = all.get(nextDate);

                double changePnt = dayK.getChangePnt();
                double nextChangePnt = nextDayK.getChangePnt();

                double close = dayK.getClose();
                double nextHigh = nextDayK.getHigh();
                double diff = nextHigh - close;

//                if (changePnt < 0) {
//                    continue;
//                }
                sum++;
                boolean contains = numerator.contains(day);
                if (contains) {
                    trueCount++;
                    if (!dayToCountMap.containsKey(day)) {
                        dayToCountMap.put(day, 0);
                    }
                    dayToCountMap.put(day, dayToCountMap.get(day) + 1);

                    if (!dayToChangePntMap.containsKey(day)) {
                        dayToChangePntMap.put(day, Lists.newArrayList());
                    }
                    dayToChangePntMap.get(day).add(changePnt);
                }
                System.out.println(i + 1 + "\t" + day + "\t" + stepBackRateMap.get(day) + "\t" + contains + "\t" + changePnt + "\t" + nextChangePnt + "\t" + diff);
            }
            if (sum == 0) {
                continue;
            }
            //            System.out.println(stock + "\t" + trueCount / sum * 100);
            //            System.out.println(diff);
        }
//        System.out.println(dayToCountMap);
//        for (String day : dayToCountMap.keySet()) {
//            Integer count = dayToCountMap.get(day);
//            List<Double> changePntList = dayToChangePntMap.get(day);
//            Map<String, Long> changePntCountMap = changePntList.stream().map(c -> String.valueOf(c)).map(c -> c.substring(0, c.indexOf("."))).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
//            System.out.println(day + "\t" + count + "\t" + JSON.toJSONString(changePntCountMap));
//        }
    }

    private static Map<String, String> nextDay(String stock, String period) {
        Map<String, String> map = Maps.newHashMap();
        for (int i = 0; i < kLineList.size() - 1; i++) {
            StockKLine next = kLineList.get(i);
            StockKLine current = kLineList.get(i + 1);
            map.put(current.getDate(), next.getDate());
        }
        return map;
    }

    private static Map<String, MA> longPermute(String stock, String period, int longCount, int maDayCount) throws Exception {
        String maPath = Constants.INDICATOR_MA_PATH + period + "/" + stock;
        List<String> lineList = BaseUtils.readFile(maPath);
        List<MA> maList = lineList.stream().map(MA::convert).collect(Collectors.toList());
        Collections.reverse(maList);

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

    private static Map<String, StockKLine> all() throws Exception {
        Map<String, StockKLine> map = Maps.newHashMap();
        for (int i = 0; i < kLineList.size() - 1; i++) {
            StockKLine current = kLineList.get(i);
            StockKLine prev = kLineList.get(i + 1);
            String date = current.getDate();
            if (prev.getClose() == 0) {
                continue;
            }
            double changePnt = BigDecimal.valueOf((current.getClose() - prev.getClose()) / prev.getClose() * 100).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
            current.setChangePnt(changePnt);

            map.put(date, current);
        }

        return map;
    }

    private static Map<String, StockKLine> openGreatPrevClose() throws Exception {
        Map<String, StockKLine> map = Maps.newHashMap();
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

    private static Map<String, StockKLine> nextCloseGreatCurrClose() throws Exception {
        Map<String, StockKLine> map = Maps.newHashMap();
        for (int i = 0; i < kLineList.size() - 1; i++) {
            StockKLine next = kLineList.get(i);
            StockKLine current = kLineList.get(i + 1);
            String date = current.getDate();

            if (next.getClose() > current.getClose()) {
                map.put(date, current);
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

    private static Map<String, StockKLine> lowGreatMA20(String stock, String period) throws Exception {
        return lowGreatMA(stock, period, 20);
    }

    private static Map<String, StockKLine> lowLessMA(String stock, String period, int man) throws Exception {
        Map<String, StockKLine> dateToKlineMap = kLineList.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k, (k1, k2) -> k1));

        String maPath = Constants.INDICATOR_MA_PATH + period + "/" + stock;
        List<String> mas = BaseUtils.readFile(maPath);
        Map<String, MA> dateToMaMap = mas.stream().map(MA::convert).collect(Collectors.toMap(MA::getDate, m -> m, (m1, m2) -> m1));

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

    private static Map<String, StockKLine> closeGreatMA10(String stock, String period) throws Exception {
        return closeGreatMA(stock, period, 10);
    }

    private static Map<String, StockKLine> closeGreatMA(String stock, String period, int man) throws Exception {
        Map<String, StockKLine> dateToKlineMap = kLineList.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k, (k1, k2) -> k1));

        String maPath = Constants.INDICATOR_MA_PATH + period + "/" + stock;
        List<String> mas = BaseUtils.readFile(maPath);
        Map<String, MA> dateToMaMap = mas.stream().map(MA::convert).collect(Collectors.toMap(MA::getDate, m -> m, (m1, m2) -> m1));

        Map<String, StockKLine> map = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::dateToInt).reversed());
        for (String date : dateToMaMap.keySet()) {
            if (dateToKlineMap.containsKey(date)) {
                MA ma = dateToMaMap.get(date);
                StockKLine kLine = dateToKlineMap.get(date);
                double close = kLine.getClose();
                if (man == 5 && close > ma.getMa5()) {
                    map.put(date, kLine);
                } else if (man == 10 && close > ma.getMa10()) {
                    map.put(date, kLine);
                } else if (man == 20 && close > ma.getMa20()) {
                    map.put(date, kLine);
                } else if (man == 30 && close > ma.getMa30()) {
                    map.put(date, kLine);
                } else if (man == 60 && close > ma.getMa60()) {
                    map.put(date, kLine);
                }
            }
        }

        return map;
    }

    private static Map<String, StockKLine> lowGreatMA(String stock, String period, int man) throws Exception {
        Map<String, StockKLine> dateToKlineMap = kLineList.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k, (k1, k2) -> k1));

        String maPath = Constants.INDICATOR_MA_PATH + period + "/" + stock;
        List<String> mas = BaseUtils.readFile(maPath);
        Map<String, MA> dateToMaMap = mas.stream().map(MA::convert).collect(Collectors.toMap(MA::getDate, m -> m, (m1, m2) -> m1));

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
