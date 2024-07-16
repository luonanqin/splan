package luonq.execute;

import bean.StockEvent;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ReadWriteOptionTradeInfo {

    public static String TRADE_INFO_BASE_PATH = Constants.USER_PATH + "optionData/tradeInfo/";

    /**
     * 股票的开盘价
     */
    public static String OPEN_PRICE_PATH = "openPrice";

    /**
     * 已下单买入的股票
     */
    public static String HAS_BOUGHT_ORDER_PATH = "hasBoughtOrder";
    /**
     * 已下单买入的期权订单id
     */
    public static String BUY_ORDER_ID_PATH = "buyOrderId";
    /**
     * 下单买入的时间
     */
    public static String BUY_ORDER_TIME_PATH = "buyOrderTime";
    /**
     * 下单买入成功的股票
     */
    public static String HAS_BOUGHT_SUCCESS_PATH = "hasBoughtSuccess";
    /**
     * 股票期权的下单数量
     */
    public static String ORDER_COUNT_PATH = "orderCount";

    /**
     * 已下单卖出的股票
     */
    public static String HAS_SOLD_ORDER_PATH = "hasSoldOrder";
    /**
     * 已下单卖出的期权订单id
     */
    public static String SELL_ORDER_ID_PATH = "sellOrderId";
    /**
     * 下单卖出的时间
     */
    public static String SELL_ORDER_TIME_PATH = "sellOrderTime";
    /**
     * 下单卖出成功的股票
     */
    public static String HAS_SOLD_SUCCESS_PATH = "hasSoldSuccess";

    private static List<String> openPriceList = Lists.newArrayList();

    public static void init() {
        String currentTradeDate = LoadOptionTradeData.currentTradeDate;
        String basePath = TRADE_INFO_BASE_PATH + currentTradeDate + "/";
        BaseUtils.createDirectory(basePath);
        OPEN_PRICE_PATH = basePath + OPEN_PRICE_PATH;
        HAS_BOUGHT_ORDER_PATH = basePath + HAS_BOUGHT_ORDER_PATH;
        BUY_ORDER_ID_PATH = basePath + BUY_ORDER_ID_PATH;
        BUY_ORDER_TIME_PATH = basePath + BUY_ORDER_TIME_PATH;
        HAS_BOUGHT_SUCCESS_PATH = basePath + HAS_BOUGHT_SUCCESS_PATH;
        ORDER_COUNT_PATH = basePath + ORDER_COUNT_PATH;
        HAS_SOLD_ORDER_PATH = basePath + HAS_SOLD_ORDER_PATH;
        SELL_ORDER_ID_PATH = basePath + SELL_ORDER_ID_PATH;
        SELL_ORDER_TIME_PATH = basePath + SELL_ORDER_TIME_PATH;
        HAS_SOLD_SUCCESS_PATH = basePath + HAS_SOLD_SUCCESS_PATH;
    }

    public static void addStockOpenPrice(String stock, Double price) {
        if (StringUtils.isNotBlank(stock)) {
            openPriceList.add(stock + "\t" + price);
        }
    }

    public static List<StockEvent> readStockOpenPrice() {
        List<StockEvent> events = Lists.newArrayList();
        try {
            List<String> lines = BaseUtils.readFile(OPEN_PRICE_PATH);
            for (String line : lines) {
                String[] split = line.split("\t");
                StockEvent stockEvent = new StockEvent();
                stockEvent.setStock(split[0]);
                stockEvent.setPrice(Double.valueOf(split[1]));
                events.add(stockEvent);
            }
        } catch (Exception e) {
            log.error("readStockOpenPrice error", e);
        }
        return events;
    }

    public static void writeStockOpenPrice() {
        try {
            BaseUtils.writeFile(OPEN_PRICE_PATH, openPriceList);
        } catch (Exception e) {
            log.error("writeStockOpenPrice error, lines={}", openPriceList, e);
        }
    }

    public static Set<String> readHasBoughtOrder() {
        Set<String> sets = Sets.newHashSet();
        List<String> lines = null;
        try {
            lines = BaseUtils.readFile(HAS_BOUGHT_ORDER_PATH);
        } catch (Exception e) {
            log.error("readHasBoughtOrder error", e);
        }
        if (CollectionUtils.isNotEmpty(lines)) {
            for (String line : lines) {
                sets.add(line);
            }
        }
        return sets;
    }

    public static void writeHasBoughtOrder(String stock) {
        List<String> list = Lists.newArrayList(readHasBoughtOrder());
        list.add(stock);
        try {
            BaseUtils.writeFile(HAS_BOUGHT_ORDER_PATH, list);
            log.info("writeHasBoughtOrder {}", stock);
        } catch (Exception e) {
            log.error("writeHasBoughtOrder error. stock={}, list={}", stock, list, e);
        }
    }

    public static Set<String> readHasBoughtSuccess() {
        Set<String> sets = Sets.newHashSet();
        List<String> lines = null;
        try {
            lines = BaseUtils.readFile(HAS_BOUGHT_SUCCESS_PATH);
        } catch (Exception e) {
            log.error("readHasBoughtSuccess error", e);
        }
        if (CollectionUtils.isNotEmpty(lines)) {
            for (String line : lines) {
                sets.add(line);
            }
        }
        return sets;
    }

    public static void writeHasBoughtSuccess(String stock) {
        List<String> list = Lists.newArrayList(readHasBoughtSuccess());
        list.add(stock);
        try {
            BaseUtils.writeFile(HAS_BOUGHT_SUCCESS_PATH, list);
            log.info("writeHasBoughtSuccess {}", stock);
        } catch (Exception e) {
            log.error("writeHasBoughtSuccess error. stock={}, list={}", stock, list, e);
        }
    }

    public static Map<String, Long> readBuyOrderId() {
        Map<String, Long> map = Maps.newHashMap();
        List<String> lines = null;
        try {
            lines = BaseUtils.readFile(BUY_ORDER_ID_PATH);
        } catch (Exception e) {
            log.error("readBuyOrderId error", e);
        }
        if (CollectionUtils.isNotEmpty(lines)) {
            for (String line : lines) {
                String[] split = line.split("\t");
                map.put(split[0], Long.valueOf(split[1]));
            }
        }
        return map;
    }

    public static void writeBuyOrderId(String optionFutu, Long orderId) {
        Map<String, Long> map = readBuyOrderId();
        map.put(optionFutu, orderId);
        List<String> lines = map.entrySet().stream().map(e -> e.getKey() + "\t" + e.getValue()).collect(Collectors.toList());
        try {
            BaseUtils.writeFile(BUY_ORDER_ID_PATH, lines);
            log.info("writeBuyOrderId {}, orderId={}", optionFutu, orderId);
        } catch (Exception e) {
            log.error("writeBuyOrderId error. option={}, orderId={}", optionFutu, orderId, e);
        }
    }

    public static Set<String> readHasSoldOrder() {
        Set<String> sets = Sets.newHashSet();
        List<String> lines = null;
        try {
            lines = BaseUtils.readFile(HAS_SOLD_ORDER_PATH);
        } catch (Exception e) {
            log.error("readHasSoldOrder error", e);
        }
        if (CollectionUtils.isNotEmpty(lines)) {
            for (String line : lines) {
                sets.add(line);
            }
        }
        return sets;
    }

    public static void writeHasSoldOrder(String stock) {
        List<String> list = Lists.newArrayList(readHasSoldOrder());
        list.add(stock);
        try {
            BaseUtils.writeFile(HAS_SOLD_ORDER_PATH, list);
            log.info("writeHasSoldOrder {}", stock);
        } catch (Exception e) {
            log.error("writeHasSoldOrder error. stock={}, list={}", stock, list, e);
        }
    }

    public static Set<String> readHasSoldSuccess() {
        Set<String> sets = Sets.newHashSet();
        List<String> lines = null;
        try {
            lines = BaseUtils.readFile(HAS_SOLD_SUCCESS_PATH);
        } catch (Exception e) {
            log.error("readHasSoldSuccess error", e);
        }
        if (CollectionUtils.isNotEmpty(lines)) {
            for (String line : lines) {
                sets.add(line);
            }
        }
        return sets;
    }

    public static void writeHasSoldSuccess(String stock) {
        List<String> list = Lists.newArrayList(readHasSoldSuccess());
        list.add(stock);
        try {
            BaseUtils.writeFile(HAS_SOLD_SUCCESS_PATH, list);
            log.info("writeHasSoldSuccess {}", stock);
        } catch (Exception e) {
            log.error("writeHasSoldSuccess error. stock={}, list={}", stock, list, e);
        }
    }

    public static Map<String, Long> readSellOrderId() {
        Map<String, Long> map = Maps.newHashMap();
        List<String> lines = null;
        try {
            lines = BaseUtils.readFile(SELL_ORDER_ID_PATH);
        } catch (Exception e) {
            log.error("readSellOrderId error", e);
        }
        if (CollectionUtils.isNotEmpty(lines)) {
            for (String line : lines) {
                String[] split = line.split("\t");
                map.put(split[0], Long.valueOf(split[1]));
            }
        }
        return map;
    }

    public static void writeSellOrderId(String optionFutu, Long orderId) {
        Map<String, Long> map = readSellOrderId();
        map.put(optionFutu, orderId);
        List<String> lines = map.entrySet().stream().map(e -> e.getKey() + "\t" + e.getValue()).collect(Collectors.toList());
        try {
            BaseUtils.writeFile(SELL_ORDER_ID_PATH, lines);
            log.info("writeSellOrderId {}, orderId={}", optionFutu, orderId);
        } catch (Exception e) {
            log.error("writeSellOrderId error. option={}, orderId={}", optionFutu, orderId, e);
        }
    }

    public static Map<String, Long> readBuyOrderTime() {
        Map<String, Long> map = Maps.newHashMap();
        List<String> lines = null;
        try {
            lines = BaseUtils.readFile(BUY_ORDER_TIME_PATH);
        } catch (Exception e) {
            log.error("readBuyOrderTime error", e);
        }
        if (CollectionUtils.isNotEmpty(lines)) {
            for (String line : lines) {
                String[] split = line.split("\t");
                map.put(split[0], Long.valueOf(split[1]));
            }
        }
        return map;
    }

    public static void writeBuyOrderTime(String stock, Long timestamp) {
        Map<String, Long> map = readBuyOrderTime();
        map.put(stock, timestamp);
        List<String> lines = map.entrySet().stream().map(e -> e.getKey() + "\t" + e.getValue()).collect(Collectors.toList());
        try {
            BaseUtils.writeFile(BUY_ORDER_TIME_PATH, lines);
        } catch (Exception e) {
            log.error("writeBuyOrderTime error. option={}, orderId={}", stock, timestamp, e);
        }
    }

    public static Map<String, Long> readSellOrderTime() {
        Map<String, Long> map = Maps.newHashMap();
        List<String> lines = null;
        try {
            lines = BaseUtils.readFile(SELL_ORDER_TIME_PATH);
        } catch (Exception e) {
            log.error("readSellOrderTime error", e);
        }
        if (CollectionUtils.isNotEmpty(lines)) {
            for (String line : lines) {
                String[] split = line.split("\t");
                map.put(split[0], Long.valueOf(split[1]));
            }
        }
        return map;

    }

    public static void writeSellOrderTime(String stock, Long timestamp) {
        Map<String, Long> map = readSellOrderTime();
        map.put(stock, timestamp);
        List<String> lines = map.entrySet().stream().map(e -> e.getKey() + "\t" + e.getValue()).collect(Collectors.toList());
        try {
            BaseUtils.writeFile(SELL_ORDER_TIME_PATH, lines);
        } catch (Exception e) {
            log.error("writeSellOrderTime error. option={}, orderId={}", stock, timestamp, e);
        }
    }

    public static Map<String, Double> readOrderCount() {
        Map<String, Double> map = Maps.newHashMap();
        List<String> lines = null;
        try {
            lines = BaseUtils.readFile(ORDER_COUNT_PATH);
        } catch (Exception e) {
            log.error("readOrderCount error", e);
        }
        if (CollectionUtils.isNotEmpty(lines)) {
            for (String line : lines) {
                String[] split = line.split("\t");
                map.put(split[0], Double.valueOf(split[1]));
            }
        }
        return map;
    }

    public static void writeOrderCount(String stock, Double count) {
        Map<String, Double> map = readOrderCount();
        map.put(stock, count);
        List<String> lines = map.entrySet().stream().map(e -> e.getKey() + "\t" + e.getValue()).collect(Collectors.toList());
        try {
            BaseUtils.writeFile(ORDER_COUNT_PATH, lines);
            log.info("writeOrderCount {}, count={}", stock, count);
        } catch (Exception e) {
            log.error("writeOrderCount error. stock={}, count={}", stock, count, e);
        }
    }
}
