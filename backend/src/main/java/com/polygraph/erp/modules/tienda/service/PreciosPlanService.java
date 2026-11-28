package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.Analisis;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaPrecio;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.FilaPlan;
import com.polygraph.erp.modules.tienda.entity.PlanTienda;
import com.polygraph.erp.modules.tienda.repository.PlanTiendaRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Precios por plan de un servicio desde el administrador (TP-04). El plan de 1 unidad es el valor base y los demás son tramos
 * con esa cantidad mínima. Todo cálculo (descuentos, margen, alertas) sale de aquí; el navegador solo lo muestra.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreciosPlanService {

    static final Set<Integer> UNIDADES_PLAN = Set.of(1, 5, 20, 100, 200);

    private final ProcesoRepository procesoRepository;
    private final TramoPrecioProcesoRepository tramoRepository;
    private final PlanTiendaRepository planRepository;
    private final ParametrosTiendaService parametros;
    private final HistorialPreciosService historial;

    /** Analiza unos precios propuestos SIN guardar nada (vista previa y margen en vivo). */
    @Transactional(readOnly = true)
    public Analisis analizar(List<EntradaPrecio> precios, BigDecimal costoDirecto) {
        Map<Integer, BigDecimal> porUnidades = porUnidades(precios);
        return armar(porUnidades, costoDirecto);
    }

    /** Los precios guardados hoy del servicio, con su análisis. */
    @Transactional(readOnly = true)
    public Analisis obtener(Integer idProceso) {
        Proceso p = proceso(idProceso);
        Map<Integer, BigDecimal> m = new HashMap<>();
        if (p.getValor() != null) m.put(1, p.getValor());
        for (TramoPrecioProceso t : tramoRepository.findByProceso_IdProcesoOrderByCantidadMinimaAsc(idProceso)) {
            if (UNIDADES_PLAN.contains(t.getCantidadMinima())) m.put(t.getCantidadMinima(), t.getValorUnitario());
        }
        return armar(m, p.getCostoDirecto());
    }

    /**
     * Guarda los precios por plan. Rechaza (400) y NO guarda si algún plan no baja de precio frente al anterior.
     * Un plan sin precio deja de venderse para este servicio (se quita su tramo). Los tramos con otras cantidades no se tocan.
     */
    @Transactional
    public Analisis guardar(Integer idProceso, List<EntradaPrecio> precios) {
        Proceso p = proceso(idProceso);
        Map<Integer, BigDecimal> nuevos = porUnidades(precios);
        Set<Integer> enviados = new java.util.HashSet<>();
        for (EntradaPrecio e : precios == null ? List.<EntradaPrecio>of() : precios) enviados.add(e.unidades());
        if (nuevos.get(1) == null) {
            throw new ApiException("El precio del plan de 1 unidad es obligatorio: es el precio base del servicio.", HttpStatus.BAD_REQUEST);
        }
        List<PlanTienda> visibles = planRepository.findByVisibleTrueOrderByUnidadesAsc();
        CalculadoraPlanes.validarEscalera(visibles, nuevos.get(1), tramosSinBase(nuevos))
                .ifPresent(msg -> { throw new ApiException(msg, HttpStatus.BAD_REQUEST); });

        historial.registrar(idProceso, "Precio plan 1", p.getValor(), nuevos.get(1));
        p.setValor(nuevos.get(1));
        procesoRepository.save(p);

        Map<Integer, TramoPrecioProceso> actuales = new HashMap<>();
        for (TramoPrecioProceso t : tramoRepository.findByProceso_IdProcesoOrderByCantidadMinimaAsc(idProceso)) {
            actuales.put(t.getCantidadMinima(), t);
        }
        for (Integer u : UNIDADES_PLAN) {
            if (u == 1 || !enviados.contains(u)) continue;   // un plan que no viene (p. ej. oculto) no se toca
            TramoPrecioProceso existente = actuales.get(u);
            BigDecimal precio = nuevos.get(u);
            historial.registrar(idProceso, "Precio plan " + u, existente != null ? existente.getValorUnitario() : null, precio);
            if (precio == null) {
                if (existente != null) tramoRepository.delete(existente);
            } else if (existente != null) {
                existente.setValorUnitario(precio);
                tramoRepository.save(existente);
            } else {
                tramoRepository.save(TramoPrecioProceso.builder().proceso(p).cantidadMinima(u).valorUnitario(precio).build());
            }
        }
        log.info("Precios por plan guardados: proceso={}", p.getNombreProceso());
        return armar(nuevos, p.getCostoDirecto());
    }

    private Analisis armar(Map<Integer, BigDecimal> porUnidades, BigDecimal costoDirecto) {
        List<PlanTienda> visibles = planRepository.findByVisibleTrueOrderByUnidadesAsc();
        BigDecimal margenMinimo = parametros.decimal("margenMinimoPorcentaje");
        List<FilaPlan> filas = CalculadoraPlanes.analizar(visibles, porUnidades.get(1), tramosSinBase(porUnidades), costoDirecto, margenMinimo);
        String error = porUnidades.get(1) == null ? null
                : CalculadoraPlanes.validarEscalera(visibles, porUnidades.get(1), tramosSinBase(porUnidades)).orElse(null);
        BigDecimal descMax = filas.stream().map(FilaPlan::descuentoTotal).filter(java.util.Objects::nonNull)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        return new Analisis(filas, margenMinimo, descMax, error == null, error);
    }

    private static Map<Integer, BigDecimal> tramosSinBase(Map<Integer, BigDecimal> porUnidades) {
        Map<Integer, BigDecimal> m = new HashMap<>(porUnidades);
        m.remove(1);
        return m;
    }

    private static Map<Integer, BigDecimal> porUnidades(List<EntradaPrecio> precios) {
        Map<Integer, BigDecimal> m = new HashMap<>();
        if (precios == null) return m;
        for (EntradaPrecio e : precios) {
            if (e.unidades() == null || !UNIDADES_PLAN.contains(e.unidades())) {
                throw new ApiException("Plan inválido: " + e.unidades(), HttpStatus.BAD_REQUEST);
            }
            if (e.precioUnitario() == null) continue;
            if (e.precioUnitario().signum() <= 0) {
                throw new ApiException("El precio del plan de " + e.unidades() + " unidades debe ser mayor a cero", HttpStatus.BAD_REQUEST);
            }
            m.put(e.unidades(), e.precioUnitario());
        }
        return m;
    }

    private Proceso proceso(Integer id) {
        return procesoRepository.findById(id).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
    }
}
