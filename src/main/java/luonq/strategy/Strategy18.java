package luonq.strategy;

import bean.BOLL;
import bean.StockKLine;
import bean.Total;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import luonq.data.ReadFromDB;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
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
 * 拉出跳空低开10个点以上的股票
 * 开盘后5分钟的实时价格高于开盘价5%的备选
 * 计算收益：收盘卖出，有止损
 */
@Component
@Slf4j
public class Strategy18 {

    @Autowired
    private ReadFromDB readFromDB;

    private List<Total> allYearDate;
    private Map<String, List<Total>> codeTotalMap;
    private Map<String, List<StockKLine>> codeKLineMap = Maps.newHashMap();
    private Map<String, List<BOLL>> codeBollMap = Maps.newHashMap();
    private Set<String> allCode;
    private String calcYear = "2023";

    private void init() {
        allYearDate = readFromDB.getAllYearDate(calcYear);
        //                allYearDate = readFromDB.getCodeDate(calcYear, "APPS", "desc");
        codeTotalMap = allYearDate.stream().collect(Collectors.groupingBy(Total::getCode, Collectors.toList()));
        codeTotalMap.forEach((code, totals) -> {
            codeKLineMap.put(code, totals.stream().map(Total::toKLine).sorted(Comparator.comparingInt(o -> BaseUtils.formatDateToInt(o.getDate()))).collect(Collectors.toList()));
            codeBollMap.put(code, totals.stream().map(Total::toBoll).sorted(Comparator.comparingInt(o -> BaseUtils.formatDateToInt(o.getDate()))).collect(Collectors.toList()));
        });
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
        Map<String, Map<String, Double>> _5minDataMap = get5minData();
        //        Map<String, Map<String, List<Double>>> minAggreateMap = getMinAggreateData();
        init();

        double stopLossRatio = 0.07;
        double oneGainRatio = 0.08;
        double twoGainRatio = oneGainRatio * 2;
        double stopGainRatio = 0.05;
        double init = 10000;

        //        System.out.printf("code\tdate\tclose\tcloseRatio\thighLowRatio\tcloseDnRatio\tvolMutiple\topenGain\tcloseGain\tnextGain\topenGainRatio\tcloseGainRatio\tnextGainRatio\n");
        //        Map<String, Double> map = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::formatDateToInt));
        Map<String/* code */, Double/* successRatio */> map = Maps.newHashMap();
        for (String code : allCode) {
            List<StockKLine> stockKLines = codeKLineMap.get(code);
            List<BOLL> bolls = codeBollMap.get(code);
            Map<String, Double> datePriceMap = _5minDataMap.get(code);
            //            Map<String, List<Double>> dateMinPriceMap = minAggreateMap.get(code);
            if (CollectionUtils.isEmpty(stockKLines) || CollectionUtils.isEmpty(bolls) || MapUtils.isEmpty(datePriceMap)) {
                continue;
            }

            double successCount = 0, failCount = 0;
            //            Map<String, BOLL> dateToBollMap = bolls.stream().collect(Collectors.toMap(BOLL::getDate, Function.identity()));
            for (int i = 1; i < stockKLines.size() - 1; i++) {
                StockKLine kLine = stockKLines.get(i);
                String date = kLine.getDate();
                if (date.equals("2023-04-12")) {
                    //                    System.out.println();
                }
                Double _5minPrice = datePriceMap.get(date);
                //                List<Double> minPriceList = dateMinPriceMap.get(date);
                if (_5minPrice == null) {
                    continue;
                }
                //                BOLL boll = dateToBollMap.get(date);
                //                if (boll == null) {
                //                    continue;
                //                }

                StockKLine prevKLine = stockKLines.get(i - 1);
                StockKLine nextKLine = stockKLines.get(i + 1);
                //                BOLL prevBoll = dateToBollMap.get(prevKLine.getDate());

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
                    continue;
                }

                double diff = (prevClose / open - 1) * 100;
                if (diff > 10) {
                    boolean _5minLessClose = _5minPrice < close;
                    boolean _5minGreatOpen = _5minPrice > open;
                    boolean highGreat5min = high > _5minPrice;

                    if (_5minGreatOpen) {
                        double openRatio = 100 * (_5minPrice / open - 1);
                        double stopLossPrice = _5minPrice * (1 - stopLossRatio);
                        boolean getStopLoss = stopLossPrice <= low;
                        System.out.println(date +
                          "\t" + code +
                          "\t" + open +
                          "\t" + close +
                          "\t" + high +
                          "\t" + _5minPrice +
                          "\t" + low +
                          "\t" + _5minLessClose +
                          "\t" + _5minGreatOpen +
                          "\t" + highGreat5min +
                          "\t" + openRatio +
                          "\t" + getStopLoss
                        );
                    }
                }
            }
        }
    }

    public Map<String/* code */, Map<String/* date */, Double/* price */>> get5minData() {
        Map<String, Map<String, Double>> result = Maps.newHashMap();

        try {
            Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + calcYear + "/open5MinTrade");
            for (String code : fileMap.keySet()) {
                String filePath = fileMap.get(code);
                List<String> lines = BaseUtils.readFile(filePath);
                Map<String, Double> priceMap = Maps.newHashMap();
                for (String line : lines) {
                    String[] split = line.split(",");
                    if (split.length < 3) {
                        continue;
                    }

                    String date = BaseUtils.formatDate(split[0]);
                    double price = Double.valueOf(split[4]);
                    priceMap.put(date, price);
                }

                result.put(code, priceMap);
            }
        } catch (Exception e) {
            log.error("get5minData error", e);
        }

        return result;
    }

    public Map<String/* code */, Map<String/* date */, List<Double>/* price */>> getMinAggreateData() {
        Map<String, Map<String, List<Double>>> result = Maps.newHashMap();

        try {
            Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "minAggregate/");
            for (String code : fileMap.keySet()) {
                String fileDir = fileMap.get(code);
                Map<String, String> dateFileMap = BaseUtils.getFileMap(fileDir);
                Map<String, List<Double>> priceMap = Maps.newHashMap();

                for (String date : dateFileMap.keySet()) {
                    String filePath = dateFileMap.get(date);
                    List<String> lines = BaseUtils.readFile(filePath);
                    List<Double> priceList = Lists.newArrayList();
                    for (String line : lines) {
                        String[] split = line.split("\t");
                        double price = Double.valueOf(split[1]);
                        priceList.add(price);
                    }

                    priceMap.put(date, priceList);
                }
                result.put(code, priceMap);
            }
        } catch (Exception e) {
            log.error("getMinAggreateData error", e);
        }

        return result;
    }


    public static void main(String[] args) {
        List<String> list = Lists.newArrayList();
        list.add("-3");
        list.add("8");
        list.add("-3");
        list.add("-3");
        list.add("16");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("2.781706742");
        list.add("-3");
        list.add("-3");
        list.add("5.10687316");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("16");
        list.add("-3");
        list.add("-3");
        list.add("19.7586727");
        list.add("8");
        list.add("8");
        list.add("-3");
        list.add("20.39520113");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("8");
        list.add("-3");
        list.add("8");
        list.add("-3");
        list.add("-3");
        list.add("8");
        list.add("0.08567393");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("8");
        list.add("8");
        list.add("8");
        list.add("-3");
        list.add("-3");
        list.add("16");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("2.353556485");
        list.add("-3");
        list.add("-3");
        list.add("8");
        list.add("-3");
        list.add("-3");
        list.add("1.739506707");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("-3");
        list.add("3.117600631");
        list.add("8");
        list.add("8");
        list.add("-3");
        list.add("-3");
        list.add("16.80956175");
        list.add("8");
        list.add("8");
        list.add("8");
        list.add("-3");
        list.add("8");
        list.add("1.697892272");
        double a = 10000;
        for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            String[] split = s.split("\t");
            double a1 = a / split.length;
            double temp = 0;
            for (String s1 : split) {
                double aa1 = a1 * (1 + Double.valueOf(s1) / 100);
                temp += aa1;
            }
            a = temp;
        }
        System.out.println(a);
    }
}
