package luonq.polygon;

import bean.Node;
import bean.NodeList;
import com.futu.openapi.FTAPI;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import luonq.futu.BasicQuoteDemo;
import luonq.futu.GetOptionChain;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Created by Luonanqin on 2023/5/9.
 */
@Component
@Data
@Slf4j
public class OptionQuoteExecutor {

    private NodeList list;
    private BasicQuoteDemo quote;
    private GetOptionChain getOptionChain;
    private int cut = 990000;
    private List<String> tradeStock = Lists.newArrayList();
    private RealTimeDataWS_DB client;
    private boolean realTrade = true;

    public void init() {
        FTAPI.init();
        quote = new BasicQuoteDemo();
        quote.start();
        getOptionChain = new GetOptionChain();
        getOptionChain.start();
        //        tradeApi.useSimulateEnv();
        //        tradeApi.setAccountId(TradeApi.simulateUsAccountId);
        //        tradeApi.useRealEnv();
        //        tradeApi.start();
        //        tradeApi.unlock();
        //        tradeApi.clearStopLossStockSet();
    }

    public void beginSubcribe() {
        List<Node> nodes = list.getNodes();
        if (CollectionUtils.isEmpty(nodes)) {
            log.info("option stock is empty");
            return;
        }

        String show = list.show();
        System.out.println("option stock: " + show);
        for (Node node : nodes) {
            String code = node.getName();
            double price = node.getPrice();
            List<String> chainList = getOptionChain.getChainList(code);
            if (CollectionUtils.isEmpty(chainList)) {
                System.out.println(code + " has no option chain");
                continue;
            }

            String optionCode = getOptionCode(chainList, price);
            int c_index = optionCode.lastIndexOf("C");
            StringBuffer sb = new StringBuffer(optionCode);
            String putOptionCode = sb.replace(c_index, c_index + 1, "P").toString();

            subQuote(optionCode);
            subOrder(optionCode);

            subQuote(putOptionCode);
            subOrder(putOptionCode);
        }
        System.out.println("finish option subcribe");
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

    public String getOptionCode(List<String> chainList, double price) {
        // 找出当前价前后的行权价及等于当前价的行权价
        double priceDiff = Double.MAX_VALUE;
        String optionCode = null;
        for (String chain : chainList) {
            String[] split = chain.split("\t");
            String code = split[0];
            Double strikePrice = Double.valueOf(split[2]);
            if (strikePrice < price) {
                if (priceDiff > price - strikePrice) {
                    priceDiff = price - strikePrice;
                    optionCode = code;
                }
            } else if (strikePrice == price) {
                optionCode = code;
                break;
            } else if (strikePrice > price) {
                if (priceDiff > strikePrice - price) {
                    optionCode = code;
                }
                break;
            }
        }

        return optionCode;
    }

    public static void main(String[] args) {
        OptionQuoteExecutor executor = new OptionQuoteExecutor();
        executor.init();
        //        executor.subOrder("");
        NodeList nodeList = new NodeList(10);
        Node node = new Node("RNAZ", 1);
        node.setPrice(5.71d);
        Node node2 = new Node("AAPL", 2);
        node2.setPrice(191.04);
        nodeList.add(node);
        nodeList.add(node2);
        executor.setList(nodeList);
        executor.beginSubcribe();

        //        futuListener.beginTrade();
    }
}
