package com.polygraph.erp.modules.formularios.entity;

import jakarta.persistence.*;
import lombok.*;

/** Instancia: una fila real dentro de UN formulario, para una sección tipo TABLA — se crea una
 *  por cada fila sugerida de la plantilla al abrir el formulario por primera vez, y el analista
 *  puede agregar/quitar filas libremente además de esas. */
@Entity
@Table(name = "formulario_filas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormularioFila {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_fila")
    private Integer idFila;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_formulario", nullable = false)
    private Formulario formulario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_seccion", nullable = false)
    private FormularioSeccion seccion;

    @Column(name = "etiqueta", nullable = false, length = 150)
    private String etiqueta;

    @Column(name = "orden", nullable = false)
    private Integer orden;
}
