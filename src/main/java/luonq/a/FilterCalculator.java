package luonq.a;

import com.futu.openapi.FTAPI;
import com.futu.openapi.pb.QotCommon;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import luonq.futu.BasicQuote;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import util.LoadData;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class FilterCalculator {

    private BasicQuote quote;
    private Set<String> invalidCodes = Sets.newHashSet();

    public void init() {
        FTAPI.init();
        quote = new BasicQuote();
        quote.start();
        getInvalidCode();
    }

    public void cal() {
        LoadData.init();
        //        List<String> filter2 = Lists.newArrayList("000001", "600000");
        List<String> filter2 = new Filter2().cal();
        List<String> filter3 = new Filter3().cal();
        List<String> filter4 = new Filter4().cal();
        List<String> filter5 = new Filter5().cal();
        List<String> filter6 = new Filter6().cal();
        List<String> filter7 = new Filter7().cal();
        List<String> filter9 = new Filter9().cal();
        List<String> filter10 = new Filter10().cal();
        List<String> filter11 = new Filter11().cal();
        List<String> filter12 = new Filter12().cal();
        List<String> filter13 = new Filter13().cal();
        List<String> filter14 = new Filter14().cal();
        List<String> filter15 = new Filter15().cal();
        List<String> filter16 = new Filter16().cal();
        List<String> filter17 = new Filter17().cal();
        List<String> filter18 = new Filter18().cal();
        List<String> filter19 = new Filter19().cal();
        List<String> filter20 = new Filter20().cal();

        try {
            updateGroup(filter2, "F2");
            Thread.sleep(4000);
            //            updateGroup(filter3, "F3");
            //            Thread.sleep(4000);
            updateGroup(filter4, "F4");
            Thread.sleep(4000);
            updateGroup(filter5, "F5");
            Thread.sleep(4000);
            updateGroup(filter6, "F6");
            Thread.sleep(4000);
            //            updateGroup(filter7, "F7");
            //            Thread.sleep(4000);
            updateGroup(filter9, "F9");
            Thread.sleep(4000);
            updateGroup(filter10, "F10");
            Thread.sleep(4000);
            updateGroup(filter11, "F11");
            Thread.sleep(4000);
//            updateGroup(filter12, "F12");
//            Thread.sleep(4000);
//            updateGroup(filter13, "F13");
//            Thread.sleep(4000);
            updateGroup(filter14, "F14");
            Thread.sleep(4000);
            updateGroup(filter15, "F15");
            Thread.sleep(4000);
            updateGroup(filter16, "F16");
            Thread.sleep(4000);
            updateGroup(filter17, "F17");
            Thread.sleep(4000);
            updateGroup(filter18, "F18");
            Thread.sleep(4000);
            updateGroup(filter19, "F19");
            Thread.sleep(4000);
            updateGroup(filter20, "F20");
        } catch (InterruptedException e) {
            log.error("Filter InterruptedException", e);
        }
    }

    public void getInvalidCode() {
        Map<String, Integer> invalid1 = quote.getUserSecurity("已过滤");
        Map<String, Integer> invalid2 = quote.getUserSecurity("高位出货");
        invalidCodes.addAll(invalid1.keySet());
        invalidCodes.addAll(invalid2.keySet());
    }

    public void updateGroup(List<String> codeList, String group) {
        //        Map<String, Integer> hisCodeMarket = quote.getUserSecurity(group);
        //        quote.moveOutUserSecurity(hisCodeMarket, group);

        if (CollectionUtils.isEmpty(codeList)) {
            log.info("{} is empty", group);
            return;
        }

        Map<String, Integer> codeMarketMap = Maps.newHashMap();
        for (String code : codeList) {
            if (invalidCodes.contains(code)) {
                continue;
            }
            if (StringUtils.startsWith(code, "0")) {
                codeMarketMap.put(code, QotCommon.QotMarket.QotMarket_CNSZ_Security_VALUE);
            } else {
                codeMarketMap.put(code, QotCommon.QotMarket.QotMarket_CNSH_Security_VALUE);
            }
        }
        quote.addUserSecurity(codeMarketMap, group);
    }

    public static void main(String[] args) {
        FilterCalculator filterCalculator = new FilterCalculator();
        filterCalculator.init();
        filterCalculator.updateGroup(Lists.newArrayList("000001"), "Filter4");
    }
}
