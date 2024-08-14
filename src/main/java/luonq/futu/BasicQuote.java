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
import com.futu.openapi.pb.QotGetUserSecurity;
import com.futu.openapi.pb.QotModifyUserSecurity;
import com.futu.openapi.pb.QotRequestRehab;
import com.futu.openapi.pb.QotSub;
import com.futu.openapi.pb.QotUpdateBasicQot;
import com.futu.openapi.pb.QotUpdateOrderBook;
import com.google.common.collect.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Created by Luonanqin on 2023/5/5.
 */
@Data
@Slf4j
public class BasicQuote implements FTSPI_Qot, FTSPI_Conn {


    FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();

    private Map<String, Double> stockToCurrPriceMap = Maps.newHashMap();
    private Map<String/* code */, Double/* bid price */> codeToBidMap = Maps.newHashMap();
    private Map<String/* code */, Double/* ask price */> codeToAskMap = Maps.newHashMap();
    private Map<String/* code */, Integer/* 1=call, 2=put */> optionTypeMap = Maps.newHashMap();
    private Map<String/* code */, String/* iv|updatetime */> optionIvTimeMap = Maps.newHashMap();
    private Map<String/* code */, Double/* trade */> optionTradeMap = Maps.newHashMap();
    private Map<String/* code */, Boolean/* show price */> showTradePriceMap = Maps.newHashMap();

    @Data
    public static class StockQuote {
        private String stock;
        private double price;
    }

    private static PriorityQueue<StockQuote> queue = new PriorityQueue<>(100, (o1, o2) -> (int) (o1.getPrice() - o2.getPrice()));

