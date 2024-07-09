package luonq.polygon;

import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/5/5.
 */
@Slf4j
public class GetHistoricalDaily {

    public static String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
    public static String api = "https://api.polygon.io/v1/open-close/";
    public static Set<String> invalidStock = Sets.newHashSet(
      "SCOB", "NVCN", "STOR", "MBAC", "DHHC", "MSAC", "STRE", "HLBZ", "MSDA", "GIAC", "WPCA",
      "SJI", "WPCB", "LGTO", "CNCE", "NVSA", "GRAY", "LHCG", "RACY", "KMPH", "PTOC", "LHDX", "TPBA",
      "AYLA", "IPAX", "KSI", "LYLT", "MCAE", "OIIM", "VVNT", "KNBE", "SMIH", "KVSC", "WQGA", "ELVT",
      "FOXW", "THAC", "BCOR", "FPAC", "SVFB", "SVFA", "PDOT", "AIMC", "DCT", "GSQD", "SESN", "COUP", "JCIC", "LION",
      "ARCK", "COWN", "DNZ", "CPAR", "CPAQ", "MCG", "MIT", "RKTA", "EVOP", "VGFC", "AAWW", "TZPS", "RCII", "OBSV",
      "MTP", "MEAC", "SJR", "APEN", "BLI", "CENQ", "JATT", "TYDE", "MLAI", "HERA", "VORB", "JMAC", "VPCB", "ABGI",
      "PFDR", "PFHD", "ESM", "HORI", "NGC", "FINM", "SGFY", "BNFT", "UMPQ", "DLCA", "DCRD", "DTRT", "FRON", "IBER",
      "ATCO", "FRSG", "PONO", "ACDI", "SPKB", "MFGP", "TBSA", "NAAC", "ALBO", "ACQR", "CIXX", "GEEX", "BSGA", "BYN",
      "SUAC", "BRD", "SQL", "APAC", "NETC", "CNGL", "SLAM", "CFFS", "CCM", "BTBD", "KVSA", "GATE", "LBTYB", "PLMI", "LHC", "APTM",
      "AGMH", "APRN", "IXAQ", "RRAC", "MCAC", "LGST", "DISA", "MCAG", "APCA", "EMCG", "MTRY", "SLAC", "SELB", "LGVC", "NEWR", "PMGM",
      "CGA", "MBTC", "CEQP", "GSMG", "PCYG", "QOMO", "CNNB", "HUDA", "SUNL", "NXGN", "SFR", "YOTA", "SCPL", "CIR", "NFNT", "PCCT",
      "DEN", "SFT", "CXAC", "CCV", "BLUA", "AACI", "NOVV", "ZYNE", "SSU", "BTWN", "FXCO", "ARIZ", "THRN", "KLR", "VEDU", "JUN", "NPAB",
      "SDC", "MCLD", "MDNA", "SLVR", "GTAC", "MBSC", "RADI", "FHLT", "BTB", "UTAA", "SLGG", "BKI", "RCAC", "SURF", "DRTT", "FOCS",
      "CFMS", "LOV", "LATG", "YELL", "UBA", "ARTE", "ARYD", "KDNY", "CGRN", "BWC", "APPH", "HMPT", "FGMC", "UPH", "WE", "ENCP", "BSAQ",
      "PDCE", "IRAA", "ZING", "SDAC", "APGN", "PTRA", "BLNG", "MURF", "NOVN", "ASCA", "SWSS", "MMP", "FORG", "SQZ", "HMAC", "OSTK",
      "DMS", "EAC", "CHAA", "FICV", "MTAC", "UTME", "TRON", "TRCA", "IRRX", "TRHC", "OCAX", "FWAC", "LSI", "EFHT", "TRTL", "LSXMB",
      "DCP", "DICE", "OTEC", "APMI", "GRIL", "AZYO", "ARBG", "GRCY", "TALS", "DBTX", "GRNA", "NCR", "EMBK", "IRNT", "NMTR", "FRBN",
      "SGII", "RAAS", "ASPA", "DALS", "VNTR", "CPAA", "VQS", "VECT", "KYCH", "BWAQ", "FRLA", "FREQ", "GDNR", "ATAK", "SEV", "TBCP",
      "BLU", "NLS", "ACAX", "EDTX", "ARNC", "AHRN", "GDST", "ORCC", "RIDE", "HVBC", "HWKZ", "FRGI", "ACBA", "MNTN", "CORS", "RMGC",
      "OLIT", "MTCR", "TRTN", "RCLF", "ATEK", "JUPW", "DUET", "CVT", "PFSW", "PNAC", "MLVF", "WTMA", "MMMB", "MTVC", "GSRM", "LCI",
      "OTMO", "SHAP", "BOCN", "WAVC", "PLXP", "SNRH", "ARYE", "METX", "MOLN", "GAQ", "MLAC", "BBLN", "AZRE", "OPA", "HEXO", "ACAQ",
      "BOAC", "ICCH", "RJAC", "DTOC", "MGI", "SCUA", "TCBS", "DSEY", "AQUA", "OSI", "EGLX", "CS", "HPLT", "ITAQ", "STSA", "LITT",
      "VMGA", "ENOB", "SHUA", "USX", "GSQB", "GIA", "XM", "NEX", "CPUH", "SUMO", "BVXV", "BBBY", "ADEX", "VHNA", "RVLP", "EUCR",
      "BSMX", "SVNA", "SI", "ATVI", "TGR", "AJRD", "VIVE", "FZT", "JUGG", "CEMI", "GBRG", "TCOA", "VACC", "FSRX", "RE", "ICPT", "ORIA",
      "IPVI", "ATAQ", "XPDB", "NATI", "GLG", "SYNH", "KRNL", "FSNB", "AMAO", "ENTF", "OFC", "NYMX", "CHRA", "WWE", "TIG", "KAL", "IQMD",
      "CIH", "GLOP", "CBIO", "RETA", "OIG", "TA", "NH", "ZT", "FACT", "FTII", "ABST", "ADER", "BWAC", "ICNC", "CBRG", "PEAR", "SGTX",
      "FRG", "TRAQ", "RWOD", "NSTD", "MEKA", "GMVD", "BMAQ", "NBST", "OBNK", "PHYT", "GFX", "EOCW", "SPPI", "TIOA", "ROCL", "CYXT",
      "ISEE", "ADMP", "GFGD", "ATTO", "CYAD", "GENQ", "HZNP", "EVOJ", "PNTM", "MEOA", "NHIC", "MGTA", "UNVR", "CREC", "VSAC", "HAIA",
      "EQRX", "FLFV", "AURC", "AVID", "TTCF", "FISV", "GFOR", "VRAY", "HILS", "LMNL", "ALPA", "RUTH", "VBFC", "ALPS", "SGHL", "REUN",
      "MNTV", "MPRA", "DTEA", "BYTS", "SPCM", "XPAX", "CIDM", "TLGA", "KSPN", "SAMA", "AMOT", "IDBA", "VBLT", "BNNR", "JNCE", "TCFC",
      "CIIG", "BPAC", "GNUS", "PGRW", "TCRR", "RENN", "CCAI", "JWAC", "GVCI", "DFFN", "BGCP", "PKI", "AMRS", "DNAB", "IVCP", "SRGA",
      "HHC", "PRLH", "REVE", "ATNX", "PIAI", "ROCC", "BGRY", "FRC", "WWAC", "AFAR", "GLS", "TCVA", "CRZN", "TMKR", "PRTC", "PRPC", "KINZ",
      "NBRV", "UPTD", "DNAD", "CINC", "PRDS", "KBAL", "ADAL", "ITCB", "OSH", "CLRC", "AAC", "PICC", "PHCF", "BOXD", "ATCX", "PTE", "ZEV",
      "TCDA", "YVR", "LFAC", "RAD", "GOGN", "BIOC", "BITE", "ONCS", "MACA", "BREZ", "AIB", "GWII", "HMA", "IMBI", "PRTK", "QTEK", "RXDX",
      "CSII", "QUOT", "AOGO", "PBAX", "HSC", "AVAC", "ANZU", "VBOC", "DPCS", "CTIC", "LMST", "LDHA", "BGSX", "APM", "DMYS", "BIOS",
      "OXUS", "INTE", "CMCA", "AKU", "GET", "DKDCA", "INFI", "VCXB", "GYRO", "CLBR", "ROCG", "ONEM", "BRQS", "PRBM", "FSTX", "LVAC",
      "SIRE", "GGAA", "ERES", "ANGN", "FTAA", "RNER", "FCAX", "ABC", "OXAC", "OPOF", "LEGA", "HCCI", "HYRE", "PME", "AMCI", "WEJO",
      "KAII", "TWNK", "FEXD", "HCMA", "ESTE", "POW", "DHCA", "MYOV", "RONI", "ZEST", "TMDI", "LVRA", "RTL", "AEAC", "VIVO", "FTEV",
      "FMIV", "CTAQ", "AFTR", "FTPA", "AMOV", "INT", "AMYT", "ANAC", "HSKA", "NUVA", "ANPC", "IBA", "INDT", "AGAC", "AXAC", "SRNE",
      "SCHN", "IDRA", "SIVB", "WEBR", "TWCB", "HAPP", "UIHC", "ERYP", "HZN", "GXII", "YTPG", "FCRD", "AERC", "SAL", "IAA", "IMV", "PANA",
      "ABB", "QTT", "IMRA", "CLAA", "AEHA", "SCAQ", "AVCT", "STET", "VLAT", "PACX", "CLXT", "ROC", "MICT", "HTGM", "PRVB", "AVEO",
      "BRIV", "HSAQ", "ISO", "TETC", "SKYA", "AVYA", "BRMK", "ATY", "AMV", "RAM", "CDAK", "TOAC", "QUMU", "AUD", "VLON", "EBAC", "HCNE",
      "AUY", "PSPC", "IVC", "MAXR", "PAYA", "DGNU", "AGFS", "RFP", "OPNT", "INKA", "ITQ", "APN", "STAR", "LOKM", "ALR", "BIOT", "TWNI",
      "VLTA", "LGAC", "VLDR", "AGGR", "SCMA", "VLNS", "CMRA", "OCFCP", "NMRD", "HLGN", "BSBK", "SLGC", "AGRX", "RIBT", "BSGM", "FFBW", "BBIG", "VMCA",
      "FNVT", "TGAA", "NVTA", "NESR", "CNDA", "MSVB", "PLAO", "LTRPA", "LTRPB", "USDP", "CWBR", "TGVC", "GRTX", "MCAA", "BCEL", "ELOX", "PUCK", "XTLB",
      "ZIVO", "SEAC", "THCP", "BLCM", "BTTX", "ELYS", "FXLV", "BLEU", "LIBY", "COMS", "THMO", "RBKB", "CORR", "BLPH", "EMLD", "SVVC", "WINV", "ARDS", "DSAQ",
      "UTRS", "PEGR", "EVLO", "NXTP", "MDRX", "PETV", "CPTK", "ASAP", "FIAC", "ASCB", "TRIS", "SONX", "ASPU", "HGEN", "FRBK", "BNIX", "POCI", "PFTA", "EFTR",
      "GMBL", "LTCH", "GMFI", "WTER", "ATIF", "PORT", "NRAC", "LCFY", "MOBQ", "ISUN", "CRGE", "PXMD", "EXPR", "CALA", "HHLA", "HHGC", "REED", "FSEA", "RENE",
      "PPHP", "MOTS", "MGAM", "KRBP", "GHG", "ALSA", "KACL", "JWSM", "OMQS", "TCON", "BXRX", "PPYA", "TLGY", "VAPO", "SIOX", "VAXX", "CSTA", "NSTB", "NSTC",
      "SRAX", "AVHI", "HALL", "OFED", "BYNO", "YTEN", "KBNT", "IVCB", "CCTS", "AFIB", "CLOE", "PROC", "SBIG", "INAQ", "SBNY", "CUEN", "MRAI", "TETE", "CLVR",
      "MITA", "HSTO", "FEDU", "EBET", "CURO", "GHIX", "MAQC", "PBLA", "MARK", "FNCH", "MAYS", "TWLV");

