package luonq.strategy;

import bean.BOLL;
import bean.StockKLine;
import bean.Total;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 突然下跌超过布林线下轨收阳并放量，第二天上涨，可收盘前买第二天卖
 */
@Component
public class Strategy13 {

    @Autowired
    private ReadFromDB readFromDB;

    private List<Total> allYearDate;
    private Map<String, List<Total>> codeTotalMap;
    private Map<String, List<StockKLine>> codeKLineMap = Maps.newHashMap();
    private Map<String, List<BOLL>> codeBollMap = Maps.newHashMap();
    private Set<String> allCode;

    private void init() {
        allYearDate = readFromDB.getAllYearDate("2023");
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
        init();

        System.out.printf("code\tdate\tclose\tcloseRatio\thighLowRatio\tcloseDnRatio\tvolMutiple\topenGain\tcloseGain\tnextGain\topenGainRatio\tcloseGainRatio\tnextGainRatio\n");
        Map<String, Double> map = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::formatDateToInt));
        for (String code : allCode) {
            List<StockKLine> stockKLines = codeKLineMap.get(code);
            List<BOLL> bolls = codeBollMap.get(code);
            if (CollectionUtils.isEmpty(stockKLines) || CollectionUtils.isEmpty(bolls)) {
                continue;
            }

            Map<String, BOLL> dateToBollMap = bolls.stream().collect(Collectors.toMap(BOLL::getDate, Function.identity()));
            for (int i = 1; i < stockKLines.size() - 1; i++) {
                StockKLine kLine = stockKLines.get(i);
                String date = kLine.getDate();
                BOLL boll = dateToBollMap.get(date);
                if (boll == null) {
                    continue;
                }

                StockKLine prevKLine = stockKLines.get(i - 1);
                StockKLine nextKLine = stockKLines.get(i + 1);
                BOLL prevBoll = dateToBollMap.get(prevKLine.getDate());

                double open = kLine.getOpen();
                double close = kLine.getClose();
                double high = kLine.getHigh();
                double low = kLine.getLow();
                BigDecimal volume = kLine.getVolume();
                double prevClose = prevKLine.getClose();
                double prevLow = prevKLine.getLow();
                BigDecimal prevVolume = prevKLine.getVolume();
                double nextOpen = nextKLine.getOpen();
                double nextClose = nextKLine.getClose();
                double dn = boll.getDn();
                double prevDn = prevBoll.getDn();

                // 当天开盘或收盘大于布林线下轨，则跳过
                if (open > dn || close > dn || high > dn) {
                    continue;
                }

                // 当前收盘小于开盘，则跳过
                if (close < open) {
//                    continue;
                }

                // 当前收盘大于于开盘，则跳过
                if (close > open) {
                    continue;
                }

                // 前天最低大于布林线下轨，则跳过
                if (prevLow < prevDn) {
                    continue;
                }

                // 前天收盘小于7或成交量小于10w，则跳过
                if (prevClose < 7 || prevVolume.doubleValue() < 100000) {
                    continue;
                }

                // 当天收盘成交量小于前天2倍成交量，则跳过
                if (volume.doubleValue() < prevVolume.doubleValue()) {
                    continue;
                }

                double volMutiple = volume.divide(prevVolume, BigDecimal.ROUND_CEILING).doubleValue();

                // 当天收盘下跌百分比
                double closeRatio = (prevClose - close) / prevClose * 100;
                // 下影线比例
                double highLowRatio = (Math.min(open, close) - low) / (high - low) * 100;

                // 第二天开盘是否大于当天收盘
                boolean openGain = nextOpen > close;
                // 第二天收盘是否大于当天收盘
                boolean closeGain = nextClose > close;
                // 第二天收盘是否大于第二天开盘
                boolean nextGain = nextClose > nextOpen;

                // 第二天开盘对于当天收盘的涨跌幅
                double openGainRatio = (nextOpen - close) / close * 100;
                // 第二天收盘对于当天收盘的涨跌幅
                double closeGainRatio = (nextClose - close) / close * 100;
                // 第二天收盘对于第二天开盘的涨跌幅
                double nextGainRatio = (nextClose - nextOpen) / nextClose * 100;

                // 当天收盘对布林线下轨的偏移程度
                double closeDnRatio = (dn - close) / dn * 100;

                System.out.printf("%s\t%s\t%f\t%.2f\t%.2f\t%.2f\t%.2f\t%b\t%b\t%b\t%.2f\t%.2f\t%.2f\n", code, date, close, closeRatio, highLowRatio, closeDnRatio, volMutiple, openGain, closeGain, nextGain, openGainRatio, closeGainRatio, nextGainRatio);

                if (highLowRatio < 10) {
                    map.put(date, closeGainRatio);
                }
            }
        }

        double init = 10000;
        for (String key : map.keySet()) {
            Double ratio = map.get(key);
            if (ratio > 7) {
                ratio = 7d;
            }
            if (ratio > 0) {
                init = init - init * ratio / 100;
            } else {
                init = init * (1 + (-ratio) / 100);
            }
        }
        System.out.println("result=" + init);
    }
}
