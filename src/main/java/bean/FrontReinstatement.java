package bean;

import lombok.Data;

@Data
public class FrontReinstatement {

    private String stock;
    private String date;
    private double factor; // 前复权因子
    private int factorType; // 公司行动类型
}
