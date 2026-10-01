package castbridge.server.licenses;

/** Turns an {@link ActivationSigner.ActivationRequest} into the bytes to sign and into the text given to the owner. */
public interface ActivationEncoder {

    /** The exact bytes covered by the Ed25519 signature. */
    byte[] payload(ActivationSigner.ActivationRequest request, String kid);

    /** The text of the activation: payload + signature, in the textual coding of the format. */
    String render(ActivationSigner.ActivationRequest request, String kid, byte[] payload, byte[] signature);

    /** Which format this encoder speaks (shown on the issue page so that nobody mistakes the provisional one for the real one). */
    String formatName();
}
