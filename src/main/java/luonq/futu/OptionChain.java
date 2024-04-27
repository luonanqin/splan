package luonq.futu;

import com.futu.openapi.FTAPI;
import com.futu.openapi.FTAPI_Conn;
import com.futu.openapi.FTAPI_Conn_Qot;
import com.futu.openapi.FTSPI_Conn;
import com.futu.openapi.FTSPI_Qot;
import com.futu.openapi.pb.QotCommon;
import com.futu.openapi.pb.QotGetOptionChain;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class OptionChain implements FTSPI_Qot, FTSPI_Conn {

    FTAPI_Conn_Qot qot = new FTAPI_Conn_Qot();
    FileWriter fw;
    String fileName;

    public OptionChain() {
        qot.setClientInfo("javaclient", 1);  //设置客户端信息
        qot.setConnSpi(this);  //设置连接回调
        qot.setQotSpi(this);   //设置交易回调
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void start() {
        qot.initConnect("127.0.0.1", (short) 11112, false);
    }

    @Override
    public void onInitConnect(FTAPI_Conn client, long errCode, String desc) {
        System.out.printf("Qot onInitConnect: ret=%b desc=%s connID=%d\n", errCode, desc, client.getConnectID());
        if (errCode != 0) {
            return;
        }

        //        codeList = Lists.newArrayList("AAPL", "TSLA");
        //        for (String code : codeList) {
        //            QotCommon.Security sec = QotCommon.Security.newBuilder()
        //              .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
        //              .setCode(code)
        //              .build();
        //            QotGetOptionChain.C2S c2s = QotGetOptionChain.C2S.newBuilder()
        //              .setOwner(sec)
        //              .setBeginTime("2023-01-17")
        //              .setEndTime("2023-02-01")
        //              .build();
        //            QotGetOptionChain.Request req = QotGetOptionChain.Request.newBuilder().setC2S(c2s).build();
        //            System.out.println(code + " " + qot.getOptionChain(req));
        //        }
    }

    @Override
    public void onDisconnect(FTAPI_Conn client, long errCode) {
        System.out.printf("Qot onDisConnect: %d\n", errCode);
    }

    @Override
    public void onReply_GetOptionChain(FTAPI_Conn client, int nSerialNo, QotGetOptionChain.Response rsp) {
        if (rsp.getRetType() != 0) {
            System.out.printf("get code=[%s] option chain failed\n", rsp.getRetMsg());
        } else {
            try {
                int optionChainCount = rsp.getS2C().getOptionChainCount();
                if (optionChainCount > 0) {
                    String code = rsp.getS2C().getOptionChainOrBuilder(0).getOptionOrBuilder(0).getCall().getOptionExData().getOwner().getCode();
                    System.out.println(code);
                    fw.write(code + "\n");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public List<String> getCodeList(String market) {
        List<String> codeList = Lists.newArrayList();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream("/historicalData/code/list/" + market)));
            String code;
            while (StringUtils.isNotBlank(code = br.readLine())) {
                codeList.add(code);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return codeList;
    }

    public static void main(String[] args) throws Exception {
        FTAPI.init();
        OptionChain qot = new OptionChain();
        qot.start();

        TimeUnit.SECONDS.sleep(5);

        List<String> marketList = Lists.newArrayList("XNAS-ADRC","XNYS-ADRC");
        for (String market : marketList) {
            List<String> codeList = qot.getCodeList(market);
            qot.fw = new FileWriter(market + "_x");
            for (int i = 0; i < codeList.size(); i++) {
                String code = codeList.get(i);
                QotCommon.Security sec = QotCommon.Security.newBuilder()
                  .setMarket(QotCommon.QotMarket.QotMarket_US_Security_VALUE)
                  .setCode(code)
                  .build();
                QotGetOptionChain.C2S c2s = QotGetOptionChain.C2S.newBuilder()
                  .setOwner(sec)
                  .setBeginTime("2023-02-06")
                  .setEndTime("2023-02-10")
                  .build();
                QotGetOptionChain.Request req = QotGetOptionChain.Request.newBuilder().setC2S(c2s).build();
                qot.qot.getOptionChain(req);
                // System.out.println(code + " " + qot.qot.getOptionChain(req));

                if ((i + 1) % 10 == 0) {
                    qot.fw.flush();
                    TimeUnit.SECONDS.sleep(35);
                }
            }
            TimeUnit.SECONDS.sleep(35);
        }
    }

}
