package luonq.strategy.continuerise;

import bean.BOLL;
import bean.ContinueRise;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static luonq.strategy.continuerise.ContinueRiseStrategy.*;

/**
 * 候选：
 * 1.连续三天上涨（收盘价）
 * 2.第一天的前一天下跌（收盘价）
 * <p>
 * 过滤：
 * 1.这三天成交量连续递减
 * 2.这三天成交量先减后增 且 第三天比第一天少
 * 3.这三天成交量先增后减
 * <p>
 * 买入：第三天收盘买入
 * 计算：第四天收盘的涨跌情况，第四天收盘大于第三天收盘则为盈利，否则为亏损
 * <p>
 * 收益17849 avgRatio1.0132174702790833
 * <p>
 */
public class Strategy12_5 {

    public static final String TEST_STOCK = "";
    public static final Set<String> SKIP_SET = Sets.newHashSet("FRC", "SIVBQ");

    public static void main(String[] args) throws Exception {
        double test = 10000 * Math.pow(1.004, 200);
        System.out.println(test);
        double exchange = 6.94;
        double init = 10000 / exchange;
        int beforeYear = 2023, afterYear = 2021;
        double capital = init;
        Set<String> invalidStockSet = Sets.newHashSet("FIAC", "FRC", "SIVB", "BIOR", "HALL", "OBLG", "ALBT", "IPDN", "OPGN", "TENX", "AYTU", "DAVE", "NXTP", "ATHE", "CANF", "GHSI", "EEMX", "EFAX", "HYMB", "NYC", "SPYX", "PBLA", "JEF", "ACGN", "EAR", "FWBI", "IDRA", "JFU", "CNET", "APM", "JAGX", "OCSL", "OGEN", "SIEN", "SRKZG", "CETX", "UVIX", "EDBL", "PHIO", "SWVL", "MRKR", "REED", "WISA", "FTFT", "FVCB", "LMNL", "REVB", "DYNT", "BRSF", "LCI", "DGLY", "PCAR", "CZOO", "MIGI", "NAOV", "COMS", "GFAI", "INBS", "SNGX", "APRE", "FNGG", "GNUS", "VYNE", "CRBP", "ATNX", "CFRX", "ECOR", "NVDEF", "SHIP", "AMST", "GMBL", "RELI", "WINT", "FNRN", "MFH", "XBRAF", "RKDA", "HCDI", "IONM", "VXX", "SFT", "VEON", "AKAN", "NYMT", "ORTX", "ASLN", "KRBP", "IVOG", "IVOO", "IVOV", "VIOG", "VIOO", "VIOV", "GRAY", "MRBK", "BAOS", "GGB", "LKCO", "TESTING", "VIA", "IDAI", "PTIX", "RDHL", "CUEN", "FRGT", "GCBC", "ALLR", "CREX", "MTP", "MNST", "NOGN", "BPTS", "CETXP", "ENSC", "HLBZ", "CHNR", "BEST", "MBIO", "WTER", "AGRX", "BLBX", "VBIV", "WISH", "EJH", "ARVL", "MEIP", "MINM", "ASNS", "VERB", "BKTI", "FRSX", "OIG", "LGMK", "POAI", "SMFL", "CLXT", "JXJT", "SBET", "EZFL", "IMPP", "MEME", "PSTV", "VISL", "WEED", "MDRR", "MULN", "WGS", "GTE", "SMH", "CRESY", "BBIG", "HEPA", "AWH", "FRLN", "LPCN", "RETO", "VERO", "ALPP", "BNMV", "EAST", "GLMD", "IFBD", "RETO", "XBIO", "XELA", "XELAP", "CYCN", "GREE", "SDIG", "BIOC", "AULT", "NISN", "CHDN", "LGMK", "HLBZ", "LPCN", "BBIG", "XBIO", "JATT", "TGAA", "GRAY", "GREE", "SDIG", "SMFL", "SMFG", "VERO", "LCI", "TYDE", "DRMA", "BLIN", "HEPA", "SESN", "CR", "LITM", "SNGX", "GE", "MULN", "CGNX", "ML", "MDRR", "PR", "VAL", "EBF", "MTP", "CYCN", "XELA", "ENVX", "EQT", "GLMD", "DCFC", "POAI", "BNOX", "FRLN", "CINC", "NISN", "REFR", "CAPR", "SYRS", "ALPP", "RETO", "VISL", "GNLN", "JXJT", "SAFE", "EZFL", "IDRA", "CRESY", "IMPP", "ZEV", "EAST", "BIOC", "IFBD", "STAR", "AWH", "TNXP", "WORX", "VLON", "PSTV", "SFT", "AGRX", "MBIO", "APRE", "GAME", "VERB", "CFRX", "BLBX", "COMS", "RKDA", "WISH", "NXTP", "TR", "ARVL", "EJH", "MEIP", "ENSC", "NYMT", "PNTM", "ASNS", "AKAN", "RDFN", "GMBL", "VYNE", "MNST", "LCAA", "FRSX", "CRBP", "ATNX", "OIG", "REED", "OUST", "ALLR", "NAOV", "KRBP", "ICMB", "XOS", "GFAI", "GNUS", "BGXX", "FTFT", "AMST", "FCUV", "VBIV", "BIIB", "MINM", "CLXT", "DGLY", "MRKR");
        Set<String> lessVolStockSet = Sets.newHashSet("CVCO", "BBU", "NVCN", "SLAC", "FNLC", "CVCY", "CVEO", "BSBK", "MSBI", "NVEE", "SFE", "BSAQ", "AGMH", "JUN", "SGC", "VLGEA", "NECB", "STRE", "JVA", "BFC", "SGU", "APAC", "OCFCP", "SLDB", "SII", "HCVI", "APCA", "BBDO", "SLGL", "BSET", "MBCN", "RICK", "SCUA", "VMAR", "GIFI", "SCWO", "SLN", "PKOH", "FFBW", "BBGI", "LGST", "CELC", "ECBK", "IONR", "LGVC", "MBIN", "CVLY", "KMDA", "VMCA", "PCCT", "FNVT", "JAQC", "FNWB", "KAI", "FNWD", "CENT", "XBIT", "IXAQ", "JRSH", "TGAN", "NEPH", "SRT", "SLNG", "SSU", "BRD", "RZLT", "VMGA", "NEOV", "BRT", "STG", "STN", "APMI", "SDAC", "IOSP", "NETC", "GIPR", "BSRR", "MSSA", "IXHL", "KFS", "HUDA", "CNFR", "BVH", "SLRX", "GRCY", "SXI", "BBSI", "BWB", "BWC", "CNGL", "IGIC", "RRBI", "FFNW", "GABC", "BBUC", "BSVN", "BYN", "MBTC", "HLVX", "SLVR", "APTM", "MSVB", "SDHY", "MBWM", "PLBC", "BATRA", "CNNB", "VECT", "ELDN", "GAIA", "USCB", "PTRS", "QFTA", "LTRPB", "USAP", "KOP", "USAU", "APXI", "PCTI", "KVHI", "CAC", "PCSA", "USCT", "JJSF", "IPAR", "TCX", "GAMC", "CCB", "CCD", "CCM", "PTWO", "CWCO", "GANX", "SMBK", "BTBD", "RANI", "MCAA", "MCAG", "MCAC", "MCAF", "IGTA", "MTAC", "SMAP", "UBFO", "MTAL", "SMBC", "TGR", "MCBS", "TGVC", "BCBP", "KWE", "CGA", "USIO", "PCYG", "QOMO", "KWR", "CGO", "NWFL", "PCYO", "CFFS", "MCBC", "RJAC", "CFFE", "CHE", "FORA", "CIA", "CIH", "PLMI", "CNXN", "PLOW", "PLPC", "CFIV", "GRVY", "KEQU", "SMIH", "PARAA", "FORR", "FGBI", "GATE", "USNA", "SMHI", "MCHX", "PUCK", "TNC", "VERY", "USPH", "VNCE", "CNF", "EUCR", "SMLR", "RAYA", "SMMF", "CFMS", "COE", "NFNT", "XTLB", "BTMD", "SMLP", "CPK", "TRC", "CPZ", "LBC", "DISA", "TSQ", "NWPX", "JBSS", "BCML", "LCW", "THCP", "ELYM", "CSR", "HVBC", "CFSB", "BCOW", "CODA", "CTG", "COEP", "GSBC", "THFF", "ORIA", "MLAB", "MLAC", "COFS", "FXNC", "NODK", "IPVF", "FGMC", "IPVI", "CVV", "GBBK", "LHC", "PDLB", "BCSA", "IPWR", "PULM", "SMTI", "SEDA", "MCRI", "SVFB", "DAIO", "LIBY", "UTAA", "DALS", "LMB", "SVII", "MCVT", "NFYS", "PDOT", "LND", "EDRY", "LNN", "KNSW", "EMCG", "CXAC", "DJCO", "TYRA", "COOL", "SELF", "SVNA", "ORRF", "DRRX", "KFFB", "PMGM", "BLNG", "RBKB", "DCO", "CORS", "GBLI", "GBNY", "DDI", "UFI", "LINK", "GSQB", "CXDO", "JCTCF", "PUYI", "YORW", "NXGL", "WRAC", "UHT", "AC", "BLTE", "YOTA", "AP", "WINV", "AZ", "LION", "AIRG", "RSSS", "BLUA", "BQ", "UTMD", "SNEX", "COYA", "SNES", "GBRG", "SVVC", "ULH", "AACI", "IQMD", "MLVF", "FC", "UNB", "LIVB", "UNF", "LIVE", "GB", "LRFC", "PVBC", "MDGS", "UPH", "HY", "AZYO", "UTSI", "HNNA", "PMTS", "IH", "PEBO", "ARIZ", "NPAB", "CPAC", "KA", "KE", "EVGR", "NPCE", "UTL", "MG", "MDV", "MEC", "LAKE", "BMAC", "NH", "WRLD", "SNPO", "NL", "AROW", "SNSE", "HWEL", "HFBL", "WALD", "GTAC", "SFBC", "MURF", "NGVC", "HFFG", "ARRW", "OSIS", "RCAC", "CPHC", "RM", "RKTA", "SP", "HNVR", "ARTW", "EVOJ", "TC", "MKL", "ARTE", "MDWD", "PNAC", "RCFA", "LATG", "HWKN", "MDWT", "MLR", "UI", "PEPL", "UK", "HWKZ", "CPLP", "TZOO", "PERF", "WF", "WAVE", "EVTV", "WAVC", "ARYD", "MDXH", "ARYE", "SWKH", "GTIM", "MOR", "OBNK", "MPX", "EAC", "IRAA", "PESI", "WAVS", "ENER", "ZT", "MRM", "JUGG", "RCLF", "CHAA", "VOXR", "TRDA", "MSC", "MSB", "NYAX", "TRCA", "VEL", "MVBF", "CHCT", "EDN", "CHCO", "CPSS", "CPSI", "TISI", "TACT", "CPTK", "GLBZ", "CHEA", "SWSS", "VII", "WSBF", "BEDU", "ASCA", "LBBB", "LSBK", "HOFT", "IRIX", "LSEA", "SOHO", "FICV", "NHIC", "JMAC", "PFBC", "CHMG", "EML", "EMP", "VPCB", "FCNCA", "TANH", "MEKA", "VPG", "IROQ", "IRON", "KELYB", "TRON", "GLLI", "IRRX", "DCBO", "OCAX", "ERO", "ZTEK", "CHRA", "TARO", "EFHT", "TRTL", "ESE", "ESM", "ESQ", "OTEC", "SOPH", "TRST", "TARA", "NEU", "PFIS", "VVX", "NGC", "NGS", "EVO", "LBPH", "NYXH", "ASRV", "NIC", "FINW", "SOTK", "LSTA", "GLST", "FRBA", "QIPT", "FRBN", "DTOC", "FACT", "BVXV", "SGII", "BNIX", "NMG", "GDEV", "KYCH", "FISI", "SGHL", "POCI", "NNI", "NOA", "PFTA", "PWUP", "NPO", "ASYS", "TSAT", "RLYB", "RDIB", "JMSB", "FAT", "QRHC", "NRC", "NRP", "BNNR", "NRT", "BWAC", "TSBK", "WEL", "GDNR", "MNPR", "BWAY", "BWAQ", "FRLA", "BNRE", "IBCP", "FET", "BFAC", "TBCP", "EFXT", "GURE", "ATAK", "FRON", "UEIC", "NVX", "MNSB", "FANH", "WHG", "CZFS", "SPCM", "DLHC", "FGF", "IBEX", "ATAQ", "MNTX", "SPFI", "ACAX", "BWFG", "RBCAA", "ACAH", "CIGI", "ACAB", "GDST", "RMBI", "OLIT", "WTBA", "RMCF", "ACBA", "MNTN", "ATEK", "GMFI", "FRST", "CIIG", "WMK", "TBLD", "SPKB", "RDVT", "FATP", "MFIN", "DLNG", "GMGI", "CZNC", "BFIN", "SPLP", "UVSP", "PORT", "LCFY", "BWMN", "HPLT", "XFIN", "NRAC", "CRAI", "ISSC", "BWMX", "OBT", "ATLX", "ATLO", "ATLC", "OCN", "ISTR", "WTMA", "DUET", "ODV", "CACC", "EGGF", "BELFB", "FSV", "ATNI", "POWL", "CABO", "LCNB", "CREG", "CREC", "MOBV", "SHAP", "BOCN", "ACNT", "CADL", "SHBI", "ALAR", "MODD", "CIVB", "MODV", "GVCI", "BFST", "FSBW", "MOFG", "LTRN", "FSBC", "ACRV", "HHGC", "FZT", "FSEA", "VIAO", "DUOT", "DUNE", "GEEX", "MOGU", "WLDN", "CRMT", "CALB", "CALA", "HHLA", "PGRW", "PGRU", "TKNO", "FSFG", "LCUT", "CALT", "GEHI", "MOLN", "OPA", "ACXP", "ALIM", "OPT", "OPY", "PGSS", "GAQ", "ITAQ", "NAMS", "FBIZ", "TCBC", "OSA", "ICCH", "TCBK", "XOMA", "OSI", "UFCS", "ITCB", "VIGL", "UNTY", "GENQ", "RENE", "CARE", "CASS", "TCBX", "TCBS", "GEG", "CASI", "OMEX", "GET", "PPIH", "XGN", "FBMS", "ALPA", "TCFC", "ALOR", "CATC", "NATR", "GFX", "GEOS", "CRVL", "XIN", "ICFI", "KINS", "SHUA", "GHG", "GHM", "CRWS", "ADAG", "BOTJ", "AUBN", "MGEE", "GIA", "GIC", "ALSA", "ALRS", "RVSB", "ALRN", "XLO", "FSRX", "MOVE", "ALTI", "RNGR", "REVE", "ADEX", "PHAR", "ADER", "VABK", "FSTR", "REUN", "ALVO", "ICLK", "GNE", "OVBC", "AUID", "LDHA", "WDFC", "VACC", "EPSN", "ICNC", "PAC", "PHCF", "DECA", "ITRN", "OMQS", "TCOA", "PCB", "GRC", "LUMO", "CBAN", "GSD", "PDS", "CSBR", "PET", "GWAV", "XPAX", "PFX", "ADOC", "PPYA", "PGC", "AMAO", "VALN", "KRNL", "BGSF", "TLGY", "MGRC", "RFAC", "CBFV", "TLGA", "VRME", "AURA", "FCAP", "GNTY", "OVLY", "FCBC", "GNSS", "PKE", "ADSE", "HIFS", "GNTA", "FCCO", "EQBK", "FTEV", "CSLM", "PMN", "DNAB", "DNAD", "VRTS", "MGYR", "LUXH", "ADUS", "CBNK", "VJET", "FTII", "DERM", "KRUS", "RFIL", "FTHM", "FKWL", "HBB", "HBT", "HCI", "IDBA", "CSTA", "CBRG", "NBRV", "HIPO", "CSTR", "ONEW", "BHAC", "RWOD", "AMNB", "AVAC", "AMOV", "AMOT", "LVAC", "GWRS", "SRDX", "BPRN", "FTPA", "NSTS", "ZEUS", "GFOR", "LMNR", "SRCE", "SIRE", "NBST", "PHYT", "MPRA", "ROCL", "ROCG", "AEAE", "MYFW", "CSWI", "AMSF", "WMPN", "LMST", "AMTB", "SAGA", "FCRD", "VSAC", "SAFT", "PIAI", "HMA", "QCRH", "GOGN", "HAIA", "SZZL", "VSEC", "FLFV", "LEGH", "FLIC", "SAMA", "BYNO", "SAMG", "TURN", "HQI", "CTBI", "IMAQ", "MYNA", "CCAP", "OFED", "CCAI", "HAPP", "SANG", "LEJU", "DFFN", "AENZ", "BYTS", "LVRA", "ONYX", "GGAA", "MYSZ", "VBNK", "MHUA", "RGCO", "PINE", "FDBC", "SATL", "HYW", "ROSE", "AVTE", "ROSS", "OFLX", "TMKR", "QLI", "ANEB", "WNEB", "PIPR", "VBOC", "VSTA", "LVWR", "CCLD", "PRBM", "HAYN", "GXII", "CCLP", "CCNE", "ANIP", "ANIK", "AEYE", "PACI", "ERES", "IMRX", "IBA", "QUIK", "UPTD", "IVCB", "IVCP", "LNKB", "NCRA", "SSBI", "SSBK", "ERIE", "CCTS", "HBCP", "PRLH", "TEDU", "GPAC", "NCSM", "ANPC", "ERNA", "AFAR", "CLGN", "FMAO", "AFBI", "NTWK", "LFAC", "ANTE", "PANA", "LNSR", "FUSN", "PRPC", "OOMA", "CLIN", "WFCF", "SSIC", "FMBH", "ANTX", "PROC", "SBFG", "PROF", "FUSB", "HSKA", "TELA", "DOOO", "KTCC", "PRSR", "TENK", "PRTH", "PRTC", "RPHM", "OXAC", "RAM", "PATI", "FITBI", "HSON", "INBK", "IPX", "RBB", "CLPR", "ANZU", "INAQ", "CLRC", "CDAQ", "IESC", "RDI", "INDT", "BRAG", "NUBI", "BRAC", "BIOX", "ACR", "BIOS", "INCR", "IMKTA", "REX", "TETE", "TETC", "CLST", "FMNB", "RGF", "IVA", "BACA", "MRDB", "MACA", "DGICA", "HSTM", "BITE", "AGM", "AGX", "SSTI", "AWRE", "MIRO", "LOCC", "AFRI", "BRFH", "INKA", "BREZ", "AIB", "AIH", "AIU", "MITA", "VLAT", "BAFN", "BRIV", "SKGR", "CULP", "CULL", "ESAC", "RMR", "KTRA", "ALG", "AFTR", "AOGO", "LFVN", "PBBK", "AMK", "ROC", "VTRU", "DXPE", "ESCA", "FEDU", "LFUS", "MIXT", "OPBK", "BRLI", "TWCB", "PBFS", "AQU", "BROG", "ESGR", "RTC", "CDRO", "NDRA", "CMCT", "CMCM", "DKDCA", "OXUS", "HTBI", "INTE", "VCXA", "VCXB", "HCCI", "MAQC", "AXAC", "DXYN", "GYRO", "FENG", "SCAQ", "QRTEB", "OPHC", "GHRS", "TWIN", "AGAC", "JHX", "NUWE", "FATBB", "FNCB", "PSPC", "NUZE", "IFIN", "ESOA", "EBMT", "OPNT", "SKYA", "TWNI", "ESSA", "OPOF", "TWLV", "STIX", "AGGR", "OHAA", "PKBK", "TWOA", "SAL", "TFPM", "FEXD", "NVAC", "IOBT", "SBT", "IOAC", "EBTC", "TOAC", "SCM", "DHCA", "SCL");

        int riseTimes = 3;
        Map<String, List<ContinueRise>> buyDateToListMap = Maps.newHashMap();
        Map<String, List<ContinueRise>> stockToListMap = Maps.newHashMap();

        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(mergePath);
        Map<String, String> bollFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "mergeBoll/");
        double amount = 10000;

