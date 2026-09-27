package se.meepo.dinso.database.seed;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.meepo.dinso.database.entity.*;
import se.meepo.dinso.database.repository.DemoProfileRepository;
import se.meepo.dinso.service.CompanyAction;
import se.meepo.dinso.service.CustomerId;
import se.meepo.dinso.service.DemoRole;

@Component
public class DemoDataSeeder {
  private final EntityManager entities;
  private final DemoProfileRepository profiles;
  private final Clock clock;

  public DemoDataSeeder(EntityManager entities, DemoProfileRepository profiles, Clock clock) {
    this.entities = entities;
    this.profiles = profiles;
    this.clock = clock;
  }

  @Transactional
  public void seed(CustomerId customer) {
    if (entities
            .createQuery(
                "select count(p) from PersonEntity p where p.customerId = :customer", Long.class)
            .setParameter("customer", customer)
            .getSingleResult()
        > 0) {
      grantSystemAdminAccess(customer);
      return;
    }
    var catalog = CustomerSeedCatalog.forCustomer(customer);
    var today = LocalDate.now(clock);
    var elin = new PersonEntity(customer, "person-elin", "Elin Berg");
    var oscar = new PersonEntity(customer, "person-oscar", "Oscar Lind Aknar");
    entities.persist(elin);
    entities.persist(oscar);
    seedPrivateInsuranceData(customer, elin, catalog, today);
    seedPrivateInsuranceData(customer, oscar, catalog, today);
    entities.persist(
        new OutPaymentEntity(oscar, today.plusDays(15), new BigDecimal("12640.00"), "UPCOMING"));
    entities.persist(
        new OutPaymentEntity(oscar, today.minusDays(16), new BigDecimal("12640.00"), "ONGOING"));
    entities.persist(
        new DocumentEntity(
            customer, elin, null, "Årsbesked 2025", "ANNUAL_STATEMENT", today.minusMonths(8)));
    if (catalog.employeeCount() > 0) seedCompanies(customer, catalog, today, elin);
    entities.persist(
        new DemoEventEntity(
            customer, clock.instant(), "SEED_COMPLETE", "Demo-data är klar för " + customer));
    var prefix = customer.name().toLowerCase();
    profiles
        .findByCustomerIdAndExternalId(customer, prefix + "-portfolio")
        .ifPresent(profile -> profile.assignPerson(elin));
    profiles
        .findByCustomerIdAndExternalId(customer, prefix + "-payment")
        .ifPresent(profile -> profile.assignPerson(oscar));
    profiles
        .findByCustomerIdAndExternalId(customer, prefix + "-system-admin")
        .ifPresent(profile -> profile.assignPerson(elin));
  }

  private void grantSystemAdminAccess(CustomerId customer) {
    var profile =
        profiles
            .findByCustomerIdAndExternalId(
                customer, customer.name().toLowerCase() + "-system-admin")
            .orElse(null);
    if (profile == null) return;
    entities
        .createQuery(
            "select p from PersonEntity p where p.customerId = :customer and p.externalId ="
                + " :externalId",
            PersonEntity.class)
        .setParameter("customer", customer)
        .setParameter("externalId", "person-elin")
        .getResultStream()
        .findFirst()
        .ifPresent(profile::assignPerson);
    if (profile.getRole() == se.meepo.dinso.service.DemoRole.SYSTEM_ADMIN
        && entities
                .createQuery(
                    "select count(a) from CompanyAuthorizationEntity a where a.profile = :profile",
                    Long.class)
                .setParameter("profile", profile)
                .getSingleResult()
            == 0)
      entities
          .createQuery(
              "select c from CompanyEntity c where c.customerId = :customer", CompanyEntity.class)
          .setParameter("customer", customer)
          .getResultStream()
          .forEach(
              company ->
                  entities.persist(
                      new CompanyAuthorizationEntity(
                          profile, company, profile.getRole(), presetFor(profile.getRole()))));
  }

