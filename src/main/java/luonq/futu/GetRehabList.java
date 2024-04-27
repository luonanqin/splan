package luonq.futu;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetSubInfo;
import com.futu.openapi.pb.QotRequestRehab;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import util.BaseUtils;
import util.Constants;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Luonanqin on 2023/5/5.
 */
@Data
public class GetRehabList implements FTSPI_Qot, FTSPI_Conn {

    // CompanyAct_None = 0; 无
    // CompanyAct_Split = 1; 拆股
    // CompanyAct_Join = 2; 合股
    // CompanyAct_Bonus = 4; 送股
    // CompanyAct_Transfer = 8; 转赠股
    // CompanyAct_Allot = 16; 配股
    // CompanyAct_Add = 32; 增发股
    // CompanyAct_Dividend = 64; 现金分红
    // CompanyAct_SPDividend = 128; 特别股息

    FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();

    private Map<String, List<String>> result = Maps.newHashMap();
    private Map<Integer, String> seqNoToStock = Maps.newHashMap();

    public GetRehabList() {
        qot.setClientInfo("javaclient", 1);  //设置客户端信息
        qot.setConnSpi(this);  //设置连接回调
        qot.setQotSpi(this);   //设置交易回调
    }

    public void start() {
        qot.initConnect("127.0.0.1", (short) 11111, false);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException exc) {
        }
    }

    public void end() {
        qot.close();
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


    public void getSubInfo() {
        QotGetSubInfo.C2S c2s = QotGetSubInfo.C2S.newBuilder()
          .build();
        QotGetSubInfo.Request req = QotGetSubInfo.Request.newBuilder().setC2S(c2s).build();
        int seqNo = qot.getSubInfo(req);
        System.out.printf("Send QotGetSubInfo: %d\n", seqNo);
    }


    public void getRehab(String code) {
        QotCommon.Security sec = QotCommon.Security.newBuilder()
          .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
          .setCode(code)
          .build();
        QotRequestRehab.C2S c2s = QotRequestRehab.C2S.newBuilder()
          .setSecurity(sec)
          .build();
        QotRequestRehab.Request req = QotRequestRehab.Request.newBuilder().setC2S(c2s).build();
        int seqNo = qot.requestRehab(req);
        //        System.out.println(code + " " + seqNo);
        seqNoToStock.put(seqNo, code);
    }

    @Override
    public void onReply_RequestRehab(FTAPI_Conn client, int nSerialNo, QotRequestRehab.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotRequestRehab failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }

            List<QotCommon.Rehab> rehabList = rsp.getS2COrBuilder().getRehabListList();
            if (CollectionUtils.isEmpty(rehabList)) {
                return;
            }
            String stock = seqNoToStock.get(nSerialNo);
            List<String> info = Lists.newArrayList();
            for (QotCommon.Rehab rehab : rehabList) {
                String time = rehab.getTime();
                double fwdFactorA = rehab.getFwdFactorA();
                long companyActFlag = rehab.getCompanyActFlag();

                String value = time + " " + fwdFactorA + " " + companyActFlag;
                info.add(stock + " " + value);
            }
            System.out.println(stock + " count:" + info.size());

            result.put(stock, info);
        }
    }

    public static void getData() throws Exception {
        FTAPI.init();
        GetRehabList quote = new GetRehabList();
        quote.start();

        //        quote.getRehab("DPST");

        Map<String, String> stockFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");
        Set<String> invalidSet = Sets.newHashSet("SDC", "SEV", "STSA", "BSAQ", "DHHC", "STRE", "HLBZ", "MSDA", "BBBY", "FWAC", "KDNY", "WPCB", "SJR", "RIDE", "SLGG", "SCUA", "APEN", "APGN", "CEMI", "YELL", "KAL", "SQZ", "JATT", "BBLN", "VMGA", "CNCE", "APMI", "SDAC", "BTB", "GRAY", "GRCY", "MBSC", "RAAS", "BWC", "RADI", "FOCS", "SLVR", "PTRA", "GRIL", "DICE", "SUMO", "LYLT", "GRNA", "MTAC", "TGR", "OIIM", "TIG", "RJAC", "FORG", "CIH", "VVNT", "AHRN", "KVSC", "SMIH", "ZING", "EUCR", "CFMS", "PDCE", "ORCC", "LCI", "PLXP", "TYDE", "HMPT", "HVBC", "ORIA", "MLAC", "FGMC", "IPVI", "CVT", "FPAC", "SVFB", "AQUA", "MTVC", "EMBK", "UTAA", "DALS", "PDOT", "VNTR", "UBA", "SVNA", "BLNG", "AIMC", "LSI", "DRTT", "DCP", "CORS", "DCT", "HERA", "GSQB", "GSRM", "SESN", "AZRE", "LION", "UTME", "CS", "GBRG", "HEXO", "IQMD", "LITT", "MLVF", "PEAR", "DMS", "AZYO", "CPAA", "MCG", "USX", "DSEY", "ARNC", "CGRN", "SNRH", "MURF", "RKTA", "SI", "EVOJ", "EVOP", "VORB", "PNAC", "HWKZ", "MMP", "WAVC", "ARYE", "OBNK", "XM", "CYAD", "TRAQ", "MMMB", "AAWW", "TIOA", "ZT", "JUGG", "RCLF", "OBSV", "MTP", "CPUH", "AJRD", "ENOB", "NHIC", "JMAC", "NYMX", "JUPW", "VPCB", "MEKA", "PNTM", "VQS", "ENTF", "DTEA", "CHRA", "ESM", "TRTN", "GLOP", "NGC", "CYXT", "SGFY", "METX", "FISV", "FACT", "BVXV", "ABST", "SGHL", "EOCW", "BNNR", "BWAC", "ISEE", "CIDM", "FRON", "SPCM", "ATAQ", "ATCO", "MNTV", "VHNA", "RUTH", "SGTX", "CIIG", "SPKB", "JNCE", "CINC", "FRC", "FRG", "ATNX", "SPPI", "OFC", "CREC", "WWE", "ALBO", "GVCI", "ACQR", "ATTO", "FZT", "JWAC", "GEEX", "GMVD", "PGRW", "SYNH", "TTCF", "OSH", "ITCB", "GENQ", "RENN", "UNVR", "GET", "ALPA", "TCFC", "GFX", "BGCP", "ALPS", "ADAL", "RETA", "BOXD", "REVE", "ADER", "GLS", "REUN", "FSTX", "LDHA", "ICNC", "PHCF", "BPAC", "XPAX", "ADMP", "DMYS", "BGRY", "TLGA", "AURC", "TCVA", "CSII", "MGTA", "PKI", "GFGD", "FTEV", "GNUS", "DNAB", "DNAD", "QTEK", "HILS", "ONCS", "IDBA", "PTE", "LMNL", "NBRV", "ZEST", "AVAC", "AMOV", "AMOT", "LVAC", "FTPA", "GFOR", "SIRE", "MPRA", "HHC", "ROCG", "SRGA", "AMRS", "ROCC", "LMST", "FCRD", "SIVB", "PIAI", "HMA", "GOGN", "IMBI", "AMYT", "CCAI", "HAPP", "TMDI", "HSC", "RONI", "MYOV", "WEJO", "YVR", "DFFN", "LVRA", "GGAA", "CTIC", "RXDX", "TMKR", "VBOC", "ANGN", "PRBM", "PRDS", "ERES", "IBA", "CLBR", "UPTD", "YTPG", "ZEV", "QTT", "ANPC", "QUOT", "LFAC", "PANA", "HSKA", "IMV", "PRTK", "OXAC", "RAM", "PRVB", "FMIV", "ANZU", "CDAK", "INDT", "ISO", "TETC", "ERYP", "INKA", "CLXT", "VLAT", "BRIV", "AFTR", "ALR", "DGNU", "ROC", "AMV", "BRMK", "TWCB", "RTL", "ATY", "NUVA", "AUY", "SCAQ", "UIHC", "HTGM", "AGAC", "STET", "PSPC", "VLON", "OPNT", "SKYA", "TWNI", "AGFS", "SCHN", "VLTA", "AGGR", "MAXR", "SAL", "HCNE", "TOAC");
        for (String stock : stockFileMap.keySet()) {
            if (invalidSet.contains(stock)) {
                continue;
            }
            quote.getRehab(stock);
            Thread.sleep(600);
        }
        Map<String, List<String>> result = quote.getResult();
        List<String> data = Lists.newArrayList();
        for (String stock : result.keySet()) {
            for (String value : result.get(stock)) {
                data.add(String.format("%s %s", stock, value));
            }
        }
        BaseUtils.writeFile(Constants.SPLIT_PATH + "rehabList", data);
    }

    public static void main(String[] args) throws Exception {
        getData();
    }
}