    public static List<StockKLine> getHistoricalDaily(String stock, List<String> addDate, HttpClient httpClient) {
        List<StockKLine> list = Lists.newArrayList();
        for (String date : addDate) {
            String url = api + stock + "/" + date + "?adjust=true&" + apiKey;
            GetMethod get = new GetMethod(url);

            try {
                httpClient.executeMethod(get);
                InputStream stream = get.getResponseBodyAsStream();
                Map<String, Object> result = JSON.parseObject(stream, Map.class);
                String status = MapUtils.getString(result, "status");
                if (StringUtils.equals(status, "NOT_FOUND")) {
                    continue;
                }
                if (!StringUtils.equals(status, "OK") && StringUtils.equals(status, "NOT_FOUND")) {
                    System.err.println(stock + " date=" + date + " status=" + status);
                    return null;
                }

                String symbol = MapUtils.getString(result, "symbol");
                if (!symbol.equals(stock)) {
                    System.err.println(stock + " date=" + date + " symbol=" + symbol);
                    return null;
                }

                String from = MapUtils.getString(result, "from");
                if (!from.equals(date)) {
                    System.err.println(stock + " date=" + date + " from=" + from);
                    return null;
                }

                double open = MapUtils.getDouble(result, "open");
                double close = MapUtils.getDouble(result, "close");
                double high = MapUtils.getDouble(result, "high");
                double low = MapUtils.getDouble(result, "low");
                BigDecimal volume = BigDecimal.valueOf(MapUtils.getDouble(result, "volume"));

                String formatDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd")).format(Constants.FORMATTER);
                StockKLine kLine = StockKLine.builder().date(formatDate).open(open).close(close).high(high).low(low).volume(volume).build();
                list.add(kLine);
            } catch (Exception e) {
                log.error(stock + " " + e.getMessage());
            } finally {
                get.releaseConnection();
            }
        }

        return list;
    }

