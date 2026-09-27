package se.meepo.dinso.database.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.meepo.dinso.database.entity.*;

public interface CompanyAuthorizationRepository
    extends JpaRepository<CompanyAuthorizationEntity, String> {
  List<CompanyAuthorizationEntity> findByProfile(DemoProfileEntity profile);

  Optional<CompanyAuthorizationEntity> findByProfileAndCompanyId(
      DemoProfileEntity profile, String companyId);

  @Query(
      """
      select distinct a from CompanyAuthorizationEntity a
      join fetch a.company
      left join fetch a.actions
      where a.profile in :profiles
      """)
  List<CompanyAuthorizationEntity> findByProfileInFetchingCompanyAndActions(
      @Param("profiles") Collection<DemoProfileEntity> profiles);
}
