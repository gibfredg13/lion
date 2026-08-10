package com.github.bgalek.database;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    List<LoginHistory> findByUserIdOrderByLoginTimeDesc(String userId, Pageable pageable);
    
    List<LoginHistory> findByEmailOrderByLoginTimeDesc(String email, Pageable pageable);
    
    List<LoginHistory> findAllByOrderByLoginTimeDesc(Pageable pageable);
    
    @Query("SELECT COUNT(l) FROM LoginHistory l WHERE l.loginTime >= ?1")
    long countLoginsAfter(Instant time);
}
