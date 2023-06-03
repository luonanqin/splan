package luonq.futu;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotSub;
import com.futu.openapi.pb.QotUpdateTicker;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Luonanqin on 2023/5/5.
 */
public class TickerDemo implements FTSPI_Qot, FTSPI_Conn {


    FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();

    public TickerDemo() {
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
    public void onPush_UpdateTicker(FTAPI_Conn client, QotUpdateTicker.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotUpdateTicker failed: %s\n", rsp.getRetMsg());
        } else {
            QotUpdateTicker.S2COrBuilder s2c = rsp.getS2COrBuilder();
            QotCommon.Security security = s2c.getSecurity();
            String stock = security.getCode();

            List<QotCommon.Ticker> tickerListList = s2c.getTickerListList();

            for (QotCommon.Ticker ticker : tickerListList) {
                double price = ticker.getPrice();
                long volume = ticker.getVolume();
                int dir = ticker.getDir();
                System.out.println("stock=" + stock + "\tprice=" + price + "\tvolume=" + volume + "\tdir=" + dir);
            }
        }
    }

    public void subTicker(String stock) {
        List<Integer> subTypeList = new ArrayList<>();
        subTypeList.add(QotCommon.SubType.SubType_Ticker_VALUE);

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

    public static void main(String[] args) {
        FTAPI.init();
        TickerDemo ticker = new TickerDemo();
        ticker.start();

        ticker.subTicker("FUTU");
        ticker.subTicker("AAPL");

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException exc) {

            }
        }
    }

}