    public static void getData() throws Exception {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        LocalDate yesterday;
        if (today.getDayOfWeek().getValue() == 1) {
            yesterday = today.minusDays(3);
        } else {
            yesterday = today.minusDays(1);
        }

        // 每年的第一个工作日需要初始化目录及文件
        firstWorkDayInit();

        int threadCount = 100;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        ThreadPoolExecutor cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        BlockingQueue<HttpClient> queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(new HttpClient());
        }

        String dirPath = getHistoryDirPath(today);
        LocalDate firstWorkDay = BaseUtils.getFirstWorkDay();
        Map<String, String> stockMap = BaseUtils.getFileMap(dirPath);
        int fileYear = 2023;
        if (year != 2023) {
            if (today.isAfter(firstWorkDay)) {
                fileYear = year;
            } else {
                fileYear = year - 1;
            }
        }
        for (String stock : stockMap.keySet()) {
            if (invalidStock.contains(stock)) {
                //                log.info("invalid stock: " + stock);
                continue;
            }
            if (!stock.equals("AAPL")) {
                //                continue;
            }
            String file = stockMap.get(stock);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(file, fileYear);
            List<String> addDate = Lists.newArrayList();
            String date;
            if (CollectionUtils.isEmpty(stockKLines)) {
                //                log.info("no file " + stock);
                date = "01/01/" + year;
            } else {
                StockKLine stockKLine = stockKLines.get(0);
                date = stockKLine.getDate();
            }

            LocalDate latestDate = LocalDate.parse(date, Constants.FORMATTER);
            while (latestDate.isBefore(yesterday)) {
                latestDate = latestDate.plusDays(1);
                addDate.add(latestDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            }

            if (CollectionUtils.isEmpty(addDate)) {
                //                log.info("has get " + stock);
                continue;
            }

            HttpClient httpClient = queue.take();
            cachedThread.execute(() -> {
                List<StockKLine> dataList = null;
                try {
                    dataList = getHistoricalDaily(stock, addDate, httpClient);
                    if (CollectionUtils.isEmpty(dataList)) {
                        //                        log.info("no data " + stock);
                        return;
                    }
                    for (StockKLine kLine : dataList) {
                        stockKLines.add(0, kLine);
                    }
                    BaseUtils.writeStockKLine(file, stockKLines);
                } catch (Exception e) {
                    log.error(stock + " " + e.getMessage());
                } finally {
                    if (CollectionUtils.isNotEmpty(dataList)) {
                        //                        log.info("get success " + stock);
                    }
                    queue.offer(httpClient);
                }
            });
        }
        while (true) {
            if (queue.size() == threadCount) {
                break;
            }
        }
        cachedThread.shutdown();
    }

