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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
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
    private Map<String/* code */, Double/* bid price */> oriCodeToBidMap = Maps.newHashMap(); // 原始摆盘未过滤
    private Map<String/* code */, Double/* ask price */> oriCodeToAskMap = Maps.newHashMap(); // 原始摆盘未过滤
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
                    double delta = optionExData.getDelta();
                    double gamma = optionExData.getGamma();
                    double theta = optionExData.getTheta();
                    double vega = optionExData.getVega();
                    optionIvTimeMap.put(code, impliedVolatility + "|" + updateTime);
                    optionTradeMap.put(code, curPrice);
                    boolean showTradePrice = MapUtils.getBoolean(showTradePriceMap, code, false);
                    if (showTradePrice) {
                        log.info("option cur price: code={}\tprice={}", code, curPrice);
                    } else {
                        log.info("update futu iv and curPrice: code={}\tiv={}\tdelta={}\tgamma={}\ttheta={}\tvega={}\tprice={}\ttime={}", code, impliedVolatility, delta, gamma, theta, vega, curPrice, updateTime);
                    }
                }
            }
        }
    }

    @Override
    public void onPush_UpdateOrderBook(FTAPI_Conn client, QotUpdateOrderBook.Response rsp) {
        try {
            if (rsp.getRetType() != 0) {
                System.out.printf("QotUpdateOrderBook failed: %s\n", rsp.getRetMsg());
            } else {
                QotUpdateOrderBook.S2C s2C = rsp.getS2C();
                String code = s2C.getSecurity().getCode();
                List<QotCommon.OrderBook> orderBookAskListList = s2C.getOrderBookAskListList();
                List<QotCommon.OrderBook> orderBookBidListList = s2C.getOrderBookBidListList();
                Double bidPrice = null, askPrice = null;
                if (CollectionUtils.isNotEmpty(orderBookBidListList)) {
                    QotCommon.OrderBook orderBook = orderBookBidListList.get(0);
                    double price = orderBook.getPrice();
                    long volume = orderBook.getVolume();
                    //                System.out.print(code + " bid price=" + bidPrice + "\tvolume=" + volume);
                    //                log.info("futu quote. code={}\tbidPrice={}\tbidVol={}", code, bidPrice, volume);
                    if (volume >= 5 && price != 0) {
                        bidPrice = price;
                    }
                    oriCodeToBidMap.put(code, bidPrice);
                }
                if (CollectionUtils.isNotEmpty(orderBookAskListList)) {
                    QotCommon.OrderBook orderBook = orderBookAskListList.get(0);
                    double price = orderBook.getPrice();
                    long volume = orderBook.getVolume();
                    //                System.out.print(code + " ask price=" + askPrice + "\tvolume=" + volume);
                    //                log.info("futu quote. code={}\taskPrice={}\taskVol={}", code, askPrice, volume);
                    if (volume >= 5 && price != 0) {
                        askPrice = price;
                    }
                    oriCodeToAskMap.put(code, askPrice);
                }
                Double lastBid = codeToBidMap.get(code);
                Double lastAsk = codeToAskMap.get(code);
                if (bidPrice != null && askPrice != null) {
                    if (lastBid == null || lastAsk == null) { // 如果没有最新摆盘，则put
                        codeToBidMap.put(code, bidPrice);
                        codeToAskMap.put(code, askPrice);
                    } else {
                        if (!(bidPrice.compareTo(lastBid) < 0 && askPrice.compareTo(lastAsk) > 0)) { // 如果bid小于最新bid，且ask大于最新ask，则认为是无效摆盘
                            codeToBidMap.put(code, bidPrice);
                            codeToAskMap.put(code, askPrice);
                        }
                    }
                } else if (bidPrice != null) {
                    codeToBidMap.put(code, bidPrice);
                } else if (askPrice != null) {
                    codeToAskMap.put(code, askPrice);
                }
                log.info("futu quote. code={}\tbidPrice={}\taskPrice={}\toriBidPrice={}\toriAksPrice={}", code, codeToBidMap.get(code), codeToAskMap.get(code), oriCodeToBidMap.get(code), oriCodeToAskMap.get(code));
                //            String data = String.format("%.2f|%.2f", bidPrice, askPrice);
                //            System.out.println(code + "\t" + data);
            }
        } catch (Exception e) {
            log.error("onPush_UpdateOrderBook error.", e);
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

    public void addUserSecurity(List<String> codeList, String groupName, int market) {
        for (String code : codeList) {
            QotCommon.Security sec = QotCommon.Security.newBuilder()
              .setMarket(market)
              .setCode(code)
              .build();
            QotModifyUserSecurity.C2S c2s = QotModifyUserSecurity.C2S.newBuilder()
              .setGroupName(groupName)
              .setOp(QotModifyUserSecurity.ModifyUserSecurityOp.ModifyUserSecurityOp_Add_VALUE)
              .addSecurityList(sec)
              .build();
            QotModifyUserSecurity.Request req = QotModifyUserSecurity.Request.newBuilder().setC2S(c2s).build();
            int seqNo = qot.modifyUserSecurity(req);
        }
    }

    public void addUsUserSecurity(String code) {
        addUserSecurity(Lists.newArrayList(code), "美股观察", QotCommon.QotMarket.QotMarket_US_Security_VALUE);
    }

    public void addChinaUserSecurity(String code) {
        if (StringUtils.startsWith(code, "0")) {
            addUserSecurity(Lists.newArrayList(code), "a股测试", QotCommon.QotMarket.QotMarket_CNSZ_Security_VALUE);
        } else {
            addUserSecurity(Lists.newArrayList(code), "a股测试", QotCommon.QotMarket.QotMarket_CNSH_Security_VALUE);
        }
    }

    public static void main(String[] args) throws Exception {
        Double a = 1.4;
        Double b = 1.3;
        System.out.println(a.compareTo(b));
        FTAPI.init();
        BasicQuote quote = new BasicQuote();
        quote.start();

        List<String> codeList = Lists.newArrayList("000612","000973","000972","002918","000735","603132","601198","603377","603015","603017","002905","002908","603363","600094","600097","603123","603002","603126","000993","000751","603229","000633","000510","000759","000636","002816","603117","603599","000980","002928","000504","000509","000507","600193","603103","600076","603585","603227","603106","603328","000932","000816","603693","600063","601033","603697","601156","600187","600067","603458","603577","603578","603336","601018","000802","000922","000928","000809","600053","603686","600176","600299","600178","600179","000959","000958","600282","600284","601010","601133","601015","600168","603798","000821","000820","000701","000708","000705","001914","603660","600392","600151","600152","601002","600155","600156","601003","600399","600279","603303","002996","002875","002512","002513","601901","002750","002630","600815","000697","600817","002758","002516","002638","002519","603098","605399","000582","002622","002501","002860","600802","002861","000567","002620","002626","002628","603081","603088","605388","000571","000691","002898","000595","002410","600916","002660","002782","002766","000584","002647","002526","002761","002641","002883","002520","002763","002769","002527","002649","605122","603065","603066","002890","605366","603188","000592","000531","000892","002951","000659","000899","000657","000419","603172","603055","605599","603177","000882","000886","000523","002828","000528","002708","002825","000529","605580","603161","603165","603162","603041","603042","603168","000672","002735","000797","002973","000796","002616","603390","603033","603399","603278","603155","000680","605337","603038","002721","002600","000782","002724","002603","002966","002609","000547","000426","000668","002606","603020","000791","603027","603028","600897","002479","600777","601989","603801","600658","600416","600538","603808","002598","603806","000061","002120","002122","600782","601872","600300","002480","002360","600644","600523","002226","001258","002106","600525","600405","600648","002343","002586","601619","002224","002225","002110","002111","600892","600651","600774","002259","600513","002019","600636","603901","600516","601606","600759","002498","601608","002015","002378","002016","002379","002258","002020","002263","002385","601611","002382","600622","002248","600744","601958","002244","002124","002245","600507","002246","002488","002489","600509","002373","002494","002011","002254","002250","002677","000014","002679","002558","600615","002673","600857","600979","000016","002554","600739","600619","002682","002440","600981","600982","600983","600863","002787","002424","600843","002546","002789","001216","002426","002427","002662","001331","600846","600847","600727","002786","000488","002423","000009","002309","002790","002793","002672","600973","000157","002215","600710","002337","002458","601801","600955","600834","002453","002696","002454","002697","001366","000037","002456","600719","605050","605055","002340","002204","001236","002446","002207","000029","002200","600703","002322","600825","002686","002202","600705","000026","002324","600829","002329","002208","002209","605166","002570","002693","002451","000030","601108","002079","600380","600023","603535","600268","603759","603639","603637","601339","603879","002069","002191","600490","603880","600491","600130","601101","600495","601222","600133","600497","600255","002195","003042","600358","603869","600359","000911","000910","000917","600360","603993","601330","600241","600121","600243","603997","600366","600468","600105","600228","603978","603739","000902","000903","002090","600470","603982","002098","002099","601200","600595","600232","600233","600597","002094","600234","601566","600215","600216","603848","600459","603725","002035","002399","002038","002042","600582","002043","600343","600222","601798","002041","603836","601777","000088","600207","603719","002145","002024","600208","002394","600330","002033","002275","600452","603601","603843","600696","002391","002030","600556","600435","600558","603703","603828","003032","600320","002067","600203","600667","600425","600789","601515","600428","600429","603817","600550","002176","002170","600310","002173");
        for (String code : codeList) {
            quote.addChinaUserSecurity(code);
            Thread.sleep(1000);
        }
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
        quote.subBasicQuote("TSLA241129P335000");
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
