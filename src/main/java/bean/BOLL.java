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
public class BOLL {

    private String date;
    private String code;
    private double md; // 20日标准差
    private double mb; // 中轨线
    private double up; // 上轨线
    private double dn; // 下轨线

    @Override
    public String toString() {
        return String.format("%s,%s,%s,%s,%s", date, String.valueOf(md), String.valueOf(mb), String.valueOf(up), String.valueOf(dn));
    }

    public static BOLL convert(String line) {
        String[] split = line.split(",");
        String date = split[0];
        String md = split[1];
        String mb = split[2];
        String up = split[3];
        String dn = split[4];
        return BOLL.builder().date(date).md(Double.valueOf(md)).mb(Double.valueOf(mb)).up(Double.valueOf(up)).dn(Double.valueOf(dn)).build();
    }
}
