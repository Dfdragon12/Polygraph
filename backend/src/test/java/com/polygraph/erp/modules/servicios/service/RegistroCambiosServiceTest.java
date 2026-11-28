package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.servicios.entity.RegistroCambioSubproceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.RegistroCambioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.Marca;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistroCambiosServiceTest {

    private final RegistroCambioSubprocesoRepository repo = mock(RegistroCambioSubprocesoRepository.class);
    // 2026-10-27 02:30 UTC = 26 de octubre, 9:30 p. m. en Colombia: el día de Colombia es el 26, no el 27
    private final Clock reloj = Clock.fixed(Instant.parse("2026-10-27T02:30:00Z"), ZoneOffset.UTC);
    private final RegistroCambiosService service = new RegistroCambiosService(repo, reloj);

    private ServicioSubproceso subproceso() {
        return ServicioSubproceso.builder().id(77L)
                .servicio(Servicio.builder().idServicio(39).build())
                .tipoProgreso(TipoProgreso.builder().idTipoProgreso(1).nombreProgreso("Antecedentes").build())
                .usuarioAsignado(Usuario.builder().idUsuario(5L).build())
                .estado(EstadoSubproceso.FINALIZADO).build();
    }

    private RegistroCambioSubproceso registrarYCapturar(Marca marca, String motivo, String texto) {
        when(repo.save(any(RegistroCambioSubproceso.class))).thenAnswer(i -> i.getArgument(0));
        service.registrar(subproceso(), EstadoSubproceso.FINALIZADO, EstadoSubproceso.EN_EJECUCION, marca, motivo, texto,
                Usuario.builder().idUsuario(9L).build());
        ArgumentCaptor<RegistroCambioSubproceso> c = ArgumentCaptor.forClass(RegistroCambioSubproceso.class);
        verify(repo).save(c.capture());
        return c.getValue();
    }

    @Test
    void guardaTodosLosDatosDelCambio() {
        RegistroCambioSubproceso f = registrarYCapturar(Marca.DEVUELTO, "Informe incompleto", "falta la sección 4");
        assertEquals(39, f.getIdServicio());
        assertEquals(77L, f.getIdServicioSubproceso());
        assertEquals("Antecedentes", f.getTipoSubproceso());
        assertEquals("FINALIZADO", f.getEstadoAnterior());
        assertEquals("EN_EJECUCION", f.getEstadoNuevo());
        assertEquals("DEVUELTO", f.getMarca());
        assertEquals("Informe incompleto", f.getMotivo());
        assertEquals("falta la sección 4", f.getTextoAdicional());
        assertEquals(5L, f.getIdResponsable());
        assertEquals(9L, f.getIdUsuario());
        assertEquals("EN_VIVO", f.getOrigen());
    }

    @Test
    void laFechaSeGuardaEnHoraDeColombiaSinImportarLaZonaDelServidor() {
        RegistroCambioSubproceso f = registrarYCapturar(null, null, null);
        assertEquals(LocalDateTime.of(2026, 10, 26, 21, 30), f.getFechaBogota());
        assertEquals(Instant.parse("2026-10-27T02:30:00Z"), f.getFechaHora().toInstant());
        assertEquals(ZoneOffset.ofHours(-5), f.getFechaHora().getOffset());
    }

    @Test
    void enBogotaCortaElDiaEnHoraDeColombia() {
        OffsetDateTime instante = OffsetDateTime.parse("2026-10-27T04:59:59Z");
        assertEquals(LocalDateTime.of(2026, 10, 26, 23, 59, 59), RegistroCambiosService.enBogota(instante));
        assertEquals(LocalDateTime.of(2026, 10, 27, 0, 0), RegistroCambiosService.enBogota(OffsetDateTime.parse("2026-10-27T05:00:00Z")));
    }

    @Test
    void sinMotivoNiTextoQuedanVacios() {
        RegistroCambioSubproceso f = registrarYCapturar(null, "  ", " ");
        assertNull(f.getMotivo());
        assertNull(f.getTextoAdicional());
        assertNull(f.getMarca());
    }

    @Test
    void unMotivoLargoSeRecorta() {
        RegistroCambioSubproceso f = registrarYCapturar(null, "x".repeat(500), null);
        assertEquals(400, f.getMotivo().length());
    }

    @Test
    void elRegistroNoTieneNingunMetodoParaCambiarSusDatos() {
        // Solo inserción: la entidad no expone setters y el repositorio no declara actualizar ni borrar a medida.
        Method[] metodos = RegistroCambioSubproceso.class.getDeclaredMethods();
        assertTrue(Arrays.stream(metodos).noneMatch(m -> m.getName().startsWith("set")));
        assertTrue(Arrays.stream(RegistroCambioSubprocesoRepository.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().startsWith("update") || m.getName().startsWith("delete")));
    }
}
