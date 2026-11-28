package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.Accion;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.AlDesistir;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.Contexto;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.Marca;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.Transicion;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.TransicionInvalida;
import com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.TransicionInvalida.Codigo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.polygraph.erp.shared.enums.EstadoSubproceso.*;
import static com.polygraph.erp.shared.estados.MaquinaEstadosSubproceso.Accion.*;
import static org.junit.jupiter.api.Assertions.*;

class MaquinaEstadosSubprocesoTest {

    private static final Contexto ANALISIS = Contexto.vacio().conResponsable(true);
    private static final Contexto CITA = Contexto.vacio().conEsCita(true);

    private static Transicion ok(Accion a, EstadoSubproceso desde, Rol quien, Contexto c) {
        return MaquinaEstadosSubproceso.evaluar(a, desde, quien, c);
    }

    private static Codigo rechazo(Accion a, EstadoSubproceso desde, Rol quien, Contexto c) {
        return assertThrows(TransicionInvalida.class, () -> MaquinaEstadosSubproceso.evaluar(a, desde, quien, c)).codigo();
    }

    // ───────────── cada transición válida pasa ─────────────

    @Test
    void enviarAlProgramadorSoloUnaCitaPendiente() {
        Transicion t = ok(ENVIAR_AL_PROGRAMADOR, PENDIENTE, Rol.GESTOR, CITA);
        assertEquals(PROGRAMANDO, t.hacia());
    }

    @Test
    void asignarUnAnalisisConResponsable() {
        assertEquals(EN_EJECUCION, ok(ASIGNAR, PENDIENTE, Rol.GESTOR, ANALISIS).hacia());
    }

    @Test
    void agendarUnaCitaConProfesionalDiaYHora() {
        Contexto c = CITA.conResponsable(true).conDiaYHora(true);
        assertEquals(EN_EJECUCION, ok(AGENDAR, PROGRAMANDO, Rol.PROGRAMADOR, c).hacia());
    }

