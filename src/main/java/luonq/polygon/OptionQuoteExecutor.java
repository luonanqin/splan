package luonq.polygon;

import bean.Node;
import bean.NodeList;
import bean.OrderFill;
import bean.StockPosition;
import bean.StopLoss;
import com.futu.openapi.FTAPI;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import luonq.futu.BasicQuoteDemo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static util.Constants.TRADE_ERROR_CODE;

/**
 * Created by Luonanqin on 2023/5/9.
 */
@Component
@Data
@Slf4j
public class OptionQuoteExecutor {

//    private NodeList list;
    private BasicQuoteDemo quote;
    private int cut = 990000;
    private List<String> tradeStock = Lists.newArrayList();
    private RealTimeDataWS_DB client;
    private boolean realTrade = true;

    public void init() {
        FTAPI.init();
        quote = new BasicQuoteDemo();
        quote.start();
        //        tradeApi.useSimulateEnv();
        //        tradeApi.setAccountId(TradeApi.simulateUsAccountId);
        //        tradeApi.useRealEnv();
        //        tradeApi.start();
        //        tradeApi.unlock();
        //        tradeApi.clearStopLossStockSet();
    }

    public void begin(NodeList list){
        List<Node> nodes = list.getNodes();
        if (CollectionUtils.isNotEmpty(nodes)) {
            for (Node node : nodes) {
                String name = node.getName();

            }
        }
    }

    public void subQuote(String optionStock) {
        quote.subBasicQuote(optionStock);
    }

    public void subOrder(String optionStock) {
        quote.subOrderBook(optionStock);
    }

    public void setTradeStock(List<String> stocks) {
        tradeStock = stocks;
    }

    public static void main(String[] args) {
        OptionQuoteExecutor executor = new OptionQuoteExecutor();
        executor.subOrder("");
        //        NodeList nodeList = new NodeList(10);
        //        Node node = new Node("RNAZ", 1);
        //        node.setPrice(5.71d);
        //        nodeList.add(node);
        //        tradeExecutor.setList(nodeList);

        //        futuListener.beginTrade();
    }
}
