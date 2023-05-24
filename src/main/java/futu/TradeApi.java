package futu;

import bean.OrderFill;
import bean.StockPosition;
import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn;
import com.futu.openapi.FTAPI_Conn_Trd;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Trd;
import com.futu.openapi.pb.TrdCommon;
import com.futu.openapi.pb.TrdGetFunds;
import com.futu.openapi.pb.TrdGetPositionList;
import com.futu.openapi.pb.TrdModifyOrder;
import com.futu.openapi.pb.TrdPlaceOrder;
import com.futu.openapi.pb.TrdSubAccPush;
import com.futu.openapi.pb.TrdUnlockTrade;
import com.futu.openapi.pb.TrdUpdateOrder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import util.MD5Util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2022/12/22.
 */
public class TradeApi implements FTSPI_Trd, FTSPI_Conn {

    public static String pwd = MD5Util.calcMD5("134931");
    public static long simulateUsAccountId = 7681786L;

    private long accountId = 281756460288713754L;
    private int tradeEnv = TrdCommon.TrdEnv.TrdEnv_Real_VALUE;
    private int tradeMarket = TrdCommon.TrdMarket.TrdMarket_US_VALUE;

    private AtomicDouble remainCash = new AtomicDouble(0);
    private AtomicLong orderId = new AtomicLong(0);
    private AtomicInteger cancelResCode = new AtomicInteger(1);
    private Map<Long, LinkedBlockingQueue<OrderFill>> orderFillMap = Maps.newHashMap();
    private BlockingQueue<Map<String, StockPosition>> stockPositionBlock = new LinkedBlockingQueue<>();

    FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();

    public FTAPI_Conn_Trd getTrd() {
        return trd;
    }

