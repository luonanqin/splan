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
import util.BaseUtils;
import util.Constants;
import util.LoadData;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static util.Constants.INVALID_CODE_PATH;

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
        List<String> filter16 = new Filter24().cal();
        List<String> filter17 = new Filter17().cal();
        List<String> filter18 = new Filter18().cal();
        List<String> filter19 = new Filter19().cal();
        List<String> filter20 = new Filter20().cal();
        List<String> filter21 = new Filter21().cal();
        List<String> filter22 = new Filter22().cal();
        List<String> filter23 = new Filter23().cal();

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
            //            Thread.sleep(4000);
            //            updateGroup(filter10, "F10");
            Thread.sleep(4000);
            updateGroup(filter11, "F11");
            Thread.sleep(4000);
            updateGroup(filter12, "F12");
            //            Thread.sleep(4000);
            //            updateGroup(filter13, "F13");
            //            Thread.sleep(4000);
            //            updateGroup(filter14, "F14");
            Thread.sleep(4000);
            updateGroup(filter15, "F15");
            Thread.sleep(4000);
            updateGroup(filter16, "F16");
            Thread.sleep(4000);
            updateGroup(filter17, "F17");
            Thread.sleep(4000);
            updateGroup(filter18, "F18");
            //            Thread.sleep(4000);
            //            updateGroup(filter19, "F19");
            Thread.sleep(4000);
            updateGroup(filter20, "F20");
            Thread.sleep(4000);
            updateGroup(filter21, "F21");
            Thread.sleep(4000);
            updateGroup(filter22, "F22");
            Thread.sleep(4000);
            updateGroup(filter23, "F23");

            Map<String, List<String>> filterMap = Maps.newTreeMap((o1, o2) -> {
                Integer o1No = Integer.valueOf(o1.substring(6));
                Integer o2No = Integer.valueOf(o2.substring(6));
                return o1No - o2No;
            });
            filterMap.put("Filter2", filter2);
            filterMap.put("Filter4", filter4);
            filterMap.put("Filter5", filter5);
            filterMap.put("Filter6", filter6);
            filterMap.put("Filter9", filter9);
            filterMap.put("Filter11", filter11);
            filterMap.put("Filter12", filter12);
            filterMap.put("Filter15", filter15);
            filterMap.put("Filter16", filter16);
            filterMap.put("Filter17", filter17);
            filterMap.put("Filter18", filter18);
            filterMap.put("Filter20", filter20);
            filterMap.put("Filter21", filter21);
            filterMap.put("Filter22", filter22);
            filterMap.put("Filter23", filter23);
            sendEmail(filterMap);
        } catch (InterruptedException e) {
            log.error("Filter InterruptedException", e);
        }
    }

    public void getInvalidCode() {
        Map<String, Integer> invalid1 = quote.getUserSecurity("已过滤");
//        Map<String, Integer> invalid2 = quote.getUserSecurity("高位出货");
        invalidCodes.addAll(invalid1.keySet());
//        invalidCodes.addAll(invalid2.keySet());
        invalidCodes.addAll(invalidCodeList());
    }

    public List<String> invalidCodeList() {
        List<String> codeList = Lists.newArrayList();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(INVALID_CODE_PATH)));
            String str;
            while (StringUtils.isNotBlank(str = br.readLine())) {
                codeList.add(StringUtils.split(str, "\t")[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return codeList;
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

    // 手动调用清理
    public void clear() {
        Map<String, Integer> clearCodeMap = Maps.newHashMap();
        clearCodeMap.put("000001", QotCommon.QotMarket.QotMarket_CNSZ_Security_VALUE);
        String clear = "清理";
        // 排除分组
        List<String> exceptGroup = Lists.newArrayList("全部", "加密货币", "沪深", "港股", "债券", "指数", "新加坡", "美股", "期货", "期权", "日股", "马来西亚", "澳大利亚", "加拿大", "美股期权", "美指", "港股", clear);

        // 获取所有待清理code
        Map<String, Integer> clearMap = quote.getUserSecurity(clear);
        List<String> groupList = quote.getUserSecurityGroup();
        groupList.removeAll(exceptGroup);

        // 获取所有除清理分组以外的code
        Map<String, Integer> codeMap = Maps.newHashMap();
        groupList.forEach(group -> {
            Map<String, Integer> userSecurity = quote.getUserSecurity(group);
            codeMap.putAll(userSecurity);
            System.out.println(group + " 分组股票数量=" + userSecurity.size());
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        // 如果code在清理分组但是不在其他分组，则清理
        for (String code : clearMap.keySet()) {
            if (!codeMap.containsKey(code)) {
                clearCodeMap.put(code, clearMap.get(code));
            }
        }
        System.out.println();
    }

    public void sendEmail(Map<String, List<String>> codeListMap) {
        StringBuffer sb = new StringBuffer();
        int sum = 0;
        for (String filterName : codeListMap.keySet()) {
            List<String> codeList = codeListMap.get(filterName);
            if (codeList.size() == 0) {
                continue;
            }

            sb.append(filterName)
              .append(":\n")
              .append(codeListMap.get(filterName))
              .append("\n")
              .append(" ")
              .append("\n");
            sum += codeList.size();
        }
        String message = sb.toString();

        LocalDate now = LocalDate.now();
        if (now.getDayOfWeek().getValue() > 5) {
            return;
        }
        String today = now.format(Constants.DB_DATE_FORMATTER);
        if (sum == 0) {
            BaseUtils.sendEmail(today + "选股结果(无)", "");
        } else {
            BaseUtils.sendEmail(today + "选股结果(" + sum + ")", message);
        }
    }

    public static void main(String[] args) {
        FilterCalculator filterCalculator = new FilterCalculator();
        filterCalculator.init();
        //        filterCalculator.updateGroup(Lists.newArrayList("000001"), "Filter4");
        filterCalculator.invalidCodeList();
        //        filterCalculator.clear();
        //        Map<String, Integer> userSecurity = filterCalculator.quote.getUserSecurity("沪深");
        //        filterCalculator.cal();
        //        List<String> userSecurityGroup = filterCalculator.quote.getUserSecurityGroup();

        System.out.println();
    }
}
