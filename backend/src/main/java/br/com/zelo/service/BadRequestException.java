package br.com.zelo.service;

/** Erro de validação de negócio com mensagem segura para exibir ao usuário. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
