package luonq.polygon;

import bean.Trade;
import bean.TradeResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/5/14.
 */
@Slf4j
public class GetHistoricalOpenFirstTradeTemp {

    public static boolean retry = false;

    public static void getData() throws Exception {
        String api = "https://api.polygon.io/v3/trades/";

        int threadCount = 100;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        ThreadPoolExecutor cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        BlockingQueue<HttpClient> clients = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            clients.offer(new HttpClient());
        }

        Set<String> stocks = Sets.newHashSet("FRSH", "ATEN", "TWST", "FNKO", "ATEC", "ACCO", "NVCR", "TSLA", "OLMA", "WLK", "BBY", "FARO", "NMRK", "SLAB", "BCC", "WLY", "KLXE", "WMB", "AKTS", "POOL", "SEM", "EXAI", "WMS", "OPTN", "ACDC", "OLLI", "SES", "BDC", "A", "B", "D", "TBLA", "AGNC", "FLO", "J", "L", "FATE", "FLS", "M", "EGBN", "ACET", "ATGE", "SLCA", "CECO", "S", "U", "V", "BDX", "ACEL", "SFL", "Z", "EOSE", "FMC", "BJRI", "NVDA", "URGN", "BEN", "BBAR", "WTFC", "STRC", "BBAI", "WOR", "FNB", "FAST", "CMTL", "FND", "BSBR", "FNF", "OLPX", "POST", "WPC", "SHC", "IBOC", "FNV", "GDYN", "WPM", "NINE", "ACGL", "JWN", "SHW", "PBYI", "MSCI", "FOR", "HCSG", "BGS", "ACHR", "EXEL", "CVGW", "QRVO", "BHE", "FPI", "STTK", "JXN", "TBPH", "SPNT", "PGEN", "ACIW", "BFLY", "AXSM", "MSFT", "BSFC", "SJM", "SJT", "SJW", "EXFY", "UNCY", "BIO", "CRBG", "WSM", "LXRX", "LCID", "AXTI", "WSO", "NEGG", "WSR", "WST", "AXTA", "AGRO", "STVN", "SKY", "SKX", "SLB", "FRT", "WTI", "EGHT", "SLG", "RMNI", "SLM", "QNST", "SHAK", "NABL", "BKD", "ODP", "SLS", "CRBU", "BKH", "MSGE", "SCVL", "FFBC", "FSM", "CVLT", "SPOT", "CRDO", "BKR", "SMG", "OEC", "BKU", "CRDF", "BSIG", "POWL", "JAMF", "POWI", "ACLS", "KURA", "FTI", "BLD", "UNFI", "ZYME", "IBRX", "CADE", "FTS", "SNA", "WVE", "IONS", "ATOS", "IONQ", "SPSC", "ATOM", "PTCT", "OFG", "ACMR", "SNV", "TSVT", "JANX", "BMO", "SPRC", "SPRB", "SOI", "SOL", "SON", "WWW", "NRDS", "XFOR", "BBIO", "RIGL", "OGS", "NRDY", "IBTX", "ODFL", "MODG", "SPB", "BNS", "PTEN", "NVOS", "MODN", "VMEO", "SPG", "SPR", "SPT", "BOC", "NRGV", "BOH", "SHEL", "ACON", "SHCR", "GIII", "BOX", "OII", "SPTN", "PXLW", "OIS", "YELP", "ATRA", "CRGY", "FFIN", "SRE", "EXPD", "EXPE", "EXPI", "FFIV", "PTGX", "NRIX", "FSCO", "ATSG", "LPLA", "SPWR", "ALDX", "SSB", "LTRN", "OKE", "APLT", "NEOG", "LTRX", "SSP", "BRC", "APLE", "CEPU", "APLD", "FFIC", "ALEX", "VIAV", "STC", "VUZI", "CERE", "NVST", "DUOL", "OLK", "ATUS", "OLO", "ALEC", "STZ", "SLQT", "NVRO", "CNDT", "OMC", "SUI", "JRVR", "PGNY", "BSY", "SUN", "EPAC", "HUBG", "WTTR", "EPAM", "HUBS", "CERS", "KEY", "CALM", "BTU", "ONB", "ALGT", "DDOG", "VICR", "ALGN", "KIDS", "ALGM", "BUD", "EXTR", "GRBK", "HLTH", "CRMD", "PPBI", "SWI", "JNPR", "SWK", "KGC", "CRNT", "SWN", "APPS", "GRAB", "ATXS", "HYLN", "IOVA", "APPN", "CALX", "IKNA", "RIOT", "ATXI", "CRNC", "MSTR", "CROX", "ALIT", "OPI", "PGTI", "SXT", "JAZZ", "PTLO", "SYK", "SYM", "YETI", "KIM", "SYY", "TOWN", "SHLS", "BXP", "SYNA", "ORA", "NAPA", "ORC", "FBIO", "PTON", "ORN", "ALKT", "AHCO", "ALKS", "SHOP", "TCBI", "SHOO", "PTPI", "SDGR", "RVNC", "FWRD", "LPRO", "OMCL", "OSG", "ALLY", "OSK", "FWRG", "GCI", "APTV", "ALLO", "BZH", "OSW", "KKR", "ITCI", "ALLK", "BOOM", "SLVM", "RACE", "GCT", "MKFG", "LPSN", "RVMD", "XEL", "CARS", "RIVN", "CARG", "CRSR", "CARA", "CRSP", "GDS", "TGLS", "OMGA", "RENT", "YEXT", "KMI", "ALNY", "GEF", "BATRK", "OMER", "GEO", "FSLR", "KMT", "ELAN", "KMX", "LYFT", "PLAB", "GENI", "FSLY", "FBMS", "HUMA", "GFF", "REPL", "OVV", "CRUS", "BORR", "GFS", "CASY", "PCRX", "CNMD", "XHR", "IGMS", "GGG", "BKKT", "LBRDK", "KOS", "VECO", "GGR", "ITGR", "ETSY", "PLBY", "ALPN", "MXCT", "PLCE", "CATY", "CNNE", "TAL", "TAP", "NAVI", "OXM", "ICHR", "NNOX", "OXY", "PTVE", "NRXP", "TBI", "TXRH", "PCTY", "MGEE", "UWMC", "CAE", "CAG", "GIL", "CAL", "GIS", "CAN", "FFWM", "CAT", "ALRM", "ADBE", "RVSN", "KRC", "GRND", "KRG", "OZK", "GETY", "SUPN", "SUPV", "GRNT", "CBT", "CBU", "UBER", "ADEA", "TDS", "TDW", "ETWO", "CCI", "CCK", "KSS", "CCJ", "WDAY", "CCO", "CCS", "NWBI", "ALTR", "KTB", "ALTO", "TEL", "TER", "CWEN", "ICLR", "SHYF", "GRPN", "PHAT", "GLT", "TFC", "TGTX", "GLW", "CNSL", "VIRT", "PYCR", "FOLD", "CEG", "NFBK", "TFX", "HYZN", "SMAR", "SURG", "BCAN", "RAMP", "GEVO", "TGI", "XOM", "ALVR", "BTBT", "AUGX", "TTMI", "WHLR", "VITL", "VREX", "REXR", "CFR", "THC", "MKSI", "MTDR", "GNW", "RAPT", "XPO", "THS", "LYRA", "ELME", "TCMD", "SMCI", "DVAX", "SDRL", "UFPI", "VRDN", "BKSY", "IPGP", "CFFN", "QBTS", "HDSN", "PAG", "GPC", "ALXO", "VIST", "PAM", "EYEN", "CHD", "ITRI", "CHH", "RARE", "REZI", "MKTX", "CHK", "CNXA", "GRTS", "GPS", "GRTX", "PBA", "CHX", "PBF", "PBI", "PBH", "XRX", "BGNE", "CIO", "TCON", "TCOM", "TKC", "REYN", "TPIC", "PCH", "PCG", "TKR", "PCT", "MGNX", "KZR", "FOSL", "CSCO", "LUMN", "PDM", "GSK", "DMTK", "JBLU", "FORM", "TMC", "PEB", "TME", "RNLX", "WULF", "MGNI", "LULU", "GATO", "TMO", "HUYA", "CLB", "PEN", "CLH", "GTN", "SIBN", "FOUR", "FGEN", "GTX", "CLS", "CLX", "TNK", "PFG", "TNL", "CMA", "CMC", "CME", "VERV", "PFS", "VERX", "VNDA", "SQSP", "LUNG", "DINO", "CMP", "TOL", "ZIMV", "AUPH", "TOP", "CFLT", "MGPI", "ADNT", "MCHP", "PGR", "CNK", "AMAT", "CNM", "KALV", "CSGS", "CNP", "PDCO", "TPC", "CSGP", "CNQ", "TPG", "CNX", "KRNT", "VNET", "KRNY", "KAMN", "FOXF", "TPR", "COF", "BCLI", "ZION", "COP", "GWW", "PLRX", "LAB", "LAD", "PII", "AMBC", "CPB", "AMBA", "CPE", "ADPT", "CPG", "LBTYK", "TTWO", "GXO", "XPEV", "UBSI", "LAZ", "AMCR", "CPT", "CSIQ", "TRI", "GNTX", "XPER", "TRP", "TRS", "NSIT", "TRU", "EYPT", "KRON", "ADSK", "TSE", "KROS", "ORCL", "VRNS", "VRNT", "PLUG", "PKG", "TSN", "TSM", "AUTL", "CRC", "CRI", "RNST", "CRL", "OABI", "CRM", "AMCX", "TTC", "ADTN", "TTD", "COCO", "NBIX", "TTI", "CWST", "LDI", "PLL", "KEYS", "VRRM", "CSQ", "PYPL", "PDFS", "LEA", "TUP", "CODI", "NWSA", "LEN", "BCOV", "FTDR", "FCEL", "ADVM", "PNC", "AMGN", "PNM", "PNR", "SMSI", "CUK", "ORIC", "MTSI", "LUXH", "DENN", "ILMN", "TWI", "CUZ", "EDIT", "ORGO", "ORGN", "POR", "CVE", "PLYM", "CVI", "SMRT", "VRSK", "VRSN", "MTTR", "MPLN", "TXG", "CVX", "HIMS", "BLDR", "BLDP", "BTTX", "TXN", "PUMP", "PPL", "BLDE", "BCRX", "SEER", "RBBN", "LHX", "SIMO", "CWT", "MCRB", "TYL", "COHU", "COHR", "LII", "HAE", "HAL", "SEDG", "VATE", "CXM", "HAS", "PRA", "DESP", "CXW", "PRG", "BLFS", "PRM", "PRO", "AMKR", "COIN", "HBI", "AQST", "HBM", "CYH", "PSA", "GBCI", "ZVSA", "AMLX", "PSN", "PSO", "BPOP", "LKQ", "PSX", "HCP", "PHUN", "AMLI", "DAKT", "VNOM", "EMBC", "GSHD", "SEIC", "CBRE", "KNSA", "LMND", "PUK", "CBSH", "HEI", "ZETA", "NSSC", "LMT", "COLL", "CSTM", "HES", "COLD", "COLB", "NBTB", "COMP", "LNT", "COMM", "BCYC", "LNW", "HELE", "XPRO", "PWP", "AMPX", "SIRI", "IDEX", "LIFW", "SRCL", "AMPS", "UAA", "AVAV", "LOW", "FTNT", "GSIT", "AMPL", "SABR", "AMPH", "NOMD", "PXD", "BLKB", "HIVE", "COOP", "LPX", "QCOM", "HAFC", "GBIO", "SITE", "AMSC", "SITC", "MLKN", "UBS", "SITM", "AMRX", "DARE", "DAN", "UCBI", "DAR", "AMRK", "LECO", "CGAU", "THRM", "LRN", "DBI", "SAGE", "QTRX", "LILA", "AVDX", "VSAT", "MPWR", "THRY", "LILM", "HKD", "UDR", "DNLI", "WEAV", "CORT", "MYGN", "GOEV", "YMM", "SAIA", "LTH", "VSCO", "HLF", "AVGO", "HLI", "DDD", "WIMI", "DDL", "LIND", "DNOW", "HLX", "NOTE", "ONON", "HEPS", "DRVN", "DEI", "AZPN", "SNAP", "LUV", "COTY", "GOGO", "UGI", "BHIL", "DAVA", "YOU", "HAIN", "AVIR", "LEGN", "DFH", "NOVA", "LVS", "SNDX", "YPF", "SNDR", "CGEN", "CGEM", "FLGT", "COUR", "HRMY", "HALO", "AMWL", "AA", "UHS", "HOG", "BHLB", "ARBK", "AG", "AI", "AM", "AN", "EMKR", "AR", "RBOT", "DGX", "HESM", "SNCY", "ARAY", "UIS", "BA", "BC", "GOLD", "ROKU", "HPK", "ARCO", "DHI", "BG", "AIRC", "MLTX", "LXU", "BJ", "ARCH", "HPQ", "HPP", "BL", "BN", "BP", "BR", "DHT", "NTAP", "BV", "BW", "DHX", "BX", "LRCX", "BZ", "WRBY", "CC", "CE", "CF", "TDOC", "CG", "IMAB", "LYV", "DIN", "CM", "YSG", "CP", "LZB", "IMAX", "HRB", "ARCT", "EZGO", "AMZN", "DD", "ARES", "DG", "HRL", "DH", "SRPT", "DK", "DM", "DO", "HRT", "LITE", "DQ", "AZUL", "DT", "DV", "NTCT", "DX", "EVBG", "ARDX", "GOOD", "EB", "ED", "EE", "SAND", "EH", "AVNS", "AVNT", "EL", "EM", "HST", "UMC", "SANM", "DKS", "HSY", "UMH", "IMCR", "EW", "TMCI", "DLB", "HTH", "FE", "ONTO", "FF", "DLO", "FN", "KODK", "FR", "DLX", "LIVN", "UNP", "FLNC", "GD", "CCCC", "NKTR", "HUN", "HUM", "WELL", "GT", "GOOS", "DNA", "HA", "HD", "ARGX", "SRRK", "HI", "HL", "PECO", "ARIS", "HP", "SASR", "ULCC", "TMHC", "HWC", "UPS", "DOC", "WAFD", "NKTX", "IP", "IQ", "DOV", "GGAL", "IT", "TDUP", "DOW", "MAC", "HASI", "HRTX", "CGNT", "JD", "HXL", "EVER", "MAR", "MAT", "MDLZ", "IDYA", "MAX", "GOTU", "RKLB", "ARKO", "PINC", "URI", "EEFT", "QUBT", "PMVP", "KC", "KD", "PINS", "SAVE", "SAVA", "BHVN", "OFIX", "KN", "NXPI", "GOSS", "SATS", "MCD", "UPLD", "LC", "EVGO", "DRH", "LH", "MCS", "LI", "FUBO", "LL", "ANET", "PRAA", "LRMR", "LU", "MDB", "UTI", "SWBI", "MDC", "LZ", "MA", "CTKB", "HNRG", "UTZ", "NTLA", "WERN", "MP", "MQ", "SNOW", "MS", "CTLT", "MU", "UCTT", "CTLP", "MEG", "MEI", "LESL", "DTC", "DTE", "NE", "SNPS", "RGEN", "NI", "DTM", "ERAS", "PRCH", "CTMX", "NR", "NS", "HNST", "PAAS", "NU", "LAND", "BMBL", "MFC", "MFG", "LEVI", "VSTO", "AEVA", "ANGI", "MUSA", "BURL", "UVV", "OC", "NCLH", "PRCT", "OI", "DUK", "EVLV", "ON", "QDEL", "OR", "AAOI", "MGA", "GKOS", "IMMR", "PB", "PD", "HAYW", "PH", "PI", "LAMR", "PK", "PL", "PM", "DVN", "MGY", "AAPL", "RCAT", "FLWS", "TIGR", "PX", "DSKE", "ARQT", "CTOS", "ARQQ", "MHK", "NGVT", "QD", "MHO", "DOCN", "VBTX", "LEXX", "DOCS", "UPST", "DOCU", "ZBH", "QS", "CCOI", "PRDO", "DXC", "IAG", "RF", "NTNX", "PACB", "NCMI", "RH", "MIR", "BMEA", "RL", "IAS", "RS", "KOPN", "RY", "RGLD", "SA", "PRGO", "QLYS", "SD", "NTRA", "SF", "SG", "IBN", "IBP", "SM", "SR", "QUIK", "SU", "QSI", "IIIV", "TD", "CTRM", "ICL", "TH", "TK", "GTES", "TEAM", "ICU", "TS", "CTRA", "TU", "EVRG", "HBAN", "IDA", "EVRI", "LILAK", "UPWK", "TILE", "IMTX", "MLI", "UA", "UE", "PZZA", "SBAC", "RGNX", "HSCS", "UP", "CLDX", "CLDT", "FULC", "VC", "ERII", "TECH", "MMS", "FULT", "ARVN", "EVTC", "ZGN", "OSPN", "IFF", "WD", "WH", "ARWR", "WK", "SBCF", "WU", "WW", "MDXG", "WY", "MOD", "SFIX", "IMUX", "SWKS", "TIMB", "MOS", "MOR", "IGT", "CLFD", "CPNG", "VAC", "IIPR", "ZIP", "MPC", "RXST", "GTLB", "BIDU", "PRMW", "ENFN", "MPW", "IRBT", "YY", "RXRX", "EAF", "ZG", "ZH", "ZI", "ZM", "CTXR", "IMXI", "MRC", "PRME", "EBC", "MRO", "RGTI", "DOMO", "LAZR", "NYCB", "IRDM", "LNTH", "EBS", "CYBR", "DOLE", "MSM", "CPRX", "PNFP", "SFNC", "ANTX", "SBFM", "QURE", "MTB", "CPRI", "RCKT", "BIGC", "ZBRA", "MTH", "MTG", "VET", "MTN", "TITN", "DOOR", "EDR", "MTZ", "VFC", "TREX", "EDU", "ENIC", "OSUR", "MUR", "QMCO", "BEAM", "MUX", "FUTU", "TREE", "TRGP", "VGR", "ASAN", "TENB", "EFC", "PARA", "PRST", "INN", "GLBE", "PRTA", "INO", "SWTX", "MMSI", "RLAY", "PARR", "MWA", "EFX", "CHEF", "VTEX", "DXCM", "GLAD", "EGO", "EGP", "BECN", "EGY", "CLNN", "EARN", "VIR", "MEDP", "MXL", "PATH", "IPG", "TRIP", "GLDD", "LSCC", "PRVA", "PATK", "CLOV", "RBA", "MYE", "BILL", "INBX", "XMTR", "CHGG", "BILI", "RBT", "CUBI", "SBLK", "ABBV", "AAL", "AAN", "IQV", "AAP", "WSBC", "EIX", "SOFI", "LBAI", "RCM", "LSEA", "ENPH", "INDB", "IRM", "INDI", "IRT", "TERN", "ABM", "SBOW", "EWBC", "ABCL", "ZTO", "VLO", "ZTS", "UDMY", "RDN", "ABCB", "VLY", "RDW", "BIPC", "RDY", "FIBK", "ENOV", "ACT", "REG", "ZUO", "INCY", "ELF", "ADC", "WSFS", "RES", "INFA", "ADI", "ASGN", "EWCZ", "ADM", "TALK", "ADN", "ELS", "ITW", "TNET", "INFN", "ADT", "RYAN", "ADV", "CHKP", "RYAM", "VNO", "TALO", "XRAY", "VNT", "CLSK", "PAYC", "VTLE", "BABA", "TVTX", "BIRD", "EMN", "AEL", "RCUS", "AEM", "VXRT", "AEP", "GPOR", "MVIS", "ZWS", "ENB", "IVVD", "RGP", "BRBR", "TROX", "TROW", "BEKE", "GPRK", "GPRO", "ENR", "ENS", "IVZ", "TNGX", "FIGS", "RHI", "SBSW", "RHP", "EOG", "BITF", "AGO", "HOLO", "AGS", "SSTK", "RIG", "MIRM", "ENTG", "HOMB", "GPRE", "MRCY", "AHH", "BRFS", "VRA", "OXLC", "EPR", "VRE", "AHT", "RJF", "SBUX", "EQH", "HOPE", "NBR", "CYRX", "EQR", "VSH", "VTNR", "MREO", "KTOS", "AIV", "MELI", "ASLE", "CHPT", "VST", "RKT", "SONO", "HOOD", "TARS", "XENE", "ASML", "RLJ", "LSXMA", "VTR", "VTRS", "CDIO", "LSXMK", "GLOB", "ESI", "SSYS", "INMD", "RLX", "KLAC", "ESS", "AKR", "WFRD", "SOPA", "OKTA", "NEE", "ETD", "CHRW", "NEM", "ALB", "LFST", "CHRS", "ALE", "GLNG", "ALK", "ETR", "LOGI", "VLCN", "RNG", "ABNB", "ALV", "NFG", "IRWD", "VVV", "AMC", "LSPD", "INOD", "AMD", "AME", "RNW", "AMG", "AMH", "OCGN", "TASK", "AMN", "AMP", "AMT", "BRKR", "TAST", "ROG", "AMX", "BRKL", "ROL", "NGG", "ROK", "NGL", "CDLX", "NGM", "ASPI", "EBAY", "EVH", "DCGO", "JMIA", "CHTR", "EVR", "VCTR", "HOUS", "PSEC", "XERS", "AKAM", "CDNA", "XNCR", "ANY", "RPM", "SOUN", "BERY", "PFLT", "CDMO", "BALL", "AOS", "ASRT", "OTIS", "APA", "BNED", "NIO", "APD", "JAN", "AKBA", "APG", "CDNS", "APH", "XAIR", "NIU", "APP", "RRC", "MRNS", "BALY", "NJR", "CHWY", "KLIC", "LBRT", "YMAB", "INSM", "AQN", "META", "METC", "OPCH", "PSFE", "NKE", "RSI", "BANC", "BNGO", "BROS", "ASTR", "ASTS", "ARE", "MRNA", "ARR", "INTR", "INTU", "FREE", "HCAT", "ASB", "FITB", "ASH", "BANR", "ABUS", "INST", "STAA", "CUTR", "OPEN", "INTA", "NMR", "HTBK", "VTYX", "MANU", "WOLF", "ATO", "DCOM", "WOOF", "FEMY", "AUB", "NNN", "NUVL", "SCCO", "MNKD", "VCYT", "FREY", "BARK", "NOG", "DCPH", "NOK", "AVA", "AVB", "NUTX", "PWSC", "INVA", "NOV", "QRTEA", "INVH", "NOW", "AVO", "AVT", "WAL", "JHG", "RXO", "WAT", "RXT", "BASE", "SGMO", "ESNT", "SGML", "MNMD", "AWR", "MARA", "RYI", "RYN", "PFSI", "WBS", "STEP", "MARK", "STEM", "CMCSA", "AXL", "WCC", "ZLAB", "AXP", "MRTN", "NRG", "CDXS", "TSCO", "AGCO", "TBBK", "FBP", "OLED", "NSA", "NSC", "PSNL", "TNYA", "RYTM", "MRUS", "FCF", "NSP", "PSNY", "FCN", "FVRR", "WEC", "TFII", "STGW", "FCX", "NMIH", "MATX", "MRVI", "NTB", "TSEM", "WEN", "FRME", "JLL", "NTR", "OCUL", "SKYW", "ESRT", "LOVE", "AGEN", "FDP", "FDS", "WFC", "SPCB", "NUE", "TWLO", "BRZE", "SCHW", "CIEN", "EOLS", "WHD", "FNGR", "PSTG", "FANG", "RMAX", "MNSO", "NWE", "TSHA", "WHR", "JOE", "NWL", "GDOT", "NWN", "HTLD", "GMAB", "HTLF", "NWS", "CIFR", "KYMR", "ATAT", "NXE", "ESTC", "SAM", "FHB", "GDRX", "WIT", "FHI", "WIX", "CVAC", "FHN", "BNTX", "JELD", "AGIO", "ACAD", "NVAX", "PSTX", "NYT", "CVBF", "RMBS", "FIP", "CMPX", "BAM", "FRPT", "FIX", "AKRO", "BAX", "MNTK", "STNG");
        for (String stock : stocks) {
            try {

                HttpClient httpClient = clients.take();
                cachedThread.execute(() -> {
                    List<String> result = Lists.newLinkedList();
                    try {
                        long startTime = 1711546200000000000L;
                        String preUrl = api + stock + "?order=asc&timestamp.gte=1711546200000000000&timestamp.lte=1711547940000000000&limit=10&sort=timestamp&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
                        String preTrade = getTrade(preUrl, httpClient, startTime);
                        if (!preTrade.contains(",")) {
                            log.warn(stock + " " + preTrade);
                        }
                        String str = stock + "," + preTrade;
                        System.out.println(str);
                    } finally {
                        clients.offer(httpClient);
                    }
                });
            } catch (Exception e) {
                log.error("error stock: " + stock);
                e.printStackTrace();
            }
        }
        cachedThread.shutdown();
    }

    private static String getTrade(String preUrl, HttpClient httpclient, long startTime) {
        GetMethod get = new GetMethod(preUrl);
        try {
            int code = 0;
            for (int i = 0; i < 3; i++) {
                code = httpclient.executeMethod(get);
                if (code == 200) {
                    break;
                }
            }
            if (code != 200) {
                return "request error";
            }

            InputStream stream = get.getResponseBodyAsStream();
            TradeResp tickerResp = JSON.parseObject(stream, TradeResp.class);
            List<Trade> results = tickerResp.getResults();
            if (CollectionUtils.isNotEmpty(results)) {
                for (int i = 0; i < results.size(); i++) {
                    Trade trade = results.get(i);
                    double price = trade.getPrice();
                    long participantTimestamp = trade.getParticipant_timestamp();
                    if (participantTimestamp < startTime) {
                        continue;
                    }
                    long nano = participantTimestamp % 1000000000L;
                    long second = participantTimestamp / 1000000000L;
                    LocalDateTime time = LocalDateTime.ofEpochSecond(second, (int) nano, ZoneOffset.of("+8"));
                    String format = time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    return price + "," + format;
                }
                return "no data";
            } else {
                return "no data";
            }
        } catch (Exception e) {
            return e.getMessage();
        } finally {
            get.releaseConnection();
        }
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);

        System.setProperty("javax.net.ssl.trustStore", "/Users/Luonanqin/Downloads/truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "134931");

        getData();
        log.info("============ end ============");
    }
}
