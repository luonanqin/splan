package bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对应表 {@code ma}：按 code + date + type 唯一。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMa {

    private Integer id;
    private String code;
    private String date;
    /** day / week / month */
    private String type;
    private double ma5;
    private double ma10;
    private double ma20;
    private double ma30;
    private double ma60;
}
