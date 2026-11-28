package com.polygraph.erp.modules.trabajos.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.EstadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Un trabajo pesado en la cola persistente — ver V194__trabajos_asincronos.sql. El uuid es lo
 *  único que sale hacia el frontend (el id numérico no se expone, no se puede adivinar el de otro). */
@Entity
@jakarta.persistence.EntityListeners(com.polygraph.erp.modules.tiemporeal.CambiosEntityListener.class)
@Table(name = "trabajos_asincronos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"usuario", "parametrosJson", "resultadoJson", "errorTecnico"})
public class TrabajoAsincrono {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true)
    private UUID uuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40)
    private TipoTrabajo tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoTrabajo estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "parametros_json", nullable = false, columnDefinition = "TEXT")
    private String parametrosJson;

    @Column(name = "progreso", nullable = false)
    private Integer progreso;

    @Column(name = "intentos", nullable = false)
    private Integer intentos;

    @Column(name = "max_intentos", nullable = false)
    private Integer maxIntentos;

    /** Antes de esta hora el despachador no lo toma — así se espacian los reintentos. */
    @Column(name = "disponible_desde", nullable = false)
    private LocalDateTime disponibleDesde;

    @Column(name = "resultado_json", columnDefinition = "TEXT")
    private String resultadoJson;

    /** Relativa a app.trabajos.directorio — nunca una ruta absoluta que venga de afuera. */
    @Column(name = "resultado_ruta", length = 500)
    private String resultadoRuta;

    @Column(name = "resultado_nombre", length = 255)
    private String resultadoNombre;

    @Column(name = "resultado_tipo_contenido", length = 100)
    private String resultadoTipoContenido;

    @Column(name = "mensaje_usuario", length = 500)
    private String mensajeUsuario;

    @Column(name = "error_tecnico", columnDefinition = "TEXT")
    private String errorTecnico;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;
}
