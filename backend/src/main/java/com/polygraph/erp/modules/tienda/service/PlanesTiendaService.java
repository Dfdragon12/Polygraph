package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso;
import com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository;
import com.polygraph.erp.modules.tienda.dto.PlanPublicoResponse;
import com.polygraph.erp.modules.tienda.entity.BeneficioPlan;
import com.polygraph.erp.modules.tienda.entity.PlanTienda;
import com.polygraph.erp.modules.tienda.repository.BeneficioPlanRepository;
import com.polygraph.erp.modules.tienda.repository.PlanTiendaRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Planes de un servicio tal como los ve el cliente (TP-01/TP-02). Todo se calcula aquí; React solo muestra.
 * Precios SIN IVA. Un plan sin precio para el servicio no se devuelve.
 */
@Service
@RequiredArgsConstructor
public class PlanesTiendaService {

    private final PlanTiendaRepository planRepository;
    private final BeneficioPlanRepository beneficioRepository;
    private final TramoPrecioProcesoRepository tramoRepository;

    @Transactional(readOnly = true)
    public List<PlanPublicoResponse> planesDe(Proceso proceso) {
        if (proceso.getValor() == null) return List.of();
        List<BeneficioPlan> beneficios = beneficioRepository.findByProceso_IdProcesoOrderByDesdeUnidadesAscOrdenAsc(proceso.getIdProceso());
        BigDecimal base = CalculadoraPlanes.entero(proceso.getValor());
        return CalculadoraPlanes.conPrecio(planRepository.findByVisibleTrueOrderByUnidadesAsc(), proceso.getValor(), tramosPorCantidad(proceso.getIdProceso()))
                .stream()
                .map(pp -> construir(pp, base, beneficios))
                .toList();
    }

    /** El plan que se está comprando, con el precio vigente. 400 si ese plan no existe, está oculto o no tiene precio para el servicio. */
    @Transactional(readOnly = true)
    public PlanPublicoResponse planDe(Proceso proceso, int unidades) {
        return planesDe(proceso).stream()
                .filter(p -> p.unidades() == unidades)
                .findFirst()
                .orElseThrow(() -> new ApiException("El plan de " + unidades + " unidades no está disponible para '"
                        + proceso.getNombreProceso() + "'", HttpStatus.BAD_REQUEST));
    }

    /** Mayor descuento por volumen entre los planes («Hasta X % menos»); 0 si no hay. */
    public BigDecimal descuentoMaximo(List<PlanPublicoResponse> planes) {
        return planes.stream().map(PlanPublicoResponse::descuentoPorcentaje).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    /**
     * Valida la escalera de precios con el precio base y los tramos que quedarían guardados. Lanza 400 con el mensaje para el admin.
     * `tramos` va por cantidad mínima; solo cuentan los que coinciden con las unidades de un plan visible.
     */
    @Transactional(readOnly = true)
    public void validarEscalera(BigDecimal valorBase, Map<Integer, BigDecimal> tramos) {
        if (valorBase == null) return;
        CalculadoraPlanes.validarEscalera(planRepository.findByVisibleTrueOrderByUnidadesAsc(), valorBase, tramos)
                .ifPresent(msg -> { throw new ApiException(msg, HttpStatus.BAD_REQUEST); });
    }

    private Map<Integer, BigDecimal> tramosPorCantidad(Integer idProceso) {
        Map<Integer, BigDecimal> m = new HashMap<>();
        for (TramoPrecioProceso t : tramoRepository.findByProceso_IdProcesoOrderByCantidadMinimaDesc(idProceso)) {
            m.put(t.getCantidadMinima(), t.getValorUnitario());
        }
        return m;
    }

    private PlanPublicoResponse construir(CalculadoraPlanes.PlanConPrecio pp, BigDecimal base, List<BeneficioPlan> beneficios) {
        PlanTienda plan = pp.plan();
        int n = plan.getUnidades();
        BigDecimal paquete = pp.precioUnitario().multiply(BigDecimal.valueOf(n));
        BigDecimal ahorro = base.multiply(BigDecimal.valueOf(n)).subtract(paquete).max(BigDecimal.ZERO);
        List<String> textos = beneficios.stream()
                .filter(b -> b.getDesdeUnidades() <= n)
                .map(BeneficioPlan::getTexto)
                .toList();
        return new PlanPublicoResponse(n, plan.getNombre(), Boolean.TRUE.equals(plan.getDestacado()), plan.getVigenciaMeses(),
                pp.precioUnitario(), paquete, ahorro, CalculadoraPlanes.descuentoPorcentaje(pp.precioUnitario(), base), textos);
    }
}
