package com.polygraph.erp.modules.trabajos.service;

import com.polygraph.erp.shared.enums.TipoTrabajo;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Registro tipo → procesador. Cada módulo aporta el suyo como bean (ej. solicitudes aporta
 *  CARGA_MASIVA e INFORME_PDF); agregar un tipo nuevo no toca el despachador. */
@Component
public class ProcesadoresTrabajos {

    private final Map<TipoTrabajo, ProcesadorTrabajo> porTipo = new EnumMap<>(TipoTrabajo.class);

    public ProcesadoresTrabajos(List<ProcesadorTrabajo> procesadores) {
        for (ProcesadorTrabajo p : procesadores) {
            if (porTipo.put(p.tipo(), p) != null) {
                throw new IllegalStateException("Hay dos procesadores para el tipo de trabajo " + p.tipo());
            }
        }
    }

    public ProcesadorTrabajo de(TipoTrabajo tipo) {
        ProcesadorTrabajo p = porTipo.get(tipo);
        if (p == null) throw new IllegalStateException("No hay procesador para el tipo de trabajo " + tipo);
        return p;
    }
}
