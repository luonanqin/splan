package luonq.futu;

import bean.OrderFill;
import bean.StockPosition;
import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTAPI_Conn_Trd;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.FTSPI_Trd;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetBasicQot;
import com.futu.openapi.pb.QotRequestTradeDate;
import com.futu.openapi.pb.QotSub;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetFunds;
import com.futu.openapi.pb.TrdGetHistoryOrderList;
import com.futu.openapi.pb.TrdGetMaxTrdQtys;
import com.futu.openapi.pb.TrdGetOrderList;
import com.futu.openapi.pb.TrdGetPositionList;
import com.futu.openapi.pb.TrdModifyOrder;
import com.futu.openapi.pb.TrdPlaceOrder;
import com.futu.openapi.pb.TrdSubAccPush;
import com.futu.openapi.pb.TrdUnlockTrade;
import com.futu.openapi.pb.TrdUpdateOrder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.protobuf.util.JsonFormat;
import lombok.extern.slf4j.Slf4j;
import luonq.polygon.RealTimeDataWS_DB;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import util.MD5Util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static util.Constants.TRADE_ERROR_CODE;
import static util.Constants.TRADE_PROHIBT_CODE;

/**
 * Created by Luonanqin on 2022/12/22.
 */
@Slf4j
public class TradeApi implements FTSPI_Trd, FTSPI_Qot, FTSPI_Conn {

    public static String pwd = MD5Util.calcMD5("134931");
    public static long simulateUsAccountId = 7681786L;

    private long accountId = 281756460288713754L;
    private int tradeEnv = TrdCommon.TrdEnv.TrdEnv_Real_VALUE;
    private int tradeMarket = TrdCommon.TrdMarket.TrdMarket_US_VALUE;

    private AtomicDouble remainCash = new AtomicDouble(0);
    private AtomicLong orderId = new AtomicLong(0);
    private AtomicLong modifyOrderId = new AtomicLong(0);
    private AtomicDouble quote = new AtomicDouble(0);
    private Map<Long, LinkedBlockingQueue<OrderFill>> orderFillMap = Maps.newHashMap();
    private BlockingQueue<Map<String, StockPosition>> stockPositionBlock = new LinkedBlockingQueue<>();
    private BlockingQueue<Long> stopLossOrder = new LinkedBlockingQueue<>();
    private AtomicInteger maxCashBuy = new AtomicInteger(-2);
    private Set<String> stopLossStockSet = Sets.newHashSet();
    private Map<String, Long> curOrderMap = Maps.newHashMap();

    FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();
    FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();

    public FTAPI_Conn_Trd getTrd() {
        return trd;
    }

