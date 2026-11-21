package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.servicios.entity.PrecioCiudadProceso;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.PrecioCiudadProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.tienda.entity.PrecioCiudadArchivado;
import com.polygraph.erp.modules.tienda.repository.LimpiezaCatalogoAuditoriaRepository;
import com.polygraph.erp.modules.tienda.repository.PrecioCiudadArchivadoRepository;
import com.polygraph.erp.modules.tienda.service.LimpiezaCatalogoService.Plan;
import com.polygraph.erp.shared.enums.NivelCiudad;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TP-10: limpieza del catálogo: solo con confirmación, archiva sin borrar, no inventa recargos, alinea días con los textos y solo reporta lo demás. */
class TiendaTp10Test {

    private ProcesoRepository procesos;
    private PrecioCiudadProcesoRepository precios;
    private PrecioCiudadArchivadoRepository archivo;
    private LimpiezaCatalogoAuditoriaRepository auditoria;
    private LimpiezaCatalogoService servicio;
    private final List<Proceso> catalogo = new ArrayList<>();

    @BeforeEach
    void setUp() {
        procesos = mock(ProcesoRepository.class);
        precios = mock(PrecioCiudadProcesoRepository.class);
        archivo = mock(PrecioCiudadArchivadoRepository.class);
        auditoria = mock(LimpiezaCatalogoAuditoriaRepository.class);
        when(procesos.findAll()).thenReturn(catalogo);
        servicio = new LimpiezaCatalogoService(procesos, precios, archivo, auditoria, Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneOffset.UTC));
    }

    private Proceso proceso(int id, String nombre, boolean aplicaCiudad, int dias, String valor) {
        Proceso p = Proceso.builder().idProceso(id).nombreProceso(nombre).aplicaPrecioCiudad(aplicaCiudad).diasHabilesEntrega(dias).activo(true)
                .valor(valor == null ? null : new BigDecimal(valor)).tituloComercial("Título").frasePrincipal("Frase").descripcionCorta("Qué es").build();
        catalogo.add(p);
        return p;
    }

    private void recargos(Proceso p, String intermedia, String municipio) {
        List<PrecioCiudadProceso> filas = new ArrayList<>();
        if (intermedia != null) filas.add(PrecioCiudadProceso.builder().proceso(p).nivelCiudad(NivelCiudad.INTERMEDIA).valor(new BigDecimal(intermedia)).build());
        if (municipio != null) filas.add(PrecioCiudadProceso.builder().proceso(p).nivelCiudad(NivelCiudad.MUNICIPIO_SECUNDARIO).valor(new BigDecimal(municipio)).build());
        when(precios.findByProceso_IdProcesoOrderByNivelCiudadAsc(p.getIdProceso())).thenReturn(filas);
    }

    @Test
    @DisplayName("Sin escribir «CONFIRMO» no se aplica nada")
    void sinConfirmacion() {
        var ant = proceso(1, "Antecedentes", true, 1, "40000");
        recargos(ant, "5000", "9000");
        assertThatThrownBy(() -> servicio.aplicar(null, null, "admin@x.co")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> servicio.aplicar("si", null, "admin@x.co")).isInstanceOf(ApiException.class);
        assertThat(ant.getAplicaPrecioCiudad()).isTrue();
        verify(procesos, never()).save(any());
        verify(archivo, never()).save(any());
    }

    @Test
    @DisplayName("Simular muestra lo que se haría y no cambia nada")
    void simularNoCambia() {
        var ant = proceso(1, "Antecedentes", true, 1, "40000");
        recargos(ant, "5000", "9000");
        Plan plan = servicio.simular();
        assertThat(plan.acciones()).extracting(LimpiezaCatalogoService.Accion::paso).contains(LimpiezaCatalogoService.ARCHIVAR);
        assertThat(ant.getAplicaPrecioCiudad()).isTrue();
        verify(procesos, never()).save(any());
        verify(precios, never()).deleteByProceso_IdProceso(any());
    }

    @Test
    @DisplayName("Antecedentes y Análisis Financiero: sus recargos por ciudad se ARCHIVAN (no se borran sin copia) y se apaga la casilla")
    void archivaPreciosQueNoAplican() {
        var ant = proceso(1, "Antecedentes", true, 1, "40000");
        var fin = proceso(2, "Análisis Financiero", true, 5, "90000");
        recargos(ant, "5000", "9000");
        recargos(fin, "7000", null);

        servicio.aplicar("CONFIRMO", null, "admin@x.co");

        verify(archivo, times(3)).save(any(PrecioCiudadArchivado.class));
        verify(precios).deleteByProceso_IdProceso(1);
        verify(precios).deleteByProceso_IdProceso(2);
        assertThat(ant.getAplicaPrecioCiudad()).isFalse();
        assertThat(fin.getAplicaPrecioCiudad()).isFalse();
        verify(auditoria, times(2)).save(any());
    }

    @Test
    @DisplayName("Pre Empleo con recargos importados: se enciende «aplica precio por ciudad». Avanzado sin recargos: se advierte y NO se toca (no se inventan valores)")
    void corrigeSoloLoImportado() {
        var pre = proceso(3, "Poligrafia Pre Empleo", false, 2, "200000");
        var avz = proceso(4, "Avanzado", false, 5, "500000");
        recargos(pre, "15000", "25000");
        recargos(avz, null, null);

        var resultado = servicio.aplicar("CONFIRMO", null, "admin@x.co");

        assertThat(pre.getAplicaPrecioCiudad()).isTrue();
        assertThat(avz.getAplicaPrecioCiudad()).isFalse();
        assertThat(resultado.plan().advertencias()).anyMatch(a -> a.contains("Avanzado") && a.contains("impórtalos"));
    }

    @Test
    @DisplayName("Los nombres se comparan sin tildes ni mayúsculas ni espacios alrededor del «+»")
    void nombresTolerantes() {
        var v = proceso(5, "POLIGRAFÍA+VISITA", false, 3, "300000");
        recargos(v, "20000", "30000");
        servicio.aplicar("CONFIRMO", null, "admin@x.co");
        assertThat(v.getAplicaPrecioCiudad()).isTrue();
        assertThat(LimpiezaCatalogoService.normalizar("Poligrafía  +  Antecedentes")).isEqualTo("poligrafia + antecedentes");
    }

    @Test
    @DisplayName("Días hábiles: «12 horas» = 1 día (Antecedentes ya con 1: sin cambio); «3 días hábiles» alinea la ficha")
    void unificaDias() {
        assertThat(LimpiezaCatalogoService.diasDelTexto("Resultado en 12 horas")).isEqualTo(1);
        assertThat(LimpiezaCatalogoService.diasDelTexto("Informe en 3 días hábiles")).isEqualTo(3);
        assertThat(LimpiezaCatalogoService.diasDelTexto("Informe en 3 días hábiles", "o 12 horas")).isEqualTo(3);
        assertThat(LimpiezaCatalogoService.diasDelTexto("Sin plazo")).isNull();

        var ant = proceso(1, "Antecedentes", false, 1, "40000");
        ant.setFrasePrincipal("Resultado en 12 horas");
        var otro = proceso(6, "Otro", false, 1, "50000");
        otro.setDescripcionCorta("Entrega en 3 días hábiles");
        recargos(ant, null, null);
        recargos(otro, null, null);

        Plan plan = servicio.simular();
        assertThat(plan.acciones()).extracting(LimpiezaCatalogoService.Accion::idProceso).containsExactly(6);   // Antecedentes ya está unificado

        servicio.aplicar("CONFIRMO", Set.of(LimpiezaCatalogoService.DIAS), "admin@x.co");
        assertThat(otro.getDiasHabilesEntrega()).isEqualTo(3);
        assertThat(ant.getDiasHabilesEntrega()).isEqualTo(1);
    }

    @Test
    @DisplayName("Se puede aplicar solo algunos pasos")
    void soloPasosElegidos() {
        var ant = proceso(1, "Antecedentes", true, 1, "40000");
        var pre = proceso(3, "Poligrafia Pre Empleo", false, 2, "200000");
        recargos(ant, "5000", "9000");
        recargos(pre, "15000", "25000");
        servicio.aplicar("CONFIRMO", Set.of(LimpiezaCatalogoService.ACTIVAR), "admin@x.co");
        assertThat(pre.getAplicaPrecioCiudad()).isTrue();
        assertThat(ant.getAplicaPrecioCiudad()).isTrue();   // no se eligió archivar
    }

    @Test
    @DisplayName("Reporte: servicios activos sin precio base o sin textos comerciales; SOLO reporta, no oculta ni cambia nada")
    void soloReporta() {
        var sinPrecio = proceso(7, "Sin precio", false, 3, null);
        var sinTextos = proceso(8, "Sin textos", false, 3, "10000");
        sinTextos.setTituloComercial(null);
        sinTextos.setFrasePrincipal(" ");
        var inactivo = proceso(9, "Inactivo sin precio", false, 3, null);
        inactivo.setActivo(false);

        Plan plan = servicio.simular();

        assertThat(plan.sinPrecioBase()).extracting(LimpiezaCatalogoService.Hallazgo::idProceso).containsExactly(7);
        assertThat(plan.sinTextos()).extracting(LimpiezaCatalogoService.Hallazgo::idProceso).containsExactly(8);
        assertThat(plan.sinTextos().get(0).detalle()).contains("título comercial", "frase principal", "Sigue visible");
        verify(procesos, never()).save(any());
        assertThat(sinPrecio.getActivo()).isTrue();
        assertThat(sinTextos.getActivo()).isTrue();
    }

    @Test
    @DisplayName("Aplicar dos veces: la segunda no encuentra nada que hacer")
    void idempotente() {
        var ant = proceso(1, "Antecedentes", true, 1, "40000");
        recargos(ant, "5000", "9000");
        servicio.aplicar("CONFIRMO", null, "admin@x.co");
        when(precios.findByProceso_IdProcesoOrderByNivelCiudadAsc(1)).thenReturn(List.of());   // ya archivados
        assertThat(servicio.simular().acciones()).isEmpty();
    }
}
