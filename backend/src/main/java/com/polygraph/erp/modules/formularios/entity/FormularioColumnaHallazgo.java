package com.polygraph.erp.modules.formularios.entity;

import jakarta.persistence.*;
import lombok.*;

/** Hallazgo del catálogo (codigos_calificacion.codigo) que genera UNA opción de una lista del
 *  formulario — ej. "Estado del documento = No encontrada" → LEG-01 (V205). */
@Entity
@Table(name = "formulario_columna_hallazgos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "columna")
@EqualsAndHashCode(exclude = "columna")
public class FormularioColumnaHallazgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_columna", nullable = false)
    private FormularioColumna columna;

    @Column(name = "opcion", nullable = false, length = 200)
    private String opcion;

    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;
}
