package com.polygraph.erp.modules.servicios.entity;

import jakarta.persistence.*;
import lombok.*;

/** Un tema que el cliente pidió evaluar en la poligrafía de una solicitud. El nombre se copia del
 *  catálogo (PreguntaPoligrafia) al pedirlo. */
@Entity
@Table(name = "servicio_preguntas_poligrafia")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicioPreguntaPoligrafia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_servicio", nullable = false)
    private Servicio servicio;

    @Column(name = "id_pregunta")
    private Integer idPregunta;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** Lo que escribió el cliente en "Otros ¿Cuál?". */
    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;

    @Builder.Default
    @Column(name = "orden", nullable = false)
    private Integer orden = 0;
}
