package se.meepo.dinso.database;

import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.meepo.dinso.database.entity.*;
import se.meepo.dinso.database.repository.*;
import se.meepo.dinso.service.*;

@Service
@Transactional
public class DemoSessionService {
  private final DemoProfileRepository profiles;
  private final DemoSessionRepository sessions;
  private final CompanyAuthorizationRepository authorizations;
  private final DemoJwtService jwt;
  private final Clock clock;

  public DemoSessionService(
      DemoProfileRepository profiles,
      DemoSessionRepository sessions,
      CompanyAuthorizationRepository authorizations,
      DemoJwtService jwt,
      Clock clock) {
    this.profiles = profiles;
    this.sessions = sessions;
    this.authorizations = authorizations;
    this.jwt = jwt;
    this.clock = clock;
  }

  public DemoProfile login(CustomerId customer, String profileId) {
    return profiles
        .findByCustomerIdAndExternalId(customer, profileId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown demo profile"))
        .toDomain();
  }

  public String createSession(CustomerId customer, String profileId) {
    var profile =
        profiles
            .findByCustomerIdAndExternalId(customer, profileId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown demo profile"));
    if (profile.getPortal() == PortalType.COMPANY
        && authorizations.findByProfile(profile).stream()
            .noneMatch(item -> item.holds(CompanyAction.READ)))
      throw new SecurityException("Ingen behörighet till företagsportalen");
    var sessionId = UUID.randomUUID().toString();
    var expiresAt = clock.instant().plus(Duration.ofHours(2));
    sessions.save(new DemoSessionEntity(sessionId, profile, expiresAt));
    return jwt.issue(sessionId, customer, expiresAt);
  }

  public DemoProfile requireActive(String token) {
    var claims = jwt.verify(token);
    var session =
        sessions
            .findByToken(claims.sessionId())
            .filter(
                item ->
                    item.isActive()
                        && item.getExpiresAt().isAfter(clock.instant())
                        && item.getProfile().getCustomerId() == claims.customer())
            .orElseThrow(() -> new SecurityException("Invalid or expired demo session"));
    return session.getProfile().toDomain();
  }

  public void logout(String token) {
    sessions.findByToken(jwt.verify(token).sessionId()).ifPresent(session -> session.invalidate());
  }
}
