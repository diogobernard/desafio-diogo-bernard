#!/usr/bin/env python3
"""Atualiza a tabela de resultados mais recentes por volume de carga."""

import argparse
import html
import re
from html.parser import HTMLParser
from pathlib import Path

INICIO = "<!-- RESULTADOS-CARGA:INICIO -->"
FIM = "<!-- RESULTADOS-CARGA:FIM -->"
INICIO_POST = "<!-- RESULTADOS-CARGA-POST:INICIO -->"
FIM_POST = "<!-- RESULTADOS-CARGA-POST:FIM -->"
PADRAO_POOL = re.compile(
    r"Pool Hikari: role=(\w+) pool=(\S+) active=(\d+) idle=\d+ pending=(\d+) max=\d+ "
    r"acquire_max_ms=([\d.]+) usage_max_ms=([\d.]+)"
)
PADRAO_RECURSO = re.compile(r"([\w.-]+)\|([\d.]+)%\|([\d.]+[KMG]?i?B) /")


class LinhasGatling(HTMLParser):
    def __init__(self):
        super().__init__()
        self.linhas = {}
        self.linha_id = None
        self.celula_aberta = False
        self.texto = ""
        self.celulas = []

    def handle_starttag(self, tag, attrs):
        atributos = dict(attrs)
        if tag == "tr" and atributos.get("id", "").startswith("req_"):
            self.linha_id = atributos["id"]
            self.celulas = []
        elif self.linha_id and tag == "td":
            self.celula_aberta = True
            self.texto = ""

    def handle_data(self, data):
        if self.celula_aberta:
            self.texto += data

    def handle_endtag(self, tag):
        if tag == "td" and self.celula_aberta:
            self.celulas.append(" ".join(self.texto.split()))
            self.celula_aberta = False
        elif tag == "tr" and self.linha_id:
            self.linhas[self.linha_id] = self.celulas
            self.linha_id = None


PADRAO_ERRO = re.compile(
    r'<td class="error-col-1[^>]*>(.*?)<span class="value"[^>]*>.*?</span></td>\s*'
    r'<td class="value error-col-2[^>]*>(\d+)</td>',
    re.S,
)
INDICADORES_TRANSPORTE = ("Exception", "Premature close", "Connection", "timeout", "Timeout")


def erros_gatling(index_html):
    """Classifica os KO do relatório em 503, HTTP 500, outros HTTP, transporte e outros."""
    tabela = re.search(r'<table id="container_errors".*?</table>', index_html, re.S)
    contagem = {}
    for mensagem, quantidade in PADRAO_ERRO.findall(tabela.group(0) if tabela else ""):
        texto = html.unescape(mensagem).strip()
        codigo = re.search(r"status\.find.*found (\d{3})", texto)
        if codigo:
            chave = f"HTTP {codigo.group(1)}"
        elif any(indicador in texto for indicador in INDICADORES_TRANSPORTE):
            chave = "transporte"
        else:
            chave = "outros"
        contagem[chave] = contagem.get(chave, 0) + int(quantidade)
    return contagem


def resumo_erros(contagem):
    if not contagem:
        return "0 KO"
    ordem = sorted(contagem.items(), key=lambda item: (item[0] == "transporte", item[0]))
    return "; ".join(f"{chave}: {quantidade}" for chave, quantidade in ordem)


def resumo_escoamento(caminho):
    if not caminho.exists():
        return "—"
    campos = dict(
        parte.split("=", 1) for parte in caminho.read_text().split() if "=" in parte
    )
    if campos.get("escoado") == "sim":
        return f"escoado em {campos.get('tempo_s', '?')} s após a carga (prazo {campos.get('prazo_s', '?')} s)"
    if campos.get("escoado") == "nao":
        return (
            f"não escoou em {campos.get('prazo_s', '?')} s: outbox {campos.get('outbox_restante', '?')}, "
            f"lag Kafka {campos.get('lag_restante', '?')}"
        )
    return "indisponível"


def amostragem_traces(reports):
    caminho = reports / "amostragem-traces.txt"
    if not caminho.exists():
        return "traces: não registrada"
    achado = re.search(r"amostragem_traces=([\d.]+)", caminho.read_text())
    return f"traces {float(achado.group(1)) * 100:g}%" if achado else "traces: não registrada"


