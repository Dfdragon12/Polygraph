package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.shared.enums.EstadoServicio;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@jakarta.persistence.EntityListeners(com.polygraph.erp.modules.tiemporeal.CambiosEntityListener.class)
@Table(name = "servicios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"cliente", "candidato", "proceso", "usuarioSolicita", "usuarioCriticidad"})
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_servicio")
    private Integer idServicio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_candidato")
    private Candidato candidato;

    @Column(name = "fecha_solicitud")
    private LocalDate fechaSolicitud;

    @Column(name = "hora_solicitud")
    private LocalTime horaSolicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proceso", nullable = false)
    private Proceso proceso;

    /** Prepago (NS-02): de qué lote de compra salió la unidad y cuánto valía. Se guarda al enviar y no se recalcula con compras nuevas. */
    @Column(name = "id_lote_prepago")
    private Long idLotePrepago;

    @Column(name = "valor_unitario_prepago", precision = 15, scale = 2)
    private java.math.BigDecimal valorUnitarioPrepago;

    /** NS-04: cuándo se le devolvió al cliente lo que costó esta solicitud al cancelarla (null = no devuelto). Evita devolver dos veces. */
    @Column(name = "cobro_devuelto_en")
    private java.time.LocalDateTime cobroDevueltoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 40)
    private EstadoServicio estado;

    @Column(name = "resultado", length = 100)
    private String resultado;

    @Column(name = "observacion", columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "fecha_entrega_estudio")
    private LocalDate fechaEntregaEstudio;

    @Column(name = "fecha_de_envio")
    private LocalDate fechaDeEnvio;

    /** NS-08: dirección donde se hace la visita (solo servicios con visita). */
    @Column(name = "direccion_visita", columnDefinition = "TEXT")
    private String direccionVisita;

    @Column(name = "cargo", length = 150)
    private String cargo;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    // ── Tiempo de la solicitud, en días hábiles de Colombia (ver TiempoServicioService) ──

    /** Día en que se considera recibida: el mismo día si llegó en día hábil y hasta la hora de corte; si no, el siguiente día hábil. */
    @Column(name = "fecha_recepcion_efectiva")
    private LocalDate fechaRecepcionEfectiva;

    /** Plazo en días hábiles, copiado del catálogo al crear la solicitud (un cambio posterior del catálogo no la altera). */
    @Column(name = "plazo_dias_habiles")
    private Integer plazoDiasHabiles;

    /** La primera fecha de compromiso; no cambia aunque la vigente se reprograme. */
    @Column(name = "fecha_compromiso_original")
    private LocalDate fechaCompromisoOriginal;

    /** La fecha de compromiso VIGENTE (antes se llamaba fecha de entrega estimada; el campo conserva ese nombre en Java). */
    @Column(name = "fecha_compromiso")
    private LocalDate fechaEntregaEstimada;

    /** Días hábiles en que el reloj estuvo en pausa (espera del candidato o del cliente); corren la fecha de compromiso. */
    @Builder.Default
    @Column(name = "dias_pausa_habiles", nullable = false)
    private Integer diasPausaHabiles = 0;

    /** La fecha de compromiso corrida por los días de pausa (y medida contra la original si la causa de una reprogramación fue de Polygraph):
     *  contra ella se mide el semáforo y es la fecha que se muestra. */
    @Column(name = "fecha_compromiso_ajustada")
    private LocalDate fechaCompromisoAjustada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_tiempo", length = 20)
    private com.polygraph.erp.shared.enums.EstadoTiempo estadoTiempo;

    /** Quién tiene detenido el reloj mientras la solicitud está EN_PAUSA (para decir "esperando al candidato / al cliente"); null si no. */
    @Enumerated(EnumType.STRING)
    @Column(name = "pausa_origen", length = 10)
    private com.polygraph.erp.shared.enums.OrigenPausa pausaOrigen;

    /** Días hábiles que faltan para la fecha de compromiso ajustada (en las abiertas). */
    @Column(name = "dias_restantes_habiles")
    private Integer diasRestantesHabiles;

    /** Días hábiles de retraso (en las vencidas) o que faltan (en las abiertas); en las entregadas, el retraso congelado. */
    @Column(name = "dias_retraso_habiles")
    private Integer diasRetrasoHabiles;

    /** Cuándo se entregó (se publicó) y si fue a tiempo: se fijan al publicar y no se recalculan. */
    @Column(name = "fecha_entrega_real")
    private LocalDate fechaEntregaReal;

    @Enumerated(EnumType.STRING)
    @Column(name = "cumplimiento", length = 10)
    private com.polygraph.erp.shared.enums.Cumplimiento cumplimiento;

    @Column(name = "estado_tiempo_actualizado_en")
    private java.time.LocalDateTime estadoTiempoActualizadoEn;

    /** Marca CANDIDATO_DESISTIO (SE-02): el candidato desistió y quedó algún subproceso iniciado que sigue hasta publicarse. */
    @Builder.Default
    @Column(name = "candidato_desistio", nullable = false)
    private Boolean candidatoDesistio = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_solicita", nullable = false)
    private Usuario usuarioSolicita;

    /** Criticidad del cargo de ESTE caso para el motor de riesgo; null = usa la del proceso
     *  (Proceso.criticidadDefecto). La ajusta el gestor según el cargo real del evaluado. */
    @Enumerated(EnumType.STRING)
    @Column(name = "criticidad_cargo", length = 10)
    private com.polygraph.erp.shared.enums.CriticidadCargo criticidadCargo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_criticidad")
    private Usuario usuarioCriticidad;

    @PrePersist
    void alCrear() {
        if (fechaCompromisoOriginal == null) fechaCompromisoOriginal = fechaEntregaEstimada;
        if (diasPausaHabiles == null) diasPausaHabiles = 0;
    }

    /** La fecha de entrega que se le muestra a todos: la de compromiso ajustada (con pausas) o, si aún no se calculó, la estimada. */
    public LocalDate fechaParaMostrar() {
        return fechaCompromisoAjustada != null ? fechaCompromisoAjustada : fechaEntregaEstimada;
    }
}
