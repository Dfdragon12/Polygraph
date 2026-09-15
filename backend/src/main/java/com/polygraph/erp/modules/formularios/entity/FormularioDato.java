package com.polygraph.erp.modules.formularios.entity;

import jakarta.persistence.*;
import lombok.*;

/** El valor real que escribió el analista. fila es null para secciones CAMPOS (un solo valor
 *  por columna por formulario); va lleno para secciones TABLA (un valor por columna por fila). */
@Entity
@Table(name = "formulario_datos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormularioDato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_dato")
    private Integer idDato;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_formulario", nullable = false)
    private Formulario formulario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_columna", nullable = false)
    private FormularioColumna columna;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fila")
    private FormularioFila fila;

    @Column(name = "valor", columnDefinition = "TEXT")
    private String valor;
}
