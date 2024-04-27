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
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ROUND_HALF_UP;

/**
 * 1.计算2021-2022年之间，当日开盘低于实时布林线下轨的比例（dn-open)/dn=>x，及当日收盘大于开盘的成功率=>y
 * 2.x的百分比从0~6（大于6的统一为6）作为key，y作为value建立历史策略数据map，并计算key对应的平均收益率z，比如0对应的收益率1%，1对应的收益率3%
 * 3.计算2023年的数据，计算上面的x，取对应的y，与给定的hit进行比较
 * 4.指定lossRange，作为止损线
 * 5.满足hit的x，将对应key的收益率z倒排，取第一个进行计算
 * <p>
 * 不满足：
 * 2.如果y比给定的hit小，则不满足条件
 * 4.如果开盘价低于给定openRange，则不满足条件
 * <p>
 * 不满足的数据，会继续加入历史策略数据map，共后续的计算使用
 * 满足的数据，根据止损线（若触发）进行收益计算
 * <p>
 * 注：参与2023年计算的股票，一定要在开盘后5秒内有真实交易，否则会被过滤
 * <p>
 */
public class Strategy11 {

    public static final String TEST_STOCK = "FUTU";
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
    public static class TestBean {
        private double highOpenDiffRatio;
        private double gainRatio;
        private double open;
        private double close;
        private double low;
        private String date;
    }

