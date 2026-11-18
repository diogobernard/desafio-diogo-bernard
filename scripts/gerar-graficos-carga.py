#!/usr/bin/env python3
"""Gera os gráficos SVG de carga usados no README (tema claro e escuro).

Requer matplotlib 3.9.4, a versão usada nos gráficos atuais:

    python3 -m venv <diretorio-do-venv>
    <diretorio-do-venv>/bin/pip install matplotlib==3.9.4
    <diretorio-do-venv>/bin/python scripts/gerar-graficos-carga.py [diretorio-de-saida]

Os dados ficam nas constantes abaixo. Antes de gerar, copie para elas os valores do
relatório de carga (docs/carga/relatorio.md). O diretório de saída padrão é
docs/carga/imagens; os arquivos `<nome>.svg` e `<nome>-escuro.svg` são sobrescritos.
"""

import sys
from pathlib import Path

import matplotlib

matplotlib.use("svg")
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
from matplotlib.patches import Patch

SAIDA_PADRAO = Path("docs/carga/imagens")
VOLUMES = ["1k", "5k", "10k"]
P50, P95, P99 = "#9ec5f4", "#3987e5", "#1c5cab"
TEMAS = {
    "": dict(bg="#fcfcfb", fg="#52514e", grade="#e4e3df", titulo="#0b0b0b", azul="#2a78d6", laranja="#eb6834"),
    "-escuro": dict(bg="#1a1a19", fg="#c3c2b7", grade="#3a3a37", titulo="#f0efec", azul="#3987e5", laranja="#d95926"),
}

# Rodada de 18/11/2026. POST isolado: (p50, p95, p99, máx.) em ms por volume; vazão em req/s.
POST = {
    "Rodada 1": [(610, 835, 913, 953), (1279, 2241, 2435, 2567), (82, 481, 641, 799)],
    "Rodada 2": [(74, 137, 166, 175), (51, 180, 229, 308), (12, 168, 318, 421)],
}
VAZAO_POST = {"Rodada 1": [500, 833.33, 909.09], "Rodada 2": [500, 833.33, 1000]}
# Consumo isolado: segundos até emitir e até notificar; vazão de emissão em notas/s.
EMISSAO_S = [5, 8, 18]
NOTIFICACAO_S = [8, 27, 57]
VAZAO_EMISSAO = [200, 625, 555.56]
# Consulta isolada (teste só de GET, sem Kafka): (p50, p95, p99, máx.) em ms por volume, para cada GET; vazão em GET/s.
ROTULO_CONSULTA = "Consulta (2 GETs)"
CONSULTA = {
    "GET /issuance-requests/{id}": [(209, 342, 451, 483), (58, 274, 335, 494), (3, 72, 116, 181)],
    "GET /invoices/{número}": [(196, 314, 406, 576), (31, 232, 312, 516), (2, 43, 83, 179)],
}
VAZAO_CONSULTA = [1000, 2000, 2000]


def milhar(valor):
    return f"{valor:,.0f}".replace(",", ".")


def estilo(tema):
    plt.rcParams.update({
        "svg.hashsalt": "carga",
        "font.size": 9,
        "text.color": tema["fg"],
        "axes.labelcolor": tema["fg"],
        "xtick.color": tema["fg"],
        "ytick.color": tema["fg"],
    })


def figura(tema, largura, titulo, subtitulo):
    fig = plt.figure(figsize=(largura, 4.2), facecolor=tema["bg"])
    fig.text(0.018, 0.945, titulo, fontsize=12, fontweight="bold", color=tema["titulo"], ha="left", va="center")
    fig.text(0.018, 0.88, subtitulo, fontsize=9, color=tema["fg"], ha="left", va="center")
    return fig


def preparar_eixo(ax, tema, ticks, limite):
    ax.set_facecolor(tema["bg"])
    for lado in ("top", "right", "left"):
        ax.spines[lado].set_visible(False)
    ax.spines["bottom"].set_color(tema["fg"])
    ax.set_yticks(ticks)
    ax.set_yticklabels([milhar(t) for t in ticks])
    ax.set_ylim(0, limite)
    ax.yaxis.grid(True, color=tema["grade"], linewidth=0.9)
    ax.set_axisbelow(True)
    ax.tick_params(length=0)


