package luonq.strategy;

import bean.BOLL;
import bean.Bean;
import bean.RatioBean;
import bean.StockKLine;
import bean.StockRatio;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.io.File;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_DOWN;

/**
 * OverBollingDn 的周线版本，将所有股票的周线计算dn，要过滤掉当前周收盘价格小于7的股票
 * 失败：收益太少
 */
public class Strategy26 {

    public static final String TEST_STOCK = "";
    public static final Set<String> SKIP_SET = Sets.newHashSet("FRC", "SIVBQ");

    public static void main(String[] args) throws Exception {
        double exchange = 6.94;
        double init = 10000 / exchange;
        int beforeYear = 2022, afterYear = 1900;
        int curYear = beforeYear + 1;
        double capital = init;

        // 构建每日交易的前一日的成交分时，小于390行的股票过滤不作为候选交易
        Map<String, Map<String, Integer>> dateToStockMinLineMap = getStockMinLineMap(beforeYear);

        Map<String, StockRatio> originRatioMap = computeHistoricalOverBollingerRatio(beforeYear, afterYear);
        Set<String> invalidStockSet = Sets.newHashSet("FRC", "SIVB", "BIOR", "HALL", "OBLG", "ALBT", "IPDN", "OPGN", "TENX", "AYTU", "DAVE", "NXTP", "ATHE", "CANF", "GHSI", "EEMX", "EFAX", "HYMB", "NYC", "SPYX", "PBLA", "JEF", "ACGN", "EAR", "FWBI", "IDRA", "JFU", "CNET", "APM", "JAGX", "OCSL", "OGEN", "SIEN", "SRKZG", "CETX", "UVIX", "EDBL", "PHIO", "SWVL", "MRKR", "REED", "WISA", "FTFT", "FVCB", "LMNL", "REVB", "DYNT", "BRSF", "LCI", "DGLY", "PCAR", "CZOO", "MIGI", "NAOV", "COMS", "GFAI", "INBS", "SNGX", "APRE", "FNGG", "GNUS", "VYNE", "CRBP", "ATNX", "CFRX", "ECOR", "NVDEF", "SHIP", "AMST", "GMBL", "RELI", "WINT", "FNRN", "MFH", "XBRAF", "RKDA", "HCDI", "IONM", "VXX", "SFT", "VEON", "AKAN", "NYMT", "ORTX", "ASLN", "KRBP", "IVOG", "IVOO", "IVOV", "VIOG", "VIOO", "VIOV", "GRAY", "MRBK", "BAOS", "GGB", "LKCO", "TESTING", "VIA", "IDAI", "PTIX", "RDHL", "CUEN", "FRGT", "GCBC", "ALLR", "CREX", "MTP", "MNST", "NOGN", "BPTS", "CETXP", "ENSC", "HLBZ", "CHNR", "BEST", "MBIO", "WTER", "AGRX", "BLBX", "VBIV", "WISH", "EJH", "ARVL", "MEIP", "MINM", "ASNS", "VERB", "BKTI", "FRSX", "OIG", "LGMK", "POAI", "SMFL", "CLXT", "JXJT", "SBET", "EZFL", "IMPP", "MEME", "PSTV", "VISL", "WEED", "MDRR", "MULN", "WGS", "GTE", "SMH", "CRESY", "BBIG", "HEPA", "AWH", "FRLN", "LPCN", "RETO", "VERO", "ALPP", "BNMV", "EAST", "GLMD", "IFBD", "RETO", "XBIO", "XELA", "XELAP", "CYCN", "GREE", "SDIG", "BIOC", "AULT", "NISN", "CHDN", "LGMK", "HLBZ", "LPCN", "BBIG", "XBIO", "JATT", "TGAA", "GRAY", "GREE", "SDIG", "SMFL", "SMFG", "VERO", "LCI", "TYDE", "DRMA", "BLIN", "HEPA", "SESN", "CR", "LITM", "SNGX", "GE", "MULN", "CGNX", "ML", "MDRR", "PR", "VAL", "EBF", "MTP", "CYCN", "XELA", "ENVX", "EQT", "GLMD", "DCFC", "POAI", "BNOX", "FRLN", "CINC", "NISN", "REFR", "CAPR", "SYRS", "ALPP", "RETO", "VISL", "GNLN", "JXJT", "SAFE", "EZFL", "IDRA", "CRESY", "IMPP", "ZEV", "EAST", "BIOC", "IFBD", "STAR", "AWH", "TNXP", "WORX", "VLON", "PSTV", "SFT", "AGRX", "MBIO", "APRE", "GAME", "VERB", "CFRX", "BLBX", "COMS", "RKDA", "WISH", "NXTP", "TR", "ARVL", "EJH", "MEIP", "ENSC", "NYMT", "PNTM", "ASNS", "AKAN", "RDFN", "GMBL", "VYNE", "MNST", "LCAA", "FRSX", "CRBP", "ATNX", "OIG", "REED", "OUST", "ALLR", "NAOV", "KRBP", "ICMB", "XOS", "GFAI", "GNUS", "BGXX", "FTFT", "AMST", "FCUV", "VBIV", "BIIB", "MINM", "CLXT", "DGLY", "MRKR");
        invalidStockSet.forEach(s -> originRatioMap.remove(s));
        Set<String> stockSet = originRatioMap.keySet();
        BaseUtils.filterStock(stockSet);

        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "week/");

