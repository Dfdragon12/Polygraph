package com.polygraph.erp.modules.catalogo.entity;

import com.polygraph.erp.modules.servicios.entity.Proceso;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "documentos_requeridos_proceso")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "proceso")
public class DocumentoRequeridoProceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proceso", nullable = false)
    private Proceso proceso;

    @Column(name = "clave", nullable = false, length = 100)
    private String clave;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "obligatorio", nullable = false)
    private Boolean obligatorio;

    @Column(name = "orden", nullable = false)
    private Integer orden;
}
