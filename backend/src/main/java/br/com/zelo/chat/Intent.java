package br.com.zelo.chat;

/** Intenção não clínica da mensagem. Sintomas são tratados pelo motor de triagem, não por aqui. */
public enum Intent {
    GREETING,
    THANKS,
    FAREWELL,
    HELP,
    CARE_PLAN,
    MEDICATION_REQUEST,
    SHARE,
    YES,
    NO,
    FINISH,
    NEW_REPORT,
    OTHER
}
