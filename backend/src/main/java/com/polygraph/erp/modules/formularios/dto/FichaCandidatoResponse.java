package com.polygraph.erp.modules.formularios.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Todo lo que se sabe del candidato de un subproceso, para consultarlo sin salir del
 *  formulario: sus datos agrupados (identificación, contacto, otros) y los soportes que cargó,
 *  cruzados contra los documentos que exige el proceso (DocumentoRequeridoProceso) para que se
 *  vea de un vistazo qué falta. */
public record FichaCandidatoResponse(
        List<GrupoDatos> grupos,
        List<Soporte> soportes
) {
    public record GrupoDatos(String nombre, List<CampoCandidatoValorResponse> campos) {}

    /** Un documento requerido por el proceso (cargado o no) o uno que el candidato subió aunque
     *  el proceso no lo exija. idDocumento/nombreArchivo/fechaCarga son null si no lo ha cargado.
     *  requerido=false: lo subió por su cuenta, no está en la lista del proceso. */
    public record Soporte(
            String clave,
            String nombre,
            boolean requerido,
            boolean obligatorio,
            Long idDocumento,
            String nombreArchivo,
            LocalDateTime fechaCarga) {}
}