        Set<String> stockSet = dailyFileMap.keySet();
        BaseUtils.filterStock(stockSet);

        for (String stock : stockSet) {
            if (invalidStockSet.contains(stock)) {
                continue;
            }
            if (lessVolStockSet.contains(stock)) {
                continue;
            }
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }

            String filePath = dailyFileMap.get(stock);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(filePath, beforeYear, afterYear);

            String bollFilePath = bollFileMap.get(stock);
            List<BOLL> bolls = BaseUtils.readBollFile(bollFilePath, beforeYear, afterYear);

            List<ContinueRise> resultList = Lists.newLinkedList();
            boolean reset = true;
            List<StockKLine> riseList = Lists.newArrayList();
            double allVol = 0d;
            for (int i = stockKLines.size() - 2; i >= 3; ) {
                StockKLine prev = stockKLines.get(i + 1);
                StockKLine lastLastKLine = stockKLines.get(i);
                StockKLine lastKLine = stockKLines.get(i - 1);
                StockKLine currKLine = stockKLines.get(i - 2);
                StockKLine nextKLine = null;
                if (i > 2) {
                    nextKLine = stockKLines.get(i - 3);
                }
                BOLL currBoll = bolls.get(i - 2);

                double lastLastClose = lastLastKLine.getClose();
                double lastClose = lastKLine.getClose();
                double currClose = currKLine.getClose();
                double open = currKLine.getOpen();
                allVol += lastLastKLine.getVolume().doubleValue();

                if (open < 7) {
                    reset = true;
                    i--;
                    continue;
                }

                if (reset) {
                    riseList = Lists.newArrayList();
                }

                if (lastLastKLine.getDate().equals("03/21/2022")) {
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
                        continueRise.setStock(stock);
                        continueRise.setRiseList(riseList);
                        continueRise.setPrev(prev);
                        continueRise.setCurrBoll(currBoll);
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

            double avgVol = allVol / (stockKLines.size() - 3);
            if (avgVol < 100000) {
                //                System.out.println(stock + " vol invalid");
                continue;
            }

            List<ContinueRise> filter1List = Lists.newArrayList();
            List<ContinueRise> filter2List = Lists.newArrayList();
            List<ContinueRise> filter3List = Lists.newArrayList();
            List<ContinueRise> filter4List = Lists.newArrayList();
            List<ContinueRise> filter5List = Lists.newArrayList();
            List<ContinueRise> afterFilter = Lists.newArrayList();
            for (ContinueRise continueRise : resultList) {
                if (false) {
                } else if (filter1(continueRise)) {
                    filter1List.add(continueRise);
                    continue;
//                } else if (filter2(continueRise)) {
//                    filter2List.add(continueRise);
//                    continue;
                } else if (filter3(continueRise)) {
                    filter3List.add(continueRise);
                    continue;
                } else if (filter4(continueRise)) {
                    filter4List.add(continueRise);
                    continue;
                } else if (filter5(continueRise)) {
                    filter5List.add(continueRise);
                    continue;
//                } else if (filter6(continueRise)) {
//                    filter5List.add(continueRise);
//                    continue;
//                } else if (filter7(continueRise)) {
//                    filter5List.add(continueRise);
//                    continue;
                } else {
                    String buyDate = continueRise.getBuyDate();
                    if (!buyDateToListMap.containsKey(buyDate)) {
                        buyDateToListMap.put(buyDate, Lists.newArrayList());
                    }
                    buyDateToListMap.get(buyDate).add(continueRise);
                    afterFilter.add(continueRise);
                }
            }

            //            insertFilter(filter1List, buyDateToListMap, afterFilter);
            //            insertFilter(filter2List, buyDateToListMap, afterFilter);
            //            insertFilter(filter3List, buyDateToListMap, afterFilter);
            //            insertFilter(filter4List, buyDateToListMap, afterFilter);
            //            insertFilter(filter5List, buyDateToListMap, afterFilter);

            stockToListMap.put(stock, resultList);
            //            System.out.println(stock + " finish");
        }

        // 2023年的交易日期
        List<StockKLine> dateKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", 2023, 2022);
        List<String> dateList = dateKLines.stream().map(StockKLine::getDate).collect(Collectors.toList());
        Collections.reverse(dateList);

        List<Double> ratioList = Lists.newArrayList();
        for (int i = 0; i < dateList.size(); i++) {
            String date = dateList.get(i);
            if (date.equals("02/02/2023")) {
//                System.out.println();
            }
            int currDateInt = BaseUtils.dateToInt(date);

            List<ContinueRise> canBuyList = buyDateToListMap.get(date);
            if (CollectionUtils.isEmpty(canBuyList)) {
                System.out.println(date + " has no stock to buy");
                continue;
            }
            if (canBuyList.size() == 1) {
                System.out.println(date + " has only one stock to buy");
                continue;
            }
            //            canBuyList = canBuyList.stream().filter(b -> {
            //                StockKLine kLine = b.getRiseList().get(2);
            //                boolean priceFlag = kLine.getClose() > 7;
            //                boolean volFlag = kLine.getVolume().doubleValue() > 200000;
            //                return priceFlag && volFlag;
            //            }).collect(Collectors.toList());
            //            if (CollectionUtils.isEmpty(canBuyList)) {
            //                System.out.println(date + " has no stock to buy");
            //                continue;
            //            }

            double maxSuccessRatio = Double.MIN_VALUE;
            double maxAvgGain = Double.MIN_VALUE;
            String maxStock = null;
            ContinueRise maxBuy = null;
            int maxCount = 0;
            for (ContinueRise canBuy : canBuyList) {
                String stock = canBuy.getStock();
                if (filter1(canBuy) || filter2(canBuy) || filter3(canBuy) || filter4(canBuy) || filter5(canBuy) || filter6(canBuy)||filter7(canBuy) || filter8(canBuy)) {
//                    continue;
                }
                if (stock.equals("HOMB") || stock.equals("CACI")) {
//                    System.out.println();
                }

                List<ContinueRise> riseList = stockToListMap.get(stock);
                List<ContinueRise> hisRiseList = riseList.stream().filter(r -> {
                    int hisBuyDateInt = BaseUtils.dateToInt(r.getBuyDate());
                    //                    return BaseUtils.dateToInt("01/01/2019") < hisBuyDateInt && hisBuyDateInt < currDateInt;
                    return hisBuyDateInt < currDateInt;
                }).collect(Collectors.toList());

                List<ContinueRise> filter1List = Lists.newArrayList();
                List<ContinueRise> filter2List = Lists.newArrayList();
                List<ContinueRise> filter3List = Lists.newArrayList();
                List<ContinueRise> filter4List = Lists.newArrayList();
                List<ContinueRise> filter5List = Lists.newArrayList();
                List<ContinueRise> filter6List = Lists.newArrayList();
                List<ContinueRise> filter7List = Lists.newArrayList();
                List<ContinueRise> afterFilter = Lists.newArrayList();
                for (ContinueRise continueRise : hisRiseList) {
                    if (false) {
                    } else if (filter1(continueRise)) {
                        filter1List.add(continueRise);
                        continue;
                    } else if (filter2(continueRise)) {
                        filter2List.add(continueRise);
                        continue;
                    } else if (filter3(continueRise)) {
                        filter3List.add(continueRise);
                        continue;
                    } else if (filter4(continueRise)) {
                        filter4List.add(continueRise);
                        continue;
                    } else if (filter5(continueRise)) {
                        filter5List.add(continueRise);
                        continue;
                    } else if (filter6(continueRise)) {
                        filter6List.add(continueRise);
                        continue;
                    } else if (filter7(continueRise)) {
                        filter7List.add(continueRise);
                        continue;
                    } else {
                        afterFilter.add(continueRise);
                    }
                }
                insertFilter(filter1List, afterFilter);
                insertFilter(filter2List, afterFilter);
                insertFilter(filter3List, afterFilter);
                insertFilter(filter4List, afterFilter);
                insertFilter(filter5List, afterFilter);
//                                insertFilter(filter6List, afterFilter);
//                                insertFilter(filter7List, afterFilter);

                List<ContinueRise> computeList = hisRiseList;
                List<ContinueRise> successList = computeList.stream().filter(h -> h.getResult(riseTimes)).collect(Collectors.toList());
                if (successList.size() <= 1) {
                    continue;
                }
                int successCount = successList.size();
                double successRatio = (double) successCount / (double) computeList.size();
                double successAvgGain = computeList.stream().collect(Collectors.averagingDouble(c -> c.getRatio()));
                //                System.out.println(stock + " " + successRatio + "\n" + successList);
                if (successRatio> 0.7 && (successRatio > maxSuccessRatio || successRatio == maxSuccessRatio && successAvgGain > maxAvgGain)) {
//                if (successRatio == 1.0d || successRatio == maxSuccessRatio && successAvgGain > maxAvgGain) {
//                if (successRatio > maxSuccessRatio) {
                    maxSuccessRatio = successRatio;
                    maxAvgGain = successAvgGain;
                    maxStock = stock;
                    maxBuy = canBuy;
                    maxCount++;
                }
                //                System.out.println(maxSuccessRatio);

                //                                Double avgRatio = afterFilter.stream().map(ContinueRise::getRatio).collect(Collectors.averagingDouble(d -> d));
                //                                if (avgRatio > maxSuccessRatio) {
                //                                    maxSuccessRatio = avgRatio;
                //                                    maxStock = stock;
                //                                    maxBuy = canBuy;
                //                                }
            }

            if (maxBuy == null) {
                System.out.println(date + " has no stock to max buy");
                continue;
            }
            List<StockKLine> riseList = maxBuy.getRiseList();
            StockKLine next = riseList.get(3);
            StockKLine curr = riseList.get(2);
            double nextClose = next.getClose();
            double currClose = curr.getClose();

            amount = amount / currClose * nextClose;
            System.out.println(maxBuy + "\t" + amount);
            ratioList.add(nextClose / currClose);
        }
        System.out.println("avgRatio" + ratioList.stream().collect(Collectors.averagingDouble(d -> d)));
    }

    public static void insertFilter(List<ContinueRise> filterList, List<ContinueRise> afterFilter) {
        if (computeFilter(filterList)) {
            afterFilter.addAll(filterList);
        }
    }

    public static void insertFilter(List<ContinueRise> filterList, Map<String, List<ContinueRise>> buyDateToListMap, List<ContinueRise> afterFilter) {
        if (computeFilter(filterList)) {
            for (ContinueRise continueRise : filterList) {
                String buyDate = continueRise.getBuyDate();
                if (!buyDateToListMap.containsKey(buyDate)) {
                    buyDateToListMap.put(buyDate, Lists.newArrayList());
                }
                buyDateToListMap.get(buyDate).add(continueRise);
            }
            afterFilter.addAll(filterList);
        }
    }

    public static boolean computeFilter(List<ContinueRise> filterList) {
        long success = filterList.stream().filter(f -> f.getRiseList().get(3) != null && f.getResult(3)).count();
        long fail = filterList.stream().filter(f -> f.getRiseList().get(3) != null && !f.getResult(3)).count();
        return success > fail;
    }
}
