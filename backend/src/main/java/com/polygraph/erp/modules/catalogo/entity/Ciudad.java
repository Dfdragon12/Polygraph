package com.polygraph.erp.modules.catalogo.entity;

import com.polygraph.erp.shared.enums.NivelCiudad;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_ciudad", nullable = false, length = 30)
    private NivelCiudad nivelCiudad;

    @Column(name = "pendiente_confirmacion", nullable = false)
    private Boolean pendienteConfirmacion;
}
