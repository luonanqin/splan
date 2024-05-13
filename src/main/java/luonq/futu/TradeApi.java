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
import com.futu.openapi.pb.TrdGetMarginRatio;
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
import util.BaseUtils;
import util.Constants;
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

    public void getMarginRatio(List<String> codeList) {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();
        List<QotCommon.Security> securityList = Lists.newArrayList();
        for (String code : codeList) {
            QotCommon.Security security = QotCommon.Security.newBuilder()
              .setCode(code)
              .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
              .build();
            securityList.add(security);
        }
        TrdGetMarginRatio.C2S c2s = TrdGetMarginRatio.C2S.newBuilder()
          .setHeader(header)
          .addAllSecurityList(securityList)
          .build();
        TrdGetMarginRatio.Request req = TrdGetMarginRatio.Request.newBuilder().setC2S(c2s).build();
        trd.getMarginRatio(req);
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

    @Override
    public void onReply_GetMarginRatio(FTAPI_Conn client, int nSerialNo, TrdGetMarginRatio.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("TrdGetMarginRatio failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                List<TrdGetMarginRatio.MarginRatioInfo> marginRatioInfoListList = rsp.getS2C().getMarginRatioInfoListList();
                for (TrdGetMarginRatio.MarginRatioInfo marginRatioInfo : marginRatioInfoListList) {
                    String code = marginRatioInfo.getSecurity().getCode();
                    boolean isShortPermit = marginRatioInfo.getIsShortPermit();
                    System.out.println(code + "\t" + isShortPermit);
                }
            } catch (Exception e) {
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
        Map<String, String> dailyFileMap = null;
        try {
            dailyFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "week/");
        } catch (Exception e) {
            e.printStackTrace();
        }

        Set<String> stockSets = dailyFileMap.keySet();
        Set<String> invalidSet = Sets.newHashSet("CNXA","NGMS","ARRW","DCFC","EXPR","KAMN","SLAC","CVCY","SFE","STRC","APAC","BHG","NVIV","SLGC","SCTL","LGST","JAQC","SRC","NEPT","NVTA","CNDB","NETI","BVH","BKCC","GRCL","BYN","APTM","KLR","QFTA","PCTI","RAIN","GRPH","MCAF","TGH","CHS","KERN","CPE","LCA","DISA","DISH","SEAS","BLCM","SVFD","LIAN","CYT","KNTE","THRX","SNCE","PUYI","GBNH","WRAC","PEAK","ARIZ","JT","LIZI","DSKE","RCAC","PEPL","MDVL","ENCP","EAC","ENER","EAR","RCII","LAZY","CPSI","CHEA","ASAP","ASCA","LBBB","FICV","TRKA","TRMR","SOLO","NGM","SGEN","SOVO","KYCH","FIXX","FRLN","ATAK","GMDA","ACAX","VYNT","LCAA","ATCX","ATHX","LTHM","SPLK","FAZE","EGGF","FSR","CRGE","ACOR","EGLE","BODY","ACRX","HHLA","DMAQ","OPA","PGTI","VIEW","CASA","GIA","ALTU","ADEX","ADES","JOAN","GOL","ALYA","CBAY","GTH","BXRX","ADOC","AMAM","SIEN","XPDB","ICVX","AMEH","VAQC","LMDX","KRTX","PNT","VJET","RWLK","ONCR","NSTG","CSTR","JGGC","AMNB","AMTI","DWAC","SZZL","ONTX","NTCO","LEJU","EIGR","NCAC","LVOX","HARP","FLME","SASI","IMGN","ROVR","WETG","CCLP","TMST","TEDU","WNNR","CLIN","RAD","CDAY","NUBI","ESAC","PBAX","MIXT","RPT","CURO","OXUS","STAR","EBIX","MRTX","AYX","OHAA","TWOA","PBTS","IOAC","HCMA","DHCA","AGLE","STRE","APEN","BSMX","CEQP","APRN","PTRS","LYLT","TIG","WQGA","ORCC","SECO","AQUA","THRN","COUP","CS","MDGS","MCG","HWEL","RKTA","WAVC","AJRD","MEKA","PFHD","FINM","UMPQ","ISEE","OLIT","RMGC","TBSA","EGLX","GEEX","OSA","TCFC","BOXD","PHCF","DMYS","CBIO","ONCS","SIRE","GOGN","IDRA","RXDX","ERES","QUMU","PRTK","CDAK","BRDS","VLAT","VLDR","VCXA","STET","VLTA","SDC","SEV","STSA","BSAQ","DHHC","HLBZ","MSDA","BBBY","FWAC","KDNY","WPCB","SJR","RIDE","SLGG","SCUA","APGN","CEMI","YELL","KAL","SQZ","JATT","BBLN","VMGA","CNCE","APMI","SDAC","BTB","GRAY","GRCY","MBSC","RAAS","BWC","RADI","FOCS","SLVR","PTRA","GRIL","DICE","SUMO","GRNA","MTAC","TGR","OIIM","RJAC","FORG","CIH","VVNT","AHRN","KVSC","SMIH","ZING","EUCR","CFMS","PDCE","LCI","PLXP","TYDE","HMPT","HVBC","ORIA","MLAC","FGMC","IPVI","CVT","FPAC","SVFB","MTVC","EMBK","UTAA","DALS","PDOT","VNTR","UBA","SVNA","BLNG","AIMC","LSI","DRTT","DCP","CORS","DCT","HERA","GSQB","GSRM","SESN","AZRE","LION","UTME","GBRG","HEXO","IQMD","LITT","MLVF","PEAR","DMS","AZYO","CPAA","USX","DSEY","ARNC","CGRN","SNRH","MURF","SI","EVOJ","EVOP","VORB","PNAC","HWKZ","MMP","ARYE","OBNK","XM","CYAD","TRAQ","MMMB","AAWW","TIOA","ZT","JUGG","RCLF","OBSV","MTP","CPUH","ENOB","NHIC","JMAC","NYMX","JUPW","VPCB","PNTM","VQS","ENTF","DTEA","CHRA","ESM","TRTN","GLOP","NGC","CYXT","SGFY","METX","FISV","FACT","BVXV","ABST","SGHL","EOCW","BNNR","BWAC","CIDM","FRON","SPCM","ATAQ","ATCO","MNTV","VHNA","RUTH","SGTX","CIIG","SPKB","JNCE","CINC","FRC","FRG","ATNX","SPPI","OFC","CREC","WWE","ALBO","GVCI","ACQR","ATTO","FZT","JWAC","GMVD","PGRW","SYNH","TTCF","OSH","ITCB","GENQ","RENN","UNVR","GET","ALPA","GFX","BGCP","ALPS","ADAL","RETA","REVE","ADER","GLS","REUN","FSTX","LDHA","ICNC","BPAC","XPAX","ADMP","BGRY","TLGA","AURC","TCVA","CSII","MGTA","PKI","GFGD","FTEV","GNUS","DNAB","DNAD","QTEK","HILS","IDBA","PTE","LMNL","NBRV","ZEST","AVAC","AMOV","AMOT","LVAC","FTPA","GFOR","MPRA","HHC","ROCG","SRGA","AMRS","ROCC","LMST","FCRD","SIVB","PIAI","HMA","IMBI","AMYT","CCAI","HAPP","TMDI","HSC","RONI","MYOV","WEJO","YVR","DFFN","LVRA","GGAA","CTIC","TMKR","VBOC","ANGN","PRBM","PRDS","IBA","CLBR","UPTD","YTPG","ZEV","QTT","ANPC","QUOT","LFAC","PANA","HSKA","IMV","OXAC","RAM","PRVB","FMIV","ANZU","INDT","ISO","TETC","ERYP","INKA","CLXT","BRIV","AFTR","ALR","DGNU","ROC","AMV","BRMK","TWCB","RTL","ATY","NUVA","AUY","SCAQ","UIHC","HTGM","AGAC","PSPC","VLON","OPNT","SKYA","TWNI","AGFS","SCHN","AGGR","MAXR","SAL","HCNE","TOAC","SCU","SCOB","NVCN","STOR","MBAC","NMTR","SFT","JUN","SCPL","MSAC","GIAC","SJI","WPCA","BSGA","LGTO","ZYNE","BLI","APGB","PCCT","SQL","CENQ","SSU","SUAC","BSQR","NVSA","NETC","LHCG","NEWR","PTOC","CNNB","VECT","SUNL","TPBA","VEDU","AYLA","IPAX","HMAC","KSI","CCV","MCAE","SMAP","PCYG","CFFE","CIR","KNBE","CFIV","KVSA","NFNT","ELVT","FOXW","MCLD","THAC","CFRX","CTG","BCOR","MTRY","MLAI","IPVF","LHC","SVFA","BTWN","KNSW","EDTX","CXAC","SELB","GSMG","GSQD","DEN","NXGN","NOVN","ARBG","JCIC","ARCK","ARCE","COWN","ARGO","LIVB","UPH","HT","DNZ","AAIC","CPAR","CPAQ","BMAQ","MDNA","NM","MIT","ARTE","VGFC","TZPS","TRCA","OSTK","DBTX","MEAC","TRHC","VII","ABCM","VMW","IRNT","TALS","ABGI","PFDR","NCR","EFHT","MEOA","HORI","ASPA","BNFT","FRBN","SGII","FREQ","OTMO","FRGI","DLCA","DCRD","DTRT","PFSW","GDNR","TBCP","IBER","ACAQ","FRSG","PONO","WMC","ACDI","MFGP","FRXB","HPLT","BOAC","NAAC","MOBV","ACRO","CIXX","ATVI","GEHI","ITAQ","RVLP","TCDA","HYRE","NATI","ALOR","SHUA","KINZ","RNER","FSRX","CRZN","GLG","MOXC","VACC","VIVO","ICPT","KAII","AMAO","FTAA","BGSX","AMCI","FTCH","FCAX","VRTV","GWII","POW","HZNP","ONEM","HEP","AEAC","PYR","AVCT","AVEO","WEBR","EQRX","AEHA","AVID","LEGA","PICC","SAMA","SRNE","CTAQ","ANAC","VBLT","AERC","AVYA","PACW","IMPL","PACX","TMPO","PACI","CLAA","HSAQ","IMRA","CCVI","MICT","WWAC","PRPC","BIOC","BIOT","BIOS","ITQ","PAYA","RFP","IVC","NLTX","MIRO","LOCC","EBAC","AKU","LOKM","DKDCA","HCCI","VLNS","HCDI","LGAC","TWNK","ESTE","SCMA","BWV","NOGN","BMAC","MDWT","DUNE","GHL","BYTS","CLAY","PRSR","PATI");
        Set<String> validSet = Sets.newHashSet("SJM","BIG","SJW","FFIN","MBLY","SRE","ULTA","MSM","CPRX","PNFP","SFNC","ECL","CPRT","TRGP","VGR","ASAN","ASAI","GLBE","SWTX","FBK","FBP","WDC","OLED","NSA","NSC","WDS","PBI","PBH","XRX","PBR","BGNE","XPRO","BAX","LGIH","SCLX","JACK","SCL","BBDO","BSET","MBCN","APDN","BSGM","CENN","JRSH","TGAN","SRI","GILT","NYAX","ENLC","EFC","TRUE","WDH","PBF","ADIL","TCOA","ROAD","GWRS","SRDX","BPRN","HITI","NBTX","NSTS","STNG","SCM","SCSC","CVGI","NVGS","HCTI","BBDC","STSS","OCFCP","PBYI","SCRM","BGS","SLDB","SII","BBCP","BHE","STTK","NVFY","AGRI","HLGN","CVII","LPCN","BHR","HCVI","AXSM","BSFC","APCA","KMDA","VMCA","HLMN","BNR","PTEN","NVOS","VMEO","SPH","SPI","BOC","BON","EKSO","GIII","BBLG","FNVT","NVNO","HLLY","FNWB","FNWD","XBIO","XBIT","IXAQ","EBR","GCMG","CHAA","VOXR","TRDA","MSC","TIRX","KXIN","ECX","VEL","MTC","CYCN","VET","VEV","TITN","MVBF","CHCT","SOBR","EDN","CHCO","MTW","CPSS","CHCI","VFF","CPSH","DBVT","OSUR","BMRA","BEAT","TISI","IREN","MUX","CPTN","TREE","EEX","TACT","CPTK","RCMT","VHC","LSAK","ENLV","CHEK","GLBS","CHEF","GLBZ","SWSS","GLAD","RCON","EGY","WBX","RDIB","JMSB","FAT","QRHC","OTRK","NRC","EFTR","OCSL","NRP","FCF","BNOX","NSP","DCTH","TSBK","AKLI","NTB","WEL","WES","WKME","MNPR","MOFG","QBTS","ALXO","VIST","KITT","PAM","EYEN","VIVK","DECA","ALZN","CSAN","ITRM","TCPC","ITRN","SQNS","PAX","GNLN","PAY","VIVE","TCON","ILAG","PCB","GRC","MGNX","CSTA","TDCX","VAXX","CBRG","ONFO","NSTC","NSTD","NSTB","CSSE","TLSA","HIPO","PYXS","KRYS","SRAD","BHAT","XPOF","SRAX","ZETA","NSSC","ONEW","BHAC","RWOD","CSTE","XPON","AVAH","NBTB","ZEUS","TFPM","FEXD","HTOO","NVAC","AGIO","AGIL","IOBT","STOK","PSTV","PSTX","SBT","DPRO","EBTC","CMPX","AXLA","CMPS","OPRA","CMPO","SHC","SHG","SHO","JWN","SHW","MSCI","HCSG","SIG","BHC","CVGW","JXN","BHF","SIX","STWD","MSFT","SOS","CVNA","BBIO","RIGL","BNL","SPB","BNS","SPG","SPR","SPT","HLNE","BOH","BOX","KAI","CENX","CENT","YELP","KAR","EBS","CYBR","MSA","MTB","CPRI","RCKT","MTD","ZBRA","MTH","MTG","MTN","MTX","EDR","MTZ","VFC","TREX","EDU","ENIC","VOYA","MUR","CHDN","BEAM","MMSI","RLAY","MWA","EFX","BMRN","EGO","EGP","BECN","VIR","FAF","CMCSA","WCC","WKHS","ZLAB","NRG","WCN","OTTR","TSCO","FCN","WEC","FCX","TSEM","WEN","FRME","EXPD","SPWH","EXPE","PAG","GPC","JOBY","ITRI","PAR","GPI","GPK","GPN","REZI","GPS","PBA","TCOM","REYN","PCH","PCG","DECK","PCT","CBRL","HDB","CBRE","LMND","PUK","CBSH","HEI","CSTM","CSTL","HES","EQIX","GWRE","PVH","CVAC","SBH","NVAX","BAC","SBS","CVBF","BAH","BAM","BAP","CMPR","SCI","BIP","RIBT","LXRX","AXTI","AGRX","NEGG","JAKK","RICK","FWBI","APCX","SKT","AGRO","STVN","VMAR","GIFI","AGTI","QNRX","HLIO","APEI","SCWO","HLIT","SLN","SLP","SCWX","BKD","SLS","BKE","SCVL","BKI","HCWB","MSGM","PKOH","CVLG","FFBW","SMP","SMR","BBGI","BBIG","CELC","IONM","ECBK","IONR","SND","SCYX","BLU","LGVC","BLX","TOMZ","LGVN","BMA","SNX","MBIN","CVLY","MBIO","UROY","JANX","GIGM","SOI","CELZ","SOL","CELU","QOMO","CGO","NWFL","AYRO","PCYO","CFFS","BKSY","MTCR","MCBC","CFFN","TIL","HDSN","CHI","MKTW","SMFL","GRTS","VVOS","GRTX","CHW","CHY","FORA","CIA","GASS","FXCO","CIO","MTEK","MTEM","TKC","BCEL","PLMI","CNXN","MCFT","PLOW","KZR","FOSL","PLPC","GRVY","KEQU","LQDA","FORR","FGBI","VVPR","GATE","TMC","LYTS","ELOX","LQDT","USNA","CLB","SMHI","MCHX","VERO","BKYI","PUCK","TNC","EDBL","CLS","KNDI","EUDA","CLW","LIVE","CDZIP","GB","DSAQ","LRFC","LADR","ARHS","PVBC","GP","BLZE","KFRC","OSBC","EVEX","PECO","ARIS","UTRS","UPC","MULN","HY","UTSI","YGMZ","HNNA","PMTS","LIXT","IH","PEBO","MAC","MDJH","WAFU","ULBI","JG","EVER","MAT","MAX","NPAB","ARKO","SNOA","MBC","JZ","CPAC","PMVP","MBI","KA","KE","NXPL","NX","SNSE","MFH","OB","EVLO","AROC","EVLV","LANV","DUO","OP","NXTP","EEIQ","HFBL","WALD","FHLT","GTAC","MGI","SFBC","CGTX","MMAT","BUSE","DBGI","RCAT","NGVC","PT","PX","ARQQ","QH","HFFG","SNTG","OSIS","RC","MDRR","RE","SNTI","CPHC","MIR","BDSX","BMEA","MDRX","TIGO","RM","GCBC","KOPN","CPIX","SA","SB","SD","TIMB","GTIM","MOR","MOV","MMLP","VAL","YI","ENFN","YJ","PETS","CPOP","PETV","MPX","YQ","PETZ","TIPT","IRAA","PESI","WAVS","MRC","EBC","EBF","CYCC","MRM","VOXX","MEDS","TRIN","GLDD","FIAC","WSBF","BEDU","TRIS","ASCB","RCRT","MYE","EIG","LSBK","HOFT","BEEM","HOFV","IRIX","ZKIN","LBAI","SWVL","LSEA","EJH","SOHO","VLD","MEGL","UDMY","PNNT","OCCI","NBR","PWFL","TAOP","ASLN","OCAX","SOND","ENVB","ASLE","SONN","SONM","TARS","ZTEK","TARO","LSXMB","TRTL","ESE","ASMB","ESQ","OTEC","ASNS","SOPA","CYTO","CYTK","SOPH","CYTH","RLMD","TRST","NEO","TARA","NEX","PFIS","TRVG","VVI","SGBX","NHTC","KPLT","TRVN","DTIL","TRVI","LSPD","VVX","HOTH","TRTX","HGBL","TAST","ASPU","ASPS","ASPN","SGHC","NMM","GDEV","FISI","POCI","DCOM","NNI","FRGE","GLYC","RDHL","ABVC","EFSC","EWTX","FRGT","WKEY","NOA","LKFN","ZCMD","KPTI","PFTA","VYGR","PWUP","SGLY","DTSS","DTST","ASYS","TSAT","MNMD","RLYB","ACRE","ALCC","RMTI","NRIX","FSCO","ALDX","LTRN","LTRY","LTRX","FSBC","ACRV","HHGC","ACRS","ALEX","OLB","FSEA","VIAO","ACTG","WLDS","CRKN","OLK","REFI","OLP","OLO","DUOT","TKLF","ALEC","REFR","SHIP","ACST","EPAC","MOGO","REED","MOGU","WLDN","HYMC","EGRX","CRMT","ALGS","DMAC","CALB","CALA","ONL","KIDS","ALGM","CRMD","PGRU","TKNO","CRNX","CRNT","FSFG","ATXS","HYLN","LCUT","PPBT","IKNA","ALHC","CALT","VRDN","RVYL","PAA","PPSI","LUMO","TCRR","CBAT","TCRX","CBAN","TCRT","GSD","PDS","LUNA","CSBR","DMTK","GSL","ADMA","RNLX","WULF","TTSH","PET","PEV","SIBN","GWAV","GTX","PFC","GTY","GNPX","RWAY","PFS","KRMD","LUNG","PFX","MPAA","KRKR","PPYA","PGC","MGPI","PHIO","SIDU","KALV","PGY","VALN","KRNL","VRNA","BGSF","AMAL","TLGY","KRNY","SIEB","FTAI","LMAT","GWH","PHX","XYF","GFAI","OVID","KALA","RFAC","CBFV","PIK","AMBC","ADPT","VRME","AURA","ZVIA","GXO","TLIS","NBHC","FCAP","GNTY","VANI","OVLY","FTCI","PYPD","EYPT","FCBC","KRON","GNSS","KROS","PKE","ADSE","HIFS","AUTL","GNTA","MGTX","AMCX","VAPO","ADTN","ADTH","ONCT","AMLI","SINT","NBSE","CSWC","LMNR","SRCE","PWP","AMPX","AMPY","IDEX","UXIN","AMPS","NBST","PHYT","SABS","AMPH","JXJT","AMPG","PXD","AVAL","ROCL","AMRC","PXS","BYFC","HIVE","BPTS","AEAE","HAFC","MYFW","CSWI","BPTH","AMSF","AMSC","LOVE","SKYX","AOUT","SKYT","STKH","ESSA","DYAI","OPOF","VLRS","CMMB","STIM","AGFY","TWLV","CMLS","SCHL","STIX","QNCX","LXFR","HCKT","MAYS","TWOU","FNGR","MARPS","LGHL","PSTL","SCKT","PKBK","STKL","HTLD","HTLF","STKS","DHAC","SAI","ESTA","KLTR","CMND","OPRX","SAR","OPRT","BIO","AXTA","MSEX","SKY","SKX","SLB","SLF","CEIX","SLG","SLM","QNST","BKH","MSGE","FFBC","CVLT","SLGN","BKR","SMG","BKU","BSIG","JAMF","MSGS","KURA","BLD","ZYME","BLK","NVMI","CELH","PCAR","SNA","IONS","IONQ","PTCT","SNV","BMI","SNY","BMO","BMY","SON","JBHT","SMCI","KWR","MTCH","SDRL","IPGP","CHD","CHE","CHH","RARE","PLMR","MKTX","CHK","CNXC","CHT","CHX","TJX","CIM","TPIC","TKR","SMFG","TLS","GRWG","JBLU","FORM","PLNT","PARAA","TME","GATO","TMO","HUYA","CLF","CLH","GATX","FOUR","FGEN","VERI","CLX","TNK","DLR","UNF","FR","UNH","DLX","UNM","LIVN","UNP","GD","GE","GH","GL","GM","GO","GS","GT","DNA","DNB","HA","EVCM","HD","ARGX","HE","HI","HL","HP","HR","ULCC","UPS","DOC","WAFD","IP","IQ","IR","DOV","IT","MAA","DOX","DOW","CGNX","CGNT","JD","MAN","MAS","MAR","MDLZ","RKLB","URI","EEFT","DPZ","KB","KC","KD","KN","LANC","MFA","BMBL","MFC","MFG","MUSA","BURL","UVV","OC","OI","DUK","OM","ON","DSGX","AAON","OR","AAOI","MGA","GKOS","DVA","PB","MGM","PD","PG","PH","PI","LAMR","PK","PL","PM","DVN","MGY","AAPL","PR","TIGR","ARQT","MHK","NGVT","QD","MHO","QS","ARRY","DXC","RF","RH","RL","RS","RY","PENN","SE","SF","SWKS","MOS","LAUR","XP","CPNG","VAC","IIPR","MPC","GTLB","GTLS","MPW","PETQ","IRBT","YY","ZD","EAF","DBRG","ZG","ZH","ZI","ZM","EAT","ZS","MRK","MRO","LAZR","NYCB","IRDM","MEDP","MXL","EHC","VIV","TRIP","LSCC","TIXT","XMTR","CHGG","ABBV","WSBC","EIX","SOFI","ENPH","FICO","SOHU","EWBC","ABCL","VLO","VLN","ABCB","CYRX","EQR","EQT","VSH","MELI","ENVA","CHPT","VST","ERF","SONO","ERJ","HOOD","ERO","CHRD","XENE","ASML","LSXMA","VTR","MEOH","LSXMK","GLOB","ESI","ESS","BEPC","OKTA","NEE","ETD","CHRW","ASND","PFGC","NEM","NEP","IART","CHRS","IRTC","NET","OTEX","ETN","NEU","GLNG","ETR","OCFT","ABNB","NFG","IRWD","VVV","OCGN","TASK","NGG","NMR","NNN","FIVE","PODD","FIVN","MNKD","FREY","NOC","NOG","DCPH","NOK","PWSC","NOV","NOW","WAB","WAL","WAT","NPO","SGMO","SGML","WBA","WBD","FRHC","PFSI","WBS","EXPI","EXPO","ATSG","SPWR","OKE","WTRG","VIAV","ZUMZ","BWXT","DUOL","ATUS","OLN","OMC","PGNY","CAKE","OMF","OMI","SPXC","WTTR","EPAM","CALM","ONB","ALGT","DDOG","VICR","ALGN","PGRE","EXTR","ACVA","YALA","OMAB","PPBI","JNPR","VICI","CALX","BOKF","GEHC","PAC","CSCO","LUMN","PDD","PDM","ITUB","GSK","GSM","PEB","MGNI","LULU","PEG","PEN","PEP","GTN","ICUI","PFE","PFG","SQSP","HIBB","BXSL","AUPH","GVA","ADNT","PGR","AMAT","CSGS","CSGP","VALE","KRNT","PHM","PHR","AMBP","GWW","XYL","MGRC","PII","AMBA","GNRC","KALU","TTWO","LDOS","XPEV","AMCR","CSIQ","GNTX","XPER","NSIT","SIGA","PJT","SIGI","ADSK","VRNS","VRNT","PKG","XPEL","RNST","NBIX","PLD","HCP","PHUN","PTC","PWR","SIRI","SRCL","AVAV","FTNT","AMPL","HGV","SABR","ROCK","EQNR","QCOM","SITE","SITC","SKYW","ESRT","AGEN","TWLO","BRZE","SCHW","JNJ","URBN","PSTG","JOE","MAXN","STLA","SAH","ESTC","SAM","STLD","JPM","IFRX","SCS","CMRE","TWST","CMRA","BBU","SCOR","NMRD","BBW","CEAD","NVCT","FNLC","KLXE","CMRX","OPTN","SES","CMTG","CVEO","BSBK","STRR","UONEK","MSBI","NMTC","JAGX","NVEE","CECO","SFL","SFR","AGMH","BJRI","SLAM","URGN","JZXN","KUKE","BEP","TFSL","BBAR","SCPH","SGC","VLGEA","NECB","JVA","SGH","BBAI","STRO","LGMK","CMTL","BFC","SGU","SLDP","BFI","BFH","NEPH","SRL","PTGX","SRT","AGYS","CVRX","APLT","SLNA","NEON","SLNG","TGAA","BRD","SST","RZLT","CEPU","APLD","NEOV","FFIC","SLNO","STC","BRT","VUZI","STG","BRY","CERE","STR","PTIX","BSM","MBOT","NERV","NVRO","RILY","CNDT","SUN","SUP","NNBR","KEN","CNDA","CERT","CNEY","RRAC","CNET","GIPR","BSRR","MSSA","IXHL","NVVE","HLTH","APOG","KFS","ZYXI","BUR","HUDA","CNFR","SLRC","HUDI","NESR","CETX","SLRX","APPH","BVS","TOUR","SXC","PTMN","ETNB","SXI","BBSI","HUGE","BWB","CNGL","CEVA","IGIC","RRBI","PTLO","FFNW","BXC","GABC","APRE","MBRX","RACY","NEXA","NEXI","BBUC","MBUU","BSVN","NEXT","LYEL","PTPI","HUIZ","MBTC","LPRO","HLVX","FWRG","GREE","APTX","ETON","NEWT","BZH","KKR","APTO","MSVB","SLVM","KMPH","MKFG","LPSN","SDHY","SDIG","PLAO","MBWM","PLAY","ELBM","PLBC","TGLS","ECOR","BATRA","APWC","LPTX","BATRK","LHDX","APVO","TXMD","RRGB","HUMA","ELDN","GAIA","USCB","GAIN","CNOB","KOD","IGMS","USAC","LTRPB","USAP","BKKT","KOP","LTRPA","VECO","USAU","PLBY","APXI","SUNW","USDP","KVHI","APYX","PTVE","TBI","USEA","CAC","PCSA","VEEE","USCT","RAIL","DIBS","FFWM","ELEV","IPAR","TCN","GRND","GALT","TCS","TCX","KRP","KRO","KRT","CBL","GAMB","GRNQ","SUPV","GRNT","GAMC","GAME","IPDN","CWBR","CCB","BTAI","CCD","TDW","CCM","CCO","NWBI","PTWO","ECVT","CWCO","CNTG","GANX","SMBK","CNTB","CNTA","BTBD","RANI","TGTX","MCAA","CNSP","MCAG","CNSL","MCAC","BTCM","NFBK","GROM","IGTA","HURN","CNTY","CNTX","SURG","UBFO","SURF","BCAN","GROW","GROV","MTAL","TGI","TGL","BTBT","BCAB","SMBC","CFB","WHLR","IPHA","MCBS","TGVC","BCBP","RAPT","BTCY","KWE","CGA","BTCS","LYRA","ELME","THR","BCDA","USIO","VERU","VERY","JSPR","TOI","VNCE","ZIMV","TOP","PUBM","VERA","CNA","VERB","RAVE","CNF","SMLR","TPC","DZSI","VNET","RAYA","SMMF","COE","XTLB","DRCT","BCLI","PLRX","LBTYB","PLSE","LAB","BTMD","CPF","SMLP","CPK","LAW","TRC","LAZ","CPS","CPZ","LBC","TRS","IPSC","TSP","TSQ","NWPX","BTOG","JBSS","PLUR","OABI","BCML","PLUS","LCW","COCP","THCP","COCO","AQMS","TTI","HEAR","LDI","SEAC","ELYM","CSQ","CSR","PDFS","CSV","CFSB","TUP","CODI","BCOW","CODA","BCOV","TPST","LEV","FXLV","THCH","CTO","ZIVO","CTS","CTV","MTRN","HMST","COEP","MTRX","TPVG","GSBC","GSBD","CUE","THFF","BLBD","DRIO","SMSI","ORIC","LFT","CODX","ELYS","SEAT","MLAB","COFS","FXNC","TWI","NWTN","ORGS","NODK","ORGN","CVE","PLYM","CVI","SMRT","BLBX","CVV","GBBK","COGT","BTTX","HEES","BCSF","PUMP","PDLB","BCSA","BLDE","SEED","RBBN","SEEL","IPWR","MCRB","PULM","BLEU","SMTI","SEDA","MCRI","DAIO","DRMA","BLFY","CXW","SMWB","AQST","CYD","LICY","CYN","BCTX","DALN","FPAY","KNOP","LIBY","DAKT","EMBC","LIFE","LMB","XCUR","ORMP","MLGO","SVII","LIDR","MCVT","BLIN","COLL","NFYS","LND","EDRY","LNN","EDSA","COMS","COMP","COMM","BCYC","LNW","CONX","EMCG","AIHS","LIFW","THMO","LOV","CONN","GSIT","KNTK","PMCB","LPG","UAN","SEMR","DJCO","TYRA","COOL","COOK","GBIO","PDSB","DAC","SELF","UBX","DARE","ORRF","DAO","DRRX","KFFB","EDTK","RSKD","UCL","DRUG","THRD","ORTX","PMGM","DBD","CGAU","THRM","LRN","DBI","HEPA","THRY","BLND","RBKB","DATS","DRTS","DCO","CORR","MLNK","GBLI","CGBD","LTH","YOSH","WIMI","GBNY","DDI","LIND","LINC","NOTE","UFI","LINK","COSM","BLPH","CXDO","THTX","SNAL","HEPS","JCTCF","SVRE","YORW","DAVE","SNAX","SVRA","EMLD","LVO","DFH","NXGL","BLRX","CGEM","LIPO","UHT","AB","AC","BLTE","ARBK","NOTV","SERA","ARBE","YOTA","AM","SNCR","EMKR","AP","WINV","RBOT","SNCY","ARAY","ARAV","AZ","UIS","BE","LIQT","ARCO","AIRG","AIRC","RSSS","LXU","BLUA","ARCB","BQ","RKDA","UTMD","BV","BW","DHX","BY","CD","WRAP","NOVV","LYT","SNEX","COYA","SNES","LZB","SVVC","AIRS","ARES","DH","WISA","LITB","EVAX","DO","AZUL","ULH","AREB","AREC","LITM","AACI","AACG","RSVR","ARDX","ARDS","EE","LABP","DKL","GSUN","EM","SNGX","EQ","SEVN","ET","UMH","AADI","FA","FC","FF","UNB","MDIA","JCSE","ARLO","ARLP","MCB","USM","LC","EVGN","LE","EVGR","LL","NPCE","DRQ","SNPX","PEGR","LRMR","UTI","SWBI","UTL","MDC","LZ","DSGN","ME","MF","HNRG","MG","NXTC","MDV","ML","DSP","NXRT","DSX","MEC","MX","LAKE","SWAG","MEG","IZEA","DTC","NA","NH","HWBK","WRLD","SNPO","NL","NN","NR","UVE","NS","HNST","AROW","LAND","SG","SJ","DYN","BDTX","SP","HNVR","SY","IIIN","ARTW","GTEC","EVOK","TA","TC","IIIV","TG","TH","TK","ARTL","GTES","TR","MDWD","PEPG","TILE","RCFA","LATG","HWKN","MLR","UK","UP","GTHX","LASE","MMI","OBLG","CPLP","SWIM","KORE","MMV","LASR","WASH","ARVN","ARVL","RCEL","TZOO","IINN","OSPN","EVTL","PERI","PERF","WE","WF","WAVE","EVTV","WT","MOB","WATT","ARYD","MDXH","SFIX","FHTX","SWKH","HFWA","VMD","GCTK","EWCZ","TALK","PFBC","ABEO","NYMT","TALO","ENSC","MEIP","CHMG","IRMD","EML","EMP","VOC","VXRT","TRMD","VOR","ENG","TANH","VPG","ENTX","ENZ","PNTG","CHMI","HONE","HOLI","IROQ","IRON","HOLO","ENTA","EPD","TROO","KELYB","TRON","KELYA","ASIX","SONX","HOOK","GLLI","VRA","VRE","XELB","XELA","IRRX","VRM","DCBO","ABIO","GLMD","EVA","NGL","EVC","OCFC","EVE","ASPI","PFIE","DCGO","NGS","GLPG","EVO","MESA","HOUR","XERS","MNDO","AKAN","HGEN","LBPH","MESO","CHUY","OKYO","GLSI","PFLT","ABOS","NYXH","ASRV","NIC","ASRT","FINV","FINW","MVST","BNED","SOTK","AKBA","GLRE","EXK","BEST","NIU","RUBY","GLTO","OTLK","LKCO","XNET","GLUE","LSTA","LBRT","METC","GLST","ABSI","FRBA","BNGO","ASTR","FRBK","ASTL","PFMT","QIPT","HOWL","POAI","DTOC","SGHT","KPRX","FREE","ASUR","NLS","ABUS","BNIX","GDEN","NMG","OCUP","BWAY","FDP","HPCO","SPCB","BWAQ","RDNT","FRLA","BNRE","IBCP","MNOV","FET","BFAC","EFXT","GURE","ATAI","UEIC","GMBL","NVX","POLA","VYNE","MNSB","WHF","RMAX","FANH","WHG","CZFS","BNRG","TSHA","BWEN","DLHC","NWN","FGF","BNTC","FGI","IBEX","SXTC","FAMI","MNTX","NXL","MNTS","SPFI","BWFG","LTCH","XWEL","RBCAA","FWONA","ACAH","CIGI","NYC","ACAC","ACAB","GDST","RMBL","RMBI","HGTY","FIP","WTBA","RMCF","AKRO","LTBR","MNTK","ACBA","MNTN","ATEN","ATEK","GMFI","FRST","ATEC","FRSX","ACCO","OLMA","FARM","WKSP","WLY","AKTX","TBIO","WMK","AKTS","ATEX","EXAI","ATER","ACDC","TBLD","TBLA","FLJ","FLL","WNC","EGBN","FATH","ACET","FLT","TBLT","ACER","RDVT","MFIC","FATP","ACEL","MFIN","SPIR","DLNG","WNW","WTER","ATHE","EGAN","ATHA","CZNC","BFIN","FNA","WOW","CING","ATIP","SPLP","CZOO","WPC","ISPO","TBNK","ATIF","GDYN","NINE","UVSP","FOA","PORT","DLPN","FOR","LCFY","BWMN","RDWR","AKYA","ACHV","CINT","ACHL","XFIN","FPI","ISPC","FPH","TBPH","NRAC","SPNS","SPNT","CRAI","ACIU","FRZA","ISSC","CION","DLTH","BWMX","SPOK","OBT","ATLX","CRBP","EXFY","UNCY","ATLO","OCG","ATLC","WSR","OCN","CABA","OCX","ISTR","WTI","CAAS","RMNI","CAAP","WTMA","NABL","SYBX","WTW","CRBU","DUET","ODV","BELFB","CRDL","OEC","CRDF","ATNI","ACLX","POWL","NRBO","ATNF","ACLS","UNFI","FTK","CRCT","NREF","SPRY","SPRU","WVE","CITE","LCNB","ATOS","CREG","ATOM","OFG","CACO","MOBQ","TSVT","CISO","OFS","FUN","ISUN","SHAP","BFRI","FUV","BOCN","EGIO","SPRC","SPRB","JEWL","NISN","ACNT","NRDS","XFOR","CADL","SHBI","SPRO","NRDY","CREX","ALAR","MODD","MODN","REAX","LLAP","CIVB","MODV","CZWI","NRGV","REBN","GECC","ACON","SHCR","ALBT","OIG","ATRO","OII","SPTN","PXLW","OIS","GMRE","BFST","HYFM","PXMD","ATRA","CRGY","FSBW","SHFS","CRIS","ATXI","ATXG","MOLN","CANO","ACXP","ALIT","OPI","BOLT","ALIM","CRON","OPT","JWEL","OPY","CAMT","CAMP","ICAD","JFBR","PGSS","GAN","GAQ","NAMS","NAPA","FBIO","CAPL","ORN","ALKT","FBIZ","SYPR","WLKP","RELL","RELI","TCBC","TCBP","ICCH","TCBK","XOMA","OSG","KZIA","OSI","UFCS","ALLT","OSS","ALLR","VIGL","HYPR","OST","OSW","GCO","ALLK","BOOM","ALLG","UNTY","MOND","NAOV","CAPR","SYRS","CARS","GDC","RENE","CARE","CARA","RVPH","OMGA","RENT","CASS","TCBX","ICCM","TCBS","GEG","CASI","SHPH","CASH","PPHP","GEL","GEN","GENE","OMEX","GENI","SHPW","PPIH","EPIX","NRSN","XGN","FBMS","JFIN","CATO","GFF","FBNC","BORR","CATC","RNAZ","NATR","OMIC","FSNB","MGAM","GEOS","GGE","MORF","CRVS","GGR","SYTA","ALPP","ALPN","MXCT","CRVL","XIN","VINC","KINS","MOTS","OXM","GHG","LUCD","SQFT","ADAP","GHM","CRWS","ADAG","NRXP","BOTJ","LUCY","VINO","VINP","AUBN","MGEE","UWMC","GIC","ALSA","AUDC","ALRS","RVSB","NAUT","KIND","RVSN","ALRN","ALTG","GETY","VRAR","RETO","BGFV","ADCT","GNFT","KRBP","XLO","VRAX","VRAY","ADEA","DMLP","VIOT","EPOW","MOVE","ALTO","GETR","ALTI","CJJD","RNGR","REVG","VIRI","SHYF","XXII","GLP","PHAR","ICMB","PHAT","GLT","MGIC","VIRX","VABK","KACL","BOXL","FBRX","EHAB","HYZN","FSTR","VRCA","KIRK","ALVR","REVB","ALVO","AUGX","ICLK","XOS","GNE","GNL","GNK","JWSM","TTOO","PPTA","OVBC","AUID","DMRC","TCMD","WDFC","VISL","TTNP","EPSN","ITOS","FCCO","PLL","EQBK","FTEK","AUUD","LMFA","PME","VRPX","CSLM","PMN","UONE","ADTX","AUVI","ADVM","VRTS","MGYR","ZENV","LUXH","BGXX","DENN","RNXT","ADUS","KARO","FTFT","PPC","ZEPP","CBNK","ADXN","MPLX","FTII","DERM","KRUS","RFIL","FTHM","VATE","FKWL","PRA","SILO","DESP","PRE","HBB","PRM","ONDS","PHVS","SIOX","HBT","OESX","ZVSA","IDAI","AMLX","PSN","HCI","KAVL","ONCY","AMRX","GOCO","AMRK","WMPN","AMTB","SAGA","QTRX","DNMR","AMST","AVDX","HKD","SAFE","WEAV","TLYS","VSAC","SAFT","DNOW","KBAL","AVHI","LEDS","QCRH","HAIA","GOGO","BHIL","KSCP","AVGR","FLGC","HNI","ROIV","HALL","EZFL","BHLB","AEHR","VSEC","AEHL","FLFV","LEGH","FCUV","FLIC","VBFC","BYNO","YRD","SAMG","HROW","TURN","MYNZ","HQI","MYMD","CTBI","IMAB","QLGN","UGRO","CKPT","IMAQ","MHLD","IMAX","MYNA","EZGO","HRT","MYPS","CCAP","OFED","GOOD","AVNW","SANG","AEMD","SANA","LVLU","IMCR","SANW","ONTF","TMCI","TUSK","MYRG","BYRN","HTZ","HRTG","CCCC","FLNG","AVPT","AENZ","VBIV","BYSI","PIII","ONVO","SRRK","HVT","AMSWA","ANAB","ONYX","NKTX","GGAL","FLNT","AVRO","YTEN","MYSZ","SRTS","MYTE","VBNK","IDYA","MHUA","KSPN","RGCO","CRESY","QUBT","PINE","OWLT","EZPW","FDBC","SATL","DFLI","QUAD","OFIX","HYW","AVTX","GOSS","ROSE","ANDE","UPLD","AVTE","ROSS","HZN","KBNT","OFLX","WEST","QLI","ANEB","PRAX","WNEB","PIPR","HRZN","CTLP","LVTX","ERAS","IMNM","VSTA","CTMX","IMNN","CCLD","FLUX","ANGO","ANGH","ANGI","PRCT","VSTM","PIRS","IMMP","IMMR","IMMX","HAYN","GXII","SRZN","AVXL","ANIX","FLWS","IMPP","CCNE","ANIP","CTOS","NCNA","ANIK","LEXX","IAA","IMOS","IVAC","AEYE","NCMI","IAS","PACK","ZFOX","IMRN","AEZS","CLAR","IVDA","IMRX","NTRB","NCPL","RGLS","QUIK","CLBT","QSI","IVCA","ICD","CLBK","IVCB","PRFX","CTRM","IVCP","LNKB","ICU","NCRA","SSBI","CCRN","NTST","PAHC","IMTX","SSBK","CTSO","IDN","IDT","RGNX","HSCS","DOGZ","PIXY","CLDX","YTRA","CLDT","FULC","ANNX","CCSI","ERII","BIAF","IEP","IMTE","CCTS","FDMT","HBCP","CLEU","IFS","PRLH","NCTY","PRLD","IMUX","GPAC","UPXI","NCSM","CLFD","HSDT","ZIP","ERNA","RXST","AFAR","BZFD","CLGN","IHS","FMAO","PALT","III","AFBI","CTXR","IMXI","NTWK","PRME","ANTE","LNSR","DOMH","PANL","LFCR","HSII","FUSN","RGTI","CLIR","OOMA","AFCG","SBGI","MIGI","WFCF","SSIC","PRPH","DOLE","FMBH","ANTX","SBFM","PROC","IKT","SBFG","JYNT","PROK","PROF","BIGC","DOMA","FUSB","TELA","HBIO","PRQR","SBIG","PRPO","KTCC","QMCO","CLLS","ANVS","BIIB","PRST","INN","TENK","INM","INT","CLMT","PRTH","PRTC","PARR","VTEX","OXBR","EAST","AFIB","CLOE","RPHM","SSKN","CLNN","EARN","IPA","PRSO","FDUS","RPID","SSNT","IPI","FITBI","HSON","INBK","VTGN","HBNC","IPX","IPW","RBB","INBS","INBX","KCGI","PASG","AAC","INAB","PRTS","RBT","CUBI","SBLK","CLPT","CLPS","CLPR","INAQ","BIMI","VCEL","ABB","ABC","CLRC","CLRB","INDI","TERN","PAVM","CDAQ","IESC","SBOW","INDP","RDI","AFMD","RDW","CLSD","RDY","BRAG","BRAC","BIOX","ACR","GPMT","ACT","REE","BIOR","INCR","MIND","IMKTA","SBNY","DOUG","ITI","MINM","RES","ADD","REX","CUEN","TETE","MRBK","INFI","ADN","ADV","CLST","RYAM","RFL","FMNB","CLSK","LFLY","AEI","BIRD","AEL","MRAI","GPOR","RGC","BRCC","RGF","LFMD","MRAM","IVA","IVVD","RGP","NLSP","BRBR","RGS","GPRK","SBSI","BACA","IVR","IVT","MRDB","BRDG","TNGX","MACA","LOAN","MIST","DGHI","DGICA","MACK","MRCC","PAYO","HSTM","BITF","SSSS","BITE","HSTO","AGM","AGS","PAYS","INGN","RIG","AGX","SSTI","MIRM","AWRE","BACK","CLVR","GPRE","AHG","AHH","AFRI","AHI","OXLC","FEAM","LOCO","AHT","BRFH","FVCB","LOCL","BREZ","AIB","VCNX","AIH","BIVI","AIN","AIP","VTNR","AIR","MREO","AIU","MITA","AIV","DXLG","VTOL","MITK","MITT","AJX","DGLY","AKA","CDIO","INMB","BAFN","SKIN","SSYS","SKIL","RPTX","SKGR","AKR","CULP","INKT","CULL","ESAB","RMR","LFST","KTRA","ALG","RNA","AOGO","VLCN","ALT","INNV","LFVN","VCSA","INOD","NDLS","RNW","PBBK","AMK","VTRU","DXPE","ESCA","AMX","BRKL","MRIN","FEDU","BRKH","KTTA","VTSI","VCTR","BRLT","ANY","OPBK","BRLI","VTVT","DPCS","OPAD","MRKR","OPAL","ESEA","GHIX","AFYA","JAN","APG","XAIR","APM","TNON","APN","MRNS","JBI","AQB","INSE","PBFS","INSG","YMAB","OXSQ","AQU","PSFE","CURV","BANC","CMBM","EBET","ARC","CURI","BROG","GHLD","IFBD","CMAX","RTC","ARR","CDRO","INTR","NDRA","OPFI","CMCT","STBA","CMCO","INTZ","CMCM","CDRE","RTO","AOMR","STBX","ASC","CMCA","BANX","HTCR","BANR","STAF","NURO","INST","INSW","BRQS","CUTR","INTA","HTBI","INTE","LOMA","HTBK","BAOS","VCXB","MAQC","AXAC","LOOP","DXYN","FEMY","GYRO","AUD","FENC","NUVB","JFU","NMFC","FENG","TFFP","AUR","BARK","STCN","QRTEB","OPGN","AVD","NUTX","BRSP","INVA","MAPS","INVE","OPHC","AVO","CDTX","BRSH","INVO","PBLA","BJDX","GHRS","RXO","BRTX","TWIN","BASE","AWH","AGAE","TNXP","GHSI","MASS","XRTX","JHX","JILL","RYI","STER","MARK","STEL","AORT","ESMT","AXL","AGBA","MRTN","FATBB","CDXS","INZY","FNCB","LXEH","FNCH","ESPR","CDXC","AXDX","NUZE","AOSL","EBON","IFIN","ESOA","PSNL","EBMT","TNYA","MRUS","MATH","TFIN","MATW","MATV","WORX","STGW","AXGN","AZZ","PBPB","CDZI","CVCO","BBD","FNKO","NVCR","BBY","NMRK","SLAB","BCC","BCE","BCH","BCO","SEE","SEM","BDC","A","B","C","D","AGNC","F","G","AXON","H","J","K","L","BDN","M","O","SLCA","R","LGND","S","T","U","V","BDX","W","X","Z","SFM","AXNX","NVDA","BEN","STRA","STRL","BSBR","APAM","FFIV","KBH","LPLA","KBR","QFIN","SSB","SSD","APLS","NEOG","SSP","BRC","APLE","GILD","FFIE","BRO","BRP","STE","BRX","STN","NVST","STT","STX","KDP","STZ","SLQT","IOSP","SUI","BSX","JRVR","BSY","SUM","HUBB","HUBG","SUZ","TOST","HUBS","CERS","KEY","KEX","SVC","BTU","BUD","GRBK","KFY","NVTS","SWI","SWK","KGC","SWN","APPS","NNDM","GRAB","IOVA","APPN","SWX","RIOT","APPF","MSTR","KHC","BWA","SXT","JAZZ","SYF","SYK","SYM","YETI","KIM","SYY","CNHI","TOWN","BXP","PTON","KMPR","BYD","AHCO","RITM","GRFS","SDGR","FWRD","APTV","WPRT","RACE","RIVN","ETRN","BBWI","YEXT","KMB","KMI","KMT","PCOR","ELAN","ECPG","KMX","LYFT","PLAB","TGNA","KNX","PCRX","CNMD","KOS","GRIN","ETSY","TAC","PLCE","CNNE","TAL","TAK","TAP","VEEV","NNOX","TXRH","PCTY","CAE","CAG","CAH","CAL","CAN","JJSF","CAR","CAT","KRC","CWAN","KRG","SUPN","PCVX","TDC","CBT","TDG","CBU","CBZ","UBER","BKNG","TDS","ETWO","TDY","CCI","CCK","KSS","CCJ","GRMN","CCL","CCS","KTB","TEL","USFD","TER","CWEN","CDE","JBGS","TEX","GRPN","WYNN","TFC","CDW","FOLD","CEG","TFX","SMAR","RAMP","TGT","CFG","CFR","THC","MKSI","MTDR","THG","THO","CGC","THS","TNL","CMA","TNP","CMC","CME","QGEN","VERV","CMG","VERX","VNDA","CMI","DINO","CMP","CMS","TOL","USPH","CFLT","CNC","MCHP","DIOD","CNI","CNK","CNM","CNP","PDCO","CNO","CNQ","TPB","CNS","MTLS","TPG","CNX","TPH","FOXA","PLTK","FOXF","TPR","COF","TPX","PLTR","NFLX","COO","ZION","COP","LBTYA","LAD","LAC","CPB","CPA","CPG","LBTYK","UBSI","CPT","TRI","NOAH","TRN","TRP","TRU","TRV","DADA","TSE","SMMT","ORCL","PLUG","TSN","TSM","CRC","CRI","CRL","CRK","CRM","TTC","TTD","CRS","CWST","PLXS","KEYS","CSL","CSX","LEA","BCPC","LEG","SMPL","NWSA","LEN","CUK","MTSI","PLYA","CUZ","BLCO","EDIT","TWO","ORGO","JKHY","MTTR","CVS","TXG","CVX","BLDR","BLDP","TXN","TXT","CWH","CWK","BCRX","SEER","LHX","CWT","TYL","COHU","COHR","LII","MLCO","SMTC","LIN","SEDG","CXM","AZEK","BLFS","COIN","CYH","ORLY","GBCI","LKQ","CZR","GBDC","VNOM","GSHD","SEIC","EURN","KNSA","LLY","KNSL","LMT","COLM","COLD","COLB","LNC","LNT","HELE","LOB","UAA","LOW","NOMD","BLKB","UAL","LPL","COOP","LPX","MLKN","UBS","DAL","DAN","UCBI","DAR","BLMN","IHRT","DASH","LILA","BLNK","RBLX","DBX","LILM","UDR","DCI","CORT","LTC","DDD","SNBR","DDL","UTHR","COST","DDS","DEA","DRVN","DEI","AZPN","SNAP","LUV","COTY","UGI","DAVA","SNDL","NOVA","LVS","SNDX","DAWN","SNDR","DFS","CGEN","COUR","NOVT","AA","UHS","WING","AG","AI","AL","AN","AR","DGX","HESM","AX","AY","BA","BB","BC","FYBR","DHI","BLUE","LXP","BG","MLTX","BJ","ARCH","BK","BL","BN","MUFG","BP","ARCC","DHR","BR","DHT","LYB","BX","LRCX","BZ","WRBY","LYG","CB","CC","CE","CF","CG","CI","LYV","DIN","CL","CM","CP","CR","DIS","CW","AZTA","ARCT","DB","DD","DE","DG","DK","DM","LITE","DQ","WISH","DT","DV","DX","DY","EVBG","MDGL","EA","EB","EC","ED","WABC","EH","EL","UMC","WIRE","DKS","ES","GBTG","EW","DLB","FE","FG","DLO","FL","FN","KODK","KO","USB","NXPI","KR","KW","MCD","MCK","OSCR","MCO","EVGO","DRI","DRH","LH","MCS","LI","MCW","MCY","DRS","LU","MDB","LW","LX","NXST","ARMK","MA","MC","MD","UTZ","MDT","MDU","MO","MP","MQ","SNOW","MS","MU","UCTT","PEGA","MED","MEI","DTE","NE","SNPS","NI","DTM","MET","SWAV","SFBS","NU","SM","SO","SQ","SR","ST","SU","MKC","MKL","TD","TS","TT","TU","TW","TX","EVRG","EVRI","LILAK","MLI","UA","MLM","UE","UI","MMC","MMM","VC","MMS","EVTC","VZ","WB","WD","WH","ARWR","WK","WM","WU","WW","MDXG","WY","MOD","MOH","VLY","FIBK","VMC","ENOV","VMI","ELF","WSFS","ASGN","ELP","ABEV","ELS","ELV","CHKP","VNO","TRNO","EME","ENSG","VNT","MMYT","DKNG","EMN","RCUS","VOD","EMR","TRMB","FCNCA","MVIS","ENB","TRMK","TROX","TROW","BEKE","ENR","ENS","ENV","FIGS","EOG","HOLX","EPC","UMBF","ENTG","HOMB","NAT","SONY","EPR","ENVX","EQC","VRT","EQH","HOPE","EVH","JMIA","CHTR","TRUP","GLPI","EVR","HOUS","AKAM","XNCR","NHI","MNDY","SOUN","BERY","OTIS","MERC","EXC","NIO","EXP","EXR","EYE","GDDY","OTLY","NJR","CHWY","META","NKE","ASTS","ASTE","FITB","NLY","RDFN","LSTR","WEX","NTR","OCUL","FDS","WFC","SPCE","FDX","NUE","FIZZ","NUS","BFAM","WGO","WGS","FROG","NVT","CIEN","EOLS","WHD","FANG","MNSO","NWE","NWG","WHR","NWL","GDOT","GMAB","NWS","CIFR","KYMR","ATAT","NXE","MNRO","SGRY","FHB","RUSHA","GDRX","WIT","FHI","WIX","FHN","BNTX","JELD","FWONK","SPGI","ACAD","MNST","NYT","RMBS","FIS","FRPT","FIX","FRSH","EXAS","TSLA","WLK","FARO","ACCD","GMED","WMB","WMG","POOL","WMS","WMT","OLLI","FLO","CENTA","FATE","FLS","FLR","ATGE","EOSE","WNS","FMC","IBKR","ATHM","TSLX","WTFC","WOR","FNB","FAST","FND","FNF","OLPX","CINF","ACHC","POST","IBOC","FNV","WPM","ACGL","FOX","ACHR","EXEL","QRVO","ATKR","PGEN","WRB","ACIW","BFLY","WRK","WSC","CRBG","WSM","LCID","WSO","LCII","WST","ISRG","NAAS","FRO","FRT","EGHT","WTS","SHAK","ODP","FSK","CACC","FSM","SPOT","CRDO","DLTR","FSS","FSV","POWI","CABO","FTI","POWW","IBRX","CADE","FTS","FTV","SPSC","ACMR","EXLS","CACI","FUL","WWD","OGE","OGI","WWW","OGN","OGS","IBTX","ODFL","MODG","CIVI","OHI","SHEN","SHEL","UNIT","REAL","ATRC","CRNC","REGN","CROX","CANG","OPK","SHLS","SYNA","ORA","ORC","FBIN","VZIO","ORI","ALKS","SHOP","TCBI","SHOO","GBX","RVNC","MOMO","OMCL","OUST","ALLY","OSK","RVLV","GCI","BOOT","ALLO","ITCI","GCT","ALLE","REKR","RVMD","XEL","TTEK","CARR","CARG","NARI","CRSR","CRSP","GDS","RELY","ALNY","GEF","OMER","OUT","GEO","FSLR","GES","CRTO","TTEC","FSLY","REPL","TTGT","GFL","OVV","CRUS","GFS","LBRDA","CASY","CRWD","XHR","OWL","GGG","LBRDK","ITGR","MORN","CATY","ICFI","RERE","NAVI","ICHR","OXY","GIB","GIL","GIS","ALRM","ADBE","GERN","OZK","ALSN","VIPS","FBRT","WDAY","ALTR","BOWL","ICLR","EPRT","GLW","VIRT","PYCR","GME","GMS","GEVO","XOM","TTMI","VITL","VREX","REXR","GNW","XPO","BXMT","DVAX","KREF","UFPI","AMED","DELL","VRRM","PYPL","PMT","FTDR","FCEL","PNC","AMGN","PNM","PNR","PNW","ILMN","POR","VRSK","VRSN","MPLN","HIMS","PPG","PPL","HIMX","SIMO","VRTX","FCFS","HAE","HAL","SILK","EHTH","BPMC","HAS","PRG","PRI","PRO","AMKR","HBI","PRU","HBM","TLRY","IDCC","PSA","HCA","HCC","PSO","BPOP","HCM","PSX","HIG","HII","SITM","AMRN","HIW","AMTD","LECO","SAGE","VSAT","MPWR","FCPT","DNLI","AMTX","MYGN","GOEV","YMM","SAIA","VSCO","HLF","AVGO","HLI","HLN","SAIC","HLT","HLX","NKLA","FLEX","ONON","AMWD","HMN","GOGL","YOU","HAIN","AVIR","LEGN","YPF","FLGT","HRMY","QTWO","HALO","AMWL","HOG","HON","ROIC","HPE","GOLD","ROKU","HPK","GOLF","HPQ","HPP","AEIS","NTAP","BYND","TDOC","YSG","HQY","HRB","CTAS","AMZN","HRI","HRL","SRPT","ZNTL","DNUT","NTCT","SAND","AVNS","AVNT","HST","SANM","HSY","HTH","ONTO","ROOT","CCCS","NTES","FLNC","NKTR","HUN","GOOG","HUM","WELL","HUT","TMDX","GOOS","SASR","TMHC","CCEP","HWC","NTGR","HWM","TDUP","HASI","HRTX","DFIN","HXL","GOTU","PINC","PINS","SAVE","SAVA","BHVN","SATS","AVTR","TUYA","ROST","IDXX","HZO","FUBO","ANET","PRAA","CTKB","NTLA","WERN","CTLT","LESL","RGEN","PRCH","PAAS","LVWR","LEVI","VSTO","AEVA","NCLH","UHAL","QDEL","HAYW","NCNO","DOCN","VBTX","DOCS","UPST","DOCU","ZBH","CCOI","PRDO","IAC","IAG","NTNX","PACB","FLYW","PRGS","RGLD","PRGO","QLYS","NTRA","IBN","IBM","IBP","ICE","QSR","CTRN","ICL","PRFT","TEAM","CTRE","CTRA","PRIM","HBAN","IDA","UPWK","PZZA","SBAC","CTSH","NTRS","ERIE","HSBC","TECH","FULT","PAGS","IEX","TECK","IMVT","ZGN","CTVA","IFF","RPAY","SBCF","PRLB","TMUS","IGT","ZIM","BIDU","IHG","PRMW","RXRX","MIDD","HSIC","DOMO","ANSS","LNTH","PRPL","PANW","QURE","GOOGL","DOOO","DOOR","FUTU","TENB","PARA","PRTA","INO","VKTX","DXCM","IOT","CLNE","PATH","IPG","PRVA","PATK","CLOV","RBA","RBC","BILL","BILI","DORM","CUBE","AAL","AAN","IQV","AAP","SSNC","AAT","RCI","RCL","RCM","INDB","NDAQ","IRM","ABG","IRT","ABM","ABR","ABT","ZTO","ZTS","RDN","ACA","ACB","BIPC","ACI","ACM","ACN","REG","ZUO","INCY","ADC","INFA","ADI","ITT","ADM","ADP","ITW","TNET","INFN","ADT","RYAN","SBRA","XRAY","PAYC","VTLE","BABA","TVTX","AEE","TNDM","AEM","AEO","AEP","RGA","AER","AES","ZWS","RGR","SSRM","AFG","AFL","DOYU","GPRO","IVZ","RHI","SBSW","RHP","AGI","AGL","AGO","AGR","BZUN","SSTK","INGR","PAYX","CLVT","TEVA","DGII","MRCY","BRFS","RJF","SBUX","AIG","RPRX","AIT","KTOS","AIZ","RKT","AJG","AFRM","RLI","RLJ","VTRS","INMD","RLX","KLAC","RMD","WFRD","ALB","ALC","ALE","ALK","ALL","LOGI","RNG","ALV","AMC","RNR","AMD","AME","MAIN","AMG","YUMC","AMH","AMN","AMP","AMR","AMT","BRKR","ROG","ROL","ROK","CDLX","ROP","EBAY","ANF","LFUS","SKLZ","PSEC","RPD","CDNA","RPM","CDMO","AON","BALL","AOS","APA","APD","CDNS","APH","API","APO","APP","RRC","BALY","RRR","JBL","KLIC","RRX","INSM","JBT","AQN","OPCH","RSG","RSI","BANF","BAND","BROS","JCI","ARE","ARI","ESGR","MRNA","INTU","ARW","HCAT","ASB","ASH","RTX","FELE","ASO","INSP","ASR","STAG","ASX","STAA","JEF","RUN","RUM","MANH","OPEN","VTYX","INTC","ATI","MANU","WOLF","ATO","ATR","WOOF","INVZ","AUB","NUVL","SCCO","LOPE","VCYT","AVA","AVB","RWT","QRTEA","NDSN","INVH","AVT","AVY","JHG","MASI","HTGC","RXT","PSMT","AWI","ESNT","AWK","AWR","MARA","MRSN","RYN","STEP","STEM","AXP","AXS","TWKS","AGCO","AYI","FERG","RYTM","PSNY","FVRR","JKS","TFII","AZN","AZO","HTHT","NMIH","MATX","MRVI","MRVL","JLL");
        Set<String> validSet2 = Sets.newHashSet("SJT","BPT","MSB","MVO","NRT","PBT","PVL");
        stockSets.removeAll(invalidSet);
//        stockSets.removeAll(validSet);
        stockSets.removeAll(validSet2);

        List<List<String>> partition = Lists.partition(Lists.newArrayList(stockSets), 100);
        for (List<String> codeList : partition) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(codeList);
            trdDemo.getMarginRatio(codeList);
        }
        System.out.println();
    }
}
