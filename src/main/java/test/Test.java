package test;

import util.BaseUtils;

import java.util.Map;

/**
 * Created by Luonanqin on 2023/2/9.
 */
public class Test {

    public static void main(String[] args) throws Exception{
        String market = "XNAS";
        Map<String, String> openMap = BaseUtils.getOpenData(market);

    }
}
