package com.readenglish.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
}
