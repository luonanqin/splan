package util;

import com.google.common.collect.Sets;
import org.apache.commons.collections4.SetUtils;

import java.util.Collection;
import java.util.Set;

/**
 * Created by Luonanqin on 2023/3/14.
 */
public class StrategyUtils {

    public static Set<String> computerIntersection(Collection... coll) {
        Set<String> result = Sets.newHashSet();

        for (int i = 0; i < coll.length; i++) {
            if (i == 0) {
                result = Sets.newHashSet(coll[i]);
            } else {
                result = SetUtils.intersection(result, Sets.newHashSet(coll[i]));
            }
        }
        return result;
    }
}
