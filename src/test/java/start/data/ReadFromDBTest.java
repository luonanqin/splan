package start.data;

import bean.EarningDate;
import bean.Page;
import bean.StockRehab;
import bean.Total;
import bean.TradeCalendar;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import luonq.data.ReadFromDB;
import luonq.mapper.EarningDataMapper;
import luonq.mapper.StockDataMapper;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;
import util.Constants;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static bean.EarningDate.*;

@Slf4j
public class ReadFromDBTest extends BaseTest {

    @Autowired
    private StockDataMapper stockDataMapper;

    @Autowired
    private EarningDataMapper earningDataMapper;

    @Autowired
    private ReadFromDB readFromDB;

    @Before
    public void before() {
        log.info("begin");
    }

    @After
    public void after() {
        log.info("after");
    }

    @Test
    public void queryForAllYear() throws Exception {
        Page page = new Page();
        List<Total> allTotals = Lists.newLinkedList();
        while (true) {
            List<Total> totals = stockDataMapper.queryForAllYear("2023", page);
            int size = totals.size();
            if (size == 0) {
                break;
            }
            page.setId(totals.get(size - 1).getId());
            allTotals.addAll(totals);
        }
        System.out.println(allTotals.size());
    }

    @Test
    public void getStockForEarning() {
        List<String> codeList = readFromDB.getStockForEarning("2023-12-08");
        System.out.println(codeList);
    }

    @Test
    public void getAllStock() {
        List<String> allStock = readFromDB.getAllStock(2023, "2023-12-07");
        System.out.println(allStock);
    }

    @Test
    public void getAllStockData() {
        List<Total> allStockData = readFromDB.getAllStockData(2023, "2023-12-07");
        System.out.println(allStockData.size());
    }

