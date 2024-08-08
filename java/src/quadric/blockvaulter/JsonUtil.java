package quadric.blockvaulter;

import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonUtil {
	
	// Matches "%s": "loadme"
	private static final String STRING_KEY_VAL_REGEX = "\\\"%s\\\"\\B.*?\\\"(.*?)\\\"";
	private static HashMap<String, Pattern> patternCache = new HashMap<String,Pattern>(); 
	
	/**
	 * Extracts a string value from a json string
	 * @param key the key whose value to extract
	 * @param json what to search in
	 * @return the result, after unescaping it
	 */
	public static String getStringVal(String key, String json) {
		String theGuy = STRING_KEY_VAL_REGEX;
		theGuy = theGuy.replace("%s", key);
		Pattern p;
		synchronized(patternCache) {
			p = patternCache.get(key);
			if(p == null) {
				p = Pattern.compile(theGuy);
				patternCache.put(key, p);
			}
		}
		Matcher m = p.matcher(json);
		if(m.find()) {
			return unescapeJsonString(m.group(1));
		} else {
			return "";
		}
	}
	
	/*public static String escapeJsonString(String orig) {
		return orig.replace("\"", "\\\"").replace("\\", "\\\\").replace("/", "\\/");
	}*/
	
	public static String unescapeJsonString(String encoded) {
		return encoded.replace("\\\\", "\\").replace("\\\"", "\"");
	}
}
