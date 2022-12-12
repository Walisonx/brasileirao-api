package br.com.phc.brasileiraoapi.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.phc.brasileiraoapi.dto.PartidaGoogleDTO;

public class ScrapingUtil {

	private static final Logger LOGGER = LoggerFactory.getLogger(ScrapingUtil.class);
	private static final String BASE_URL_GOOGLE = "https://www.google.com.br/search?q=";
	private static final String COMPLEMENTO_URL_GOOGLE = "&hl=pt-BR";
	
	private static final String DIV_PARTIDA_ANDAMENTO = "div[class=imso_mh__lv-m-stts-cont]";
	private static final String DIV_PARTIDA_ENCERRADO = "span[class=imso_mh__ft-mtch imso-medium-font imso_mh__ft-mtchc]";

	private static final String DIV_PLACAR_EQUIPE_CASA = "div[class=imso_mh__l-tm-sc imso_mh__scr-it imso-light-font]"; 
	private static final String DIV_PLACAR_EQUIPE_VISITANTE = "div[class=imso_mh__r-tm-sc imso_mh__scr-it imso-light-font]"; 

	private static final String DIV_DADOS_EQUIPE_VISITANTE = "div[class=imso_mh__second-tn-ed imso_mh__tnal-cont imso-tnol]";
	private static final String DIV_DADOS_EQUIPE_CASA = "div[class=imso_mh__first-tn-ed imso_mh__tnal-cont imso-tnol]";
	

	private static final String ITEM_LOGO = "img[class=imso_btl__mh-logo]";
	
	private static final String DIV_GOLS_EQUIPE_CASA = "div[class=imso_gs__tgs imso_gs__left-team]";
	private static final String DIV_GOLS_EQUIPE_VISITANTE = "div[class=imso_gs__tgs imso_gs__right-team]";
	
	private static final String ITEM_GOL = "div[class=imso_gs__gs-r]";
	
	private static final String DIV_PENALIDADES = "div[class=imso_mh_s__psn-sc]";
	
	private static final String CASA = "casa";
	private static final String VISITANTE = "visitante";
	
	private static final String HTTPS = "https:";
	private static final String SRC = "src";
	private static final String SPAN = "span";
	private static final String PENALTIS = "Pênaltis";
	
	public static void main(String[] args) {
		String url = BASE_URL_GOOGLE + "brasil+x+croacia";
		ScrapingUtil scraping = new ScrapingUtil();
		scraping.obtemInformacoesPartida(url);
	}

	public PartidaGoogleDTO obtemInformacoesPartida(String url) {
		PartidaGoogleDTO partida = new PartidaGoogleDTO();

		Document document = null;

		try {
			document = Jsoup.connect(url).get();

			String title = document.title();
			LOGGER.info("Titulo da pagina: {}", title);

			StatusPartida statusPartida = obtemStatusPartida(document);
			LOGGER.info("Status Partida: {}", statusPartida);

			if (statusPartida != StatusPartida.PARTIDA_NAO_INICIA) {
				String tempoPartida = obtemTempoPartida(document);
				LOGGER.info("Tempo Partida: {}", tempoPartida);
				
				Integer placarEquipeCasa = recuperaPlacarEquipes(document, DIV_PLACAR_EQUIPE_CASA);
				LOGGER.info("Placar equipe casa: {}", placarEquipeCasa);
				
				Integer placarEquipeVisitante = recuperaPlacarEquipes(document, DIV_PLACAR_EQUIPE_VISITANTE);
				LOGGER.info("Placar equipe visitante: {}", placarEquipeVisitante);
				
				String golsEquipeCasa = recuperaGolsEquipe(document, DIV_GOLS_EQUIPE_CASA);
				LOGGER.info("Gols equipe casa: {}", golsEquipeCasa);
				
				String golsEquipeVisitante = recuperaGolsEquipe(document,DIV_GOLS_EQUIPE_VISITANTE);
				LOGGER.info("Gols equipe visitante: {}", golsEquipeVisitante);
				
				Integer placarEstendidoEquipeCasa = buscaPenalidades(document, CASA);
				LOGGER.info("placar estendido equipe casa: {}", placarEstendidoEquipeCasa);
				
				Integer placarEstendidoEquipeVisitante = buscaPenalidades(document, VISITANTE);
				LOGGER.info("placar estendido equipe visitante: {}", placarEstendidoEquipeVisitante);
			}

			String nomeEquipeCasa = recuperaNomeEquipes(document, DIV_DADOS_EQUIPE_CASA);
			LOGGER.info("Nome Equipe Casa: {}", nomeEquipeCasa);

			String nomeEquipeVisitante = recuperaNomeEquipes(document, DIV_DADOS_EQUIPE_VISITANTE);
			LOGGER.info("Nome Equipe Visitante: {}", nomeEquipeVisitante);

			String urlLogoEquipeCasa = recuperaLogoEquipes(document, DIV_DADOS_EQUIPE_CASA);
			LOGGER.info("Url logo equipe casa: {}", urlLogoEquipeCasa);

			String urlLogoEquipeVisitante = recuperaLogoEquipes(document, DIV_DADOS_EQUIPE_VISITANTE);
			LOGGER.info("Url logo equipe visitante: {}", urlLogoEquipeVisitante);

			

		} catch (IOException e) {
			LOGGER.error("ERRO  AO TENTAR CONECTAR NO GOOGLE COM JSOUP -> {}", e.getMessage());
		}

		return partida;

	}