        // 每支股票的周k线
        Map<String, Map<String, StockKLine>> dateToStockLineMap = Maps.newHashMap();
        for (String stock : stockSet) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = buildWeekKLine(filePath);

            for (StockKLine kLine : kLines) {
                String date = kLine.getDate();
                if (!dateToStockLineMap.containsKey(date)) {
                    dateToStockLineMap.put(date, Maps.newHashMap());
                }
                dateToStockLineMap.get(date).put(stock, kLine);
            }
        }

        // 构建各股票周bolling线
        Map<String, Map<String, BOLL>> dateToStockBollMap = Maps.newHashMap();
        for (String stock : stockSet) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "weekboll/" + stock, curYear, curYear - 2);

            for (BOLL boll : bolls) {
                String date = boll.getDate();
                if (!dateToStockBollMap.containsKey(date)) {
                    dateToStockBollMap.put(date, Maps.newHashMap());
                }
                dateToStockBollMap.get(date).put(stock, boll);
            }
        }

        // 准备当天和20天前的日期映射，用于实时计算布林值
        List<StockKLine> kLines = buildWeekKLine(Constants.HIS_BASE_PATH + "week/AAPL");
        List<String> dateList = Lists.newArrayList();
        for (int i = 0; i < kLines.size(); i++) {
            String date = kLines.get(i).getDate();
            String year = date.substring(date.lastIndexOf("/") + 1);
            if (year.equals(String.valueOf(beforeYear + 1))) {
                dateList.add(date);
            }
        }
        Collections.reverse(dateList);
        //        dateList = dateList.subList(0, 75);

        // 计算出open低于dn（收盘后的dn）比例最高的前十股票，然后再遍历计算收益
        Map<String, List<String>> dateToStocksMap = Maps.newHashMap();
        Map<String, Map<String, Double>> dateToStockRatioMap = Maps.newHashMap();
        Map<String, String> openbollFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "weekopenboll/");
        Map<String, Map<String, BOLL>> dataToOpenBollMap = Maps.newHashMap();
        for (String stock : openbollFileMap.keySet()) {
            String filePath = openbollFileMap.get(stock);
            List<BOLL> openBolls = BaseUtils.readBollFile(filePath, curYear, curYear - 1);

            for (BOLL openBoll : openBolls) {
                String date = openBoll.getDate();
                if (!dataToOpenBollMap.containsKey(date)) {
                    dataToOpenBollMap.put(date, Maps.newHashMap());
                }
                dataToOpenBollMap.get(date).put(stock, openBoll);
            }
        }
        for (int j = 0; j < dateList.size(); j++) {
            Map<String, Double> stockToRatioMap = Maps.newHashMap();

            String date = dateList.get(j);
            Map<String, StockKLine> stockKLineMap = dateToStockLineMap.get(date);
            Map<String, BOLL> openBollMap = dataToOpenBollMap.get(date);

            for (String stock : stockSet) {
                StockKLine kLine = stockKLineMap.get(stock);
                BOLL openBoll = openBollMap.get(stock);
                if (kLine == null || openBoll == null) {
                    continue;
                }
                if (date.equals("10/27/2023") && (stock.equals("ALGN") || stock.equals("MARA"))) {
                    //                                        System.out.println();
                }
                double open = kLine.getOpen();
                double dn = openBoll.getDn();
                if (dn < 0 || open > dn) {
                    continue;
                }

                double ratio = (dn - open) / dn * 100;
                stockToRatioMap.put(stock, ratio);
            }

            List<String> stocks = stockToRatioMap.entrySet().stream().sorted((o1, o2) -> {
                if (o1.getValue() < o2.getValue()) {
                    return 1;
                }
                return -1;
            }).map(o -> o.getKey()).collect(Collectors.toList());
            dateToStocksMap.put(date, stocks);
            dateToStockRatioMap.put(date, stockToRatioMap);
        }

        List<Double> hitRatio = Lists.newArrayList(0.5d, 0.6d, 0.7d, 0.8d, 0.9d);
        List<Double> lossRatioRange = Lists.newArrayList(0.07d, 0.08d, 0.09d, 0.1d, 0.2d, 0.3d);
        List<Integer> openRange = Lists.newArrayList(6, 7);
        boolean show = false;
        for (Integer openR : openRange) {
            for (Double lossRange : lossRatioRange) {
                for (int i = 0; i < hitRatio.size(); i++) {
                    double hit = hitRatio.get(i);

                    if (hit != 0.5d || lossRange != 0.07d || openR != 7) {
                        show = true;
                        continue;
                    }
                    Map<String, StockRatio> ratioMap = SerializationUtils.clone((HashMap<String, StockRatio>) originRatioMap);

                    int gainCount = 0, lossCount = 0;
                    for (int j = 1; j < dateList.size(); j++) {
                        String date = dateList.get(j);
                        if (date.equals("01/30/2024")) {
                            //                            System.out.println();
                        }
                        Map<String, BOLL> lastStockBollMap = Maps.newHashMap();
                        Map<String, StockKLine> lastStockKLineMap = Maps.newHashMap();
                        Map<String, Integer> lastMinLineMap = Maps.newHashMap();
                        String lastDate = dateList.get(j - 1);
                        lastStockBollMap = dateToStockBollMap.get(lastDate);
                        lastStockKLineMap = dateToStockLineMap.get(lastDate);
                        lastMinLineMap = dateToStockMinLineMap.get(lastDate);
                        Map<String, StockKLine> stockKLineMap = dateToStockLineMap.get(date);
                        Map<String, BOLL> stockBollMap = dateToStockBollMap.get(date);
                        List<String> stocks = dateToStocksMap.get(date);
                        Map<String, Double> stockToRatioMap = dateToStockRatioMap.get(date);

                        boolean hasCompute = false;
                        double income = 0;
                        double sum = capital;
                        int size = 0;
                        for (String stock : stocks) {
                            StockKLine kLine = stockKLineMap.get(stock);
                            StockKLine lastKLine = lastStockKLineMap.get(stock);
                            BOLL boll = stockBollMap.get(stock);
                            BOLL lastBoll = lastStockBollMap.get(lastDate);

                            if (lastKLine != null && (lastKLine.getVolume().doubleValue() < 10000000 || lastKLine.getClose() > lastKLine.getOpen())) {
                                                                continue;
                            }

                            if (lastMinLineMap != null && lastMinLineMap.containsKey(stock) && lastMinLineMap.get(stock) < 350) {
                                //                                continue;
                            }

                            double open = kLine.getOpen();
                            double close = kLine.getClose();
                            double low = kLine.getLow();
                            if (boll == null) {
                                System.out.println(date + " " + stock + " boll is null");
                                continue;
                            }
                            double currMb = boll.getMb();
                            if (lastBoll != null) {
                                double lastDn = lastBoll.getDn();
                                if (open > lastDn) {
                                    continue;
                                }
                            }

                            if (open < openR) {
                                continue;
                            }

                            if (open > currMb) {
                                continue;
                            }

                            // 根据开盘价算openDnDiffRatio
                            double openDnDiffPnt = stockToRatioMap.get(stock);
                            int openDnDiffInt = (int) openDnDiffPnt;
                            if (openDnDiffInt > 6) {
                                openDnDiffInt = 6;
                            }

                            BigDecimal volume = kLine.getVolume();
                            int avgVolume = (int) volume.doubleValue() / 360;

                            StockRatio stockRatio = ratioMap.get(stock);
                            Map<Integer, RatioBean> ratioDetail = stockRatio.getRatioMap();
                            if (MapUtils.isEmpty(ratioDetail)) {
                                //                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            RatioBean ratioBean = ratioDetail.get(openDnDiffInt);
                            if (ratioBean == null || ratioBean.getRatio() < hit) {
                                //                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            if (hasCompute) {
                                //                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                             int count = (int) (sum / open);
                            //                            if (stockRealOpenVolMap == null) {
                            //                                continue;
                            //                            }
                            //                            SimpleTrade realOpenVol = stockRealOpenVolMap.get(stock);
                            //                            if (realOpenVol == null || realOpenVol.getVolume() == 0) {
                            //                                continue;
                            //                            }
                            //                            avgVolume = (int) realOpenVol.getVolume() / 2;
                            //                            if (count == 0) {
                            //                                break;
                            //                            }
                            double lossRatio = (open - low) / open;
                            double v = lossRange;
                            if (avgVolume < count) {
                                count = avgVolume;
                            }
                            sum -= count * open;
                            if (lossRatio > v) {
                                double loss = -count * open * v;
                                income += loss;
                                if (show) {
                                    System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", loss = " + (int) loss * exchange);
                                }
                                lossCount++;
                            } else {
                                double gain = count * (close - open);
                                income += gain;
                                if (show) {
                                    System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", gain = " + (int) gain * exchange);
                                }

                                if (gain >= 0) {
                                    gainCount++;
                                } else {
                                    lossCount++;
                                }
                            }
                            //                            stockRatio.addBean(buildBean(kLine, boll));
                            size++;
                            break;
                        }
                        capital += income;
                        if (show) {
                            System.out.println("date=" + date + ", income=" + income + ", sum=" + capital * exchange);
                        }
                        //                        System.out.println(date+" "+size);
                    }
                    double successRatio = (double) gainCount / (gainCount + lossCount);
                    System.out.println("openRange=" + openR + ", hit=" + hit + ", loss=" + lossRange + ", sum=" + (int) (capital * exchange) + ", gainCount=" + gainCount + ", lossCount=" + lossCount + ", successRatio=" + successRatio);
                    capital = init;
                }
                System.out.println();
            }
        }
    }

    private static List<StockKLine> buildWeekKLine(String filePath) throws Exception {
        List<String> lines = BaseUtils.readFile(filePath);

        List<StockKLine> kLines = Lists.newArrayList();
        for (String line : lines) {
            String[] split = line.split("\t");
            String date = BaseUtils.unformatDate(split[0]);
            double open = Double.valueOf(split[1]);
            double close = Double.valueOf(split[2]);
            double high = Double.valueOf(split[3]);
            double low = Double.valueOf(split[4]);
            BigDecimal volume = BigDecimal.valueOf(Long.valueOf(split[5]));
            StockKLine kLine = new StockKLine();
            kLine.setDate(date);
            kLine.setOpen(open);
            kLine.setClose(close);
            kLine.setHigh(high);
            kLine.setLow(low);
            kLine.setVolume(volume);
            kLines.add(kLine);
        }
        return kLines;
    }

    private static Map<String, Map<String, Integer>> getStockMinLineMap(int year) throws Exception {
        Map<String, Map<String, Integer>> dateToStockMinLineMap = Maps.newHashMap();
        String path = "/Users/Luonanqin/study/intellij_idea_workspaces/temp/" + year;
        File stockDir = new File(path);
        File[] stockFiles = stockDir.listFiles();

        for (File stockFile : stockFiles) {
            String stock = stockFile.getName();
            if (!stock.equals("TCBP")) {
                //                continue;
            }

            File[] files = stockFile.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String name = BaseUtils.unformatDate(file.getName());
                if (!dateToStockMinLineMap.containsKey(name)) {
                    dateToStockMinLineMap.put(name, Maps.newHashMap());
                }
                Map<String, Integer> stockMinLineMap = dateToStockMinLineMap.get(name);
                List<String> lines = BaseUtils.readFile(file);
                stockMinLineMap.put(stock, lines.size());
            }
        }
        return dateToStockMinLineMap;
    }

    public static Map<String, StockRatio> computeHistoricalOverBollingerRatio(int beforeYear, int afterYear) throws Exception {
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "week/");

        Map<String, StockRatio> stockRatioMap = Maps.newHashMap();
        for (String stock : dailyFileMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            if (SKIP_SET.contains(stock)) {
                continue;
            }

            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = buildWeekKLine(filePath);

            List<BOLL> bollWithOpen = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "weekopenboll/" + stock, beforeYear, afterYear);
            Map<String, BOLL> dateToOpenBollMap = bollWithOpen.stream().collect(Collectors.toMap(BOLL::getDate, b -> b, (b1, b2) -> b1));
            List<Bean> result = strategy2(kLines, dateToOpenBollMap);

            StockRatio stockRatio = new StockRatio();
            result.stream().forEach(r -> stockRatio.addBean(r));
            stockRatioMap.put(stock, stockRatio);
        }

        return stockRatioMap;
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

        bean.setCloseGreatOpen(close > open ? 1 : 0);
        return bean;
    }
}
