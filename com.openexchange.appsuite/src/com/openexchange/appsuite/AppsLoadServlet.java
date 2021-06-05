/*
 * @copyright Copyright (c) OX Software GmbH, Germany <info@open-xchange.com>
 * @license AGPL-3.0
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OX App Suite.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>.
 *
 * Any use of the work other than as authorized under this license or copyright law is prohibited.
 *
 */

package com.openexchange.appsuite;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpStatus;
import com.openexchange.ajax.SessionServlet;
import com.openexchange.appsuite.FileCache.Filter;
import com.openexchange.java.Charsets;
import com.openexchange.java.Strings;
import com.openexchange.osgi.RankingAwareNearRegistryServiceTracker;
import com.openexchange.session.Session;
import com.openexchange.tools.session.ServerSession;

/**
 * {@link AppsLoadServlet} - Provides App Suite data for loading applications.
 *
 * @author <a href="mailto:viktor.pracht@open-xchange.com">Viktor Pracht</a>
 */
public class AppsLoadServlet extends SessionServlet {

	/**
     * The hardcoded appsuite ui uri limit in characters
     */
    private static final int UI_URI_LIMIT = 8190;

    private static final long serialVersionUID = -8909104490806162791L;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AppsLoadServlet.class);

    private static String ZONEINFO = "io.ox/core/date/tz/zoneinfo/";

    // ----------------------------------------------------------------------------------------------------------------------

    private volatile DefaultFileCache appCache;
    private volatile DefaultFileCache tzCache;
    private final RankingAwareNearRegistryServiceTracker<FileCacheProvider> fileCacheProviderTracker;

    /**
     * Initializes a new {@link AppsLoadServlet}.
     *
     * @param contributor The (composite) file contributor.
     */
    public AppsLoadServlet(RankingAwareNearRegistryServiceTracker<FileCacheProvider> fileCacheProviderTracker) {
        super();
        this.fileCacheProviderTracker = fileCacheProviderTracker;
    }

    /**
     * Reinitializes this Servlet using given arguments
     *
     * @param roots The app roots
     * @param zoneinfo The zone information
     * @throws IOException If canonical path names of given files cannot be determined
     */
    public synchronized void reinitialize(File[] roots, File zoneinfo) throws IOException {
        appCache = new DefaultFileCache(roots);
        tzCache = new DefaultFileCache(zoneinfo);
    }

    /**
     * Escapes the given name
     *
     * @param name The name to escape
     * @return The escaped name
     */
    private String escapeName(String name) {
        String nameToEscape = name;
        if (nameToEscape.length() > 256) {
            nameToEscape = nameToEscape.substring(0, 256);
        }
        final StringBuffer sb = new StringBuffer();
        escape(nameToEscape, sb);
        return sb.toString();
    }

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        // create a new HttpSession if it's missing
        req.getSession(true);
        super.service(req, resp);
    }

    /*
     * Errors must not be cached. Since this is controlled by the "Expires" header at the start, data must be buffered until either an error
     * is found or the end of the data is reached. Since non-error data is cached in RAM anyway, the only overhead is an array of pointers.
     */
    private static class ErrorWriter {

        private boolean buffering = true;
        private final HttpServletResponse resp;
        private OutputStream out;
        private byte[][] buffer;
        private int count = 0;

        ErrorWriter(HttpServletResponse resp, int length) {
            super();
            this.resp = resp;
            this.buffer = new byte[length][];
        }

        private void stopBuffering() throws IOException {
            buffering = false;
            out = resp.getOutputStream();
            for (int i = 0; i < count; i++) {
                write(buffer[i]);
            }
            buffer = null;
        }

        public void write(byte[] data) throws IOException {
            write(data, null);
        }

        public void write(byte[] data, String options) throws IOException {
            byte[] dataToWrite = data;
            if (buffering) {
                dataToWrite = new StringBuilder(new String(dataToWrite, "UTF-8")).append("\n\n/* :oxoptions: " + options + " :/oxoptions: */").toString().getBytes("UTF-8");
                buffer[count++] = dataToWrite;
            } else {
                out.write(dataToWrite);
                if (options != null) {
                    out.write(("\n// :oxoptions: " + options + " :/oxoptions: \n").getBytes("UTF-8"));
                }
                out.write(SUFFIX);
                out.flush();
            }
        }

        public void error(byte[] data) throws IOException {
            if (buffering) {
                resp.setHeader("Expires", "0");
                stopBuffering();
            }
            write(data);
        }

        public void done() throws IOException {
            if (!buffering) {
                return;
            }
            resp.setDateHeader("Expires", System.currentTimeMillis() + (long) 3e10); // + almost a year
            stopBuffering();
        }
    }

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
			throws ServletException, IOException {
	    if (req.getRequestURL().length() > UI_URI_LIMIT) {
	        LOG.error("Url length exceeds maximum allowed characters.");
	        writeErrorPage(HttpStatus.SC_REQUEST_URI_TOO_LONG, "The request is too long", resp);
	        return;
	    }
		ServerSession session = getSessionObject(req, true);
		String[] modules = Strings.splitByComma(req.getPathInfo());
		if (null == modules) {
			return; // no actual files requested
		}

		int length = modules.length;
        if (length < 2) {
            return; // no actual files requested
        }
		// Filter duplicates
        modules = Arrays.asList(modules).stream().distinct().toArray(String[]::new);
        // Check length again
		length = modules.length;
		if (length < 2) {
			return; // no actual files requested
		}
		String version = modules[0]; // modules 0 contains the version string
		resp.setContentType("text/javascript;charset=UTF-8");
		ErrorWriter ew = new ErrorWriter(resp, length);
		for (int i = 1; i < length; i++) {
			final String module = modules[i].replace(' ', '+');
			// Module names may only contain letters, digits, '_', '-', '/' and
			// '.', but not "..".
			final Matcher m = moduleRE.matcher(module);
			if (!m.matches()) {
				final String escapedName = escapeName(module);
				LOG.debug("Invalid module name: '{}'", escapedName);
				ew.error(("console.error('Invalid module name detected');\n").getBytes("UTF-8"));
				continue;
			}

			// Map module name to file name
			final String format = m.group(1);
			String name = m.group(2);
			boolean isTZ = name.startsWith(ZONEINFO);
			final String resolved = isTZ ? name.substring(ZONEINFO.length()) : name;
			byte[] data = null;
            if (Strings.isNotEmpty(version)) {
                Optional<FileCache> optCache = getExternalCache(session, version, module);
                if (optCache.isPresent()) {
                    // Getting data from external cache
                    data = optCache.get().get(module, getFilter(resolved, format, module));
                }
            }
			if (data != null) {
				ew.write(data);
			} else {
				// Fallback to normal cache
				FileCache cache = isTZ ? tzCache : appCache;
				data = cache.get(module, getFilter(resolved, format, module));
				if (data != null) {
					ew.write(data);
				} else {
					LOG.debug("Error loading data for module '{}'", escapeName(module));
					writeErrorResponse(ew, format, module);
				}
			}
		}
		ew.done();
	}

    /**
     * Gets a filter for the {@link FileCache}
     *
     * @param resolved The resolved name of the file
     * @param format The requested format
     * @param module The requested module
     * @return
     */
    private Filter getFilter(String resolved, String format, String module) {
    	return new DefaultFileCache.Filter() {

            @Override
            public String resolve(String path) {
                return resolved;
            }

            @Override
            @SuppressWarnings("deprecation")
            public byte[] filter(ByteArrayOutputStream baos) {
                if (format == null) {
                    return baos.toByteArray();
                }

                // Special cases for JavaScript-friendly reading of raw files:
                // /text;* returns the file as a UTF-8 string
                // /raw;* maps every byte to [u+0000..u+00ff]
                final StringBuffer sb = new StringBuffer();
                sb.append("define('").append(module).append("','");
                try {
                    escape("raw".equals(format) ? baos.toString(0) : baos.toString(Charsets.UTF_8_NAME), sb);
                } catch (UnsupportedEncodingException e) {
                    // everybody should have UTF-8
                }
                sb.append("');\n");
                return sb.toString().getBytes(Charsets.UTF_8);
            }
        };
    }

    /**
     * Gets the external {@link FileCache} which is applicable for the given values
     *
     * @param session The user session
     * @param version The requested version
     * @param path The requested path
     * @return An {@link Optional} {@link FileCache}
     */
    private Optional<FileCache> getExternalCache(Session session, String version, String path) {
    	if (fileCacheProviderTracker != null) {
	    	for(FileCacheProvider provider: fileCacheProviderTracker.getServiceList()) {
	    		if (provider.isApplicable(session, version, path)) {
	    			return Optional.of(provider.getData());
	    		}
	    	}
    	}
    	return Optional.empty();

    }

    /**
     * Writes the error response code
     *
     * @param ew The {@link ErrorWriter}
     * @param format The requested format
     * @param module The requested module
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    private void writeErrorResponse(ErrorWriter ew, String format, String module) throws UnsupportedEncodingException, IOException {
        LOG.debug("Could not read data for module '{}'", escapeName(module));
        int len = module.length() - 3;
        String moduleName = module;
        if (format == null && len > 0 && ".js".equals(module.substring(len))) {
            moduleName = module.substring(0, len);
        }
        int[] key = new int[8];
        java.util.Random r = new java.util.Random();
        for (int j = 0; j < key.length; j++) {
            key[j] = r.nextInt(256);
        }
        char[] obfuscated = moduleName.toCharArray();
        for (int j = 0; j < obfuscated.length; j++) {
            obfuscated[j] += key[j % key.length];
        }
        ew.error(("(function(){" +
            "var key = [" + Strings.join(key, ",") + "], name = '" + escapeName(new String(obfuscated)) + "';" +
            "function c(c, i) { return c.charCodeAt(0) - key[i % key.length]; }" +
            "name = String.fromCharCode.apply(String, [].slice.call(name).map(c));" +
            "define(name, function () {" +
            "if (ox.debug) console.log(\"Could not read '\" + name + \"'\");" +
            "throw new Error(\"Could not read '\" + name + \"'\");" +
            "});" +
            "}());").getBytes("UTF-8")
        );

    }

    // ------------------------------------------------------------------------------------------------------------------------

    private static Pattern moduleRE = Pattern.compile("(?:/(text|raw);)?([\\w/+-]+(?:\\.[\\w/+-]+)*)");

    private static Pattern escapeRE = Pattern.compile("[\\x00-\\x1f'\\\\\\u2028\\u2029]");

    private static String[] escapes = {
        "\\\\x00", "\\\\x01", "\\\\x02", "\\\\x03", "\\\\x04", "\\\\x05", "\\\\x06", "\\\\x07", "\\\\b", "\\\\t", "\\\\n", "\\\\v",
        "\\\\f", "\\\\r", "\\\\x0e", "\\\\x0f", "\\\\x10", "\\\\x11", "\\\\x12", "\\\\x13", "\\\\x14", "\\\\x15", "\\\\x16", "\\\\x17",
        "\\\\x18", "\\\\x19", "\\\\x1a", "\\\\x1b", "\\\\x1c", "\\\\x1d", "\\\\x1e", "\\\\x1f" };

    static byte[] SUFFIX;
    static {
        try {
            SUFFIX = "\n/*:oxsep:*/\n".getBytes(Charsets.UTF_8_NAME);
        } catch (UnsupportedEncodingException e) {
            SUFFIX = "\n/*:oxsep:*/\n".getBytes();
        }
    }

    /**
     * Escapes the given {@link CharSequence}
     *
     * @param s The string to escape
     * @param sb The target {@link StringBuffer}
     */
    static void escape(final CharSequence s, final StringBuffer sb) {
        final Matcher e = escapeRE.matcher(s);
        while (e.find()) {
            final int chr = e.group().codePointAt(0);
            String replacement;
            switch (chr) {
            case 0x27:
                replacement = "\\\\'";
                break;
            case 0x5c:
                replacement = "\\\\\\\\";
                break;
            case 0x2028:
                replacement = "\\\\u2028";
                break;
            case 0x2029:
                replacement = "\\\\u2029";
                break;
            default:
                replacement = escapes[chr];
            }
            e.appendReplacement(sb, replacement);
        }
        e.appendTail(sb);
    }

}
