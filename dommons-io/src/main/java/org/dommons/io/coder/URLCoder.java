/*
 * @(#)URLCoder.java     2012-4-12
 */
package org.dommons.io.coder;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dommons.core.Environments;
import org.dommons.core.collections.map.concurrent.ConcurrentSoftMap;
import org.dommons.core.convert.Converter;
import org.dommons.core.number.Radix64;
import org.dommons.core.string.Stringure;
import org.dommons.core.util.Arrayard;
import org.dommons.security.coder.Coder;

/**
 * URL 编码转换器
 * @author Demon 2012-4-12
 */
public class URLCoder implements Coder {

	private static Map<String, URLCoder> ccs = new ConcurrentSoftMap();

	/**
	 * 解码
	 * @param code 密文
	 * @param charset 字符集
	 * @return 明文
	 */
	public static String decode(String code, Charset charset) {
		if (code == null) return null;
		if (charset == null) charset = Environments.defaultCharset();

		int numChars = code.length();
		boolean needToChange = false;
		StringBuilder builder = new StringBuilder(numChars > 500 ? numChars / 2 : numChars);

		for (int i = 0; i < numChars;) {
			char c = code.charAt(i);
			switch (c) {
			case '+':
				builder.append(' ');
				i++;
				needToChange = true;
				break;
			case '%':
				byte[] bytes = new byte[(numChars - i) / 3];
				int pos = 0;
				per: {
					while (((i + 2) < numChars) && (c == '%')) {
						try {
							bytes[pos++] = (byte) Radix64.toInteger(code.substring(i + 1, i + 3), 16);
							i += 3;
							if (i < numChars) c = code.charAt(i);
						} catch (NumberFormatException e) {
							pos--;
							break per;
						}
					}

					if ((i < numChars) && (c == '%')) break per;

					Stringure.toString(bytes, 0, pos, charset, builder);

					needToChange = true;
					break;
				}
				if (pos > 0) {
					Stringure.toString(bytes, 0, pos, charset, builder);
					needToChange = true;
				}
			default:
				builder.append(c);
				i++;
				break;
			}
		}

		return (needToChange ? builder.toString() : code);
	}

	/**
	 * 解码
	 * @param code 密文
	 * @param enc 字符集
	 * @return 明文
	 */
	public static String decode(String code, String enc) {
		return decode(code, Stringure.isEmpty(enc) ? null : Charset.forName(enc));
	}

	/**
	 * 编码
	 * @param code 原文
	 * @param charset 字符集
	 * @return 密文
	 */
	public static String encode(String code, Charset charset) {
		if (code == null) return null;
		if (charset == null) charset = Environments.defaultCharset();

		try {
			return URLEncoder.encode(code, charset.name());
		} catch (UnsupportedEncodingException e) {
			throw new UnsupportedOperationException(e);
		}
	}

	/**
	 * 编码
	 * @param code 明文
	 * @param enc 字符集
	 * @return 密文
	 */
	public static String encode(String code, String enc) {
		return encode(code, Stringure.isEmpty(enc) ? null : Charset.forName(enc));
	}

	/**
	 * 获取转码器实例
	 * @return 转码器实例
	 */
	public static URLCoder instance() {
		return instance(null);
	}

	public static URLCoder instance(Charset cs) {
		if (cs == null) cs = Environments.defaultCharset();
		String key = cs.name();
		URLCoder coder = ccs.get(key);
		if (coder == null) {
			synchronized (ccs) {
				ccs.put(key, coder = new URLCoder(cs));
			}
		}
		return coder;

	}

	/**
	 * 解析参数集
	 * @param query 参数串
	 * @return 参数集
	 */
	public static Map parameters(String query) {
		return parameters(query, Stringure.charset("utf8"));
	}

	/**
	 * 解析参数集
	 * @param query 参数串
	 * @param charset 字符集
	 * @return 参数集
	 */
	public static Map parameters(String query, Charset charset) {
		return instance().parse(query, null, charset);
	}

	private final Charset cs;

	protected URLCoder(Charset cs) {
		this.cs = cs;
	}

	public String decode(String code) {
		return decode(code, cs);
	}

	public String encode(String code) {
		return encode(code, cs);
	}

	/**
	 * 拼接参数集
	 * @param query 参数集
	 * @return 参数集串
	 */
	public String join(Map<String, ?> query) {
		return join(null, query).toString();
	}

