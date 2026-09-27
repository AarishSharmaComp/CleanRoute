package com.cleanroute.repository;
import com.cleanroute.domain.SavedRoute; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SavedRouteRepository extends JpaRepository<SavedRoute,UUID> { List<SavedRoute> findAllByUserIdOrderByCreatedAtDesc(UUID userId); Optional<SavedRoute> findByIdAndUserId(UUID id,UUID userId); }
