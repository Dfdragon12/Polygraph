package com.polygraph.erp.modules.servicios.service;

import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.TipoAjuste;
import com.polygraph.erp.shared.enums.Cumplimiento;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.PastaEstado;
import com.polygraph.erp.shared.estados.AnillosRiesgo;
import com.polygraph.erp.shared.estados.AnillosRiesgo.Anillos;
import com.polygraph.erp.shared.estados.PeriodoEstadistica;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Estadísticas de estudios por periodo (PB-05): UN solo cálculo para el inicio del cliente y el bloque «Riesgo detectado» del gestor. Solo cuenta
 * estudios con la pasta EMITIDA (el resultado que el cliente ya ve) y el nivel que vale (el que puso el gestor o, si no cambió nada, el del motor).
 * Hora de Colombia, reloj inyectable.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstadisticasEstudiosService {

    static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final int LOTE = 500;

    @PersistenceContext
    private EntityManager em;

    private final Clock reloj;

    /** Indicadores y anillos del cliente. solicitados no incluye las canceladas; enProceso = solicitados − con resultado emitido. */
    public record EstadisticasCliente(String periodo, LocalDate desde, LocalDate hasta, long solicitados, long emitidos, long enProceso, long aTiempo, Anillos riesgo) {}

    /** «Riesgo detectado» del gestor: estudios cerrados (pasta emitida) en el periodo, con cuántos fueron de riesgo alto y con hallazgo crítico. */
    public record RiesgoGestor(String periodo, LocalDate desde, LocalDate hasta, long cerrados, long riesgoAlto, long conCritico, Anillos riesgo) {}

    public EstadisticasCliente delCliente(Integer idCliente, PeriodoEstadistica periodo) {
        LocalDate hoy = LocalDate.now(reloj.withZone(ZONA));
        LocalDate desde = periodo.desde(hoy);
        String jpql = "SELECT s.idServicio, s.pastaEstado, s.cumplimiento FROM Servicio s WHERE s.cliente.idCliente = :cliente AND s.estado <> :cancelado"
                + (desde != null ? " AND s.fechaSolicitud >= :desde" : "");
        Query q = em.createQuery(jpql).setParameter("cliente", idCliente).setParameter("cancelado", EstadoServicio.CANCELADO);
        if (desde != null) q.setParameter("desde", desde);
        @SuppressWarnings("unchecked") List<Object[]> filas = q.getResultList();

        long emitidos = 0, aTiempo = 0;
        List<Integer> ids = new ArrayList<>();
        for (Object[] f : filas) {
            if (f[1] == PastaEstado.EMITIDA) {
                emitidos++;
                ids.add((Integer) f[0]);
                if (f[2] == Cumplimiento.A_TIEMPO) aTiempo++;
            }
        }
        Niveles n = niveles(ids);
        return new EstadisticasCliente(periodo.name(), desde, hoy, filas.size(), emitidos, filas.size() - emitidos, aTiempo, AnillosRiesgo.de(n.bajo, n.medio, n.alto));
    }

    public RiesgoGestor delGestor(Long idGestor, PeriodoEstadistica periodo) {
        LocalDate hoy = LocalDate.now(reloj.withZone(ZONA));
        LocalDate desde = periodo.desde(hoy);
        String jpql = "SELECT s.idServicio FROM Servicio s WHERE s.cliente.idGestor = :gestor AND s.pastaEstado = :emitida"
                + (desde != null ? " AND s.emitidoEn >= :desde" : "");
        Query q = em.createQuery(jpql).setParameter("gestor", idGestor).setParameter("emitida", PastaEstado.EMITIDA);
        if (desde != null) q.setParameter("desde", desde.atStartOfDay());
        @SuppressWarnings("unchecked") List<Integer> ids = q.getResultList();
        Niveles n = niveles(ids);
        return new RiesgoGestor(periodo.name(), desde, hoy, ids.size(), n.alto, n.criticos, AnillosRiesgo.de(n.bajo, n.medio, n.alto));
    }

    private static final class Niveles { long bajo, medio, alto, criticos; }

    /** El nivel que vale de cada estudio, en lotes (un estudio sin cálculo vigente no entra en los anillos). */
    private Niveles niveles(List<Integer> ids) {
        Niveles n = new Niveles();
        for (int i = 0; i < ids.size(); i += LOTE) {
            List<Integer> lote = ids.subList(i, Math.min(ids.size(), i + LOTE));
            @SuppressWarnings("unchecked")
            List<Object[]> filas = em.createQuery("SELECT c.nivelDecidido, c.nivelFinal, c.tipoAjuste FROM CalificacionRiesgo c WHERE c.vigente = true AND c.servicio.idServicio IN :ids")
                    .setParameter("ids", lote).getResultList();
            for (Object[] f : filas) {
                NivelRiesgo nivel = f[0] != null ? (NivelRiesgo) f[0] : (NivelRiesgo) f[1];
                if (nivel == null) continue;
                switch (nivel) {
                    case BAJO -> n.bajo++;
                    case MEDIO -> n.medio++;
                    case ALTO -> n.alto++;
                }
                if (f[2] == TipoAjuste.CRITICO) n.criticos++;
            }
        }
        return n;
    }
}
