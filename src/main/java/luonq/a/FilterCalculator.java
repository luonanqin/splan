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
        List<String> filter2 = Filter2.cal();
        List<String> filter3 = Filter3.cal();
        List<String> filter4 = Filter4.cal();
        List<String> filter5 = Filter5.cal();
        List<String> filter6 = Filter6.cal();
        List<String> filter7 = Filter7.cal();
        List<String> filter9 = Filter9.cal();
        List<String> filter10 = Filter10.cal();
        List<String> filter11 = Filter11.cal();
        List<String> filter12 = Filter12.cal();
        List<String> filter13 = Filter13.cal();
        List<String> filter14 = Filter14.cal();
        List<String> filter15 = Filter15.cal();

        try {
            updateGroup(filter2, "Filter2");
            Thread.sleep(4000);
            updateGroup(filter3, "Filter3");
            Thread.sleep(4000);
            updateGroup(filter4, "Filter4");
            Thread.sleep(4000);
            updateGroup(filter5, "Filter5");
            Thread.sleep(4000);
            updateGroup(filter6, "Filter6");
            Thread.sleep(4000);
            updateGroup(filter7, "Filter7");
            Thread.sleep(4000);
            updateGroup(filter9, "Filter9");
            Thread.sleep(4000);
            updateGroup(filter10, "Filter10");
            Thread.sleep(4000);
            updateGroup(filter11, "Filter11");
            Thread.sleep(4000);
            updateGroup(filter12, "Filter12");
            Thread.sleep(4000);
            updateGroup(filter13, "Filter13");
            Thread.sleep(4000);
            updateGroup(filter14, "Filter14");
            Thread.sleep(4000);
            updateGroup(filter15, "Filter15");
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
