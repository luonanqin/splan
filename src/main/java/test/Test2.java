package test;

import util.BaseUtils;
import util.Constants;

import java.util.Map;

/**
 * Created by Luonanqin on 2023/3/26.
 */
public class Test2 {

    public static void main(String[] args) throws Exception{
        Map<String, String> dailyMap = BaseUtils.getFileMap(Constants.STD_DAILY_PATH);
        Map<String, String> monthlyMap = BaseUtils.getFileMap(Constants.STD_MONTHLY_PATH);

        for (String stock : monthlyMap.keySet()) {
            if (!dailyMap.containsKey(stock)) {
                System.out.println(stock);
            }
        }
    }
}
