package barchart;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import util.BaseUtils;

import java.util.List;
import java.util.Map;

import static barchart.FixGrabStockDateHistory.waitFixGrab;
import static util.Constants.BASE_PATH;
import static util.Constants.GRAB_PATH;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class MergeFixGrabStockDateHistory {

    public static void main(String[] args) throws Exception {
        // 等待抓取修复的
        Map<String, String> waitFixGrabMap = waitFixGrab();

        for (String stock : waitFixGrabMap.keySet()) {
            String grabFile = GRAB_PATH + stock + "_day";
            String range = waitFixGrabMap.get(stock);
            String[] split = range.split(", ");

            String hasFixFileDir = BASE_PATH + "fixGrab/" + stock + "/";

            Map<String, List<String>> yearToList = Maps.newHashMap();
            for (String rangeStr : split) {
                String[] rangeDate = rangeStr.split("~");

                String beginDate = rangeDate[0];
                String endDate = rangeDate[1];
                String year = beginDate.substring(6);

                String hasFixFile = beginDate.replaceAll("/", "") + "_" + endDate.replaceAll("/", "") + "_day";
                List<String> dayList = BaseUtils.readFile(hasFixFileDir + hasFixFile);
                if (CollectionUtils.isEmpty(dayList)) {
                    continue;
                }
                yearToList.put(year, dayList);
            }

            List<String> lineList = BaseUtils.readFile(grabFile);
            Map<String, List<String>> grabMap = Maps.newHashMap();
            for (String line : lineList) {
                String[] split1 = line.split(",");
                String year = split1[0].substring(6);
                if (!grabMap.containsKey(year)) {
                    grabMap.put(year, Lists.newArrayList());
                }
                grabMap.get(year).add(line);
            }

            List<String> merge = Lists.newArrayList();
            int year = 1999;
            String yearStr = String.valueOf(year);
            while (grabMap.containsKey(yearStr) || yearToList.containsKey(yearStr)) {
                if (yearToList.containsKey(yearStr)) {
                    merge.addAll(yearToList.get(yearStr));
                } else {
                    merge.addAll(grabMap.get(yearStr));
                }
                year--;
                yearStr = String.valueOf(year);
            }

            System.out.println(stock + " " + merge.size());

            String grabFix = BASE_PATH + "grabFix/" + stock + "_day";
            BaseUtils.writeFile(grabFix, merge);
        }

    }
}
