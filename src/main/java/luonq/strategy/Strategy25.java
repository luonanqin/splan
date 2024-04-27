package luonq.strategy;

import bean.EarningDate;
import bean.StockKLine;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import util.BaseUtils;
import util.Constants;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 1.读取每个股票的分钟线数据
 * 2.计算开盘一小时内，每一分钟都计算下成功率，取成功率最高的时间时买入且收盘卖出的成功率，即按照Strategy22计算
 * 3.同一时刻的股票可以作为这个时刻成功率最高的一组股票
 * 4.当实际交易时，有股票满足交易条件并且在这一组股票中出现时，取这些股票中成功率最高的进行交易
 * 3.验证2024年同样也是满足交易条件的股票，按照之前计算的成功率倒排，选第一个进行交易，计算收益
 * <p>
 * 策略改进备选方案：
 * 1.有个问题：满足4.的股票可能在不同时刻分别出现，比如在5min/10min/15min分别各出现3只/1只/5只，
 * 可能需要考虑每个时刻的股票数量， 股票数>=3时才能进行交易，当然这得结合实际测试的情况做决定，暂定3
 */
public class Strategy25 {

    @Data
    public static class StockMin {
        private String stock;
        private double buyPrice;
        private double closePrice;
        private double afterBuyHigh;
        private double afterBuyLow;

        public String toString() {
            return String.format("%s\tbuy=%.2f\tclose=%.2f\thigh=%.2f\tlow=%.2f", stock, buyPrice, closePrice, afterBuyHigh, afterBuyLow);
        }
    }

    @Data
    public static class StockRatio {
        private String stock;
        private Double ratio;
        private int min;
    }

    public static void main(String[] args) throws Exception {
        double i = 10000;
        for (int j = 0; j < 200; j++) {
            i = i * 1.003;
        }
        System.out.println(i);
        List<String> dateList = getDate();

        Map<Integer, List<StockRatio>> stockRatioMap1 = getStockRatioMap();
        Map<Integer, List<String>> stockRatioMap = buildRatioMap();
        Set<String> allStock = stockRatioMap.values().stream().flatMap(e -> e.stream()).collect(Collectors.toSet());
        BaseUtils.filterStock(allStock);

        List<String> allStocks = Lists.newArrayList(allStock);
        Map<String, Map<String, StockKLine>> stockLineMap = getStockLineMap(allStocks);
        Map<String, List<String>> filterStock = getFilterStock(allStocks, dateList, stockLineMap);

        List<Integer> minList = Lists.newArrayList(5,10,15,20,25,29);
        for (Integer min : minList) {
            System.out.println("min=" + min);

            double init = 10000;
            for (String date : dateList) {
                List<String> filters = filterStock.get(BaseUtils.unformatDate(date));
                boolean hasTrade = false;
                //            for (Integer min : stockRatioMap.keySet()) {
                List<String> stockRatios = stockRatioMap.get(min);
                for (String stock : stockRatios) {
                    if (!filters.contains(stock)) {
                        continue;
                    }
                    StockMin stockMin = calStockSuccess(date, stock, min);
                    if (stockMin == null) {
                        continue;
                    }

                    double buyPrice = stockMin.getBuyPrice();
                    double closePrice = stockMin.getClosePrice();
                    int count = (int) (init / buyPrice);
                    init = init + (closePrice - buyPrice) * count;

                    System.out.println(date + " " + min + " " + stockMin.toString() + "\t" + init);
                    hasTrade = true;
                    break;
                }

                //            if (hasTrade) {
                //                break;
                //            }
            }
            //        }
        }
    }

