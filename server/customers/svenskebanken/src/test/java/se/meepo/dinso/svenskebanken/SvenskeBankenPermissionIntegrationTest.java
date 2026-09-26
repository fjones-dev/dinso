package se.meepo.dinso.svenskebanken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import se.meepo.dinso.database.CompanyPortalDataService;
import se.meepo.dinso.database.DemoSessionService;
import se.meepo.dinso.database.AdminPermissionService;
import se.meepo.dinso.service.CompanyAction;
import se.meepo.dinso.service.CustomerId;
import se.meepo.dinso.service.DemoProfile;
import se.meepo.dinso.service.LeaveReason;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
    classes = SvenskeBankenApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SvenskeBankenPermissionIntegrationTest {
  private static final String NORAH = "svenskebanken-multi";
  private static final String PRIMARY = "Nordljus Teknik AB";
  private static final String SECONDARY = "Västhamn Gruppen AB";

  @Autowired private DemoSessionService sessions;
  @Autowired private CompanyPortalDataService companyData;
  @Autowired private AdminPermissionService admin;

  @Value("${local.server.port}")
  private int port;

  @BeforeEach
  void resetNorahToFullAccess() {
    for (var company : List.of(PRIMARY, SECONDARY))
      admin.setActions(permissionsFor(company).authorizationId(), EnumSet.allOf(CompanyAction.class));
  }

  @Test
  void readAndApproveOnlyPersonaCanApproveButNotChangeSalary() {
    var primary = permissionsFor(PRIMARY);
    admin.setActions(
        primary.authorizationId(), EnumSet.of(CompanyAction.READ, CompanyAction.APPROVE_CASE));
    var profile = norah();

    assertThat(companyData.companies(profile))
        .filteredOn(company -> company.id().equals(primary.companyId()))
        .singleElement()
        .satisfies(
            company ->
                assertThat(company.actions())
                    .containsExactly(CompanyAction.READ, CompanyAction.APPROVE_CASE));
    var pending = pendingCase(profile, primary.companyId());
    assertThat(companyData.approveCase(profile, primary.companyId(), pending).status())
        .isEqualTo("APPROVED");
    var employment = companyData.employments(profile, primary.companyId(), null).getFirst();
    assertThatThrownBy(
            () ->
                companyData.changeSalary(
                    profile, primary.companyId(), employment.id(), new BigDecimal("40000")))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void salaryAndLeavePersonaCanDoThoseActionsButNotApprove() {
    var primary = permissionsFor(PRIMARY);
    admin.setActions(
        primary.authorizationId(),
        EnumSet.of(
            CompanyAction.READ, CompanyAction.CHANGE_SALARY, CompanyAction.REGISTER_LEAVE));
    var profile = norah();
    var employment = companyData.employments(profile, primary.companyId(), null).getFirst();

    assertThat(
            companyData
                .changeSalary(
                    profile, primary.companyId(), employment.id(), new BigDecimal("40000"))
                .monthlySalary())
        .isEqualTo("40000");
    companyData.registerLeave(
        profile,
        primary.companyId(),
        employment.id(),
        LeaveReason.PARENTAL_LEAVE,
        LocalDate.now().plusMonths(1));
    var pending = pendingCase(profile, primary.companyId());
    assertThatThrownBy(() -> companyData.approveCase(profile, primary.companyId(), pending))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void personaWithoutActionsCannotLogInAndAnOpenSessionIsRefused() {
    var openSession = sessions.createSession(CustomerId.SVENSKEBANKEN, NORAH);
    var profile = sessions.requireActive(openSession);
    var primary = permissionsFor(PRIMARY);
    for (var company : List.of(PRIMARY, SECONDARY))
      admin.setActions(permissionsFor(company).authorizationId(), Set.of());

    assertThatThrownBy(() -> sessions.createSession(CustomerId.SVENSKEBANKEN, NORAH))
        .isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> companyData.overview(profile, primary.companyId()))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void grantsAreScopedPerCompany() {
    var primary = permissionsFor(PRIMARY);
    var secondary = permissionsFor(SECONDARY);
    admin.setActions(primary.authorizationId(), EnumSet.of(CompanyAction.READ));
    var profile = norah();
    var inPrimary = companyData.employments(profile, primary.companyId(), null).getFirst();
    var inSecondary = companyData.employments(profile, secondary.companyId(), null).getFirst();

    assertThatThrownBy(
            () ->
                companyData.changeSalary(
                    profile, primary.companyId(), inPrimary.id(), new BigDecimal("40000")))
        .isInstanceOf(SecurityException.class);
    companyData.changeSalary(
        profile, secondary.companyId(), inSecondary.id(), new BigDecimal("40000"));
  }

  @Test
  void companyEndpointsRequireAnAuthorizedCompanyId() throws Exception {
    var client = HttpClient.newHttpClient();
    var token = login(client, "svenskebanken-admin");

    assertThat(status(client, token, "GET", "/api/company/plans", null)).isEqualTo(400);
    assertThat(status(client, token, "GET", "/api/company/plans?companyId=", null)).isEqualTo(400);
    assertThat(status(client, token, "GET", "/api/company/plans?companyId=unknown", null))
        .isEqualTo(403);
  }

  @Test
  void adminEndpointsAreSystemAdminOnlyAndEnforceTheReadInvariant() throws Exception {
    var client = HttpClient.newHttpClient();
    var companyAdmin = login(client, "svenskebanken-admin");
    assertThat(status(client, companyAdmin, "GET", "/api/admin/profiles", null)).isEqualTo(403);

    var systemAdmin = login(client, "svenskebanken-system-admin");
    assertThat(status(client, systemAdmin, "GET", "/api/admin/profiles", null)).isEqualTo(200);

    var path = "/api/admin/authorizations/" + permissionsFor(PRIMARY).authorizationId() + "/actions";
    assertThat(status(client, systemAdmin, "PUT", path, "{\"actions\":[\"APPROVE_CASE\"]}"))
        .isEqualTo(400);
    assertThat(status(client, systemAdmin, "PUT", path, "{\"actions\":[\"READ\",\"APPROVE_CASE\"]}"))
        .isEqualTo(200);
  }

  private DemoProfile norah() {
    return sessions.requireActive(sessions.createSession(CustomerId.SVENSKEBANKEN, NORAH));
  }

  private String pendingCase(DemoProfile profile, String companyId) {
    return companyData.cases(profile, companyId).stream()
        .filter(item -> item.status().equals("PENDING"))
        .findFirst()
        .orElseThrow()
        .id();
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

  private int status(HttpClient client, String token, String method, String path, String body)
      throws Exception {
    var publisher =
        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
    return client
        .send(
            request(path)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build(),
            HttpResponse.BodyHandlers.discarding())
        .statusCode();
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  private AdminPermissionService.CompanyPermissions permissionsFor(String companyName) {
    return admin.overview().profiles().stream()
        .filter(profile -> profile.id().equals(NORAH))
        .flatMap(profile -> profile.companyPermissions().stream())
        .filter(item -> item.companyName().equals(companyName))
        .findFirst()
        .orElseThrow();
  }
}
