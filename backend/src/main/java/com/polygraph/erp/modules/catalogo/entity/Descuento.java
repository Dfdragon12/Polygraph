package com.polygraph.erp.modules.catalogo.entity;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.shared.enums.AlcanceDescuento;
import com.polygraph.erp.shared.enums.TipoDescuento;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Descuento configurable por el admin — reemplaza la idea de precios fijos por una regla
 * dinámica: % o monto fijo, con vigencia opcional, y aplicable a todo el catálogo, una
 * categoría o un proceso puntual. Con o sin código: si "codigo" es null, se aplica
 * automáticamente a lo que coincida con su alcance; si tiene código, el cliente debe
 * ingresarlo (cupón).
 */
@Entity
@Table(name = "descuentos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Descuento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_descuento")
    private Long idDescuento;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "codigo", unique = true, length = 50)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoDescuento tipo;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "alcance", nullable = false, length = 20)
    private AlcanceDescuento alcance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clasificacion")
    private ClasificacionProceso clasificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proceso")
    private Proceso proceso;

    /** Solo relevante para tipo PORCENTAJE — límite en pesos de cuánto puede reducir. */
    @Column(name = "monto_maximo_descuento", precision = 15, scale = 2)
    private BigDecimal montoMaximoDescuento;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void alCrear() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }
}
