package com.polygraph.erp.modules.formularios.repository;

import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormularioSeccionRepository extends JpaRepository<FormularioSeccion, Integer> {
    /** Todas — activas y archivadas. Para el formulario del analista y el documento generado,
     *  que necesitan ver datos ya guardados aunque el campo se haya archivado después. */
    List<FormularioSeccion> findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(Integer idTipoProgreso);

    /** Solo activas — lo que el administrador edita en admin/catálogo → Plantilla. */
    List<FormularioSeccion> findByTipoProgreso_IdTipoProgresoAndActivoTrueOrderByOrdenAsc(Integer idTipoProgreso);

    /** true = este tipo de progreso tiene una plantilla armada (al menos una sección, activa o
     *  archivada) — usado para saber si "completar" un subproceso debe exigir un formulario
     *  diligenciado (ver AsignacionService.validarFormularioDiligenciado). */
    boolean existsByTipoProgreso_IdTipoProgreso(Integer idTipoProgreso);
}
