package luonq.futu;

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
import com.futu.openapi.pb.TrdUnlockTrade;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.apache.commons.lang3.StringUtils;
import util.MD5Util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Created by Luonanqin on 2022/12/22.
 */
public class TradeDemo implements FTSPI_Trd, FTSPI_Conn {

    public static long accountId = 281756460288713754L;
    public static String pwd = MD5Util.calcMD5("134931");

    FTAPI_Conn_Trd trd = new FTAPI_Conn_Trd();

    public FTAPI_Conn_Trd getTrd() {
        return trd;
    }

    public TradeDemo() {
        trd.setClientInfo("javaclient", 1);  //设置客户端信息
        trd.setConnSpi(this);  //设置连接回调
        trd.setTrdSpi(this);   //设置交易回调
    }

    public void start() {
        trd.initConnect("127.0.0.1", (short) 11111, false);
    }

    @Override
    public void onInitConnect(FTAPI_Conn client, long errCode, String desc) {
        System.out.printf("Qot onInitConnect: ret=%b desc=%s connID=%d\n", errCode, desc, client.getConnectID());
        if (errCode != 0) {
            return;
        }
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
    public void getFunds() {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(TrdCommon.TrdEnv.TrdEnv_Simulate_VALUE)
          .setTrdMarket(TrdCommon.TrdMarket.TrdMarket_HK_VALUE)
          .build();
        TrdGetFunds.C2S c2s = TrdGetFunds.C2S.newBuilder()
          .setHeader(header)
          .build();
        TrdGetFunds.Request req = TrdGetFunds.Request.newBuilder().setC2S(c2s).build();
        int seqNo = trd.getFunds(req);
        System.out.printf("Send TrdGetFunds: %d\n", seqNo);
    }

    // 获取持仓
    public void getPositionList() {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(TrdCommon.TrdEnv.TrdEnv_Real_VALUE)
          .setTrdMarket(TrdCommon.TrdMarket.TrdMarket_US_VALUE)
          .build();
        TrdGetPositionList.C2S c2s = TrdGetPositionList.C2S.newBuilder()
          .setHeader(header)
          .build();
        TrdGetPositionList.Request req = TrdGetPositionList.Request.newBuilder().setC2S(c2s).build();
        int seqNo = trd.getPositionList(req);
        System.out.printf("Send TrdGetPositionList: %d\n", seqNo);
    }

    // 下单接口
    public void placeOrder() {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(TrdCommon.TrdEnv.TrdEnv_Real_VALUE)
          .setTrdMarket(TrdCommon.TrdMarket.TrdMarket_US_VALUE)
          .build();
        TrdPlaceOrder.C2S c2s = TrdPlaceOrder.C2S.newBuilder()
          .setPacketID(trd.nextPacketID())
          .setHeader(header)
          .setTrdSide(TrdCommon.TrdSide.TrdSide_Sell_VALUE)
          .setOrderType(TrdCommon.OrderType.OrderType_Normal_VALUE)
          .setSecMarket(TrdCommon.TrdSecMarket.TrdSecMarket_US_VALUE)
          .setCode("TQQQ")
          .setQty(1)
          .setPrice(28)
          .build();
        TrdPlaceOrder.Request req = TrdPlaceOrder.Request.newBuilder().setC2S(c2s).build();
        int seqNo = trd.placeOrder(req);
        System.out.printf("Send TrdPlaceOrder: %d\n", seqNo);
    }

    // 改单（修改、撤单、删除等）
    public void modifyOrder() {
        TrdCommon.TrdHeader header = TrdCommon.TrdHeader.newBuilder()
          .setAccID(accountId)
          .setTrdEnv(TrdCommon.TrdEnv.TrdEnv_Real_VALUE)
          .setTrdMarket(TrdCommon.TrdMarket.TrdMarket_US_VALUE)
          .build();
        TrdModifyOrder.C2S c2s = TrdModifyOrder.C2S.newBuilder()
          .setPacketID(trd.nextPacketID())
          .setHeader(header)
          .setOrderID(3613423524290050192L)
          .setModifyOrderOp(TrdCommon.ModifyOrderOp.ModifyOrderOp_Cancel_VALUE)
          .setPrice(100)
          .build();
        TrdModifyOrder.Request req = TrdModifyOrder.Request.newBuilder().setC2S(c2s).build();
        int seqNo = trd.modifyOrder(req);
        System.out.printf("Send TrdModifyOrder: %d\n", seqNo);
    }

    @Override
    public void onDisconnect(FTAPI_Conn client, long errCode) {
        System.out.printf("Qot onDisConnect: %d\n", errCode);
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
                System.out.printf("Receive TrdGetFunds: %s\n", json);
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
                String json = JsonFormat.printer().print(rsp);
                System.out.printf("Receive TrdGetPositionList: %s\n", json);
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
            InputStream resourceAsStream = TradeDemo.class.getResourceAsStream(filePath);
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
        TradeDemo trdDemo = new TradeDemo();
        trdDemo.start();

        try {
            Thread.sleep(3000);
        } catch (InterruptedException exc) {

        }

        //        trdDemo.unlock();
//                trdDemo.getFunds();
                trdDemo.getPositionList();
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
