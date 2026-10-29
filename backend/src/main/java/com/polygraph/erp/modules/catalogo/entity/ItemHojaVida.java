package com.polygraph.erp.modules.catalogo.entity;

import jakarta.persistence.*;
import lombok.*;

/** Opción de una lista de la hoja de vida del candidato (libreta militar, EPS, fondo de pensiones,
 *  tipo de contrato) o pregunta de declaración. Administrable desde Admin → Novedades → Hoja de vida
 *  del candidato. La hoja de vida guarda el TEXTO elegido, no el id: borrar una opción no cambia
 *  lo que un candidato ya respondió. */
@Entity
@Table(name = "items_hoja_vida")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemHojaVida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "lista", nullable = false, length = 30)
    private ListaHojaVida lista;

    @Column(name = "nombre", nullable = false, columnDefinition = "TEXT")
    private String nombre;

    /** Solo en PREGUNTA: la explicación que ve el candidato debajo de la pregunta. */
    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Builder.Default
    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
