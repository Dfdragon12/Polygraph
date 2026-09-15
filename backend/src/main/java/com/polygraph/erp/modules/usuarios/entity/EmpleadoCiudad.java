package com.polygraph.erp.modules.usuarios.entity;

import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import jakarta.persistence.*;
import lombok.*;

// Relación real empleado-ciudad (POLIGRAFISTA: una fila por ciudad+sala; VISITADOR: una fila por
// ciudad de cobertura, sin sala). Reemplaza los antiguos campos de texto libre sala_encargada/
// zonas_visita para poder filtrar "quién cubre la ciudad X" al asignar un servicio.
@Entity
@Table(name = "empleado_ciudades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpleadoCiudad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empleado_ciudad")
    private Integer idEmpleadoCiudad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_empleado", nullable = false)
    private UsuariosInternos empleado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ciudad", nullable = false)
    private Ciudad ciudad;

    // Solo aplica a POLIGRAFISTA; null para VISITADOR (solo cubre la ciudad, sin sala física).
    @Column(name = "numero_sala")
    private Integer numeroSala;
}
