package com.polygraph.erp.modules.catalogo.entity;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "procesos_tipos_progreso")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"proceso", "tipoProgreso"})
public class ProcesoTipoProgreso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proceso", nullable = false)
    private Proceso proceso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_progreso", nullable = false)
    private TipoProgreso tipoProgreso;

    @Column(name = "habilitado")
    private Boolean habilitado;

    @Column(name = "orden_en_proceso")
    private Integer ordenEnProceso;

    /** También lo usa el motor de riesgo: si un obligatorio queda No realizado, revisión manual. */
    @Column(name = "obligatorio")
    private Boolean obligatorio;

    /** % que aporta este subproceso al puntaje de riesgo del proceso. null = sin configurar (el
     *  proceso no califica); 0 = se hace pero no califica. */
    @Column(name = "peso", precision = 5, scale = 2)
    private BigDecimal peso;
}
