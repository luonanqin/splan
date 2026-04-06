package bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对应表 {@code boll}：按 code + date + type 唯一。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBoll {

    private Integer id;
    private String code;
    private String date;
    /** day / week / month / quarter */
    private String type;
    private double md;
    private double mb;
    private double up;
    private double dn;
}