def configuracao_ingresso(reports):
    caminho = reports / "ingresso-config.txt"
    if not caminho.exists():
        return "ingresso: padrão"
    valores = dict(re.findall(r"(\w+)=(\S+)", caminho.read_text()))
    return (
        f"fila {valores.get('ingresso_capacidade', '?')}, espera {valores.get('ingresso_espera_maxima', '?')}, "
        f"Tomcat {valores.get('tomcat_max_connections', '?')} conexões"
        + (f" e accept-count {valores['tomcat_accept_count']}" if "tomcat_accept_count" in valores else "")
        + (
            f", {valores['ingresso_escritores']} escritores, lote até {valores.get('ingresso_lote_maximo', '?')}"
            if "ingresso_escritores" in valores
            else ""
        )
    )


def series_prometheus(caminho):
    series = {}
    if not caminho.exists():
        return series
    for linha in caminho.read_text().splitlines():
        achado = re.match(r"^(\w+)(?:\{[^}]*\})?\s+([-+\d.eE]+|NaN)$", linha)
        if achado:
            series.setdefault(achado.group(1), []).append(float(achado.group(2)))
    return series


def amostras_fila(caminho):
    if not caminho.exists():
        return []
    valores = []
    for linha in caminho.read_text().splitlines():
        campos = linha.split("\t")
        if len(campos) == 2 and re.fullmatch(r"[\d.eE+-]+", campos[1]):
            valores.append(float(campos[1]))
    return valores


def resumo_ingresso(reports, requests):
    fila = amostras_fila(reports / f"ingresso-fila-{requests}.tsv")
    series = series_prometheus(reports / f"metricas-{requests}.txt")
    partes = []
    if fila:
        partes.append(f"fila pico {max(fila):g}, não vazia em {sum(v > 0 for v in fila)} de {len(fila)} amostras de 1 s")
    lotes = sum(series.get("notafiscal_ingresso_lote_tamanho_count", []))
    if lotes:
        soma = sum(series.get("notafiscal_ingresso_lote_tamanho_sum", []))
        partes.append(f"{lotes:g} lotes, tamanho médio {soma / lotes:.1f}")
        duracao = sum(series.get("notafiscal_ingresso_lote_duracao_seconds_sum", []))
        partes.append(f"gravação por lote média {duracao / lotes * 1000:.0f} ms")
    rejeitadas = sum(series.get("notafiscal_ingresso_rejeitadas_total", []))
    if partes:
        partes.append(f"{rejeitadas:g} rejeitadas")
    return "métricas de ingresso: " + "; ".join(partes) if partes else "métricas de ingresso: indisponíveis"


def linha_sql(caminho, chave):
    for linha in caminho.read_text().splitlines():
        celulas = [celula.strip() for celula in linha.split("|")]
        if celulas and celulas[0] == chave:
            return celulas
    return []


def numero(valor):
    return valor if valor and valor.lower() != "null" else "—"


def metricas_gatling(celulas):
    if not celulas or len(celulas) < 12:
        return "sem requisições"
    total, ok, ko = celulas[1:4]
    return f"{ok}/{total} OK, {ko} KO; p50/p95/p99/máx {celulas[7]}/{celulas[9]}/{celulas[10]}/{celulas[11]} ms"


def recursos_app(caminho):
    servicos = []
    for linha in caminho.read_text().splitlines():
        if "-api-1" in linha or "-worker-1" in linha:
            campos = linha.split()
            indice_cpu = next(
                (indice for indice, campo in enumerate(campos) if campo.endswith("%")),
                None,
            )
            if indice_cpu is None or len(campos) <= indice_cpu + 3 or campos[indice_cpu + 2] != "/":
                continue
            cpu = campos[indice_cpu].removesuffix("%")
            usado, limite = campos[indice_cpu + 1], campos[indice_cpu + 3]
            servicos.append((float(cpu), bytes_memoria(usado), bytes_memoria(limite)))
    if not servicos:
        return "indisponível"
    cpu_total = sum(cpu for cpu, _, _ in servicos)
    memoria_usada = sum(usado for _, usado, _ in servicos)
    memoria_limite = sum(limite for _, _, limite in servicos)
    percentual = memoria_usada / memoria_limite * 100 if memoria_limite else 0
    return (
        f"CPU {cpu_total:.2f}%; memória {formatar_memoria(memoria_usada)} / "
        f"{formatar_memoria(memoria_limite)} ({percentual:.2f}%)"
    )


def bytes_memoria(valor):
    unidades = {"B": 1, "KB": 1000, "MB": 1000**2, "GB": 1000**3,
                "KiB": 1024, "MiB": 1024**2, "GiB": 1024**3}
    correspondencia = re.fullmatch(r"([\d.]+)([KMG]?i?B)", valor)
    return float(correspondencia.group(1)) * unidades[correspondencia.group(2)]