    public BasicQuote() {
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

    public String getOptionIvTimeMap(String code) {
        return MapUtils.getString(optionIvTimeMap, code, "");
    }

    public Double getOptionTrade(String code) {
        return MapUtils.getDouble(optionTradeMap, code, 0d);
    }

    public void setShowTradePrice(String code) {
        showTradePriceMap.put(code, true);
    }

    @Override
    public void onPush_UpdateBasicQuote(FTAPI_Conn client, QotUpdateBasicQot.Response rsp) {
        if (rsp.getRetType() != 0) {
            log.info("QotUpdateBasicQuote failed: {}", rsp.getRetMsg());
        } else {
            QotUpdateBasicQot.S2C s2C = rsp.getS2C();
            List<QotCommon.BasicQot> basicQotListList = s2C.getBasicQotListList();
            for (QotCommon.BasicQot basicQot : basicQotListList) {
                String updateTime = basicQot.getUpdateTime();
                String code = basicQot.getSecurity().getCode();
                double curPrice = basicQot.getCurPrice();
                QotCommon.OptionBasicQotExData optionExData = basicQot.getOptionExData();
                if (optionExData != null) {
                    double impliedVolatility = BigDecimal.valueOf(optionExData.getImpliedVolatility() / 100).setScale(4, RoundingMode.HALF_UP).doubleValue();
                    optionIvTimeMap.put(code, impliedVolatility + "|" + updateTime);
                    optionTradeMap.put(code, curPrice);
                    boolean showTradePrice = MapUtils.getBoolean(showTradePriceMap, code, false);
                    if (showTradePrice) {
                        log.info("option cur price: code={}\tprice={}", code, curPrice);
                    } else {
                        log.info("update futu iv and curPrice: code={}\tiv={}\tprice={}\ttime={}", code, impliedVolatility, curPrice, updateTime);
                    }
                }
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
            double bidPrice = 0d, askPrice = 0d;
            if (CollectionUtils.isNotEmpty(orderBookBidListList)) {
                QotCommon.OrderBook orderBook = orderBookBidListList.get(0);
                bidPrice = orderBook.getPrice();
                long volume = orderBook.getVolume();
                //                System.out.print(code + " bid price=" + bidPrice + "\tvolume=" + volume);
                //                log.info("futu quote. code={}\tbidPrice={}\tbidVol={}", code, bidPrice, volume);
                if (volume >= 5 && bidPrice != 0) {
                    codeToBidMap.put(code, bidPrice);
                }
            }
            if (CollectionUtils.isNotEmpty(orderBookAskListList)) {
                QotCommon.OrderBook orderBook = orderBookAskListList.get(0);
                askPrice = orderBook.getPrice();
                long volume = orderBook.getVolume();
                //                System.out.print(code + " ask price=" + askPrice + "\tvolume=" + volume);
                //                log.info("futu quote. code={}\taskPrice={}\taskVol={}", code, askPrice, volume);
                if (volume >= 5 && askPrice != 0) {
                    codeToAskMap.put(code, askPrice);
                }
            }
            log.info("futu quote. code={}\tbidPrice={}\taskPrice={}", code, codeToBidMap.get(code), codeToAskMap.get(code));
            //            String data = String.format("%.2f|%.2f", bidPrice, askPrice);
            //            System.out.println(code + "\t" + data);
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

    public void subBasicQuote(String code) {
        List<Integer> subTypeList = new ArrayList<>();
        subTypeList.add(QotCommon.SubType.SubType_Basic_VALUE);

        QotCommon.Security sec = QotCommon.Security.newBuilder()
          .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
          .setCode(code)
          .build();
        QotSub.C2S c2s = QotSub.C2S.newBuilder()
          .addSecurityList(sec)
          .addAllSubTypeList(subTypeList)
          .setIsSubOrUnSub(true)
          .setIsRegOrUnRegPush(true)
          .build();
        //        if (code.length() > 5) {
        //            int i = code.length() - 1;
        //            for (; i >= 0; i--) {
        //                if (code.charAt(i) < '0' || code.charAt(i) > '9') {
        //                    break;
        //                }
        //            }
        //            if (code.charAt(i) == 'C') {
        //                optionTypeMap.put(code, 1);
        //            } else if (code.charAt(i) == 'P') {
        //                optionTypeMap.put(code, 2);
        //            } else {
        //                optionTypeMap.put(code, 0);
        //            }
        //        }
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

    public void unSubOrderBook(String stock) {
        List<Integer> subTypeList = new ArrayList<>();
        subTypeList.add(QotCommon.SubType.SubType_OrderBook_VALUE);

        QotCommon.Security sec = QotCommon.Security.newBuilder()
          .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
          .setCode(stock)
          .build();
        QotSub.C2S c2s = QotSub.C2S.newBuilder()
          .addSecurityList(sec)
          .addAllSubTypeList(subTypeList)
          .setIsSubOrUnSub(false)
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
        //        System.out.printf("Send QotSub: %d\n", seqNo);
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
          .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
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
                double price = snapshot.getBasic().getCurPrice();
                //                if (!code.contains("24")) {
                //                    stockToCurrPriceMap.put(code, price);
                //                    return;
                //                }
                //                String stock = code.substring(0, code.indexOf("24"));
                //                if (!stockToCurrPriceMap.containsKey(stock)) {
                //                    return;
                //                }
                QotGetSecuritySnapshot.OptionSnapshotExData optionExData = snapshot.getOptionExData();
                double impliedVolatility = optionExData.getImpliedVolatility();
                double putPredictedValue = 0;
                //                double putPredictedValue = BaseUtils.getPutPredictedValue(stockToCurrPriceMap.get(stock), 126, 0.0526, impliedVolatility, "2024-06-17", "2024-06-21");

                System.out.println(code + "\t" + price + "\t" + putPredictedValue + "\t" + impliedVolatility + "\t" + optionExData.getPremium());

                //                System.out.printf("Receive QotGetSecuritySnapshot: %s\n", json);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    public void getUserSecurity() {
        QotGetUserSecurity.C2S c2s = QotGetUserSecurity.C2S.newBuilder()
          .setGroupName("观察")
          .build();
        QotGetUserSecurity.Request req = QotGetUserSecurity.Request.newBuilder().setC2S(c2s).build();
        int seqNo = qot.getUserSecurity(req);
    }

    @Override
    public void onReply_GetUserSecurity(FTAPI_Conn client, int nSerialNo, QotGetUserSecurity.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotGetUserSecurity failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                String json = JsonFormat.printer().print(rsp);
                System.out.printf("Receive QotGetUserSecurity: %s\n", json);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    public void addUserSecurity(String code) {
        QotCommon.Security sec = QotCommon.Security.newBuilder()
          .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
          .setCode(code)
          .build();
        QotModifyUserSecurity.C2S c2s = QotModifyUserSecurity.C2S.newBuilder()
          .setGroupName("观察")
          .setOp(QotModifyUserSecurity.ModifyUserSecurityOp.ModifyUserSecurityOp_Add_VALUE)
          .addSecurityList(sec)
          .build();
        QotModifyUserSecurity.Request req = QotModifyUserSecurity.Request.newBuilder().setC2S(c2s).build();
        int seqNo = qot.modifyUserSecurity(req);
    }

    public static void main(String[] args) throws Exception {
        FTAPI.init();
        BasicQuote quote = new BasicQuote();
        quote.start();

        //        quote.getUserSecurity();
        //        quote.addUserSecurity("AAPL");
        //        quote.addUserSecurity("AAPL240809C210000");
        //        quote.addUserSecurity("AAPL240809P207500");
        //        quote.subBasicQuote("U240816C15500");
        //        quote.subBasicQuote("SEDG240719P28000");
        //        quote.subBasicQuote("HUT240719C18500");
        //        quote.subBasicQuote("HUT240719P18000");
        //        quote.subBasicQuote("NVDA240719C121000");
        //        quote.unSubBasicQuote("TSLA240719C252500");
        quote.subBasicQuote("TSLA240816P207500");
        //        System.out.println(quote.getOptionIvTimeMap("TSLA240719C257500"));
        //        quote.getBasicQot("TSLA240719P255000");
        //                quote.getBasicQot("TSLA240719P255000");
        quote.subOrderBook("U240816C15500");
        //        quote.subOrderBook("WULF240705C5500");
        //        quote.subOrderBook("NVDA240628C127000");
        //        quote.subBasicQuote("NVDA240621P126000");
        for (int i = 0; i < 20000; i++) {
            //            quote.getSecuritySnapshot("NVDA");
            quote.getSecuritySnapshot("NVDA240621P126000");
            //            quote.getSecuritySnapshot("OXY240621C59000");
            //            quote.getSecuritySnapshot("AAPL240621C215000");
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
