package com.polygraph.erp.modules.catalogo.entity;

import com.polygraph.erp.shared.enums.Rol;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/** Una opción de "¿Qué pasó?" al reportar una novedad de subproceso — antes era el enum
 *  TipoNovedadSubproceso. roles: a qué rol ejecutor (analista/poligrafista/visitador) le aparece.
 *  Si ya se usó en alguna novedad no se borra, se desactiva. */
@Entity
@Table(name = "tipos_novedad")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "categoria")
@EqualsAndHashCode(of = "idTipoNovedad")
public class TipoNovedad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_novedad")
    private Integer idTipoNovedad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_categoria_novedad", nullable = false)
    private CategoriaNovedad categoria;

    /** Identificador estable (MAYÚSCULAS_CON_GUION_BAJO) — no cambia aunque se renombre. */
    @Column(name = "clave", nullable = false, unique = true, length = 40)
    private String clave;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** Ejemplo que aparece en el campo "Detalle" al elegir este tipo. */
    @Column(name = "texto_ayuda", length = 300)
    private String textoAyuda;

    @Builder.Default
    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    /** El cliente ve las novedades de este tipo en el detalle de su solicitud (solo tipo, estado y
     *  respuesta — nunca la descripción del ejecutor). V193. */
    @Builder.Default
    @Column(name = "visible_cliente", nullable = false)
    private Boolean visibleCliente = false;

    /** Al abrir una novedad de este tipo el reloj de la solicitud se detiene (la demora no es de Polygraph) hasta que se resuelva. */
    @Builder.Default
    @Column(name = "pausa_reloj", nullable = false)
    private Boolean pausaReloj = false;

    /** De quién es la causa: POLYGRAPH, CANDIDATO o CLIENTE. Si es del candidato o del cliente y pausa el reloj, no cuenta como retraso. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "imputable_a", nullable = false, length = 10)
    private com.polygraph.erp.shared.enums.ImputableA imputableA = com.polygraph.erp.shared.enums.ImputableA.POLYGRAPH;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tipos_novedad_roles", joinColumns = @JoinColumn(name = "id_tipo_novedad"))
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 30)
    private Set<Rol> roles = new HashSet<>();

    public boolean aplicaA(Rol rolResponsable) {
        return roles.contains(rolResponsable);
    }
}
