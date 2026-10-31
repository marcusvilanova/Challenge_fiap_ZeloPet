# IoB / IoT — status

**Não implementado na Sprint 4.** A Sprint 3 documentou a arquitetura de ingestão (`POST /api/iot/events`, agregação por janela, alerta de mudança de rotina), mas dois fatos mudaram o escopo desta entrega:

1. A pasta de dados fornecida pela equipe **não contém** o dataset de sensores (o "Movement Sensor Dataset for Dog Behavior Classification" é apenas citado como referência na Sprint 3). Sem série temporal real, qualquer detector de mudança de rotina seria validado só contra dados sintéticos criados por nós.
2. O enunciado da Sprint 4 pede a implementação da solução de IA definida na entrega anterior, aceitando *agentes conversacionais, classificação e LLMs*; o caminho crítico priorizado no próprio documento da Sprint 3 era **D2 (triagem textual) + D3 (visão)**.

Decisão: concentrar o esforço em fazer o chatbot e o classificador funcionarem de ponta a ponta, com validação honesta, em vez de simular IoB. O contrato proposto na Sprint 3 continua válido como próximo passo:

```json
{ "pet_id": "PET-0001", "device_id": "DEV-0001", "occurred_at": "2026-09-01T18:00:00Z",
  "metric": "activity_duration", "value": 32.5, "unit": "minutes", "activity_type": "walking",
  "source": "wearable_historical_dataset" }
```

Ponto de integração previsto: o `PriorityEngine` já aceita fatores de contexto (`context_factors`); uma queda de atividade seria mais um fator, **nunca** um diagnóstico.