	/**
	 * 连接网址
	 * @param host 服务地址
	 * @param path 路径
	 * @param ps 参数集
	 * @return 网址
	 */
	public String join(String host, String path, Map ps) {
		StringBuilder buf = new StringBuilder(64);
		host = Stringure.trim(host);
		boolean h = false;
		if (!host.isEmpty()) {
			try {
				URL u = new URL(host);
				buf.append(u.getProtocol()).append("://");
				h: if (!Stringure.isEmpty(u.getHost())) {
					buf.append(u.getHost());
					int p = u.getPort();
					if (p <= 0) break h;
					else if ("https".equals(u.getProtocol()) && p == 443) break h;
					else if ("http".equals(u.getProtocol()) && p == 80) break h;
					buf.append(':').append(u.getPort());
				}
				h = path(buf, u.getPath(), false);
				ps = parse(u.getQuery(), ps);
			} catch (MalformedURLException e) { // ignored
			}
		}
		path(buf, path, !h);
		join(buf, ps);
		return buf.toString();
	}

	/**
	 * 拼接参数集
	 * @param buf 字符缓存区
	 * @param query 参数集
	 * @return 参数集串
	 */
	public StringBuilder join(StringBuilder buf, Map<String, ?> query) {
		if (buf == null) buf = new StringBuilder(32);
		if (query != null) {
			int[] x = { 0 };
			for (Entry<String, ?> en : query.entrySet()) {
				String key = Stringure.trim(en.getKey());
				if (Stringure.isEmpty(key)) continue;
				Object v = en.getValue();
				if (v == null) continue;
				if (v instanceof Collection) inner(buf, key, x, ((Collection) v).toArray());
				else if (v.getClass().isArray()) inner(buf, key, x, Arrayard.asArray(v));
				else inner(buf, key, x, v);
			}
		}
		return buf;
	}

	/**
	 * 解析参数集
	 * @param query 参数串
	 * @param ps 参数集
	 * @return 参数集
	 */
	public Map parse(String query, Map ps) {
		return parse(query, ps, null);
	}

	/**
	 * 解析参数集
	 * @param query 参数串
	 * @param ps 参数集
	 * @param cs 字符集
	 * @return 参数集
	 */
	public Map parse(String query, Map ps, Charset cs) {
		query = Stringure.trim(query);
		if (!query.isEmpty()) {
			if (ps == null) ps = new TreeMap();
			if (cs == null) cs = Environments.defaultCharset();
			Pattern pattern = Pattern.compile("([^=&]+)(\\=([^&=]*))?(?=&|$)");
			if (query != null) {
				Matcher m = pattern.matcher(query);
				while (m.find()) {
					String k = decode(m.group(1), cs), v = decode(m.group(3), cs);
					Object old = ps.get(k);
					if (old != null) {
						Collection list = null;
						if (old instanceof Collection) {
							list = (Collection) old;
						} else {
							list = new LinkedList();
							list.add(old);
							ps.put(k, list);
						}
						list.add(k);
					} else {
						ps.put(k, v);
					}
				}
			}
		}
		return ps;
	}

	/**
	 * 追加参数集
	 * @param buf 字符缓存区
	 * @param k 键值
	 * @param x 序号
	 * @param vs 参数集
	 */
	protected void inner(StringBuilder buf, String k, int[] x, Object... vs) {
		for (Object v : vs) {
			String sv = Converter.F.convert(v, String.class);
			if (sv == null) continue;
			if (x[0]++ > 0) buf.append('&');
			else if (buf.length() > 0) buf.append('?');
			buf.append(encode(k)).append('=').append(encode(Stringure.trim(sv)));
		}
	}

	/**
	 * 追加路径
	 * @param buf 缓存区
	 * @param p 路径
	 * @param end 是否追加截止斜杠
	 * @return 是否追加
	 */
	private boolean path(StringBuilder buf, String p, boolean end) {
		p = Stringure.trim(p);
		if (!p.isEmpty()) {
			int h = 0;
			if (buf.length() > 0) {
				boolean b = buf.charAt(buf.length() - 1) == '/';
				if (b) {
					if (p.charAt(0) == '/') h = 1;
				} else if (p.charAt(0) != '/') {
					buf.append('/');
				}
			}
			buf.append(p, h, p.length());
			return true;
		} else if (buf.length() > 0 && buf.charAt(buf.length() - 1) != '/' && end) {
			buf.append('/');
			return true;
		}
		return false;
	}
}
