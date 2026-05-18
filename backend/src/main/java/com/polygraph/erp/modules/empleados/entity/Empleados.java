package com.polygraph.erp.modules.empleados.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Table(name = "empleados")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Empleados {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) 
    @Column(name = "id_empleado")
    private Integer idEmpleado;

    @Column(name = "tipo_empleado", nullable = false, length = 20)
    private String tipoEmpleado;      

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellido", length = 100)
    private String apellido;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "sala_encargada", length = 100)
    private String salaEncargada;

    @Column(name = "zonas_visita", length = 200)
    private String zonasVisita;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fecha_ingreso", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaIngreso;
}