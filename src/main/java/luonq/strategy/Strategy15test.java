package luonq.strategy;

import bean.BOLL;
import bean.StockKLine;
import bean.Total;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import luonq.data.ReadFromDB;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 计算历史光脚阴线第二天上涨的概率（光脚阴线每个股票比例不一样），找成功率高的收盘前买入第二天收盘卖出
 */
@Component
public class Strategy15test {

    @Autowired
    private ReadFromDB readFromDB;

    private List<Total> allYearDate = Lists.newArrayList();
    private Map<String, List<Total>> codeTotalMap;
    private Map<String, List<StockKLine>> codeKLineMap = Maps.newHashMap();
    private Map<String, List<BOLL>> codeBollMap = Maps.newHashMap();
    private Set<String> allCode;

    private Map<String, Map<String, StockKLine>> _2023codeTotalMap = Maps.newHashMap();
    Map<String/* code */, StockRatio/* successRatio */> map = Maps.newHashMap();

    private void init() {
        List<Total> _2021 = readFromDB.getAllYearDate("2021");
        List<Total> _2022 = readFromDB.getAllYearDate("2022");
        allYearDate.addAll(_2021);
        allYearDate.addAll(_2022);
        codeTotalMap = allYearDate.stream().collect(Collectors.groupingBy(Total::getCode, Collectors.toList()));
        codeTotalMap.forEach((code, totals) -> {
            codeKLineMap.put(code, totals.stream().map(Total::toKLine).sorted(Comparator.comparingInt(o -> BaseUtils.formatDateToInt(o.getDate()))).collect(Collectors.toList()));
            //            codeBollMap.put(code, totals.stream().map(Total::toBoll).sorted(Comparator.comparingInt(o -> BaseUtils.formatDateToInt(o.getDate()))).collect(Collectors.toList()));
        });

        List<Total> _2023 = readFromDB.getAllYearDate("2023");

        _2023codeTotalMap = Maps.newHashMap();
        for (Total total : _2023) {
            String date = total.getDate();
            String code = total.getCode();
            StockKLine stockKLine = total.toKLine();

            Map<String, StockKLine> sMap = _2023codeTotalMap.get(date);
            if (sMap == null) {
                sMap = Maps.newHashMap();
                _2023codeTotalMap.put(date, sMap);
            }

            sMap.put(code, stockKLine);
        }
    }

    private void buildCodeSet() throws Exception {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<String> codeList;
        do {
            int year = yesterday.getYear();
            String day = yesterday.format(Constants.DB_DATE_FORMATTER);
            codeList = readFromDB.getAllStock(year, day);
            yesterday = yesterday.minusDays(1);
        } while (CollectionUtils.isEmpty(codeList));

        allCode = Sets.newHashSet(codeList);
        Set<String> invalidStockSet = Sets.newHashSet("FRC", "SIVB", "BIOR", "HALL", "OBLG", "ALBT", "IPDN", "OPGN", "TENX", "AYTU", "DAVE", "NXTP", "ATHE", "CANF", "GHSI", "EEMX", "EFAX", "HYMB", "NYC", "SPYX", "PBLA", "JEF", "ACGN", "EAR", "FWBI", "IDRA", "JFU", "CNET", "APM", "JAGX", "OCSL", "OGEN", "SIEN", "SRKZG", "CETX", "UVIX", "EDBL", "PHIO", "SWVL", "MRKR", "REED", "WISA", "FTFT", "FVCB", "LMNL", "REVB", "DYNT", "BRSF", "LCI", "DGLY", "PCAR", "CZOO", "MIGI", "NAOV", "COMS", "GFAI", "INBS", "SNGX", "APRE", "FNGG", "GNUS", "VYNE", "CRBP", "ATNX", "CFRX", "ECOR", "NVDEF", "SHIP", "AMST", "GMBL", "RELI", "WINT", "FNRN", "MFH", "XBRAF", "RKDA", "HCDI", "IONM", "VXX", "SFT", "VEON", "AKAN", "NYMT", "ORTX", "ASLN", "KRBP", "IVOG", "IVOO", "IVOV", "VIOG", "VIOO", "VIOV", "GRAY", "MRBK", "BAOS", "GGB", "LKCO", "TESTING", "VIA", "IDAI", "PTIX", "RDHL", "CUEN", "FRGT", "GCBC", "ALLR", "CREX", "MTP", "MNST", "NOGN", "BPTS", "CETXP", "ENSC", "HLBZ", "CHNR", "BEST", "MBIO", "WTER", "AGRX", "BLBX", "VBIV", "WISH", "EJH", "ARVL", "MEIP", "MINM", "ASNS", "VERB", "BKTI", "FRSX", "OIG", "LGMK", "POAI", "SMFL", "CLXT", "JXJT", "SBET", "EZFL", "IMPP", "MEME", "PSTV", "VISL", "WEED", "MDRR", "MULN", "WGS", "GTE", "SMH", "CRESY", "BBIG", "HEPA", "AWH", "FRLN", "LPCN", "RETO", "VERO", "ALPP", "BNMV", "EAST", "GLMD", "IFBD", "RETO", "XBIO", "XELA", "XELAP", "CYCN", "GREE", "SDIG", "BIOC", "AULT", "NISN", "CHDN", "LGMK", "HLBZ", "LPCN", "BBIG", "XBIO", "JATT", "TGAA", "GRAY", "GREE", "SDIG", "SMFL", "SMFG", "VERO", "LCI", "TYDE", "DRMA", "BLIN", "HEPA", "SESN", "CR", "LITM", "SNGX", "GE", "MULN", "CGNX", "ML", "MDRR", "PR", "VAL", "EBF", "MTP", "CYCN", "XELA", "ENVX", "EQT", "GLMD", "DCFC", "POAI", "BNOX", "FRLN", "CINC", "NISN", "REFR", "CAPR", "SYRS", "ALPP", "RETO", "VISL", "GNLN", "JXJT", "SAFE", "EZFL", "IDRA", "CRESY", "IMPP", "ZEV", "EAST", "BIOC", "IFBD", "STAR", "AWH", "TNXP", "WORX", "VLON", "PSTV", "SFT", "AGRX", "MBIO", "APRE", "GAME", "VERB", "CFRX", "BLBX", "COMS", "RKDA", "WISH", "NXTP", "TR", "ARVL", "EJH", "MEIP", "ENSC", "NYMT", "PNTM", "ASNS", "AKAN", "RDFN", "GMBL", "VYNE", "MNST", "LCAA", "FRSX", "CRBP", "ATNX", "OIG", "REED", "OUST", "ALLR", "NAOV", "KRBP", "ICMB", "XOS", "GFAI", "GNUS", "BGXX", "FTFT", "AMST", "FCUV", "VBIV", "BIIB", "MINM", "CLXT", "DGLY", "MRKR");
        allCode.removeAll(invalidStockSet);
        BaseUtils.filterStock(allCode);
    }

