package util;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Created by Luonanqin on 1/28/19.
 */
public class LoadData {

    public final static Map<String, List<StockKLine>> kLineMap = Maps.newHashMap();
    //	public final static Map<String, Map<String, FinanceData>> financeMap = Maps.newHashMap();
    //	public final static Map<String, ExtInfo> extInfoMap = Maps.newHashMap();

    private static List<String> hsList = Stock.getHsList();
    private static List<String> ssList = Stock.getSsList();

    public static void init() {
        loadStockKline();
        //		loadFinanceData();
        //		loadBaseExt();
    }

    private static void loadStockKline() {
        //				hsList = Lists.newArrayList("600000");
        loadBaseData(Constants.HS_BASE_PATH, hsList);
        loadBaseData(Constants.SS_BASE_PATH, ssList);
        System.out.println("load stock kline finish!");
    }

    //	private static void loadFinanceData() {
    //		//		hsList = Lists.newArrayList("600000");
    //		loadFinanceData(Constants.HS_FIN_PATH, hsList);
    //		loadFinanceData(Constants.SS_FIN_PATH, ssList);
    //		System.out.println("loadFinanceData finish!");
    //	}

    //	private static void loadBaseExt() {
    //		//		hsList = Lists.newArrayList("600000");
    //		loadBaseExtData(Constants.HS_BASE_EXT_PATH, hsList);
    //		loadBaseExtData(Constants.SS_BASE_EXT_PATH, ssList);
    //		System.out.println("loadBaseExt finish!");
    //	}

    //	private static void loadBaseExtData(String rootPath, List<String> codeList) {
    //		for (String code : codeList) {
    //			String path = rootPath + code + ".csv";
    //
    //			BufferedReader br = null;
    //			try {
    //				br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
    //				String capitalStock = null; // 总股本
    //				String str;
    //				while ((str = br.readLine()) != null) {
    //					String[] split = str.split("\t");
    //					if (str.startsWith("总股本")) {
    //						capitalStock = split[1];
    //					}
    //
    //					long capitalStockNum = (long) (Float.parseFloat(capitalStock) * 100000000);
    //
    //					ExtInfo extInfo = new ExtInfo();
    //					extInfo.setCode(code);
    //					extInfo.setCapitalStock(capitalStockNum);
    //					extInfoMap.put(code, extInfo);
    //				}
    //			} catch (Exception e) {
    //				e.printStackTrace();
    //			} finally {
    //				try {
    //					br.close();
    //				} catch (IOException e) {
    //					e.printStackTrace();
    //				}
    //			}
    //		}
    //	}

