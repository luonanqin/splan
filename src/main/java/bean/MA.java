package bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by Luonanqin on 2023/3/2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MA {

    private String date;
    private double ma5;
    private double ma10;
    private double ma20;
    private double ma30;
    private double ma60;

    @Override
    public String toString() {
        return String.format("%s,%s,%s,%s,%s,%s", date, String.valueOf(ma5), String.valueOf(ma10), String.valueOf(ma20), String.valueOf(ma30), String.valueOf(ma60));
    }
}
