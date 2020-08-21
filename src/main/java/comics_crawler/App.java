package comics_crawler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class App {

	// WebDriver
	private WebDriver drv;

	// Properties
	private static final String WEB_DRIVER_ID 	= "webdriver.chrome.driver";
	private static final String WEB_DRIVER_PATH	= "/usr/local/bin/chromedriver";

	private static final String DOWNLOAD_PATH 	= "comics";

	// 마나모아 특정 만화 URL
	private String url 			= "";

	private String comic_title 	= "";
	private Map<String, List<String>> linkMap 	= new ConcurrentHashMap<>();


	private void init() {
		log( "초기화 중" );

		// set system properties
		System.setProperty( WEB_DRIVER_ID, WEB_DRIVER_PATH );

		// Chrome option
		ChromeOptions options 	= new ChromeOptions();
		options.addArguments("start-maximized"); // open Browser in maximized mode
		options.addArguments("disable-infobars"); // disabling infobars
		options.addArguments("--disable-extensions"); // disabling extensions
		options.addArguments("--disable-gpu"); // applicable to windows os only
		options.addArguments("--disable-dev-shm-usage"); // overcome limited resource problems
		options.addArguments("--no-sandbox"); // Bypass OS security model
		options.addArguments("--headless"); // Bypass OS security model

		// init web driver
		drv 	= new ChromeDriver( options );
	}

	public void doProcess() {
		url = "https://manamoa53.net/bbs/page.php?hid=manga_detail&manga_id=4285";
		
		log( "작업 시작" );
		log( "URL: "+ url );
		
		init();

		makeLinkMap( url );

		doDownload();
		
		doZip();
	}

	private void doZip() {
		try {
			File epPath 	= null;

			log( "압축 시작" );
			int score = 1;
			for (Entry<String, List<String>> en: linkMap.entrySet()) {
				log( " - 압축 중 .. "+ en.getKey() +" [ "+ (score++) +" / "+ linkMap.size() +" ]" );

				epPath 	= Paths.get( DOWNLOAD_PATH, comic_title, en.getKey() ).toFile();

				ProcessBuilder pb 	= new ProcessBuilder( "zip", "-r", Paths.get( DOWNLOAD_PATH, comic_title, en.getKey() ) +".zip", epPath.getPath() );
				@SuppressWarnings( "unused" )
				Process p = pb.start();
				//printStream( p );	

				pb 	= new ProcessBuilder( "rm", "-rf", epPath.getPath() );
				p 	= pb.start();
			}

		} catch (Exception e) {
			log( e );
			e.printStackTrace();
		}
	}

	private void doDownload() {
		int total_page = 0;
		for (Entry<String, List<String>> en: linkMap.entrySet()) {
			total_page += en.getValue().size();
		}

		log( "총 페이지 수: "+ total_page);

		try {
			File epPath 	= null;
				
			log( "다운로드 시작" );
			int score = 1;
			for (Entry<String, List<String>> en: linkMap.entrySet()) {
				epPath 	= Paths.get( DOWNLOAD_PATH, comic_title, en.getKey() ).toFile();
				epPath.mkdirs();

				int pageIndex = 0;
				for (String pageUrl: en.getValue()) {
					downloadPage( pageIndex++, pageUrl, epPath.getAbsolutePath() );
				
					log( String.format(" - 다운로드 중 .. %s [ %d / %d ]", en.getKey(), score++, total_page) );
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			log( e );
		}
	}

	private void downloadPage(int pageIndex, String pageUrl, String epPath) {
		
		String fileExt 	= pageUrl.substring( pageUrl.lastIndexOf(".") +1, pageUrl.length() );
		String fileName = String.format("%03d.%s", pageIndex, fileExt);
		
		URL website = null;

		try {
			website = new URL( pageUrl );

		} catch( Exception e ) {
			e.printStackTrace();
		}

		try (
				ReadableByteChannel rbc = Channels.newChannel(website.openStream());
				FileOutputStream fos = new FileOutputStream( Paths.get( epPath, fileName ).toFile() );
			) {

			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);  // 처음부터 끝까지 다운로드

		} catch (Exception e) {
			e.printStackTrace();
		}     
	}

	private void makeLinkMap(String url) {
		String innerUrl 	= null;

		Document doc 	= null;
		Document innerDoc 	= null;

		try {
			doc 	= Jsoup.connect( url ).get();

			Elements elems 	= doc.select( "div.chapter-list div.slot" );
			Element title 	= doc.selectFirst( "div.red.title" );
			Element subTitle;
			Element link;

			Elements pages;

			comic_title 	= title.text();
			log( "타이틀: "+ title.text() );
			log( "총 에피소드 수: "+ elems.size() );

			log( "링크 수집 시작" );
			int i=1;
			for (Element elem: elems) {
				log(String.format( " - 링크 수집 중 .. [ %d / %d ]", i++, elems.size() ));
				link 	= elem.selectFirst("a");
				subTitle 	= elem.selectFirst("div.title");

				innerUrl 	= link.attr( "abs:href" );

				// using selenium for dynamic page
				drv.get( innerUrl );
				innerDoc 	= Jsoup.parse( drv.getPageSource() );

				pages 	= innerDoc.select( "div.view-content img" );

				List<String> imgs 	= new ArrayList<>();
				for (Element innerElem: pages) {
					String imgSrc 	= innerElem.attr( "src" );

					// get lazy-src 
					if (imgSrc == null || imgSrc.length() == 0) {
						imgSrc 	= innerElem.attr( "lazy-src" );
					}

					// delete ?quick 
					if (imgSrc.endsWith( "?quick" )) {
						imgSrc 	= imgSrc.substring(0, imgSrc.length() -6);
					}

					imgs.add( imgSrc );
				}

				linkMap.put( subTitle.ownText(), imgs );

				//MapUtils.debugPrint( System.out, "LINK MAP", linkMap );
			}
			
			drv.quit();

		} catch( Exception e ) {
			log( e );

			drv.quit();
		}
	}

	private void printStream(Process process) throws IOException, InterruptedException {
		process.waitFor();
		try (InputStream psout = process.getInputStream()) {
			copy(psout, System.out);
		}
	}

	public void copy(InputStream input, OutputStream output) throws IOException {
		byte[] buffer = new byte[1024];
		int n = 0;
		while ((n = input.read(buffer)) != -1) {
			output.write(buffer, 0, n);
		}
	}

	private void log(Exception e) {
		printSTEInfo( Thread.currentThread().getStackTrace()[2], e.getMessage() );
	}

	private void log(String message) {
		printSTEInfo( Thread.currentThread().getStackTrace()[2], message );
	}

	private void printSTEInfo(StackTraceElement ste, String message) {
		String className 		= ste.getClassName();
		className 	= className.substring( className.lastIndexOf(".") +1, className.length());

		StringBuilder sb = new StringBuilder();
		sb.append( String.format("[%-15s][%-15s][%-3s] ", className, ste.getMethodName(), ste.getLineNumber()) );
		sb.append(message);

		System.out.println( sb.toString() );
	}

	public static void main(String[] args) {
		App proc 	= new App();

		proc.doProcess();
    }
}

