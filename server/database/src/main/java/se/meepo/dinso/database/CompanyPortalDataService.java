package se.meepo.dinso.database;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.meepo.dinso.database.entity.*;
import se.meepo.dinso.database.repository.*;
import se.meepo.dinso.service.*;

@Service
@Transactional(readOnly = true)
public class CompanyPortalDataService {

  private final DemoProfileRepository profiles;
  private final CompanyAuthorizationRepository authorizations;
  private final EmploymentRepository employments;
  private final PensionPlanRepository plans;
  private final CompanyCaseRepository cases;
  private final CustomerRules rules;
  private final Clock clock;
  private final EntityManager entities;

  public CompanyPortalDataService(
      DemoProfileRepository profiles,
      CompanyAuthorizationRepository authorizations,
      EmploymentRepository employments,
      PensionPlanRepository plans,
      CompanyCaseRepository cases,
      CustomerRules rules,
      Clock clock,
      EntityManager entities) {
    this.profiles = profiles;
    this.authorizations = authorizations;
    this.employments = employments;
    this.plans = plans;
    this.cases = cases;
    this.rules = rules;
    this.clock = clock;
    this.entities = entities;
  }

  public List<Company> companies(DemoProfile profile) {
    return authorizations(profile).stream()
        .filter(item -> item.holds(CompanyAction.READ))
        .map(
            item ->
                new Company(
                    item.getCompany().getId(),
                    item.getCompany().getName(),
                    item.getRole(),
                    item.grantedActions().stream().sorted().toList()))
        .toList();
  }

  public Overview overview(DemoProfile profile, String companyId) {
    var company = requireCompanyPermission(profile, companyId, CompanyAction.READ);
    return new Overview(
        company.getId(),
        company.getName(),
        employments.countByCompany(company),
        plans.countByCompany(company),
        (int)
            cases.findByCompanyOrderByDueOn(company).stream()
                .filter(
                    item ->
                        !item.getStatus().equals("COMPLETED")
                            && !item.getStatus().equals("APPROVED"))
                .count());
  }

  public List<Case> cases(DemoProfile profile, String companyId) {
    return cases.findByCompanyOrderByDueOn(requireCompanyPermission(profile, companyId, CompanyAction.READ))
        .stream()
        .map(this::caseItem)
        .toList();
  }

  @Transactional
  public Case approveCase(DemoProfile profile, String companyId, String caseId) {
    var item = ownedCase(profile, companyId, caseId, CompanyAction.APPROVE_CASE);
    item.approve();
    event(profile, "APPROVE_CASE", item.getTitle() + " har godkänts");
    return caseItem(item);
  }

  public List<Plan> plans(DemoProfile profile, String companyId) {
    return plans.findByCompany(requireCompanyPermission(profile, companyId, CompanyAction.READ)).stream()
        .map(
            item ->
                new Plan(item.getId(), item.getName(), item.getMonthlyPremium().toPlainString()))
        .toList();
  }

  public List<Employment> employments(DemoProfile profile, String companyId, String query) {
    return employments.findByCompany(requireCompanyPermission(profile, companyId, CompanyAction.READ)).stream()
        .filter(
            item ->
                query == null
                    || query.isBlank()
                    || item.getPerson()
                        .getDisplayName()
                        .toLowerCase()
                        .contains(query.toLowerCase()))
        .sorted(Comparator.comparing(item -> item.getPerson().getDisplayName()))
        .map(this::employment)
        .toList();
  }

  public Employment employment(DemoProfile profile, String companyId, String employmentId) {
    var item = ownedEmployment(profile, companyId, employmentId, CompanyAction.READ);
    return employment(item);
  }

  @Transactional
  public Employment addEmployee(
      DemoProfile profile,
      String companyId,
      String name,
      String planId,
      BigDecimal salary,
      LocalDate startsOn) {
    requireCustomerSupports(CompanyMutation.ADD_EMPLOYEE);
    var company = requireCompanyPermission(profile, companyId, CompanyAction.ADD_EMPLOYEE);
    if (name == null
        || name.isBlank()
        || salary == null
        || salary.signum() <= 0
        || startsOn == null)
      throw new IllegalArgumentException("Namn, lön och startdatum måste anges");
    var plan =
        plans
            .findById(planId)
            .orElseThrow(() -> new IllegalArgumentException("Pensionsplanen finns inte"));
    if (!plan.getCompany().getId().equals(company.getId()))
      throw new SecurityException("Pensionsplanen tillhör inte valt företag");
    var person =
        new PersonEntity(profile.customerId(), "employee-demo-" + System.nanoTime(), name.trim());
    entities.persist(person);
    var item =
        employments.save(
            new EmploymentEntity(
                profile.customerId(), person, company, plan, salary, startsOn, "ACTIVE"));
    event(profile, "ADD_EMPLOYEE", name.trim() + " har lagts till");
    return employment(item);
  }