	public StatusPartida obtemStatusPartida(Document document) {
		// situacoes
		// 1 - partida nao iniciada
		// 2 - partida iniciada/jogo rolando/intervalo
		// 3 - partida encerrada
		// 4 - penalidades

		StatusPartida statusPartida = StatusPartida.PARTIDA_NAO_INICIA;

		boolean isTempoPartida = document.select(DIV_PARTIDA_ANDAMENTO).isEmpty();
		if (!isTempoPartida) {
			String tempoPartida = document.select(DIV_PARTIDA_ANDAMENTO).first().text();
			statusPartida = StatusPartida.PARTIDA_EM_ANDAMENTO;
			if (tempoPartida.contains(PENALTIS)) {
				statusPartida = StatusPartida.PARTIDA_PENALTS;
			}
		}
		isTempoPartida = document.select(DIV_PARTIDA_ENCERRADO).isEmpty();
		if (!isTempoPartida) {
			statusPartida = StatusPartida.PARTIDA_ENCERRADA;
		}

		return statusPartida;
	}

	public String obtemTempoPartida(Document document) {
		String tempoPartida = null;
		// jogo rolando ou intervalo ou penalidades
		boolean isTempoPartida = document.select(DIV_PARTIDA_ANDAMENTO).isEmpty();
		if (!isTempoPartida) {
			tempoPartida = document.select(DIV_PARTIDA_ANDAMENTO).first().text();
		}
		isTempoPartida = document.select(DIV_PARTIDA_ENCERRADO).isEmpty();
		if (!isTempoPartida) {
			tempoPartida = document.select(DIV_PARTIDA_ENCERRADO).first()
					.text();
		}

		return corrigeTempoPartida(tempoPartida);
	}

	public String corrigeTempoPartida(String tempo) {

		if (tempo.contains("'")) {
			return tempo.replace(" ", "").replace("'", " min");

		} else {
			return tempo;
		}

	}

	public String recuperaNomeEquipes(Document document, String itemHtml) {
		Element elemento = document.selectFirst(itemHtml);
		String nomeEquipe = elemento.select(SPAN).text();

		return nomeEquipe;
	}


	public String recuperaLogoEquipes(Document document, String itemHtml) {
		Element elemento = document.selectFirst(itemHtml);
		String urlLogo = HTTPS + elemento.select(ITEM_LOGO).attr(SRC);

		return urlLogo;
	}

	public Integer recuperaPlacarEquipes(Document document , String itemHtml) {
		String placarEquipe = document.selectFirst(itemHtml).text();
		return formataPlacarStringInteger(placarEquipe);

	}
	
	public String recuperaGolsEquipe(Document document, String itemHtml) {
		List<String> golsEquipe = new ArrayList<>();
		
		Elements elementos = document.select(itemHtml).select(ITEM_GOL);
		for(Element e : elementos) {
			String infoGol = e.select(ITEM_GOL).text();
			golsEquipe.add(infoGol);
		}
		
		
		return String.join(", ", golsEquipe);
	}
	
	public Integer buscaPenalidades(Document document, String tipoEquipe) {
		boolean isPenalidades = document.select(DIV_PENALIDADES).isEmpty();
		if(!isPenalidades) {
			String penalidades = document.select(DIV_PENALIDADES).text();
			String penalidadesCompleta = penalidades.substring(0,5).replace(" ", "");
			String[] divisao = penalidadesCompleta.split("-");
			
			return tipoEquipe.equals(CASA) ? formataPlacarStringInteger(divisao[0]) : formataPlacarStringInteger(divisao [1]);
		}
		
		return 0;
	}
	
	public Integer formataPlacarStringInteger( String placar) {
		Integer valor;
		try {
			valor = Integer.parseInt(placar);
					}
		catch(Exception e) {
			valor = 0;
		}
		return valor;
		
			
		
	}
	

}