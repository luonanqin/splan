package luonq.a;

import com.futu.openapi.FTAPI;
import com.futu.openapi.pb.QotCommon;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import luonq.futu.BasicQuote;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class FilterCalculator {

    private BasicQuote quote;

    public void init() {
        FTAPI.init();
        quote = new BasicQuote();
        quote.start();
    }

    public void cal() {
        //        List<String> filter2 = Lists.newArrayList("000001", "600000");
        List<String> filter2 = Filter2.cal();
        List<String> filter3 = Filter3.cal();
        List<String> filter4 = Filter4.cal();
        List<String> filter5 = Filter5.cal();
        List<String> filter6 = Filter6.cal();
        List<String> filter7 = Filter7.cal();
        List<String> filter9 = Filter9.cal();

        try {
            updateGroup(filter2, "Filter2");
            Thread.sleep(3000);
            updateGroup(filter3, "Filter3");
            Thread.sleep(3000);
            updateGroup(filter4, "Filter4");
            Thread.sleep(3000);
            updateGroup(filter5, "Filter5");
            Thread.sleep(3000);
            updateGroup(filter6, "Filter6");
            Thread.sleep(3000);
            updateGroup(filter7, "Filter7");
            Thread.sleep(3000);
            updateGroup(filter9, "Filter9");
        } catch (InterruptedException e) {
            log.error("Filter InterruptedException", e);
        }
    }

    public void updateGroup(List<String> codeList, String group) {
        Map<String, Integer> hisCodeMarket = quote.getUserSecurity(group);
        quote.moveOutUserSecurity(hisCodeMarket, group);

        if (CollectionUtils.isEmpty(codeList)) {
            log.info("{} is empty", group);
            return;
        }

        Map<String, Integer> codeMarketMap = Maps.newHashMap();
        for (String code : codeList) {
            if (StringUtils.startsWith(code, "0")) {
                codeMarketMap.put(code, QotCommon.QotMarket.QotMarket_CNSZ_Security_VALUE);
            } else {
                codeMarketMap.put(code, QotCommon.QotMarket.QotMarket_CNSH_Security_VALUE);
            }
        }
        quote.addUserSecurity(codeMarketMap, group);
    }
}
