package com.cleanroute.repository;
import com.cleanroute.domain.RouteHistory; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface RouteHistoryRepository extends JpaRepository<RouteHistory,UUID> { List<RouteHistory> findAllByUserIdOrderByCreatedAtDesc(UUID userId); }
