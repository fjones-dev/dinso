package se.meepo.dinso.api;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import se.meepo.dinso.database.CompanyPortalDataService;
import se.meepo.dinso.database.DemoSessionService;
import se.meepo.dinso.service.*;

@RestController
@Profile("company")
@RequestMapping("/api/company")
public class CompanyPortalController {
  private final DemoSessionService sessions;
  private final CompanyPortalDataService data;

  public CompanyPortalController(DemoSessionService sessions, CompanyPortalDataService data) {
    this.sessions = sessions;
    this.data = data;
  }

  @GetMapping("/companies")
  java.util.List<CompanyPortalDataService.Company> companies(HttpServletRequest request) {
    return data.companies(current(request));
  }

  @GetMapping("/overview")
  CompanyPortalDataService.Overview overview(
      HttpServletRequest request,
      @RequestParam(name = "companyId") String companyId) {
    return data.overview(current(request), companyId);
  }

  @GetMapping("/plans")
  java.util.List<CompanyPortalDataService.Plan> plans(
      HttpServletRequest request,
      @RequestParam(name = "companyId") String companyId) {
    return data.plans(current(request), companyId);
  }

  @GetMapping("/cases")
  java.util.List<CompanyPortalDataService.Case> cases(
      HttpServletRequest request,
      @RequestParam(name = "companyId") String companyId) {
    return data.cases(current(request), companyId);
  }

  @PutMapping("/cases/{caseId}/approve")
  CompanyPortalDataService.Case approveCase(
      HttpServletRequest request,
      @PathVariable("caseId") String caseId,
      @RequestParam(name = "companyId") String companyId) {
    return data.approveCase(current(request), companyId, caseId);
  }

  @GetMapping("/employments")
  java.util.List<CompanyPortalDataService.Employment> employments(
      HttpServletRequest request,
      @RequestParam(name = "companyId") String companyId,
      @RequestParam(name = "query", required = false) String query) {
    return data.employments(current(request), companyId, query);
  }

  @GetMapping("/employments/{employmentId}")
  CompanyPortalDataService.Employment employment(
      HttpServletRequest request,
      @PathVariable("employmentId") String employmentId,
      @RequestParam(name = "companyId") String companyId) {
    return data.employment(current(request), companyId, employmentId);
  }

  @PostMapping("/employees")
  @ResponseStatus(HttpStatus.CREATED)
  CompanyPortalDataService.Employment addEmployee(
      HttpServletRequest request,
      @RequestParam(name = "companyId") String companyId,
      @RequestBody EmployeeRequest input) {
    return data.addEmployee(
        current(request),
        companyId,
        input.name(),
        input.planId(),
        input.salary(),
        input.startsOn());
  }

  @PutMapping("/employments/{employmentId}/salary")
  CompanyPortalDataService.Employment changeSalary(
      HttpServletRequest request,
      @PathVariable("employmentId") String employmentId,
      @RequestParam(name = "companyId") String companyId,
      @RequestBody SalaryRequest input) {
    return data.changeSalary(current(request), companyId, employmentId, input.salary());
  }

  @PutMapping("/employments/{employmentId}/leave")
  CompanyPortalDataService.Employment registerLeave(
      HttpServletRequest request,
      @PathVariable("employmentId") String employmentId,
      @RequestParam(name = "companyId") String companyId,
      @RequestBody LeaveRequest input) {
    return data.registerLeave(
        current(request), companyId, employmentId, input.reason(), input.until());
  }

  @PutMapping("/employments/{employmentId}/end")
  CompanyPortalDataService.Employment endEmployment(
      HttpServletRequest request,
      @PathVariable("employmentId") String employmentId,
      @RequestParam(name = "companyId") String companyId,
      @RequestBody EndRequest input) {
    return data.endEmployment(current(request), companyId, employmentId, input.endsOn());
  }

  private DemoProfile current(HttpServletRequest request) {
    var profile = sessions.requireActive(token(request));
    if (profile.portal() != PortalType.COMPANY && profile.role() != DemoRole.SYSTEM_ADMIN)
      throw new Forbidden();
    return profile;
  }

  private static String token(HttpServletRequest request) {
    var value = request.getHeader("Authorization");
    if (value == null || !value.startsWith("Bearer ")) throw new Forbidden();
    return value.substring(7);
  }

  public record EmployeeRequest(
      String name, String planId, BigDecimal salary, LocalDate startsOn) {}

  public record SalaryRequest(BigDecimal salary) {}

  public record LeaveRequest(LeaveReason reason, LocalDate until) {}

  public record EndRequest(LocalDate endsOn) {}

  @ResponseStatus(HttpStatus.FORBIDDEN)
  static class Forbidden extends RuntimeException {}
}
