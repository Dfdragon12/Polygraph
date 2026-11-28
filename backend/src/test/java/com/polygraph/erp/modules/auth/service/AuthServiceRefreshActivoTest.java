package com.polygraph.erp.modules.auth.service;

import com.polygraph.erp.modules.auth.dto.RefreshRequest;
import com.polygraph.erp.modules.auth.entity.TokenRefresh;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.TokenRefreshRepository;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Refresh de sesión — un usuario desactivado no renueva su sesión")
class AuthServiceRefreshActivoTest {

    @Mock private TokenRefreshRepository tokenRefreshRepository;

    @InjectMocks
    private AuthService service;

    @Test
    @DisplayName("Refresh token vigente de un usuario inactivo → 401")
    void inactivoNoRenueva() {
        Usuario inactivo = Usuario.builder().idUsuario(1L).email("baja@test.com").rol(Rol.GESTOR).activo(false).build();
        TokenRefresh token = TokenRefresh.builder().usuario(inactivo).token("r").revocado(false)
                .fechaExpiracion(LocalDateTime.now().plusDays(3)).build();
        when(tokenRefreshRepository.findByToken("r")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh(new RefreshRequest("r")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
