package luonq.futu;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetBasicQot;
import com.futu.openapi.pb.QotGetSecuritySnapshot;
import com.futu.openapi.pb.QotGetSubInfo;
import com.futu.openapi.pb.QotRequestRehab;
import com.futu.openapi.pb.QotSub;
import com.futu.openapi.pb.QotUpdateBasicQot;
import com.futu.openapi.pb.QotUpdateOrderBook;
import com.google.common.collect.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import util.BaseUtils;
import util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Created by Luonanqin on 2023/5/5.
 */
public class BasicQuoteDemo implements FTSPI_Qot, FTSPI_Conn {


    FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();

    private Map<String, Double> stockToCurrPriceMap = Maps.newHashMap();

    @Data
    public static class StockQuote {
        private String stock;
        private double price;
    }

    private static PriorityQueue<StockQuote> queue = new PriorityQueue<>(100, (o1, o2) -> (int) (o1.getPrice() - o2.getPrice()));

    public BasicQuoteDemo() {
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

    @Override
    public void onReply_Sub(FTAPI_Conn client, int nSerialNo, QotSub.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotSub failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                String json = JsonFormat.printer().print(rsp);
//                System.out.printf("Receive QotSub: %s\n", json);
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
                long id = Thread.currentThread().getId();
//                System.out.println("threadId=" + id + " stock=" + stock + " price=" + curPrice);
                System.out.println(" stock=" + stock + " price=" + curPrice);
            }
        }
    }

    @Override
    public void onPush_UpdateOrderBook(FTAPI_Conn client, QotUpdateOrderBook.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotUpdateOrderBook failed: %s\n", rsp.getRetMsg());
        } else {
            QotUpdateOrderBook.S2C s2C = rsp.getS2C();
            String code = s2C.getSecurity().getCode();
            List<QotCommon.OrderBook> orderBookAskListList = s2C.getOrderBookAskListList();
            List<QotCommon.OrderBook> orderBookBidListList = s2C.getOrderBookBidListList();
            System.out.print(code + "\t");
            if (CollectionUtils.isNotEmpty(orderBookBidListList)) {
                QotCommon.OrderBook orderBook = orderBookBidListList.get(0);
                double price = orderBook.getPrice();
                long volume = orderBook.getVolume();
                int orderCount = orderBook.getOrederCount();
                System.out.print("bid price=" + price + "\tvolume=" + volume);
            }
            if (CollectionUtils.isNotEmpty(orderBookAskListList)) {
                QotCommon.OrderBook orderBook = orderBookAskListList.get(0);
                double price = orderBook.getPrice();
                long volume = orderBook.getVolume();
                int orderCount = orderBook.getOrederCount();
                System.out.print(" ask price=" + price + "\tvolume=" + volume);
            }
            System.out.println();
        }
    }

    @Override
    public void onReply_GetBasicQot(FTAPI_Conn client, int nSerialNo, QotGetBasicQot.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotGetBasicQot failed: %s\n", rsp.getRetMsg());
        } else {
            QotGetBasicQot.S2C s2C = rsp.getS2C();
            List<QotCommon.BasicQot> basicQotListList = s2C.getBasicQotListList();
            for (QotCommon.BasicQot basicQot : basicQotListList) {
                String stock = basicQot.getSecurity().getCode();
                //                QotCommon.PreAfterMarketData preMarket = basicQot.getPreMarket();
                //                double curPrice = preMarket.getPrice();
                double curPrice = basicQot.getCurPrice();
                long id = Thread.currentThread().getId();
                System.out.println("threadId=" + id + " stock=" + stock + " price=" + curPrice);
            }
        }
    }

    public void getBasicQot(String stock) {
        QotCommon.Security sec = QotCommon.Security.newBuilder()
          .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
          .setCode(stock)
          .build();
        QotGetBasicQot.C2S c2s = QotGetBasicQot.C2S.newBuilder()
          .addSecurityList(sec)
          .build();
        QotGetBasicQot.Request req = QotGetBasicQot.Request.newBuilder().setC2S(c2s).build();
        int seqNo = qot.getBasicQot(req);
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
//        System.out.printf("Send QotSub: %d\n", seqNo);
    }

    public void subOrderBook(String stock) {
        List<Integer> subTypeList = new ArrayList<>();
        subTypeList.add(QotCommon.SubType.SubType_OrderBook_VALUE);

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
//        System.out.printf("Send QotSub: %d\n", seqNo);
    }

    public void unSubBasicQuote(String stock) {
        List<Integer> subTypeList = new ArrayList<>();
        subTypeList.add(QotCommon.SubType.SubType_Basic_VALUE);

        QotCommon.Security sec = QotCommon.Security.newBuilder()
          .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
          .setCode(stock)
          .build();
        QotSub.C2S c2s = QotSub.C2S.newBuilder()
          .addSecurityList(sec)
          .addAllSubTypeList(subTypeList)
          .setIsSubOrUnSub(false)
          //          .setIsRegOrUnRegPush(true)
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
    }

    @Override
    public void onReply_RequestRehab(FTAPI_Conn client, int nSerialNo, QotRequestRehab.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotRequestRehab failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                List<QotCommon.Rehab> rehabListList = rsp.getS2COrBuilder().getRehabListList();
                String json = JsonFormat.printer().print(rsp);
                System.out.printf("Receive QotRequestRehab: %s\n", json);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    public void getSecuritySnapshot(String stock) {
        QotCommon.Security sec = QotCommon.Security.newBuilder()
          .setMarket(QotCommon.QotMarket.QotMarket_HK_Security_VALUE)
          .setCode(stock)
          .build();
        QotGetSecuritySnapshot.C2S c2s = QotGetSecuritySnapshot.C2S.newBuilder()
          .addSecurityList(sec)
          .build();
        QotGetSecuritySnapshot.Request req = QotGetSecuritySnapshot.Request.newBuilder().setC2S(c2s).build();
        int seqNo = qot.getSecuritySnapshot(req);
    }

    @Override
    public void onReply_GetSecuritySnapshot(FTAPI_Conn client, int nSerialNo, QotGetSecuritySnapshot.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotGetSecuritySnapshot failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                String json = JsonFormat.printer().print(rsp);
                QotGetSecuritySnapshot.Snapshot snapshot = rsp.getS2COrBuilder().getSnapshotList(0);
                String code = snapshot.getBasic().getSecurity().getCode();
                QotGetSecuritySnapshot.OptionSnapshotExData optionExData = snapshot.getOptionExData();

                System.out.println(code + "\t" + optionExData.getImpliedVolatility());
                //                System.out.printf("Receive QotGetSecuritySnapshot: %s\n", json);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        FTAPI.init();
        BasicQuoteDemo quote = new BasicQuoteDemo();
        quote.start();

        quote.subOrderBook("TCH240530C390000");
        quote.subBasicQuote("TCH240530C390000");
        for (int i = 0; i < 100; i++) {
            quote.getSecuritySnapshot("TCH240530C390000");
            Thread.sleep(1000);
        }
        quote.getRehab("DPST");
        quote.subBasicQuote("FUTU");
        quote.getSubInfo();

        quote.end();
        quote.start();
        quote.unSubBasicQuote("FUTU");
        quote.getSubInfo();

        Map<String, String> stockFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2023daily/");
        Set<String> stockSet = stockFileMap.keySet();
        int count = 0;
        for (String stock : stockSet) {
            quote.subBasicQuote("FUTU");
            quote.getBasicQot("FUTU");
            if (queue.size() > 100) {
                StockQuote remove = queue.remove();

            }
        }
        quote.subBasicQuote("FUTU");
        quote.subBasicQuote("AAPL");
        quote.subBasicQuote("AMZN");
        quote.subBasicQuote("TSLA");
        quote.subBasicQuote("JPM");
        quote.subBasicQuote("TSM");
        quote.subBasicQuote("BA");
        quote.subBasicQuote("CSCO");
        quote.subBasicQuote("CVCO");

        //        quote.getSubInfo();

        while (true) {
            try {
                Thread.sleep(1000);
                quote.getBasicQot("FUTU");
                quote.getBasicQot("AAPL");
                quote.getBasicQot("AMZN");
                quote.getBasicQot("TSLA");
                quote.getBasicQot("JPM");
                quote.getBasicQot("TSM");
                quote.getBasicQot("BA");
                quote.getBasicQot("CSCO");
                quote.getBasicQot("CVCO");
            } catch (InterruptedException exc) {

            }
        }
    }

}
