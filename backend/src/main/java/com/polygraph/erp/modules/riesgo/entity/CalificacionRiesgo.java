package com.polygraph.erp.modules.riesgo.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.EstadoResultado;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.NivelRiesgo;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.TipoAjuste;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Un cálculo del motor de riesgo para un caso (Servicio). Solo inserción: cada recálculo que
 *  cambia el resultado agrega una fila vigente y deja la anterior de historial. La decisión del
 *  gestor vive en la fila vigente; al publicar el informe queda bloqueada. */
@Entity
@jakarta.persistence.EntityListeners(com.polygraph.erp.modules.tiemporeal.CambiosEntityListener.class)
@Table(name = "calificaciones_riesgo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"servicio", "calculadoPor", "decididoPor", "detalleJson"})
public class CalificacionRiesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio", nullable = false)
    private Servicio servicio;

    @Column(name = "vigente", nullable = false)
    private Boolean vigente;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoResultado estado;

    @Column(name = "puntaje_ponderado", precision = 5, scale = 1)
    private BigDecimal puntajePonderado;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_por_puntaje", length = 10)
    private NivelRiesgo nivelPorPuntaje;

    @Column(name = "puntaje_final", precision = 5, scale = 1)
    private BigDecimal puntajeFinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_final", length = 10)
    private NivelRiesgo nivelFinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_ajuste", length = 10)
    private TipoAjuste tipoAjuste;

    @Column(name = "cobertura", precision = 5, scale = 2)
    private BigDecimal cobertura;

    @Column(name = "requiere_revision", nullable = false)
    private Boolean requiereRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "criticidad", nullable = false, length = 10)
    private CriticidadCargo criticidad;

    @Column(name = "version_configuracion", nullable = false, length = 40)
    private String versionConfiguracion;

    /** Registro interno completo (ResultadoCalificacion del motor + configuración usada), en JSON. */
    @Column(name = "detalle_json", nullable = false, columnDefinition = "TEXT")
    private String detalleJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculado_por")
    private Usuario calculadoPor;

    @Column(name = "fecha_calculo", nullable = false)
    private LocalDateTime fechaCalculo;

    // ── Decisión del gestor (§6) ──
    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_decidido", length = 10)
    private NivelRiesgo nivelDecidido;

    @Column(name = "justificacion_decision", columnDefinition = "TEXT")
    private String justificacionDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidido_por")
    private Usuario decididoPor;

    @Column(name = "fecha_decision")
    private LocalDateTime fechaDecision;

    @Builder.Default
    @Column(name = "bloqueado", nullable = false)
    private Boolean bloqueado = false;
}
