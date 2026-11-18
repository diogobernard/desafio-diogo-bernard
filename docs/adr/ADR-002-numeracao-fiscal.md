# ADR-002: Numeração fiscal transacional

- **Status:** aceito
- **Contexto:** cada série exige números únicos e sem lacunas causadas por rollback.
- **Decisão:** incrementar a linha da série em `contador_numeracao` com
  `UPDATE ... RETURNING` dentro da transação que grava a nota; adquirir esse lock por
  último.
- **Consequências:** o rollback também desfaz a reserva e o lock serializa a emissão
  por série. Séries distintas podem evoluir em paralelo.
- **Alternativa rejeitada:** `SEQUENCE`, cujo avanço não é revertido pela transação.
