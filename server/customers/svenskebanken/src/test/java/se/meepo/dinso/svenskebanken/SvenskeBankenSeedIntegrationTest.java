package se.meepo.dinso.svenskebanken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import se.meepo.dinso.database.CompanyPortalDataService;
import se.meepo.dinso.database.DemoSessionService;
import se.meepo.dinso.database.PrivatePortalDataService;
import se.meepo.dinso.database.repository.DemoProfileRepository;
import se.meepo.dinso.service.CompanyMutation;
import se.meepo.dinso.service.CustomerId;
import se.meepo.dinso.service.CustomerRules;

@SpringBootTest(
    classes = SvenskeBankenApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SvenskeBankenSeedIntegrationTest {
  @Autowired private DemoProfileRepository profiles;
  @Autowired private DemoSessionService sessions;
  @Autowired private PrivatePortalDataService privateData;
  @Autowired private CompanyPortalDataService companyData;
  @Autowired private CustomerRules customerRules;

  @Value("${local.server.port}")
  private int port;

  @Test
  void seedsOnlySvenskeBankenProfiles() {
    assertThat(profiles.findByCustomerId(CustomerId.SVENSKEBANKEN)).hasSize(6);
    assertThat(profiles.findByCustomerId(CustomerId.SVENSKEBANKEN))
        .anySatisfy(
            profile -> {
              assertThat(profile.getExternalId()).isEqualTo("svenskebanken-system-admin");
              assertThat(profile.getRole().name()).isEqualTo("SYSTEM_ADMIN");
            });
    assertThat(profiles.findByCustomerId(CustomerId.FINBANKEN)).isEmpty();
  }

  @Test
  void exposesSvenskeBankenRules() {
    assertThat(customerRules.maximumFunds()).isEqualTo(10);
    assertThat(customerRules.maximumLeaveMonths()).isEqualTo(18);
    assertThat(customerRules.showFees()).isTrue();
    assertThat(customerRules.companyMutations())
        .containsExactlyInAnyOrder(CompanyMutation.values());
  }

  @Test
  void exposesOnlyTheLoggedInCustomersPrivatePortfolio() {
    var profile =
        sessions.requireActive(
            sessions.createSession(CustomerId.SVENSKEBANKEN, "svenskebanken-portfolio"));
    var overview = privateData.overview(profile);
    assertThat(overview.personName()).isEqualTo("Elin Berg");
    assertThat(overview.insurances()).hasSize(3);
  }

  @Test
  void seedsInsurancesForOscarLindAknar() {
    var profile =
        sessions.requireActive(
            sessions.createSession(CustomerId.SVENSKEBANKEN, "svenskebanken-payment"));
    var overview = privateData.overview(profile);
    assertThat(overview.personName()).isEqualTo("Oscar Lind Aknar");
    assertThat(overview.insurances()).hasSize(3);
  }

  @Test
  void validatesAndPersistsFundAllocation() {
    var profile =
        sessions.requireActive(
            sessions.createSession(CustomerId.SVENSKEBANKEN, "svenskebanken-portfolio"));
    var insuranceId = privateData.overview(profile).insurances().getFirst().id();
    var changed =
        privateData.updateFundAllocation(
            profile,
            insuranceId,
            List.of(
                new PrivatePortalDataService.AllocationInput("Global Index", new BigDecimal("70")),
                new PrivatePortalDataService.AllocationInput(
                    "Svenska Aktier", new BigDecimal("30"))));
    assertThat(changed.fundHoldings())
        .extracting(PrivatePortalDataService.FundHolding::allocationPercent)
        .containsExactly("70", "30");
    var profileAfterLogin =
        sessions.requireActive(
            sessions.createSession(CustomerId.SVENSKEBANKEN, "svenskebanken-portfolio"));
    assertThat(privateData.insurance(profileAfterLogin, insuranceId).fundHoldings())
        .extracting(PrivatePortalDataService.FundHolding::allocationPercent)
        .containsExactly("70.00", "30.00");
    assertThatThrownBy(
            () ->
                privateData.updateFundAllocation(
                    profile,
                    insuranceId,
                    List.of(
                        new PrivatePortalDataService.AllocationInput(
                            "Global Index", new BigDecimal("80")))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void httpApiRequiresTheCorrectProfileAndRole() throws Exception {
    var client = HttpClient.newHttpClient();
    assertThat(
            client
                .send(
                    request("/api/private/overview").GET().build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(401);
    var privateToken = login(client, "svenskebanken-portfolio");
    var privateResponse =
        client.send(
            request("/api/private/overview")
                .header("Authorization", "Bearer " + privateToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(privateResponse.statusCode()).isEqualTo(200);
    assertThat(privateResponse.body()).contains("Elin Berg");
    var insuranceId =
        privateData.overview(sessions.requireActive(privateToken)).insurances().getFirst().id();
    var insuranceResponse =
        client.send(
            request("/api/private/insurances/" + insuranceId)
                .header("Authorization", "Bearer " + privateToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(insuranceResponse.statusCode()).isEqualTo(200);
    var transactionsResponse =
        client.send(
            request("/api/private/transactions")
                .header("Authorization", "Bearer " + privateToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(transactionsResponse.statusCode()).isEqualTo(200);
    var viewerToken = login(client, "svenskebanken-viewer");
    var viewerCompanyId =
        companyData.companies(sessions.requireActive(viewerToken)).getFirst().id();
    var plansResponse =
        client.send(
            request("/api/company/plans?companyId=" + viewerCompanyId)
                .header("Authorization", "Bearer " + viewerToken)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(plansResponse.statusCode()).isEqualTo(200);
    var writeResponse =
        client.send(
            request("/api/company/employees?companyId=" + viewerCompanyId)
                .header("Authorization", "Bearer " + viewerToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Testperson\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(writeResponse.statusCode()).isEqualTo(403);
  }

  @Test
  void systemAdminCanAccessBothPortalsAndManageAllCompanies() throws Exception {
    var client = HttpClient.newHttpClient();
    var token = login(client, "svenskebanken-system-admin");
    var profile = sessions.requireActive(token);
    var companies = companyData.companies(profile);
    assertThat(companies).hasSize(2);
    assertThat(
            client
                .send(
                    request("/api/private/overview")
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(200);
    assertThat(
            client
                .send(
                    request("/api/company/companies")
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(200);
    var company = companies.getFirst();
    var pendingCase =
        companyData.cases(profile, company.id()).stream()
            .filter(item -> item.status().equals("PENDING"))
            .findFirst()
            .orElseThrow();
    var response =
        client.send(
            request("/api/company/cases/" + pendingCase.id() + "/approve?companyId=" + company.id())
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  void allowsPutRequestsFromTheCustomersLocalClient() throws Exception {
    var response =
        HttpClient.newHttpClient()
            .send(
                request("/api/private/insurances/example/fund-allocation")
                    .header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "PUT")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
        .contains("http://localhost:5173");
    assertThat(response.headers().firstValue("Access-Control-Allow-Methods"))
        .hasValueSatisfying(methods -> assertThat(methods).contains("PUT"));
  }

  @Test
  void issuesSignedSessionJwtAndInvalidatesItOnLogout() {
    var token = sessions.createSession(CustomerId.SVENSKEBANKEN, "svenskebanken-portfolio");
    assertThat(token.split("\\.")).hasSize(3);
    assertThat(sessions.requireActive(token).name()).isEqualTo("Elin Berg");
    sessions.logout(token);
    assertThatThrownBy(() -> sessions.requireActive(token)).isInstanceOf(SecurityException.class);
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  private String login(HttpClient client, String profileId) throws Exception {
    var response =
        client.send(
            request("/api/auth/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"profileId\":\"" + profileId + "\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body().replaceAll(".*\\\"token\\\":\\\"([^\\\"]+)\\\".*", "$1");
  }
}
