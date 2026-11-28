package com.polygraph.erp.modules.evaluados.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.EstadoRevisionDocumento;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@jakarta.persistence.EntityListeners(com.polygraph.erp.modules.tiemporeal.CambiosEntityListener.class)
@Table(name = "documento_evaluado")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"hojaVidaEvaluado", "revisadoPor"})
public class DocumentoEvaluado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_hoja_vida", nullable = false)
    private HojaVidaEvaluado hojaVidaEvaluado;

    @Column(name = "tipo_documento", nullable = false, length = 100)
    private String tipoDocumento;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "ruta_archivo", length = 500)
    private String rutaArchivo;

    @Column(name = "fecha_carga")
    private LocalDateTime fechaCarga;

    /** Revisión interna (V197): PENDIENTE al cargar → APROBADO o DEVUELTO (con motivo). Si el
     *  candidato vuelve a subir un devuelto, la fila se reemplaza y queda PENDIENTE otra vez. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_revision", nullable = false, length = 20)
    @Builder.Default
    private EstadoRevisionDocumento estadoRevision = EstadoRevisionDocumento.PENDIENTE;

    /** Obligatoria al devolver: es lo que lee el candidato para saber qué corregir. */
    @Column(name = "observacion_revision", columnDefinition = "TEXT")
    private String observacionRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_revision")
    private Usuario revisadoPor;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;
}
