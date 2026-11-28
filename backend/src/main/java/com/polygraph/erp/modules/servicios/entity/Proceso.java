package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "procesos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Proceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proceso")
    private Integer idProceso;

    @Column(name = "nombre_proceso", unique = true, nullable = false, length = 100)
    private String nombreProceso;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "activo")
    private Boolean activo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_clasificacion", nullable = false)
    private ClasificacionProceso clasificacion;

    @Column(name = "valor", precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "dias_habiles_entrega")
    private Integer diasHabilesEntrega;

    /** NS-08: el servicio incluye una visita: Nueva solicitud pide la dirección y rotula la ciudad «Ciudad de la visita». */
    @Builder.Default
    @Column(name = "requiere_direccion", nullable = false)
    private Boolean requiereDireccion = false;

    /** NS-08: el servicio incluye poligrafía o VerifEye: la ciudad se rotula «Ciudad donde presentará la prueba». */
    @Builder.Default
    @Column(name = "ciudad_es_de_prueba", nullable = false)
    private Boolean ciudadEsDePrueba = false;

    @Column(name = "aplica_precio_ciudad", nullable = false)
    private Boolean aplicaPrecioCiudad;

    /** El servicio necesita la hoja de vida del candidato: solo entonces el reloj arranca en pausa esperando el link (SE-05). Antecedentes no la necesita. */
    @Builder.Default
    @Column(name = "requiere_hoja_vida", nullable = false)
    private Boolean requiereHojaVida = true;

    /** Años hacia atrás que cubre la verificación laboral de este estudio (LB-02, V249); null = sin sugerencia. */
    @Column(name = "anios_periodo_laboral")
    private Integer aniosPeriodoLaboral;

    /** Puntos clave cortos para la tarjeta de la tienda — un punto por línea. */
    @Column(name = "puntos_clave", columnDefinition = "TEXT")
    private String puntosClave;

    // ── Textos comerciales y tienda (TP-01, V257) ──

    /** Nombre que ve el cliente; vacío = se usa nombreProceso. */
    @Column(name = "titulo_comercial", length = 120)
    private String tituloComercial;

    @Column(name = "frase_principal", length = 160)
    private String frasePrincipal;

    /** «Qué es». */
    @Column(name = "descripcion_corta", length = 400)
    private String descripcionCorta;

    /** Chips de «Qué incluye», uno por línea (máx. 6). */
    @Column(name = "incluye", columnDefinition = "TEXT")
    private String incluye;

    @Column(name = "ideal_para", length = 200)
    private String idealPara;

    @Column(name = "unidad_singular", length = 30)
    private String unidadSingular;

    @Column(name = "unidad_plural", length = 30)
    private String unidadPlural;

    /** Solo admin, para margen y alertas. NUNCA se expone al cliente. */
    @Column(name = "costo_directo", precision = 15, scale = 2)
    private BigDecimal costoDirecto;

    @Builder.Default
    @Column(name = "visible_en_tienda", nullable = false)
    private Boolean visibleEnTienda = true;

    @Builder.Default
    @Column(name = "aplica_iva", nullable = false)
    private Boolean aplicaIva = true;

    /** TP-05: el servicio aparece en la lista de precios pospago (la usa TP-11). */
    @Builder.Default
    @Column(name = "ofrecer_pospago", nullable = false)
    private Boolean ofrecerPospago = true;

    /** TP-05: precio pospago especial; vacío = el del plan de 100 unidades (TP-11). */
    @Column(name = "precio_pospago_propio", precision = 15, scale = 2)
    private BigDecimal precioPospagoPropio;

    /** TP-11: PLAN_100 (por omisión) = el precio pospago es el del plan de 100; PROPIO = precioPospagoPropio. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "modo_precio_pospago", nullable = false, length = 20)
    private com.polygraph.erp.shared.enums.ModoPrecioPospago modoPrecioPospago = com.polygraph.erp.shared.enums.ModoPrecioPospago.PLAN_100;

    /** LP-01: quién y cuándo cambió por última vez el precio pospago o «ofrecido» (null = nunca desde la lista pospago). */
    @Column(name = "precio_pospago_actualizado_por")
    private Long precioPospagoActualizadoPor;

    @Column(name = "precio_pospago_actualizado_en")
    private java.time.LocalDateTime precioPospagoActualizadoEn;

    /** Formato adicional que el cliente diligencia al solicitar este proceso (poligrafías); null = ninguno. */
    @Enumerated(EnumType.STRING)
    @Column(name = "formato_solicitud", length = 20)
    private com.polygraph.erp.modules.solicitudes.formato.FormatoSolicitud formatoSolicitud;

    // ── Calificación de riesgo (ver docs/modelo-calificacion-riesgo.md §2) ──

    /** % mínimo de peso que debe quedar realizado para no mandar el caso a revisión manual. */
    @Builder.Default
    @Column(name = "cobertura_minima", nullable = false, precision = 5, scale = 2)
    private BigDecimal coberturaMinima = new BigDecimal("100");

    /** Criticidad del cargo con la que se califica por defecto; el gestor la ajusta por caso. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "criticidad_defecto", nullable = false, length = 10)
    private CriticidadCargo criticidadDefecto = CriticidadCargo.ESTANDAR;

    /** Sube con cada cambio de pesos/obligatorios/cobertura/criticidad — cada cálculo guarda con
     *  qué versión se hizo. */
    @Builder.Default
    @Column(name = "version_calificacion", nullable = false)
    private Integer versionCalificacion = 1;
}
