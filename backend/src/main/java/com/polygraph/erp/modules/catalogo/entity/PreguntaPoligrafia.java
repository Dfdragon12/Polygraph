package com.polygraph.erp.modules.catalogo.entity;

import jakarta.persistence.*;
import lombok.*;

/** Tema que el cliente puede pedir evaluar en una poligrafía (hasta 4 por solicitud). Administrable
 *  desde Admin → Novedades → Temas de poligrafía. La solicitud guarda el TEXTO elegido, no el id:
 *  quitar o renombrar un tema no cambia lo que ya se pidió. */
@Entity
@Table(name = "preguntas_poligrafia")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreguntaPoligrafia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** El cliente debe escribir cuál (el tema "Otros ¿Cuál?"). */
    @Builder.Default
    @Column(name = "requiere_detalle", nullable = false)
    private Boolean requiereDetalle = false;

    /** Grupo al que pertenece (ej. «Conducta laboral»); texto libre, null = sin grupo. */
    @Column(name = "grupo", length = 100)
    private String grupo;

    @Builder.Default
    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
