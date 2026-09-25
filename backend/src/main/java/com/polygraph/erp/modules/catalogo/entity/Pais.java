package com.polygraph.erp.modules.catalogo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "paises")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pais {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pais")
    private Integer idPais;

    @Column(name = "nombre", nullable = false, length = 100, unique = true)
    private String nombre;

    @Column(name = "codigo_iso", nullable = false, length = 2, unique = true)
    private String codigoIso;
}
