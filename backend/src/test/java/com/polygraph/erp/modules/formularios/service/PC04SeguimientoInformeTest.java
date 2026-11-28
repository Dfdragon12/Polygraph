package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** PC-04: lo que el ejecutor ve de su informe después de enviarlo: publicado (con lo que el gestor ajustó) o devuelto (quién, cuándo, motivo y detalle). */
@DisplayName("PC-04 — publicado y devuelto, vistos por el ejecutor")
class PC04SeguimientoInformeTest {

    private JdbcTemplate jdbc;
    private UsuarioRepository usuarios;
    private SeguimientoInformeService servicio;
    private final LocalDateTime PUBLICADO_EL = LocalDateTime.of(2026, 11, 24, 15, 26);

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        usuarios = mock(UsuarioRepository.class);
        servicio = new SeguimientoInformeService(jdbc, usuarios);
        when(usuarios.findById(5L)).thenReturn(Optional.of(Usuario.builder().idUsuario(5L).nombre("Andrea").apellido("Clamente").build()));
    }

    private ServicioSubproceso publicado(String conclusion, String publicada, String nivel) {
        return ServicioSubproceso.builder().id(9L).estado(EstadoSubproceso.PUBLICADO).fechaPublicado(PUBLICADO_EL).publicadoPor(5L)
                .conclusion(conclusion).conclusionPublicada(publicada).nivelPublicado(nivel).build();
    }

    @Test
    @DisplayName("a) #42: el gestor reemplazó la conclusión al publicar → se ve quién y cuándo publicó, el texto anterior y el nuevo")
    void conclusionAjustada() {
        var p = servicio.publicacion(publicado("Con base en las consultas… Se emite concepto FAVORABLE para el cargo de Conductor de reparto.",
                "No se encontraron antecedentes judiciales ni registros en listas restrictivas.", "BAJO"));
        assertThat(p.publicadoPor()).isEqualTo("Andrea Clamente");
        assertThat(p.fecha()).isEqualTo(PUBLICADO_EL);
        assertThat(p.conclusionAjustada()).isTrue();
        assertThat(p.conclusionOriginal()).contains("FAVORABLE");
        assertThat(p.conclusionPublicada()).startsWith("No se encontraron");
        assertThat(p.nivelAjustado()).isFalse();
    }

    @Test
    @DisplayName("Si el gestor no cambió nada, no hay bloque de ajuste: ni texto anterior, ni nivel anterior")
    void sinAjustes() {
        var p = servicio.publicacion(publicado("No registra antecedentes.", " No registra antecedentes. ", "BAJO"));
        assertThat(p.conclusionAjustada()).isFalse();
        assertThat(p.conclusionOriginal()).isNull();
        assertThat(p.nivelAjustado()).isFalse();
        assertThat(p.nivelSugerido()).isNull();
        assertThat(p.nivelPublicado()).isEqualTo("BAJO");
    }

    @Test
    @DisplayName("Si cambió el nivel, se ve el sugerido, el publicado y el motivo que escribió el gestor")
    void nivelAjustado() {
        when(jdbc.queryForList(contains("CAMBIO_NIVEL"), anyLong())).thenReturn(List.of(Map.of("valor_anterior", "BAJO", "valor_nuevo", "MEDIO", "motivo", "Hubo una inconsistencia menor")));
        var p = servicio.publicacion(publicado("No registra antecedentes.", "No registra antecedentes.", "MEDIO"));
        assertThat(p.nivelAjustado()).isTrue();
        assertThat(p.nivelSugerido()).isEqualTo("BAJO");
        assertThat(p.nivelPublicado()).isEqualTo("MEDIO");
        assertThat(p.motivoNivel()).isEqualTo("Hubo una inconsistencia menor");
        assertThat(p.conclusionAjustada()).isFalse();
    }

    @Test
    @DisplayName("Un cambio de nivel viejo que ya no es el publicado (se volvió a publicar) no se muestra como ajuste")
    void nivelViejoNoCuenta() {
        when(jdbc.queryForList(contains("CAMBIO_NIVEL"), anyLong())).thenReturn(List.of(Map.of("valor_anterior", "BAJO", "valor_nuevo", "MEDIO", "motivo", "x")));
        assertThat(servicio.publicacion(publicado("a", "a", "BAJO")).nivelAjustado()).isFalse();
    }

    @Test
    @DisplayName("Solo un informe PUBLICADO tiene aviso de publicado")
    void soloPublicado() {
        ServicioSubproceso enCurso = ServicioSubproceso.builder().id(9L).estado(EstadoSubproceso.EN_EJECUCION).build();
        assertThat(servicio.publicacion(enCurso)).isNull();
        ServicioSubproceso finalizado = ServicioSubproceso.builder().id(9L).estado(EstadoSubproceso.FINALIZADO).build();
        assertThat(servicio.publicacion(finalizado)).isNull();
    }

    @Test
    @DisplayName("c) Devuelto: «Devuelto por … el … · Motivo … · detalle» solo mientras tenga la marca; al volver a enviarlo se limpia")
    void devolucion() {
        ServicioSubproceso sp = ServicioSubproceso.builder().id(11L).estado(EstadoSubproceso.EN_EJECUCION).devuelto(true).motivoDevolucion("Redacción o formato del informe")
                .detalleDevolucion("Prueba de auditoria (dato ficticio): revisar la redaccion de la conclusion.").devueltoPor(5L).fechaDevuelto(LocalDateTime.of(2026, 11, 25, 10, 0)).build();
        var d = servicio.devolucion(sp);
        assertThat(d.por()).isEqualTo("Andrea Clamente");
        assertThat(d.fecha()).isEqualTo(LocalDateTime.of(2026, 11, 25, 10, 0));
        assertThat(d.motivo()).isEqualTo("Redacción o formato del informe");
        assertThat(d.detalle()).contains("revisar la redaccion");

        sp.limpiarDevolucion();   // el ejecutor lo corrigió y lo volvió a enviar
        assertThat(sp.getDevuelto()).isFalse();
        assertThat(sp.getMotivoDevolucion()).isNull();
        assertThat(sp.getDetalleDevolucion()).isNull();
        assertThat(sp.getDevueltoPor()).isNull();
        assertThat(sp.getFechaDevuelto()).isNull();
        assertThat(servicio.devolucion(sp)).isNull();
    }
}
