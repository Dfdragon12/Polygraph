package com.polygraph.erp.modules.catalogo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "barrios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Barrio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_barrio")
    private Integer idBarrio;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ciudad", nullable = false)
    private Ciudad ciudad;

    @Column(name = "pendiente_confirmacion", nullable = false)
    private Boolean pendienteConfirmacion;
}
