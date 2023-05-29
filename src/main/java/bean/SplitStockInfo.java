package bean;

import lombok.Data;

/**
 * 拆股信息
 * Created by Luonanqin on 2023/5/28.
 */
@Data
public class SplitStockInfo {

    private String date;
    private String stock;
    private int from;
    private int to;
}