def pintar_eixo_x(ax, rotulos):
    ax.set_xticks(range(len(rotulos)))
    ax.set_xticklabels(rotulos)
    ax.set_xlim(-0.5, len(rotulos) - 0.5)


def linha_meta(ax, tema, y, texto, x_texto, alinhamento):
    ax.axhline(y, color=tema["fg"], linestyle="--", linewidth=1.3, zorder=3)
    ax.text(x_texto, y, texto, color=tema["fg"], fontsize=8, ha=alinhamento, va="bottom")


def rotular(ax, tema, xs, valores, textos):
    for x, valor, texto in zip(xs, valores, textos):
        ax.annotate(texto, (x, valor), xytext=(0, 2.5), textcoords="offset points", ha="center", va="bottom",
                    fontsize=8, color=tema["titulo"], zorder=5,
                    bbox=dict(facecolor=tema["bg"], edgecolor="none", pad=0.4))


def titulo_do_painel(ax, tema, texto):
    ax.set_title(texto, loc="left", fontsize=10, color=tema["titulo"], pad=16)


def painel_vazao_post(fig, grade, tema):
    ax = fig.add_subplot(grade[0])
    preparar_eixo(ax, tema, [0, 200, 400, 600, 800, 1000], 1150)
    r1, r2 = VAZAO_POST["Rodada 1"], VAZAO_POST["Rodada 2"]
    cor_r2 = P99 if tema["bg"] == TEMAS[""]["bg"] else P50
    ax.bar([i - 0.19 for i in range(3)], r1, 0.36, color=tema["azul"], label="Rodada 1")
    ax.bar([i + 0.19 for i in range(3)], r2, 0.36, color=cor_r2, label="Rodada 2")
    rotular(ax, tema, [i - 0.19 for i in range(3)], r1, [milhar(v) for v in r1])
    rotular(ax, tema, [i + 0.19 for i in range(3)], r2, [milhar(v) for v in r2])
    linha_meta(ax, tema, 1000, "oferta: 1.000", -0.5, "left")
    titulo_do_painel(ax, tema, "POST (ingresso)")
    ax.set_ylabel("req/s")
    ax.legend(loc="upper center", bbox_to_anchor=(0.5, -0.12), ncol=2, frameon=False, fontsize=8)
    pintar_eixo_x(ax, VOLUMES)


def painel_barras(fig, posicao, tema, config):
    ax = fig.add_subplot(posicao)
    preparar_eixo(ax, tema, config["ticks"], config["limite"])
    ax.bar(range(3), config["valores"], 0.55, color=tema["azul"])
    rotular(ax, tema, range(3), config["valores"], [milhar(v) for v in config["valores"]])
    if "oferta" in config:
        linha_meta(ax, tema, config["oferta"], f"oferta: {milhar(config['oferta'])}", -0.5, "left")
    titulo_do_painel(ax, tema, config["titulo"])
    ax.set_ylabel(config["unidade"])
    pintar_eixo_x(ax, VOLUMES)


def vazao(tema):
    fig = figura(tema, 11, "Vazão por serviço e volume",
                 "Média do Gatling na janela de medição (POST e GET) e notas emitidas por segundo (worker)")
    grade = fig.add_gridspec(1, 3, left=0.075, right=0.99, top=0.70, bottom=0.2, wspace=0.34)
    painel_vazao_post(fig, grade, tema)
    painel_barras(fig, grade[1], tema, dict(
        valores=VAZAO_EMISSAO, ticks=list(range(0, 701, 100)), limite=720, titulo="Emissão (worker)", unidade="notas/s"))
    painel_barras(fig, grade[2], tema, dict(
        valores=VAZAO_CONSULTA, ticks=[0, 500, 1000, 1500, 2000], limite=2300, oferta=2000,
        titulo=ROTULO_CONSULTA, unidade="GET/s"))
    return fig


def legenda_percentis(fig, tema):
    itens = [Patch(color=P50), Patch(color=P95), Patch(color=P99), Line2D([0], [0], color=tema["titulo"], linewidth=2.2)]
    fig.legend(itens, ["p50", "p95", "p99", "máximo"], loc="upper left", bbox_to_anchor=(0.008, 0.845),
               ncol=4, frameon=False, fontsize=9, labelcolor=tema["titulo"])


