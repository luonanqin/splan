package luonq.strategy;

import bean.BOLL;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ROUND_HALF_UP;

/**
 * 候选：
 * 1.连续三天上涨（收盘价）
 * 2.第一天的前一天下跌（收盘价）
 *
 * 过滤：
 * 1.这三天成交量连续递减
 * 2.这三天成交量先减后增 且 第三天比第一天少
 * 3.这三天成交量先增后减
 *
 * 买入：第三天收盘买入
 * 计算：第四天收盘的涨跌情况，第四天收盘大于第三天收盘则为盈利，否则为亏损
 * <p>
 * 注：参与2023年计算的股票，一定要在开盘后5秒内有真实交易，否则会被过滤
 * <p>
 */
public class Strategy12 {

    public static final String TEST_STOCK = "";
    public static final Set<String> SKIP_SET = Sets.newHashSet("FRC", "SIVBQ");

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
        List<Double> gainPntList = Lists.newArrayList();
        double ratio;
        double avgGainRatio;

        public void add(Bean bean) {
            beanList.add(bean);
            long trueCount = beanList.stream().filter(c -> c.getCloseLessOpen() == 1).count();
            int count = beanList.size();
            ratio = (double) trueCount / count;

            avgGainRatio = beanList.stream().filter(c -> c.getCloseLessOpen() == 1).map(b -> (b.close - b.open) / b.open).collect(Collectors.averagingDouble(c -> c));
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
            if (openDnDiffRange > 6 && openDnDiffRange < 10) {
                if (!ratioMap.containsKey(6)) {
                    ratioMap.put(6, new RatioBean());
                }
                ratioMap.get(6).add(bean);
            } else if (openDnDiffRange > 10) {
                if (!ratioMap.containsKey(10)) {
                    ratioMap.put(10, new RatioBean());
                }
                ratioMap.get(10).add(bean);
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

    @Data
    public static class RealOpenVol {
        private String date;
        private double volumn;
        private double avgPrice;
    }

    @Data
    public static class ContinueRise {
        private List<StockKLine> riseList;

        public String getDate() {
            return riseList.get(0).getDate();
        }

        public boolean getResult(int n) {
            if (n + 1 > riseList.size()) {
                return false;
            }

            StockKLine last = riseList.get(n);
            StockKLine prev = riseList.get(n - 1);
            return last.getClose() > prev.getClose();
        }

        public String toString() {
            String closeDetail = riseList.stream().map(k -> String.valueOf(k.getClose())).collect(Collectors.joining("\t"));
            String volDetail = riseList.stream().map(k -> String.valueOf(k.getVolume())).collect(Collectors.joining("\t"));
            return getDate() + ", close=\t" + closeDetail + "\tvolume=" + volDetail + ",\t" + getResult(riseList.size() - 1);
        }
    }

    public static void main(String[] args) throws Exception {
        double test = 10000 * Math.pow(1.009, 200);
        System.out.println(test);
        double exchange = 6.94;
        double init = 10000 / exchange;
        int beforeYear = 2022, afterYear = 2000, afterYear2 = 2022;
        double capital = init;
        Set<String> invalidStockSet = Sets.newHashSet("FRC", "SIVB", "BIOR", "HALL", "OBLG", "ALBT", "IPDN", "OPGN", "TENX", "AYTU", "DAVE", "NXTP", "ATHE", "CANF", "GHSI", "EEMX", "EFAX", "HYMB", "NYC", "SPYX", "PBLA", "JEF", "ACGN", "EAR", "FWBI", "IDRA", "JFU", "CNET", "APM", "JAGX", "OCSL", "OGEN", "SIEN", "SRKZG", "CETX", "UVIX", "EDBL", "PHIO", "SWVL", "MRKR", "REED", "WISA", "FTFT", "FVCB", "LMNL", "REVB", "DYNT", "BRSF", "LCI", "DGLY", "PCAR", "CZOO", "MIGI", "NAOV", "COMS", "GFAI", "INBS", "SNGX", "APRE", "FNGG", "GNUS", "VYNE", "CRBP", "ATNX", "CFRX", "ECOR", "NVDEF", "SHIP", "AMST", "GMBL", "RELI", "WINT", "FNRN", "MFH", "XBRAF", "RKDA", "HCDI", "IONM", "VXX", "SFT", "VEON", "AKAN", "NYMT", "ORTX", "ASLN", "KRBP", "IVOG", "IVOO", "IVOV", "VIOG", "VIOO", "VIOV", "GRAY", "MRBK", "BAOS", "GGB", "LKCO", "TESTING", "VIA", "IDAI", "PTIX", "RDHL", "CUEN", "FRGT", "GCBC", "ALLR", "CREX", "MTP", "MNST", "NOGN", "BPTS", "CETXP", "ENSC", "HLBZ", "CHNR", "BEST", "MBIO", "WTER", "AGRX", "BLBX", "VBIV", "WISH", "EJH", "ARVL", "MEIP", "MINM", "ASNS", "VERB", "BKTI", "FRSX", "OIG", "LGMK", "POAI", "SMFL", "CLXT", "JXJT", "SBET", "EZFL", "IMPP", "MEME", "PSTV", "VISL", "WEED", "MDRR", "MULN", "WGS", "GTE", "SMH", "CRESY", "BBIG", "HEPA", "AWH", "FRLN", "LPCN", "RETO", "VERO", "ALPP", "BNMV", "EAST", "GLMD", "IFBD", "RETO", "XBIO", "XELA", "XELAP", "CYCN", "GREE", "SDIG", "BIOC", "AULT", "NISN", "CHDN", "LGMK", "HLBZ", "LPCN", "BBIG", "XBIO", "JATT", "TGAA", "GRAY", "GREE", "SDIG", "SMFL", "SMFG", "VERO", "LCI", "TYDE", "DRMA", "BLIN", "HEPA", "SESN", "CR", "LITM", "SNGX", "GE", "MULN", "CGNX", "ML", "MDRR", "PR", "VAL", "EBF", "MTP", "CYCN", "XELA", "ENVX", "EQT", "GLMD", "DCFC", "POAI", "BNOX", "FRLN", "CINC", "NISN", "REFR", "CAPR", "SYRS", "ALPP", "RETO", "VISL", "GNLN", "JXJT", "SAFE", "EZFL", "IDRA", "CRESY", "IMPP", "ZEV", "EAST", "BIOC", "IFBD", "STAR", "AWH", "TNXP", "WORX", "VLON", "PSTV", "SFT", "AGRX", "MBIO", "APRE", "GAME", "VERB", "CFRX", "BLBX", "COMS", "RKDA", "WISH", "NXTP", "TR", "ARVL", "EJH", "MEIP", "ENSC", "NYMT", "PNTM", "ASNS", "AKAN", "RDFN", "GMBL", "VYNE", "MNST", "LCAA", "FRSX", "CRBP", "ATNX", "OIG", "REED", "OUST", "ALLR", "NAOV", "KRBP", "ICMB", "XOS", "GFAI", "GNUS", "BGXX", "FTFT", "AMST", "FCUV", "VBIV", "BIIB", "MINM", "CLXT", "DGLY", "MRKR");
        Set<String> stockSet = Sets.newHashSet();
        BaseUtils.filterStock(stockSet);

        int riseTimes = 3;
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(mergePath);
        for (String stock : dailyFileMap.keySet()) {
            double amount = 10000;
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }

            String filePath = dailyFileMap.get(stock);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(filePath, beforeYear, afterYear);

            List<ContinueRise> resultList = Lists.newLinkedList();
            boolean reset = true;
            List<StockKLine> riseList = Lists.newArrayList();
            for (int i = stockKLines.size() - 1; i >= 3; ) {
                StockKLine lastLastKLine = stockKLines.get(i);
                StockKLine lastKLine = stockKLines.get(i - 1);
                StockKLine currKLine = stockKLines.get(i - 2);
                StockKLine nextKLine = stockKLines.get(i - 3);

                double lastLastClose = lastLastKLine.getClose();
                double lastClose = lastKLine.getClose();
                double currClose = currKLine.getClose();
                double open = currKLine.getOpen();

                if (open < 7) {
                    reset = true;
                    i--;
                    continue;
                }

                if (reset) {
                    riseList = Lists.newArrayList();
                }

                if (lastLastKLine.getDate().equals("11/25/2022")) {
                    //                    System.out.println();
                }
                if (reset && lastClose > lastLastClose) {
                    i--;
                    continue;
                }

                if (currClose > lastClose) {
                    riseList.add(currKLine);
                    if (riseList.size() == riseTimes) {
                        riseList.add(nextKLine);
                        ContinueRise continueRise = new ContinueRise();
                        continueRise.setRiseList(riseList);
                        resultList.add(continueRise);

                        //                        System.out.println(continueRise);
                        reset = true;
                        i = i - 2;
                        continue;
                    } else {
                        reset = false;
                    }
                } else {
                    reset = true;
                }
                i--;
            }

            for (ContinueRise continueRise : resultList) {
                if (filter1(continueRise)) {
                    //                    System.out.println(continueRise + " filter1");
                    continue;
                } else if (filter2(continueRise)) {
                    //                    System.out.println(continueRise + " filter2");
                    continue;
                } else if (filter3(continueRise)) {
                    //                    System.out.println(continueRise + " filter3");
                    continue;
                } else {
                    StockKLine kLine = continueRise.getRiseList().get(3);
                    StockKLine prevKLine = continueRise.getRiseList().get(2);
                    double close = kLine.getClose();
                    double prevClose = prevKLine.getClose();
                    amount *= (close / prevClose);
                    //                    System.out.println(continueRise);
                }
            }
            double ratio = BigDecimal.valueOf(amount / 10000).setScale(2, RoundingMode.HALF_UP).subtract(BigDecimal.valueOf(1)).multiply(BigDecimal.valueOf(100)).doubleValue();
            String ratioRes;
            if (amount > 10000) {
                ratioRes = "gain";
            } else {
                ratioRes = "loss";
            }
            System.out.println(stock + "\t" + ratio + "\t" + ratioRes);

            boolean showDetail = true;
            //            boolean showDetail = false;
            List<Integer> gainRatioRange = Lists.newArrayList(1, 2, 3, 5, 7, 10);
            //            List<Double> upLimitRange = Lists.newArrayList(1d, 2d, 3d, 5d, 7d, 10d);
            List<Double> upLimitRange = Lists.newArrayList(5d);
        }
    }

    // 成交量递减
    public static boolean filter1(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine1 = riseList.get(0);
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);

        double vol1 = kLine1.getVolume().doubleValue();
        double vol2 = kLine2.getVolume().doubleValue();
        double vol3 = kLine3.getVolume().doubleValue();

        return vol1 > vol2 && vol2 > vol3;
    }

