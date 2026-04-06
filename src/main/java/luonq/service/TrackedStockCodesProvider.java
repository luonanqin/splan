package luonq.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 从 {@code classpath:option/weekOption} 读取跟踪标的列表；供日 K 同步、MA/BOLL 等共用，避免重复解析。
 */
@Component
@Slf4j
public class TrackedStockCodesProvider {

    /**
     * weekOption 每行一个 code，空行与首尾空白忽略。
     */
    public List<String> loadAll() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("option/weekOption");
        if (in == null) {
            log.warn("classpath option/weekOption not found");
            return Collections.emptyList();
        }
        List<String> codes = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String c = line.trim();
                if (!c.isEmpty()) {
                    codes.add(c);
                }
            }
        } catch (Exception e) {
            log.error("read weekOption failed", e);
            return Collections.emptyList();
        }
        return codes;
    }
}