def percentis_por_painel(fig, tema, paineis, config):
    grade = fig.add_gridspec(1, 2, left=0.06, right=0.99, top=0.66, bottom=0.12, wspace=0.12)
    for coluna, (titulo, dados) in enumerate(paineis.items()):
        ax = fig.add_subplot(grade[coluna], sharey=fig.axes[0] if coluna else None)
        preparar_eixo(ax, tema, config["ticks"], config["limite"])
        for volume, (p50, p95, p99, maximo) in enumerate(dados):
            for deslocamento, valor, cor in ((-0.25, p50, P50), (0, p95, P95), (0.25, p99, P99)):
                ax.bar(volume + deslocamento, valor, 0.23, color=cor)
            ax.hlines(maximo, volume - 0.36, volume + 0.36, color=tema["titulo"], linewidth=2.2, zorder=4)
            ax.text(volume, maximo + config["folga"], milhar(maximo), ha="center", va="bottom", fontsize=8, color=tema["fg"])
        if "meta" in config:
            linha_meta(ax, tema, config["meta"], "meta: máx. 3 s", 2.5, "right")
        titulo_do_painel(ax, tema, titulo)
        ax.set_ylabel("ms") if coluna == 0 else ax.tick_params(labelleft=False)
        pintar_eixo_x(ax, VOLUMES)
    legenda_percentis(fig, tema)


def latencia_post(tema):
    fig = figura(tema, 11, "POST /issuance-requests — latência até o aceite (202)",
                 "Oferta de 1.000 POST/s com o worker parado · API com 4 CPUs · 100% aceito")
    percentis_por_painel(fig, tema, POST, dict(
        ticks=list(range(0, 3001, 500)), limite=3300, folga=70, meta=3000))
    return fig


def latencia_consulta(tema):
    fig = figura(tema, 11, "Consulta — latência dos GETs",
                 "1.000 usuários/s, dois GETs cada · só PostgreSQL e API, base de 5k notas · 100% 200")
    maximo = max(max(p[3] for p in dados) for dados in CONSULTA.values())
    passo = 200 if maximo < 1500 else 500 if maximo < 4000 else 1000 if maximo < 8000 else 2000
    teto = (int(maximo * 1.12) // passo + 1) * passo
    percentis_por_painel(fig, tema, CONSULTA, dict(
        ticks=list(range(0, teto + 1, passo)), limite=teto, folga=teto * 0.02))
    return fig


def consumo(tema):
    fig = figura(tema, 7.5, "Consumo — backlog até emissão e notificações",
                 "Worker com 4 CPUs · sem duplicidade, lacunas ou DLQ")
    ax = fig.add_axes([0.1, 0.12, 0.88, 0.58])
    preparar_eixo(ax, tema, list(range(0, 71, 10)), 72)
    esquerda, direita = [i - 0.19 for i in range(3)], [i + 0.19 for i in range(3)]
    ax.bar(esquerda, EMISSAO_S, 0.36, color=tema["azul"], label="Até emitir")
    ax.bar(direita, NOTIFICACAO_S, 0.36, color=tema["laranja"], label="Até notificar (4 consumidores)")
    rotular(ax, tema, esquerda, EMISSAO_S, [f"{v} s" for v in EMISSAO_S])
    rotular(ax, tema, direita, NOTIFICACAO_S, [f"{v} s" for v in NOTIFICACAO_S])
    ax.set_ylabel("segundos após retomar o worker")
    pintar_eixo_x(ax, [f"{v} notas" for v in VOLUMES])
    fig.legend(loc="upper left", bbox_to_anchor=(0.008, 0.845), ncol=2, frameon=False, fontsize=9,
               labelcolor=tema["titulo"], handles=ax.get_legend_handles_labels()[0])
    return fig


GRAFICOS = {
    "vazao": vazao,
    "post-latencia": latencia_post,
    "consulta-latencia": latencia_consulta,
    "consumo-tempos": consumo,
}


def main():
    saida = Path(sys.argv[1]) if len(sys.argv) > 1 else SAIDA_PADRAO
    saida.mkdir(parents=True, exist_ok=True)
    for sufixo, tema in TEMAS.items():
        estilo(tema)
        for nome, construir in GRAFICOS.items():
            fig = construir(tema)
            fig.savefig(saida / f"{nome}{sufixo}.svg", format="svg", facecolor=tema["bg"], metadata={"Date": None})
            plt.close(fig)
    print(f"{len(GRAFICOS) * len(TEMAS)} gráficos gerados em {saida}")


if __name__ == "__main__":
    main()