    public static void main(String[] args) throws Exception {
        double test = 10000 * Math.pow(1.01, 400);
        System.out.println(test);
        double exchange = 6.94;
        double init = 10000 / exchange;
        int beforeYear = 2022, afterYear = 2021, afterYear2 = 2022;
        double capital = init;
        Set<String> invalidStockSet = Sets.newHashSet("FRC", "SIVB", "BIOR", "HALL", "OBLG", "ALBT", "IPDN", "OPGN", "TENX", "AYTU", "DAVE", "NXTP", "ATHE", "CANF", "GHSI", "EEMX", "EFAX", "HYMB", "NYC", "SPYX", "PBLA", "JEF", "ACGN", "EAR", "FWBI", "IDRA", "JFU", "CNET", "APM", "JAGX", "OCSL", "OGEN", "SIEN", "SRKZG", "CETX", "UVIX", "EDBL", "PHIO", "SWVL", "MRKR", "REED", "WISA", "FTFT", "FVCB", "LMNL", "REVB", "DYNT", "BRSF", "LCI", "DGLY", "PCAR", "CZOO", "MIGI", "NAOV", "COMS", "GFAI", "INBS", "SNGX", "APRE", "FNGG", "GNUS", "VYNE", "CRBP", "ATNX", "CFRX", "ECOR", "NVDEF", "SHIP", "AMST", "GMBL", "RELI", "WINT", "FNRN", "MFH", "XBRAF", "RKDA", "HCDI", "IONM", "VXX", "SFT", "VEON", "AKAN", "NYMT", "ORTX", "ASLN", "KRBP", "IVOG", "IVOO", "IVOV", "VIOG", "VIOO", "VIOV", "GRAY", "MRBK", "BAOS", "GGB", "LKCO", "TESTING", "VIA", "IDAI", "PTIX", "RDHL", "CUEN", "FRGT", "GCBC", "ALLR", "CREX", "MTP", "MNST", "NOGN", "BPTS", "CETXP", "ENSC", "HLBZ", "CHNR", "BEST", "MBIO", "WTER", "AGRX", "BLBX", "VBIV", "WISH", "EJH", "ARVL", "MEIP", "MINM", "ASNS", "VERB", "BKTI", "FRSX", "OIG", "LGMK", "POAI", "SMFL", "CLXT", "JXJT", "SBET", "EZFL", "IMPP", "MEME", "PSTV", "VISL", "WEED", "MDRR", "MULN", "WGS", "GTE", "SMH", "CRESY", "BBIG", "HEPA", "AWH", "FRLN", "LPCN", "RETO", "VERO", "ALPP", "BNMV", "EAST", "GLMD", "IFBD", "RETO", "XBIO", "XELA", "XELAP", "CYCN", "GREE", "SDIG", "BIOC", "AULT", "NISN", "CHDN", "LGMK", "HLBZ", "LPCN", "BBIG", "XBIO", "JATT", "TGAA", "GRAY", "GREE", "SDIG", "SMFL", "SMFG", "VERO", "LCI", "TYDE", "DRMA", "BLIN", "HEPA", "SESN", "CR", "LITM", "SNGX", "GE", "MULN", "CGNX", "ML", "MDRR", "PR", "VAL", "EBF", "MTP", "CYCN", "XELA", "ENVX", "EQT", "GLMD", "DCFC", "POAI", "BNOX", "FRLN", "CINC", "NISN", "REFR", "CAPR", "SYRS", "ALPP", "RETO", "VISL", "GNLN", "JXJT", "SAFE", "EZFL", "IDRA", "CRESY", "IMPP", "ZEV", "EAST", "BIOC", "IFBD", "STAR", "AWH", "TNXP", "WORX", "VLON", "PSTV", "SFT", "AGRX", "MBIO", "APRE", "GAME", "VERB", "CFRX", "BLBX", "COMS", "RKDA", "WISH", "NXTP", "TR", "ARVL", "EJH", "MEIP", "ENSC", "NYMT", "PNTM", "ASNS", "AKAN", "RDFN", "GMBL", "VYNE", "MNST", "LCAA", "FRSX", "CRBP", "ATNX", "OIG", "REED", "OUST", "ALLR", "NAOV", "KRBP", "ICMB", "XOS", "GFAI", "GNUS", "BGXX", "FTFT", "AMST", "FCUV", "VBIV", "BIIB", "MINM", "CLXT", "DGLY", "MRKR");
        Set<String> stockSet = Sets.newHashSet();
        BaseUtils.filterStock(stockSet);

        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(mergePath);
        for (String stock : dailyFileMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }

            String filePath = dailyFileMap.get(stock);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(filePath, beforeYear, afterYear);

            List<TestBean> list = Lists.newLinkedList();
            for (StockKLine stockKLine : stockKLines) {
                double open = stockKLine.getOpen();
                double close = stockKLine.getClose();
                double high = stockKLine.getHigh();
                double low = stockKLine.getLow();
                String date = stockKLine.getDate();

                if (open < 7) {
                    continue;
                }

                double gain = close - open;
                double gainRatio = 0;
                if (gain > 0) {
                    gainRatio = BigDecimal.valueOf(gain / open).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    System.out.println(date + " true");
                } else {
                    System.out.println(date + " false");
                }

                double retrace = high - close;
                double highOpenDiff = high - open;

                //                double retraceRatio = BigDecimal.valueOf(retrace / highOpenDiff).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
                double highOpenDiffRatio = BigDecimal.valueOf(highOpenDiff / open).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();

                //                System.out.println(date + "\t" + highOpenDiffRatio + "\t" + gainRatio + "\t" + retraceRatio);
                TestBean testBean = new TestBean();
                testBean.setHighOpenDiffRatio(highOpenDiffRatio);
                testBean.setGainRatio(gainRatio);
                testBean.setLow(low);
                testBean.setOpen(open);
                testBean.setClose(close);
                testBean.setDate(date);
                list.add(testBean);
            }

            boolean showDetail = true;
            //            boolean showDetail = false;
            List<Integer> gainRatioRange = Lists.newArrayList(1, 2, 3, 5, 7, 10);
            //            List<Double> upLimitRange = Lists.newArrayList(1d, 2d, 3d, 5d, 7d, 10d);
            List<Double> upLimitRange = Lists.newArrayList(5d);
            for (Integer range : gainRatioRange) {
                if (range != 1) {
                    continue;
                }

                double lossLimit = 0.97;
                Random random = new Random(System.currentTimeMillis());
                for (double upLimit : upLimitRange) {
                    double sum = 0, gainCount = 0;
                    double upRatio = 1 + upLimit / 100;
                    double amount = 10000;
                    for (TestBean testBean : list) {
                        double gainRatio = testBean.getGainRatio();
                        double highOpenDiffRatio = testBean.getHighOpenDiffRatio();
                        double low = testBean.getLow();
                        double close = testBean.getClose();
                        double open = testBean.getOpen();
                        String date = testBean.getDate();

                        if (highOpenDiffRatio > range) {
                            sum++;
                        } else {
                            //                            continue;
                        }

                        // 假设最低价出现早于止盈点
                        // 1.如果止损价高于最低价，则止损卖出
                        //   否则
                        //      如果止盈价低于最高价，则止盈卖出
                        //      否则
                        //      收盘卖出（可能盈利可能亏损）

                        // 假设最低价出现晚于止盈点
                        // 2.如果止盈价低于最高价，则止盈卖出
                        //   否则
                        //      如果止损价高于最低价，则止损卖出
                        //      否则
                        //      收盘卖出（可能盈利可能亏损）

                        if (open * lossLimit > low) {
                            amount *= lossLimit;
                            if (showDetail) {
                                System.out.println("date=" + date + " gainRatio=" + gainRatio + " highOpenDiffRatio=" + highOpenDiffRatio + " lossLimit");
                            }
                        } else {
                            double count = amount / open;
                            double sellPrice = open * upRatio;
                            if (highOpenDiffRatio < upLimit) {
                                sellPrice = close;
                            }
                            sellPrice = close;
                            amount = count * sellPrice;
                            if (open < sellPrice) {
                                gainCount++;
                                if (showDetail) {
                                    System.out.println("date=" + date + " gainRatio=" + gainRatio + " highOpenDiffRatio=" + highOpenDiffRatio + " gain=" + (close - open) / open * 100);
                                }
                            } else {
                                System.out.println("date=" + date + " gainRatio=" + gainRatio + " highOpenDiffRatio=" + highOpenDiffRatio + " loss=" + (open - close) / open * 100);
                            }
                        }

                        //                        int rand = random.nextInt(100);
                        //                        boolean case1 = rand > 50;
                        //                        double buyPrice = open * upRatio;
                        //                        if (case1) {
                        //                            if (buyPrice * lossLimit > low) {
                        //                                amount *= lossLimit;
                        //                            } else {
                        //                                if (gainRatio > range) {
                        //                                    gainCount++;
                        //                                    amount *= upRatio;
                        //                                } else {
                        //                                    double count = amount / buyPrice;
                        //                                    amount = count * close;
                        //                                    if (buyPrice < close) {
                        //                                        gainCount++;
                        //                                    }
                        //                                }
                        //                            }
                        //                        } else {
                        //                            if (gainRatio > range) {
                        //                                gainCount++;
                        //                                amount *= upRatio;
                        //                            } else {
                        //                                if (buyPrice * lossLimit > low) {
                        //                                    amount *= lossLimit;
                        //                                } else {
                        //                                    double count = amount / buyPrice;
                        //                                    amount = count * close;
                        //                                    if (buyPrice < close) {
                        //                                        gainCount++;
                        //                                    }
                        //                                }
                        //                            }
                        //                        }

                        //                        if (gainRatio > range) {
                        //                            if (showDetail) {
                        //                                System.out.println("date=" + date + " gainRatio=" + gainRatio + " highOpenDiffRatio=" + highOpenDiffRatio + " gain=" + (gainRatio - range));
                        //                            }
                        //
                        //                            if (open * upRatio * lossLimit > low) {
                        //                                amount *= lossLimit;
                        //                            } else {
                        //                                gainCount++;
                        //                                amount *= 1 + Math.min(gainRatio - range, upLimit) / 100;
                        //                            }
                        //                            //                            amount *= 1 + (gainRatio - range) / 100;
                        //                        } else {
                        //                            if (highOpenDiffRatio > (range + upLimit)) {
                        //                                if (showDetail) {
                        //                                    System.out.println("date=" + date + " gainRatio=" + gainRatio + " highOpenDiffRatio=" + highOpenDiffRatio + " gain2");
                        //                                }
                        //
                        //                                gainCount++;
                        //                                amount *= upRatio;
                        //                            } else {
                        //                                if (showDetail) {
                        //                                    System.out.println("date=" + date + " gainRatio=" + gainRatio + " highOpenDiffRatio=" + highOpenDiffRatio + " loss");
                        //                                }
                        //
                        //                                amount *= lossLimit; // todo 要看最低点是否低于止损线，如果低于止损线则会提前止损
                        //                            }
                        //                        }
                    }

                    System.out.println("stock=" + stock + " upLimit=" + upLimit + " gainCount=" + gainCount + " sum=" + sum + " ratio=" + gainCount / sum + " amount=" + amount);
                }
            }
        }
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