    //	private static void loadFinanceData(String rootPath, List<String> codeList) {
    //		for (String code : codeList) {
    //			String path = rootPath + code + ".csv";
    //
    //			BufferedReader br = null;
    //			try {
    //				br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
    //				String[] dateArray = null; // 报告日期
    //				String[] perStockProfitArray = null; // 基本每股收益(元)
    //				String[] perStockAssetArray = null; // 每股净资产(元)
    //				String[] mainIncomeArray = null; // 主营业务收入(万元)
    //				String[] mainProfitArray = null; // 主营业务利润(万元)
    //				String[] businessProfitArray = null;// 营业利润(万元)
    //				String[] investProfitArray = null; // 投资收益(万元)
    //				String[] totalProfitArray = null;// 利润总额(万元)
    //				String[] retainedProfitArray = null; // 净利润(万元)
    //				String[] retainedProfit2Array = null; // 净利润(扣除非经常性损益后)(万元)
    //				String[] totalAssetsArray = null; // 总资产(万元)
    //				String[] circulatedAssetsArray = null; // 流动资产(万元)
    //				String[] totalDebtArray = null; // 总负债(万元)
    //				String[] circulatedDebtArray = null; // 流动负债(万元)
    //				String str;
    //				int length = 0;
    //				while ((str = br.readLine()) != null) {
    //					String[] split = str.split(",");
    //					String title = split[0];
    //					length = split.length;
    //
    //					if (StringUtils.equals(title, "报告日期")) {
    //						dateArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "基本每股收益(元)")) {
    //						perStockProfitArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "每股净资产(元)")) {
    //						perStockAssetArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "主营业务收入(万元)")) {
    //						mainIncomeArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "主营业务利润(万元)")) {
    //						mainProfitArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "营业利润(万元)")) {
    //						businessProfitArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "投资收益(万元)")) {
    //						investProfitArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "利润总额(万元)")) {
    //						totalProfitArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "净利润(万元)")) {
    //						retainedProfitArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "净利润(扣除非经常性损益后)(万元)")) {
    //						retainedProfit2Array = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "总资产(万元)")) {
    //						totalAssetsArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "流动资产(万元)")) {
    //						circulatedAssetsArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "总负债(万元)")) {
    //						totalDebtArray = ArrayUtils.subarray(split, 1, length);
    //					} else if (StringUtils.equals(title, "流动负债(万元)")) {
    //						circulatedDebtArray = ArrayUtils.subarray(split, 1, length);
    //					}
    //				}
    //
    //				Map<String, FinanceData> finances = new HashMap();
    //				for (int i = 0; i < length - 1; i++) {
    //					String date = dateArray[i];
    //					String perStockProfit = perStockProfitArray[i];
    //					String perStockAsset = perStockAssetArray[i];
    //					String mainIncome = mainIncomeArray[i];
    //					String mainProfit = mainProfitArray[i];
    //					String businessProfit = businessProfitArray[i];
    //					String investProfit = investProfitArray[i];
    //					String totalProfit = totalProfitArray[i];
    //					String retainedProfit = retainedProfitArray[i];
    //					String retainedProfit2 = retainedProfit2Array[i];
    //					String totalAssets = totalAssetsArray[i];
    //					String circulatedAssets = circulatedAssetsArray[i];
    //					String totalDebt = totalDebtArray[i];
    //					String circulatedDebt = circulatedDebtArray[i];
    //
    //					if (StringUtils.equals("--", perStockProfit)) {
    //						perStockProfit = "-99999";
    //					}
    //					if (StringUtils.equals("--", perStockAsset)) {
    //						perStockAsset = "-99999";
    //					}
    //					if (StringUtils.equals("--", mainIncome)) {
    //						mainIncome = "-99999";
    //					}
    //					if (StringUtils.equals("--", mainProfit)) {
    //						mainProfit = "-99999";
    //					}
    //					if (StringUtils.equals("--", businessProfit)) {
    //						businessProfit = "-99999";
    //					}
    //					if (StringUtils.equals("--", investProfit)) {
    //						investProfit = "-99999";
    //					}
    //					if (StringUtils.equals("--", totalProfit)) {
    //						totalProfit = "-99999";
    //					}
    //					if (StringUtils.equals("--", retainedProfit)) {
    //						retainedProfit = "-99999";
    //					}
    //					if (StringUtils.equals("--", retainedProfit2)) {
    //						retainedProfit2 = "-99999";
    //					}
    //					if (StringUtils.equals("--", totalAssets)) {
    //						totalAssets = "-99999";
    //					}
    //					if (StringUtils.equals("--", circulatedAssets)) {
    //						circulatedAssets = "-99999";
    //					}
    //					if (StringUtils.equals("--", totalDebt)) {
    //						totalDebt = "-99999";
    //					}
    //					if (StringUtils.equals("--", circulatedDebt)) {
    //						circulatedDebt = "-99999";
    //					}
    //
    //					FinanceData financeData = new FinanceData();
    //					financeData.setCode(code);
    //					financeData.setDate(date);
    //					financeData.setPerStockProfit(Float.valueOf(perStockProfit));
    //					financeData.setPerStockAsset(Float.valueOf(perStockAsset));
    //					financeData.setMainIncome(Float.valueOf(mainIncome));
    //					financeData.setMainProfit(Float.valueOf(mainProfit));
    //					financeData.setBusinessProfit(Float.valueOf(businessProfit));
    //					financeData.setInvestProfit(Float.valueOf(investProfit));
    //					financeData.setTotalProfit(Float.valueOf(totalProfit));
    //					financeData.setRetainedProfit(Float.valueOf(retainedProfit));
    //					financeData.setRetainedProfit2(Float.valueOf(retainedProfit2));
    //					financeData.setTotalAssets(Long.valueOf(totalAssets));
    //					financeData.setCirculatedAssets(Long.valueOf(circulatedAssets));
    //					financeData.setTotalDebt(Long.valueOf(totalDebt));
    //					financeData.setCirculatedDebt(Long.valueOf(circulatedDebt));
    //
    //					finances.put(date, financeData);
    //				}
    //				financeMap.put(code, finances);
    //			} catch (Exception e) {
    //				e.printStackTrace();
    //			} finally {
    //				try {
    //					br.close();
    //				} catch (IOException e) {
    //					e.printStackTrace();
    //				}
    //			}
    //		}
    //	}