    public TradeApi() {
        trd.setClientInfo("javaclient", 1);  //设置客户端信息
        trd.setConnSpi(this);  //设置连接回调
        trd.setTrdSpi(this);   //设置交易回调
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
        System.out.println("staring...");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException exc) {
        }
        System.out.println("start finish!");
    }

    @Override
    public void onInitConnect(FTAPI_Conn client, long errCode, String desc) {
        System.out.printf("Qot onInitConnect: ret=%b desc=%s connID=%d\n", errCode, desc, client.getConnectID());
        if (errCode != 0) {
            return;
        }

        TrdSubAccPush.C2S c2s = TrdSubAccPush.C2S.newBuilder()
          .addAccIDList(accountId)
          .build();
        TrdSubAccPush.Request req = TrdSubAccPush.Request.newBuilder().setC2S(c2s).build();
        int seqNo = trd.subAccPush(req);
        System.out.printf("Send TrdSubAccPush: %d\n", seqNo);
    }

    // 解锁接口，只需要解锁一次
    public void unlock() {
        TrdUnlockTrade.C2S c2s = TrdUnlockTrade.C2S.newBuilder()
          .setPwdMD5(pwd) // 密码md5小写
          .setUnlock(true)
          .setSecurityFirm(TrdCommon.SecurityFirm.SecurityFirm_FutuSecurities_VALUE) // account返回的securityFirm
          .build();
        TrdUnlockTrade.Request req = TrdUnlockTrade.Request.newBuilder().setC2S(c2s).build();
        int seqNo = trd.unlockTrade(req);
        System.out.printf("Send TrdUnlockTrade: %d\n", seqNo);
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
        int seqNo = trd.getFunds(req);
        System.out.printf("Send TrdGetFunds: %d\n", seqNo);
        while (true) {
            if (remainCash.get() != 0) {
                return remainCash.getAndSet(0);
            }
        }
    }

    // 获取持仓
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
        int seqNo = trd.getPositionList(req);
        System.out.printf("Send TrdGetPositionList: %d\n", seqNo);

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
        System.out.printf("Send TrdPlaceOrder: %d\n", seqNo);
    }

    // 下单接口，指定数量和价格，有价格就是限价单，没价格就是市价单，返回订单ID
    public long placeOrder(String code, int count, Double price) {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();

        TrdPlaceOrder.C2S.Builder c2sBuilder = TrdPlaceOrder.C2S.newBuilder()
          .setPacketID(trd.nextPacketID())
          .setHeader(header)
          .setTrdSide(TrdCommon.TrdSide.TrdSide_Buy_VALUE)
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
        int seqNo = trd.placeOrder(req);
        System.out.printf("Send TrdPlaceOrder: %d\n", seqNo);
        while (true) {
            if (orderId.get() != 0) {
                return orderId.get();
            }
        }
    }

    // 下单接口，指定数量和价格，有价格就是限价单，没价格就是市价单，返回订单ID
    public long placeOrderForLossMarket(String code, double count, Double auxPrice) {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(tradeEnv)
          .setTrdMarket(tradeMarket)
          .build();

        TrdPlaceOrder.C2S c2s = TrdPlaceOrder.C2S.newBuilder()
          .setPacketID(trd.nextPacketID())
          .setHeader(header)
          .setTrdSide(TrdCommon.TrdSide.TrdSide_Sell_VALUE)
          .setSecMarket(TrdCommon.TrdSecMarket.TrdSecMarket_US_VALUE)
          .setCode(code)
          .setQty(count)
          .setAuxPrice(auxPrice)
          .setOrderType(TrdCommon.OrderType.OrderType_Stop_VALUE).build();

        TrdPlaceOrder.Request req = TrdPlaceOrder.Request.newBuilder().setC2S(c2s).build();
        int seqNo = trd.placeOrder(req);
        System.out.printf("Send TrdPlaceOrder: %d\n", seqNo);
        while (true) {
            if (orderId.get() != 0) {
                return orderId.get();
            }
        }
    }

    // 撤单
    public int cancelOrder(long orderId) {
        return modifyOrder(orderId, TrdCommon.ModifyOrderOp.ModifyOrderOp_Cancel_VALUE);
    }

    // 改单（修改、撤单、删除等），返回操作结果码
    public int modifyOrder(long orderId, int modifyOrderOp) {
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
          .build();
        TrdModifyOrder.Request req = TrdModifyOrder.Request.newBuilder().setC2S(c2s).build();
        int seqNo = trd.modifyOrder(req);
        System.out.printf("Send TrdModifyOrder: %d\n", seqNo);
        while (true) {
            if (cancelResCode.get() < 1) {
                return cancelResCode.get();
            }
        }
    }

    @Override
    public void onDisconnect(FTAPI_Conn client, long errCode) {
        System.out.printf("Qot onDisConnect: %d\n", errCode);
    }

    // 订单成交回调
    @Override
    public void onPush_UpdateOrder(FTAPI_Conn client, TrdUpdateOrder.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("TrdUpdateOrder failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                TrdCommon.Order order = rsp.getS2COrBuilder().getOrder();
                long orderID = order.getOrderID();
                int orderStatus = order.getOrderStatus();
                double fillQty = order.getFillQty();
                double fillAvgPrice = order.getFillAvgPrice();
                if (orderStatus != TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE) {
                    System.out.println("fillQty: " + fillQty + ", fillAvgPrice: " + fillAvgPrice);
                    return;
                }

                OrderFill fill = new OrderFill();
                fill.setOrderID(orderID);
                fill.setCode(order.getCode());
                fill.setName(order.getName());
                fill.setPrice(order.getPrice());
                fill.setCreateTime(order.getCreateTime());
                fill.setUpdateTimestamp(order.getUpdateTimestamp());
                fill.setCount(order.getQty());
                fill.setTradeSide(order.getTrdSide());
                if (!orderFillMap.containsKey(orderID)) {
                    orderFillMap.put(orderID, new LinkedBlockingQueue<>(10));
                }
                orderFillMap.get(orderID).offer(fill);
                String json = JsonFormat.printer().print(rsp);
                System.out.printf("Receive TrdUpdateOrder: %s\n", json);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    // 解锁回调
    @Override
    public void onReply_UnlockTrade(FTAPI_Conn client, int nSerialNo, TrdUnlockTrade.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("TrdUnlockTrade failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                String json = JsonFormat.printer().print(rsp);
                System.out.printf("Receive TrdUnlockTrade: %s\n", json);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    // 资金回调
    @Override
    public void onReply_GetFunds(FTAPI_Conn client, int nSerialNo, TrdGetFunds.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("TrdGetFunds failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                String json = JsonFormat.printer().print(rsp);
                //                System.out.printf("Receive TrdGetFunds: %s\n", json);
                double cash = rsp.getS2COrBuilder().getFunds().getCash();
                remainCash.set(cash);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    // 持仓回调
    @Override
    public void onReply_GetPositionList(FTAPI_Conn client, int nSerialNo, TrdGetPositionList.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("TrdGetPositionList failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                List<TrdCommon.Position> positionListList = rsp.getS2COrBuilder().getPositionListList();
                Map<String, TrdCommon.Position> codeToPositionMap = positionListList.stream().collect(Collectors.toMap(TrdCommon.Position::getCode, Function.identity()));
                Map<String, StockPosition> positionMap = Maps.newHashMap();
                for (String code : codeToPositionMap.keySet()) {
                    TrdCommon.Position position = codeToPositionMap.get(code);
                    double canSellQty = position.getCanSellQty();
                    double costPrice = position.getCostPrice();

                    StockPosition stockPosition = new StockPosition();
                    stockPosition.setStock(code);
                    stockPosition.setCanSellQty(canSellQty);
                    stockPosition.setCostPrice(costPrice);

                    positionMap.put(code, stockPosition);
                }

                String json = JsonFormat.printer().print(rsp);
                System.out.printf("Receive TrdGetPositionList: %s\n", json);
                stockPositionBlock.offer(positionMap);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    // 下单回调
    @Override
    public void onReply_PlaceOrder(FTAPI_Conn client, int nSerialNo, TrdPlaceOrder.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("TrdPlaceOrder failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                long orderID = rsp.getS2COrBuilder().getOrderID();
                orderFillMap.put(orderID, new LinkedBlockingQueue<>());
                orderId.set(orderID);
                String json = JsonFormat.printer().print(rsp);
                System.out.printf("Receive TrdPlaceOrder: %s\n", json);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
    }

    // 改单回调
    @Override
    public void onReply_ModifyOrder(FTAPI_Conn client, int nSerialNo, TrdModifyOrder.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("TrdModifyOrder failed: %s\n", rsp.getRetMsg());
        } else {
            try {
                cancelResCode.set(rsp.getErrCode());
                String json = JsonFormat.printer().print(rsp);
                System.out.printf("Receive TrdModifyOrder: %s\n", json);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
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
            System.out.println("can not find file: " + filePath);
            return Lists.newArrayList();
        }
        return lineList;
    }

    public static void main(String[] args) {
        FTAPI.init();
        TradeApi trdDemo = new TradeApi();
        trdDemo.setAccountId(simulateUsAccountId);
        trdDemo.useSimulateEnv();
        trdDemo.start();

        //        trdDemo.unlock();
        //        double funds = trdDemo.getFunds();
        //        System.out.println(funds);
        //                trdDemo.getPositionList();
        //        trdDemo.placeOrder();
        //        trdDemo.modifyOrder();

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException exc) {

            }
        }
    }
}
