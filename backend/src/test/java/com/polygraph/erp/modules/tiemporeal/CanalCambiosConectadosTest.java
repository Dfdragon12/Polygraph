package com.polygraph.erp.modules.tiemporeal;

import com.polygraph.erp.shared.enums.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Tiempo real — quién está conectado ahora (para el tablero del administrador)")
class CanalCambiosConectadosTest {

    @Test
    @DisplayName("Cuenta personas distintas por tipo: varias pestañas de la misma persona cuentan una vez")
    void cuentaPersonasDistintas() {
        CanalCambios canal = new CanalCambios();
        canal.suscribirUsuario(1L, Rol.ADMIN_POLYGRAPH, null);
        canal.suscribirUsuario(1L, Rol.ADMIN_POLYGRAPH, null); // segunda pestaña de la misma persona
        canal.suscribirUsuario(2L, Rol.GESTOR, null);
        canal.suscribirUsuario(3L, Rol.ADMIN_CLIENTE, 10);
        canal.suscribirUsuario(4L, Rol.ANALISTA_CLIENTE, 10);
        canal.suscribirCandidato(55);

        CanalCambios.Conectados c = canal.conectados();

        assertThat(c.internos()).isEqualTo(2);
        assertThat(c.clientes()).isEqualTo(2);
        assertThat(c.candidatos()).isEqualTo(1);
        assertThat(canal.conexionesAbiertas()).isEqualTo(6);
    }

    @Test
    @DisplayName("Sin conexiones todo es cero, y avisar a los administradores sin nadie conectado no falla")
    void sinNadie() {
        CanalCambios canal = new CanalCambios();

        assertThat(canal.conectados()).isEqualTo(new CanalCambios.Conectados(0, 0, 0));
        canal.publicarAdmin(List.of("documentos", "pagos")); // no debe lanzar
    }
}
