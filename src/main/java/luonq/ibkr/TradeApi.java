package luonq.ibkr;

import com.futu.openapi.pb.TrdCommon;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ib.client.ComboLeg;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.OrderCondition;
import com.ib.client.OrderConditionType;
import com.ib.client.OrderStatus;
import com.ib.client.OrderType;
import com.ib.client.PriceCondition;
import com.ib.client.TimeCondition;
import com.ib.client.Types;
import com.ib.controller.AccountSummaryTag;
import com.ib.controller.ApiController;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import util.Constants;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class TradeApi {

    public static String simulateAccount = "DUA100430";
    public static String realAccount = "U18653989";
    private PositionHandlerImpl positionHandler = new PositionHandlerImpl();
    private Map<Integer/* orderId */, OrderHandlerImpl> orderIdToHandlerMap = Maps.newHashMap();
    private Map<Integer/* orderId */, Contract> orderIdToContractMap = Maps.newHashMap();
    private Map<Integer/* orderId */, Order> orderIdToOrderMap = Maps.newHashMap();
    private int port;
    private ApiController client;
    private boolean real = true;

    public TradeApi() {
        this(false);
    }

    public TradeApi(boolean simulate) {
        real = !simulate;
        if (!real) {
            useSimulateEnv();
        } else {
            useRealEnv();
        }
        start();
    }

    public void useSimulateEnv() {
        port = 7497;
    }

    public void useRealEnv() {
        port = 7496;
    }

    public void setAccountId(long accountId) {
    }

    public void unlock() {
    }

    public void start() {
        ApiController.IConnectionHandler connectionHanlder = new ConnectionHanlderImpl();
        client = new ApiController(connectionHanlder);
        client.connect("127.0.0.1", port, 1, null);
    }

    public void end() {
        client.disconnect();
    }

    public long placeNormalBuyOrder(String code, double count, double price) {
        long orderId = placeNormalOrder(code, price, count, Types.SecType.OPT, Types.Action.BUY);
        log.info("place buy order. code={}\tcount={}\tprice={}\torderId={}", code, count, price, orderId);
        for (int i = 0; i < 10; i++) {
            bean.Order buyCallOrder = getOrder(orderId);
            if (buyCallOrder == null) {
                log.info("order is null. orderId={}", orderId);
                continue;
            }

            int orderStatus = buyCallOrder.getOrderStatus();
            if (orderStatus == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE || orderStatus == TrdCommon.OrderStatus.OrderStatus_Submitted_VALUE) {
                log.info("buy option submit success. code={}", code);
                break;
            } else if (orderStatus == TrdCommon.OrderStatus.OrderStatus_Cancelled_All_VALUE) {
                return -1;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
            }
            log.info("waiting buy option. code={}", code);
        }
        bean.Order buyCallOrder = getOrder(orderId);
        if (buyCallOrder != null) {
            int orderStatus = buyCallOrder.getOrderStatus();
            if (orderStatus == 0 || orderStatus == TrdCommon.OrderStatus.OrderStatus_WaitingSubmit_VALUE) {
                cancelOrder(orderId);
            }
        } else {
            log.info("there is no leagl order. code={}, orderId={}", code, orderId);
            return -1;
        }
        return orderId;
    }

    public long placeMarketConditionBuyStockOrder(String code, double count, List<OrderCondition> condList) {
        Contract contract = new Contract();
        contract.localSymbol(code);
        contract.secType(Types.SecType.STK);
        contract.exchange("SMART");
        contract.currency("USD");

        Order order = new Order();
        order.action(Types.Action.BUY);
        order.orderType(OrderType.MKT);
        order.totalQuantity(Decimal.get(count));
        order.conditions(condList);
        order.conditionsIgnoreRth(false);

        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        orderHandler.setCode(code);
        orderHandler.setCount(count);
        client.placeOrModifyOrder(contract, order, orderHandler);
        int orderId = order.orderId();
        orderHandler.setOrderId(orderId);

        orderIdToContractMap.put(orderId, contract);
        orderIdToOrderMap.put(orderId, order);
        orderIdToHandlerMap.put(orderId, orderHandler);

        log.info("place condition order: code=" + code + " conditions=" + condList + " count=" + count);
        return orderId;
    }

    public long placeNormalBuyOrderForStock(String code, double count, double price) {
        return placeNormalOrder(code, price, count, Types.SecType.STK, Types.Action.BUY);
    }

    public long placeNormalSellOrder(String code, double count, double price) {
        long orderId = placeNormalOrder(code, price, count, Types.SecType.OPT, Types.Action.SELL);
        log.info("place sell order. code={}\tcount={}\tprice={}\torderId={}", code, count, price, orderId);
        while (true) {
            bean.Order sellCallOrder = getOrder(orderId);
            if (sellCallOrder == null) {
                continue;
            }

            int orderStatus = sellCallOrder.getOrderStatus();
            if (orderStatus == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE || orderStatus == TrdCommon.OrderStatus.OrderStatus_Submitted_VALUE) {
                log.info("sell option submit success. code={}", code);
                break;
            } else if (orderStatus == TrdCommon.OrderStatus.OrderStatus_Cancelled_All_VALUE) {
                return -1;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
            }
            log.info("waiting sell option. code={}", code);
        }
        return orderId;
    }

    public long placeNormalSellOrderForStock(String code, double count, double price) {
        return placeNormalOrder(code, price, count, Types.SecType.STK, Types.Action.SELL);
    }

    public long placeNormalOrder(String code, double price, double count, Types.SecType secType, Types.Action action) {
        Contract contract = new Contract();
        contract.localSymbol(code);
        contract.secType(secType);
        contract.exchange("SMART");

        Order order = new Order();
        order.action(action);
        order.lmtPrice(price);
        order.orderType(OrderType.LMT);
        order.totalQuantity(Decimal.get(count));

        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        orderHandler.setCode(code);
        orderHandler.setCount(count);
        client.placeOrModifyOrder(contract, order, orderHandler);
        int orderId = order.orderId();
        orderHandler.setOrderId(orderId);

        orderIdToContractMap.put(orderId, contract);
        orderIdToOrderMap.put(orderId, order);
        orderIdToHandlerMap.put(orderId, orderHandler);

        //        log.info("place order: action=" + action + " price=" + price + " count=" + count);
        return orderId;
    }

    // modify order
    public long upOrderPrice(long orderId, double count, double price) {
        modifyOrder((int) orderId, price, count);
        for (int i = 0; i < 4; i++) {
            bean.Order order = getOrder(orderId);
            if (order == null) {
                continue;
            }

            int orderStatus = order.getOrderStatus();
            if (orderStatus == TrdCommon.OrderStatus.OrderStatus_Filled_All_VALUE || orderStatus == TrdCommon.OrderStatus.OrderStatus_Submitted_VALUE) {
                log.info("modify option submit success. orderId={}", orderId);
                break;
            } else if (orderStatus == TrdCommon.OrderStatus.OrderStatus_Cancelled_All_VALUE) {
                return -1;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
            }
            log.info("waiting modify option. orderId={}", orderId);
        }
        bean.Order order = getOrder(orderId);
        if (order.getOrderStatus() == TrdCommon.OrderStatus.OrderStatus_Cancelled_All_VALUE) {
            log.info("modify order failed. orderId={}", orderId);
            return -1;
        }

        return orderId;
    }

    public void modifyOrder(int orderId, double price, double count) {
        if (!orderIdToOrderMap.containsKey(orderId) || !orderIdToContractMap.containsKey(orderId)) {
            // todo log
            return;
        }

        Contract contract = orderIdToContractMap.get(orderId);
        Order order = orderIdToOrderMap.get(orderId);
        order.lmtPrice(price);
        order.totalQuantity(Decimal.get(count));

        OrderHandlerImpl orderHandler = orderIdToHandlerMap.get(orderId);
        client.placeOrModifyOrder(contract, order, orderHandler);
        log.info("modify order: orderId={}, action={}, price={}, count={}", orderId, order.action(), price, count);
    }

    public long cancelOrder(long orderId) {
        OrderCancelHandlerImpl orderCancelHandler = new OrderCancelHandlerImpl();
        orderCancelHandler.setOrderId((int) orderId);
        Contract contract = orderIdToContractMap.get((int) orderId);
        if (contract != null) {
            orderCancelHandler.setCode(contract.localSymbol());
        }

        client.cancelOrder((int) orderId, null, orderCancelHandler);

        return orderId;
    }

    public void removeOrderHandler(long orderId) {
        OrderHandlerImpl orderHandler = orderIdToHandlerMap.get((int) orderId);
        if (orderHandler == null) {
            return;
        }

        client.removeOrderHandler(orderHandler);
    }

    public bean.Order getOrder(long orderId) {
        OrderHandlerImpl orderHandler = orderIdToHandlerMap.get((int) orderId);
        if (orderHandler == null) {
            return null;
        }

        double avgPrice = orderHandler.getAvgPrice();
        OrderStatus status = orderHandler.getStatus();
        double count = orderHandler.getCount();
        //        System.out.println("avgPrice: " + avgPrice + " status: " + status);

        bean.Order order = new bean.Order();
        order.setOrderID(orderId);
        order.setAvgPrice(avgPrice);
        order.setTradeCount(count);
        if (status == OrderStatus.Submitted) {
            order.setOrderStatus(5);
        } else if (status == OrderStatus.Filled) {
            order.setOrderStatus(11);
        } else if (status == OrderStatus.PreSubmitted) {
            order.setOrderStatus(1);
        } else if (status == OrderStatus.Cancelled) {
            order.setOrderStatus(15);
        }

        return order;
    }

    public double getCanSellQty(String code) {
        return positionHandler.getCanSellQty(code);
    }

    public void reqPosition() {
        client.reqPositions(positionHandler);
    }

    public boolean positionIsEmpty() {
        return positionHandler.isEmpty();
    }

    public void rebuildOrderHandler(long orderId, double cost, double count, OrderStatus status) {
        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        orderHandler.setAvgPrice(cost);
        orderHandler.setCount(count);
        orderHandler.setStatus(status);

        orderIdToHandlerMap.put((int) orderId, orderHandler);
    }

    public double getAccountCash() {
        double cash = Constants.INIT_CASH;
        AccountSummaryHandlerImpl accountSummaryHandler = new AccountSummaryHandlerImpl();
        for (int i = 0; i < 3; i++) {
            log.info("getAccountCash");
            client.reqAccountSummary("All", new AccountSummaryTag[] { AccountSummaryTag.NetLiquidation }, accountSummaryHandler);
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
            }
            client.cancelAccountSummary(accountSummaryHandler);
            double accountCash = accountSummaryHandler.getCash();

            if (accountCash == 0) {
                continue;
            } else {
                cash = accountCash;
                break;
            }
        }
        return cash;
    }

    public double getAvailableCash() {
        double cash = Constants.INIT_CASH;
        AvailableCashHandlerImpl accountSummaryHandler = new AvailableCashHandlerImpl();
        for (int i = 0; i < 3; i++) {
            log.info("getAccountCash");
            client.reqAccountSummary("All", new AccountSummaryTag[] { AccountSummaryTag.SettledCash }, accountSummaryHandler);
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
            }
            client.cancelAccountSummary(accountSummaryHandler);
            double accountCash = accountSummaryHandler.getCash();

            if (accountCash == 0) {
                continue;
            } else {
                cash = accountCash;
                break;
            }
        }
        return cash;
    }

    public double getPnl() {
        Map<Integer, Double> conIdValueMap = Maps.newHashMap();
        conIdValueMap.put(51529211, 8979d); // GLD
        conIdValueMap.put(320227571, 8723d); // QQQ
        conIdValueMap.put(15547844, 5998d); // IEF
        double allCost = conIdValueMap.values().stream().collect(Collectors.summingDouble(Double::new));
        double allPnl = 0;

        String account = real ? realAccount : simulateAccount;
        for (Integer conId : conIdValueMap.keySet()) {
            PnLSingleHandlerImpl iPnLSingleHandler = new PnLSingleHandlerImpl();
            client.reqPnLSingle(account, "", conId, iPnLSingleHandler); // GLD
            double value = 0d;
            for (int i = 0; i < 10; i++) {
                value = iPnLSingleHandler.getValue();
                if (value == 0d) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    break;
                }
            }
            if (value == 0d) {
                value = conIdValueMap.get(conId);
            }
            allPnl += value;
            log.info("{} pnl is {}, cost is {}", conId, value, conIdValueMap.get(conId));
            client.cancelPnLSingle(iPnLSingleHandler);
        }

        int diff = (int) (allPnl - allCost);
        log.info("all pnl is {}, all cost is {}, diff is {}", allPnl, allCost, diff);
        return diff;
    }

    public Contract buildSpread(String stock, int buyConId, int sellConId) {
        Contract contract = new Contract();
        contract.symbol(stock);
        contract.secType(Types.SecType.BAG);
        contract.exchange("SMART");
        contract.currency("USD");

        ComboLeg leg1 = new ComboLeg();
        ComboLeg leg2 = new ComboLeg();
        List<ComboLeg> addAllLegs = Lists.newArrayList();
        leg1.conid(buyConId);
        leg1.ratio(1);
        leg1.action(Types.Action.BUY);
        leg1.exchange("SMART");

        leg2.conid(sellConId);
        leg2.ratio(1);
        leg2.action(Types.Action.SELL);
        leg2.exchange("SMART");
        addAllLegs.add(leg1);
        addAllLegs.add(leg2);

        contract.comboLegs(addAllLegs);

        return contract;
    }

    public int buySpread(String stock, int buyConId, int sellConId, double count) throws Exception {
        Contract contract = buildSpread(stock, buyConId, sellConId);

        Order order = new Order();
        order.action(Types.Action.BUY);
        order.orderType(OrderType.MKT);
        order.totalQuantity(Decimal.get(count));

        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        client.placeOrModifyOrder(contract, order, orderHandler);
        int orderId = order.orderId();
        orderHandler.setOrderId(orderId);
        log.info("buy spread place order. orderId={}\tstock={}\tbuyConId={}\tsellConId={}\tcount={}", orderId, stock, buyConId, sellConId, count);

        while (true) {
            OrderStatus status = orderHandler.getStatus();
            if (status == OrderStatus.Filled) {
                log.info("buy spread place order success. orderId={}", orderId);
                break;
            } else if (orderHandler.getErrorCode() == 201) {
                log.info("buy spread place order has been cancelled. orderId={}\tstock={}\tbuyConId={}\tsellConId={}\tcount={}", orderId, stock, buyConId, sellConId, count);
                return -1;
            } else {
                log.info("waiting trade. orderId={}", orderId);
                TimeUnit.SECONDS.sleep(1);
            }
        }

        orderIdToContractMap.put(orderId, contract);
        orderIdToOrderMap.put(orderId, order);
        orderIdToHandlerMap.put(orderId, orderHandler);

        return orderId;
    }

    public void stopForBuySpread_lmt(String stock, int buyConId, int sellConId, int count, double stopLossPrice) {
        Contract contract = buildSpread(stock, buyConId, sellConId);

        Order order = new Order();
        order.action(Types.Action.SELL);
        order.auxPrice(stopLossPrice);
        order.orderType(OrderType.STP);
        order.totalQuantity(Decimal.get(count));

        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        client.placeOrModifyOrder(contract, order, orderHandler);
    }

    public void stopForBuySpread_lmt(int orderId, double stopLossPrice) {
        Contract contract = orderIdToContractMap.get(orderId);
        Order buyOrder = orderIdToOrderMap.get(orderId);

        Order order = new Order();
        order.action(Types.Action.SELL);
        order.lmtPrice(stopLossPrice);
        order.orderType(OrderType.LMT);
        order.totalQuantity(buyOrder.totalQuantity());

        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        client.placeOrModifyOrder(contract, order, orderHandler);
    }

    public void stopForBuySpread_mkt(int orderId) {
        Contract contract = orderIdToContractMap.get(orderId);
        Order buyOrder = orderIdToOrderMap.get(orderId);

        Order order = new Order();
        order.action(Types.Action.SELL);
        order.orderType(OrderType.MKT);
        order.totalQuantity(buyOrder.totalQuantity());

        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        client.placeOrModifyOrder(contract, order, orderHandler);
    }

    public int getOptionConId(String code) throws InterruptedException {
        Contract contract = new Contract();
        contract.localSymbol(code);
        contract.secType(Types.SecType.OPT);
        contract.exchange("SMART");
        CountDownLatch cdl = new CountDownLatch(1);
        client.reqContractDetails(contract, list -> {
            try {
                if (CollectionUtils.isEmpty(list)) {
                    log.info("{} can't get conId", code);
                    return;
                }
                ContractDetails contractDetails = list.get(0);
                int conid = contractDetails.conid();
                contract.conid(conid);
                log.info("{} conId={}", code, conid);
            } catch (Exception e) {
                log.error("get option conId error. code={}", code, e);
            } finally {
                cdl.countDown();
            }
        });
        cdl.await(2, TimeUnit.SECONDS);
        return contract.conid();
    }

    public int getStockConId(String code) throws InterruptedException {
        Contract contract = new Contract();
        contract.localSymbol(code);
        contract.secType(Types.SecType.STK);
        contract.exchange("SMART");
        CountDownLatch cdl = new CountDownLatch(1);
        client.reqContractDetails(contract, list -> {
            try {
                if (CollectionUtils.isEmpty(list)) {
                    log.info("{} can't get conId", code);
                    return;
                }
                ContractDetails contractDetails = list.get(0);
                int conid = contractDetails.conid();
                contract.conid(conid);
                log.info("{} conId={}", code, conid);
            } catch (Exception e) {
                log.error("get stock conId error. code={}", code, e);
            } finally {
                cdl.countDown();
            }
        });
        cdl.await(2, TimeUnit.SECONDS);

        return contract.conid();
    }

    /**
     * @param datetime      2025-03-03 15:59:50
     * @param beforeOrAfter after=true before=false
     * @return
     */
    public TimeCondition buildTimeCond(String datetime, boolean beforeOrAfter) {
        TimeCondition timeCondition = (TimeCondition) OrderCondition.create(OrderConditionType.Time);
        timeCondition.time(datetime);
        timeCondition.isMore(beforeOrAfter);

        return timeCondition;
    }

    /**
     * @param price       10d
     * @param greatOrLess great=true less=false
     * @return
     */
    public PriceCondition buildPriceCond(double price, boolean greatOrLess) {
        PriceCondition priceCondition = (PriceCondition) OrderCondition.create(OrderConditionType.Price);
        priceCondition.price(price);
        priceCondition.isMore(greatOrLess);

        return priceCondition;
    }

    // 开盘市价单
    public long placeOpenBuyStockOrder(String code, int count) {
        Contract contract = new Contract();
        contract.localSymbol(code);
        contract.secType(Types.SecType.STK);
        contract.exchange("SMART");

        Order order = new Order();
        order.action(Types.Action.BUY);
        order.orderType(OrderType.MKT);
        order.tif("OPG");
        order.totalQuantity(Decimal.get(count));

        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        orderHandler.setCode(code);
        orderHandler.setCount(count);
        client.placeOrModifyOrder(contract, order, orderHandler);
        int orderId = order.orderId();
        orderHandler.setOrderId(orderId);

        orderIdToContractMap.put(orderId, contract);
        orderIdToOrderMap.put(orderId, order);
        orderIdToHandlerMap.put(orderId, orderHandler);

        log.info("place open buy order: code=" + code + " count=" + count);
        return orderId;
    }

    public long placeUpDownOpenBuyStockOrder(String code, int count, double price, String date, boolean priceCond, boolean timeCond) {
        Contract contract = new Contract();
        contract.localSymbol(code);
        contract.secType(Types.SecType.STK);
        contract.exchange("SMART");
        contract.currency("USD");

        Order order = new Order();
        order.action(Types.Action.BUY);
        order.orderType(OrderType.MKT);
        order.totalQuantity(Decimal.get(count));

        TimeCondition timeCondition = buildTimeCond(date + "-14:30:05", timeCond);
        timeCondition.conjunctionConnection(true);
        PriceCondition priceCondition = buildPriceCond(price, priceCond);
        order.conditions(Lists.newArrayList(timeCondition, priceCondition));
        order.conditionsIgnoreRth(false);

        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        orderHandler.setCode(code);
        orderHandler.setCount(count);
        client.placeOrModifyOrder(contract, order, orderHandler);
        int orderId = order.orderId();
        orderHandler.setOrderId(orderId);

        orderIdToContractMap.put(orderId, contract);
        orderIdToOrderMap.put(orderId, order);
        orderIdToHandlerMap.put(orderId, orderHandler);

        String openInfo = priceCond ? "up" : "down";
        log.info("place open " + openInfo + " buy open order: code=" + code + " count=" + count);
        return orderId;
    }

    // 20250305-20:59:59
    // 开盘上涨开盘买
    public long placeUpOpenBuyStockOrder(String code, int count, double price, String date) {
        return placeUpDownOpenBuyStockOrder(code, count, price, date, true, false);
    }

    // 开盘下跌开盘买
    public long placeDownOpenBuyStockOrder(String code, int count, double price, String date) {
        return placeUpDownOpenBuyStockOrder(code, count, price, date, false, false);
    }

    public long placeUpDownCloseBuyStockOrder(String code, int count, double price, String date, boolean priceCond, boolean timeCond) {
        Contract contract = new Contract();
        contract.localSymbol(code);
        contract.secType(Types.SecType.STK);
        contract.exchange("SMART");
        contract.currency("USD");

        Order order = new Order();
        order.action(Types.Action.BUY);
        order.orderType(OrderType.MKT);
        order.totalQuantity(Decimal.get(count));

        TimeCondition timeCondition = buildTimeCond(date + "-20:59:50", timeCond);
        timeCondition.conjunctionConnection(true);
        PriceCondition priceCondition = buildPriceCond(price, priceCond);
        order.conditions(Lists.newArrayList(timeCondition, priceCondition));
        order.conditionsIgnoreRth(false);

        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        orderHandler.setCode(code);
        orderHandler.setCount(count);
        client.placeOrModifyOrder(contract, order, orderHandler);
        int orderId = order.orderId();
        orderHandler.setOrderId(orderId);

        orderIdToContractMap.put(orderId, contract);
        orderIdToOrderMap.put(orderId, order);
        orderIdToHandlerMap.put(orderId, orderHandler);

        String openInfo = priceCond ? "up" : "down";
        log.info("place open " + openInfo + " buy close order: code=" + code + " count=" + count);
        return orderId;
    }

    // 开盘下跌收盘买
    public long placeDownCloseBuyStockOrder(String code, int count, double price, String date) {
        return placeUpDownCloseBuyStockOrder(code, count, price, date, false, true);
    }

    // 开盘上涨收盘买
    public long placeUpCloseBuyStockOrder(String code, int count, double price, String date) {
        return placeUpDownCloseBuyStockOrder(code, count, price, date, true, true);
    }

    // 收盘下跌收盘买
    public long placeDownCloseBuyStockOrder(String code, int count, double price, String date) {
        Contract contract = new Contract();
        contract.localSymbol(code);
        contract.secType(Types.SecType.STK);
        contract.exchange("SMART");
        contract.currency("USD");

        Order order = new Order();
        order.action(Types.Action.BUY);
        order.orderType(OrderType.MKT);
        order.totalQuantity(Decimal.get(count));

        TimeCondition timeCondition = buildTimeCond(date + "-20:59:50", true);
        timeCondition.conjunctionConnection(true);
        PriceCondition priceCondition = buildPriceCond(price, false);
        order.conditions(Lists.newArrayList(timeCondition, priceCondition));
        order.conditionsIgnoreRth(false);

        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        orderHandler.setCode(code);
        orderHandler.setCount(count);
        client.placeOrModifyOrder(contract, order, orderHandler);
        int orderId = order.orderId();
        orderHandler.setOrderId(orderId);

        orderIdToContractMap.put(orderId, contract);
        orderIdToOrderMap.put(orderId, order);
        orderIdToHandlerMap.put(orderId, orderHandler);

        String openInfo = priceCond ? "up" : "down";
        log.info("place close " + openInfo + " buy open order: code=" + code + " count=" + count);
        return orderId;
    }

    public static void main(String[] args) {
        TradeApi tradeApi = new TradeApi(true);
        tradeApi.reqPosition();
        double accountCash = tradeApi.getAccountCash();
        System.out.println(accountCash);
        double pnl = tradeApi.getPnl();
        accountCash = accountCash - pnl;
        System.out.println(accountCash);
        int count = 1;
        //        tradeApi.positionHandler.setAvgCost("NVDA240802P00110000", count);
        String code = "NVDA  241011P00125000";
        //        long orderId = tradeApi.placeNormalBuyOrder(code, count, 0.6);
        TimeCondition timeCondition = tradeApi.buildTimeCond("2025-03-05 15:59:59", true);
        timeCondition.conjunctionConnection(true);
        PriceCondition priceCondition = tradeApi.buildPriceCond(220d, false);
        long orderId = tradeApi.placeMarketConditionBuyStockOrder("AAPL", 1, Lists.newArrayList(timeCondition, priceCondition));
        //        long orderId = tradeApi.placeNormalBuyOrderForStock("AAPL", 1, 224.6);
        System.out.println("orderId: " + orderId);
        //        bean.Order order = tradeApi.getOrder(orderId);
        //        tradeApi.setPositionAvgCost(code, order.getAvgPrice());

        //        Map<String, StockPosition> positionMap = tradeApi.getPositionMap(null); // todo 要限制时间不能死等
        //        System.out.println("position: " + positionMap);

        //        System.out.println();
        //        for (int i = 0; i < 4; i++) {
        //            long modifyOrderId = tradeApi.upOrderPrice(orderId, count, 0.61);
        //            tradeApi.upOrderPrice(orderId, count, 0.62);
        //            System.out.println("modifyOrderId: " + modifyOrderId);
        //        }

        //        tradeApi.getOrder(orderId);

        //        tradeApi.removeOrderHandler(orderId);
        //        long sellorderId = tradeApi.placeNormalSellOrderForStock(code, count, 1.0);
        //        long sellorderId = tradeApi.placeNormalSellOrder(code, count, 0.9);

        //        tradeApi.cancelOrder(orderId);

        //        tradeApi.getOrder(orderId);

        System.out.println();

        //        tradeApi.end();
    }
}