  @Transactional
  public Employment changeSalary(
      DemoProfile profile, String companyId, String employmentId, BigDecimal salary) {
    requireCustomerSupports(CompanyMutation.CHANGE_SALARY);
    if (salary == null || salary.signum() <= 0)
      throw new IllegalArgumentException("Lönen måste vara större än noll");
    var item = ownedEmployment(profile, companyId, employmentId, CompanyAction.CHANGE_SALARY);
    item.changeSalary(salary);
    event(profile, "CHANGE_SALARY", "Lön ändrad för " + item.getPerson().getDisplayName());
    return employment(item);
  }

  @Transactional
  public Employment registerLeave(
      DemoProfile profile,
      String companyId,
      String employmentId,
      LeaveReason reason,
      LocalDate until) {
    requireCustomerSupports(CompanyMutation.REGISTER_LEAVE);
    if (!rules.leaveReasons().contains(reason)
        || until == null
        || until.isAfter(LocalDate.now(clock).plusMonths(rules.maximumLeaveMonths())))
      throw new IllegalArgumentException("Tjänstledigheten följer inte kundens regler");
    var item = ownedEmployment(profile, companyId, employmentId, CompanyAction.REGISTER_LEAVE);
    item.registerLeave(until);
    event(
        profile,
        "REGISTER_LEAVE",
        reason + " registrerad för " + item.getPerson().getDisplayName());
    return employment(item);
  }

  @Transactional
  public Employment endEmployment(
      DemoProfile profile, String companyId, String employmentId, LocalDate endsOn) {
    requireCustomerSupports(CompanyMutation.END_EMPLOYMENT);
    var item = ownedEmployment(profile, companyId, employmentId, CompanyAction.END_EMPLOYMENT);
    item.end(endsOn == null ? LocalDate.now(clock) : endsOn);
    event(
        profile, "END_EMPLOYMENT", "Anställning avslutad för " + item.getPerson().getDisplayName());
    return employment(item);
  }

  private void requireCustomerSupports(CompanyMutation mutation) {
    if (!rules.companyMutations().contains(mutation))
      throw new IllegalArgumentException("Åtgärden är inte tillgänglig för denna kund");
  }

  private EmploymentEntity ownedEmployment(
      DemoProfile profile, String companyId, String employmentId, CompanyAction action) {
    var company = requireCompanyPermission(profile, companyId, action);
    var item =
        employments
            .findById(employmentId)
            .orElseThrow(() -> new IllegalArgumentException("Anställningen finns inte"));
    if (!item.getCompany().getId().equals(company.getId()))
      throw new SecurityException("Anställningen tillhör inte valt företag");
    return item;
  }

  private CompanyCaseEntity ownedCase(
      DemoProfile profile, String companyId, String caseId, CompanyAction action) {
    var company = requireCompanyPermission(profile, companyId, action);
    var item =
        cases
            .findById(caseId)
            .orElseThrow(() -> new IllegalArgumentException("Ärendet finns inte"));
    if (!item.getCompany().getId().equals(company.getId()))
      throw new SecurityException("Ärendet tillhör inte valt företag");
    return item;
  }

  private CompanyEntity requireCompanyPermission(DemoProfile profile, String companyId, CompanyAction action) {
    if (companyId == null || companyId.isBlank())
      throw new IllegalArgumentException("companyId krävs");
    var authorization =
      authorizations
        .findByProfileAndCompanyId(profileEntity(profile), companyId)
        .orElseThrow(() -> new SecurityException("Profilen är inte behörig för företaget"));
    if (!authorization.holds(action))
      throw new SecurityException("Profilen saknar behörighet för åtgärden " + action);
    return authorization.getCompany();
  }

  private List<CompanyAuthorizationEntity> authorizations(DemoProfile profile) {
    return authorizations.findByProfile(profileEntity(profile));
  }

  private DemoProfileEntity profileEntity(DemoProfile profile) {
    return profiles
        .findByCustomerIdAndExternalId(profile.customerId(), profile.id())
        .orElseThrow(() -> new SecurityException("Unknown demo profile"));
  }

  private Employment employment(EmploymentEntity item) {
    return new Employment(
        item.getId(),
        item.getPerson().getDisplayName(),
        item.getPensionPlan().getName(),
        item.getMonthlySalary().toPlainString(),
        item.getStatus(),
        item.getStartsOn().toString(),
        item.getEndsOn() == null ? null : item.getEndsOn().toString());
  }

  private Case caseItem(CompanyCaseEntity item) {
    return new Case(
        item.getId(),
        item.getTitle(),
        item.getStatus(),
        item.getDueOn().toString(),
        item.getDetail());
  }

  private void event(DemoProfile profile, String type, String summary) {
    entities.persist(new DemoEventEntity(profile.customerId(), clock.instant(), type, summary));
  }

  public record Company(String id, String name, DemoRole role, List<CompanyAction> actions) {}

  public record Overview(
      String companyId, String companyName, long employees, long plans, int openCases) {}

  public record Plan(String id, String name, String monthlyPremium) {}

  public record Employment(
      String id,
      String personName,
      String planName,
      String monthlySalary,
      String status,
      String startsOn,
      String endsOn) {}

  public record Case(String id, String name, String status, String dueOn, String detail) {}
}
