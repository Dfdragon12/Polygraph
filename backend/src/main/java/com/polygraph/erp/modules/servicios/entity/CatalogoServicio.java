package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.shared.enums.CategoriaServicio;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "catalogo_servicios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogoServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_catalogo")
    private Integer idCatalogo;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 40)
    private CategoriaServicio categoria;

    @Column(name = "precio_base", precision = 12, scale = 2)
    private BigDecimal precioBase;

    @Column(name = "activo")
    private Boolean activo;

    @Column(name = "orden")
    private Integer orden;

    @Column(name = "dias_habiles_entrega")
    private Integer diasHabilesEntrega;
}
