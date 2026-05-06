package com.polygraph.erp.modules.servicios.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "procesos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Proceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proceso")
    private Integer idProceso;

    @Column(name = "nombre_proceso", unique = true, nullable = false, length = 100)
    private String nombreProceso;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "activo")
    private Boolean activo;
}
