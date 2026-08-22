package com.polygraph.erp.modules.usuarios.entity;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

@Entity
@Table(name = "empleados")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuariosInternos {

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

    @Column(name = "novedades_sala", columnDefinition = "TEXT")
    private String novedadesSala;

    @Column(name = "zonas_visita", length = 200)
    private String zonasVisita;

    @Column(name = "tipo_documento", length = 20)
    private String tipoDocumento;

    @Column(name = "documento", length = 20)
    private String documento;

    @Column(name = "id_ciudad_residencia")
    private Integer idCiudadResidencia;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fecha_ingreso", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fechaIngreso;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "empleados_tipos_progreso",
        joinColumns = @JoinColumn(name = "id_empleado"),
        inverseJoinColumns = @JoinColumn(name = "id_tipo_progreso"))
    @Builder.Default
    private List<TipoProgreso> subprocesosAsignados = new ArrayList<>();
}