    @Test
    void elResponsableEnviaElInforme() {
        Contexto c = ANALISIS.esElResponsable(true);
        for (Rol r : List.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR)) {
            assertEquals(FINALIZADO, ok(ENVIAR_INFORME, EN_EJECUCION, r, c).hacia());
        }
    }

    @Test
    void aprobarPublica() {
        assertEquals(PUBLICADO, ok(APROBAR, FINALIZADO, Rol.GESTOR, ANALISIS).hacia());
    }

    @Test
    void devolverVuelveAEjecucionConLaMarcaDevuelto() {
        Transicion t = ok(DEVOLVER, FINALIZADO, Rol.GESTOR, ANALISIS.conMotivo(true));
        assertEquals(EN_EJECUCION, t.hacia());
        assertEquals(Set.of(Marca.DEVUELTO), t.marcas());
    }

    @Test
    void citaNoRealizadaPasaAReprogramado() {
        for (Rol r : List.of(Rol.POLIGRAFISTA, Rol.VISITADOR)) {
            assertEquals(REPROGRAMADO, ok(CITA_NO_REALIZADA, EN_EJECUCION, r, CITA.esElResponsable(true).conMotivo(true)).hacia());
        }
        assertEquals(REPROGRAMADO, ok(CITA_NO_REALIZADA, EN_EJECUCION, Rol.PROGRAMADOR, CITA.conMotivo(true)).hacia());
    }

    @Test
    void reprogramarVuelveAEjecucionConLaMarcaReprogramada() {
        Transicion t = ok(REPROGRAMAR, REPROGRAMADO, Rol.PROGRAMADOR, CITA.conDiaYHora(true).conMotivo(true));
        assertEquals(EN_EJECUCION, t.hacia());
        assertEquals(Set.of(Marca.REPROGRAMADA), t.marcas());
    }

    @Test
    void reasignarDejaAsignadaNoRealizada() {
        Transicion t = ok(REASIGNAR, REPROGRAMADO, Rol.PROGRAMADOR, CITA.conResponsable(true).conDiaYHora(true).conMotivo(true));
        assertEquals(EN_EJECUCION, t.hacia());
        assertEquals(Set.of(Marca.ASIGNADA_NO_REALIZADA), t.marcas());
    }

    @Test
    void cancelarCualquieraNoIniciado() {
        for (EstadoSubproceso e : List.of(PENDIENTE, PROGRAMANDO, EN_EJECUCION, REPROGRAMADO)) {
            Transicion t = ok(CANCELAR, e, Rol.GESTOR, ANALISIS.conMotivo(true));
            assertEquals(CANCELADO, t.hacia());
            assertTrue(t.marcas().contains(Marca.CANCELADA));
        }
    }

    // ───────────── cada inválida se rechaza ─────────────

    @Test
    void ningunaAccionSirveDesdeUnEstadoQueNoEsElSuyo() {
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(ASIGNAR, EN_EJECUCION, Rol.GESTOR, ANALISIS));
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(ASIGNAR, PUBLICADO, Rol.GESTOR, ANALISIS));
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(AGENDAR, PENDIENTE, Rol.PROGRAMADOR, CITA.conResponsable(true).conDiaYHora(true)));
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(ENVIAR_INFORME, PENDIENTE, Rol.ADMIN_POLYGRAPH, ANALISIS));
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(APROBAR, EN_EJECUCION, Rol.GESTOR, ANALISIS));
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(APROBAR, PUBLICADO, Rol.GESTOR, ANALISIS));
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(DEVOLVER, PUBLICADO, Rol.GESTOR, ANALISIS.conMotivo(true)));
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(REPROGRAMAR, EN_EJECUCION, Rol.PROGRAMADOR, CITA.conDiaYHora(true).conMotivo(true)));
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(CITA_NO_REALIZADA, PENDIENTE, Rol.PROGRAMADOR, CITA.conMotivo(true)));
    }

    @Test
    void siempreHayUnaFormaDeLlegarAPublicadoYNingunaDeSalirDeCancelado() {
        for (Accion a : Accion.values()) {
            assertThrows(TransicionInvalida.class, () -> MaquinaEstadosSubproceso.evaluar(a, CANCELADO, Rol.ADMIN_POLYGRAPH,
                    CITA.conResponsable(true).conDiaYHora(true).conMotivo(true).esElResponsable(true)), a.name());
        }
    }

    @Test
    void soloLasCitasPasanPorElProgramador() {
        assertEquals(Codigo.CONDICION, rechazo(ENVIAR_AL_PROGRAMADOR, PENDIENTE, Rol.GESTOR, ANALISIS));
        assertEquals(Codigo.CONDICION, rechazo(ASIGNAR, PENDIENTE, Rol.GESTOR, CITA.conResponsable(true)));
        assertEquals(Codigo.CONDICION, rechazo(CITA_NO_REALIZADA, EN_EJECUCION, Rol.PROGRAMADOR, ANALISIS.conMotivo(true)));
    }

    @Test
    void faltanDatosParaAsignarOAgendar() {
        assertEquals(Codigo.CONDICION, rechazo(ASIGNAR, PENDIENTE, Rol.GESTOR, Contexto.vacio()));
        assertEquals(Codigo.CONDICION, rechazo(AGENDAR, PROGRAMANDO, Rol.PROGRAMADOR, CITA.conResponsable(true)));
        assertEquals(Codigo.CONDICION, rechazo(AGENDAR, PROGRAMANDO, Rol.PROGRAMADOR, CITA.conDiaYHora(true)));
    }

    @Test
    void loIniciadoNoSeCancela() {
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(CANCELAR, EN_EJECUCION, Rol.GESTOR, ANALISIS.iniciado(true).conMotivo(true)));
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(CANCELAR, FINALIZADO, Rol.GESTOR, ANALISIS.conMotivo(true)));
        assertEquals(Codigo.ESTADO_INVALIDO, rechazo(CANCELAR, PUBLICADO, Rol.GESTOR, ANALISIS.conMotivo(true)));
    }

    // ───────────── cada perfil sin permiso es rechazado ─────────────

    @Test
    void perfilesSinPermiso() {
        assertEquals(Codigo.SIN_PERMISO, rechazo(ASIGNAR, PENDIENTE, Rol.ANALISTA_INTERNO, ANALISIS));
        assertEquals(Codigo.SIN_PERMISO, rechazo(ASIGNAR, PENDIENTE, Rol.PROGRAMADOR, ANALISIS));
        assertEquals(Codigo.SIN_PERMISO, rechazo(ENVIAR_AL_PROGRAMADOR, PENDIENTE, Rol.PROGRAMADOR, CITA));
        assertEquals(Codigo.SIN_PERMISO, rechazo(AGENDAR, PROGRAMANDO, Rol.GESTOR, CITA.conResponsable(true).conDiaYHora(true)));
        assertEquals(Codigo.SIN_PERMISO, rechazo(APROBAR, FINALIZADO, Rol.ANALISTA_INTERNO, ANALISIS));
        assertEquals(Codigo.SIN_PERMISO, rechazo(APROBAR, FINALIZADO, Rol.ADMIN_CLIENTE, ANALISIS));
        assertEquals(Codigo.SIN_PERMISO, rechazo(DEVOLVER, FINALIZADO, Rol.POLIGRAFISTA, ANALISIS.conMotivo(true)));
        assertEquals(Codigo.SIN_PERMISO, rechazo(CANCELAR, PENDIENTE, Rol.ANALISTA_CLIENTE, ANALISIS.conMotivo(true)));
        assertEquals(Codigo.SIN_PERMISO, rechazo(CANCELAR, PENDIENTE, Rol.ANALISTA_INTERNO, ANALISIS.conMotivo(true)));
        assertEquals(Codigo.SIN_PERMISO, rechazo(REPROGRAMAR, REPROGRAMADO, Rol.GESTOR, CITA.conDiaYHora(true).conMotivo(true)));
    }

    @Test
    void soloElResponsableEnviaSuInforme() {
        assertEquals(Codigo.SIN_PERMISO, rechazo(ENVIAR_INFORME, EN_EJECUCION, Rol.ANALISTA_INTERNO, ANALISIS.esElResponsable(false)));
        assertEquals(Codigo.SIN_PERMISO, rechazo(ENVIAR_INFORME, EN_EJECUCION, Rol.GESTOR, ANALISIS.esElResponsable(true)));
    }

    @Test
    void elVisitadorOPoligrafistaAjenosNoRegistranLaCitaNoRealizada() {
        assertEquals(Codigo.SIN_PERMISO, rechazo(CITA_NO_REALIZADA, EN_EJECUCION, Rol.VISITADOR, CITA.esElResponsable(false).conMotivo(true)));
        assertEquals(Codigo.SIN_PERMISO, rechazo(CITA_NO_REALIZADA, EN_EJECUCION, Rol.ANALISTA_INTERNO, CITA.esElResponsable(true).conMotivo(true)));
    }

    @Test
    void elProgramadorSoloApruebaDevuelveYCancelaCitas() {
        assertEquals(PUBLICADO, ok(APROBAR, FINALIZADO, Rol.PROGRAMADOR, CITA).hacia());
        assertEquals(Codigo.SIN_PERMISO, rechazo(APROBAR, FINALIZADO, Rol.PROGRAMADOR, ANALISIS));
        assertEquals(CANCELADO, ok(CANCELAR, PROGRAMANDO, Rol.PROGRAMADOR, CITA.conMotivo(true)).hacia());
        assertEquals(Codigo.SIN_PERMISO, rechazo(CANCELAR, PENDIENTE, Rol.PROGRAMADOR, ANALISIS.conMotivo(true)));
    }

    @Test
    void elAdministradorPuedeTodoLoPermitidoPorEstado() {
        assertEquals(PUBLICADO, ok(APROBAR, FINALIZADO, Rol.ADMIN_POLYGRAPH, ANALISIS).hacia());
        assertEquals(EN_EJECUCION, ok(ASIGNAR, PENDIENTE, Rol.ADMIN_POLYGRAPH, ANALISIS).hacia());
    }

    // ───────────── el motivo es obligatorio donde corresponde ─────────────

    @Test
    void elMotivoEsObligatorio() {
        assertEquals(Codigo.FALTA_MOTIVO, rechazo(DEVOLVER, FINALIZADO, Rol.GESTOR, ANALISIS));
        assertEquals(Codigo.FALTA_MOTIVO, rechazo(CANCELAR, PENDIENTE, Rol.GESTOR, ANALISIS));
        assertEquals(Codigo.FALTA_MOTIVO, rechazo(CITA_NO_REALIZADA, EN_EJECUCION, Rol.PROGRAMADOR, CITA));
        assertEquals(Codigo.FALTA_MOTIVO, rechazo(REPROGRAMAR, REPROGRAMADO, Rol.PROGRAMADOR, CITA.conDiaYHora(true)));
        assertEquals(Codigo.FALTA_MOTIVO, rechazo(REASIGNAR, REPROGRAMADO, Rol.PROGRAMADOR, CITA.conResponsable(true).conDiaYHora(true)));
    }

    @Test
    void asignarAprobarYEnviarNoPidenMotivo() {
        assertDoesNotThrow(() -> ok(ASIGNAR, PENDIENTE, Rol.GESTOR, ANALISIS));
        assertDoesNotThrow(() -> ok(APROBAR, FINALIZADO, Rol.GESTOR, ANALISIS));
        assertDoesNotThrow(() -> ok(ENVIAR_INFORME, EN_EJECUCION, Rol.ANALISTA_INTERNO, ANALISIS.esElResponsable(true)));
    }

    @Test
    void elGestorTambienRegistraLaCitaNoRealizadaPeroSoloSiLaCitaNoSeHaIniciado() {
        assertEquals(REPROGRAMADO, ok(CITA_NO_REALIZADA, EN_EJECUCION, Rol.GESTOR, CITA.conMotivo(true)).hacia());
        assertEquals(Codigo.SIN_PERMISO, rechazo(CITA_NO_REALIZADA, EN_EJECUCION, Rol.GESTOR, ANALISIS.conMotivo(true)));   // un análisis no tiene cita
        assertEquals(Codigo.CONDICION, rechazo(CITA_NO_REALIZADA, EN_EJECUCION, Rol.PROGRAMADOR, CITA.iniciado(true).conMotivo(true)));
    }

    // ───────────── desistimiento del candidato ─────────────

    @Test
    void desistimientoSinNingunoIniciadoCancelaTodo() {
        assertEquals(AlDesistir.CANCELAR, MaquinaEstadosSubproceso.alDesistir(PENDIENTE, false));
        assertEquals(AlDesistir.CANCELAR, MaquinaEstadosSubproceso.alDesistir(PROGRAMANDO, false));
        assertEquals(AlDesistir.CANCELAR, MaquinaEstadosSubproceso.alDesistir(EN_EJECUCION, false));
        assertEquals(AlDesistir.CANCELAR, MaquinaEstadosSubproceso.alDesistir(REPROGRAMADO, false));
        assertFalse(MaquinaEstadosSubproceso.llevaMarcaDeDesistimiento(List.of()));
    }

    @Test
    void desistimientoConAlgunoIniciadoDejaSeguirLosIniciadosYCancelaElResto() {
        assertEquals(AlDesistir.CONTINUAR, MaquinaEstadosSubproceso.alDesistir(EN_EJECUCION, true));
        assertEquals(AlDesistir.CONTINUAR, MaquinaEstadosSubproceso.alDesistir(FINALIZADO, true));
        assertEquals(AlDesistir.CONTINUAR, MaquinaEstadosSubproceso.alDesistir(PUBLICADO, true));
        assertEquals(AlDesistir.CANCELAR, MaquinaEstadosSubproceso.alDesistir(PENDIENTE, false));
        assertTrue(MaquinaEstadosSubproceso.llevaMarcaDeDesistimiento(List.of(true)));
    }

    @Test
    void unFinalizadoOPublicadoCuentaComoIniciadoAunqueNoTengaLaBandera() {
        assertEquals(AlDesistir.CONTINUAR, MaquinaEstadosSubproceso.alDesistir(FINALIZADO, false));
        assertEquals(AlDesistir.CONTINUAR, MaquinaEstadosSubproceso.alDesistir(PUBLICADO, false));
    }

    // ───────────── botones que ofrece la pantalla ─────────────

    @Test
    void accionesQueOfreceElGestorSegunElEstado() {
        assertEquals(List.of(APROBAR, DEVOLVER), MaquinaEstadosSubproceso.disponibles(FINALIZADO, Rol.GESTOR, ANALISIS)
                .stream().filter(a -> a == APROBAR || a == DEVOLVER).toList());
        assertTrue(MaquinaEstadosSubproceso.disponibles(PUBLICADO, Rol.GESTOR, ANALISIS).isEmpty());
        assertTrue(MaquinaEstadosSubproceso.disponibles(PENDIENTE, Rol.GESTOR, ANALISIS).contains(CANCELAR));
    }
}
