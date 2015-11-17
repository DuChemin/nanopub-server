package ch.tkuhn.nanopub.server;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import javax.servlet.http.HttpServletResponse;

import net.trustyuri.TrustyUriUtils;

import org.apache.commons.lang.StringEscapeUtils;
import org.nanopub.MalformedNanopubException;
import org.nanopub.Nanopub;
import org.nanopub.NanopubUtils;
import org.nanopub.extra.index.IndexUtils;
import org.nanopub.extra.index.NanopubIndex;
import org.openrdf.model.URI;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.Rio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NanopubPage extends Page {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	public static void show(ServerRequest req, HttpServletResponse httpResp) throws IOException {
		NanopubPage obj = new NanopubPage(req, httpResp);
		obj.show();
	}

	public NanopubPage(ServerRequest req, HttpServletResponse httpResp) {
		super(req, httpResp);
	}

	public void show() throws IOException {
		Nanopub nanopub;
		String ac = getReq().getArtifactCode();
		try {
			nanopub = NanopubDb.get().getNanopub(ac);
		} catch (Exception ex) {
			getResp().sendError(500, "Internal error: " + ex.getMessage());
			logger.error(ex.getMessage(), ex);
			return;
		}
		if (nanopub == null) {
			getResp().sendError(404, "Nanopub not found: " + ac);
			return;
		}
		setCanonicalLink("/" + ac);
		boolean isIndexNanopub = IndexUtils.isIndex(nanopub);
		String ext = getReq().getExtension();
		String rf = getReq().getPresentationFormat();
		RDFFormat format = null;
		if (ext != null) {
			format = Rio.getParserFormatForFileName("np." + ext);
			if (format == null) {
				getResp().sendError(400, "Unknown format: " + ext);
				return;
			}
		} else if (rf == null) {
			String suppFormats = "application/trig,application/x-trig,text/x-nquads,application/trix,application/ld+json,text/html";
			if (!isIndexNanopub) {
				suppFormats += ",text/plain";
			}
			String mimeType = Utils.getMimeType(getHttpReq(), suppFormats);
			if (isIndexNanopub && "text/html".equals(mimeType)) {
				showIndex(nanopub);
				return;
			}
			else if (format == null && "text/html".equals(mimeType)) {
				String url = getReq().getFullRequest();
				displayJSONLD(nanopub, url);
				return;
			}
			if ("text/plain".equals(mimeType)) {
				rf = "text/plain";
			} else {
				format = Rio.getParserFormatForMIMEType(mimeType);
			}
		}
		if (format == null) {
			format = RDFFormat.TRIG;
		}
		if (!format.supportsContexts()) {
			getResp().sendError(400, "Unsuitable RDF format: " + ext);
			return;
		}
		if (rf != null) {
			getResp().setContentType(rf);
		} else {
			getResp().setContentType(format.getDefaultMIMEType());
			getResp().addHeader("Content-Disposition", "filename=\"" + ac + "." + format.getDefaultFileExtension() + "\"");
		}
		OutputStream out = null;
		try {
			out = getResp().getOutputStream();
			NanopubUtils.writeToStream(nanopub, out, format);
		} catch (Exception ex) {
			getResp().sendError(500, "Internal error: " + ex.getMessage());
			logger.error(ex.getMessage(), ex);
		} finally {
			if (out != null) out.close();
		}
	}

	private void showIndex(Nanopub np) throws IOException {
		try {
			NanopubIndex npi = IndexUtils.castToIndex(np);
			getResp().setContentType("text/html");
			String title, headerTitle;
			if (npi.getName() == null) {
				if (npi.isIncomplete()) {
					headerTitle = "Part of Unnamed Index";
					title = "<em>Part of Unnamed Index</em>";
				} else {
					headerTitle = "Unnamed Index";
					title = "<em>Unnamed Index</em>";
				}
			} else {
				String escapedName = StringEscapeUtils.escapeHtml(npi.getName());
				if (npi.isIncomplete()) {
					headerTitle = "Part of Index '" + escapedName + "'";
					title = "<em>Part of Index</em> '" + escapedName + "'";
				} else {
					headerTitle = escapedName + " (Nanopub Index)";
					title = escapedName + " <em>(Nanopub Index)</em>";
				}
			}
			printHtmlHeader(headerTitle);
			println("<h1>" + title + "</h1>");
			println("<p>[ <a href=\".\" rel=\"home\">home</a> ]</p>");
			println("<h3>This:</h3>");
			println("<table><tbody>");
			printItem(npi.getUri());
			println("</tbody></table>");
			if (!npi.isIncomplete()) {
				if (npi.getDescription() != null) {
					println("<h3>Description:</h3>");
					println("<p>");
					println(StringEscapeUtils.escapeHtml(npi.getDescription()));
					println("</p>");
				}
				if (npi.getCreationTime() != null) {
					println("<h3>Creation Time:</h3>");
					println("<p>" + SimpleDateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(npi.getCreationTime().getTime()) + "</p>");
				}
				if (!npi.getCreators().isEmpty()) {
					println("<h3>Creators:</h3>");
					println("<ul>");
					for (URI uri : npi.getCreators()) {
						println("<li><a href=\"" + uri + "\" rel=\"nofollow\">" + uri + "</a></li>");
					}
					println("</ul>");
				}
			}
			if (!npi.getSeeAlsoUris().isEmpty()) {
				println("<h3>See Also:</h3>");
				println("<ul>");
				for (URI uri : npi.getSeeAlsoUris()) {
					println("<li><a href=\"" + uri + "\" rel=\"nofollow\">" + uri + "</a></li>");
				}
				println("</ul>");
			}
			if (npi.getAppendedIndex() != null) {
				println("<h3>Appends:</h3>");
				println("<table><tbody>");
				printItem(npi.getAppendedIndex());
				println("</tbody></table>");
			}
			if (!npi.getSubIndexes().isEmpty()) {
				println("<h3>Includes as Sub-Indexes:</h3>");
				println("<table><tbody>");
				for (URI uri : npi.getSubIndexes()) {
					printItem(uri);
				}
				println("</tbody></table>");
			}
			if (!npi.getElements().isEmpty()) {
				println("<h3>Includes as Elements:</h3>");
				println("<table><tbody>");
				for (URI uri : npi.getElements()) {
					printItem(uri);
				}
				println("</tbody></table>");
			}
			printHtmlFooter();
		} catch (MalformedNanopubException ex) {
			throw new RuntimeException(ex);
		}
	}

	private void printItem(URI uri) throws IOException {
		String artifactCode = TrustyUriUtils.getArtifactCode(uri.toString());
		print("<tr>");
		print("<td>");
		printAltLinks(artifactCode);
		print("</td>");
		print("<td><span class=\"code\">" + uri + "</span></td>");
		println("</tr>");
	}

	private void displayJSONLD(Nanopub npm, String url) throws IOException {
		getResp().setContentType("text/html");
		String nphash = url.substring(1);
		String title = "Nanopublication - Display";
		String headerTitle = "Nanopublication - Display";
		printHtmlHeader(headerTitle);
		// Initialize JS tool!
		println("<h1>Nanopublication - Display</h1>");
		println("<h4>"+nphash+"</h4>");
		println("<ul class='inline'>");
		println("<li><a href='"+nphash+".trig'>trig</a></li>");
		println("<li><a href='"+nphash+".nq'>nq</a></li>");
		println("<li><a href='"+nphash+".xml'>xml</a></li>");
		println("<li><a href='"+nphash+".jsonld'>jsonld</a></li>");
		println("</ul>");
		println("<div id='display'></div>");
		println("<script src='js/jquery.js'></script>");
		println("<script src='js/verovio.js'></script>");
		println("<script src='js/display.js'></script>");
		println("<script>displayJSONLD('"+nphash+"');</script>");
		printHtmlFooter();
	}
}
