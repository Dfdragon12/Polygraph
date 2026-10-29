package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.estados.SemaforoEstados.EtiquetaPublicacion;
import com.polygraph.erp.shared.estados.SemaforoEstados.Fila;
import com.polygraph.erp.shared.estados.SemaforoEstados.Resultado;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.polygraph.erp.shared.enums.EstadoSubproceso.*;
import static org.junit.jupiter.api.Assertions.*;

/** Los seis servicios de prueba del cliente «QA Semáforo» (#40 a #45) de la especificación. */
class SemaforoEstadosTest {

    private static final List<Fila> QA = new ArrayList<>();

    static {
        // #40 Avanzado
        for (EstadoSubproceso e : List.of(PUBLICADO, EN_EJECUCION, PROGRAMANDO, PROGRAMANDO, PENDIENTE)) QA.add(new Fila(40, e));
        // #41 Antecedentes
        QA.add(new Fila(41, PUBLICADO));
        // #42 Básico Virtual
        for (EstadoSubproceso e : List.of(FINALIZADO, EN_EJECUCION, REPROGRAMADO)) QA.add(new Fila(42, e));
        // #43 Poligrafía Pre Empleo
        QA.add(new Fila(43, PENDIENTE));
        // #44 Poligrafía + Antecedentes
        for (EstadoSubproceso e : List.of(PUBLICADO, REPROGRAMADO)) QA.add(new Fila(44, e));
        // #45 Antecedentes cancelado
        QA.add(new Fila(45, CANCELADO));
    }

    private static List<Integer> servicios(Resultado r) {
        return EstadoSubproceso.enOrden().stream().map(e -> r.porEstado().get(e).servicios()).toList();
    }

    private static List<Integer> subprocesos(Resultado r) {
        return EstadoSubproceso.enOrden().stream().map(e -> r.porEstado().get(e).subprocesos()).toList();
    }

