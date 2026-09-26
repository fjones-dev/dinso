package se.meepo.dinso.database.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import se.meepo.dinso.database.entity.*;


public interface CompanyAuthorizationRepository
    extends JpaRepository<CompanyAuthorizationEntity, String> {
  List<CompanyAuthorizationEntity> findByProfile(DemoProfileEntity profile);

  Optional<CompanyAuthorizationEntity> findByProfileAndCompanyId(
      DemoProfileEntity profile, String companyId);
}