  private static Set<CompanyAction> presetFor(DemoRole role) {
    return role == DemoRole.COMPANY_VIEWER
        ? EnumSet.of(CompanyAction.READ)
        : EnumSet.allOf(CompanyAction.class);
  }

  private void seedPrivateInsuranceData(
      CustomerId customer, PersonEntity person, CustomerSeedCatalog catalog, LocalDate today) {
    var fund = persistInsurance(customer, person, catalog.fundInsurance());
    persistInsurance(customer, person, catalog.traditionalInsurance());
    persistInsurance(customer, person, catalog.riskInsurance());
    if (fund != null) {
      entities.persist(
          new FundHoldingEntity(
              fund,
              "Global Index",
              new BigDecimal("60.00"),
              catalog.fundInsurance().value().multiply(new BigDecimal("0.60"))));
      entities.persist(
          new FundHoldingEntity(
              fund,
              "Svenska Aktier",
              new BigDecimal("40.00"),
              catalog.fundInsurance().value().multiply(new BigDecimal("0.40"))));
      entities.persist(
          new TransactionEntity(
              fund,
              today.minusDays(3),
              "Premie från arbetsgivare",
              new BigDecimal("4850.00"),
              "BOOKED"));
      entities.persist(
          new TransactionEntity(
              fund, today.minusDays(10), "Fondbyte Global Index", BigDecimal.ZERO, "COMPLETED"));
    }
  }

  private InsuranceEntity persistInsurance(
      CustomerId customer, PersonEntity person, CustomerSeedCatalog.Insurance insurance) {
    if (insurance == null) return null;
    var entity =
        new InsuranceEntity(
            customer,
            person,
            insurance.productName(),
            insurance.type(),
            insurance.status(),
            insurance.value());
    entities.persist(entity);
    return entity;
  }

  private void seedCompanies(
      CustomerId customer, CustomerSeedCatalog catalog, LocalDate today, PersonEntity elin) {
    var primary =
        seedCompany(
            customer,
            "company-primary",
            catalog.companyName(),
            catalog,
            today,
            catalog.employeeCount(),
            elin);
    var secondaryName =
        customer == CustomerId.SVENSKEBANKEN ? "Västhamn Gruppen AB" : "Horisont Gruppen AB";
    var secondary =
        seedCompany(customer, "company-secondary", secondaryName, catalog, today, 3, null);
    profiles.findByCustomerId(customer).stream()
        .filter(
            profile ->
                profile.getPortal().name().equals("COMPANY")
                    || profile.getRole() == se.meepo.dinso.service.DemoRole.SYSTEM_ADMIN)
        .forEach(
            profile -> {
              entities.persist(
                  new CompanyAuthorizationEntity(
                      profile, primary, profile.getRole(), presetFor(profile.getRole())));
              if (profile.getExternalId().endsWith("-multi")
                  || profile.getRole() == se.meepo.dinso.service.DemoRole.SYSTEM_ADMIN)
                entities.persist(
                    new CompanyAuthorizationEntity(
                        profile, secondary, profile.getRole(), presetFor(profile.getRole())));
            });
  }

  private CompanyEntity seedCompany(
      CustomerId customer,
      String externalId,
      String companyName,
      CustomerSeedCatalog catalog,
      LocalDate today,
      int employeeCount,
      PersonEntity firstEmployee) {
    var company = new CompanyEntity(customer, externalId, companyName);
    entities.persist(company);
    var plans =
        catalog.plans().stream()
            .map(
                plan -> {
                  var planName =
                      externalId.equals("company-secondary") ? plan.name() + " Grupp" : plan.name();
                  var entity =
                      new PensionPlanEntity(customer, company, planName, plan.monthlyPremium());
                  entities.persist(entity);
                  return entity;
                })
            .toList();
    for (int number = 1; number <= employeeCount; number++) {
      var person =
          number == 1 && firstEmployee != null
              ? firstEmployee
              : new PersonEntity(
                  customer, externalId + "-employee-" + number, employeeName(number));
      if (person != firstEmployee) entities.persist(person);
      entities.persist(
          new EmploymentEntity(
              customer,
              person,
              company,
              plans.get(number % plans.size()),
              BigDecimal.valueOf(36000L + number * 1200L),
              today.minusMonths(number + 2L),
              employmentStatus(number)));
    }
    entities.persist(
        new DocumentEntity(
            customer, null, company, "Faktura september", "INVOICE", today.minusDays(2)));
    entities.persist(
        new InvoiceEntity(company, today.plusDays(20), new BigDecimal("133290.00"), "OPEN"));
    seedCompanyCases(customer, company, companyName, externalId, today);
    return company;
  }

