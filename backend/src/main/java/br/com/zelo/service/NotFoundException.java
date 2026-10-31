package br.com.zelo.service;

/** Recurso inexistente OU de outro tutor: ambos viram 404 para não revelar a existência de dados alheios. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