    private static Map<Integer, List<String>> buildRatioMap() {
        Map<Integer, List<String>> map = Maps.newTreeMap((o1, o2) -> o1.compareTo(o2));
        map.put(5, JSON.parseArray("[\"ORI\",\"AQN\",\"HTHT\",\"SPLK\",\"TDS\",\"LMND\",\"BRX\",\"GDS\",\"HSBC\",\"AEHR\",\"NNN\",\"CM\",\"CMA\",\"CLSK\",\"VNO\",\"COTY\",\"AGI\",\"MDB\",\"EXAS\",\"OMC\",\"NTNX\",\"JNPR\",\"CNK\",\"HIG\",\"BHC\",\"SMAR\",\"LYB\",\"NEOG\",\"FND\",\"PLAY\",\"VOYA\",\"KR\",\"PSTG\",\"CRDO\",\"ASX\",\"SHW\",\"DEN\",\"DHT\",\"TMO\",\"GTES\",\"TALO\",\"GFS\",\"NTRS\",\"CIEN\",\"HCP\",\"WEN\",\"FMC\",\"ALKS\",\"GOOGL\",\"A\",\"HRL\",\"FLNC\",\"FNF\",\"KO\",\"HASI\",\"BAM\",\"DBI\",\"OPRA\",\"ACI\",\"CUZ\",\"HAS\",\"SNY\",\"ZBH\",\"MMC\",\"CHS\",\"TRU\",\"CERE\",\"LKQ\",\"BANC\",\"HOLX\",\"SPCE\",\"XM\",\"IEP\",\"ELS\",\"KGC\"]", String.class));
        map.put(6, JSON.parseArray("[\"VKTX\",\"ANF\",\"GOOS\",\"CRSP\",\"GPN\",\"BMBL\",\"OTIS\",\"HUT\",\"BLDR\",\"TRP\",\"CHGG\",\"INMD\",\"LSCC\",\"AMT\",\"BXP\",\"TER\",\"XYL\",\"COHR\",\"UAA\",\"TOL\",\"NEXT\",\"TTWO\",\"ESTE\",\"ESTC\",\"CROX\",\"PTGX\",\"STAG\",\"SMTC\",\"GDDY\",\"CB\",\"ISEE\",\"GNRC\",\"PBR\",\"HAL\",\"C\",\"BKR\",\"ROL\",\"BAC\",\"BZ\",\"WPC\",\"GLPI\",\"IMVT\",\"WSC\",\"HIMS\",\"UNM\",\"GMBL\",\"PRGO\",\"CHX\",\"HA\",\"PPG\",\"TTOO\",\"HDB\"]", String.class));
        map.put(7, JSON.parseArray("[\"MAS\",\"REXR\",\"OZK\",\"DAR\",\"FYBR\",\"NUE\",\"UPWK\",\"CRBG\",\"NOW\",\"BCE\",\"BBIO\",\"ILMN\",\"RNG\",\"WOLF\",\"OGN\",\"LNT\",\"AVGO\",\"ELAN\",\"PRU\",\"NTES\",\"PATH\",\"X\",\"TD\",\"IRWD\",\"ALIT\",\"LIN\",\"WM\",\"CARR\",\"ABNB\",\"CVNA\",\"ACHR\",\"EDR\",\"AG\",\"BLNK\",\"AKAM\"]", String.class));
        map.put(8, JSON.parseArray("[\"FTV\",\"CNM\",\"EDIT\",\"KBH\",\"LEVI\",\"BALL\",\"AMGN\",\"ETRN\",\"OHI\",\"ARCC\",\"DEI\",\"OPCH\",\"CCJ\",\"AXP\",\"AR\",\"AEP\",\"TGNA\",\"KD\",\"HL\",\"AUPH\",\"CEG\",\"FISV\",\"NWL\",\"MUR\",\"DD\",\"ADSK\",\"RPRX\",\"APA\",\"S\",\"RCL\",\"HAYW\"]", String.class));
        map.put(9, JSON.parseArray("[\"EWBC\",\"SHC\",\"CSGP\",\"ED\",\"SWK\",\"INCY\",\"BLMN\",\"EXEL\",\"ZTS\",\"HBI\",\"YMM\",\"RDFN\",\"MAT\",\"COF\",\"AEE\",\"BWA\",\"WY\",\"FAST\",\"TMUS\",\"CTIC\",\"CPB\",\"HBAN\",\"CPRI\",\"LLY\",\"DXC\",\"CMCSA\",\"KC\",\"LW\",\"PNR\",\"HRB\",\"KMB\",\"HR\",\"IREN\",\"ZTO\",\"KRC\",\"OLN\",\"ALT\",\"RDN\",\"KSS\",\"GOOG\",\"NOVA\",\"ENVX\",\"PEP\",\"BA\",\"AYX\",\"HLN\",\"MNSO\",\"WRB\"]", String.class));
        map.put(10, JSON.parseArray("[\"PEB\",\"ACGL\",\"RMBS\",\"CUBE\",\"OWL\",\"ALL\",\"SBRA\",\"WW\",\"FRC\",\"YPF\",\"PAGS\",\"VOD\",\"ETR\",\"GTLB\",\"AMH\",\"ASAN\",\"LEN\",\"UGI\",\"JWN\",\"EMR\",\"T\",\"PCG\",\"SYY\",\"EW\",\"FREY\",\"DHR\",\"WBA\",\"SYF\",\"EXC\",\"IBM\",\"F\",\"PTLO\",\"SONO\"]", String.class));
        map.put(11, JSON.parseArray("[\"MAR\",\"UA\",\"RKLB\",\"LAC\",\"LYV\",\"KNX\",\"VLY\",\"SPOT\",\"PK\",\"ZS\",\"VTR\",\"BXMT\",\"MODG\",\"NEX\",\"GLW\",\"PD\",\"ARR\",\"VFC\",\"AES\",\"VRT\",\"GILD\",\"ABR\",\"GM\",\"TENB\",\"NI\",\"BJ\",\"RITM\",\"SWKS\",\"INVH\",\"EPD\",\"D\",\"EOG\",\"OUT\",\"FTCH\",\"LULU\",\"FOLD\"]", String.class));
        map.put(12, JSON.parseArray("[\"NXPI\",\"EXTR\",\"SBSW\",\"BILL\",\"ALK\",\"IFF\",\"CBRE\",\"HTZ\",\"AAOI\",\"HWM\",\"SLG\",\"ICE\",\"IVZ\",\"HST\",\"AEM\",\"PAA\",\"OKE\",\"FRSH\",\"SAVE\",\"INTC\",\"BX\",\"FLEX\",\"VZ\",\"BK\",\"DDOG\",\"AXLA\",\"KDP\"]", String.class));
        map.put(13, JSON.parseArray("[\"WELL\",\"FIGS\",\"HUN\",\"ADP\",\"COLB\",\"HOG\",\"WDAY\",\"ZI\",\"SM\",\"FL\",\"IBN\",\"BNS\",\"NFLX\",\"DELL\",\"AMZN\",\"EXPI\",\"JCI\",\"ISRG\",\"PARA\",\"VTRS\",\"SNOW\",\"DISH\",\"BMY\",\"WOOF\",\"NEP\"]", String.class));
        map.put(14, JSON.parseArray("[\"MGY\",\"DOCS\",\"TAP\",\"PR\",\"KOS\",\"FOXA\",\"OKTA\",\"PLD\",\"SRPT\",\"EQR\",\"BBWI\",\"MSFT\",\"AI\",\"TOST\",\"CRWD\",\"LCID\",\"CPRX\",\"PACB\",\"ST\",\"BCRX\",\"ENPH\",\"RIVN\",\"SLB\",\"GOLD\",\"OSH\"]", String.class));
        map.put(15, JSON.parseArray("[\"AXTA\",\"CF\",\"CMS\",\"FUTU\",\"FDX\",\"ATVI\",\"EDU\",\"CAH\",\"TFC\",\"PINS\",\"PACW\",\"ETSY\",\"MBLY\",\"ASO\",\"ON\",\"PANW\",\"CAT\",\"BEKE\",\"CFG\",\"BABA\",\"WSM\",\"MS\",\"PDD\",\"ENLC\"]", String.class));
        map.put(16, JSON.parseArray("[\"FSLR\",\"WISH\",\"CIVI\",\"ARMK\",\"COST\",\"BYND\",\"ETN\",\"USFD\",\"ZIM\",\"NIO\",\"EQT\",\"QS\",\"ALLY\",\"NWSA\",\"MP\",\"SPWR\",\"WBD\",\"CME\",\"SLM\",\"DBX\",\"UNH\",\"DOW\",\"UBS\",\"LESL\",\"NOG\"]", String.class));
        map.put(17, JSON.parseArray("[\"DG\",\"ALB\",\"SPR\",\"KKR\",\"FSLY\",\"EL\",\"IPG\",\"BIDU\",\"SKX\",\"AAP\",\"PLUG\",\"RTX\",\"UPS\",\"PPL\",\"KHC\",\"YUMC\",\"CVS\",\"BILI\",\"JNJ\",\"GIS\",\"DVN\",\"HPP\"]", String.class));
        map.put(18, JSON.parseArray("[\"DLR\",\"AM\",\"HLX\",\"AZEK\",\"IR\",\"IOVA\",\"SI\",\"PAYX\",\"TS\",\"BEN\",\"LOW\",\"ES\",\"NVAX\",\"LVS\",\"TPX\",\"KEY\",\"AZN\",\"TSN\",\"CELH\",\"TROW\",\"NTAP\",\"ITUB\",\"SPG\",\"TPR\",\"ARRY\",\"PM\",\"THC\"]", String.class));
        map.put(19, JSON.parseArray("[\"OSTK\",\"PGR\",\"KMX\",\"TEAM\",\"EA\",\"CG\",\"ROKU\",\"NTR\",\"UPST\",\"ZION\",\"WU\",\"EIX\",\"TME\",\"FE\",\"PG\",\"PTEN\",\"MRNA\",\"CTRA\",\"W\",\"NVDA\",\"APLE\",\"SCHW\",\"CL\",\"COP\",\"MAC\"]", String.class));
        map.put(20, JSON.parseArray("[\"WAL\",\"GEN\",\"MFC\",\"FRO\",\"MCD\",\"GS\",\"DINO\",\"EXPE\",\"TXN\",\"MLCO\",\"ABBV\"]", String.class));
        map.put(21, JSON.parseArray("[\"SEDG\",\"MKC\",\"HD\",\"JOBY\",\"TAL\",\"MGM\",\"CVE\",\"LI\",\"PSX\",\"IMGN\",\"AWK\",\"HPQ\",\"ROIV\",\"RIOT\",\"NEM\",\"VALE\",\"WMB\",\"HE\"]", String.class));
        map.put(22, JSON.parseArray("[\"ANET\",\"APTV\",\"CCEP\",\"CPE\",\"TEVA\",\"ROST\",\"VSCO\",\"DLTR\",\"DHI\",\"ORCL\",\"STX\",\"XP\",\"SWN\",\"MRVL\",\"SOFI\",\"XOM\",\"MQ\",\"CP\",\"BSX\",\"IONQ\",\"CHWY\",\"NEE\",\"OXY\",\"ONB\"]", String.class));
        map.put(23, JSON.parseArray("[\"CNP\",\"GH\",\"RKT\",\"PEAK\",\"APH\",\"CLF\",\"WPM\",\"BN\",\"IP\",\"SE\",\"HON\",\"ONON\",\"AFL\",\"STEM\",\"SRE\",\"UBER\",\"META\",\"AIG\",\"O\",\"JD\",\"AA\",\"XEL\",\"SNV\",\"FTNT\",\"MOS\"]", String.class));
        map.put(24, JSON.parseArray("[\"CDNS\",\"UMC\",\"ACN\",\"CNI\",\"AGL\",\"EVRG\",\"UDR\",\"GPK\",\"CLX\",\"PENN\",\"DASH\",\"BUD\",\"OVV\",\"TECK\",\"CTSH\",\"UNP\",\"SEE\",\"CNHI\",\"DOCU\",\"RF\",\"CSX\",\"EBAY\",\"RUN\",\"TSLA\",\"STLA\",\"VICI\",\"AMCR\",\"U\",\"SU\",\"GE\",\"IQ\",\"ET\",\"MDLZ\",\"DTE\",\"EURN\",\"MUFG\"]", String.class));
        map.put(25, JSON.parseArray("[\"QSR\",\"DXCM\",\"CNQ\",\"BE\",\"MA\",\"STWD\",\"WEC\",\"GEHC\",\"ALGM\",\"CCI\",\"MPW\",\"NRG\",\"CPRT\",\"STT\",\"K\",\"APLD\",\"NDAQ\",\"AAL\",\"AFRM\",\"BAX\",\"NOV\",\"JBLU\",\"NU\",\"FIS\",\"WDC\",\"SHLS\",\"GPS\",\"VST\",\"SHEL\",\"AMC\",\"RIG\",\"SBUX\",\"MTCH\",\"MNST\",\"CCL\"]", String.class));
        map.put(26, JSON.parseArray("[\"DFS\",\"CRK\",\"WB\",\"TRIP\",\"APLS\",\"DE\",\"URBN\",\"FHN\",\"AEO\",\"XRAY\",\"DT\",\"FITB\",\"UAL\",\"PNC\",\"FSR\",\"CHPT\",\"ADBE\",\"WFC\",\"DKNG\",\"CTLT\",\"NYCB\",\"SMCI\",\"TJX\",\"CPNG\",\"LYFT\",\"USB\",\"MU\",\"ZM\",\"TWLO\",\"RCM\",\"FCX\",\"SQ\",\"MRO\",\"DIS\",\"CAG\"]", String.class));
        map.put(27, JSON.parseArray("[\"VIPS\",\"RXRX\",\"LBRT\",\"GSK\",\"APO\",\"LNC\",\"EQH\",\"ADI\",\"MCHP\",\"TGT\",\"MET\",\"CFLT\",\"CNX\",\"LAZR\",\"AMD\",\"AMAT\",\"DAL\",\"TTD\",\"TSM\",\"KIM\",\"M\",\"HOOD\",\"COIN\",\"PXD\",\"MARA\",\"NKE\",\"PYPL\",\"VLO\",\"AAPL\",\"RBLX\",\"EQNR\",\"ENB\",\"CVX\",\"CRM\",\"QCOM\",\"BP\"]", String.class));
        map.put(28, JSON.parseArray("[\"GT\",\"CZR\",\"IOT\",\"CTVA\",\"NLY\",\"TRGP\",\"Z\",\"HPE\",\"APP\",\"TGTX\",\"PBF\",\"BBY\",\"PAAS\",\"PCAR\",\"PEG\",\"NET\",\"HES\",\"XPEV\",\"KMI\",\"RRC\",\"MO\",\"DUK\",\"WMT\",\"PHM\",\"SNAP\",\"MPLX\",\"V\",\"SO\",\"CSCO\",\"ABT\"]", String.class));
        map.put(29, JSON.parseArray("[\"PTON\",\"BTU\",\"WRK\",\"GME\",\"STNE\",\"ADM\",\"CLVT\",\"AGNC\",\"MPC\",\"FTI\",\"PFE\",\"CNC\",\"NCLH\",\"XPO\",\"LTHM\",\"JPM\",\"SHOP\",\"AVTR\",\"FANG\",\"MDT\",\"LUV\",\"MMM\",\"ASB\",\"WYNN\",\"MRK\",\"TDOC\",\"TCOM\",\"PLTR\",\"FNB\"]", String.class));

        return map;
    }

