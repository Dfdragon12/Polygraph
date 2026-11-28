package com.polygraph.erp.modules.formularios.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Puntos de riesgo de UNA respuesta posible de un campo calificable (Lista o Sí/No) — el
 *  "hallazgo" del modelo de riesgo vive aquí, dentro de la plantilla. opcion es el texto exacto de
 *  la opción de la lista, o 'true'/'false' en campos Sí/No. */
@Entity
@Table(name = "formulario_columna_puntajes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "columna")
@EqualsAndHashCode(exclude = "columna")
public class FormularioColumnaPuntaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_columna", nullable = false)
    private FormularioColumna columna;

    @Column(name = "opcion", nullable = false, length = 200)
    private String opcion;

    @Builder.Default
    @Column(name = "puntos", nullable = false, precision = 5, scale = 2)
    private BigDecimal puntos = BigDecimal.ZERO;

    /** Si quien diligencia elige esta respuesta, el subproceso queda en 100 y el estudio en ALTO. */
    @Builder.Default
    @Column(name = "es_critico", nullable = false)
    private Boolean esCritico = false;
}