    @Test
    public void getLatestRehab() {
        List<String> list = Lists.newArrayList("CVCO", "SCS", "CMRE", "BBD", "BBU", "SCOR", "NMRD", "BBW", "NVCN", "BBY", "NMRK", "FNLC", "BCC", "BCE", "BCH", "KLXE", "BCO", "SEE", "CVCY", "SEM", "SES", "BDC", "A", "CMTG", "CVEO", "B", "C", "D", "AGNC", "STRR", "F", "G", "H", "MSBI", "JAGX", "J", "K", "L", "BDN", "M", "O", "SLCA", "CECO", "R", "LGND", "SFE", "T", "V", "BDX", "SFL", "X", "SFR", "BJRI", "NVDA", "JZXN", "BEN", "BEP", "TFSL", "BBAR", "SGC", "VLGEA", "NECB", "JVA", "SGH", "STRC", "STRA", "LGMK", "CMTL", "BFC", "BSBR", "SGU", "APAM", "BFH", "SCSC", "NVGS", "HCTI", "SHG", "SHO", "BBDC", "JWN", "SHW", "OCFCP", "MSCI", "HCSG", "BGS", "SIG", "SLDB", "SII", "CVGW", "BHE", "NVFY", "BHG", "JXN", "SIX", "AGRI", "STWD", "HLGN", "LPCN", "BHR", "MSFT", "NVIV", "BSFC", "BBDO", "SJM", "SJT", "BSET", "BIG", "SJW", "MBCN", "APDN", "BIP", "BIO", "RIBT", "LXRX", "AGRX", "NEGG", "JAKK", "RICK", "FWBI", "APCX", "SKT", "MSEX", "AGRO", "STVN", "SKY", "SKX", "GIFI", "QNRX", "SLB", "HLIO", "SLF", "CEIX", "HLIT", "SLG", "SLM", "SLP", "BKD", "SLS", "BKE", "BKH", "SCVL", "FFBC", "BKI", "SLGN", "MSGM", "BKR", "SMG", "BKU", "BSIG", "PKOH", "CVLG", "JAMF", "MSGS", "SMP", "BBGI", "BLK", "CELH", "PCAR", "BBIG", "SNA", "IONM", "SCYX", "BLX", "BMA", "SNV", "SNX", "MBIN", "BMI", "CVLY", "MBIO", "SNY", "BMO", "GIGM", "SOI", "CELZ", "BMY", "SOL", "SON", "SOS", "RIGL", "BNL", "SPB", "BNS", "PTEN", "NVOS", "SPH", "SPG", "SPI", "SPR", "HLNE", "BOH", "EKSO", "GIII", "BSMX", "BBLG", "NVNO", "FNWB", "KAI", "CENX", "FNWD", "XBIO", "XBIT", "KAR", "CENN", "JRSH", "FFIN", "SRC", "SRE", "BPT", "SRG", "GILT", "NEPH", "SRL", "FFIV", "KBH", "SRT", "LPLA", "KBR", "AGYS", "QFIN", "NEPT", "SSB", "SSD", "NEOG", "SSP", "BRC", "NEON", "SLNG", "SST", "APLE", "CEPU", "APLD", "FFIC", "GILD", "SLNO", "FFIE", "BRO", "BRP", "STC", "BRT", "STE", "VUZI", "STG", "BRY", "BRX", "CERE", "STN", "STR", "STT", "PTIX", "STX", "KDP", "STZ", "BSM", "MBOT", "NERV", "IOSP", "RILY", "SUI", "BSX", "JRVR", "BSY", "SUM", "SUN", "BSQR", "HUBB", "SUP", "HUBG", "NNBR", "KEN", "CNDA", "SUZ", "KEY", "KEX", "SVC", "BTU", "CNET", "GIPR", "NETI", "BSRR", "BUD", "IXHL", "GRBK", "APOG", "KFS", "ZYXI", "BUR", "KFY", "SWI", "SWK", "KGC", "SWN", "APPS", "SLRC", "NNDM", "GRAB", "IOVA", "SWX", "BVH", "BKCC", "CETX", "SLRX", "RIOT", "SXC", "MSTR", "PTMN", "SXI", "KHC", "BBSI", "BWA", "SXT", "IGIC", "RRBI", "SYF", "SYK", "FFNW", "BXC", "KIM", "SYY", "CNHI", "TOWN", "GABC", "BXP", "APRE", "MBRX", "NEXA", "KMPR", "NEXI", "BYD", "BBUC", "RITM", "BSVN", "PTPI", "GRFS", "FWRD", "GREE", "APTV", "NEWT", "BZH", "WPRT", "KKR", "APTO", "MSVB", "SLVM", "RACE", "SDHY", "SDIG", "ETRN", "BBWI", "MBWM", "KLR", "PLAY", "PLBC", "TGLS", "ECOR", "KMB", "APWC", "KMI", "LPTX", "KMT", "APVO", "KMX", "PLAB", "TXMD", "TGNA", "ELDN", "GAIA", "KNX", "CNMD", "GAIN", "CNOB", "USAC", "KOP", "KOS", "GRIN", "USAU", "TAC", "PLCE", "TAL", "TAK", "TAP", "SUNW", "USDP", "PCTI", "PTVE", "TBI", "TXRH", "USEA", "CAC", "CAE", "CAG", "CAH", "CAL", "JJSF", "RAIL", "CAR", "FFWM", "CAT", "IPAR", "KRC", "TCN", "KRG", "GALT", "TCX", "KRP", "KRO", "KRT", "CBL", "GRNQ", "SUPV", "GRNT", "GAME", "IPDN", "CBT", "TDG", "CBU", "CBZ", "CWBR", "TDS", "CCD", "TDW", "CCI", "CCK", "KSS", "CCJ", "CCM", "GRMN", "CCL", "CCO", "CCS", "NWBI", "KTB", "TEL", "ECVT", "CWCO", "TER", "CWEN", "CDE", "SMBK", "JBGS", "TEX", "GRPN", "WYNN", "TFC", "TGTX", "CDW", "CNSP", "CNSL", "BTCM", "CEG", "NFBK", "TFX", "GROM", "SURG", "UBFO", "GROW", "GROV", "TGH", "TGI", "SMBC", "WHLR", "TGT", "MCBS", "CFG", "CFR", "THC", "MKSI", "BCBP", "MTDR", "THG", "BTCY", "KWE", "THO", "CGA", "BTCS", "CGC", "ELME", "BCDA", "USIO", "JBHT", "KWR", "MTCH", "CGO", "NWFL", "AYRO", "PCYO", "SDRL", "IPGP", "MTCR", "MCBC", "CFFN", "TIL", "CHD", "CHE", "CHH", "CHI", "MKTW", "SMFL", "MKTX", "CHK", "CNXC", "CNXA", "VVOS", "CHT", "CHS", "CHX", "CHW", "CHY", "CIA", "GASS", "TJX", "CIM", "CIO", "MTEM", "TKC", "TKR", "SMFG", "CNXN", "MCFT", "PLOW", "FOSL", "PLPC", "GRVY", "KEQU", "JBLU", "PLNT", "PARAA", "FORR", "FGBI", "VVPR", "LYTS", "ELOX", "USNA", "TMO", "CLB", "CLF", "CLH", "GATX", "MCHX", "VERO", "BKYI", "TNC", "EDBL", "CLS", "CLX", "CLW", "TNK", "TNL", "CMA", "TNP", "CMC", "VERU", "CME", "QGEN", "CMI", "VERY", "DINO", "CMP", "CMS", "TOL", "USPH", "VNCE", "KERN", "CNA", "VERB", "RAVE", "CNC", "MCHP", "DIOD", "CNI", "CNK", "CNP", "PDCO", "CNO", "TPC", "CNQ", "TPB", "CNS", "TPG", "CNX", "DZSI", "FOXA", "SMMF", "TPR", "COE", "XTLB", "COF", "TPX", "NFLX", "BCLI", "COO", "ZION", "COP", "LBTYA", "LAD", "LAC", "CPB", "CPA", "CPF", "SMLP", "CPE", "CPG", "LBTYK", "CPK", "UBSI", "TRC", "LAZ", "CPT", "TRI", "CPZ", "LBC", "NOAH", "TRN", "TRP", "TRS", "TRU", "TRV", "TSE", "ORCL", "PLUG", "TSN", "TSM", "TSQ", "BTOG", "CRC", "DISH", "JBSS", "CRI", "PLUR", "CRK", "CRM", "BCML", "PLUS", "TTC", "TTD", "CRS", "COCP", "COCO", "TTI", "HEAR", "LDI", "SEAC", "PLXS", "CSL", "CSQ", "CSR", "CSV", "CSX", "LEA", "BCPC", "TUP", "CODI", "LEG", "SMPL", "NWSA", "LEN", "CFRX", "CODA", "CTG", "TPST", "CTO", "ZIVO", "CTS", "CTV", "MTRN", "HMST", "MTRX", "TPVG", "GSBC", "GSBD", "THFF", "DRIO", "SMSI", "SECO", "CUK", "LFT", "ELYS", "SEAS", "SEAT", "MLAB", "COFS", "FXNC", "TWI", "CUZ", "ORGS", "BLCM", "TWO", "JKHY", "CVE", "PLYM", "CVI", "SMRT", "BLBX", "CVS", "CVX", "SVFD", "COGT", "BLDP", "HEES", "TXN", "BCSF", "NOGN", "TXT", "CWH", "SEED", "LHX", "SEEL", "IPWR", "CWT", "PULM", "TYL", "COHU", "COHR", "MCRI", "LII", "MLCO", "SMTC", "LIN", "DAIO", "DRMA", "CXW", "BLFS", "CYD", "CYH", "ORLY", "CYN", "GBCI", "DALN", "FPAY", "KNOP", "LKQ", "DAKT", "GBDC", "VNOM", "EMBC", "GSHD", "SEIC", "EURN", "LLY", "LIFE", "KNSL", "XCUR", "ORMP", "MCVT", "BLIN", "LMT", "COLM", "COLD", "COLB", "LNC", "LND", "LNN", "EDSA", "COMS", "LNT", "LNW", "HELE", "LOB", "EMCG", "AIHS", "LIFW", "THMO", "UAA", "LOW", "KNTK", "BLKB", "UAL", "LPG", "UAN", "LPL", "COOP", "LPX", "COOL", "PDSB", "MLKN", "UBS", "DAC", "SELF", "UBX", "DAL", "DARE", "DAN", "UCBI", "ORRF", "DRRX", "DAR", "KFFB", "BLMN", "DRUG", "ORTX", "DBD", "CGAU", "THRM", "DBI", "HEPA", "THRX", "UDR", "DATS", "DCI", "DCO", "CORR", "GBLI", "CGBD", "LTC", "YOSH", "DDD", "GBNY", "UTHR", "LINC", "COST", "DDS", "UFI", "LINK", "COSM", "SNCE", "BLPH", "CXDO", "THTX", "DEA", "DRVN", "DEI", "AZPN", "JCTCF", "LUV", "COTY", "YORW", "DAVE", "SNAX", "SVRA", "UGI", "SNDL", "LVS", "BLRX", "SNDR", "DFS", "NOVT", "UHT", "AA", "UHS", "AB", "AC", "WING", "AG", "AL", "AM", "SNCR", "AN", "EMKR", "AP", "DGX", "HESM", "AY", "ARAV", "AZ", "UIS", "BA", "BC", "LIQT", "ARCO", "DHI", "BLUE", "LXP", "BG", "AIRC", "LXU", "ARCH", "BK", "BN", "MUFG", "BP", "ARCB", "ARCC", "BQ", "DHR", "RKDA", "BR", "DHT", "UTMD", "LYB", "BW", "BX", "BY", "LRCX", "LYG", "CB", "CC", "CE", "CF", "CG", "CI", "DIN", "SNEX", "CL", "CM", "CP", "SNES", "CR", "DIS", "CW", "LZB", "SVVC", "AIRS", "AZTA", "ARCT", "DB", "DD", "ARES", "DE", "DG", "DK", "WISA", "DM", "DO", "DQ", "WISH", "ULH", "AREB", "DV", "DX", "DY", "MDGL", "EA", "EC", "ED", "EE", "LABP", "WABC", "DKL", "EL", "SNGX", "UMC", "SEVN", "WIRE", "DKS", "ES", "ET", "UMH", "EW", "AADI", "DLB", "FA", "FE", "FF", "FG", "FL", "UNB", "DLR", "UNF", "FR", "UNH", "LIVE", "CDZIP", "DLX", "UNM", "UNP", "GB", "GD", "GE", "LRFC", "LADR", "PVBC", "GL", "GM", "MDGS", "GP", "GS", "GT", "KFRC", "DNB", "HA", "PEAK", "HD", "HE", "OSBC", "HI", "HL", "PECO", "ARIS", "UTRS", "HP", "UPC", "HR", "UPH", "MULN", "HY", "UTSI", "HNNA", "UPS", "DOC", "PMTS", "LIXT", "WAFD", "AAIC", "PEBO", "IP", "IR", "DOV", "IT", "MAA", "DOX");
        System.out.println(System.currentTimeMillis());
        for (String s : list) {
            StockRehab aapl = readFromDB.getLatestRehab(s);
            System.out.println(aapl);
        }
        System.out.println(System.currentTimeMillis());
    }

