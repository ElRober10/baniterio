package com.baniterio.api.perfil;

/**
 * Conflicto de estado al declarar o aceptar un vínculo de pareja → HTTP 409.
 * {@code codigo} distingue el caso para el cliente:
 * <ul>
 *   <li>{@code TELEFONO_YA_EMPAREJADO} — el teléfono es de un miembro que ya
 *       tiene un vínculo vivo con otra persona.
 *   <li>{@code YA_TIENE_PAREJA} — al aceptar, alguno de los dos adquirió otro
 *       vínculo vivo entre medias.
 * </ul>
 */
public class VinculoConflictoException extends RuntimeException {

    private final String codigo;

    public VinculoConflictoException(String codigo) {
        super(codigo);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
