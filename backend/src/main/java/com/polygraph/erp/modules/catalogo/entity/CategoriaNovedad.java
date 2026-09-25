package com.polygraph.erp.modules.catalogo.entity;

import jakarta.persistence.*;
import lombok.*;

/** Agrupa los tipos de novedad en la lista desplegable "¿Qué pasó?" (ej. "Asistencia del
 *  candidato"). Administrable desde admin/catálogo → Tipos de novedad. */
@Entity
@Table(name = "categorias_novedad")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaNovedad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria_novedad")
    private Integer idCategoriaNovedad;

    /** Identificador estable (MAYÚSCULAS_CON_GUION_BAJO) — no cambia aunque se renombre. */
    @Column(name = "clave", nullable = false, unique = true, length = 40)
    private String clave;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** Clave de la paleta del frontend (red, orange, amber, green, sky, blue, violet, slate, gray). */
    @Builder.Default
    @Column(name = "color", nullable = false, length = 20)
    private String color = "gray";

    @Builder.Default
    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
