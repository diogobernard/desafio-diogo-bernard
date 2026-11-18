SELECT
    count(*) - count(DISTINCT (serie, numero)) AS duplicidades,
    coalesce(max(numero) - min(numero) + 1 - count(DISTINCT numero), 0) AS lacunas
FROM notafiscal.nota_fiscal
WHERE cliente_id = :'cliente'
GROUP BY serie
ORDER BY serie;

SELECT serie, count(*) AS notas, min(numero) AS primeiro_numero, max(numero) AS ultimo_numero
FROM notafiscal.nota_fiscal
WHERE cliente_id = :'cliente'
GROUP BY serie
ORDER BY serie;