    public TradeApi() {
        trd.setClientInfo("javaclient", 1);  //设置客户端信息
        trd.setConnSpi(this);  //设置连接回调
        trd.setTrdSpi(this);   //设置交易回调
        qot.setClientInfo("javaclient", 1);  //设置客户端信息
        qot.setConnSpi(this);  //设置连接回调
        qot.setQotSpi(this);   //设置行情回调
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public void useRealEnv() {
        tradeEnv = TrdCommon.TrdEnv.TrdEnv_Real_VALUE;
    }

    public void useSimulateEnv() {
        tradeEnv = TrdCommon.TrdEnv.TrdEnv_Simulate_VALUE;
    }

    public void clearStopLossStockSet() {
        stopLossStockSet.clear();
    }

    public OrderFill getOrderFill(long orderId, long timeoutForSecond) {
        try {
            return orderFillMap.get(orderId).poll(timeoutForSecond, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("getOrderFill error. orderId={}" + orderId + ", error=" + e.getMessage());
        }
        return null;
    }

    public void start() {
        trd.initConnect("127.0.0.1", (short) 11111, false);
        qot.initConnect("127.0.0.1", (short) 11111, false);
        log.info("trade api initialize staring...");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException exc) {
        }
        log.info("trade api initialize finish start!");
    }

    @Override
    public void onInitConnect(FTAPI_Conn client, long errCode, String desc) {
        log.info("TradeApi onInitConnect: ret={} desc={} connID={}", errCode, desc, client.getConnectID());
        if (errCode != 0) {
            return;
        }

        TrdSubAccPush.C2S c2s = TrdSubAccPush.C2S.newBuilder()
          .addAccIDList(accountId)
          .build();
        TrdSubAccPush.Request req = TrdSubAccPush.Request.newBuilder().setC2S(c2s).build();
        trd.subAccPush(req);
    }

    // 解锁接口，只需要解锁一次
    public void unlock() {
        TrdUnlockTrade.C2S c2s = TrdUnlockTrade.C2S.newBuilder()
          .setPwdMD5(pwd) // 密码md5小写
          .setUnlock(true)
          .setSecurityFirm(TrdCommon.SecurityFirm.SecurityFirm_FutuSecurities_VALUE) // account返回的securityFirm
          .build();
        TrdUnlockTrade.Request req = TrdUnlockTrade.Request.newBuilder().setC2S(c2s).build();
        trd.unlockTrade(req);
        //        log.info("Send TrdUnlockTrade: %d\n", seqNo);
    }

    // 获取资金接口
    public double getFunds() {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();
        TrdGetFunds.C2S c2s = TrdGetFunds.C2S.newBuilder()
          .setHeader(header)
          .build();
        TrdGetFunds.Request req = TrdGetFunds.Request.newBuilder().setC2S(c2s).build();
        trd.getFunds(req);
        while (true) {
            if (remainCash.get() != 0) {
                return remainCash.getAndSet(0);
            }
        }
    }

    // 获取指定股票的持仓
    public Map<String, StockPosition> getPositionMap(String... code) {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();
        TrdGetPositionList.C2S.Builder c2sBuilder = TrdGetPositionList.C2S.newBuilder()
          .setHeader(header);
        if (ArrayUtils.isNotEmpty(code)) {
            List<String> codeList = Arrays.stream(code).collect(Collectors.toList());
            c2sBuilder.setFilterConditions(TrdCommon.TrdFilterConditions.newBuilder().addAllCodeList(codeList).build());
        }
        TrdGetPositionList.C2S c2s = c2sBuilder.build();
        TrdGetPositionList.Request req = TrdGetPositionList.Request.newBuilder().setC2S(c2s).build();
        trd.getPositionList(req);

        try {
            Map<String, StockPosition> positionmap = stockPositionBlock.take();
            return positionmap;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 下单接口
    public void placeOrder(String code) {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();
        TrdPlaceOrder.C2S c2s = TrdPlaceOrder.C2S.newBuilder()
          .setPacketID(trd.nextPacketID())
          .setHeader(header)
          .setTrdSide(TrdCommon.TrdSide.TrdSide_Buy_VALUE)
          .setOrderType(TrdCommon.OrderType.OrderType_Market_VALUE)
          .setSecMarket(TrdCommon.TrdSecMarket.TrdSecMarket_US_VALUE)
          .setCode(code)
          .setQty(1)
          .setPrice(28)
          .build();
        TrdPlaceOrder.Request req = TrdPlaceOrder.Request.newBuilder().setC2S(c2s).build();
        int seqNo = trd.placeOrder(req);
        log.info("Send TrdPlaceOrder: {}", seqNo);
    }

    // 市价单买入
    public long placeMarketBuyOrder(String code, double count) {
        return placeOrder(code, count, null, TrdCommon.TrdSide.TrdSide_Buy_VALUE);
    }

    // 市价单卖出
    public long placeMarketSellOrder(String code, double count) {
        return placeOrder(code, count, null, TrdCommon.TrdSide.TrdSide_Sell_VALUE);
    }

    // 限价单买入
    public long placeNormalBuyOrder(String code, double count, double price) {
        return placeOrder(code, count, price, TrdCommon.TrdSide.TrdSide_Buy_VALUE);
    }

    // 限价单卖出
    public long placeNormalSellOrder(String code, double count, double price) {
        return placeOrder(code, count, price, TrdCommon.TrdSide.TrdSide_Sell_VALUE);
    }

    // 下单接口，指定数量和价格，有价格就是限价单，没价格就是市价单，返回订单ID
    private long placeOrder(String code, double count, Double price, int trdSide) {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();

        TrdPlaceOrder.C2S.Builder c2sBuilder = TrdPlaceOrder.C2S.newBuilder()
          .setPacketID(trd.nextPacketID())
          .setHeader(header)
          .setTrdSide(trdSide)
          .setSecMarket(TrdCommon.TrdSecMarket.TrdSecMarket_US_VALUE)
          .setCode(code)
          .setQty(count);
        if (price != null) {
            c2sBuilder.setOrderType(TrdCommon.OrderType.OrderType_Normal_VALUE).setPrice(price);
        } else {
            c2sBuilder.setOrderType(TrdCommon.OrderType.OrderType_Market_VALUE);
        }
        TrdPlaceOrder.C2S c2s = c2sBuilder.build();

        TrdPlaceOrder.Request req = TrdPlaceOrder.Request.newBuilder().setC2S(c2s).build();
        trd.placeOrder(req);
        while (true) {
            if (orderId.get() != 0) {
                return orderId.getAndSet(0);
            }
        }
    }

    // 下单接口，专用于止损市价单
    public long placeOrderForLossMarket(String code, double count, Double auxPrice) {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();

        TrdPlaceOrder.C2S.Builder c2sBuilder = TrdPlaceOrder.C2S.newBuilder()
          .setPacketID(trd.nextPacketID())
          .setHeader(header)
          .setTrdSide(TrdCommon.TrdSide.TrdSide_Sell_VALUE)
          .setSecMarket(TrdCommon.TrdSecMarket.TrdSecMarket_US_VALUE)
          .setCode(code)
          .setQty(count)
          .setOrderType(TrdCommon.OrderType.OrderType_Stop_VALUE)
          .setAuxPrice(auxPrice);

        TrdPlaceOrder.C2S c2s = c2sBuilder.build();
        TrdPlaceOrder.Request req = TrdPlaceOrder.Request.newBuilder().setC2S(c2s).build();
        trd.placeOrder(req);
        while (true) {
            if (orderId.get() != 0) {
                return orderId.getAndSet(0);
            }
        }
    }

    // 撤单
    public long cancelOrder(long orderId) {
        log.info("cancelOrder. orderId={}", orderId);
        return modifyOrder(orderId, TrdCommon.ModifyOrderOp.ModifyOrderOp_Cancel_VALUE, 0, 0);
    }

    public long upOrderPrice(long orderId, double qty, double price) {
        return modifyOrder(orderId, TrdCommon.ModifyOrderOp.ModifyOrderOp_Normal_VALUE, qty, price);
    }

    // 改单（修改、撤单、删除等），返回操作结果码
    public long modifyOrder(long orderId, int modifyOrderOp, double qty, double price) {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();
        TrdModifyOrder.C2S c2s = TrdModifyOrder.C2S.newBuilder()
          .setPacketID(trd.nextPacketID())
          .setHeader(header)
          .setOrderID(orderId)
          .setModifyOrderOp(modifyOrderOp)
          .setQty(qty)
          .setPrice(price)
          .build();
        TrdModifyOrder.Request req = TrdModifyOrder.Request.newBuilder().setC2S(c2s).build();
        trd.modifyOrder(req);
        while (true) {
            if (modifyOrderId.get() != 0) {
                return modifyOrderId.getAndSet(0);
            }
        }
    }

    // 订阅报价
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
          .build();
        QotSub.Request req = QotSub.Request.newBuilder().setC2S(c2s).build();
        int seqNo = qot.sub(req);
        log.info("subcribe quote: " + stock);
    }

    // 获取报价
    public double getBasicQot(String stock) {
        QotCommon.Security sec = QotCommon.Security.newBuilder()
          .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
          .setCode(stock)
          .build();
        QotGetBasicQot.C2S c2s = QotGetBasicQot.C2S.newBuilder()
          .addSecurityList(sec)
          .build();
        QotGetBasicQot.Request req = QotGetBasicQot.Request.newBuilder().setC2S(c2s).build();
        int seqNo = qot.getBasicQot(req);
        log.info("try to get basic qot: " + stock);
        while (true) {
            if (quote.get() != 0) {
                return quote.getAndSet(0);
            }
        }
    }

    public void getHistoryOrderList(String code) {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();
        TrdCommon.TrdFilterConditions filter = TrdCommon.TrdFilterConditions.newBuilder()
          .addCodeList(code)
          .setBeginTime("2023-08-11 00:00:00")
          .setEndTime("2023-08-12 00:00:00")
          .build();
        TrdGetHistoryOrderList.C2S c2s = TrdGetHistoryOrderList.C2S.newBuilder()
          .setHeader(header)
          .setFilterConditions(filter)
          .build();
        TrdGetHistoryOrderList.Request req = TrdGetHistoryOrderList.Request.newBuilder().setC2S(c2s).build();
        int seqNo = trd.getHistoryOrderList(req);
    }

    public Map<String, Long> getOrderList() throws InterruptedException {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();
        TrdGetOrderList.C2S c2s = TrdGetOrderList.C2S.newBuilder()
          .setHeader(header)
          .build();
        TrdGetOrderList.Request req = TrdGetOrderList.Request.newBuilder().setC2S(c2s).build();
        int seqNo = trd.getOrderList(req);
        for (int i = 0; i < 3; i++) {
            if (MapUtils.isEmpty(curOrderMap)) {
                TimeUnit.SECONDS.sleep(1);
            } else {
                break;
            }
        }
        return curOrderMap;
    }

    public int getMaxCashBuy(String code, double price) {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();
        TrdGetMaxTrdQtys.C2S c2s = TrdGetMaxTrdQtys.C2S.newBuilder()
          .setHeader(header)
          .setOrderType(TrdCommon.OrderType.OrderType_Normal_VALUE)
          .setCode(code)
          .setPrice(price)
          .setSecMarket(TrdCommon.TrdSecMarket.TrdSecMarket_US_VALUE)
          .build();
        TrdGetMaxTrdQtys.Request req = TrdGetMaxTrdQtys.Request.newBuilder().setC2S(c2s).build();
        trd.getMaxTrdQtys(req);

        while (true) {
            if (maxCashBuy.get() != -2) {
                return maxCashBuy.getAndSet(-2);
            }
        }
    }

    public void getTradeDate() {
        QotRequestTradeDate.C2S c2s = QotRequestTradeDate.C2S.newBuilder()
          .setMarket(QotCommon.TradeDateMarket.TradeDateMarket_US_VALUE)
          .setBeginTime("2024-01-01")
          .setEndTime("2024-12-31")
          .build();
        QotRequestTradeDate.Request req = QotRequestTradeDate.Request.newBuilder().setC2S(c2s).build();
        int seqNo = qot.requestTradeDate(req);
    }

    @Override
    public void onDisconnect(FTAPI_Conn client, long errCode) {
        log.info("Qot onDisConnect: {}", errCode);
    }

    // 订单成交回调
    @Override
    public void onPush_UpdateOrder(FTAPI_Conn client, TrdUpdateOrder.Response rsp) {
        if (rsp.getRetType() != 0) {
            log.error("TrdUpdateOrder failed: {}", rsp.getRetMsg());
        } else {
            try {
                TrdCommon.Order order = rsp.getS2COrBuilder().getOrder();
                long orderID = order.getOrderID();
                int orderStatus = order.getOrderStatus();
                double fillQty = order.getFillQty();
                double fillAvgPrice = order.getFillAvgPrice();
                if (orderStatus != TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE) {
                    //                    log.info("fillQty: " + fillQty + ", fillAvgPrice: " + fillAvgPrice);
                    return;
                }

                OrderFill fill = new OrderFill();
                fill.setOrderID(orderID);
                fill.setCode(order.getCode());
                fill.setName(order.getName());
                fill.setPrice(order.getPrice());
                fill.setAvgPrice(order.getFillAvgPrice());
                fill.setCreateTime(order.getCreateTime());
                fill.setUpdateTimestamp(order.getUpdateTimestamp());
                fill.setCount(order.getQty());
                fill.setTradeSide(order.getTrdSide());
                if (!orderFillMap.containsKey(orderID)) {
                    orderFillMap.put(orderID, new LinkedBlockingQueue<>(10));
                }
                orderFillMap.get(orderID).offer(fill);
                log.info("update order: {}", fill);

                //                if (fill.getTradeSide() == 1 && tradeEnd) {
                //                    log.info("ready to place stop loss");
                //                    if (!stopLossStockSet.contains(fill.getCode())) {
                //                        placeStopLossOrder(fill);
                //                    } else {
                //                        log.info("stopLossStockSet contains {}", fill.getCode());
                //                    }
                //                }
            } catch (Exception e) {
                log.error("onPush_UpdateOrder error.", e);
            }
        }
    }

    public void placeStopLossOrder(OrderFill orderFill) {
        if (orderFill == null) {
            log.info("order fill is null");
            return;
        }

        String code = orderFill.getCode();
        double canSellQty = orderFill.getCount();
        double costPrice = orderFill.getAvgPrice();
        double auxPrice = BigDecimal.valueOf(costPrice * (1 - RealTimeDataWS_DB.LOSS_RATIO)).setScale(3, BigDecimal.ROUND_DOWN).doubleValue();

        long orderId = placeOrderForLossMarket(code, canSellQty, auxPrice);
        log.info(code + " placeStopLoss market order. qty=" + canSellQty + ", auxPrice=" + auxPrice + ", orderId：" + orderId);
        stopLossStockSet.add(code);
    }

    // 解锁回调
    @Override
    public void onReply_UnlockTrade(FTAPI_Conn client, int nSerialNo, TrdUnlockTrade.Response rsp) {
        if (rsp.getRetType() != 0) {
            log.error("TrdUnlockTrade failed: {}", rsp.getRetMsg());
        } else {
            try {
                log.info("unlock success");
                //                            String json = JsonFormat.printer().print(rsp);
                //                            log.info("Receive TrdUnlockTrade: %s\n", json);
            } catch (Exception e) {
                log.error("onReply_UnlockTrade error.", e);
            }
        }
    }

    // 资金回调
    @Override
    public void onReply_GetFunds(FTAPI_Conn client, int nSerialNo, TrdGetFunds.Response rsp) {
        if (rsp.getRetType() != 0) {
            log.error("TrdGetFunds failed: {}", rsp.getRetMsg());
        } else {
            try {
                //                String json = JsonFormat.printer().print(rsp);
                //                log.info("Receive TrdGetFunds: %s\n", json);
                double cash = rsp.getS2COrBuilder().getFunds().getCash();
                remainCash.set(cash);
                log.info("onReply_GetFunds: {}", cash);
            } catch (Exception e) {
                log.error("onReply_GetFunds error.", e);
            }
        }
    }

    // 持仓回调
    @Override
    public void onReply_GetPositionList(FTAPI_Conn client, int nSerialNo, TrdGetPositionList.Response rsp) {
        if (rsp.getRetType() != 0) {
            log.error("TrdGetPositionList failed: {}", rsp.getRetMsg());
        } else {
            try {
                Map<String, StockPosition> positionMap = Maps.newHashMap();

                List<TrdCommon.Position> positionListList = rsp.getS2COrBuilder().getPositionListList();
                if (CollectionUtils.isNotEmpty(positionListList)) {
                    Map<String, TrdCommon.Position> codeToPositionMap = positionListList.stream().collect(Collectors.toMap(TrdCommon.Position::getCode, Function.identity()));
                    for (String code : codeToPositionMap.keySet()) {
                        TrdCommon.Position position = codeToPositionMap.get(code);
                        double canSellQty = position.getCanSellQty();
                        double costPrice = position.getCostPrice();
                        double price = position.getPrice();
                        if (canSellQty == 0) {
                            continue;
                        }

                        StockPosition stockPosition = new StockPosition();
                        stockPosition.setStock(code);
                        stockPosition.setCanSellQty(canSellQty);
                        stockPosition.setCostPrice(costPrice);
                        stockPosition.setCurrPrice(price);

                        positionMap.put(code, stockPosition);
                    }
                }

                //                String json = JsonFormat.printer().print(rsp);
                //                log.info("Receive TrdGetPositionList: %s\n", json);
                stockPositionBlock.offer(positionMap);
            } catch (Exception e) {
                log.error("onReply_GetPositionList error.", e);
            }
        }
    }

    // 下单回调
    @Override
    public void onReply_PlaceOrder(FTAPI_Conn client, int nSerialNo, TrdPlaceOrder.Response rsp) {
        if (rsp.getRetType() != 0) {
            log.error("TrdPlaceOrder failed: {}", rsp.getRetMsg());
            orderId.set(TRADE_ERROR_CODE);
        } else {
            try {
                long orderID = rsp.getS2COrBuilder().getOrderID();
                orderFillMap.put(orderID, new LinkedBlockingQueue<>());
                orderId.set(orderID);
                //                String json = JsonFormat.printer().print(rsp);
                //                log.info("Receive TrdPlaceOrder: %s\n", json);
            } catch (Exception e) {
                log.error("onReply_PlaceOrder error. ", e);
            }
        }
    }

    // 改单回调
    @Override
    public void onReply_ModifyOrder(FTAPI_Conn client, int nSerialNo, TrdModifyOrder.Response rsp) {
        if (rsp.getRetType() != 0) {
            String retMsg = rsp.getRetMsg();
            log.error("TrdModifyOrder failed: {}", retMsg);
            if (StringUtils.equals(retMsg, "此订单不支持此操作")) {
                modifyOrderId.set(TRADE_PROHIBT_CODE);
            } else {
                modifyOrderId.set(TRADE_ERROR_CODE);
            }
        } else {
            try {
                modifyOrderId.set(rsp.getS2C().getOrderID());
                //                String json = JsonFormat.printer().print(rsp);
                //                log.info("Receive TrdModifyOrder: %s\n", json);
            } catch (Exception e) {
                log.error("onReply_ModifyOrder error. ", e);
            }
        }
    }

    // 订阅报价回调
    @Override
    public void onReply_Sub(FTAPI_Conn client, int nSerialNo, QotSub.Response rsp) {
        if (rsp.getRetType() != 0) {
            log.error("Subcribe qot failed: {}", rsp.getRetMsg());
        } else {
            log.info("Subcribe qot success");
        }
    }

    // 获取报价回调
    @Override
    public void onReply_GetBasicQot(FTAPI_Conn client, int nSerialNo, QotGetBasicQot.Response rsp) {
        if (rsp.getRetType() != 0) {
            log.error("get basic qot failed: {}", rsp.getRetMsg());
            quote.set(-1);
        } else {
            QotGetBasicQot.S2C s2C = rsp.getS2C();
            List<QotCommon.BasicQot> basicQotListList = s2C.getBasicQotListList();
            for (QotCommon.BasicQot basicQot : basicQotListList) {
                String stock = basicQot.getSecurity().getCode();
                double curPrice = basicQot.getCurPrice();
                log.info("get basic qot. stock=" + stock + " price=" + curPrice);
                quote.set(curPrice);
                return;
            }
        }
    }

    @Override
    public void onReply_GetHistoryOrderList(FTAPI_Conn client, int nSerialNo, TrdGetHistoryOrderList.Response rsp) {
        if (rsp.getRetType() != 0) {
            log.error("TrdGetHistoryOrderLis failed: {}", rsp.getRetMsg());
        } else {
            try {
                String json = JsonFormat.printer().print(rsp);
                log.info("Receive GetHistoryOrderList: {}", json);
            } catch (Exception e) {
                log.error("onReply_GetHistoryOrderList error. ", e);
            }
        }
    }

    @Override
    public void onReply_GetMaxTrdQtys(FTAPI_Conn client, int nSerialNo, TrdGetMaxTrdQtys.Response rsp) {
        if (rsp.getRetType() != 0) {
            log.error("TrdGetMaxTrdQtys failed: {}", rsp.getRetMsg());
            maxCashBuy.set(TRADE_ERROR_CODE);
        } else {
            try {
                TrdCommon.MaxTrdQtys maxTrdQtys = rsp.getS2COrBuilder().getMaxTrdQtys();
                int count = (int) maxTrdQtys.getMaxCashBuy();
                maxCashBuy.set(count);
                log.info("onReply_GetMaxTrdQtys: " + count);
            } catch (Exception e) {
                log.error("onReply_GetMaxTrdQtys error. ", e);
            }
        }
    }

    @Override
    public void onReply_RequestTradeDate(FTAPI_Conn client, int nSerialNo, QotRequestTradeDate.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("QotRequestTradeDate failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                //                String json = JsonFormat.printer().print(rsp);
                //                System.out.printf("Receive QotRequestTradeDate: %s\n", json);
                List<QotRequestTradeDate.TradeDate> tradeDateListList = rsp.getS2C().getTradeDateListList();
                for (QotRequestTradeDate.TradeDate tradeDate : tradeDateListList) {
                    String time = tradeDate.getTime();
                    int tradeDateType = tradeDate.getTradeDateType();
                    System.out.println(time + ", " + tradeDateType);
                }
            } catch (Exception e) {
                log.error("onReply_RequestTradeDate error. ", e);
            }
        }
    }

    @Override
    public void onReply_GetOrderList(FTAPI_Conn client, int nSerialNo, TrdGetOrderList.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("TrdGetOrderList failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                //                String json = JsonFormat.printer().print(rsp);
                //                System.out.printf("Receive TrdGetOrderList: %s\n", json);
                List<TrdCommon.Order> orderList = rsp.getS2C().getOrderListList();
                if (CollectionUtils.isEmpty(orderList)) {
                    log.info("current order list is empty");
                } else {
                    for (TrdCommon.Order order : orderList) {
                        long orderID = order.getOrderID();
                        String code = order.getCode();
                        curOrderMap.put(code, orderID);
                    }
                }
            } catch (Exception e) {
                log.error("onReply_GetOrderList error.", e);
            }
        }
    }

