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
    private double md; // 20日标准差
    private double mb; // 中轨线
    private double up; // 上轨线
    private double dn; // 下轨线

    @Override
    public String toString() {
        return String.format("%s,%s,%s,%s,%s", date, String.valueOf(md), String.valueOf(mb), String.valueOf(up), String.valueOf(dn));
    }
}
