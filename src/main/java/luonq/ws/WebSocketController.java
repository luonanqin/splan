package luonq.ws;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebSocketController {

    @GetMapping("/ws-page")
    public String wsPage() {
        return "ws"; // 对应templates/ws.html（不带扩展名）
    }

    @GetMapping("")
    public String buy(){

        return "";
    }
}
