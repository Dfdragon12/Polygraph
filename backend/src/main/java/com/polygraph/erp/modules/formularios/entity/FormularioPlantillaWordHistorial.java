package com.polygraph.erp.modules.formularios.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Una versión anterior del .docx con marcadores de un subproceso — se crea una fila aquí cada
 *  vez que se reemplaza la plantilla activa (FormularioPlantillaConfig.plantillaWordRuta), con
 *  la ruta y los datos de carga de la versión SALIENTE, justo antes de sobrescribirla. */
@Entity
@Table(name = "formulario_plantilla_word_historial")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormularioPlantillaWordHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_progreso", nullable = false)
    private TipoProgreso tipoProgreso;

    @Column(nullable = false)
    private String ruta;

    @Column(name = "fecha_carga", nullable = false)
    private LocalDateTime fechaCarga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_carga")
    private Usuario usuarioCarga;
}
