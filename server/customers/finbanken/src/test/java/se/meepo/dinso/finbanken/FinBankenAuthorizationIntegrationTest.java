package se.meepo.dinso.finbanken;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import se.meepo.dinso.service.CustomerRules;
import se.meepo.dinso.service.PortalType;

@SpringBootTest(
    classes = FinBankenApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FinBankenAuthorizationIntegrationTest {
  @Value("${local.server.port}")
  private int port;

  @Autowired private CustomerRules rules;

  @Test
  void loadsPrivateOnlyEnglishRules() {
    assertThat(rules.portals()).containsExactly(PortalType.PRIVATE);
    assertThat(rules.locales()).containsExactly("en");
    assertThat(rules.showTraditionalBonusRate()).isFalse();
  }

  @Test
  void doesNotExposeCompanyApiForAnAuthenticatedPrivateProfile() throws Exception {
    var client = HttpClient.newHttpClient();
    var token = login(client);
    var response =
        client.send(
            request("/api/company/overview")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(404);
  }

  @Test
  void acceptsPreflightRequestsOnlyFromTheFinBankenClient() throws Exception {
    var client = HttpClient.newHttpClient();
    var response =
        client.send(
            preflightRequest("http://localhost:5175"), HttpResponse.BodyHandlers.discarding());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("access-control-allow-origin"))
        .contains("http://localhost:5175");

    for (var origin : java.util.List.of("http://localhost:5173", "http://localhost:5174")) {
      assertThat(
              client
                  .send(preflightRequest(origin), HttpResponse.BodyHandlers.discarding())
                  .statusCode())
          .isEqualTo(403);
    }
  }

  @Test
  void systemAdminGetsAnEmptyPermissionListInsteadOfAnError() throws Exception {
    var client = HttpClient.newHttpClient();
    var token = login(client, "finbanken-system-admin");
    var response =
        client.send(
            request("/api/admin/profiles")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"profiles\":[]");
  }

  private String login(HttpClient client) throws Exception {
    return login(client, "finbanken-portfolio");
  }

  private String login(HttpClient client, String profileId) throws Exception {
    var response =
        client.send(
            request("/api/auth/login")
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString("{\"profileId\":\"" + profileId + "\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body().replaceAll(".*\\\"token\\\":\\\"([^\\\"]+)\\\".*", "$1");
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  private HttpRequest preflightRequest(String origin) {
    return request("/api/auth/login")
        .header("Origin", origin)
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "content-type")
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
        .build();
  }
}
