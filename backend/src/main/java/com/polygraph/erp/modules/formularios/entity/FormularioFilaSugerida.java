package com.polygraph.erp.modules.formularios.entity;

import jakarta.persistence.*;
import lombok.*;

/** Plantilla: fila que aparece precargada cuando se crea un formulario nuevo, para secciones
 *  tipo TABLA (ej. "Procuraduría" en "Fuentes consultadas") — evita que el analista tenga que
 *  escribirlas siempre. El analista puede agregar filas adicionales libres además de estas. */
@Entity
@Table(name = "formulario_filas_sugeridas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormularioFilaSugerida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_fila_sugerida")
    private Integer idFilaSugerida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_seccion", nullable = false)
    private FormularioSeccion seccion;

    @Column(name = "etiqueta", nullable = false, length = 150)
    private String etiqueta;

    @Column(name = "orden", nullable = false)
    private Integer orden;
}
