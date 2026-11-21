package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.riesgo.entity.CalificacionRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoResultado;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.TipoAjuste;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioSpecifications;
import com.polygraph.erp.shared.enums.Cumplimiento;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoTiempo;
import com.polygraph.erp.shared.enums.PastaEstado;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import com.polygraph.erp.shared.estados.BucketTiempo;
import com.polygraph.erp.shared.estados.PeriodoEstadistica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PB-05 contra una base real (H2): los filtros nuevos de Solicitudes (grupo de tiempo, varios estados, estudio, responsable, nivel) y las
 * estadísticas por periodo del cliente y del gestor. Hoy = 10 de noviembre de 2026 (hora de Colombia).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("PB-05 — filtros de Solicitudes y estadísticas por periodo")
class PB05ListasTest {

    @Autowired private ServicioRepository servicioRepository;
    @Autowired private jakarta.persistence.EntityManager em;

    private static final Long GESTOR = 100L;
    private static final Long OTRO_GESTOR = 200L;
    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);

    private EstadisticasEstudiosService estadisticas;
    private Cliente cliente, ajeno;
    private Proceso avanzado, antecedentes;
    private Usuario analista, programador;
    private TipoProgreso tipoAnalisis, tipoCita;
    private Servicio vencida, venceHoy, venceManana, aTiempo, aTiempoPorVencer, enPausa, emitidaBaja, emitidaAltaCritica, cancelada, deOtro;

    @BeforeEach
    void setUp() {
        estadisticas = new EstadisticasEstudiosService(Clock.fixed(Instant.parse("2026-11-10T17:00:00Z"), ZoneId.of("America/Bogota")));
        ReflectionTestUtils.setField(estadisticas, "em", em);

        ClasificacionProceso clasificacion = persistir(ClasificacionProceso.builder().codigo("SEG").nombre("Seguridad").activo(true).build());
        avanzado = persistir(Proceso.builder().nombreProceso("Avanzado").clasificacion(clasificacion).aplicaPrecioCiudad(false).build());
        antecedentes = persistir(Proceso.builder().nombreProceso("Antecedentes").clasificacion(clasificacion).aplicaPrecioCiudad(false).build());
        tipoAnalisis = persistir(TipoProgreso.builder().nombreProgreso("Antecedentes").rolResponsable(Rol.ANALISTA_INTERNO).requiereDocumento(false).build());
        tipoCita = persistir(TipoProgreso.builder().nombreProgreso("Poligrafía").rolResponsable(Rol.POLIGRAFISTA).requiereDocumento(false).build());
        analista = usuario("analista@pb05.test", Rol.ANALISTA_INTERNO);
        programador = usuario("prog@pb05.test", Rol.PROGRAMADOR);

        cliente = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA).razonSocial("Cliente PB05")
                .emailPrincipal("pb05@test.com").idGestor(GESTOR).build());
        ajeno = persistir(Cliente.builder().tipoCliente(TipoCliente.PREPAGO).tipoPersona(TipoPersona.JURIDICA).razonSocial("Ajeno")
                .emailPrincipal("ajeno@test.com").idGestor(OTRO_GESTOR).build());

        vencida = servicio(cliente, avanzado, 1, EstadoServicio.EN_EJECUCION, EstadoTiempo.VENCIDA, -2, LocalDate.of(2026, 11, 2));
        sp(vencida, tipoAnalisis, EstadoSubproceso.EN_EJECUCION, analista, null);
        venceHoy = servicio(cliente, avanzado, 2, EstadoServicio.EN_EJECUCION, EstadoTiempo.POR_VENCER, 0, LocalDate.of(2026, 11, 5));
        sp(venceHoy, tipoAnalisis, EstadoSubproceso.FINALIZADO, analista, null);
        venceManana = servicio(cliente, antecedentes, 3, EstadoServicio.EN_EJECUCION, EstadoTiempo.POR_VENCER, 1, LocalDate.of(2026, 11, 5));
        sp(venceManana, tipoAnalisis, EstadoSubproceso.PENDIENTE, null, null);
        aTiempo = servicio(cliente, antecedentes, 4, EstadoServicio.EN_EJECUCION, EstadoTiempo.A_TIEMPO, 5, LocalDate.of(2026, 11, 6));
        sp(aTiempo, tipoAnalisis, EstadoSubproceso.EN_EJECUCION, analista, null);
        aTiempoPorVencer = servicio(cliente, antecedentes, 5, EstadoServicio.EN_EJECUCION, EstadoTiempo.POR_VENCER, 3, LocalDate.of(2026, 11, 6));   // por vencer pero con 3 días: «A tiempo»
        sp(aTiempoPorVencer, tipoAnalisis, EstadoSubproceso.EN_EJECUCION, analista, null);
        enPausa = servicio(cliente, avanzado, 6, EstadoServicio.PENDIENTE, EstadoTiempo.EN_PAUSA, null, LocalDate.of(2026, 11, 6));
        sp(enPausa, tipoCita, EstadoSubproceso.PROGRAMANDO, null, programador);
        sp(enPausa, tipoAnalisis, EstadoSubproceso.REPROGRAMADO, analista, null);

        emitidaBaja = servicio(cliente, antecedentes, 7, EstadoServicio.PUBLICADO, EstadoTiempo.ENTREGADA_A_TIEMPO, null, HOY);
        emitidaBaja.setPastaEstado(PastaEstado.EMITIDA);
        emitidaBaja.setEmitidoEn(HOY.atTime(9, 0));
        emitidaBaja.setCumplimiento(Cumplimiento.A_TIEMPO);
        sp(emitidaBaja, tipoAnalisis, EstadoSubproceso.PUBLICADO, analista, null);
        calificacion(emitidaBaja, NivelRiesgo.BAJO, TipoAjuste.NINGUNO);

        emitidaAltaCritica = servicio(cliente, avanzado, 8, EstadoServicio.PUBLICADO, EstadoTiempo.ENTREGADA_TARDE, null, LocalDate.of(2026, 8, 3));
        emitidaAltaCritica.setPastaEstado(PastaEstado.EMITIDA);
        emitidaAltaCritica.setEmitidoEn(LocalDateTime.of(2026, 8, 20, 10, 0));
        emitidaAltaCritica.setCumplimiento(Cumplimiento.TARDE);
        sp(emitidaAltaCritica, tipoAnalisis, EstadoSubproceso.PUBLICADO, analista, null);
        calificacion(emitidaAltaCritica, NivelRiesgo.ALTO, TipoAjuste.CRITICO);

        cancelada = servicio(cliente, antecedentes, 9, EstadoServicio.CANCELADO, null, null, LocalDate.of(2026, 11, 3));
        sp(cancelada, tipoAnalisis, EstadoSubproceso.CANCELADO, null, null);

        deOtro = servicio(ajeno, antecedentes, 10, EstadoServicio.EN_EJECUCION, EstadoTiempo.VENCIDA, -1, LocalDate.of(2026, 11, 3));
        sp(deOtro, tipoAnalisis, EstadoSubproceso.EN_EJECUCION, analista, null);
        em.flush();
        em.clear();
    }

    private long contar(Specification<Servicio> spec) {
        return servicioRepository.count(Specification.where(ServicioSpecifications.delGestor(GESTOR)).and(spec));
    }

    // ───────────────────────── grupos de tiempo ─────────────────────────

    @Test
    @DisplayName("Los chips de tiempo cuentan lo mismo que filtra la lista; Vence hoy/mañana salen de POR_VENCER y los días hábiles")
    void gruposDeTiempo() {
        assertThat(contar(ServicioSpecifications.conBucket(BucketTiempo.VENCIDAS))).isEqualTo(1);
        assertThat(contar(ServicioSpecifications.conBucket(BucketTiempo.VENCE_HOY))).isEqualTo(1);
        assertThat(contar(ServicioSpecifications.conBucket(BucketTiempo.VENCE_MANANA))).isEqualTo(1);
        assertThat(contar(ServicioSpecifications.conBucket(BucketTiempo.A_TIEMPO))).isEqualTo(2);   // A_TIEMPO + POR_VENCER con 3 días
        assertThat(contar(ServicioSpecifications.conBucket(BucketTiempo.EN_PAUSA))).isEqualTo(1);
    }

    @Test
    @DisplayName("La especificación y BucketTiempo.de dicen lo mismo para cada solicitud")
    void especificacionIgualQueLaRegla() {
        for (Servicio s : List.of(vencida, venceHoy, venceManana, aTiempo, aTiempoPorVencer, enPausa, emitidaBaja, emitidaAltaCritica, cancelada)) {
            BucketTiempo esperado = BucketTiempo.de(s.getEstadoTiempo(), s.getDiasRestantesHabiles());
            for (BucketTiempo b : BucketTiempo.values()) {
                boolean enLista = servicioRepository.findAll(Specification.where(ServicioSpecifications.delGestor(GESTOR)).and(ServicioSpecifications.conBucket(b)))
                        .stream().anyMatch(x -> x.getIdServicio().equals(s.getIdServicio()));
                assertThat(enLista).as("#%d en %s", s.getIdServicio(), b).isEqualTo(b == esperado);
            }
        }
    }

    // ───────────────────────── filtros combinables ─────────────────────────

    @Test
    @DisplayName("«Seguir la programación» = Programando + Reprogramado (un servicio cuenta una vez)")
    void variosEstados() {
        var estados = ServicioSpecifications.estadosDeFiltro("PROGRAMANDO,REPROGRAMADO");
        assertThat(estados).containsExactly(EstadoSubproceso.PROGRAMANDO, EstadoSubproceso.REPROGRAMADO);
        assertThat(contar(ServicioSpecifications.conSubprocesoEnAlguno(estados))).isEqualTo(1);   // #6 tiene los dos, cuenta una vez
        assertThat(contar(ServicioSpecifications.conSubprocesoEnAlguno(ServicioSpecifications.estadosDeFiltro("FINALIZADO")))).isEqualTo(1);
        assertThat(ServicioSpecifications.estadosDeFiltro(null)).isEmpty();
    }

    @Test
    @DisplayName("Cliente, estudio y responsable se combinan con el grupo de tiempo y las tarjetas de estado; nunca muestran lo de otro gestor")
    void filtrosCombinados() {
        assertThat(contar(ServicioSpecifications.delProceso(avanzado.getIdProceso()))).isEqualTo(4);          // #1, #2, #6, #8
        assertThat(contar(ServicioSpecifications.delProceso(antecedentes.getIdProceso()))).isEqualTo(5);      // #3, #4, #5, #7, #9
        // responsable: ejecutor O programador
        assertThat(contar(ServicioSpecifications.conResponsable(programador.getIdUsuario()))).isEqualTo(1);   // #6
        assertThat(contar(ServicioSpecifications.conResponsable(analista.getIdUsuario()))).isEqualTo(7);      // todos los de análisis de este gestor con analista
        // combinados: estudio Avanzado + vencidas = #1; Avanzado + A tiempo = ninguno
        assertThat(contar(Specification.where(ServicioSpecifications.delProceso(avanzado.getIdProceso())).and(ServicioSpecifications.conBucket(BucketTiempo.VENCIDAS)))).isEqualTo(1);
        assertThat(contar(Specification.where(ServicioSpecifications.delProceso(avanzado.getIdProceso())).and(ServicioSpecifications.conBucket(BucketTiempo.A_TIEMPO)))).isZero();
        // el cliente de otro gestor no entra aunque se pida
        assertThat(contar(ServicioSpecifications.delCliente(ajeno.getIdCliente()))).isZero();
        // sin filtros (null) no cambia nada
        assertThat(contar(ServicioSpecifications.conResponsable(null))).isEqualTo(9);
        assertThat(contar(ServicioSpecifications.delProceso(null))).isEqualTo(9);
    }

    @Test
    @DisplayName("El nivel que vale filtra solo estudios con la pasta emitida (Mis solicitudes desde los anillos)")
    void nivelEmitido() {
        var bajo = servicioRepository.findAll(ServicioSpecifications.conNivelEmitido(NivelRiesgo.BAJO));
        var alto = servicioRepository.findAll(ServicioSpecifications.conNivelEmitido(NivelRiesgo.ALTO));
        assertThat(bajo).extracting(Servicio::getIdServicio).containsExactly(emitidaBaja.getIdServicio());
        assertThat(alto).extracting(Servicio::getIdServicio).containsExactly(emitidaAltaCritica.getIdServicio());
        assertThat(servicioRepository.findAll(ServicioSpecifications.conNivelEmitido(NivelRiesgo.MEDIO))).isEmpty();
        assertThat(servicioRepository.findAll(Specification.where(ServicioSpecifications.conNivelEmitido(NivelRiesgo.ALTO))
                .and(ServicioSpecifications.solicitadaDesde(HOY)))).isEmpty();   // el alto se solicitó en agosto
    }

    // ───────────────────────── estadísticas del cliente ─────────────────────────

    @Test
    @DisplayName("Cliente, último año: solicitados sin canceladas, con resultado emitido, a tiempo y anillos que suman 100")
    void estadisticasClienteAnio() {
        var e = estadisticas.delCliente(cliente.getIdCliente(), PeriodoEstadistica.ANIO);
        assertThat(e.solicitados()).isEqualTo(8);        // 9 menos la cancelada
        assertThat(e.emitidos()).isEqualTo(2);
        assertThat(e.enProceso()).isEqualTo(6);
        assertThat(e.aTiempo()).isEqualTo(1);
        assertThat(e.riesgo().bajo()).isEqualTo(1);
        assertThat(e.riesgo().alto()).isEqualTo(1);
        assertThat(e.riesgo().pctBajo() + e.riesgo().pctMedio() + e.riesgo().pctAlto()).isEqualTo(100);
        assertThat(e.desde()).isEqualTo(LocalDate.of(2025, 11, 11));
        assertThat(e.hasta()).isEqualTo(HOY);
    }

    @Test
    @DisplayName("Cliente: al pasar a «Hoy» cambian indicadores y anillos; un periodo sin resultados da anillos en cero")
    void estadisticasClientePeriodos() {
        var hoy = estadisticas.delCliente(cliente.getIdCliente(), PeriodoEstadistica.HOY);
        assertThat(hoy.solicitados()).isEqualTo(1);
        assertThat(hoy.emitidos()).isEqualTo(1);
        assertThat(hoy.riesgo().pctBajo()).isEqualTo(100);
        var semana = estadisticas.delCliente(cliente.getIdCliente(), PeriodoEstadistica.SEMANA);   // 4 al 10 de noviembre
        assertThat(semana.solicitados()).isEqualTo(6);     // #2 a #7: las solicitadas del 4 al 10 de noviembre (la #1 es del 2 y la #8 de agosto)
        assertThat(semana.riesgo().total()).isEqualTo(1);
        assertThat(estadisticas.delCliente(ajeno.getIdCliente(), PeriodoEstadistica.TOTAL).emitidos()).isZero();
        assertThat(estadisticas.delCliente(ajeno.getIdCliente(), PeriodoEstadistica.TOTAL).riesgo().total()).isZero();
    }

    // ───────────────────────── riesgo del gestor ─────────────────────────

    @Test
    @DisplayName("Gestor: estudios cerrados por fecha de emisión, con los de riesgo alto y los críticos; solo sus clientes")
    void riesgoDelGestor() {
        var mes = estadisticas.delGestor(GESTOR, PeriodoEstadistica.MES);
        assertThat(mes.cerrados()).isEqualTo(1);
        assertThat(mes.riesgoAlto()).isZero();
        assertThat(mes.conCritico()).isZero();
        var total = estadisticas.delGestor(GESTOR, PeriodoEstadistica.TOTAL);
        assertThat(total.cerrados()).isEqualTo(2);
        assertThat(total.riesgoAlto()).isEqualTo(1);
        assertThat(total.conCritico()).isEqualTo(1);
        assertThat(total.riesgo().pctBajo() + total.riesgo().pctMedio() + total.riesgo().pctAlto()).isEqualTo(100);
        assertThat(estadisticas.delGestor(OTRO_GESTOR, PeriodoEstadistica.TOTAL).cerrados()).isZero();
    }

    // ───────────────────────── apoyo ─────────────────────────

    private Servicio servicio(Cliente c, Proceso proceso, int n, EstadoServicio estado, EstadoTiempo tiempo, Integer dias, LocalDate solicitada) {
        Candidato cand = persistir(Candidato.builder().cedula("2000000" + n).tipoDocumento("CC").nombres("Eval" + n).apellidos("PB05").build());
        Usuario solicita = usuario("solicita" + n + "@pb05.test", Rol.GESTOR);
        Servicio s = persistir(Servicio.builder().cliente(c).candidato(cand).proceso(proceso).usuarioSolicita(solicita)
                .fechaSolicitud(solicitada).estado(estado).build());
        s.setEstadoTiempo(tiempo);
        s.setDiasRestantesHabiles(dias);
        return s;
    }

    private void sp(Servicio s, TipoProgreso tipo, EstadoSubproceso estado, Usuario responsable, Usuario programadorResponsable) {
        persistir(ServicioSubproceso.builder().servicio(s).tipoProgreso(tipo).estado(estado).usuarioAsignado(responsable).programadorResponsable(programadorResponsable).build());
    }

    private void calificacion(Servicio s, NivelRiesgo nivel, TipoAjuste ajuste) {
        persistir(CalificacionRiesgo.builder().servicio(s).vigente(true).estado(EstadoResultado.CALCULADO).puntajeFinal(new BigDecimal("40.0")).nivelFinal(nivel)
                .tipoAjuste(ajuste).requiereRevision(false).criticidad(CriticidadCargo.ESTANDAR).versionConfiguracion("test").detalleJson("{}")
                .fechaCalculo(LocalDateTime.now()).build());
    }

    private Usuario usuario(String email, Rol rol) {
        return persistir(Usuario.builder().nombre(email).email(email).password("x").rol(rol).activo(true).emailVerificado(true).build());
    }

    private <T> T persistir(T entidad) {
        em.persist(entidad);
        return entidad;
    }
}
