package com.polygraph.erp.modules.pagos.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * TP-08: cuántas unidades del paquete NACIONAL se usaron en municipios secundarios, por mes y servicio. Una unidad nacional nunca paga recargo,
 * así que en un municipio el margen se come el desplazamiento: este reporte sirve para vigilar el factor del paquete nacional.
 */
@Service
public class ReporteNacionalesService {

    @PersistenceContext
    private EntityManager em;

    public record Fila(int anio, int mes, Integer idProceso, String servicio, long unidades) {}

    @Transactional(readOnly = true)
    public List<Fila> nacionalesEnMunicipios(LocalDate desde, LocalDate hasta) {
        List<Object[]> filas = em.createQuery(
                        "select year(s.fechaSolicitud), month(s.fechaSolicitud), p.idProceso, p.nombreProceso, count(s) "
                                + "from Servicio s join s.proceso p, Candidato c, Ciudad ci "
                                + "where s.candidato = c and ci.idCiudad = c.idCiudadResidencia "
                                + "and s.coberturaConsumo = 'NACIONAL' and ci.nivelCiudad = com.polygraph.erp.shared.enums.NivelCiudad.MUNICIPIO_SECUNDARIO "
                                + "and s.cobroDevueltoEn is null and s.fechaSolicitud >= :desde and s.fechaSolicitud <= :hasta "
                                + "group by year(s.fechaSolicitud), month(s.fechaSolicitud), p.idProceso, p.nombreProceso "
                                + "order by year(s.fechaSolicitud), month(s.fechaSolicitud), p.nombreProceso", Object[].class)
                .setParameter("desde", desde).setParameter("hasta", hasta).getResultList();
        return filas.stream().map(f -> new Fila(((Number) f[0]).intValue(), ((Number) f[1]).intValue(), (Integer) f[2], (String) f[3],
                ((Number) f[4]).longValue())).toList();
    }
}
