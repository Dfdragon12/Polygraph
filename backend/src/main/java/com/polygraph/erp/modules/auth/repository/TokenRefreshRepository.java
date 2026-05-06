package com.polygraph.erp.modules.auth.repository;

import com.polygraph.erp.modules.auth.entity.TokenRefresh;
import com.polygraph.erp.modules.auth.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TokenRefreshRepository extends JpaRepository<TokenRefresh, Long> {
    Optional<TokenRefresh> findByToken(String token);

    @Modifying
    @Query("UPDATE TokenRefresh t SET t.revocado = true WHERE t.usuario = :usuario AND t.revocado = false")
    void revocarTodosDeUsuario(Usuario usuario);
}