    // 成交量先减后增 且 第三天比第一天少
    public static boolean filter2(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine1 = riseList.get(0);
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);

        double vol1 = kLine1.getVolume().doubleValue();
        double vol2 = kLine2.getVolume().doubleValue();
        double vol3 = kLine3.getVolume().doubleValue();

        return vol1 > vol2 && vol2 < vol3 && vol3 < vol1;
    }

    // 成交量先增后减
    public static boolean filter3(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine1 = riseList.get(0);
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);

        double vol1 = kLine1.getVolume().doubleValue();
        double vol2 = kLine2.getVolume().doubleValue();
        double vol3 = kLine3.getVolume().doubleValue();

        return vol1 < vol2 && vol2 > vol3;
    }

    public static Map<String, StockRatio> computeHistoricalOverBollingerRatio() throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(mergePath);

        Map<String, StockRatio> stockRatioMap = Maps.newHashMap();
        for (String stock : dailyFileMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            if (SKIP_SET.contains(stock)) {
                continue;
            }

            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, 2022, 2020);
            Map<String, StockKLine> dateToKLineMap = kLines.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k, (k1, k2) -> k1));

            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, 2022, 2020);
            Map<String, BOLL> dateToBollMap = bolls.stream().collect(Collectors.toMap(BOLL::getDate, b -> b, (b1, b2) -> b1));

            //            List<Bean> result = strategy1(dateToKLineMap, dateToBollMap);
            //                        List<Bean> result = strategy(kLines);

            List<BOLL> bollWithOpen = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "bollWithOpen/" + stock, 2022, 2020);
            Map<String, BOLL> dateToOpenBollMap = bollWithOpen.stream().collect(Collectors.toMap(BOLL::getDate, b -> b, (b1, b2) -> b1));
            List<Bean> result = strategy2(kLines, dateToOpenBollMap);

            StockRatio stockRatio = new StockRatio();
            result.stream().forEach(r -> stockRatio.addBean(r));
            stockRatioMap.put(stock, stockRatio);
        }

        return stockRatioMap;
    }

    private static List<Bean> strategy(List<StockKLine> stockKLines) {
        List<Bean> result = Lists.newArrayList();
        BigDecimal m20close = BigDecimal.ZERO;
        int ma20count = 0;
        double md = 0, mb = 0, dn = 0;
        for (int i = stockKLines.size() - 1; i >= 0; i--) {
            StockKLine kLine = stockKLines.get(i);

            BigDecimal open = BigDecimal.valueOf(kLine.getOpen());
            BigDecimal close = BigDecimal.valueOf(kLine.getClose());
            m20close = m20close.add(close);
            ma20count++;

            if (ma20count == 20) {
                m20close = m20close.subtract(close).add(open);
                double ma20 = m20close.divide(BigDecimal.valueOf(20), 2, ROUND_HALF_UP).doubleValue();
                mb = ma20;
                BigDecimal avgDiffSum = BigDecimal.ZERO;
                int j = i, times = 20;
                while (times > 0) {
                    double c;
                    if (j == i) {
                        c = stockKLines.get(j).getOpen();
                    } else {
                        c = stockKLines.get(j).getClose();
                    }
                    j++;
                    avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(c - ma20).pow(2));
                    times--;
                }

                md = Math.sqrt(avgDiffSum.doubleValue() / 20);
                BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
                dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();

                ma20count--;
                m20close = m20close.subtract(BigDecimal.valueOf(stockKLines.get(i + 20 - 1).getClose()));
                m20close = m20close.subtract(open).add(close);

                double low = kLine.getLow();
                if (low < dn && kLine.getOpen() < dn) {
                    result.add(buildBean(kLine, dn));
                }
            }
            if (md == 0) {
                continue;
            }
        }

        return result;
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
        //        Collections.sort(result, ((Comparator<Bean>) (o1, o2) -> BaseUtils.dateToInt(o1.getDate()) - BaseUtils.dateToInt(o2.getDate())).reversed());
        return result;
    }

    private static List<Bean> strategy2(List<StockKLine> stockKLines, Map<String, BOLL> bollWithOpen) {
        List<Bean> result = Lists.newArrayList();
        for (int i = 0; i < stockKLines.size(); i++) {
            StockKLine kLine = stockKLines.get(i);
            String date = kLine.getDate();
            BOLL boll = bollWithOpen.get(date);
            if (boll == null) {
                continue;
            }

            double dn = boll.getDn();
            double open = kLine.getOpen();
            double low = kLine.getLow();
            if (low < dn && open < dn) {
                result.add(buildBean(kLine, boll));
            }
        }
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

        double openDnDiffPnt = BigDecimal.valueOf((dn - open) / dn).setScale(4, ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setOpenDnDiffPnt(openDnDiffPnt);

        bean.setCloseLessOpen(close > open ? 1 : 0);
        return bean;
    }

    private static Bean buildBean(StockKLine kLine, double dn) {
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

        double openDnDiffPnt = BigDecimal.valueOf((dn - open) / dn).setScale(4, ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setOpenDnDiffPnt(openDnDiffPnt);

        bean.setCloseLessOpen(close > open ? 1 : 0);
        return bean;
    }
}