    // 1742486400,2025-03-21,11.49,11.42,137638897,11.52,11.39,1576151833.00,-0.07,-0.61,0.71,11.49,11.48,200978064,11.63,162338197,11.60,125881004
    private static void loadBaseData(String rootPath, List<String> codeList) {
        for (String code : codeList) {
            String path = rootPath + code + ".csv";
            BufferedReader br = null;
            //			List<TradeLog> tradeLogs = new ArrayList<TradeLog>();
            List<StockKLine> stockKLines = Lists.newArrayList();

            if (!BaseUtils.fileExist(path)) {
                continue;
            }

            try {
                br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
                String str;
                while ((str = br.readLine()) != null) {
                    String[] split = str.split(",");
                    String date = split[1];
                    double open = Double.parseDouble(split[2]);
                    double close = Double.parseDouble(split[3]);
                    BigDecimal volumnCount = BigDecimal.valueOf(Long.valueOf(split[4]));
                    double highest = Double.parseDouble(split[5]);
                    double lowest = Double.parseDouble(split[6]);
                    String volumnMoney = split[7];
                    //					String rise = split[9];
                    //					String turnoverRate = split[10];
                    //					String market = split[13];
                    //					String circulatedMarket = split[14];

                    StockKLine stockKLine = StockKLine.builder().code(code).date(date).open(open).close(close).high(highest).low(lowest).volume(volumnCount).build();

                    // 上市首日
                    //					if (close.equals("0.0")) {
                    //						continue;
                    //					}

                    // 停牌
                    //					if (StringUtils.equalsIgnoreCase("none", rise)) {
                    //						continue;
                    //					}

                    //					volumnMoney = volumnMoney.substring(0, volumnMoney.indexOf("."));
                    //					if (market.indexOf("e") != -1) {
                    //						BigDecimal _market = new BigDecimal(market);
                    //						market = _market.toPlainString();
                    //					} else if (market.indexOf(".") != -1) {
                    //						market = market.substring(0, market.indexOf("."));
                    //					}

                    //					if (circulatedMarket.indexOf("e") != -1) {
                    //						BigDecimal _cirMarket = new BigDecimal(circulatedMarket);
                    //						circulatedMarket = _cirMarket.toPlainString();
                    //					} else if (circulatedMarket.indexOf(".") != -1) {
                    //						circulatedMarket = circulatedMarket.substring(0, circulatedMarket.indexOf("."));
                    //					}

                    //					TradeLog tradeLog = new TradeLog();
                    //					tradeLog.setCode(code);
                    //					tradeLog.setDate(date);
                    //					tradeLog.setOpen(Float.parseFloat(open));
                    //					tradeLog.setClose(Float.parseFloat(close));
                    //					tradeLog.setRise(Float.parseFloat(rise));
                    //					tradeLog.setHighest(Float.parseFloat(highest));
                    //					tradeLog.setLowest(Float.parseFloat(lowest));
                    //					tradeLog.setMarketVal(Long.parseLong(market));
                    //					tradeLog.setCirculatedMarketVal(Long.parseLong(circulatedMarket));
                    //					tradeLog.setPrevClose(Float.parseFloat(prevClose));
                    //					tradeLog.setTurnoverRate(Float.parseFloat(turnoverRate));
                    //					tradeLog.setVolumnCount(Long.parseLong(volumnCount));
                    //					tradeLog.setVolumnMoney(Long.parseLong(volumnMoney));
                    //					if (StringUtils.equals(highest, lowest)) {
                    //						tradeLog.setAllHighest(true);
                    //					}
                    //
                    //					 记录前一天的日期
                    //					if (!tradeLogs.isEmpty()) {
                    //						TradeLog lastLog = tradeLogs.get(tradeLogs.size() - 1);
                    //						tradeLog.setPrevDate(lastLog.getDate());
                    //					}
                    //
                    //					tradeLogs.add(tradeLog);
                    stockKLines.add(stockKLine);
                }

                kLineMap.put(code, stockKLines);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        int a = 0;
    }
}
