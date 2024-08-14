package luonq.ibkr;

import com.ib.controller.ApiController;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ConnectionHanlderImpl implements ApiController.IConnectionHandler {

    @Override
    public void connected() {
        log.info("connected");
    }

    @Override
    public void disconnected() {
        log.info("disconnected");
    }

    @Override
    public void accountList(List<String> list) {
        log.info("account list: {}", list);
    }

    @Override
    public void error(Exception e) {
        log.error("connection error: ", e);
    }

    @Override
    public void message(int id, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        log.info("connection message: id={}\terrorCode={}\terrorMsg={}\tadvancedOrderRejectJson={}", id, errorCode, errorMsg, advancedOrderRejectJson);
    }

    @Override
    public void show(String string) {
        log.info("connection show: {}", string);
    }
}
