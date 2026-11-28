package com.polygraph.erp.modules.formularios.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Configuración de la plantilla que no es una sección: la ruta del .docx diseñado en Word con
 *  marcadores ${...} que el administrador subió para este tipo de subproceso. Una fila por tipo
 *  de subproceso, y solo existe si el administrador ya subió algo. El diseño visual (logo,
 *  tipografía, marca de agua) no se configura aquí — vive dentro del .docx subido. Qué datos del
 *  candidato se muestran tampoco va aquí — se arma columna por columna (ver
 *  FormularioColumna.campoCandidato). */
@Entity
@Table(name = "formulario_plantilla_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormularioPlantillaConfig {

    @Id
    @Column(name = "id_tipo_progreso")
    private Integer idTipoProgreso;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id_tipo_progreso")
    private TipoProgreso tipoProgreso;

    @Column(name = "plantilla_word_ruta")
    private String plantillaWordRuta;

    @Column(name = "fecha_carga")
    private LocalDateTime fechaCarga;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_carga")
    private Usuario usuarioCarga;
}
