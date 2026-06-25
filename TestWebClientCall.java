
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class TestWebClientCall {
    public static void main(String[] args) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:4096")
                .build();
        
        System.out.println("=== 测试用 WebClient 调用 /api/agent ===");
        
        try {
            Mono&lt;String&gt; response = webClient.get()
                    .uri("/api/agent?workspaceId=wrk_fcoss_20260620")
                    .retrieve()
                    .bodyToMono(String.class);
            
            String result = response.block(Duration.ofSeconds(5));
            System.out.println("Success! Response: " + result.substring(0, Math.min(300, result.length())));
        } catch (Exception e) {
            System.err.println("Error: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
