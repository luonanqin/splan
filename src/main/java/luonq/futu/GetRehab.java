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
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import util.BaseUtils;
import util.Constants;

import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Created by Luonanqin on 2023/5/5.
 */
@Data
public class GetRehab implements FTSPI_Qot, FTSPI_Conn {

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

    private Map<String, String> result = Maps.newHashMap();
    private Map<Integer, String> seqNoToStock = Maps.newHashMap();

    @Data
    public static class StockQuote {
        private String stock;
        private double price;
    }

    private static PriorityQueue<StockQuote> queue = new PriorityQueue<>(100, (o1, o2) -> (int) (o1.getPrice() - o2.getPrice()));

    public GetRehab() {
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
            QotCommon.Rehab rehab = rehabList.get(rehabList.size() - 1);
            String time = rehab.getTime();
            double fwdFactorA = rehab.getFwdFactorA();
            long companyActFlag = rehab.getCompanyActFlag();

            String value = time + " " + fwdFactorA + " " + companyActFlag;
            String stock = seqNoToStock.get(nSerialNo);
            System.out.println(stock + " " + value);

            result.put(stock, value);
        }
    }

    public static void main(String[] args) throws Exception {
        FTAPI.init();
        GetRehab quote = new GetRehab();
        quote.start();

        //        quote.getRehab("DPST");

        Map<String, String> stockFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");
        for (String stock : stockFileMap.keySet()) {
            quote.getRehab(stock);
            Thread.sleep(500);
        }
        Map<String, String> result = quote.getResult();
        System.out.println(result);
        List<String> data = Lists.newArrayList();
        for (String stock : result.keySet()) {
            data.add(String.format("%s %s", stock, result.get(stock)));
        }
        BaseUtils.writeFile(Constants.SPLIT_PATH + "rehab", data);
    }
}
