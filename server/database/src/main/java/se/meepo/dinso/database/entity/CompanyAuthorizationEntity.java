package se.meepo.dinso.database.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import se.meepo.dinso.service.CompanyAction;
import se.meepo.dinso.service.DemoRole;

@Entity
@Table(
    name = "company_authorization",
    uniqueConstraints = @UniqueConstraint(columnNames = {"profile_id", "company_id"}))
public class CompanyAuthorizationEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @ManyToOne(optional = false)
  private DemoProfileEntity profile;

  @ManyToOne(optional = false)
  private CompanyEntity company;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DemoRole role;

  @ElementCollection
  @CollectionTable(
      name = "company_authorization_action",
      joinColumns = @JoinColumn(name = "authorization_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false)
  private Set<CompanyAction> actions = new HashSet<>();

  protected CompanyAuthorizationEntity() {}

  public CompanyAuthorizationEntity(
      DemoProfileEntity profile, CompanyEntity company, DemoRole role, Set<CompanyAction> actions) {
    this.profile = profile;
    this.company = company;
    this.role = role;
    replaceActions(actions);
  }

  public String getId() {
    return id;
  }

  public DemoProfileEntity getProfile() {
    return profile;
  }

  public CompanyEntity getCompany() {
    return company;
  }

  public DemoRole getRole() {
    return role;
  }

  public Set<CompanyAction> grantedActions() {
    return Set.copyOf(actions);
  }

  public boolean holds(CompanyAction action) {
    return actions.contains(action);
  }

  public void replaceActions(Set<CompanyAction> newAction) {
    if (newAction == null) throw new IllegalArgumentException("Åtgärdsbehörigheter måste anges");
    if (newAction.stream().anyMatch(CompanyAction::isWrite) && !newAction.contains(CompanyAction.READ))
      throw new IllegalArgumentException(
          "Behörighet att utföra åtgärder kräver även behörighet att läsa information");
    actions.clear();
    actions.addAll(newAction);
  }
}
