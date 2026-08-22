package com.polygraph.erp.modules.catalogo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ciudades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ciudad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ciudad")
    private Integer idCiudad;

    @Column(name = "nombre_ciudad", nullable = false, length = 150)
    private String nombreCiudad;

    @Column(name = "departamento", nullable = false, length = 100)
    private String departamento;

    @Column(name = "codigo_dane_ciudad", length = 10)
    private String codigoDaneCiudad;

    @Column(name = "codigo_dane_depto", length = 2)
    private String codigoDaneDepto;
}
