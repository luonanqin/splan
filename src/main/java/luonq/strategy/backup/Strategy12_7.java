package luonq.strategy.backup;

import bean.BOLL;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
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
 * <p>
 * 过滤：
 * 1.这三天成交量连续递减
 * 2.这三天成交量先减后增 且 第三天比第一天少
 * 3.这三天成交量先增后减
 * <p>
 * 买入：第三天收盘买入
 * 计算：第四天收盘的涨跌情况，第四天收盘大于第三天收盘则为盈利，否则为亏损
 * <p>
 * 收益14908 avgRatio1.0356937691980534
 * <p>
 */
public class Strategy12_7 {

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
        private String stock;
        private List<StockKLine> riseList;
        private StockKLine prev;
        private BOLL currBoll;

        public String getFirstDate() {
            return riseList.get(0).getDate();
        }

        public String getBuyDate() {
            return riseList.get(2).getDate();
        }

        public boolean getResult(int n) {
            if (n + 1 > riseList.size()) {
                return false;
            }

            StockKLine last = riseList.get(n);
            StockKLine prev = riseList.get(n - 1);
            return last.getClose() > prev.getClose();
        }

        public double getRatio() {
            StockKLine last = riseList.get(3);
            StockKLine prev = riseList.get(2 - 1);
            double lastClose = last.getClose();
            double prevClose = prev.getClose();
            return lastClose / prevClose;
        }

        public String toString() {
            String closeDetail = riseList.stream().map(k -> String.valueOf(k.getClose())).collect(Collectors.joining("\t"));
            String volDetail = riseList.stream().map(k -> String.valueOf(k.getVolume())).collect(Collectors.joining("\t"));
            return getBuyDate() + "\tstock=" + stock + "\t, close=\t" + closeDetail + "\tvolume=" + volDetail + ",\t" + getResult(riseList.size() - 1);
        }
    }

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
                if (successRatio> 0.8 && (successRatio > maxSuccessRatio || successRatio == maxSuccessRatio && successAvgGain > maxAvgGain)) {
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

        return vol1 > vol2 && vol2 < vol3;
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

    // 第三天成交量超过第二天成交量两倍以上
    public static boolean filter4(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine1 = riseList.get(0);
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);

        double vol1 = kLine1.getVolume().doubleValue();
        double vol2 = kLine2.getVolume().doubleValue();
        double vol3 = kLine3.getVolume().doubleValue();

        return vol3 > (vol2 * 2);
    }

    // 三天的平均涨幅小于2
    public static boolean filter5(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine1 = riseList.get(0);
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);
        StockKLine prev = continueRise.getPrev();

        double ratio1 = kLine1.getClose() / prev.getClose() - 1;
        double ratio2 = kLine2.getClose() / kLine1.getClose() - 1;
        double ratio3 = kLine3.getClose() / kLine2.getClose() - 1;

        return (ratio1 + ratio2 + ratio3) / 3 < 0.02d;
    }

    // 第三天收盘超上轨
    public static boolean filter6(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine3 = riseList.get(2);
        StockKLine prev = continueRise.getPrev();
        BOLL currBoll = continueRise.getCurrBoll();

        return kLine3.getClose() > currBoll.getUp();
    }

    // 第三天长上影线
    public static boolean filter7(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine3 = riseList.get(2);
        double high = kLine3.getHigh();
        double close = kLine3.getClose();
        double low = kLine3.getLow();
        double ratio = (high - close) / (high - low);

        return ratio > 0.3;
    }

    // 第三天最高低于第二天最高
    public static boolean filter8(ContinueRise continueRise) {
        List<StockKLine> riseList = continueRise.getRiseList();
        StockKLine kLine2 = riseList.get(1);
        StockKLine kLine3 = riseList.get(2);
        double high_2 = kLine2.getHigh();
        double high_3 = kLine3.getHigh();

        return high_2 > high_3;
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
