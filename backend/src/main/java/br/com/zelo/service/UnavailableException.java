package br.com.zelo.service;

/** Serviço dependente (ex.: visão computacional) indisponível: vira 503 com mensagem amigável. */
public class UnavailableException extends RuntimeException {

    public UnavailableException(String message) {
        super(message);
    }
}