    public static List<String> getDate() throws Exception {
        String path = "/Users/Luonanqin/study/intellij_idea_workspaces/temp/2024/AAPL";
        File appleDir = new File(path);
        File[] files = appleDir.listFiles();
        return Arrays.stream(files).map(f -> f.getName()).sorted(Comparator.comparingInt(BaseUtils::formatDateToInt)).collect(Collectors.toList());
    }

    public static StockMin calStockSuccess(String date, String stock, int min) throws Exception {
        if (stock.equals("TETC")) {
            //            System.out.println();
        }
        String path = "/Users/Luonanqin/study/intellij_idea_workspaces/temp/2024/" + stock + "/" + date;
        File file = new File(path);
        List<String> lines = BaseUtils.readFile(file);
        if (CollectionUtils.isEmpty(lines)) {
            return null;
        }

        Double buy = Double.MIN_VALUE;
        boolean flag = true;
        // 2023-01-03 22:31:00	67.1001
        int index = 0;
        for (int i = 0; i < min; i++) {
            if (min > lines.size()) {
                flag = false;
                break;
            }
            String line = lines.get(i);
            String[] split = line.split("\t");
            Double price = Double.valueOf(split[1]);
            if (price.compareTo(buy) >= 0) {
                buy = price;
                index = i;
            }
        }

        if (!flag || index != min - 1) {
            return null;
        }
        double high = Double.MIN_VALUE;
        double low = Double.MAX_VALUE;
        for (int i = min; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] split = line.split("\t");
            Double price = Double.valueOf(split[1]);
            if (price > high) {
                high = price;
            }
            if (price < low) {
                low = price;
            }
        }

