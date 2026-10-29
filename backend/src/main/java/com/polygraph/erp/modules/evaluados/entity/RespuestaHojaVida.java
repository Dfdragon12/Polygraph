package com.polygraph.erp.modules.evaluados.entity;

import jakarta.persistence.*;
import lombok.*;

/** Respuesta del candidato a una pregunta de declaración (ItemHojaVida de la lista PREGUNTA).
 *  Guarda el texto de la pregunta tal como la respondió. */
@Entity
@Table(name = "respuestas_hoja_vida")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "hojaVidaEvaluado")
public class RespuestaHojaVida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_hoja_vida", nullable = false)
    private HojaVidaEvaluado hojaVidaEvaluado;

    /** null si el admin borró la pregunta después. */
    @Column(name = "id_item")
    private Integer idItem;

    @Column(name = "pregunta", nullable = false, columnDefinition = "TEXT")
    private String pregunta;

    /** true = Sí, false = No, null = sin responder todavía. */
    @Column(name = "respuesta")
    private Boolean respuesta;

    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;
}
