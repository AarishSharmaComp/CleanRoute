package com.cleanroute.repository;
import com.cleanroute.domain.SavedPlace; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SavedPlaceRepository extends JpaRepository<SavedPlace,UUID> { List<SavedPlace> findAllByUserIdOrderByCreatedAtDesc(UUID userId); Optional<SavedPlace> findByIdAndUserId(UUID id,UUID userId); }