        String lastLine = lines.get(lines.size() - 1);
        Double close = null;
        try {
            close = Double.valueOf(lastLine.split("\t")[1]);
        } catch (Exception e) {
            System.out.println("error: " + stock + " " + date);
            return null;
        }
        StockMin stockMin = new StockMin();
        stockMin.setBuyPrice(buy);
        stockMin.setClosePrice(close);
        stockMin.setStock(stock);
        stockMin.setAfterBuyHigh(high);
        stockMin.setAfterBuyLow(low);

        return stockMin;
    }

    public static Map<Integer, List<StockRatio>> getStockRatioMap() throws Exception {
        String path = "/Users/Luonanqin/study/intellij_idea_workspaces/temp/2023";
        File stockDir = new File(path);
        File[] stockFiles = stockDir.listFiles();

        Map<Integer/*min*/, List<StockRatio>/*ratio*/> stockRatioMap = Maps.newTreeMap((o1, o2) -> o1.compareTo(o2));
        for (File stockFile : stockFiles) {
            String stock = stockFile.getName();
            if (!stock.equals("TCBP")) {
                //                continue;
            }

            File[] files = stockFile.listFiles();
            if (files == null) {
                System.out.println("empty file: " + stock);
                continue;
            }
            double successRatioTemp = Double.MIN_VALUE;
            int finalMin = 0;
            for (int min = 5; min < 30; min++) {
                int count = min;
                double success = 0, fail = 0;
                for (File file : files) {
                    List<String> lines = BaseUtils.readFile(file);
                    if (CollectionUtils.isEmpty(lines) || lines.size() < 390) {
                        continue;
                    }

                    double temp = Double.MIN_VALUE;
                    boolean flag = false;
                    // ex: 2023-01-03 22:31:00	67.1001
                    for (int i = 0; i < count; i++) {
                        if (count > lines.size()) {
                            flag = false;
                            break;
                        }
                        String line = lines.get(i);
                        String[] split = line.split("\t");
                        Double price = Double.valueOf(split[1]);
                        if (price > temp) {
                            temp = price;
                            if (i == count - 1) {
                                flag = true;
                            }
                        }
                    }

                    if (!flag) {
                        continue;
                    }

                    String lastLine = lines.get(lines.size() - 1);
                    Double close = null;
                    try {
                        close = Double.valueOf(lastLine.split("\t")[1]);
                    } catch (Exception e) {
                        System.out.println("error: " + stock + " " + file.getName());
                        continue;
                    }
                    if (close > temp) {
                        success++;
                    } else {
                        fail++;
                    }
                    //                System.out.println(date + "\t" + temp + "\t" + close + "\t" + (close > temp));
                }
                if (success == 0 && fail == 0) {
                    continue;
                }
                double sum = success + fail;
                if (sum < 3) {
                    continue;
                }
                double successRatio = success / sum;
                if (successRatio > successRatioTemp) {
                    successRatioTemp = successRatio;
                    finalMin = min;
                }
            }
            if (successRatioTemp == Double.MIN_VALUE) {
                continue;
            }

            StockRatio stockRatio = new StockRatio();
            stockRatio.setStock(stock);
            stockRatio.setRatio(successRatioTemp);
            stockRatio.setMin(finalMin);

            if (!stockRatioMap.containsKey(finalMin)) {
                stockRatioMap.put(finalMin, Lists.newArrayList());
            }
            stockRatioMap.get(finalMin).add(stockRatio);
        }

        for (Integer min : stockRatioMap.keySet()) {
            List<StockRatio> stockRatios = stockRatioMap.get(min);
            Collections.sort(stockRatios, Comparator.comparing(StockRatio::getRatio).reversed());
            List<String> stockList = stockRatios.stream().map(StockRatio::getStock).collect(Collectors.toList());
            System.out.println(min + "\t" + JSON.toJSONString(stockList));
        }
        return stockRatioMap;
    }

    public static Map<String, Map<String, StockKLine>> getStockLineMap(List<String> stockSet) throws Exception {
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");

        // 构建2023年各股票k线
        Map<String, Map<String, StockKLine>> dateToStockLineMap = Maps.newHashMap();
        for (String stock : stockSet) {
            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, 2024, 2022);

            for (StockKLine kLine : kLines) {
                String date = kLine.getDate();
                if (!dateToStockLineMap.containsKey(date)) {
                    dateToStockLineMap.put(date, Maps.newHashMap());
                }
                dateToStockLineMap.get(date).put(stock, kLine);
            }
        }

        return dateToStockLineMap;
    }

    public static Map<String, List<String>> getFilterStock(List<String> stockSet, List<String> dateList, Map<String, Map<String, StockKLine>> dateToStockLineMap) throws Exception {
        // 计算出open低于dn（收盘后的dn）比例最高的前十股票，然后再遍历计算收益

        List<StockKLine> kLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", 2024, 2022);
        Map<String, List<String>> dateToBefore20dayMap = Maps.newHashMap();
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
        }

        Map<String, List<String>> dateToStocksMap = Maps.newHashMap();
        Map<String, List<EarningDate>> earningDateMap = BaseUtils.getEarningDate(null);
        for (int j = 0; j < dateList.size(); j++) {
            List<String> stockFilterList = Lists.newArrayList();
            String date = BaseUtils.unformatDate(dateList.get(j));
            Map<String, StockKLine> stockKLineMap = dateToStockLineMap.get(date);
            List<EarningDate> earningDates = MapUtils.getObject(earningDateMap, date, Lists.newArrayList());
            Set<String> earningStockSet = earningDates.stream().map(EarningDate::getStock).collect(Collectors.toSet());

            Set<String> lastEarningStockSet = Sets.newHashSet();
            if (j > 0) {
                List<EarningDate> lastEarningDates = MapUtils.getObject(earningDateMap, dateList.get(j - 1), Lists.newArrayList());
                lastEarningStockSet = lastEarningDates.stream().map(EarningDate::getStock).collect(Collectors.toSet());
            }

            for (String stock : stockSet) {
                StockKLine kLine = stockKLineMap.get(stock);
                if (kLine == null) {
                    continue;
                }
                if (date.equals("10/27/2023") && (stock.equals("ALGN") || stock.equals("MARA"))) {
                    //                                        System.out.println();
                }
                if (earningStockSet.contains(stock) || lastEarningStockSet.contains(stock)) {
                    continue;
                }
                List<String> _20day = dateToBefore20dayMap.get(date);
                boolean failed = false;
                for (String day : _20day) {
                    StockKLine temp = dateToStockLineMap.get(day).get(stock);
                    if (temp == null || temp.getVolume().doubleValue() < 100000) {
                        failed = true;
                        break;
                    }
                }
                if (failed) {
                    continue;
                }

                stockFilterList.add(stock);
            }

            dateToStocksMap.put(date, stockFilterList);
        }

        return dateToStocksMap;
    }
}
