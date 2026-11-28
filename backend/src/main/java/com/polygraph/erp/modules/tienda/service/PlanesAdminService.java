package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.EntradaPlan;
import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.PlanAdminResponse;
import com.polygraph.erp.modules.tienda.entity.ParametroTiendaAuditoria;
import com.polygraph.erp.modules.tienda.entity.PlanTienda;
import com.polygraph.erp.modules.tienda.repository.ParametroTiendaAuditoriaRepository;
import com.polygraph.erp.modules.tienda.repository.PlanTiendaRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Planes globales de la tienda (TP-04): nombre, visible, destacado y vigencia. Solo el administrador. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanesAdminService {

    private final PlanTiendaRepository repository;
    private final ParametroTiendaAuditoriaRepository auditoria;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PlanAdminResponse> listar() {
        return repository.findAll().stream()
                .sorted(java.util.Comparator.comparing(PlanTienda::getUnidades))
                .map(p -> new PlanAdminResponse(p.getUnidades(), p.getNombre(), Boolean.TRUE.equals(p.getVisible()),
                        Boolean.TRUE.equals(p.getDestacado()), p.getVigenciaMeses()))
                .toList();
    }

    @Transactional
    public List<PlanAdminResponse> guardar(List<EntradaPlan> entradas, String usuario) {
        Map<Integer, PlanTienda> planes = repository.findAll().stream().collect(Collectors.toMap(PlanTienda::getUnidades, Function.identity()));
        Set<Integer> vistos = new HashSet<>();
        int destacados = 0, visibles = 0;
        for (EntradaPlan e : entradas == null ? List.<EntradaPlan>of() : entradas) {
            if (e.unidades() == null || !planes.containsKey(e.unidades()) || !vistos.add(e.unidades())) {
                throw new ApiException("Plan inválido o repetido: " + e.unidades(), HttpStatus.BAD_REQUEST);
            }
            if (e.nombre() == null || e.nombre().isBlank() || e.nombre().trim().length() > 60) {
                throw new ApiException("El nombre del plan de " + e.unidades() + " unidades es obligatorio (máximo 60 caracteres)", HttpStatus.BAD_REQUEST);
            }
            if (e.vigenciaMeses() != null && (e.vigenciaMeses() < 1 || e.vigenciaMeses() > 120)) {
                throw new ApiException("La vigencia debe estar entre 1 y 120 meses (vacía = sin vencimiento)", HttpStatus.BAD_REQUEST);
            }
            if (Boolean.TRUE.equals(e.destacado())) destacados++;
            if (Boolean.TRUE.equals(e.visible())) visibles++;
        }
        if (vistos.size() != planes.size()) {
            throw new ApiException("Deben venir los " + planes.size() + " planes", HttpStatus.BAD_REQUEST);
        }
        if (destacados > 1) throw new ApiException("Solo un plan puede ser «Más solicitado»", HttpStatus.BAD_REQUEST);
        if (visibles == 0) throw new ApiException("Al menos un plan debe estar visible", HttpStatus.BAD_REQUEST);

        // Lo que cada plan era antes de cambiar nada (para la auditoría).
        Map<Integer, String> antes = planes.values().stream()
                .collect(Collectors.toMap(PlanTienda::getUnidades, p -> resumen(p, Boolean.TRUE.equals(p.getDestacado()))));

        // Quita primero el destacado actual para no chocar con el índice de «solo uno destacado».
        planes.values().forEach(p -> p.setDestacado(false));
        repository.saveAllAndFlush(planes.values());

        LocalDateTime ahora = LocalDateTime.now(clock.withZone(ParametrosTiendaService.ZONA));
        for (EntradaPlan e : entradas) {
            PlanTienda p = planes.get(e.unidades());
            p.setNombre(e.nombre().trim());
            p.setVisible(Boolean.TRUE.equals(e.visible()));
            p.setDestacado(Boolean.TRUE.equals(e.destacado()));
            p.setVigenciaMeses(e.vigenciaMeses());
            String despues = resumen(p, p.getDestacado());
            if (!antes.get(e.unidades()).equals(despues)) {
                auditoria.save(ParametroTiendaAuditoria.builder().clave("plan:" + e.unidades()).valorAnterior(antes.get(e.unidades()))
                        .valorNuevo(despues).usuario(usuario).fecha(ahora).build());
            }
        }
        repository.saveAll(planes.values());
        log.info("Planes de la tienda actualizados por {}", usuario);
        return listar();
    }

    private static String resumen(PlanTienda p, boolean destacado) {
        return p.getNombre() + " | visible=" + p.getVisible() + " | destacado=" + destacado + " | vigencia=" + p.getVigenciaMeses();
    }
}