    @Test
    void losConteosDelClienteQa() {
        Resultado r = SemaforoEstados.contar(QA);
        // Pendiente · Programando · En ejecución · Reprogramado · Finalizado · Publicado · Cancelado
        assertEquals(List.of(2, 1, 2, 2, 1, 3, 1), servicios(r));
        assertEquals(List.of(2, 2, 2, 2, 1, 3, 1), subprocesos(r));
        assertEquals(6, r.totalServicios());
        assertEquals(13, subprocesos(r).stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void laSumaDeLosContadoresPuedeSuperarElTotal() {
        Resultado r = SemaforoEstados.contar(QA);
        int suma = servicios(r).stream().mapToInt(Integer::intValue).sum();
        assertEquals(12, suma);
        assertTrue(suma > r.totalServicios());
    }

    @Test
    void siempreVienenLosSieteEstadosEnOrden() {
        Resultado vacio = SemaforoEstados.contar(List.of());
        assertEquals(0, vacio.totalServicios());
        assertEquals(EstadoSubproceso.enOrden(), List.copyOf(vacio.porEstado().keySet()));
        assertTrue(vacio.porEstado().values().stream().allMatch(c -> c.servicios() == 0 && c.subprocesos() == 0));
    }

    @Test
    void elFiltroProgramandoDevuelveSoloLa40() {
        assertEquals(Set.of(40), SemaforoEstados.serviciosEn(PROGRAMANDO, QA));
    }

    @Test
    void elFiltroPublicadoDevuelveLa40LaLa41YLa44() {
        assertEquals(Set.of(40, 41, 44), SemaforoEstados.serviciosEn(PUBLICADO, QA));
    }

    @Test
    void unServicioApareceEnCadaEstadoDondeTengaUnSubproceso() {
        assertTrue(SemaforoEstados.serviciosEn(PENDIENTE, QA).contains(40));
        assertTrue(SemaforoEstados.serviciosEn(EN_EJECUCION, QA).contains(40));
        assertTrue(SemaforoEstados.serviciosEn(PUBLICADO, QA).contains(40));
        assertFalse(SemaforoEstados.serviciosEn(FINALIZADO, QA).contains(40));
    }

    @Test
    void avanceEsPublicadosSobreNoCancelados() {
        var a40 = SemaforoEstados.avance(List.of(PUBLICADO, EN_EJECUCION, PROGRAMANDO, PROGRAMANDO, PENDIENTE));
        assertEquals(1, a40.publicados());
        assertEquals(5, a40.vigentes());
        assertEquals(0.2, a40.fraccion(), 1e-9);
        // los cancelados no cuentan
        var conCancelado = SemaforoEstados.avance(List.of(PUBLICADO, CANCELADO));
        assertEquals(1, conCancelado.vigentes());
        assertEquals(1.0, conCancelado.fraccion(), 1e-9);
        // sin vigentes no hay avance
        assertEquals(0, SemaforoEstados.avance(List.of(CANCELADO)).fraccion(), 1e-9);
        assertEquals(0, SemaforoEstados.avance(List.of()).vigentes());
    }

    @Test
    void etiquetaDePublicacion() {
        // #40: 1 de 5 publicados → parcial
        assertEquals(EtiquetaPublicacion.PUBLICADO_PARCIAL, SemaforoEstados.etiqueta(SemaforoEstados.avance(List.of(PUBLICADO, EN_EJECUCION, PROGRAMANDO, PROGRAMANDO, PENDIENTE))));
        // #44: 1 de 2 → parcial
        assertEquals(EtiquetaPublicacion.PUBLICADO_PARCIAL, SemaforoEstados.etiqueta(SemaforoEstados.avance(List.of(PUBLICADO, REPROGRAMADO))));
        // #41: todos los no cancelados → completo
        assertEquals(EtiquetaPublicacion.ESTUDIO_COMPLETO_PUBLICADO, SemaforoEstados.etiqueta(SemaforoEstados.avance(List.of(PUBLICADO))));
        // completo aunque haya un cancelado
        assertEquals(EtiquetaPublicacion.ESTUDIO_COMPLETO_PUBLICADO, SemaforoEstados.etiqueta(SemaforoEstados.avance(List.of(PUBLICADO, PUBLICADO, CANCELADO))));
        // ninguno publicado → sin etiqueta
        assertEquals(EtiquetaPublicacion.NINGUNA, SemaforoEstados.etiqueta(SemaforoEstados.avance(List.of(EN_EJECUCION, PENDIENTE))));
        assertEquals(EtiquetaPublicacion.NINGUNA, SemaforoEstados.etiqueta(SemaforoEstados.avance(List.of(CANCELADO))));
        assertEquals("Publicado parcial", EtiquetaPublicacion.PUBLICADO_PARCIAL.texto());
        assertEquals("Estudio completo publicado", EtiquetaPublicacion.ESTUDIO_COMPLETO_PUBLICADO.texto());
    }

    @Test
    void estadosQueVeCadaPerfil() {
        assertEquals(List.of(PROGRAMANDO, EN_EJECUCION, REPROGRAMADO, FINALIZADO), SemaforoEstados.estadosVisibles(Rol.PROGRAMADOR));
        assertEquals(List.of(EN_EJECUCION, FINALIZADO, PUBLICADO), SemaforoEstados.estadosVisibles(Rol.ANALISTA_INTERNO));
        assertEquals(List.of(EN_EJECUCION, REPROGRAMADO, FINALIZADO, PUBLICADO), SemaforoEstados.estadosVisibles(Rol.POLIGRAFISTA));
        assertEquals(SemaforoEstados.estadosVisibles(Rol.POLIGRAFISTA), SemaforoEstados.estadosVisibles(Rol.VISITADOR));
        for (Rol r : List.of(Rol.ADMIN_CLIENTE, Rol.ANALISTA_CLIENTE, Rol.GESTOR, Rol.ADMIN_POLYGRAPH)) {
            assertEquals(EstadoSubproceso.enOrden(), SemaforoEstados.estadosVisibles(r), r.name());
        }
    }

    @Test
    void soloLosPerfilesDeEjecucionCuentanSubprocesos() {
        for (Rol r : List.of(Rol.PROGRAMADOR, Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR)) assertTrue(SemaforoEstados.cuentaSubprocesos(r), r.name());
        for (Rol r : List.of(Rol.ADMIN_CLIENTE, Rol.ANALISTA_CLIENTE, Rol.GESTOR, Rol.ADMIN_POLYGRAPH)) assertFalse(SemaforoEstados.cuentaSubprocesos(r), r.name());
    }

    @Test
    void clienteGestorYAdministradorCoincidenSiVenLasMismasFilas() {
        // Los tres perfiles le pasan al cálculo las mismas filas (las del cliente QA): el resultado es idéntico.
        Resultado cliente = SemaforoEstados.contar(QA);
        Resultado gestor = SemaforoEstados.contar(List.copyOf(QA));
        Resultado admin = SemaforoEstados.contar(QA.stream().toList());
        assertEquals(cliente, gestor);
        assertEquals(cliente, admin);
    }
}
