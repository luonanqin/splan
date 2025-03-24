package util;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Luonanqin on 1/28/19.
 */
public class Stock {

	private final static String SS_PATH = "/Users/Luonanqin/study/intellij_idea_workspaces/study/stock/src/main/resources/ssList.txt";
	private final static String HS_PATH = "/Users/Luonanqin/study/intellij_idea_workspaces/study/stock/src/main/resources/hsList.txt";

	private final static List<String> hs = new ArrayList<String>();
	private final static List<String> ss = new ArrayList<String>();

	public static List<String> getHsList(){
		if (CollectionUtils.isNotEmpty(hs)) {
		    return hs;
		}

		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(HS_PATH)));
		String str;
		while (StringUtils.isNotBlank(str = br.readLine())) {
			hs.add(str);
		}
		} catch (Exception e) {
			e.printStackTrace();
		}finally {
			try {
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return hs;
	}

	public static List<String> getSsList() {
		if (CollectionUtils.isNotEmpty(ss)) {
			return ss;
		}

		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(SS_PATH)));
			String str;
			while (StringUtils.isNotBlank(str = br.readLine())) {
				ss.add(str);
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

		return ss;
	}
}
