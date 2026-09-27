package com.cleanroute.repository;
import com.cleanroute.domain.AppUser; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface AppUserRepository extends JpaRepository<AppUser,UUID> { Optional<AppUser> findByEmail(String email); boolean existsByEmail(String email); }
