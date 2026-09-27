package com.cleanroute.repository;
import com.cleanroute.domain.UserPreference; import org.springframework.data.jpa.repository.JpaRepository; import java.util.UUID;
public interface UserPreferenceRepository extends JpaRepository<UserPreference,UUID> {}
