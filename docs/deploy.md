# Plano de deploy

## Ambientes

1. **Desenvolvimento:** Docker Compose, banco descartável quando aplicável e dados
   fictícios.
2. **Integração:** imagem imutável produzida pelo CI, PostgreSQL e Kafka isolados;
   migrations e smoke test executados antes de avançar para o próximo ambiente.
3. **Homologação:** topologia próxima à produção, validação de acesso, métricas,
   alertas, carga representativa e ensaio de rollback.
4. **Produção:** tasks redundantes em múltiplas zonas, RDS/Aurora, MSK, segredos
   gerenciados e telemetria centralizada.

## Pipeline de entrega

- Construir uma imagem por commit e avançá-la entre ambientes sem recompilar.
- Executar `build`, testes, `koverVerify`, detekt e ktlint no CI; bloquear o avanço
  se uma etapa falhar.
- Aplicar blue/green ou canary atrás do balanceador, observando erros HTTP, tempo de
  resposta, readiness, lag Kafka, outbox pendente e falhas de emissão.
- Manter a versão anterior disponível até validar a nova versão durante a janela de
  observação definida pela operação.

## Migrations e compatibilidade

Usar **expand/contract**. Primeiro adicionar estruturas compatíveis com a versão em
produção; publicar a versão que escreve/entende ambos os formatos quando necessário;
migrar dados; só depois remover colunas ou comportamentos antigos em outra entrega.
Executar Flyway como etapa controlada de deploy, com backup e verificação de
compatibilidade antes de iniciar novas tasks. Nunca fazer rollback automático de uma
migration destrutiva.

## Rollback

1. Interromper a implantação e rotear o tráfego para o conjunto saudável anterior.
2. Reverter a imagem da aplicação, desde que ela ainda seja compatível com o schema
   expandido.
3. Não apagar solicitações nem eventos do outbox; acompanhar consumidores até
   drenarem ou retomarem o processamento.
4. Restaurar backup/PITR do banco somente sob procedimento aprovado de incidente,
   avaliando perda de gravações após o ponto de restauração.
5. Preservar evidências sanitizadas e abrir nova correção para migration incompatível.

## Checklist de release

- [ ] CI verde, cobertura e análise estática aprovadas.
- [ ] Imagem identificada por digest e artefato anterior disponível.
- [ ] Migration revisada, aditiva e compatível com rollback da aplicação.
- [ ] Segredos e permissões IAM configurados fora do artefato.
- [ ] Health checks, alarmes, dashboards e DLQs conferidos.
- [ ] Plano de canary/blue-green, responsáveis e janela de rollback definidos.
- [ ] Smoke test, outbox e consumidores verificados após a implantação.
