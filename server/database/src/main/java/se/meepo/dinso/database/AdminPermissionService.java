package se.meepo.dinso.database;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.meepo.dinso.database.entity.*;
import se.meepo.dinso.database.repository.*;
import se.meepo.dinso.service.*;

@Service
@Transactional(readOnly = true)
public class AdminPermissionService {
  private final DemoProfileRepository profiles;
  private final CompanyAuthorizationRepository authorizations;
  private final CustomerId customer;
  private final Clock clock;
  private final EntityManager entities;

  public AdminPermissionService(
      DemoProfileRepository profiles,
      CompanyAuthorizationRepository authorizations,
      CustomerId customer,
      Clock clock,
      EntityManager entities) {
    this.profiles = profiles;
    this.authorizations = authorizations;
    this.customer = customer;
    this.clock = clock;
    this.entities = entities;
  }

  public Overview overview() {
    var actions =
        Arrays.stream(CompanyAction.values())
            .map(action -> new ActionInfo(action, action.isWrite()))
            .toList();
    var result =
        profiles.findByCustomerId(customer).stream()
            .filter(profile -> profile.getPortal() == PortalType.COMPANY)
            .sorted(Comparator.comparing(DemoProfileEntity::getName))
            .map(
                profile ->
                    new ProfilePermissions(
                        profile.getExternalId(),
                        profile.getName(),
                        profile.getDescription(),
                        profile.getRole(),
                        authorizations.findByProfile(profile).stream()
                            .sorted(Comparator.comparing(item -> item.getCompany().getName()))
                            .map(AdminPermissionService::companyPermissions)
                            .toList()))
            .toList();
    return new Overview(actions, result);
  }

  @Transactional
  public CompanyPermissions setActions(String authorizationId, Set<CompanyAction> actions) {
    var authorization =
        authorizations
            .findById(authorizationId)
            .orElseThrow(() -> new IllegalArgumentException("Behörigheten finns inte"));
    var profile = authorization.getProfile();

    if (profile.getCustomerId() != customer
        || profile.getPortal() != PortalType.COMPANY
        || profile.getRole() == DemoRole.SYSTEM_ADMIN)
      throw new SecurityException("Behörigheten kan inte ändras");

    authorization.replaceActions(actions);
    entities.persist(
        new DemoEventEntity(
            customer,
            clock.instant(),
            "CHANGE_PERMISSIONS",
            "Behörigheter ändrade för "
                + profile.getName()
                + " i "
                + authorization.getCompany().getName()));
    return companyPermissions(authorization);
  }

  private static CompanyPermissions companyPermissions(CompanyAuthorizationEntity item) {
    return new CompanyPermissions(
        item.getId(),
        item.getCompany().getId(),
        item.getCompany().getName(),
        item.grantedActions().stream().sorted().toList());
  }

  public record Overview(List<ActionInfo> actions, List<ProfilePermissions> profiles) {}

  public record ActionInfo(CompanyAction action, boolean write) {}

  public record ProfilePermissions(
      String id,
      String name,
      String description,
      DemoRole role,
      List<CompanyPermissions> companyPermissions) {}

  public record CompanyPermissions(
      String authorizationId, String companyId, String companyName, List<CompanyAction> actions) {}
}