def formatar_memoria(valor):
    return f"{valor / 1024**3:.2f}GiB"


def resumo_banco(reports, requests):
    rodada = linha_sql(reports / f"rodada-{requests}.txt", str(requests))
    if not rodada:
        return "sem registro SQL", "—"
    status = f"{rodada[1]} aceitas, {rodada[2]} emitidas, {rodada[3]} falhas, {rodada[4]} pendentes"
    emissao = "/".join(numero(valor) for valor in rodada[5:8])

    numeracao = (reports / f"numeracao-{requests}.txt").read_text()
    ocorrencia = re.search(
        r"duplicidades\s*\|\s*lacunas\s*\n[-+|\s]+\n\s*(\d+)\s*\|\s*(\d+)",
        numeracao,
    )
    integridade = f"{ocorrencia.group(1)} dup./{ocorrencia.group(2)} lacunas" if ocorrencia else "sem notas"
    return status, f"{emissao} ms; {integridade}"


def linha_resultado(requests, report, reports):
    gatling = LinhasGatling()
    with (report / "index.html").open(encoding="utf-8") as arquivo:
        conteudo = arquivo.read()
    gatling.feed(conteudo)
    erros = resumo_erros(erros_gatling(conteudo))
    escoamento = resumo_escoamento(reports / f"escoamento-{requests}.txt")
    post = gatling.linhas.get("req_post-solicitac---771956121", [])
    get = gatling.linhas.get("req_get-imediato-da--1865126812", [])
    estado, emissao = resumo_banco(reports, requests)
    metricas = reports / f"metricas-{requests}.txt"
    prometheus = "disponível" if "indisponível" not in metricas.read_text().lower() else "indisponível"
    recursos = recursos_app(reports / f"recursos-{requests}-fim.txt")
    identificador = report.name.rsplit("-", 1)[-1]
    caminho_report = f"../../app/build/reports/gatling/{report.name}/index.html"
    return (
        f"| {requests} | `{identificador}` | {metricas_gatling(post)} | "
        f"{metricas_gatling(get)} | {erros} | {estado} | {emissao} | {escoamento} | {recursos} | "
        f"Prometheus {prometheus}; {amostragem_traces(reports)}; {configuracao_ingresso(reports)}; "
        f"{resumo_ingresso(reports, requests)}; {perfil_instancia(reports)}; {carga_host(reports, requests)}; "
        f"[Gatling]({caminho_report}); "
        "[artefatos SQL](../../app/build/reports/load/) |"
    )


def normalizar_linha(linha):
    """Leva linhas de rodadas anteriores (8 colunas) ao formato atual (10 colunas)."""
    celulas = [celula.strip() for celula in linha.strip().strip("|").split("|")]
    if len(celulas) == 8:
        celulas.insert(4, "—")
        celulas.insert(7, "—")
    return "| " + " | ".join(celulas) + " |"


def linha_por_prefixo(linhas, prefixo):
    return next((celulas for chave, celulas in linhas.items() if chave.startswith(prefixo)), [])


def inteiro(valor):
    texto = str(valor).replace(",", "").strip()
    return int(texto) if texto.isdigit() else None


def valores_chave(caminho):
    if not caminho.exists():
        return {}
    pares = (linha.split("|", 1) for linha in caminho.read_text().splitlines() if "|" in linha)
    return {chave.strip(): inteiro(valor) for chave, valor in pares}


def consistencia_ingresso(caminho, aceitas):
    valores = valores_chave(caminho)
    if not valores:
        return "sem verificação"
    regras = (
        (f"{valores.get('solicitacoes')} gravadas para {aceitas} aceitas", valores.get("solicitacoes") != aceitas),
        (f"{valores.get('chaves_duplicadas')} chaves duplicadas", valores.get("chaves_duplicadas") != 0),
        (f"{valores.get('solicitacoes_sem_evento')} sem evento no outbox", valores.get("solicitacoes_sem_evento") != 0),
        (f"{valores.get('eventos_repetidos')} eventos repetidos", valores.get("eventos_repetidos") != 0),
    )
    problemas = [descricao for descricao, falhou in regras if falhou]
    if problemas:
        return "INCONSISTENTE: " + "; ".join(problemas)
    return (
        f"íntegra: {valores['solicitacoes']} solicitações com {valores['eventos_solicitacao']} eventos no outbox, "
        "sem duplicidade"
    )


