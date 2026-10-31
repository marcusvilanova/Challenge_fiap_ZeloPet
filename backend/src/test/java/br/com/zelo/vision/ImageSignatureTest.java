package br.com.zelo.vision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSignatureTest {

    @Test
    @DisplayName("reconhece JPEG, PNG e WebP pela assinatura")
    void detectsSupportedFormats() {
        assertThat(ImageSignature.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).contains("jpg");
        assertThat(ImageSignature.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10})).contains("png");
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(ImageSignature.detect(webp)).contains("webp");
    }

    @Test
    @DisplayName("recusa texto, GIF, SVG, vazio e nulo mesmo que o nome do arquivo diga .jpg")
    void rejectsEverythingElse() {
        assertThat(ImageSignature.detect("texto qualquer".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageSignature.detect("GIF89a....".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageSignature.detect("<svg xmlns=''></svg>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageSignature.detect(new byte[0])).isEmpty();
        assertThat(ImageSignature.detect(null)).isEmpty();
        assertThat(ImageSignature.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
    }
}
