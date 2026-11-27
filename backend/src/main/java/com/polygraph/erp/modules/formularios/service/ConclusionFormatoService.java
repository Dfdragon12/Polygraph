package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.formularios.entity.Formulario;
import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioDato;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.formularios.repository.FormularioDatoRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * PC-02 · Lee la conclusión del ejecutor desde donde el formato la guarda (ver {@link ConclusionFormato}). Una sola lectura para el envío a revisión,
 * la ventana Publicar y la reparación de datos viejos: así la ventana trae lo que el ejecutor escribió y no hay que capturarlo dos veces.
 */
@Service
@RequiredArgsConstructor
public class ConclusionFormatoService {

    private final FormularioSeccionRepository seccionRepository;
    private final FormularioRepository formularioRepository;
    private final FormularioDatoRepository datoRepository;

    /** La columna donde este tipo de subproceso escribe su conclusión, o vacío si su formato no tiene (entonces vale el cuadro genérico). */
    @Transactional(readOnly = true)
    public Optional<FormularioColumna> columnaDe(ServicioSubproceso sp) {
        List<FormularioSeccion> secciones = seccionRepository.findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(sp.getTipoProgreso().getIdTipoProgreso());
        return ConclusionFormato.columnaDe(secciones);
    }

    /** Lo que el ejecutor escribió en el campo de conclusión de su formato (null si el formato no tiene o está vacío). */
    @Transactional(readOnly = true)
    public String delFormato(ServicioSubproceso sp) {
        Optional<FormularioColumna> columna = columnaDe(sp);
        if (columna.isEmpty()) return null;
        Optional<Formulario> formulario = formularioRepository.findByServicioSubproceso_Id(sp.getId());
        if (formulario.isEmpty()) return null;
        Integer idColumna = columna.get().getIdColumna();
        return datoRepository.findByFormulario_IdFormulario(formulario.get().getIdFormulario()).stream()
                .filter(d -> d.getFila() == null && d.getColumna().getIdColumna().equals(idColumna))
                .map(FormularioDato::getValor)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .findFirst().orElse(null);
    }

    /** La conclusión del ejecutor: la de su formato si la tiene escrita; si no, la del cuadro genérico. Null si no hay ninguna. */
    @Transactional(readOnly = true)
    public String efectiva(ServicioSubproceso sp) {
        String formato = delFormato(sp);
        if (formato != null) return formato;
        String generica = sp.getConclusion();
        return generica == null || generica.isBlank() ? null : generica.trim();
    }

    /** ¿Dónde se escribió? {@code FORMATO} (campo de su plantilla), {@code SUBPROCESO} (cuadro genérico) o null si no hay conclusión. */
    @Transactional(readOnly = true)
    public String origen(ServicioSubproceso sp) {
        if (delFormato(sp) != null) return "FORMATO";
        return sp.getConclusion() == null || sp.getConclusion().isBlank() ? null : "SUBPROCESO";
    }
}
