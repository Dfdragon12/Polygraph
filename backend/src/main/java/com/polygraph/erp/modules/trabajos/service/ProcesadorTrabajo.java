package com.polygraph.erp.modules.trabajos.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.shared.enums.TipoTrabajo;

import java.util.Map;

/** La lógica de UN tipo de trabajo. El despachador se encarga de hilos, reintentos, errores y
 *  estado; el procesador solo hace el trabajo y devuelve el resultado.
 *
 *  procesar() corre FUERA de toda transacción: cada acceso a la BD que haga debe abrir y cerrar
 *  la suya (llamando servicios @Transactional), para no retener una conexión del pool mientras
 *  hace trabajo de CPU o disco (ej. LibreOffice). */
public interface ProcesadorTrabajo {

    TipoTrabajo tipo();

    /** Lanzar ApiException 4xx = error del usuario (no se reintenta, su mensaje se le muestra).
     *  Cualquier otra excepción = falla técnica (se reintenta si quedan intentos). */
    ResultadoTrabajo procesar(TrabajoAsincrono trabajo, Map<String, Object> parametros, ContextoTrabajo contexto) throws Exception;

    /** Antes de entregar el archivo resultado: vuelve a revisar que el usuario siga teniendo
     *  acceso (ej. el informe dejó de estar publicado). Por defecto, basta con ser el dueño. */
    default void validarDescarga(TrabajoAsincrono trabajo, Map<String, Object> parametros, Usuario usuario) {
    }

    /** Callback hacia el despachador, para que el procesador informe su avance. */
    interface ContextoTrabajo {
        void progreso(int porcentaje);
    }
}
