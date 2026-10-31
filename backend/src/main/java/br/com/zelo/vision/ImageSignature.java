package br.com.zelo.vision;

import java.util.Optional;

/** Valida o tipo REAL do arquivo pela assinatura (magic bytes), ignorando extensão e Content-Type enviados. */
final class ImageSignature {

    private static final int RIFF_WEBP_MIN_LENGTH = 12;

    private ImageSignature() {
    }

    /** @return a extensão canônica ("jpg", "png" ou "webp") ou vazio se não for uma imagem aceita */
    static Optional<String> detect(byte[] b) {
        if (b == null || b.length < 4) {
            return Optional.empty();
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of("jpg");
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return Optional.of("png");
        }
        boolean riffWebp = b.length >= RIFF_WEBP_MIN_LENGTH && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return riffWebp ? Optional.of("webp") : Optional.empty();
    }
}