  private void seedCompanyCases(
      CustomerId customer,
      CompanyEntity company,
      String companyName,
      String externalId,
      LocalDate today) {
    var companyType = externalId.equals("company-secondary") ? "gruppavtal" : "ordinarie avtal";
    var caseSeeds =
        new CaseSeed[] {
          new CaseSeed("Löneändring", "PENDING", -12),
          new CaseSeed("Tjänstledighet", "ONGOING", 19),
          new CaseSeed("Nyanslutning", "COMPLETED", -8),
          new CaseSeed("Avslutad anställning", "PENDING", 3),
          new CaseSeed("Premieavvikelse", "ONGOING", -2),
          new CaseSeed("Planbyte", "COMPLETED", -18),
          new CaseSeed("Förmånstagarförordnande", "PENDING", 7),
          new CaseSeed("Adressuppdatering", "COMPLETED", -5),
          new CaseSeed("Retroaktiv premie", "ONGOING", 12),
          new CaseSeed("Föräldraledighet", "PENDING", 15),
          new CaseSeed("Saknad löneuppgift", "ONGOING", -1),
          new CaseSeed("Årsavstämning", "COMPLETED", -28),
          new CaseSeed("Ny pensionsplan", "PENDING", 22),
          new CaseSeed("Uppsägning av skydd", "ONGOING", 5),
          new CaseSeed("Försenad faktura", "PENDING", -6),
          new CaseSeed("Val av placeringsinriktning", "COMPLETED", -14),
          new CaseSeed("Ändrad sysselsättningsgrad", "ONGOING", 9),
          new CaseSeed("Samordning av premier", "PENDING", 27),
          new CaseSeed("Intyg om försäkring", "COMPLETED", -21),
          new CaseSeed("Korrigerad personuppgift", "PENDING", 1),
          new CaseSeed("Återinträde efter tjänstledighet", "ONGOING", 16),
          new CaseSeed("Översyn av efterlevandeskydd", "COMPLETED", -10),
          new CaseSeed("Rapportering av nyanställda", "PENDING", 30),
          new CaseSeed("Kontroll av premieunderlag", "ONGOING", 11)
        };
    var dateOffset = externalId.equals("company-secondary") ? 4 : 0;
    for (var seed : caseSeeds) {
      entities.persist(
          new CompanyCaseEntity(
              customer,
              company,
              seed.title() + " · " + companyType,
              seed.status(),
              today.plusDays(seed.daysUntilDue() + dateOffset),
              seed.title()
                  + " för "
                  + companyName
                  + " hanteras inom "
                  + companyType
                  + "."));
    }
  }

  private record CaseSeed(String title, String status, long daysUntilDue) {}

  private static String employeeName(int number) {
    return new String[] {
              "Alva Norberg",
              "Mio Sten",
              "Tilde Rask",
              "Hugo Dahl",
              "Nora Holst",
              "Ivar Holm",
              "Saga Mark",
              "Leo Nyberg"
            }
            [(number - 1) % 8]
        + " "
        + number;
  }

  private static String employmentStatus(int number) {
    return switch (number % 4) {
      case 0 -> "UPCOMING";
      case 1 -> "ACTIVE";
      case 2 -> "LEAVE";
      default -> "ENDED";
    };
  }
}
