package luonq.ibkr;

import com.ib.controller.ApiController;

import java.util.List;

public class ConnectionHanlderImpl implements ApiController.IConnectionHandler {

    @Override
    public void connected() {
        System.out.println("connected");
    }

    @Override
    public void disconnected() {
        System.out.println("disconnected");
    }

    @Override
    public void accountList(List<String> list) {
        System.out.println("account list:" + list);
    }

    @Override
    public void error(Exception e) {
        System.out.println("error: " + e.getMessage());
    }

    @Override
    public void message(int id, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        System.out.println("message: " + id + " " + errorCode + " " + errorMsg + " " + advancedOrderRejectJson);
    }

    @Override
    public void show(String string) {
        System.out.println("show: " + string);
    }
}