    @Test
    public void getTradeCalendar() {
        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        TradeCalendar tradeCalendar = readFromDB.getTradeCalendar(today);
        System.out.println(tradeCalendar);
    }

    @Test
    public void checkEarningData() {
        List<EarningDate> earningDatas = earningDataMapper.queryEarningByDate("2024-02-28");
        GetMethod get = new GetMethod("https://api.nasdaq.com/api/calendar/earnings?date=2024-02-28");
        get.addRequestHeader("authority", "api.nasdaq.com");
        get.addRequestHeader("accept", "application/json, text/plain, */*");
        get.addRequestHeader("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36");
        get.addRequestHeader("origin", "https://www.nasdaq.com");
        get.addRequestHeader("sec-fetch-site", "same-site");
        get.addRequestHeader("sec-fetch-mode", "cors");
        get.addRequestHeader("sec-fetch-dest", "empty");
        get.addRequestHeader("referer", "https://www.nasdaq.com/");
        get.addRequestHeader("accept-language", "en-US,en;q=0.9");
        try {
            HttpClient httpClient = new HttpClient();
            httpClient.executeMethod(get);
            InputStream stream = get.getResponseBodyAsStream();
            Map<String, Object> result = JSON.parseObject(stream, Map.class);
            Map<String, Object> data = (Map<String, Object>) MapUtils.getObject(result, "data");
            List<Map<String, String>> rows = (List<Map<String, String>>) MapUtils.getObject(data, "rows");

            Map<String, EarningDate> earningMap = earningDatas.stream().collect(Collectors.toMap(EarningDate::getStock, Function.identity()));
            for (Map<String, String> row : rows) {
                String time = row.get("time");
                String code = row.get("symbol");
                if (StringUtils.isBlank(time)) {
                    continue;
                }

                if (!earningMap.containsKey(code)) {
                    System.out.println(code + " not exist");
                    continue;
                }

                EarningDate earningData = earningMap.get(code);
                if (StringUtils.equalsIgnoreCase(time, "time-after-hours") && !StringUtils.equalsIgnoreCase(AFTER_MARKET_CLOSE, earningData.getEarningType())) {
                    System.out.println(code + " is " + earningData.getEarningType());
                }
                if (StringUtils.equalsIgnoreCase(time, "time-pre-hours") && !StringUtils.equalsIgnoreCase(BEFORE_MARKET_OPEN, earningData.getEarningType())) {
                    System.out.println(code + " is " + earningData.getEarningType());
                }
                if (StringUtils.equalsIgnoreCase(time, "time-not-supplied") && !StringUtils.equalsIgnoreCase(TIME_NOT_SUPPLIED, earningData.getEarningType())) {
                    System.out.println(code + " is " + earningData.getEarningType());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            get.releaseConnection();
        }

    }
}