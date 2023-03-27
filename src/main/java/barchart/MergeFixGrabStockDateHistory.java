package barchart;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.collections4.CollectionUtils;
import util.BaseUtils;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static util.Constants.GRAB_PATH;
import static util.Constants.HIS_BASE_PATH;
import static util.Constants.STD_DAILY_PATH;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class MergeFixGrabStockDateHistory {

    public static void main(String[] args) throws Exception {
        // 已经合并没问题的
        Set<String> hasMergeStock = BaseUtils.getFileMap(STD_DAILY_PATH).keySet().stream().map(f -> f.toUpperCase()).collect(Collectors.toSet());
        File f = new File(HIS_BASE_PATH + "fixGrab/");
        String[] stockList = f.list();

        for (String stock : stockList) {
            if (!StringUtils.equals(stock, "BKR")) {
//                continue;
            }
            if (hasMergeStock.contains(stock)) {
                continue;
            }
            String grabFile = GRAB_PATH + stock + "_day";

            String hasFixFileDir = HIS_BASE_PATH + "fixGrab/" + stock + "/";
            Map<String, List<String>> yearToList = Maps.newHashMap();
            File fixDir = new File(hasFixFileDir);
            if (fixDir.listFiles() == null) {
                continue;
            }
            for (File file : fixDir.listFiles()) {
                String name = file.getName();
                String year = name.substring(4, 8);
                List<String> dayList = BaseUtils.readFile(file.getAbsolutePath());
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

            String grabFix = HIS_BASE_PATH + "mergeGrab/" + stock + "_day";
            BaseUtils.writeFile(grabFix, merge);
        }

    }
}
