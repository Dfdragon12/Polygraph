package com.polygraph.erp.shared.estados;

import com.polygraph.erp.shared.enums.EstadoSubproceso;

import java.util.List;

/**
 * La «próxima acción» del gestor sobre un servicio (columna de Solicitudes y filas del control de calidad, PB-05): UN botón por servicio. No crea
 * estados: lee los de los subprocesos, la pasta y el enlace del candidato. Pura.
 *
 * Orden de prioridad: revisar lo que llegó → emitir la pasta final → enviar o reenviar el enlace → asignar → avisar al programador → ver.
 */
public final class ProximaAccion {

    private ProximaAccion() {}

    public enum Accion {
        REVISAR("Revisar informe"), EMITIR_PASTA("Emitir pasta final"), ENVIAR_ENLACE("Enviar enlace"), REENVIAR_ENLACE("Reenviar enlace"),
        ASIGNAR("Asignar"), AVISAR_PROGRAMADOR("Avisar al programador"), VER_EXPEDIENTE("Ver expediente"), VER_AVANCE("Ver avance");

        private final String etiqueta;

        Accion(String etiqueta) { this.etiqueta = etiqueta; }

        public String etiqueta() { return etiqueta; }
    }

    /** esCita: poligrafía o visita; conResponsable: ejecutor asignado (o, en una cita, programador encargado). */
    public record Sub(EstadoSubproceso estado, boolean esCita, boolean conResponsable) {}

    /** candidatoCompletado: null si el servicio no tiene candidato o no se sabe; enlaceEnviado: Polygraph ya mandó el enlace. */
    public static Accion de(boolean cancelado, List<Sub> subprocesos, boolean pastaEmitida, Boolean candidatoCompletado, boolean enlaceEnviado) {
        List<Sub> vigentes = subprocesos.stream().filter(s -> s.estado() != EstadoSubproceso.CANCELADO).toList();
        if (cancelado || vigentes.isEmpty()) return Accion.VER_AVANCE;
        if (vigentes.stream().anyMatch(s -> s.estado() == EstadoSubproceso.FINALIZADO)) return Accion.REVISAR;
        if (vigentes.stream().allMatch(s -> s.estado() == EstadoSubproceso.PUBLICADO)) return pastaEmitida ? Accion.VER_EXPEDIENTE : Accion.EMITIR_PASTA;
        if (Boolean.FALSE.equals(candidatoCompletado)) return enlaceEnviado ? Accion.REENVIAR_ENLACE : Accion.ENVIAR_ENLACE;
        if (vigentes.stream().anyMatch(s -> s.estado() == EstadoSubproceso.PENDIENTE && !s.conResponsable())) return Accion.ASIGNAR;
        if (vigentes.stream().anyMatch(s -> s.esCita() && s.estado() == EstadoSubproceso.PROGRAMANDO)) return Accion.AVISAR_PROGRAMADOR;
        return Accion.VER_AVANCE;
    }

    /** ¿Hay algo que el gestor debe revisar o cerrar? Un subproceso FINALIZADO, o todo publicado con la pasta sin emitir. */
    public static boolean pendienteDelGestor(List<EstadoSubproceso> estados, boolean pastaEmitida) {
        List<EstadoSubproceso> vigentes = estados.stream().filter(e -> e != EstadoSubproceso.CANCELADO).toList();
        if (vigentes.isEmpty()) return false;
        if (vigentes.contains(EstadoSubproceso.FINALIZADO)) return true;
        return !pastaEmitida && vigentes.stream().allMatch(e -> e == EstadoSubproceso.PUBLICADO);
    }

    /** De quién es el retraso de una solicitud vencida (bloque «Tiempos» del gestor): la revisión del gestor, el candidato o el campo. */
    public enum ResponsableRetraso { REVISION_GESTOR, CANDIDATO, CAMPO }

    public static ResponsableRetraso responsableDelRetraso(List<EstadoSubproceso> estados, boolean pastaEmitida, Boolean candidatoCompletado) {
        if (pendienteDelGestor(estados, pastaEmitida)) return ResponsableRetraso.REVISION_GESTOR;
        if (Boolean.FALSE.equals(candidatoCompletado)) return ResponsableRetraso.CANDIDATO;
        return ResponsableRetraso.CAMPO;
    }
}