def pool_escrita_api(caminho):
    picos = []
    for linha in caminho.read_text().splitlines() if caminho.exists() else []:
        achado = PADRAO_POOL.search(linha)
        if achado and achado.group(1) == "api" and "write" in achado.group(2):
            picos.append((int(achado.group(3)), int(achado.group(4)), float(achado.group(5)), float(achado.group(6))))
    if not picos:
        return "indisponível"
    ativas, pendentes, aquisicao, uso = (max(valores) for valores in zip(*picos))
    return f"ativas {ativas}, pendentes {pendentes}, aquisição máx {aquisicao:.0f} ms, uso máx {uso:.0f} ms"


def picos_recursos(caminho, servico):
    cpus, memorias = [], []
    for linha in caminho.read_text().splitlines() if caminho.exists() else []:
        achado = PADRAO_RECURSO.search(linha)
        if achado and achado.group(1).rsplit("-", 2)[-2] == servico:
            cpus.append(float(achado.group(2)))
            memorias.append(bytes_memoria(achado.group(3)))
    if not cpus:
        return "indisponível"
    return f"CPU máx {max(cpus):.0f}%, memória máx {formatar_memoria(max(memorias))}"


def transbordos_listen(caminho):
    if not caminho.exists():
        return "fila de conexões: sem coleta"
    leituras = dict(
        (linha.split(" ", 1)[0], dict(re.findall(r"(Listen\w+)=(\d+)", linha)))
        for linha in caminho.read_text().splitlines()
        if " " in linha
    )
    antes, depois = leituras.get("antes", {}), leituras.get("depois", {})
    if not antes or not depois:
        return "fila de conexões: sem coleta"
    delta = {chave: int(depois[chave]) - int(antes.get(chave, 0)) for chave in depois}
    return (
        f"fila de conexões: {delta.get('ListenOverflows', 0)} transbordos, "
        f"{delta.get('ListenDrops', 0)} SYN descartados"
    )


def meta_post(celulas, reports):
    caminho = reports / "meta-post.txt"
    achado = re.search(r"latencia_maxima_post_ms=(\d+)", caminho.read_text()) if caminho.exists() else None
    maximo = int(achado.group(1)) if achado else 3000
    if not celulas or len(celulas) < 12:
        return "sem requisições"
    total, ok, ko, maior = inteiro(celulas[1]), inteiro(celulas[2]), inteiro(celulas[3]), inteiro(celulas[11])
    atendida = ko == 0 and ok == total and maior is not None and maior <= maximo
    situacao = "atendida" if atendida else "NÃO atendida"
    return f"{situacao}: máximo {maior} ms para meta de {maximo} ms; {ok}/{total} aceitos"


def conexoes_cliente(reports):
    caminho = reports / "meta-post.txt"
    texto = caminho.read_text() if caminho.exists() else ""
    conexoes = "conexões reaproveitadas (gateway)" if "conexoes_compartilhadas=true" in texto else "uma conexão por POST"
    aquecimento = re.search(r"aquecimento_post=(\d+)", texto)
    if aquecimento and int(aquecimento.group(1)) > 0:
        return f"{conexoes}; aquecimento de {aquecimento.group(1)} POST antes das medições"
    return f"{conexoes}; sem aquecimento"


def experimento(reports):
    caminho = reports / "experimento.txt"
    valores = dict(re.findall(r"(\w+)=(.*)", caminho.read_text())) if caminho.exists() else {}
    rotulos = (
        ("cpus_api_experimento", "CPUs da API", "padrao"),
        ("memoria_api", "memória da API", "padrao"),
        ("opcoes_jvm_api", "JVM", "padrao"),
        ("aquecimento_requisicoes", "aquecimento", "3"),
        ("jfr", "JFR", "0"),
    )
    partes = [f"{rotulo} {valores[chave]}" for chave, rotulo, padrao in rotulos if valores.get(chave, padrao) != padrao]
    return "; ".join(partes) or "ambiente padrão do Compose"


def perfil_instancia(reports):
    caminho = reports / "instancia.txt"
    if not caminho.exists():
        return "CPU da instância não registrada"
    valores = dict(re.findall(r"(\w+)=(\S+)", caminho.read_text()))
    return f"instância {valores.get('cpus_instancia_efetivas', '?')} CPUs (Docker {valores.get('cpus_docker', '?')})"


def carga_host(reports, rotulo):
    candidatos = (
        reports / f"carga-host-resumo-{rotulo}.txt",
        reports / f"carga-host-resumo-post-{rotulo}.txt",
    )
    caminho = next((item for item in candidatos if item.exists()), None)
    if caminho is None:
        return "carga do host não registrada"
    valores = dict(re.findall(r"(\w+)=(\S+)", caminho.read_text()))
    return (
        "carga média do host início/pico/fim "
        f"{valores.get('carga_host_inicio', '?')}/{valores.get('carga_host_pico', '?')}/{valores.get('carga_host_fim', '?')}"
    )


