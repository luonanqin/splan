package luonq.futu;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetOptionChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import util.BaseUtils;
import util.Constants;

import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class GetOptionChain implements FTSPI_Qot, FTSPI_Conn {

    FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    FileWriter fw;
    String fileName;
    private Map<String, List<String>> chainMap = Maps.newHashMap();
    private String today;
    private String endDay;

    public GetOptionChain() {
        qot.setClientInfo("javaclient", 1);  //设置客户端信息
        qot.setConnSpi(this);  //设置连接回调
        qot.setQotSpi(this);   //设置交易回调
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void start() {
        today = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        endDay = LocalDate.now().plusDays(31).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        qot.initConnect("127.0.0.1", (short) 11111, false);
    }

    @Override
    public void onInitConnect(FTAPI_Conn client, long errCode, String desc) {
        System.out.printf("Qot onInitConnect: ret=%b desc=%s connID=%d\n", errCode, desc, client.getConnectID());
        if (errCode != 0) {
            return;
        }
    }

    @Override
    public void onDisconnect(FTAPI_Conn client, long errCode) {
        System.out.printf("Qot onDisConnect: %d\n", errCode);
    }

    public List<String> getChainList(String code)  {
        QotCommon.Security sec = QotCommon.Security.newBuilder()
          .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
          .setCode(code)
          .build();
        QotGetOptionChain.C2S c2s = QotGetOptionChain.C2S.newBuilder()
          .setOwner(sec)
          .setBeginTime(today)
          .setEndTime(endDay)
          .build();
        QotGetOptionChain.Request req = QotGetOptionChain.Request.newBuilder().setC2S(c2s).build();
        qot.getOptionChain(req);
        for (int i = 0; i < 10; i++) {
            if (chainMap.containsKey(code)) {
                return chainMap.get(code);
            }
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    @Override
    public void onReply_GetOptionChain(FTAPI_Conn client, int nSerialNo, QotGetOptionChain.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("get code=[%s] option chain failed\n", rsp.getRetMsg());
        } else {
            try {
                int optionChainCount = rsp.getS2C().getOptionChainCount();
                if (optionChainCount > 0) {
                    QotCommon.OptionStaticExData optionExData = rsp.getS2C().getOptionChainOrBuilder(0).getOptionOrBuilder(0).getCall().getOptionExData();
                    String code = optionExData.getOwner().getCode();
                    List<QotGetOptionChain.OptionItem> optionList = rsp.getS2C().getOptionChainOrBuilder(0).getOptionList();
                    List<String> chainList = Lists.newArrayList();
                    for (QotGetOptionChain.OptionItem optionItem : optionList) {
                        QotCommon.OptionStaticExData call = optionItem.getCall().getOptionExData();
                        double strikePrice = call.getStrikePrice();
                        String strikeTime = call.getStrikeTime();
                        String optionCode = optionItem.getCall().getBasic().getSecurity().getCode();
                        String x = optionCode + "\t" + strikeTime + "\t" + strikePrice;
//                        System.out.println(x);
                        chainList.add(x);
                    }
                    chainMap.put(code, chainList);
//                    String strikeTime = optionExData.getStrikeTime();
//                    double strikePrice = optionExData.getStrikePrice();
//                    String code = optionExData.getOwner().getCode();
//                    System.out.println(code + "\t" + strikeTime);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        FTAPI.init();
        GetOptionChain qot = new GetOptionChain();
        qot.start();

        TimeUnit.SECONDS.sleep(5);

        List<String> aapl = qot.getChainList("AAPL");
        System.out.println(aapl);

        Map<String, String> stockFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");
        Set<String> invalidSet = Sets.newHashSet("SDC", "SEV", "STSA", "BSAQ", "DHHC", "STRE", "HLBZ", "MSDA", "BBBY", "FWAC", "KDNY", "WPCB", "SJR", "RIDE", "SLGG", "SCUA", "APEN", "APGN", "CEMI", "YELL", "KAL", "SQZ", "JATT", "BBLN", "VMGA", "CNCE", "APMI", "SDAC", "BTB", "GRAY", "GRCY", "MBSC", "RAAS", "BWC", "RADI", "FOCS", "SLVR", "PTRA", "GRIL", "DICE", "SUMO", "LYLT", "GRNA", "MTAC", "TGR", "OIIM", "TIG", "RJAC", "FORG", "CIH", "VVNT", "AHRN", "KVSC", "SMIH", "ZING", "EUCR", "CFMS", "PDCE", "ORCC", "LCI", "PLXP", "TYDE", "HMPT", "HVBC", "ORIA", "MLAC", "FGMC", "IPVI", "CVT", "FPAC", "SVFB", "AQUA", "MTVC", "EMBK", "UTAA", "DALS", "PDOT", "VNTR", "UBA", "SVNA", "BLNG", "AIMC", "LSI", "DRTT", "DCP", "CORS", "DCT", "HERA", "GSQB", "GSRM", "SESN", "AZRE", "LION", "UTME", "CS", "GBRG", "HEXO", "IQMD", "LITT", "MLVF", "PEAR", "DMS", "AZYO", "CPAA", "MCG", "USX", "DSEY", "ARNC", "CGRN", "SNRH", "MURF", "RKTA", "SI", "EVOJ", "EVOP", "VORB", "PNAC", "HWKZ", "MMP", "WAVC", "ARYE", "OBNK", "XM", "CYAD", "TRAQ", "MMMB", "AAWW", "TIOA", "ZT", "JUGG", "RCLF", "OBSV", "MTP", "CPUH", "AJRD", "ENOB", "NHIC", "JMAC", "NYMX", "JUPW", "VPCB", "MEKA", "PNTM", "VQS", "ENTF", "DTEA", "CHRA", "ESM", "TRTN", "GLOP", "NGC", "CYXT", "SGFY", "METX", "FISV", "FACT", "BVXV", "ABST", "SGHL", "EOCW", "BNNR", "BWAC", "ISEE", "CIDM", "FRON", "SPCM", "ATAQ", "ATCO", "MNTV", "VHNA", "RUTH", "SGTX", "CIIG", "SPKB", "JNCE", "CINC", "FRC", "FRG", "ATNX", "SPPI", "OFC", "CREC", "WWE", "ALBO", "GVCI", "ACQR", "ATTO", "FZT", "JWAC", "GEEX", "GMVD", "PGRW", "SYNH", "TTCF", "OSH", "ITCB", "GENQ", "RENN", "UNVR", "GET", "ALPA", "TCFC", "GFX", "BGCP", "ALPS", "ADAL", "RETA", "BOXD", "REVE", "ADER", "GLS", "REUN", "FSTX", "LDHA", "ICNC", "PHCF", "BPAC", "XPAX", "ADMP", "DMYS", "BGRY", "TLGA", "AURC", "TCVA", "CSII", "MGTA", "PKI", "GFGD", "FTEV", "GNUS", "DNAB", "DNAD", "QTEK", "HILS", "ONCS", "IDBA", "PTE", "LMNL", "NBRV", "ZEST", "AVAC", "AMOV", "AMOT", "LVAC", "FTPA", "GFOR", "SIRE", "MPRA", "HHC", "ROCG", "SRGA", "AMRS", "ROCC", "LMST", "FCRD", "SIVB", "PIAI", "HMA", "GOGN", "IMBI", "AMYT", "CCAI", "HAPP", "TMDI", "HSC", "RONI", "MYOV", "WEJO", "YVR", "DFFN", "LVRA", "GGAA", "CTIC", "RXDX", "TMKR", "VBOC", "ANGN", "PRBM", "PRDS", "ERES", "IBA", "CLBR", "UPTD", "YTPG", "ZEV", "QTT", "ANPC", "QUOT", "LFAC", "PANA", "HSKA", "IMV", "PRTK", "OXAC", "RAM", "PRVB", "FMIV", "ANZU", "CDAK", "INDT", "ISO", "TETC", "ERYP", "INKA", "CLXT", "VLAT", "BRIV", "AFTR", "ALR", "DGNU", "ROC", "AMV", "BRMK", "TWCB", "RTL", "ATY", "NUVA", "AUY", "SCAQ", "UIHC", "HTGM", "AGAC", "STET", "PSPC", "VLON", "OPNT", "SKYA", "TWNI", "AGFS", "SCHN", "VLTA", "AGGR", "MAXR", "SAL", "HCNE", "TOAC", "AGLE", "SCU", "SCOB", "NVCN", "STOR", "MBAC", "NMTR", "SFT", "JUN", "SCPL", "MSAC", "GIAC", "SJI", "WPCA", "BSGA", "LGTO", "ZYNE", "BLI", "APGB", "PCCT", "BSMX", "SQL", "CENQ", "SSU", "CEQP", "SUAC", "BSQR", "NVSA", "NETC", "APRN", "LHCG", "NEWR", "PTOC", "CNNB", "VECT", "PTRS", "SUNL", "TPBA", "VEDU", "AYLA", "IPAX", "HMAC", "KSI", "CCV", "MCAE", "SMAP", "PCYG", "CFFE", "CIR", "KNBE", "CFIV", "KVSA", "WQGA", "NFNT", "ELVT", "FOXW", "MCLD", "THAC", "CFRX", "CTG", "BCOR", "MTRY", "MLAI", "IPVF", "LHC", "SVFA", "BTWN", "KNSW", "EDTX", "CXAC", "SELB", "THRN", "GSMG", "GSQD", "DEN", "NXGN", "COUP", "NOVN", "ARBG", "JCIC", "ARCK", "ARCE", "COWN", "ARGO", "LIVB", "UPH", "HT", "DNZ", "AAIC", "CPAR", "CPAQ", "BMAQ", "MDNA", "NM", "HWEL", "MIT", "ARTE", "VGFC", "TZPS", "TRCA", "OSTK", "DBTX", "MEAC", "TRHC", "VII", "ABCM", "VMW", "IRNT", "TALS", "ABGI", "PFDR", "NCR", "EFHT", "MEOA", "PFHD", "HORI", "ASPA", "FINM", "BNFT", "FRBN", "SGII", "FREQ", "OTMO", "FRGI", "UMPQ", "DLCA", "DCRD", "DTRT", "PFSW", "GDNR", "TBCP", "IBER", "ACAQ", "OLIT", "FRSG", "PONO", "WMC", "ACDI", "MFGP", "FRXB", "HPLT", "TBSA", "BOAC", "NAAC", "MOBV", "EGLX", "ACRO", "CIXX", "ATVI", "GEHI", "ITAQ", "RVLP", "TCDA", "HYRE", "NATI", "ALOR", "SHUA", "KINZ", "RNER", "FSRX", "CRZN", "GLG", "MOXC", "VACC", "VIVO", "ICPT", "KAII", "AMAO", "FTAA", "BGSX", "AMCI", "FTCH", "FCAX", "CBIO", "VRTV", "GWII", "POW", "HZNP", "ONEM", "HEP", "AEAC", "PYR", "AVCT", "AVEO", "WEBR", "EQRX", "AEHA", "AVID", "LEGA", "PICC", "SAMA", "SRNE", "CTAQ", "IDRA", "ANAC", "VBLT", "AERC", "AVYA", "PACW", "IMPL", "PACX", "TMPO", "PACI", "CLAA", "HSAQ", "IMRA", "QUMU", "CCVI", "MICT", "WWAC", "PRPC", "BIOC", "BIOT", "BIOS", "ITQ", "PAYA", "RFP", "IVC", "BRDS", "NLTX", "MIRO", "LOCC", "EBAC", "AKU", "VLDR", "LOKM", "DKDCA", "VCXA", "HCCI", "VLNS", "HCDI", "LGAC", "TWNK", "ESTE", "SCMA", "BWV", "NOGN", "BMAC", "MDWT", "DUNE", "GHL", "BYTS", "CLAY", "PRSR", "PATI");
        for (String code : stockFileMap.keySet()) {
            if (invalidSet.contains(code)) {
                continue;
            }

            QotCommon.Security sec = QotCommon.Security.newBuilder()
              .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
              .setCode(code)
              .build();
            QotGetOptionChain.C2S c2s = QotGetOptionChain.C2S.newBuilder()
              .setOwner(sec)
              .setBeginTime("2024-06-01")
              .setEndTime("2024-07-01")
              .build();
            QotGetOptionChain.Request req = QotGetOptionChain.Request.newBuilder().setC2S(c2s).build();
            qot.qot.getOptionChain(req);

            TimeUnit.MILLISECONDS.sleep(3500);
        }
    }
}