package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.estados.AlertasAgenda.Fila;
import com.polygraph.erp.shared.estados.AlertasAgenda.Resultado;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.polygraph.erp.shared.enums.EstadoSubproceso.*;
import static org.junit.jupiter.api.Assertions.*;

class AlertasAgendaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 27, 10, 0);

    private static Fila fila(int servicio, long sp, EstadoSubproceso e, boolean resp, LocalDateTime fecha, Rol rol, EstadoTiempo t, Integer dias, boolean iniciado) {
        return new Fila(servicio, sp, e, resp, fecha, rol, t, dias, iniciado);
    }

    private List<Fila> datos() {
        return List.of(
                fila(1, 1, PENDIENTE, false, null, Rol.ANALISTA_INTERNO, EstadoTiempo.VENCIDA, 0, false),                              // sin asignar, servicio vencido
                fila(1, 2, EN_EJECUCION, true, null, Rol.ANALISTA_INTERNO, EstadoTiempo.VENCIDA, 0, false),                            // mismo servicio vencido
                fila(2, 3, PROGRAMANDO, false, null, Rol.POLIGRAFISTA, EstadoTiempo.POR_VENCER, 0, false),                              // sin asignar, vence hoy
                fila(3, 4, EN_EJECUCION, true, AHORA.withHour(15), Rol.VISITADOR, EstadoTiempo.A_TIEMPO, 5, false),                    // cita de hoy
                fila(4, 5, EN_EJECUCION, true, AHORA.minusHours(2), Rol.POLIGRAFISTA, EstadoTiempo.POR_VENCER, 1, false),              // por confirmar (pasaron >15 min)
                fila(5, 6, EN_EJECUCION, true, AHORA.minusDays(1), Rol.POLIGRAFISTA, EstadoTiempo.POR_VENCER, 0, true),                // ayer, ya iniciada: no cuenta como por confirmar ni de hoy
                fila(6, 7, PUBLICADO, true, null, Rol.ANALISTA_INTERNO, EstadoTiempo.ENTREGADA_A_TIEMPO, 0, true));
    }

    @Test
    void elGestorVeTodasLasAlertas() {
        Resultado r = AlertasAgenda.calcular(Rol.GESTOR, datos(), 3, AHORA);
        assertEquals(2, r.sinAsignar());           // el pendiente sin responsable y el programando sin profesional
        assertEquals(3, r.novedadesAbiertas());
        assertEquals(2, r.citasDeHoy());           // la de las 15:00 y la de hace 2 horas (también es de hoy)
        assertEquals(1, r.vencidas());             // el servicio 1, aunque tenga dos subprocesos
        assertEquals(2, r.paraHoy());              // los servicios 2 y 5 vencen hoy (0 días hábiles); el 4 vence mañana
    }

    @Test
    void vencidasYParaHoyCuentanServiciosNoSubprocesos() {
        Resultado r = AlertasAgenda.calcular(Rol.GESTOR, datos(), 0, AHORA);
        assertEquals(1, r.vencidas());
        // Para hoy: servicio 2 (0 días) y servicio 5 (0 días); el 4 tiene 1 día
        assertEquals(2, r.paraHoy());
    }

    @Test
    void porConfirmarSonLasCitasVencidasSinInformeIniciado() {
        Resultado r = AlertasAgenda.calcular(Rol.PROGRAMADOR, datos(), 0, AHORA);
        assertEquals(1, r.porConfirmar());
        // dentro de la tolerancia todavía no
        Resultado dentro = AlertasAgenda.calcular(Rol.PROGRAMADOR, List.of(fila(4, 5, EN_EJECUCION, true, AHORA.minusMinutes(10), Rol.POLIGRAFISTA, null, null, false)), 0, AHORA);
        assertEquals(0, dentro.porConfirmar());
    }

    @Test
    void cadaPerfilVeSoloLasAlertasQueLeCorresponden() {
        Resultado cliente = AlertasAgenda.calcular(Rol.ADMIN_CLIENTE, datos(), 9, AHORA);
        assertNull(cliente.sinAsignar());
        assertNull(cliente.novedadesAbiertas());
        assertNull(cliente.citasDeHoy());
        assertNull(cliente.porConfirmar());
        assertNotNull(cliente.vencidas());
        assertNotNull(cliente.paraHoy());

        Resultado analista = AlertasAgenda.calcular(Rol.ANALISTA_INTERNO, datos(), 2, AHORA);
        assertNull(analista.sinAsignar());
        assertNull(analista.citasDeHoy());
        assertNull(analista.porConfirmar());
        assertEquals(2, analista.novedadesAbiertas());
        assertNotNull(analista.vencidas());

        Resultado polig = AlertasAgenda.calcular(Rol.POLIGRAFISTA, datos(), 0, AHORA);
        assertNull(polig.sinAsignar());
        assertNotNull(polig.citasDeHoy());
        assertNotNull(polig.porConfirmar());

        Resultado programador = AlertasAgenda.calcular(Rol.PROGRAMADOR, datos(), 0, AHORA);
        assertNotNull(programador.sinAsignar());
    }

    @Test
    void sinFilasTodoEsCeroSalvoLoQueNoAplica() {
        Resultado r = AlertasAgenda.calcular(Rol.GESTOR, List.of(), 0, AHORA);
        assertEquals(0, r.sinAsignar());
        assertEquals(0, r.vencidas());
        assertEquals(0, r.porConfirmar());
        assertTrue(AlertasAgenda.aplicables(Rol.EVALUADO).isEmpty());
    }
}