def linha_resultado_post(requests, report, reports):
    gatling = LinhasGatling()
    conteudo = (report / "index.html").read_text(encoding="utf-8")
    gatling.feed(conteudo)
    post = linha_por_prefixo(gatling.linhas, "req_post")
    rotulo = f"post-{requests}"
    aceitas = inteiro(post[2]) if len(post) > 2 else None
    identificador = report.name.rsplit("-", 1)[-1]
    caminho_report = f"../../app/build/reports/gatling/{report.name}/index.html"
    return (
        f"| {requests} | `{identificador}` | {metricas_gatling(post)} | {meta_post(post, reports)} | "
        f"{resumo_erros(erros_gatling(conteudo))} | "
        f"{consistencia_ingresso(reports / f'consistencia-{rotulo}.txt', aceitas)} | "
        f"{resumo_ingresso(reports, rotulo)} | {pool_escrita_api(reports / f'pools-{rotulo}.txt')} | "
        f"{picos_recursos(reports / f'atividade-{rotulo}.tsv', 'api')}; "
        f"{transbordos_listen(reports / f'listen-{rotulo}.txt')} | "
        f"{configuracao_ingresso(reports)}; {amostragem_traces(reports)}; {conexoes_cliente(reports)}; "
        f"{perfil_instancia(reports)}; {carga_host(reports, rotulo)}; {experimento(reports)}; [Gatling]({caminho_report}) |"
    )


TABELAS = {
    "combinado": (
        INICIO,
        FIM,
        [
            "## Últimos resultados por volume",
            "",
            "Cada linha mostra o relatório mais recente daquele volume. Os relatórios Gatling e os arquivos SQL guardam os detalhes da rodada.",
            "",
            "| Solicitações | Rodada UTC | POST | GET imediato | KO por tipo | Estado final no banco | Emissão p50/p95/p99; integridade | Escoamento (outbox e lag Kafka) | Recursos do app | Evidências |",
            "|---:|---|---|---|---|---|---|---|---|---|",
        ],
        normalizar_linha,
    ),
    "post": (
        INICIO_POST,
        FIM_POST,
        [
            "## Últimos resultados do POST isolado por volume",
            "",
            "Cada linha mostra a rodada mais recente daquele volume em `scripts/carga-post.sh`: só o `POST` recebe tráfego e o worker fica parado.",
            "",
            "| Solicitações | Rodada UTC | POST | Meta (100% aceito e máximo) | KO por tipo | Consistência da escrita | Fila e lotes | Pool de escrita da API | Recursos da API | Configuração e evidências |",
            "|---:|---|---|---|---|---|---|---|---|---|",
        ],
        lambda linha: linha.strip(),
    ),
}


def atualizar_documento(documento, requests, nova_linha, cenario="combinado"):
    marcador_inicio, marcador_fim, cabecalho, normalizar = TABELAS[cenario]
    conteudo = documento.read_text()
    if marcador_inicio not in conteudo or marcador_fim not in conteudo:
        raise SystemExit(f"Marcadores de resultados automáticos ausentes em {documento}")
    inicio = conteudo.index(marcador_inicio) + len(marcador_inicio)
    fim = conteudo.index(marcador_fim)
    linhas = []
    for linha in conteudo[inicio:fim].splitlines():
        if not linha.startswith("|"):
            continue
        volume = linha.split("|", 2)[1].strip()
        if volume.isdigit() and int(volume) != requests:
            linhas.append(normalizar(linha))
    linhas.append(nova_linha)
    linhas.sort(key=lambda linha: int(linha.split("|", 2)[1].strip()))
    tabela = "\n".join(["", *cabecalho, *linhas, ""])
    documento.write_text(conteudo[:inicio] + tabela + conteudo[fim:])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--requests", type=int, required=True)
    parser.add_argument("--gatling-report", type=Path, required=True)
    parser.add_argument("--reports", type=Path, required=True)
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--cenario", choices=sorted(TABELAS), default="combinado")
    args = parser.parse_args()
    construir = linha_resultado_post if args.cenario == "post" else linha_resultado
    atualizar_documento(
        args.document,
        args.requests,
        construir(args.requests, args.gatling_report, args.reports),
        args.cenario,
    )


if __name__ == "__main__":
    main()
