package futu;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetSubInfo;
import com.futu.openapi.pb.QotSub;
import com.futu.openapi.pb.QotUpdateBasicQot;
import com.google.common.collect.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Luonanqin on 2023/5/5.
 */
public class BasicQuoteDemo implements FTSPI_Qot, FTSPI_Conn {


    FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();

    private Map<String, Double> stockToCurrPriceMap = Maps.newHashMap();

    public BasicQuoteDemo() {
        qot.setClientInfo("javaclient", 1);  //设置客户端信息
        qot.setConnSpi(this);  //设置连接回调
        qot.setQotSpi(this);   //设置交易回调
    }

    public void start() {
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

    @Override
    public void onReply_Sub(FTAPI_Conn client, int nSerialNo, QotSub.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotSub failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                String json = JsonFormat.printer().print(rsp);
                System.out.printf("Receive QotSub: %s\n", json);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onPush_UpdateBasicQuote(FTAPI_Conn client, QotUpdateBasicQot.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotUpdateBasicQuote failed: %s\n", rsp.getRetMsg());
        } else {
            QotUpdateBasicQot.S2C s2C = rsp.getS2C();
            List<QotCommon.BasicQot> basicQotListList = s2C.getBasicQotListList();
            for (QotCommon.BasicQot basicQot : basicQotListList) {
                String stock = basicQot.getSecurity().getCode();
                double curPrice = basicQot.getCurPrice();
                //                System.out.println("stock=" + stock + " price=" + curPrice);
            }
        }
    }

    @Override
    public void onReply_GetSubInfo(FTAPI_Conn client, int nSerialNo, QotGetSubInfo.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotGetSubInfo failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                String json = JsonFormat.printer().print(rsp);
                System.out.printf("Receive QotGetSubInfo: %s\n", json);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    public void subBasicQuote(String stock) {
        List<Integer> subTypeList = new ArrayList<>();
        subTypeList.add(QotCommon.SubType.SubType_Basic_VALUE);

        QotCommon.Security sec = QotCommon.Security.newBuilder()
          .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
          .setCode(stock)
          .build();
        QotSub.C2S c2s = QotSub.C2S.newBuilder()
          .addSecurityList(sec)
          .addAllSubTypeList(subTypeList)
          .setIsSubOrUnSub(true)
          .setIsRegOrUnRegPush(true)
          .build();
        QotSub.Request req = QotSub.Request.newBuilder().setC2S(c2s).build();
        int seqNo = qot.sub(req);
        System.out.printf("Send QotSub: %d\n", seqNo);
    }

    public void getSubInfo() {
        QotGetSubInfo.C2S c2s = QotGetSubInfo.C2S.newBuilder()
          .build();
        QotGetSubInfo.Request req = QotGetSubInfo.Request.newBuilder().setC2S(c2s).build();
        int seqNo = qot.getSubInfo(req);
        System.out.printf("Send QotGetSubInfo: %d\n", seqNo);
    }

    public static void main(String[] args) {
        FTAPI.init();
        BasicQuoteDemo ticker = new BasicQuoteDemo();
        ticker.start();

        ticker.subBasicQuote("FUTU");
        ticker.subBasicQuote("AAPL");
        ticker.subBasicQuote("AMZN");
        ticker.subBasicQuote("TSLA");
        ticker.subBasicQuote("JPM");
        ticker.subBasicQuote("TSM");
        ticker.subBasicQuote("BA");
        ticker.subBasicQuote("CSCO");
        ticker.subBasicQuote("CVCO");

//        ticker.getSubInfo();

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException exc) {

            }
        }
    }

}
