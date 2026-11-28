package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.BeneficioResponse;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaBeneficio;
import com.polygraph.erp.modules.tienda.entity.BeneficioPlan;
import com.polygraph.erp.modules.tienda.repository.BeneficioPlanRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Beneficios de un servicio por plan (TP-04): cada uno aparece en todos los planes con unidades >= «desde». Se guardan en el orden de la lista. */
@Service
@RequiredArgsConstructor
public class BeneficiosPlanService {

    static final int MAXIMO = 20;

    private final BeneficioPlanRepository repository;
    private final ProcesoRepository procesoRepository;

    @Transactional(readOnly = true)
    public List<BeneficioResponse> listar(Integer idProceso) {
        return repository.findByProceso_IdProcesoOrderByOrdenAsc(idProceso).stream()
                .map(b -> new BeneficioResponse(b.getTexto(), b.getDesdeUnidades(), b.getOrden()))
                .toList();
    }

    /** Reemplaza el conjunto completo de beneficios del servicio. */
    @Transactional
    public List<BeneficioResponse> guardar(Integer idProceso, List<EntradaBeneficio> entradas) {
        Proceso p = procesoRepository.findById(idProceso).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        List<EntradaBeneficio> lista = entradas == null ? List.of() : entradas.stream()
                .filter(e -> e.texto() != null && !e.texto().isBlank()).toList();
        if (lista.size() > MAXIMO) {
            throw new ApiException("Máximo " + MAXIMO + " beneficios por servicio", HttpStatus.BAD_REQUEST);
        }
        List<BeneficioPlan> nuevos = new ArrayList<>();
        int orden = 0;
        for (EntradaBeneficio e : lista) {
            String texto = e.texto().trim();
            if (texto.length() > 120) {
                throw new ApiException("Cada beneficio admite máximo 120 caracteres", HttpStatus.BAD_REQUEST);
            }
            if (e.desdeUnidades() == null || !PreciosPlanService.UNIDADES_PLAN.contains(e.desdeUnidades())) {
                throw new ApiException("«Desde N unidades» debe ser 1, 5, 20, 100 o 200", HttpStatus.BAD_REQUEST);
            }
            nuevos.add(BeneficioPlan.builder().proceso(p).texto(texto).desdeUnidades(e.desdeUnidades()).orden(orden++).build());
        }
        repository.deleteByProceso_IdProceso(idProceso);
        repository.flush();
        repository.saveAll(nuevos);
        return listar(idProceso);
    }
}