    public void main() throws Exception {
        buildCodeSet();
        init();

        for (String code : allCode) {
            List<StockKLine> stockKLines = codeKLineMap.get(code);
            if (CollectionUtils.isEmpty(stockKLines)) {
                continue;
            }

            double successCount = 0, failCount = 0;
            boolean skip = false;
            StockRatio stockRatio = new StockRatio();
            for (int i = 1; i < stockKLines.size() - 1; i++) {
                StockKLine kLine = stockKLines.get(i);
                String date = kLine.getDate();

                StockKLine prevKLine = stockKLines.get(i - 1);
                StockKLine nextKLine = stockKLines.get(i + 1);

                double open = kLine.getOpen();
                double close = kLine.getClose();
                double high = kLine.getHigh();
                double low = kLine.getLow();
                BigDecimal volume = kLine.getVolume();

                double prevOpen = prevKLine.getOpen();
                double prevClose = prevKLine.getClose();
                double prevLow = prevKLine.getLow();
                BigDecimal prevVolume = prevKLine.getVolume();

                double nextOpen = nextKLine.getOpen();
                double nextClose = nextKLine.getClose();
                //                double dn = boll.getDn();

                // 前天收盘小于7或成交量小于10w，则跳过
                if (prevClose < 7 || prevVolume.doubleValue() < 100000) {
                    //                    skip = true;
                    break;
                }

                // 前一天上涨，则跳过
                if (prevClose > prevOpen) {
                    continue;
                }

                double prevLowCloseDiff = (prevClose - prevOpen) / (prevLow - prevOpen) * 100;

                Bean bean = new Bean();
                bean.setDate(date);
                bean.setClose(close);
                bean.setPrevClose(prevClose);
                bean.setPrevLowCloseDiff(prevLowCloseDiff);
                stockRatio.addBean(bean);
            }

            map.put(code, stockRatio);
        }

        predict(map);
    }

