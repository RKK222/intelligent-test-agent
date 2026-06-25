
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TestOpencodeConnection {
    public static void main(String[] args) {
        String baseUrl = "http://127.0.0.1:4096";
        System.out.println("Testing connection to: " + baseUrl);
        
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        
        try {
            // Test 1: Simple GET to baseUrl (like health check)
            System.out.println("\n--- Test 1: Simple GET ---");
            HttpRequest request1 = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse&lt;Void&gt; response1 = httpClient.send(request1, HttpResponse.BodyHandlers.discarding());
            System.out.println("Status: " + response1.statusCode());
            
            // Test 2: Test with /api/agent path (like the actual API call)
            System.out.println("\n--- Test 2: GET /api/agent ---");
            HttpRequest request2 = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agent"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse&lt;String&gt; response2 = httpClient.send(request2, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response2.statusCode());
            System.out.println("Response: " + response2.body().substring(0, Math.min(200, response2.body().length())) + "...");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
