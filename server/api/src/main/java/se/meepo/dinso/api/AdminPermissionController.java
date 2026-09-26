package se.meepo.dinso.api;

import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import se.meepo.dinso.database.AdminPermissionService;
import se.meepo.dinso.service.CompanyAction;

@RestController
@Profile("company")
@RequestMapping("/api/admin")
public class AdminPermissionController {
  private final AdminPermissionService admin;

  public AdminPermissionController(AdminPermissionService admin) {
    this.admin = admin;
  }

  @GetMapping("/profiles")
  AdminPermissionService.Overview profiles() {
    return admin.overview();
  }

  @PutMapping("/authorizations/{authorizationId}/actions")
  AdminPermissionService.CompanyPermissions setActions(
      @PathVariable("authorizationId") String authorizationId,
      @RequestBody ActionsRequest input) {
    return admin.setActions(authorizationId, input.actions());
  }

  public record ActionsRequest(Set<CompanyAction> actions) {}
}
