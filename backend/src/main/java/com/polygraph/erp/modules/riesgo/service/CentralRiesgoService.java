package com.polygraph.erp.modules.riesgo.service;

import com.polygraph.erp.modules.riesgo.dto.CentralRiesgoDtos.BandaDto;
import com.polygraph.erp.modules.riesgo.dto.CentralRiesgoDtos.CentralRequest;
import com.polygraph.erp.modules.riesgo.dto.CentralRiesgoDtos.CentralResponse;
import com.polygraph.erp.modules.riesgo.entity.CentralRiesgo;
import com.polygraph.erp.modules.riesgo.entity.CentralRiesgoBanda;
import com.polygraph.erp.modules.riesgo.financiero.MotorFinanciero;
import com.polygraph.erp.modules.riesgo.financiero.MotorFinanciero.Banda;
import com.polygraph.erp.modules.riesgo.repository.CentralRiesgoRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Centrales de riesgo configurables: el nombre es solo interno (nunca se imprime) y las 4 bandas del indicador de pago
 *  se leen en cada cálculo, así que un cambio rige de inmediato. Si cambia el proveedor, solo se ajustan sus bandas. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CentralRiesgoService {

    private final CentralRiesgoRepository repository;

    @Transactional(readOnly = true)
    public List<CentralResponse> listar() {
        return repository.findAllByOrderByIdCentralAsc().stream().map(CentralResponse::de).toList();
    }

    public CentralResponse crear(CentralRequest r) {
        CentralRiesgo c = CentralRiesgo.builder().nombre(r.nombre().trim()).activo(!Boolean.FALSE.equals(r.activo())).build();
        aplicarBandas(c, r.bandas());
        log.info("Central de riesgo '{}' creada", c.getNombre());
        return CentralResponse.de(repository.save(c));
    }

    public CentralResponse actualizar(Integer id, CentralRequest r) {
        CentralRiesgo c = repository.findById(id).orElseThrow(() -> new ApiException("Central de riesgo no encontrada", HttpStatus.NOT_FOUND));
        c.setNombre(r.nombre().trim());
        if (r.activo() != null) c.setActivo(r.activo());
        aplicarBandas(c, r.bandas());
        log.info("Central de riesgo {} actualizada", id);
        return CentralResponse.de(repository.save(c));
    }

    /** Las bandas de la central que califica (la primera activa); las de la central actual si no hay ninguna. */
    @Transactional(readOnly = true)
    public List<Banda> bandasVigentes() {
        return repository.findFirstByActivoTrueOrderByIdCentralAsc()
                .map(c -> c.getBandas().stream().map(b -> new Banda(b.getDesde(), b.getHasta(), b.getNombre())).toList())
                .orElse(MotorFinanciero.BANDAS_POR_DEFECTO);
    }

    private static void aplicarBandas(CentralRiesgo c, List<BandaDto> bandas) {
        for (int i = 0; i < bandas.size(); i++) {
            BandaDto b = bandas.get(i);
            if (b.desde() > b.hasta()) {
                throw new ApiException("Banda " + (i + 1) + ": \"desde\" no puede ser mayor que \"hasta\".", HttpStatus.BAD_REQUEST);
            }
            if (i > 0 && b.desde() <= bandas.get(i - 1).hasta()) {
                throw new ApiException("Banda " + (i + 1) + ": debe empezar después de donde termina la anterior.", HttpStatus.BAD_REQUEST);
            }
        }
        c.getBandas().clear();
        for (int i = 0; i < bandas.size(); i++) {
            BandaDto b = bandas.get(i);
            c.getBandas().add(CentralRiesgoBanda.builder().central(c).orden(i + 1).desde(b.desde()).hasta(b.hasta()).nombre(b.nombre().trim()).build());
        }
    }
}
