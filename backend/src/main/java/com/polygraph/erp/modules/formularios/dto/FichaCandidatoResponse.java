package com.polygraph.erp.modules.formularios.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Todo lo que se sabe del candidato de un subproceso, para consultarlo sin salir del
 *  formulario: sus datos agrupados (identificación, contacto, otros) y los soportes que cargó,
 *  cruzados contra los documentos que exige el proceso (DocumentoRequeridoProceso) para que se
 *  vea de un vistazo qué falta.
 *
 *  puedeRevisarSoportes: si quien mira puede aprobar/devolver documentos (ver
 *  FichaCandidatoService.puedeRevisar). */
public record FichaCandidatoResponse(
        List<GrupoDatos> grupos,
        List<Soporte> soportes,
        boolean puedeRevisarSoportes,
        // Lo que el candidato respondió a las preguntas de declaración de su hoja de vida
        List<Declaracion> declaraciones,
        // Lo que el cliente pidió al solicitar (formato adicional de la poligrafía y temas a evaluar); null si nada
        com.polygraph.erp.modules.solicitudes.dto.SolicitudClienteResponse solicitudCliente
) {
    public record Declaracion(String pregunta, String respuesta, String detalle) {}

    public record GrupoDatos(String nombre, List<CampoCandidatoValorResponse> campos) {}

    /** Un documento requerido por el proceso (cargado o no) o uno que el candidato subió aunque
     *  el proceso no lo exija. idDocumento/nombreArchivo/fechaCarga son null si no lo ha cargado.
     *  requerido=false: lo subió por su cuenta, no está en la lista del proceso.
     *  estadoRevision: PENDIENTE · APROBADO · DENEGADO · ILEGIBLE · VENCIDO · INCOMPLETO · NO_CORRESPONDE (null si no lo ha cargado). */
    public record Soporte(
            String clave,
            String nombre,
            boolean requerido,
            boolean obligatorio,
            Long idDocumento,
            String nombreArchivo,
            LocalDateTime fechaCarga,
            String estadoRevision,
            String observacionRevision,
            String revisadoPor,
            LocalDateTime fechaRevision) {}
}