    public static List<String> readFile(String filePath) {
        List<String> lineList = Lists.newArrayList();
        BufferedReader br = null;
        try {
            InputStream resourceAsStream = TradeApi.class.getResourceAsStream(filePath);
            br = new BufferedReader(new InputStreamReader(resourceAsStream));

            String line;
            while (StringUtils.isNotBlank(line = br.readLine())) {
                lineList.add(line);
            }
            br.close();
        } catch (Exception e) {
            log.error("can not find file: {}", filePath);
            return Lists.newArrayList();
        }
        return lineList;
    }

    public static void main(String[] args) {
        FTAPI.init();
        TradeApi trdDemo = new TradeApi();
        //        trdDemo.setAccountId(simulateUsAccountId);
        //        trdDemo.useSimulateEnv();
        trdDemo.useRealEnv();
        trdDemo.start();

        //        trdDemo.getTradeDate();

        //        trdDemo.unlock();
        double funds = trdDemo.getFunds();
        System.out.println(funds);
        //        Map<String, StockPosition> positionMap = trdDemo.getPositionMap("NTES");
        //        System.out.println(positionMap);
        //        long orderId = trdDemo.placeNormalBuyOrder("OLLI", 1, 60);
        //        System.out.println(orderId);
        //        long modifyOrderId = trdDemo.upOrderPrice(orderId, 1, 60.01);
        //        System.out.println(modifyOrderId);
        //        long modifyOrderId2 = trdDemo.upOrderPrice(orderId, 1, 60.02);
        //        System.out.println(modifyOrderId2);
        //        System.out.println(crmt);
        //        int count = trdDemo.getMaxCashBuy("AAPL", 190);
        //        System.out.println(count);
        //        trdDemo.placeOrder();
        //        trdDemo.modifyOrder();
        //        trdDemo.getHistoryOrderList("WWW");
        //        trdDemo.subBasicQuote("AAPL");
        //        double aapl = trdDemo.getBasicQot("AAPL");
        //        System.out.println(aapl);

        //        while (true) {
        //            try {
        //                Thread.sleep(1000);
        //            } catch (InterruptedException exc) {
        //
        //            }
        //        }
        System.out.println();
    }
}
