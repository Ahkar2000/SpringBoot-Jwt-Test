package com.security.jwttest.model.repo;

import com.security.jwttest.model.entity.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TokenRepo extends JpaRepository<Token, Long> {
    @Query(value = "SELECT * FROM tokens WHERE user_id = :userId AND revoked = false AND expired = false", nativeQuery = true)
    List<Token> findAllValidTokenByUser(@Param("userId") Long userId);

    Optional<Token> findByToken(String token);
}
