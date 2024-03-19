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
import java.util.stream.Collectors;

/**
 * 拉出跳空低开10个点以上的股票
 * 开盘后5分钟内实时价格低于开盘价5%的备选
 * 看收盘价是否低于5分钟时的价格，如果不低于则被过滤掉
 * 计算收益：开盘5分钟后卖出，收盘前买回，高于卖出价固定百分比进行止损
 */
@Component
public class Strategy17 {

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

        //        System.out.printf("code\tdate\tclose\tcloseRatio\thighLowRatio\tcloseDnRatio\tvolMutiple\topenGain\tcloseGain\tnextGain\topenGainRatio\tcloseGainRatio\tnextGainRatio\n");
        //        Map<String, Double> map = Maps.newTreeMap(Comparator.comparingInt(BaseUtils::formatDateToInt));
        Map<String/* code */, Double/* successRatio */> map = Maps.newHashMap();
        for (String code : allCode) {
            List<StockKLine> stockKLines = codeKLineMap.get(code);
            List<BOLL> bolls = codeBollMap.get(code);
            if (CollectionUtils.isEmpty(stockKLines) || CollectionUtils.isEmpty(bolls)) {
                continue;
            }

            double successCount = 0, failCount = 0;
            boolean skip = false;
            //            Map<String, BOLL> dateToBollMap = bolls.stream().collect(Collectors.toMap(BOLL::getDate, Function.identity()));
            for (int i = 1; i < stockKLines.size() - 1; i++) {
                StockKLine kLine = stockKLines.get(i);
                String date = kLine.getDate();
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
                //                double prevDn = prevBoll.getDn();

                double nextOpen = nextKLine.getOpen();
                double nextClose = nextKLine.getClose();
                //                double dn = boll.getDn();

                // 前天收盘小于7或成交量小于10w，则跳过
                if (prevClose < 7 || prevVolume.doubleValue() < 100000) {
                    break;
                }

                double diff = (prevClose / open - 1) * 100;
                if (diff > 10) {
                    System.out.println(date + "\t" + code + "\t" + open + "\t" + close);
                }
            }
        }
    }
}
