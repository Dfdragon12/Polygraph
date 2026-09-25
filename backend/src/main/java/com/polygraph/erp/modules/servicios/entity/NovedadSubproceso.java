package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.EstadoNovedad;
import com.polygraph.erp.modules.catalogo.entity.TipoNovedad;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Novedad reportada por quien ejecuta un subproceso (analista/poligrafista/visitador) — p.ej. "el
 * candidato nunca llegó". No cambia el estado del subproceso: queda ABIERTA hasta que el gestor/
 * programador que lo coordina la marca RESUELTA con una respuesta (ver NovedadSubprocesoService).
 */
@Entity
@Table(name = "novedades_subproceso")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"servicioSubproceso", "registradoPor", "resueltaPor", "tipo"})
public class NovedadSubproceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio_subproceso", nullable = false)
    private ServicioSubproceso servicioSubproceso;

    /** Qué pasó — del catálogo administrable tipos_novedad (antes era un enum Java). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_novedad", nullable = false)
    private TipoNovedad tipo;

    @Column(name = "descripcion", nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_registrado_por", nullable = false)
    private Usuario registradoPor;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoNovedad estado = EstadoNovedad.ABIERTA;

    @Column(name = "respuesta", columnDefinition = "TEXT")
    private String respuesta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_resuelta_por")
    private Usuario resueltaPor;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @PrePersist
    protected void alCrear() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
    }
}
