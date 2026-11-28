package com.polygraph.erp.shared.enums;

import java.util.List;

/**
 * Los siete estados de un subproceso. Es el ÚNICO lugar donde existen: nombre en pantalla, color, orden y el
 * texto que lee el cliente salen de aquí (se exponen en GET /api/v1/catalogo/estados-subproceso) y ninguna
 * pantalla muestra el código interno.
 *
 * Reemplaza al antiguo EstadoAsignacion (PENDIENTE, ASIGNADO, COMPLETADO, PUBLICADO, CANCELADO): ASIGNADO pasó a
 * EN_EJECUCION y COMPLETADO a FINALIZADO (migración V224).
 */
public enum EstadoSubproceso {
    PENDIENTE(1, "Pendiente", "#F59E0B", "Recibido, por iniciar"),
    PROGRAMANDO(2, "Programando", "#0EA5E9", "Agendando la cita"),
    EN_EJECUCION(3, "En ejecución", "#6366F1", "Se está realizando"),
    REPROGRAMADO(4, "Reprogramado", "#F97316", "La cita se está reagendando"),
    FINALIZADO(5, "Finalizado", "#22C55E", "Realizado, en control de calidad y auditoría"),
    PUBLICADO(6, "Publicado", "#0D9488", "Listo: puedes descargar el informe"),
    CANCELADO(7, "Cancelado", "#9CA3AF", "Cancelado");

    private final int orden;
    private final String nombre;
    private final String color;
    private final String textoCliente;

    EstadoSubproceso(int orden, String nombre, String color, String textoCliente) {
        this.orden = orden;
        this.nombre = nombre;
        this.color = color;
        this.textoCliente = textoCliente;
    }

    public int orden() { return orden; }
    public String nombre() { return nombre; }
    public String color() { return color; }
    public String textoCliente() { return textoCliente; }

    /** Los siete, en el orden en que se muestran en todas las pantallas. */
    public static List<EstadoSubproceso> enOrden() {
        return List.of(values());
    }

    /** Aún no lo toma nadie para hacerlo: espera responsable (PENDIENTE) o agenda (PROGRAMANDO). */
    public boolean sinIniciar() {
        return this == PENDIENTE || this == PROGRAMANDO;
    }

    /** Alguien lo tiene a su cargo y no está terminado (en ejecución, o la cita se está reagendando). */
    public boolean enCurso() {
        return this == EN_EJECUCION || this == REPROGRAMADO;
    }

    /** El responsable ya terminó: pasa a revisión (FINALIZADO) o ya se aprobó (PUBLICADO). */
    public boolean cerradoParaElResponsable() {
        return this == FINALIZADO || this == PUBLICADO;
    }
}
