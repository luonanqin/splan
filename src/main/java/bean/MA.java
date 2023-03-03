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

    public static MA convert(String line) {
        String[] split = line.split(",");
        String date = split[0];
        double ma5 = Double.parseDouble(split[1]);
        double ma10 = Double.parseDouble(split[2]);
        double ma20 = Double.parseDouble(split[3]);
        double ma30 = Double.parseDouble(split[4]);
        double ma60 = Double.parseDouble(split[5]);
        return MA.builder().date(date).ma5(ma5).ma10(ma10).ma20(ma20).ma30(ma30).ma60(ma60).build();
    }
}