    public void predict(Map<String/* code */, StockRatio/* successRatio */> map) {
        List<String> dateList = _2023codeTotalMap.keySet().stream().sorted(Comparator.comparingInt(BaseUtils::formatDateToInt)).collect(Collectors.toList());
        double exchange = 6.94;
        double sum = 10000 / exchange;
        double income = 0;
        int lossCount = 0, gainCount = 0;
        for (int i = 1; i < dateList.size(); i++) {
            String today = dateList.get(i);
            String yesterday = dateList.get(i - 1);

            Map<String, Double> curRatioMap = Maps.newHashMap();
            Map<String, StockKLine> todayKline = _2023codeTotalMap.get(today);
            Map<String, StockKLine> yesterdayKline = _2023codeTotalMap.get(yesterday);
            for (String code : yesterdayKline.keySet()) {
                StockKLine yesKLine = yesterdayKline.get(code);
                double prevClose = yesKLine.getClose();
                double prevOpen = yesKLine.getOpen();
                double prevLow = yesKLine.getLow();
                double vol = yesKLine.getVolume().doubleValue();
                if (prevClose < 7 || prevClose > prevOpen || vol < 100000) {
                    continue;
                }

                double prevLowCloseDiff = (prevClose - prevOpen) / (prevLow - prevOpen) * 100;
                int range = StockRatio.getRange(prevLowCloseDiff);
                if (range < 50) {
                    continue;
                }

                StockRatio stockRatio = map.get(code);
                if (stockRatio == null || !stockRatio.getRatioMap().containsKey(range)) {
                    continue;
                }

                RatioBean ratioBean = stockRatio.getRatioMap().get(range);
                double ratio = ratioBean.getRatio();
                curRatioMap.put(code, ratio);
            }

            List<String> stockList = curRatioMap.entrySet().stream().sorted((o1, o2) -> {
                if (o1.getValue() < o2.getValue()) {
                    return 1;
                }
                if (o1.getValue() > o2.getValue()) {
                    return -1;
                }
                return 0;
            }).map(c -> c.getKey()).collect(Collectors.toList());

            if (CollectionUtils.isEmpty(stockList)) {
                continue;
            }

            System.out.println(today + " " + stockList.subList(0, 5));
            String code = stockList.get(0);
            StockKLine kLine = todayKline.get(code);
            StockKLine prevKLine = yesterdayKline.get(code);
            double close = kLine.getClose();
            double low = kLine.getLow();
            double open = prevKLine.getClose();
            double volume = kLine.getVolume().doubleValue();

            double lossRatio = (open - low) / open;
            double v = 0.07;
            int count = (int) (sum / open);
            if (lossRatio > v) {
                double loss = -count * open * v;
                income = loss;
                System.out.println("date=" + today + ", stock=" + code + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", loss = " + (int) loss * exchange);
                //                                                        System.out.println(String.format("loss lossRatio=%d", (int)(lossRatio*100)));
                lossCount++;
            } else {
                double gain = count * (close - open);
                income = gain;
                System.out.println("date=" + today + ", stock=" + code + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", gain = " + (int) gain * exchange);

                if (gain >= 0) {
                    gainCount++;
                } else {
                    lossCount++;
                }
            }
            sum += income;
            System.out.println("date=" + today + ", income=" + income + ", sum=" + sum * exchange);
        }

        double successRatio = (double) gainCount / (gainCount + lossCount);
        System.out.println("gainCount=" + gainCount + ", lossCount=" + lossCount + ", successRatio=" + successRatio);

    }

    public static void main(String[] args) {
        List<Double> d = Lists.newArrayList(1.1, 1.3, 1.2);

        List<Double> a = d.stream().sorted((o1, o2) -> {
            if (o1 < o2) {
                return 1;
            } else if (o1 > o2) {
                return -1;
            }
            return 0;
        }).collect(Collectors.toList());
        System.out.println(a);
    }

    @Data
    static class Bean {

        String date;
        double prevLowCloseDiff;
        double close;
        double prevClose;
    }

    @Data
    static class RatioBean {
        List<Bean> beanList = Lists.newArrayList();
        double ratio;

        public void add(Bean bean) {
            beanList.add(bean);
            long trueCount = beanList.stream().filter(c -> c.getClose() > c.getPrevClose()).count();
            int count = beanList.size();
            ratio = (double) trueCount / count;
        }
    }

    @Data
    static class StockRatio {

        Map<Integer, RatioBean> ratioMap = Maps.newHashMap();

        public void addBean(Bean bean) {
            double prevLowCloseDiff = bean.getPrevLowCloseDiff();
            int range = getRange(prevLowCloseDiff);

            if (ratioMap.get(range) == null) {
                ratioMap.put(range, new RatioBean());
            }
            ratioMap.get(range).add(bean);
        }

        public static int getRange(double prevLowCloseDiff) {
            int range = (int) prevLowCloseDiff;
            if (range < 10) {
                range = 10;
            } else if (range < 10) {
                range = 10;
            } else if (range < 20) {
                range = 20;
            } else if (range < 30) {
                range = 30;
            } else if (range < 40) {
                range = 40;
            } else if (range < 50) {
                range = 50;
            } else if (range < 60) {
                range = 60;
            } else if (range < 70) {
                range = 70;
            } else if (range < 80) {
                range = 80;
            } else if (range < 90) {
                range = 90;
            } else if (range < 100) {
                range = 100;
            }
            return range;
        }
    }
}