    private static String getHistoryDirPath(LocalDate today) {
        String dirPath;
        int year = today.getYear();
        if (year == 2023) {
            dirPath = Constants.HIS_BASE_PATH + "2023daily/";
        } else {
            LocalDate firstWorkDay = BaseUtils.getFirstWorkDay();

            // 如果今天是今年第一个工作日，则文件目录为前一年（2023年要特殊处理）。如果不是，则文件目录为当年
            if (today.isAfter(firstWorkDay)) {
                dirPath = Constants.HIS_BASE_PATH + year + "/dailyKLine";
            } else {
                if (year - 1 == 2023) {
                    dirPath = Constants.HIS_BASE_PATH + "2023daily/";
                } else {
                    dirPath = Constants.HIS_BASE_PATH + (year - 1) + "/dailyKLine";
                }
            }
        }
        return dirPath;
    }

    private static void firstWorkDayInit() throws Exception {
        LocalDate firstWorkDay = BaseUtils.getFirstWorkDay();
        LocalDate today = LocalDate.now();
        if (firstWorkDay.isEqual(today)) {
            int year = today.getYear();
            File dailyKLineDir = new File(Constants.HIS_BASE_PATH + year + "/dailyKLine/");
            dailyKLineDir.mkdirs();

            File tradeDir = new File(Constants.TRADE_OPEN_PATH + year + "/");
            tradeDir.mkdirs();

            String dirPath = getHistoryDirPath(today);
            Map<String, String> stockMap = BaseUtils.getFileMap(dirPath);
            Set<String> stockSet = stockMap.keySet();
            for (String stock : stockSet) {
                File file = new File(Constants.HIS_BASE_PATH + year + "/dailyKLine/" + stock);
                if (!file.exists()) {
                    file.createNewFile();
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);

        getData();
        log.info("============ end ============");
    }
}
