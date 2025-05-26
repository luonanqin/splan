package luonq.ws;

import bean.OptionStraddle;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import luonq.futu.BasicQuote;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MyWebSocketHandler extends TextWebSocketHandler {

    public static String initialData = "{\"a\": \"value1\",\"b\": \"value2\",\"c\": \"value3\",\"d\": \"value4\",\"e\": [\"item1\", \"item2\", \"item3\", \"item4\", \"item5\"]}";

    private BasicQuote bq = new BasicQuote();

    public static ThreadPoolExecutor cachedThread = new ThreadPoolExecutor(10, 10, 600, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    public MyWebSocketHandler() {
        bq.start();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 连接建立时发送初始数据
        //        session.sendMessage(new TextMessage(initialData));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();  // 接收客户端消息
        System.out.println("Received: " + payload);

        // 向客户端发送消息
        //        session.sendMessage(new TextMessage("Server response: " + payload));
        //        session.sendMessage(new TextMessage(initialData));

        String[] split = payload.split("\\-");
        String code = split[0];
        String priceStr = split[1];

        if (StringUtils.equalsIgnoreCase("SPX", code)) {
            Future<List<String>> callFuture = cachedThread.submit(() -> callSPX(priceStr));
            Future<List<String>> putFuture = cachedThread.submit(() -> putSPX(priceStr));
            List<String> callList = callFuture.get();
            List<String> putList = putFuture.get();

            Map<String, Double> codeToAskMap = bq.getOriCodeToAskMap();
            Map<String, Double> codeToBidMap = bq.getOriCodeToBidMap();
            while (true) {
                // todo 返回摆盘数据 callcode putcode call中间价 put中间价
                // 配对call和put，使得call和put的差价绝对值最小

                Map<String, Double> callMidMap = Maps.newHashMap();
                for (String call : callList) {
                    Double ask = codeToAskMap.get(call);
                    Double bid = codeToBidMap.get(call);
                    BigDecimal midPriceDecimal = BigDecimal.valueOf((ask + bid) / 2).setScale(2, RoundingMode.DOWN);
                    double mid = midPriceDecimal.doubleValue();
                    callMidMap.put(call, mid);
                }
                Map<String, Double> putMidMap = Maps.newHashMap();
                for (String put : putList) {
                    Double ask = codeToAskMap.get(put);
                    Double bid = codeToBidMap.get(put);
                    BigDecimal midPriceDecimal = BigDecimal.valueOf((ask + bid) / 2).setScale(2, RoundingMode.DOWN);
                    double mid = midPriceDecimal.doubleValue();
                    putMidMap.put(put, mid);
                }

                List<OptionStraddle> osList = Lists.newArrayList();
                JSONArray options = new JSONArray();
                for (String call : callList) {
                    Double callMid = callMidMap.get(call);
                    double min = Double.MAX_VALUE;
                    String putRes = "";
                    Double putMidRes = 0d;
                    for (String put : putList) {
                        Double putMid = putMidMap.get(put);
                        double diff = Math.abs(callMid - putMid);
                        if (min > diff) {
                            min = diff;
                            putRes = put;
                            putMidRes = putMid;
                        }
                    }
                    OptionStraddle os = new OptionStraddle();
                    os.setCallCode(call);
                    os.setPutCode(putRes);
                    os.setCallMidPrice(callMid);
                    os.setPutMidPrice(putMidRes);
                    double sumPrice = callMid + putMidRes;
                    os.setSumPrice(sumPrice);
                    JSONObject option1 = new JSONObject();
                    option1.put("callCode", call);
                    option1.put("putCode", putRes);
                    option1.put("callMidPrice", callMid);
                    option1.put("putMidPrice", putMidRes);
                    option1.put("sumPrice", sumPrice);
                    options.add(option1);
                    osList.add(os);
                }

                JSONObject res = new JSONObject();
                res.put("options", options);
                session.sendMessage(new TextMessage(res.toString()));

                Thread.sleep(1000);
                System.out.println("refresh");
            }
        }
    }

    public List<String> callSPX(String priceStr) {
        int price = Double.valueOf(priceStr).intValue() / 5 * 5;

        LocalDate today = LocalDate.now();
        String yyMMdd = today.format(DateTimeFormatter.ofPattern("yyMMdd"));
        yyMMdd = "250527";
        // todo 如果程序持续运行，这里要删除历史期权的订阅，如果每天运行则不需要

        int up = price + 50;

        Map<String, Double> codeToAskMap = bq.getOriCodeToAskMap();
        Map<String, Double> codeToBidMap = bq.getOriCodeToBidMap();
        List<String> unSubOrderBook = Lists.newArrayList();
        // SPXW250527P5800000

        // 循环增加call的价格，直到摆盘价在0.5到1的范围内，且数量不超过十个
        List<String> subCallOrderBook = Lists.newArrayList();
        boolean callBottom = false;
        while (true) {
            String option = "SPXW" + yyMMdd + "C" + up + "000";
            bq.subOrderBook(option);
            subCallOrderBook.add(option);

            Double bid = 0d;
            while (true) {
                Double ask = codeToAskMap.get(option);
                bid = codeToBidMap.get(option);
                if (ask == null || bid == null) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                } else {
                    if (bid > 1 || ask < 0.5) {
                        unSubOrderBook.add(option);
                        subCallOrderBook.remove(option);
                    }
                    if (ask < 0.5) {
                        callBottom = true;
                    }
                    break;
                }
            }
            if (subCallOrderBook.size() >= 10 || callBottom) {
                break;
            } else {
                if (bid > 2) {
                    up += 3 * 5;
                } else {
                    up += 5;
                }
            }
        }

        //        try {
        //            Thread.sleep(60000);
        //        } catch (InterruptedException e) {
        //        }

        unSubOrderBook.forEach(e -> bq.unSubOrderBook(e));

        return subCallOrderBook;
    }

    public List<String> putSPX(String priceStr) {
        int price = Integer.valueOf(priceStr) / 5 * 5;

        LocalDate today = LocalDate.now();
        String yyMMdd = today.format(DateTimeFormatter.ofPattern("yyMMdd"));
        yyMMdd = "250527";
        // todo 如果程序持续运行，这里要删除历史期权的订阅，如果每天运行则不需要

        int down = price - 50;

        Map<String, Double> codeToAskMap = bq.getOriCodeToAskMap();
        Map<String, Double> codeToBidMap = bq.getOriCodeToBidMap();
        List<String> unSubOrderBook = Lists.newArrayList();
        // SPXW250527P5800000

        // 循环增加put的价格，直到摆盘价在0.5到1的范围内，且数量不超过十个
        List<String> subPutOrderBook = Lists.newArrayList();
        boolean putBottom = false;
        while (true) {
            String option = "SPXW" + yyMMdd + "P" + down + "000";
            bq.subOrderBook(option);
            subPutOrderBook.add(option);

            Double bid = 0d;
            while (true) {
                Double ask = codeToAskMap.get(option);
                bid = codeToBidMap.get(option);
                if (ask == null || bid == null) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                } else {
                    if (bid > 1 || ask < 0.5) {
                        unSubOrderBook.add(option);
                        subPutOrderBook.remove(option);
                    }
                    if (ask < 0.5) {
                        putBottom = true;
                    }
                    break;
                }
            }
            if (subPutOrderBook.size() >= 10 || putBottom) {
                break;
            } else {
                if (bid > 2) {
                    down -= 3 * 5;
                }
                down -= 5;
            }
        }

        //        try {
        //            Thread.sleep(60000);
        //        } catch (InterruptedException e) {
        //        }

        unSubOrderBook.forEach(e -> bq.unSubOrderBook(e));

        return subPutOrderBook;
    }

    public static void main(String[] args) {
        MyWebSocketHandler myWebSocketHandler = new MyWebSocketHandler();
        List<String> strings = myWebSocketHandler.callSPX("5884.23");
    }
}
